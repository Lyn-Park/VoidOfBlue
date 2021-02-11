package net.vob.core.graphics;

import com.google.common.collect.Sets;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.vob.util.Registry;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.Maths;
import net.vob.util.math.Vector3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

/**
 * Collection of vertex attributes and triangles. Can be used on it's own for rendering,
 * or with bound shader programs and textures.
 * 
 * @author Lyn-Park
 */
final class GLMesh extends GLObject {
    static final Registry<GLMesh> REGISTRY = new Registry<>();
    
    /** 
     * A mesh representing a single quad (pair of triangles that form a 2D square). The
     * quad faces the positive-Z axis, and both sides have length 1.
     */
    static final GLMesh DEFAULT_QUAD = new GLMesh(new Vector3[] {
                                                    new Vector3(-0.5, 0.5, 0),
                                                    new Vector3(-0.5, -0.5, 0),
                                                    new Vector3(0.5, -0.5, 0),
                                                    new Vector3(0.5, 0.5, 0)
                                                 }, new Vector3[] {
                                                    new Vector3(0, 0, 0),
                                                    new Vector3(0, 1, 0),
                                                    new Vector3(1, 1, 0),
                                                    new Vector3(1, 0, 0)
                                                 }, null,
                                                    new int[] {
                                                    0, 1, 2, 0, 2, 3
                                                 });
    /**
     * A mesh representing a single cube (set of 6 quads that form a 3D cube). The cube
     * faces all face outwards, and all sides have length 1.
     */
    static final GLMesh DEFAULT_CUBE = new GLMesh(new Vector3[] {
                                                    new Vector3(0.5, -0.5, -0.5),
                                                    new Vector3(0.5, 0.5, -0.5),
                                                    new Vector3(0.5, 0.5, 0.5),
                                                    new Vector3(0.5, -0.5, 0.5),
                                                    new Vector3(-0.5, -0.5, 0.5),
                                                    new Vector3(-0.5, 0.5, 0.5),
                                                    new Vector3(-0.5, 0.5, -0.5),
                                                    new Vector3(-0.5, -0.5, -0.5)
                                                 }, new Vector3[] {
                                                    new Vector3(0.5, -0.5, -0.5),
                                                    new Vector3(0.5, 0.5, -0.5),
                                                    new Vector3(0.5, 0.5, 0.5),
                                                    new Vector3(0.5, -0.5, 0.5),
                                                    new Vector3(-0.5, -0.5, 0.5),
                                                    new Vector3(-0.5, 0.5, 0.5),
                                                    new Vector3(-0.5, 0.5, -0.5),
                                                    new Vector3(-0.5, -0.5, -0.5)
                                                 }, null,
                                                    new int[] {
                                                     0, 1, 2, 0, 2, 3,
                                                     4, 5, 6, 4, 6, 7,
                                                     1, 5, 2, 1, 6, 5,
                                                     0, 3, 4, 0, 4, 7,
                                                     2, 4, 3, 2, 5, 4,
                                                     0, 6, 1, 0, 7, 6
                                                 });
    /**
     * A mesh representing a single cube (set of 6 quads that form a 3D cube). The cube
     * faces all face inwards, and all sides have length 1.
     */
    static final GLMesh DEFAULT_INV_CUBE = new GLMesh(new Vector3[] {
                                                    new Vector3(0.5, -0.5, -0.5),
                                                    new Vector3(0.5, 0.5, -0.5),
                                                    new Vector3(0.5, 0.5, 0.5),
                                                    new Vector3(0.5, -0.5, 0.5),
                                                    new Vector3(-0.5, -0.5, 0.5),
                                                    new Vector3(-0.5, 0.5, 0.5),
                                                    new Vector3(-0.5, 0.5, -0.5),
                                                    new Vector3(-0.5, -0.5, -0.5)
                                                 }, new Vector3[] {
                                                    new Vector3(0.5, -0.5, -0.5),
                                                    new Vector3(0.5, 0.5, -0.5),
                                                    new Vector3(0.5, 0.5, 0.5),
                                                    new Vector3(0.5, -0.5, 0.5),
                                                    new Vector3(-0.5, -0.5, 0.5),
                                                    new Vector3(-0.5, 0.5, 0.5),
                                                    new Vector3(-0.5, 0.5, -0.5),
                                                    new Vector3(-0.5, -0.5, -0.5)
                                                 }, null,
                                                    new int[] {
                                                     0, 2, 1, 0, 3, 2,
                                                     4, 6, 5, 4, 7, 6,
                                                     1, 2, 5, 1, 5, 6,
                                                     0, 4, 3, 0, 7, 4,
                                                     2, 3, 4, 2, 4, 5,
                                                     0, 1, 6, 0, 6, 7
                                                 });
    
