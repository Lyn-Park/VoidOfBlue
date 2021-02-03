package net.vob.core.graphics;

import com.google.common.collect.Sets;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.vob.util.Registry;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.Matrix;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

/**
 * A class containing a reference to a complete shader program on the GPU. Starts off
 * with the default shader stages (see {@linkplain GLShader} for more info) attached,
 * and they can be replaced (or expanded upon with a geometry shader, which is optional) 
 * at any time as long as the shader program is not read-only. Once rendering is
 * attempted, the program is linked and validated.<p>
 * 
 * Several default shader programs exist, and are read-only programs that use the 
 * appropriate default shader stages.
 * 
 * @author Lyn-Park
 */
final class GLShaderProgram extends GLObject {
    static final Registry<GLShaderProgram> REGISTRY = new Registry<>();
    
    static final GLShaderProgram DEFAULT_2D = new GLShaderProgram(GLShader.DEFAULT_VERT_2D, null, GLShader.DEFAULT_FRAG_2D, false);
    static final GLShaderProgram DEFAULT_CUBE = new GLShaderProgram(GLShader.DEFAULT_VERT_CUBE, null, GLShader.DEFAULT_FRAG_CUBE, true);
    static final GLShaderProgram DEFAULT_UI = new GLShaderProgram(GLShader.DEFAULT_VERT_UI, null, GLShader.DEFAULT_FRAG_UI, false);
    static final GLShaderProgram SKYBOX = new GLShaderProgram(GLShader.SKYBOX_VERT, null, GLShader.SKYBOX_FRAG, true);
    
    static Set<GLShaderProgram> getDefaults() {
        return Sets.newHashSet(DEFAULT_2D, DEFAULT_CUBE, DEFAULT_UI, SKYBOX);
    }
    
