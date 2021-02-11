package net.vob.core.graphics;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.Objects;
import net.vob.util.ArrayTree;
import net.vob.util.Registry;
import net.vob.util.Tree;
import net.vob.util.Trees;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.AffineTransformation;
import net.vob.util.math.Maths;
import net.vob.util.math.Matrix;
import net.vob.util.math.Vector;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;

public class GLSkeleton extends GLObject {
    static final Registry<GLSkeleton> REGISTRY = new Registry<>();
    
    private final Tree<? extends AffineTransformation, ?> skeleton;
    private final Matrix weights;
    
    private int skeletonSSBO = 0, weightSSBO = 0;
    
    public GLSkeleton(Tree<? extends AffineTransformation, ?> skeleton, Matrix weights) {
        if (skeleton.size() != weights.getNumColumns())
            throw new IllegalArgumentException(LocaleUtils.format("global.Math.IllegalMatrixColumnNumber", skeleton.size(), weights.getNumColumns()));
        
        for (int r = 0 ; r < weights.getNumRows(); ++r) {
            double magnitude = 0;
            
            for (int c = 0; c < weights.getNumColumns(); ++c)
                magnitude += weights.getElement(r, c);
            
            if (Maths.approx0(magnitude))
                throw new IllegalArgumentException(LocaleUtils.format("global.Math.DivideByZero"));
            
            for (int c = 0; c < weights.getNumColumns(); ++c)
                weights.setElement(r, c, weights.getElement(r, c) / magnitude);
        }
        
        this.skeleton = Trees.unmodifiableTree(skeleton);
        this.weights = weights;
        this.weights.immutable();
    }
    
    int getExpectedNumMeshVertices() {
        return weights.getNumRows();
    }

    @Override
    protected boolean doClose() {
        closeSkeletonSSBO();
        return true;
    }
    
    @Override
    void init() {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        
        FloatBuffer mBuf = BufferUtils.createFloatBuffer(skeleton.size() * 16);
        
        Tree<Matrix, ?> baseMatrixTree = new ArrayTree<>(skeleton.map((t) -> t.getTransformationMatrix(0)));
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
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, wBuf, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    /**
     * Updates the transformation matrices for the skeleton. Does not delete the
     * current buffer, it only overwrites the current values in the buffer.
     */
    void updateTransforms() {
        Iterator<? extends Tree<? extends AffineTransformation, ?>> skeletonIt = skeleton.preOrderWalk();
        int i = 0;
        
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, skeletonSSBO);
        
        while (skeletonIt.hasNext()) {
            Tree<? extends AffineTransformation, ?> bone = skeletonIt.next();
            
            if (bone.getValue().isDirty()) {
                int j = i;
                
                Tree<Matrix, ?> boneTree = bone.map((transform) -> transform.getTransformationMatrix(0));
                Iterator<? extends Tree<Matrix, ?>> boneIt = boneTree.preOrderWalk();
                boneIt.next();
                
                while (boneIt.hasNext()) {
                    Tree<Matrix, ?> next = boneIt.next();
                    next.setValue(next.getValue().mul(next.getParent().getValue()));
                    
                    skeletonIt.next();
                    ++i;
                }
                
                boneIt = boneTree.preOrderWalk();
                FloatBuffer buf = BufferUtils.createFloatBuffer(bone.size() * 16);
                while (boneIt.hasNext())
                    boneIt.next().getValue().writeToFloatBuffer(buf, true);
                buf.flip();
                
                GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, j * 16 * Float.BYTES, buf);
            }
            
            ++i;
        }
        
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    /**
     * Binds this skeleton to the given program.
     * @param program 
     */
    void bind(GLShaderProgram program) {
        program.bindShaderStorage(skeletonSSBO, GraphicsManager.SHADER_UNIFORM_SKELETON_TRANSFORMS_NAME);
        program.bindShaderStorage(weightSSBO, GraphicsManager.SHADER_UNIFORM_SKELETON_WEIGHTS_NAME);
    }
    
    /**
     * Unbinds a skeleton from the given program.
     * @param program 
     */
    static void unbind(GLShaderProgram program) {
        program.bindShaderStorage(0, GraphicsManager.SHADER_UNIFORM_SKELETON_TRANSFORMS_NAME);
        program.bindShaderStorage(0, GraphicsManager.SHADER_UNIFORM_SKELETON_WEIGHTS_NAME);
    }
    
    /**
     * Binds an 'empty' skeleton to the given program. This simply binds a buffer containing
     * a single 0 integer value to both the skeleton and weight SSBOs.
     * @param program 
     */
    static void bindEmpty(GLShaderProgram program) {
        program.bindShaderStorage(GraphicsManager.SHADER_STORAGE_BUFFER_OBJECT_ZERO, GraphicsManager.SHADER_UNIFORM_SKELETON_TRANSFORMS_NAME);
        program.bindShaderStorage(GraphicsManager.SHADER_STORAGE_BUFFER_OBJECT_ZERO, GraphicsManager.SHADER_UNIFORM_SKELETON_WEIGHTS_NAME);
    }
    
    private void closeSkeletonSSBO() {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        GL15.glDeleteBuffers(skeletonSSBO);
        GL15.glDeleteBuffers(weightSSBO);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof GLSkeleton)) return false;
        
        GLSkeleton s = (GLSkeleton)o;
        
        return skeleton.equals(s.skeleton) && weights.equals(s.weights);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 11 * hash + Objects.hashCode(this.skeleton);
        hash = 11 * hash + Objects.hashCode(this.weights);
        return hash;
    }
}