    static Set<GLMesh> getDefaults() {
        return Sets.newHashSet(DEFAULT_QUAD, DEFAULT_CUBE, DEFAULT_INV_CUBE);
    }
    
    /** Whether this mesh has been set to rebuffer. */
    static final int STATUS_REBUFFER = 1;
    /** Whether this mesh is allowed to have its attributes changed, or be rebuffered. */
    static final int STATUS_READONLY = 2;
    /** Whether the vertex positions are dirty. */
    static final int STATUS_DIRTY_VERTS_POS = 4;
    /** Whether the vertex UV texture coordinates are dirty. */
    static final int STATUS_DIRTY_VERTS_UV = 8;
    /** Whether the vertex normals are dirty. */
    static final int STATUS_DIRTY_VERTS_NORM = 16;
    /** Whether the number of vertices is dirty. */
    static final int STATUS_DIRTY_VERTS_COUNT = 32;
    /** Whether the triangles are dirty. */
    static final int STATUS_DIRTY_TRIS = 64;
    
    static final int STATUS_DIRTY_ALL = STATUS_DIRTY_VERTS_POS | STATUS_DIRTY_VERTS_UV | STATUS_DIRTY_VERTS_NORM | STATUS_DIRTY_VERTS_COUNT | STATUS_DIRTY_TRIS;
    
    private Vector3[] positions;
    private Vector3[] uvs;
    private Vector3[] normals;
    private int[] triangles;
    
    private Vector3[] prevPos;
    private Vector3[] prevUV;
    private Vector3[] prevNormals;
    private int[] prevTri;
    
    private int vao, vbo, ebo;
    private byte status = 0;
    
    /**
     * Constructs a new mesh.
     * @param positions the array of vertex positions
     * @param uvs the array of uv coordinates for the vertices. Must be of length
     * equal to {@code positions.length}
     * @param normals the array of vertex normals. If this is {@code null}, then
     * the normals will automatically be calculated; otherwise, must be of length
     * equal to {@code positions.length}
     * @param triangles the array of triangle indices. Must be of length equally
     * divisible by 3, and every element must lie between 0 (inclusive) and 
     * {@code positions.length} (exclusive)
     */
    GLMesh(Vector3[] positions, Vector3[] uvs, Vector3[] normals, int[] triangles) {
        if (positions.length != uvs.length)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", "uvs", uvs.length, positions.length));
        if (normals != null && positions.length != normals.length)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", "normals", normals.length, positions.length));
        if (triangles.length % 3 != 0)
            throw new IllegalArgumentException(LocaleUtils.format("GLMesh._cinit_.InvalidTriArrayLength"));
        for (int tri : triangles)
            if (tri < 0 || tri >= positions.length)
                throw new IndexOutOfBoundsException(LocaleUtils.format("GLMesh._cinit_.InvalidTriIndices"));
        
        this.positions = positions;
        this.uvs = uvs;
        this.triangles = triangles;
        
        if (normals == null)
            recalculateNormals();
        else
            this.normals = normals;
        
        this.prevPos = this.positions;
        this.prevUV = this.uvs;
        this.prevNormals = this.normals;
        this.prevTri = this.triangles;
    }
    
    /**
     * Performs graphics-side initialization of this mesh. This involves creating the
     * VAO and allocating the initial data buffers for the vertices and triangles.
     */
    @Override
    final void init() {
        vao = GL30.glGenVertexArrays();
        
        GL30.glBindVertexArray(vao);
        reallocateBuffers();
        GL30.glBindVertexArray(0);
    }
    
    /**
     * Gets the value of a status flag.
     * @param statusCode
     * @return 
     */
    final boolean getStatus(int statusCode) {
        return (status & statusCode) > 0;
    }
    
    /**
     * Sets the value of a status flag.
     * @param statusCode 
     */
    protected void setStatus(int statusCode) {
        status |= statusCode;
    }
    
    /**
     * Clears the value of a status flag.
     * @param statusCode 
     */
    protected void clearStatus(int statusCode) {
        status &= ~statusCode;
    }
    
    /**
     * Puts the attribute of the given vertex into the given buffer.
     * @param i the index of the vertex
     * @param buf the buffer to put the normal into
     */
    private void bufferVertexAttribute(Vector3 attr, ByteBuffer buf) {
        buf.putFloat((float)attr.getX());
        buf.putFloat((float)attr.getY());
        buf.putFloat((float)attr.getZ());
    }
    
