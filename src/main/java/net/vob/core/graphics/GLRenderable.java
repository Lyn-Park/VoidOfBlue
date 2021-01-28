package net.vob.core.graphics;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Iterator;
import net.vob.util.ArrayTree;
import net.vob.util.Closable;
import net.vob.util.Registry;
import net.vob.util.Tree;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.AffineTransformation;
import net.vob.util.math.AffineTransformationImpl;
import net.vob.util.math.Matrix;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;

/**
 * Container class for a mesh, array of textures, an affine transform, and a reference
 * to the shader program this renderable is using. Each renderable can take multiple
 * affine transformations; the number of these transforms indicates the number of
 * instances the renderable has. Renderables also contain references to their current
 * skeleton buffer objects, if they have one.<p>
 * 
 * When this renderable is rendered using {@link render(GLShaderProgram)}, the mesh,
 * skeleton, and textures are checked for validity; if the mesh is invalid, then it is
 * reverted to a previous valid state; if the skeleton/weights are invalid, they are
 * removed; if a texture is invalid, then that texture is removed from the renderable
 * (as a special case, an invalid diffuse texture will instead be replaced with the
 * default texture). Otherwise, the textures are bound and the mesh is rendered; the
 * affine transforms for each instance are also combined with the global projection-view
 * matrix to form a complete projection-view-model matrix, and buffered.<p>
 * 
 * Skeletons are buffered to a Shader Storage Buffer Object (SSBO) such that each bone in
 * the skeleton is in <i>model</i> space rather than relative space (this means each bone
 * is combined with its parent prior to buffering). The weights are also buffered to a
 * separate SSBO in row-major order, along with the size of the weight matrix (each row of
 * the matrix corresponds to a vertex, and each column corresponds to a skeleton bone) Thus,
 * the formats of the buffer objects in the shader are expected to be:
 * <blockquote><pre>
 *      <b>{@code skeleton}</b> {@code : mat4[] bones;}
 *      <b>{@code weights}</b> {@code : uint rows;
 *                  uint columns;
 *                  float[] weights;}
 * </pre></blockquote><p>
 * 
 * Once the skeleton and weights are bound, it is the responsibility of the shader to
 * handle the skeleton and vertices as required. If no skeleton or weight is bound to this
 * renderable, then the buffer objects are instead bound to
 * {@link GraphicsManager#SHADER_STORAGE_BUFFER_OBJECT_ZERO}; this allows the weights to
 * be used for checking if a skeleton is present, as it will have a row count of 0.<p>
 * 
 * Note that the shader program reference is not used for rendering; this is due to the
 * possibility that the program is not in a state conducive to rendering, and thus the
 * default shader program is currently bound instead. The program reference is merely
 * used by outside classes to locate this renderable in the rendering map.
 * 
 * @author Lyn-Park
 */
final class GLRenderable extends Closable {
    static final Registry<GLRenderable> REGISTRY = new Registry<>();
    
    private static int skeletonSSBO = 0, weightSSBO = 0;
    
    Tree<AffineTransformation, ?> skeleton = null;
    Matrix weights = null;
    
    AffineTransformation[] instanceTransforms;
    boolean instanceNumDirty = true;
    private int ivbo;
    
    GLMesh mesh = null;
    GLTexture[] textures = new GLTexture[GraphicsManager.MAX_COMBINED_TEXTURE_UNITS];
    GLShaderProgram program = null;
    
    /**
     * Constructs a new renderable, with space for the given number of instances
     */
    GLRenderable(int numInstances) {
        if (numInstances < 1)
            throw new IllegalArgumentException(LocaleUtils.format("GLRenderable._cinit_.NoAffineTransforms"));
        
        this.instanceTransforms = new AffineTransformation[numInstances];
        for (int i = 0; i < numInstances; ++i)
            this.instanceTransforms[i] = AffineTransformationImpl.IDENTITY;
    }
    
    /**
     * Constructs a new renderable, with a set of given instance transformations.
     */
    GLRenderable(AffineTransformation... transforms) {
        if (transforms.length == 0)
            throw new IllegalArgumentException(LocaleUtils.format("GLRenderable._cinit_.NoAffineTransforms"));
        
        this.instanceTransforms = new AffineTransformation[transforms.length];
        for (int i = 0; i < transforms.length; ++i)
            this.instanceTransforms[i] = transforms[i].getAsUnmodifiable(true);
    }
    
    /**
     * Copies a renderable; this includes copying the mesh and texture references.
     * Does not copy the program assignment or the instance transforms.
     */
    GLRenderable(GLRenderable r) {
        this(AffineTransformationImpl.IDENTITY);
        
        mesh = r.mesh;
        for (int i = 0; i < textures.length; ++i)
            textures[i] = r.textures[i];
    }
    
    /**
     * Checks the skeleton parameters for validity. A return value of {@code false}
     * indicates they are not valid.
     * @return 
     */
    private boolean checkSkeleton() {
        return skeleton.size() == weights.getNumColumns() &&
               weights.getNumRows() == mesh.getNumVertices();
    }
    
