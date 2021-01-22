package net.vob.core.graphics;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.Matrix;
import net.vob.util.math.Vector3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

/**
 * A special class, reserved specifically for skyboxes. This class combines the
 * functions of renderables and meshes into a single, static pipeline, with only
 * the single texture capable of being changed.
 * 
 * @author Lyn-Park
 */
class GLSkybox extends GLObject {
    private static final float[] positions =    new float[] {
                                                    0.5f, -0.5f, -0.5f,
                                                    0.5f, 0.5f, -0.5f,
                                                    0.5f, 0.5f, 0.5f,
                                                    0.5f, -0.5f, 0.5f,
                                                    -0.5f, -0.5f, 0.5f,
                                                    -0.5f, 0.5f, 0.5f,
                                                    -0.5f, 0.5f, -0.5f,
                                                    -0.5f, -0.5f, -0.5f
                                                 };
    private static final float[] uvs =          new float[] {
                                                    0.5f, -0.5f, -0.5f,
                                                    0.5f, 0.5f, -0.5f,
                                                    0.5f, 0.5f, 0.5f,
                                                    0.5f, -0.5f, 0.5f,
                                                    -0.5f, -0.5f, 0.5f,
                                                    -0.5f, 0.5f, 0.5f,
                                                    -0.5f, 0.5f, -0.5f,
                                                    -0.5f, -0.5f, -0.5f
                                                 };
    private static final int[] triangles =      new int[] {
                                                     0, 2, 1, 0, 3, 2,
                                                     4, 6, 5, 4, 7, 6,
                                                     1, 2, 5, 1, 5, 6,
                                                     0, 4, 3, 0, 7, 4,
                                                     2, 3, 4, 2, 4, 5,
                                                     0, 1, 6, 0, 6, 7
                                                 };
    
    int vao, vbo, ivbo, ebo;
    
    GLTextureCubemap tex;
    
    private static final FloatBuffer BUFFER = GraphicsManager.getInstanceMatrixBuffer(1);
    
    GLSkybox(GLTextureCubemap tex) {
        if (tex.unit != GL13.GL_TEXTURE0)
            throw new IllegalArgumentException(LocaleUtils.format("GLSkybox.InvalidTextureUnit"));
        
        this.tex = tex;
    }
    
    @Override
    void init() throws Exception {
        vao = GL30.glGenVertexArrays();
        
        GL30.glBindVertexArray(vao);
        
        // Buffer vertices
        ByteBuffer vBuf = BufferUtils.createByteBuffer(8 * GraphicsManager.SEMI_VERTEX_STRIDE);
        for (int i = 0; i < 24; i+=3) {
            vBuf.putFloat(positions[i]);
            vBuf.putFloat(positions[i+1]);
            vBuf.putFloat(positions[i+2]);
            vBuf.putFloat(uvs[i]);
            vBuf.putFloat(uvs[i+1]);
            vBuf.putFloat(uvs[i+2]);
        }
        vBuf.flip();

        // Generate vertex buffer
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vBuf, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(GraphicsManager.SHADER_ATTRIBUTE_POSITION_INDEX, GraphicsManager.NUM_POSITION_COMPONENTS_PER_VERTEX,
                                   GL11.GL_FLOAT, false, GraphicsManager.SEMI_VERTEX_STRIDE, GraphicsManager.VERTEX_POSITION_OFFSET);
        GL20.glVertexAttribPointer(GraphicsManager.SHADER_ATTRIBUTE_UV_INDEX, GraphicsManager.NUM_UV_COMPONENTS_PER_VERTEX,
                                   GL11.GL_FLOAT, false, GraphicsManager.SEMI_VERTEX_STRIDE, GraphicsManager.VERTEX_UV_OFFSET);

        // Buffer instance attibutes, and generate the GL buffer
        bufferSkyboxMatrices();
        
        ivbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, ivbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, BUFFER, GL15.GL_DYNAMIC_DRAW);
        
        GraphicsManager.vertexAttribPointerMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_INDEX, GraphicsManager.NUM_MODEL_MATRIX_ROWS_PER_INSTANCE,
                                                  false, GraphicsManager.INSTANCE_STRIDE, GraphicsManager.INSTANCE_MODEL_MATRIX_OFFSET);
        GraphicsManager.vertexAttribPointerMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_INDEX, GraphicsManager.NUM_PROJECTION_VIEW_MODEL_MATRIX_ROWS_PER_INSTANCE,
                                                  false, GraphicsManager.INSTANCE_STRIDE, GraphicsManager.INSTANCE_PROJECTION_VIEW_MODEL_MATRIX_OFFSET);
        
        GraphicsManager.vertexAttribDivisorMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_INDEX, 1);
        GraphicsManager.vertexAttribDivisorMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_INDEX, 1);
        
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        
        // Buffer indices
        IntBuffer iBuf = BufferUtils.createIntBuffer(triangles.length);
        iBuf.put(triangles);
        iBuf.flip();

        // Generate index buffer
        ebo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, iBuf, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        
        GL30.glBindVertexArray(0);
    }
    
    void render() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL30.glBindVertexArray(vao);
        
        if (GraphicsManager.getStatus(GraphicsManager.STATUS_MATRICES_CHANGED)) {
            bufferSkyboxMatrices();
            
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, ivbo);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, GraphicsManager.INSTANCE_MODEL_MATRIX_OFFSET, BUFFER);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
        
        GLShaderProgram.DEFAULT_CUBE.bind();
        tex.bind();
        
        GL20.glEnableVertexAttribArray(GraphicsManager.SHADER_ATTRIBUTE_POSITION_INDEX);
        GL20.glEnableVertexAttribArray(GraphicsManager.SHADER_ATTRIBUTE_UV_INDEX);
        GraphicsManager.enableVertexAttribArrayMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_INDEX);
        GraphicsManager.enableVertexAttribArrayMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_INDEX);
        
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, triangles.length, GL11.GL_UNSIGNED_INT, 0, 1);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        
        GL20.glDisableVertexAttribArray(GraphicsManager.SHADER_ATTRIBUTE_POSITION_INDEX);
        GL20.glDisableVertexAttribArray(GraphicsManager.SHADER_ATTRIBUTE_UV_INDEX);
        GraphicsManager.disableVertexAttribArrayMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_INDEX);
        GraphicsManager.disableVertexAttribArrayMatrix(GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_INDEX);
        GL30.glBindVertexArray(0);
        
        tex.unbind();
        GLShaderProgram.DEFAULT_CUBE.unbind();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
    
    private static void bufferSkyboxMatrices() {
        BUFFER.clear();
        
        double s = (GraphicsEngine.windowOptions.getZFarDist() + GraphicsEngine.windowOptions.getZNearDist()) / 2d;
        Matrix model = Matrix.getScalingMatrix(new Vector3(s, s, s));
        
        Matrix pvm = GraphicsManager.PROJ_MATRIX
                                    .mul(Matrix.getRotationMatrix(GraphicsManager.VIEW_TRANSFORM.getRotation().conjugate()))
                                    .mul(model);
        
        model.writeToBuffer(BUFFER, true);
        pvm.writeToBuffer(BUFFER, true);
        BUFFER.flip();
    }

    @Override
    protected boolean doClose() {
        GL30.glBindVertexArray(vao);
        
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        
        GL15.glDeleteBuffers(vbo);
        GL15.glDeleteBuffers(ivbo);
        GL15.glDeleteBuffers(ebo);
        
        GL30.glBindVertexArray(0);
        GL30.glDeleteVertexArrays(vao);
        
        return true;
    }
    
}