    static final int STATUS_MODE = 1;
    static final int STATUS_LINKED = 2;
    static final int STATUS_LINK_FAILED = 4;
    static final int STATUS_READONLY = 8;
    
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);
    
    private int prog;
    private GLShader vert = null, geom = null, frag = null;
    private byte status = 0;
    
    final Map<String, Integer> PROGRAM_RESOURCES = new HashMap<>();
    
    @Override
    void init() {
        prog = GL20.glCreateProgram();
        
        if (vert != null) GL20.glAttachShader(prog, vert.shd);
        if (geom != null) GL20.glAttachShader(prog, geom.shd);
        if (frag != null) GL20.glAttachShader(prog, frag.shd);
    }
    
    private GLShaderProgram(GLShader vert, GLShader geom, GLShader frag, boolean mode) {
        this.vert = vert;
        this.geom = geom;
        this.frag = frag;
        
        if (mode) setStatus(STATUS_MODE);
    }
    
    GLShaderProgram(boolean mode) {
        if (mode) setStatus(STATUS_MODE);
    }
    
    /**
     * @param statusCode the status code(s) to get the status of
     * @return {@code true} if any of the input status codes have a status of
     * {@code true}, {@code false} otherwise
     */
    boolean getStatus(int statusCode) {
        return (status & statusCode) > 0;
    }
    
    private void setStatus(int statusCode) {
        status |= statusCode;
    }
    
    private void clearStatus(int statusCode) {
        status &= ~statusCode;
    }
    
    /**
     * @param texture the texture to check compatibility for
     * @return {@code true} if the given texture is compatible with this shader based
     * on the shader mode, {@code false} otherwise
     */
    boolean isCompatible(GLTexture texture) {
        return getStatus(STATUS_MODE) ^ (texture instanceof GLTexture2D);
    }
    
    /**
     * Sets this shader program to be read-only, rejecting any changes to it attempted.
     */
    void setAsReadonly() {
        if(isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLShaderProgram"));
        
        setStatus(STATUS_READONLY);
    }
    
    boolean isReadonly() {
        return getStatus(STATUS_READONLY);
    }
    
    /**
     * Attaches the given shader to this shader program.
     * @param shader the shader to attach
     * @throws IllegalStateException if this shader program is read-only or closed, or
     * the given shader is closed
     * @throws IllegalArgumentException if the given shader has an invalid type
     */
    void attachShader(GLShader shader) {
        if (getStatus(STATUS_READONLY))
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "GLShaderProgram"));
        if (shader.isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLShader"));
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLShaderProgram"));
        
        switch(shader.type) {
            case GL20.GL_VERTEX_SHADER:
                if (shader.equals(vert))
                    return;
                else if (vert != null)
                    GL20.glDetachShader(prog, vert.shd);
                
                vert = shader;
                GL20.glAttachShader(prog, vert.shd);
                clearStatus(STATUS_LINKED | STATUS_LINK_FAILED);
                break;
                    
            case GL32.GL_GEOMETRY_SHADER:
                if (shader.equals(geom))
                    return;
                else if (geom != null)
                    GL20.glDetachShader(prog, geom.shd);
                
                geom = shader;
                GL20.glAttachShader(prog, geom.shd);
                clearStatus(STATUS_LINKED | STATUS_LINK_FAILED);
                break;
                
            case GL20.GL_FRAGMENT_SHADER:
                if (shader.equals(frag))
                    return;
                else if (frag != null)
                    GL20.glDetachShader(prog, frag.shd);
                
                frag = shader;
                GL20.glAttachShader(prog, frag.shd);
                clearStatus(STATUS_LINKED | STATUS_LINK_FAILED);
                break;
                
            default:
                throw new IllegalArgumentException(LocaleUtils.format("GLShaderProgram.attachShader.UnknownShaderType", shader.type));
        }
    }
    
    /**
     * Links and validates this shader program. Sets the {@link STATUS_LINKED} and
     * {@link STATUS_LINK_FAILED} status codes, as appropriate.
     * @return a string containing the error message, or {@code null} if no such
     * message occurred
     */
    String linkAndValidate() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLShaderProgram"));
        
        GL20.glBindAttribLocation(prog, GraphicsManager.SHADER_ATTRIBUTE_POSITION_INDEX, GraphicsManager.SHADER_ATTRIBUTE_POSITION_NAME);
        GL20.glBindAttribLocation(prog, GraphicsManager.SHADER_ATTRIBUTE_UV_INDEX, GraphicsManager.SHADER_ATTRIBUTE_UV_NAME);
        GL20.glBindAttribLocation(prog, GraphicsManager.SHADER_ATTRIBUTE_NORMAL_INDEX, GraphicsManager.SHADER_ATTRIBUTE_NORMAL_NAME);
        GL20.glBindAttribLocation(prog, GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_INDEX, GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_NAME);
        GL20.glBindAttribLocation(prog, GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_INDEX, GraphicsManager.SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_NAME);
        
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            setStatus(STATUS_LINK_FAILED);
            return LocaleUtils.format("GLShaderProgram.linkAndValidate.LinkingError", GL20.glGetProgramInfoLog(prog));
        }
        
        GL20.glValidateProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            setStatus(STATUS_LINK_FAILED);
            return LocaleUtils.format("GLShaderProgram.linkAndValidate.ValidationError", GL20.glGetProgramInfoLog(prog));
        }
        
        int numUniforms = GL43.glGetProgramInterfacei(prog, GL43.GL_UNIFORM, GL43.GL_ACTIVE_RESOURCES);
        int numShaderStorageBlocks = GL43.glGetProgramInterfacei(prog, GL43.GL_SHADER_STORAGE_BLOCK, GL43.GL_ACTIVE_RESOURCES);
        
        for (int i = 0; i < numUniforms; ++i)
            PROGRAM_RESOURCES.put(GL43.glGetProgramResourceName(prog, GL43.GL_UNIFORM, i), i);
        for (int i = 0; i < numShaderStorageBlocks; ++i) {
            GL43.glShaderStorageBlockBinding(prog, i, i);
            PROGRAM_RESOURCES.put(GL43.glGetProgramResourceName(prog, GL43.GL_SHADER_STORAGE_BLOCK, i), i);
        }
        
        setStatus(STATUS_LINKED);
        return null;
    }
    
    /**
     * Binds this shader program.
     */
    void bind() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLShaderProgram"));
        
        GL20.glUseProgram(prog);
    }
    
    /**
     * Unbinds this shader program.
     */
    void unbind() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLShaderProgram"));
        
        GL20.glUseProgram(0);
    }
    
    /**
     * Uploads a vector of 3 values to the given uniform. Note that this program 
     * must be bound when this method is invoked.
     * 
     * @param u the first value
     * @param v the second value
     * @param w the third value
     * @param name the name of the uniform
     */
    void uniform3ui(int u, int v, int w, String name) {
        if (PROGRAM_RESOURCES.containsKey(name))
            GL30.glUniform3ui(PROGRAM_RESOURCES.get(name), u, v, w);
    }
    
    /**
     * Uploads a 4x4 matrix to the given uniform. Note that this program must be
     * bound when this method is invoked.
     * 
     * @param mat the {@link Matrix} to pass to the shader uniform
     * @param name the name of the uniform
     */
    void uniformMatrix4(Matrix mat, String name) {
        if (PROGRAM_RESOURCES.containsKey(name)) {
            mat.writeToFloatBuffer(MATRIX_BUFFER, false);
            MATRIX_BUFFER.flip();
            GL20.glUniformMatrix4fv(PROGRAM_RESOURCES.get(name), true, MATRIX_BUFFER);
            MATRIX_BUFFER.clear();
        }
    }
    
    /**
     * Binds the given Shader Storage Buffer Object to the given named uniform block 
     * in the program. Note that this program must be bound when this method is
     * invoked.
     * 
     * @param ssbo the index of the SSBO to bind
     * @param name the name of the uniform block
     */
    void bindShaderStorage(int ssbo, String name) {
        if (PROGRAM_RESOURCES.containsKey(name))
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, PROGRAM_RESOURCES.get(name), ssbo);
    }
    
    @Override
    protected boolean doClose() {
        GL20.glDeleteProgram(prog);
        
        return true;
    }
}
