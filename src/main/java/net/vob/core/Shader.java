package net.vob.core;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.vob.core.graphics.GraphicsEngine;
import net.vob.util.Closable;
import net.vob.util.Identity;
import net.vob.util.logging.LocaleUtils;

/**
 * Container class for a graphical shader program. This is kept distinct from the
 * internal shader program objects in the graphics engine to promote decoupling between
 * the internal graphical state and the external program, as well as to prevent confusion.
 */
public final class Shader extends Closable {
    private final Identity vertID, geomID, fragID;
    
    private Future<Integer> progID = null;
    final boolean mode;
    
    public static final Shader DEFAULT_2D, DEFAULT_CUBE, DEFAULT_UI;
    
    static {
        GraphicsEngine.MESSAGE_LOCK.lock();
        
        try {
            DEFAULT_2D = new Shader(GraphicsEngine.msgShaderProgramSelectDefault2D(), GraphicsEngine.DEFAULT_SHADER_2D_ID, GraphicsEngine.DEFAULT_SHADER_2D_ID, false);
            DEFAULT_CUBE = new Shader(GraphicsEngine.msgShaderProgramSelectDefaultCube(), GraphicsEngine.DEFAULT_SHADER_CUBE_ID, GraphicsEngine.DEFAULT_SHADER_CUBE_ID, true);
            DEFAULT_UI = new Shader(GraphicsEngine.msgShaderProgramSelectDefaultUI(), GraphicsEngine.DEFAULT_SHADER_UI_ID, GraphicsEngine.DEFAULT_SHADER_UI_ID, false);
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
    }
    
    private Shader(Future<Integer> progID, Identity vertID, Identity fragID, boolean mode) {
        this.progID = progID;
        this.mode = mode;
        
        this.vertID = vertID;
        this.geomID = null;
        this.fragID = fragID;
    }
            
    /**
     * Instantiates a shader program with the given parameters. Note that the
     * {@link Identity} parameters are all
     * {@linkplain Identity#partial(java.lang.String...) partially completed}
     * with the single parameter {@code "shader"} before they are assigned to this
     * program, and that this constructor doesn't assign the new program a geometry
     * shader.
     * 
     * @param vertID the {@code Identity} of the vertex shader
     * @param fragID the {@code Identity} of the fragment shader
     * @param mode the mode of the shader; set this to {@code true} if the shader
     * should take cubemap textures as inputs, or {@code false} for 2D textures
     * @throws NullPointerException if either of the parameters are {@code null}
     */
    public Shader(Identity vertID, Identity fragID, boolean mode) {
        if (vertID == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "vertID"));
        if (fragID == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "fragID"));
        
        this.vertID = vertID;
        this.geomID = null;
        this.fragID = fragID;
        this.mode = mode;
    }
    
    /**
     * Instantiates a shader program with the given parameters. Note that the
     * {@link Identity} parameters are all
     * {@linkplain Identity#partial(java.lang.String...) partially completed}
     * with the single parameter {@code "shader"} before they are assigned to this
     * program.
     * 
     * @param vertID the {@code Identity} of the vertex shader
     * @param geomID the {@code Identity} of the geometry shader
     * @param fragID the {@code Identity} of the fragment shader
     * @param mode the mode of the shader; set this to {@code true} if the shader
     * should take cubemap textures as inputs, or {@code false} for 2D textures
     * @throws NullPointerException if any of the parameters are {@code null}
     */
    public Shader(Identity vertID, Identity geomID, Identity fragID, boolean mode) {
        if (vertID == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "vertID"));
        if (geomID == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "geomID"));
        if (fragID == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "fragID"));
        
        this.vertID = vertID;
        this.geomID = geomID;
        this.fragID = fragID;
        this.mode = mode;
    }
    
    void select() {
        try {
            if (progID == null) {
                progID = GraphicsEngine.msgShaderProgramNew(mode);
            
                GraphicsEngine.msgShaderNewVertex(vertID);
                GraphicsEngine.msgShaderProgramAttachShader();
                
                if (geomID != null) {
                    GraphicsEngine.msgShaderNewGeometry(geomID);
                    GraphicsEngine.msgShaderProgramAttachShader();
                }
                
                GraphicsEngine.msgShaderNewFragment(fragID);
                GraphicsEngine.msgShaderProgramAttachShader();
                
            } else
                GraphicsEngine.msgShaderProgramSelect(progID.get(5, TimeUnit.SECONDS));

        } catch (InterruptedException | ExecutionException | CancellationException | TimeoutException e) {
            throw new IllegalStateException(LocaleUtils.format("Shader.select.InitFailed"), e);
        }
    }
    
    /**
     * Checks if the given texture is compatible with this shader mode. This checks
     * the texture type against the mode of the shader.
     * @param texture the texture to check for compatibility
     * @return the result of an XOR operation on the shader mode and a check on the
     * type of the texture (this is defined to be {@code true} if {@code texture} is
     * a {@link Texture2D} instance)
     */
    public boolean isCompatible(Texture texture) {
        return mode ^ (texture instanceof Texture2D);
    }

    @Override
    protected boolean doClose() {
        if (progID != null) {
            GraphicsEngine.MESSAGE_LOCK.lock();

            try {
                select();
                GraphicsEngine.msgShaderProgramClose();
                
                GraphicsEngine.msgShaderNewVertex(vertID);
                GraphicsEngine.msgShaderClose();
                
                if (geomID != null) {
                    GraphicsEngine.msgShaderNewGeometry(geomID);
                    GraphicsEngine.msgShaderClose();
                }
                
                GraphicsEngine.msgShaderNewFragment(fragID);
                GraphicsEngine.msgShaderClose();

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
        if (o == null || !(o instanceof Shader)) return false;
        
        Shader s = (Shader)o;
        
        return this.vertID.equals(s.vertID) && this.fragID.equals(s.fragID) &&
               (this.geomID == null ? s.geomID == null : this.geomID.equals(s.geomID));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.vertID);
        hash = 29 * hash + Objects.hashCode(this.geomID);
        hash = 29 * hash + Objects.hashCode(this.fragID);
        return hash;
    }
}