    /**
     * Checks the current mesh parameters for validity.
     * @return 
     */
    private boolean areParamsValid() {
        for (int tri : triangles)
            if (tri < 0 || tri >= positions.length)
                return false;
        
        return positions.length == uvs.length && positions.length == normals.length && triangles.length % 3 == 0;
    }
    
    /**
     * Sets the mesh to be read-only, rejecting any changes to it attempted.
     */
    final void setAsReadonly() {
        if(isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLMesh"));
        
        setStatus(STATUS_READONLY);
    }
    
    /**
     * Checks if this mesh is read-only.
     * @return 
     */
    final boolean isReadonly() {
        return getStatus(STATUS_READONLY);
    }
    
    /**
     * Gets the number of vertices in this mesh.
     * @return 
     */
    final int getNumVertices() {
        return positions.length;
    }
    
    /**
     * Sets the positions of the vertices of this mesh.
     * @param positions the new array of vertex positions
     * @throws IllegalStateException if the mesh is closed or read-only
     */
    final void setPositions(Vector3[] positions) {
        if(isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLMesh"));
        if (getStatus(STATUS_READONLY))
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "GLMesh"));
        
        if (this.positions.length != positions.length)
            setStatus(STATUS_DIRTY_VERTS_COUNT);
        
        this.positions = positions;
        setStatus(STATUS_DIRTY_VERTS_POS);
    }
    
    /**
     * Sets the uvs of the vertices of this mesh.
     * @param positions the new array of vertex uv coordinates
     * @throws IllegalStateException if the mesh is closed or read-only
     */
    final void setUVs(Vector3[] uvs) {
        if(isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLMesh"));
        if (getStatus(STATUS_READONLY))
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "GLMesh"));
        
