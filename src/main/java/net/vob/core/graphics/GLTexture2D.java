package net.vob.core.graphics;

import com.google.common.collect.Sets;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import javax.imageio.ImageIO;
import net.vob.util.Identity;
import net.vob.util.logging.LocaleUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * A class holding a reference to a 2D image loaded into the GPU. Also contains the unit
 * the texture is bound to, and an (optional) identifying value that can be used to equate
 * duplicate textures.<p>
 * 
 * A single default texture exists, and is bound to texture unit 0.
 * 
 * @author Lyn-Park
 */
final class GLTexture2D extends GLTexture {
    static final GLTexture2D DEFAULT = new GLTexture2D(GraphicsEngine.DEFAULT_TEXTURE_ID, 0);
    
    static Set<GLTexture2D> getDefaults() {
        return Sets.newHashSet(DEFAULT);
    }
    
    private final Identity id;
    private int tex;
    
    /**
     * Constructs the texture with an identity. This identity serves to allow for
     * equality tests with other textures; two textures are equal if they have the same
     * non-null identity and unit. Note that this only performs half of the initialization
     * of the texture; the {@link init()} method is expected to be then called once no
     * duplicate textures are found.
     * 
     * @param id the {@link Identity} of this texture
     * @param unit the texture unit of this texture
     * @throws IndexOutOfBoundsException if the given unit is out of bounds
     */
    GLTexture2D(Identity id, int unit) {
        super(unit);
        this.id = id;
    }
    
    /**
     * Constructs the texture without an identity. Note that this performs the full
     * initialization of the texture; the {@link init()} method should not be called on
     * an instance constructed using this constructor.
     * @param im the {@link BufferedImage} to source the texture from
     * @param unit the texture unit of this texture
     * @throws IndexOutOfBoundsException if the given unit is out of bounds
     */
    GLTexture2D(BufferedImage im, int unit) {
        super(unit);
        this.id = null;
        this.tex = bufferImage(im);
    }
    
    /**
     * Performs the remainder of the initialization process on textures constructed
     * using {@link GLTexture2D(Identity, int)}. Should <b>not</b> be called on textures
     * constructed using {@link GLTexture2D(BufferedImage, int)}.
     * @throws IOException if the appropriate input stream could not be found, or if an
     * {@code IOException} occurs when reading from the input stream or when the input
     * stream is closed
     */
    @Override
    void init() throws IOException {
        try (InputStream stream = id.getLastInputStream("png", "texture")) {
            if (stream == null)
                throw new IOException(LocaleUtils.format("global.Exception.SourceNotFound", id.toString()));
            
            tex = bufferImage(ImageIO.read(stream));
        }
    }
    
    private int bufferImage(BufferedImage im) {
        int width = im.getWidth(), height = im.getHeight();
        int[] pixels = new int[width * height];
        im.getRGB(0, 0, width, height, pixels, 0, width);
        
        ByteBuffer buf = BufferUtils.createByteBuffer(width * height * 4);
        
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                int pixel = pixels[(y * width) + x];
                
                buf.put((byte)((pixel >> 16) & 0xFF)); // R
                buf.put((byte)((pixel >> 8)  & 0xFF)); // G
                buf.put((byte)( pixel        & 0xFF)); // B
                buf.put((byte)((pixel >> 24) & 0xFF)); // A
            }
        }
        
        buf.flip();
        
        GL13.glActiveTexture(unit);
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }
    
    @Override
    void bind() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLTexture"));
        
        GL13.glActiveTexture(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
    }
    
    @Override
    void unbind() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLTexture"));
        
        GL13.glActiveTexture(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof GLTexture2D) || this.id == null) return false;
        
        GLTexture2D t = (GLTexture2D)o;
        
        return this.id.equals(t.id) && this.unit == t.unit;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + (this.id == null ? super.hashCode() : this.id.hashCode());
        hash = 83 * hash + this.unit;
        return hash;
    }

    @Override
    protected boolean doClose() {
        unbind();
        GL11.glDeleteTextures(tex);
        
        return true;
    }
}
