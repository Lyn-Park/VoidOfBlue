package net.vob.core.graphics;

import com.google.common.collect.Sets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Set;
import net.vob.util.Identity;
import net.vob.util.Registry;
import net.vob.util.logging.LocaleUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;

/**
 * A class containing a reference to source code loaded into the GPU for a single shader
 * stage. Also contains the type of shader stage it is, and an identifying value that can
 * be used to equate duplicate shaders.<p>
 * 
 * Two default shaders exist, and are of the vertex shader and fragment shader types. No
 * default geometry shader exists.
 * 
 * @author Lyn-Park
 */
final class GLShader extends GLObject {
    static final Registry<GLShader> REGISTRY = new Registry<>();
    
    static final GLShader DEFAULT_VERT_2D = new GLShader(GraphicsEngine.DEFAULT_SHADER_2D_ID, GL20.GL_VERTEX_SHADER);
    static final GLShader DEFAULT_FRAG_2D = new GLShader(GraphicsEngine.DEFAULT_SHADER_2D_ID, GL20.GL_FRAGMENT_SHADER);
    static final GLShader DEFAULT_VERT_CUBE = new GLShader(GraphicsEngine.DEFAULT_SHADER_CUBE_ID, GL20.GL_VERTEX_SHADER);
    static final GLShader DEFAULT_FRAG_CUBE = new GLShader(GraphicsEngine.DEFAULT_SHADER_CUBE_ID, GL20.GL_FRAGMENT_SHADER);
    static final GLShader DEFAULT_VERT_UI = new GLShader(GraphicsEngine.DEFAULT_SHADER_UI_ID, GL20.GL_VERTEX_SHADER);
    static final GLShader DEFAULT_FRAG_UI = new GLShader(GraphicsEngine.DEFAULT_SHADER_UI_ID, GL20.GL_FRAGMENT_SHADER);
    static final GLShader SKYBOX_VERT = new GLShader(GraphicsEngine.SKYBOX_SHADER_ID, GL20.GL_VERTEX_SHADER);
    static final GLShader SKYBOX_FRAG = new GLShader(GraphicsEngine.SKYBOX_SHADER_ID, GL20.GL_FRAGMENT_SHADER);
    
    static Set<GLShader> getDefaults() {
        return Sets.newHashSet(DEFAULT_VERT_2D, DEFAULT_FRAG_2D,
                               DEFAULT_VERT_CUBE, DEFAULT_FRAG_CUBE,
                               DEFAULT_VERT_UI, DEFAULT_FRAG_UI,
                               SKYBOX_VERT, SKYBOX_FRAG);
    }
    
    final Identity id;
    final int type;
    int shd;
    
    /**
     * Constructs this shader object. Does not perform any initialization functions that
     * must be performed on the graphics thread.
     * @param id the {@link Identity} of this shader
     * @param type one of {@link TYPE_VERT}, {@link TYPE_GEOM}, {@link TYPE_FRAG}
     */
    GLShader(Identity id, int type) {
        this.id = id;
        this.type = type;
    }
    
    /**
     * Performs graphics-side initialization of this shader, using the parameters passed
     * to the constructor of this object.
     * @throws IOException if the appropriate input stream could not be found, if an
     * {@code IOException} occurs when reading from the input stream or when the input
     * stream is closed, or if the shader fails to compile
     */
    @Override
    void init() throws IOException {
        String sType;
        switch(type) {
            case GL20.GL_VERTEX_SHADER: sType = "vert"; break;
            case GL32.GL_GEOMETRY_SHADER: sType = "geom"; break;
            case GL20.GL_FRAGMENT_SHADER: sType = "frag"; break;
            default: throw new IllegalArgumentException(LocaleUtils.format("GLShader.init.InvalidType"));
        }
        
        try (InputStream is = id.getLastInputStream(sType, "shader")) {
            if (is == null)
                throw new IOException(LocaleUtils.format("global.Exception.SourceNotFound", String.format("%s, shader type %s", id.toString(), sType)));

            shd = GL20.glCreateShader(type);
            buildShader(is);
        }
    }
    
    private void buildShader(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            StringBuilder builder = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null)
                builder.append(line).append("\n");
            
            GL20.glShaderSource(shd, builder);
            GL20.glCompileShader(shd);
            
            if (GL20.glGetShaderi(shd, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                String info = GL20.glGetShaderInfoLog(shd);
                GL20.glDeleteShader(shd);
                throw new IOException(LocaleUtils.format("GLShader.buildShader.CompilationError", info));
            }
        }
    }

    @Override
    protected boolean doClose() {
        GL20.glDeleteShader(shd);
        
        return true;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof GLShader)) return false;
        
        GLShader s = (GLShader)o;
        
        return id.equals(s.id) && type == s.type;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + Objects.hashCode(this.id);
        hash = 61 * hash + this.type;
        return hash;
    }
}
