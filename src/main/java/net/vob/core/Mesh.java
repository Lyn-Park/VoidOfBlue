package net.vob.core;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import net.vob.core.graphics.GraphicsEngine;
import net.vob.util.Closable;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.Cuboid;
import net.vob.util.math.Vector3;

/**
 * Container class for a mesh. This is kept distinct from the internal mesh objects in
 * the graphics engine to promote decoupling between the internal graphical state and
 * the external program, as well as to prevent confusion.<p>
 * 
 * Meshes can also be used for defining various physical properties of the object in
 * question, such as the bounding box.
 * 
 * @author Lyn-Park
 */
public final class Mesh extends Closable {
    private Vector3[] positions;
    private Vector3[] uvs;
    private Vector3[] normals;
    private int[] triangles;
    private boolean readonly = false;
    
    private Cuboid boundingBox;
    
    private Future<Integer> meshID = null;
    
    public static final Mesh DEFAULT_QUAD, DEFAULT_CUBE, DEFAULT_INV_CUBE;
    
    static {
        GraphicsEngine.MESSAGE_LOCK.lock();
        
        try {
            DEFAULT_QUAD = new Mesh(GraphicsEngine.msgMeshSelectDefaultQuad());
            DEFAULT_CUBE = new Mesh(GraphicsEngine.msgMeshSelectDefaultCube());
            DEFAULT_INV_CUBE = new Mesh(GraphicsEngine.msgMeshSelectDefaultInvCube());
            
            DEFAULT_QUAD.boundingBox = new Cuboid(-0.5d, -0.5d,    0 , 0.5d, 0.5d,   0 );
            DEFAULT_CUBE.boundingBox = new Cuboid(-0.5d, -0.5d, -0.5d, 0.5d, 0.5d, 0.5d);
            DEFAULT_INV_CUBE.boundingBox = new Cuboid(-0.5d, -0.5d, -0.5d, 0.5d, 0.5d, 0.5d);
            
            DEFAULT_QUAD.setAsReadonly();
            DEFAULT_CUBE.setAsReadonly();
            DEFAULT_INV_CUBE.setAsReadonly();
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
    }
    
    private Mesh(Future<Integer> meshID) {
        this.meshID = meshID;
        this.readonly = true;
    }
    
    /**
     * Instantiates the mesh using the given parameters.
     * @param positions the array of positions of the mesh vertices
     * @param uvs the array of uv coordinates of the mesh vertices
     * @param normals the array of normal vectors of the mesh vertices. Set this to
     * {@code null} to signal that the normals should be automatically calculated
     * @param triangles the array of triangle indices of the mesh
     * @throws NullPointerException if any of the parameters are {@code null}
     * @throws IllegalArgumentException if {@code positions.length != uvs.length},
     * or {@code triangles.length} is not divisible by 3
     * @throws IndexOutOfBoundsException if any element of {@code triangles} is
     * less than 0 or greater than or equal to {@code positions.length}
     */
    public Mesh(Vector3[] positions, Vector3[] uvs, @Nullable Vector3[] normals, int[] triangles) {
        if (positions == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "positions"));
        if (uvs == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "uvs"));
        if (triangles == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "triangles"));
        
        if (positions.length != uvs.length)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", "uvs", uvs.length, positions.length));
        if (normals != null && positions.length != normals.length)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", "normals", normals.length, positions.length));
        if (triangles.length % 3 != 0)
            throw new IllegalArgumentException(LocaleUtils.format("Mesh._cinit_.InvalidTriangleArrayLength"));
        
        for (int t : triangles)
            if (t < 0 || t >= positions.length)
                throw new IndexOutOfBoundsException(LocaleUtils.format("Mesh._cinit_.InvalidTriangleElement", positions.length));
        
        this.positions = positions;
        this.uvs = uvs;
        this.normals = normals;
        this.triangles = triangles;
        
        resetBoundingBox();
    }
    
    void select() {
        try {
            if (meshID == null)
                meshID = GraphicsEngine.msgMeshNew(positions, uvs, normals, triangles);
            else
                GraphicsEngine.msgMeshSelect(meshID.get(5, TimeUnit.SECONDS));

        } catch (InterruptedException | ExecutionException | CancellationException | TimeoutException e) {
            throw new IllegalStateException(LocaleUtils.format("Mesh.select.InitFailed"), e);
        }
    }
    
    /**
     * Sets this mesh as read-only. This is a one-way operation, and cannot be undone.
     */
    public void setAsReadonly() {
        if (readonly)
            return;
        
        readonly = true;
        
        GraphicsEngine.MESSAGE_LOCK.lock();
        
        try {
            select();
            GraphicsEngine.msgMeshSetReadonly();
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
    }
    
    /**
     * Sets the attributes of the mesh. Any {@code null} parameter signifies that
     * attribute should remain unaltered.
     * @param positions the new positions of the mesh vertices, or {@code null} to
     * not change the current positions
     * @param uvs the new uv coordinates of the mesh vertices, or {@code null} to
     * not change the current uvs
     * @param normals the new normal vectors of the mesh vertices, or {@code null} to
     * not change the current normals
     * @param triangles the new triangle indices of the mesh, or {@code null} to not
     * change the current triangles
     * @throws IllegalStateException if this mesh has been set to be read-only
     * @throws IllegalArgumentException if {@code positions.length != uvs.length}
     * (using the current values of these attributes if either parameter is
     * {@code null}), or if {@code triangles} is non-{@code null} and
     * {@code triangles.length} is not divisible by 3
     * @throws IndexOutOfBoundsException if {@code triangles} is non-{@code null}
     * any element of {@code triangles} is less than 0 or greater than or equal to
     * {@code positions.length}
     */
    public void setAttributes(@Nullable Vector3[] positions, @Nullable Vector3[] uvs, @Nullable Vector3[] normals, @Nullable int[] triangles) {
        if (positions == null && uvs == null && normals == null && triangles == null)
            return;
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Mesh"));
        
        if (positions == null && uvs != null && this.positions.length != uvs.length)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", "uvs", uvs.length, this.positions.length));
        if (positions != null && uvs == null && positions.length != this.uvs.length)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", "positions", positions.length, this.uvs.length));
        if (positions != null && uvs != null && positions.length != uvs.length)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", "uvs", uvs.length, positions.length));
        
        if (positions == null && normals != null && this.positions.length != normals.length)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", "normals", normals.length, this.positions.length));
        if (positions != null && normals == null && positions.length != this.normals.length)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", "positions", positions.length, this.normals.length));
        if (positions != null && normals != null && positions.length != normals.length)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", "normals", normals.length, positions.length));
        
        if (triangles != null) {
            if (triangles.length % 3 != 0)
                throw new IllegalArgumentException(LocaleUtils.format("Mesh._cinit_.InvalidTriangleArrayLength"));
        
            int p = positions == null ? this.positions.length : positions.length;
            
            for (int t : triangles)
                if (t < 0 || t >= p)
                    throw new IndexOutOfBoundsException(LocaleUtils.format("Mesh._cinit_.InvalidTriangleElement", positions.length));
        }
        
        if (positions != null) {
            this.positions = positions;
            resetBoundingBox();
        }
        if (uvs != null)
            this.uvs = uvs;
        if (normals != null)
            this.normals = normals;
        if (triangles != null) 
            this.triangles = triangles;
        
        if (meshID != null) {
            GraphicsEngine.MESSAGE_LOCK.lock();
            
            try {
                select();
                GraphicsEngine.msgMeshSetAttributes(positions, uvs, normals, triangles);
                
            } finally {
                GraphicsEngine.MESSAGE_LOCK.unlock();
            }
        }
    }
    
    private void resetBoundingBox() {
        if (positions.length == 0)
            boundingBox = new Cuboid(0, 0, 0, 0, 0, 0);
        
        double xL = positions[0].getX(), yL = positions[0].getY(), zL = positions[0].getZ();
        double xH = positions[0].getX(), yH = positions[0].getY(), zH = positions[0].getZ();
        
        for (int i = 1; i < positions.length; ++i) {
            double x = positions[i].getX(), y = positions[i].getY(), z = positions[i].getZ();
            
            if (x < xL) xL = x;
            if (y < yL) yL = y;
            if (z < zL) zL = z;
            if (x > xH) xH = x;
            if (y > yH) yH = y;
            if (z > zH) zH = z;
        }
        
        boundingBox = new Cuboid(xL, yL, zL, xH, yH, zH);
    }
    
    /**
     * Gets the bounding box of this mesh. This is defined to be the smallest cuboid
     * that contains the mesh in it's entirety.
     * @return the bounding box of this mesh
     */
    public Cuboid getBoundingBox() {
        return boundingBox;
    }
    
    @Override
    protected boolean doClose() {
        if (meshID != null) {
            GraphicsEngine.MESSAGE_LOCK.lock();

            try {
                select();
                GraphicsEngine.msgMeshClose();

            } catch (IllegalStateException e) {
                return false;
                
            } finally {
                GraphicsEngine.MESSAGE_LOCK.unlock();
            }
        }
        
        return true;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Mesh)) return false;
        
        Mesh m = (Mesh)o;
        
        if (this.positions == null)
            return super.equals(o);
        else
            return Arrays.equals(this.positions, m.positions) &&
                   Arrays.equals(this.uvs, m.uvs) &&
                   Arrays.equals(this.normals, m.normals) &&
                   Arrays.equals(this.triangles, m.triangles);
    }

    @Override
    public int hashCode() {
        if (this.positions == null)
            return super.hashCode();
        
        else {
            int hash = 7;
            hash = 97 * hash + Arrays.deepHashCode(this.positions);
            hash = 97 * hash + Arrays.deepHashCode(this.uvs);
            hash = 97 * hash + Arrays.deepHashCode(this.normals);
            hash = 97 * hash + Arrays.hashCode(this.triangles);
            return hash;
        }
    }
}