        this.uvs = uvs;
        setStatus(STATUS_DIRTY_VERTS_UV);
    }
    
    /**
     * Sets the normals of the vertices of this mesh.
     * @param positions the new array of vertex normals
     * @throws IllegalStateException if the mesh is closed or read-only
     */
    final void setNormals(Vector3[] normals) {
        if(isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLMesh"));
        if (getStatus(STATUS_READONLY))
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "GLMesh"));
        
        this.normals = normals;
        setStatus(STATUS_DIRTY_VERTS_NORM);
    }
    
    /**
     * Sets the triangles of this mesh.
     * @param positions the new array of triangle indices
     * @throws IllegalStateException if the mesh is closed or read-only
     */
    final void setTriangles(int[] triangles) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLMesh"));
        if (getStatus(STATUS_READONLY))
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "GLMesh"));
        
        this.triangles = triangles;
        setStatus(STATUS_DIRTY_TRIS);
    }
    
    /**
     * Flags the mesh so that it will delete and recreate the GL data buffers when it
     * is next rendered, rather than simply updating the existing buffers.
     * @throws IllegalStateException if the mesh is closed or read-only
     */
    final void setRebuffer() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLMesh"));
        if (getStatus(STATUS_READONLY))
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "GLMesh"));
        
        setStatus(STATUS_REBUFFER);
    }
    
    /**
     * Recalculates the vertex normals for this mesh.
     */
    final void recalculateNormals() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLMesh"));
        if (getStatus(STATUS_READONLY))
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "GLMesh"));
        
        List<Vector3>[] triNorms = new List[positions.length];
        for (int i = 0; i < positions.length; ++i)
            triNorms[i] = new ArrayList<>();
        
        for (int i = 0; i < triangles.length; i += 3) {
            int t0 = triangles[i], t1 = triangles[i+1], t2 = triangles[i+2];
            Vector3 p0 = positions[t0], p1 = positions[t1], p2 = positions[t2];
            
            Vector3 tn = p1.sub(p0).cross(p2.sub(p0));
            
            triNorms[t0].add(tn);
            triNorms[t1].add(tn);
            triNorms[t2].add(tn);
        }
        
        normals = new Vector3[positions.length];
        
        for (int i = 0; i < positions.length; ++i) {
            normals[i] = triNorms[i].stream().reduce(Vector3.ZERO, Vector3::add);
            
            if (Maths.approx0(normals[i]))
                normals[i] = Vector3.RIGHT;
            else
                normals[i].normalized();
        }
        
        setStatus(STATUS_DIRTY_VERTS_NORM);
    }
    
    /**
     * Reallocates the buffers. This deletes the current buffers (if any) in GPU 
     * memory, before recreating them.
     */
    protected void reallocateBuffers() {
        // Delete old buffers if any
        if (vao > 0)
            deleteBuffers();
        
        // Rebuffer vertices
        ByteBuffer vBuf = BufferUtils.createByteBuffer(positions.length * GraphicsManager.VERTEX_STRIDE);
        for (int i = 0; i < positions.length; ++i) {
            bufferVertexAttribute(positions[i], vBuf);
            bufferVertexAttribute(uvs[i], vBuf);
            bufferVertexAttribute(normals[i], vBuf);
        }
        vBuf.flip();

        // Regenerate vertex buffer
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vBuf, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(GraphicsManager.SHADER_ATTRIBUTE_POSITION_INDEX, GraphicsManager.NUM_POSITION_COMPONENTS_PER_VERTEX,
                                   GL11.GL_FLOAT, false, GraphicsManager.VERTEX_STRIDE, GraphicsManager.VERTEX_POSITION_OFFSET);
        GL20.glVertexAttribPointer(GraphicsManager.SHADER_ATTRIBUTE_UV_INDEX, GraphicsManager.NUM_UV_COMPONENTS_PER_VERTEX,
                                   GL11.GL_FLOAT, false, GraphicsManager.VERTEX_STRIDE, GraphicsManager.VERTEX_UV_OFFSET);
        GL20.glVertexAttribPointer(GraphicsManager.SHADER_ATTRIBUTE_NORMAL_INDEX, GraphicsManager.NUM_NORMAL_COMPONENTS_PER_VERTEX,
                                   GL11.GL_FLOAT, false, GraphicsManager.VERTEX_STRIDE, GraphicsManager.VERTEX_NORMAL_OFFSET);

        // Rebuffer indices
        IntBuffer iBuf = BufferUtils.createIntBuffer(triangles.length);
        iBuf.put(triangles);
        iBuf.flip();

        // Regenerate index buffer
        ebo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, iBuf, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        
        // Clear statuses
        clearStatus(STATUS_DIRTY_ALL | STATUS_REBUFFER);
    }
    
    /**
     * Updates the positions in the buffer. This does not delete the current buffer,
     * it only overwrites the current values in the buffer.
     */
    private void updatePositions() {
        ByteBuffer pBuf = BufferUtils.createByteBuffer(Float.BYTES * GraphicsManager.NUM_POSITION_COMPONENTS_PER_VERTEX);

        for (int i = 0; i < positions.length; ++i) {
            bufferVertexAttribute(positions[i], pBuf);
            pBuf.flip();
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, GraphicsManager.VERTEX_POSITION_OFFSET + (i * GraphicsManager.VERTEX_STRIDE), pBuf);
            pBuf.clear();
        }
    }
    
    /**
     * Updates the uvs in the buffer. This does not delete the current buffer, it
     * only overwrites the current values in the buffer.
     */
    private void updateUVs() {
        ByteBuffer uBuf = BufferUtils.createByteBuffer(Float.BYTES * GraphicsManager.NUM_UV_COMPONENTS_PER_VERTEX);

        for (int i = 0; i < positions.length; ++i) {
            bufferVertexAttribute(uvs[i], uBuf);
            uBuf.flip();
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, GraphicsManager.VERTEX_UV_OFFSET + (i * GraphicsManager.VERTEX_STRIDE), uBuf);
            uBuf.clear();
        }
    }
    
    /**
     * Updates the normals in the buffer. This does not delete the current buffer,
     * it only overwrites the current values in the buffer.
     */
    private void updateNormals() {
        ByteBuffer nBuf = BufferUtils.createByteBuffer(Float.BYTES * GraphicsManager.NUM_NORMAL_COMPONENTS_PER_VERTEX);

        for (int i = 0; i < positions.length; ++i) {
            bufferVertexAttribute(normals[i], nBuf);
            nBuf.flip();
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, GraphicsManager.VERTEX_NORMAL_OFFSET + (i * GraphicsManager.VERTEX_STRIDE), nBuf);
            nBuf.clear();
        }
    }
    
    /**
     * Updates the triangles in the buffer. This deletes and remakes the buffer.
     */
    private void updateTriangles() {
        IntBuffer iBuf = BufferUtils.createIntBuffer(triangles.length);
        iBuf.put(triangles);
        iBuf.flip();

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL15.glDeleteBuffers(ebo);
        ebo = GL15.glGenBuffers();

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, iBuf, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }
    
    /**
     * Selectively update all buffers. The exact behaviour of this method depends
     * on what is currently dirtied; a dirty number of vertices will cause the
     * buffers to be reallocated, while any other dirtied information will update
     * the current buffers rather than reallocating them.
     */
    private void updateBuffers() {
        // If number of vertices is dirty, then the buffers will need to be
        // reallocated
        if (getStatus(STATUS_DIRTY_VERTS_COUNT)) {
            reallocateBuffers();
            return;
        }
        
        // Bind VBO for updating
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        // If any attribute is dirty, update them
        if (getStatus(STATUS_DIRTY_VERTS_POS))  updatePositions();
        if (getStatus(STATUS_DIRTY_VERTS_UV))   updateUVs();
        if (getStatus(STATUS_DIRTY_VERTS_NORM)) updateNormals();

        // Unbind VBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        // If triangles are dirty, update them
        if (getStatus(STATUS_DIRTY_TRIS))
            updateTriangles();
        
        // Clear all dirty statuses
        clearStatus(STATUS_DIRTY_ALL);
    }
    
    /**
     * Deletes all current buffers. Used when the buffers need to be reallocated,
     * or when the mesh is closed. Does not bind or delete the VAO.
     */
    private void deleteBuffers() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        
        GL15.glDeleteBuffers(vbo);
        GL15.glDeleteBuffers(ebo);
    }
    
    /**
     * Renders this mesh. Also preforms any rebuffering or updating of the GL data
     * buffers. If the mesh currently has invalid parameters, it reverts back to the
     * last valid set of parameters.<p>
     * 
     * Note that any require uniforms, textures, buffers, etc. must be bound before
     * calling this method.
     * 
     * @param ivbo the instance vertex buffer object
     * @param instances the number of instances
     * @return {@code false} if the mesh had invalid attributes, {@code true}
     * otherwise
     */
    final boolean render(int ivbo, int instances) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLMesh"));
        
        // Bind the VAO, and check for validity
        GL30.glBindVertexArray(vao);
        boolean noError = areParamsValid();
        
        // If no error occured, update the old attribute variables, and 
        // update/reallocate the GL buffers
        // Otherwise, update the current attribute variables with the old ones
        if (noError) 
        {
            if (getStatus(STATUS_REBUFFER))
                reallocateBuffers();
            else
                updateBuffers();
            
            prevPos = positions;
            prevUV = uvs;
            prevNormals = normals;
            prevTri = triangles;
        } 
        else 
        {
            positions = prevPos;
            uvs = prevUV;
            normals = prevNormals;
            triangles = prevTri;
        }
        
        // Bind instance vertex buffer object
        
        // This is here because the instance vertex buffer object is per renderable,
        // not per mesh; thus, the attributes must be rebound to the VAO between
        // rendering calls
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, ivbo);
        
        GraphicsManager.vertexAttribPointerMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_INDEX, 4,
                                                  false, GraphicsManager.INSTANCE_STRIDE, GraphicsManager.INSTANCE_MODEL_MATRIX_OFFSET);
        GraphicsManager.vertexAttribPointerMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_INDEX, 4,
                                                  false, GraphicsManager.INSTANCE_STRIDE, GraphicsManager.INSTANCE_PROJECTION_VIEW_MODEL_MATRIX_OFFSET);
        
        GraphicsManager.vertexAttribDivisorMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_INDEX, 1);
        GraphicsManager.vertexAttribDivisorMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_INDEX, 1);
        
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        
        // Perform rendering operation
        GL20.glEnableVertexAttribArray(GraphicsManager.SHADER_ATTRIBUTE_POSITION_INDEX);
        GL20.glEnableVertexAttribArray(GraphicsManager.SHADER_ATTRIBUTE_UV_INDEX);
        GL20.glEnableVertexAttribArray(GraphicsManager.SHADER_ATTRIBUTE_NORMAL_INDEX);
        GraphicsManager.enableVertexAttribArrayMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_INDEX);
        GraphicsManager.enableVertexAttribArrayMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_INDEX);
        
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, triangles.length, GL11.GL_UNSIGNED_INT, 0, instances);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        
        GL20.glDisableVertexAttribArray(GraphicsManager.SHADER_ATTRIBUTE_POSITION_INDEX);
        GL20.glDisableVertexAttribArray(GraphicsManager.SHADER_ATTRIBUTE_UV_INDEX);
        GL20.glDisableVertexAttribArray(GraphicsManager.SHADER_ATTRIBUTE_NORMAL_INDEX);
        GraphicsManager.disableVertexAttribArrayMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_INDEX);
        GraphicsManager.disableVertexAttribArrayMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_INDEX);
        GL30.glBindVertexArray(0);
        
        return noError;
    }
    
    @Override
    protected boolean doClose() {
        GL30.glBindVertexArray(vao);
        
        deleteBuffers();
        
        GL30.glBindVertexArray(0);
        GL30.glDeleteVertexArrays(vao);
        
        return true;
    }
}