    /**
     * Rebuffers the skeleton uniform buffers. Deletes the old buffers, if they
     * exist.
     */
    void rebufferSkeletonBuffers() {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        if (skeletonSSBO > 0) {
            closeSkeletonSSBO();
        }
        
        FloatBuffer mBuf = BufferUtils.createFloatBuffer(skeleton.size() * 16);
        
        Tree<Matrix, ?> baseMatrixTree = new ArrayTree<>(skeleton.map((t) -> t.getTransformationMatrix()));
        Iterator<? extends Tree<Matrix, ?>> bmtIt = baseMatrixTree.preOrderWalk();
        
        Tree<Matrix, ?> bone = bmtIt.next();
        bone.getValue().writeToFloatBuffer(mBuf, true);
        
        while (bmtIt.hasNext()) {
            bone = bmtIt.next();
            Matrix boneMat = bone.getValue().mul(bone.getParent().getValue());
            
            boneMat.writeToFloatBuffer(mBuf, true);
            bone.setValue(boneMat);
        }
        mBuf.flip();
        
        ByteBuffer wBuf = BufferUtils.createByteBuffer((2 * Integer.BYTES) + (weights.getElements().length * Float.BYTES));
        wBuf.putInt(weights.getNumRows());
        wBuf.putInt(weights.getNumColumns());
        weights.writeToFloatBuffer(wBuf, false);
        wBuf.flip();
        
        skeletonSSBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, skeletonSSBO);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, mBuf, GL15.GL_DYNAMIC_DRAW);
        
        weightSSBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, weightSSBO);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, wBuf, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    /**
     * Updates the transformation matrices for the skeleton. Does not delete the
     * current buffer, it only overwrites the current values in the buffer.
     */
    private void updateSkeletonBuffer() {
        Iterator<? extends Tree<AffineTransformation, ?>> skeletonIt = skeleton.preOrderWalk();
        int i = 0;
        
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, skeletonSSBO);
        
        while (skeletonIt.hasNext()) {
            Tree<AffineTransformation, ?> bone = skeletonIt.next();
            
            if (bone.getValue().isDirty()) {
                int j = i;
                
                Tree<Matrix, ?> matrixTree = bone.map((transform) -> transform.getTransformationMatrix());
                Iterator<? extends Tree<Matrix, ?>> matrixIt = matrixTree.preOrderWalk();
                matrixIt.next();
                
                while (matrixIt.hasNext()) {
                    Tree<Matrix, ?> next = matrixIt.next();
                    next.setValue(next.getValue().mul(next.getParent().getValue()));
                    
                    skeletonIt.next();
                    ++i;
                }
                
                matrixIt = matrixTree.preOrderWalk();
                FloatBuffer buf = BufferUtils.createFloatBuffer(bone.size() * 16);
                while (matrixIt.hasNext())
                    matrixIt.next().getValue().writeToFloatBuffer(buf, true);
                buf.flip();
                
                GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, j * 16 * Float.BYTES, buf);
            }
            
            ++i;
        }
        
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    /**
     * Updates the transformation matrices for the given instance in the buffer.
     * Does not delete the current buffer, it only overwrites the current values
     * in the buffer.
     * @param transform the transform to write to the buffer
     * @param instanceID the instance ID to put the transform in
     */
    private void updateInstanceTransform(AffineTransformation transform, int instanceID) {
        Matrix model;
        
        transform.getLock().lock();
        try {
            model = transform.getTransformationMatrix();
        } finally {
            transform.getLock().unlock();
        }
        
        Matrix projectionViewModel = GraphicsManager.PROJ_VIEW_MATRIX.mul(model);
        
        FloatBuffer buf = GraphicsManager.getInstanceMatrixBuffer(1);
        
        model.writeToFloatBuffer(buf, true);
        projectionViewModel.writeToFloatBuffer(buf, true);
        buf.flip();
        
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, ivbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, GraphicsManager.INSTANCE_MODEL_MATRIX_OFFSET + (instanceID * GraphicsManager.INSTANCE_STRIDE), buf);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
    
    /**
     * Updates the projection-view-model matrix for all instances in the buffer.
     * Does not delete the current buffer, it only overwrites the current values
     * in the buffer.
     */
    private void updateProjViewMatrices() {
        FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, ivbo);
        
        for (int i = 0; i < instanceTransforms.length; ++i) {
            Matrix model;

            instanceTransforms[i].getLock().lock();
            try {
                model = instanceTransforms[i].getTransformationMatrix();
            } finally {
                instanceTransforms[i].getLock().unlock();
            }

            Matrix projectionViewModel = GraphicsManager.PROJ_VIEW_MATRIX.mul(model);
            
            projectionViewModel.writeToFloatBuffer(buf, true);
            buf.flip();
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, GraphicsManager.INSTANCE_PROJECTION_VIEW_MODEL_MATRIX_OFFSET + (i * GraphicsManager.INSTANCE_STRIDE), buf);
            buf.clear();
        }
        
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
    
    /**
     * Rebuffers the instance buffer. Deletes the old buffer, if one exists.
     */
    private void rebufferInstanceBuffer() {
        if (ivbo > 0) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glDeleteBuffers(ivbo);
        }
        
        FloatBuffer mBuf = GraphicsManager.getInstanceMatrixBuffer(instanceTransforms.length);
        
        if (instanceTransforms == null) {
            Matrix I = Matrix.identity(4);
            for (int i = 0; i < instanceTransforms.length; ++i) {
                I.writeToFloatBuffer(mBuf, true);
                GraphicsManager.PROJ_VIEW_MATRIX.writeToFloatBuffer(mBuf, true);
            }
        }
        else
        {
            for (int i = 0; i < instanceTransforms.length; ++i) {
                Matrix model;
                instanceTransforms[i].getLock().lock();
                try {
                    model = instanceTransforms[i].getTransformationMatrix();
                } finally {
                    instanceTransforms[i].getLock().unlock();
                }

                Matrix projectionViewModel = GraphicsManager.PROJ_VIEW_MATRIX.mul(model);

                model.writeToFloatBuffer(mBuf, true);
                projectionViewModel.writeToFloatBuffer(mBuf, true);
            }
        }
        mBuf.flip();
        
        ivbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, ivbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mBuf, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        
        instanceNumDirty = false;
    }
    
    /**
     * Updates the instance buffer. This does not destroy the buffer, it only
     * overwrites the data in it. It also only overwrites data if the data has changed.
     */
    private void updateInstanceBuffer() {
        for (int i = 0; i < instanceTransforms.length; ++i)
            if (instanceTransforms[i].isDirty())
                updateInstanceTransform(instanceTransforms[i], i);
        
        if (GraphicsManager.getStatus(GraphicsManager.STATUS_MATRICES_CHANGED))
            updateProjViewMatrices();
    }

    /**
     * If the mesh is not closed or null, renders this renderable using an already
     * bound shader program (the bound program is usually, <i>but not always</i>, the
     * program held in {@link program}, hence why it isn't used here).
     * @param program the program to use for the rendering
     * @return {@code false} if the mesh had invalid parameters and reverted back
     * to some previous valid parameters, {@code true} otherwise
     */
    boolean render(GLShaderProgram program) {
        if (mesh != null) {
            // set mesh to null if closed, and return immediately
            if (mesh.isClosed())
                mesh = null;
            
            else {
                if (skeleton != null && !checkSkeleton()) {
                    skeleton = null;
                    weights = null;
                    closeSkeletonSSBO();
                }
                
                if (skeleton != null) {
                    updateSkeletonBuffer();
                    program.bindShaderStorage(skeletonSSBO, GraphicsManager.SHADER_UNIFORM_SKELETON_TRANSFORMS_NAME);
                    program.bindShaderStorage(weightSSBO, GraphicsManager.SHADER_UNIFORM_SKELETON_WEIGHTS_NAME);
                } else {
                    program.bindShaderStorage(GraphicsManager.SHADER_STORAGE_BUFFER_OBJECT_ZERO, GraphicsManager.SHADER_UNIFORM_SKELETON_TRANSFORMS_NAME);
                    program.bindShaderStorage(GraphicsManager.SHADER_STORAGE_BUFFER_OBJECT_ZERO, GraphicsManager.SHADER_UNIFORM_SKELETON_WEIGHTS_NAME);
                }
                
                // update or rebuffer the instance buffer
                if (instanceNumDirty) rebufferInstanceBuffer();
                else                  updateInstanceBuffer();
                
                // bind textures, skipping any null or closed textures
                // if unit 0 would thus have no texture (or if the texture doesn't
                // match the program), bind the default texture
                for (int i = 0; i < textures.length; ++i) {
                    if (textures[i] == null ||
                        textures[i].isClosed() ||
                        !program.isCompatible(textures[i]))
                    {
                        if (i == 0)
                            textures[i] = program.getStatus(GLShaderProgram.STATUS_MODE) ?
                                          GLTextureCubemap.DEFAULT :
                                          GLTexture2D.DEFAULT;
                        
                        else
                            textures[i] = null;
                    }

                    if (textures[i] != null) textures[i].bind();
                }

                boolean e = mesh.render(ivbo, instanceTransforms.length);

                for (GLTexture texture : textures)
                    if (texture != null) texture.unbind();

                program.bindShaderStorage(0, GraphicsManager.SHADER_UNIFORM_SKELETON_TRANSFORMS_NAME);
                program.bindShaderStorage(0, GraphicsManager.SHADER_UNIFORM_SKELETON_WEIGHTS_NAME);
                
                return e;
            }
        }
        
        return true;
    }

    @Override
    protected boolean doClose() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glDeleteBuffers(ivbo);
        closeSkeletonSSBO();
        return true;
    }
    
    private void closeSkeletonSSBO() {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        GL15.glDeleteBuffers(skeletonSSBO);
        GL15.glDeleteBuffers(weightSSBO);
    }
}
