package net.vob.core.graphics;

import com.google.common.collect.Sets;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
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
 * A class holding a reference to a set of cubemap images loaded into the GPU. Also
 * contains the unit the texture is bound to, and a set of (optional) identifying values
 * that can be used to equate duplicate textures.<p>
 * 
 * A single default texture exists, and is bound to texture unit 0.
 * 
 * @author Lyn-Park
 */
final class GLTextureCubemap extends GLTexture {
    static final GLTextureCubemap DEFAULT = new GLTextureCubemap(GraphicsEngine.DEFAULT_TEXTURE_ID,
                                                                  GraphicsEngine.DEFAULT_TEXTURE_ID,
                                                                  GraphicsEngine.DEFAULT_TEXTURE_ID,
                                                                  GraphicsEngine.DEFAULT_TEXTURE_ID,
                                                                  GraphicsEngine.DEFAULT_TEXTURE_ID,
                                                                  GraphicsEngine.DEFAULT_TEXTURE_ID, 0);
    
    static Set<GLTextureCubemap> getDefaults() {
        return Sets.newHashSet(DEFAULT);
    }
    
    private final Identity pxID, nxID, pyID, nyID, pzID, nzID;
    private int tex;
    
    /**
     * Constructs the cubemap texture with an identity. This identity serves to allow for
     * equality tests with other cubemaps; two cubemaps are equal if they have the same
     * non-null identity and unit, and all other identities are null. Note that this only
     * performs half of the initialization of the texture; the {@link init()} method is
     * expected to be then called once no duplicate textures are found.<p>
     * 
     * The cubemap itself is derived from the sourced image in 4 different ways, depending
     * on the dimensions of the image. The layout of the faces in the image are shown
     * graphically below:
     * <blockquote><pre>
     * {@code width = height * 6}     :      +X  -X  +Y  -Y  +Z  -Z
     * 
     *                     :          +Y
     * {@code width * 3 = height * 4} :      -X  +Z  +X  -Z
     *                     :          -Y
     * 
     *                     :          +Y
     * {@code width * 4 = height * 3} :      +Z  +X  -Z
     *                     :          -Y
     *                     :          -X
     *
     *                     :          +X
     *                     :          -X
     * {@code width * 6 = height}     :          +Y
     *                     :          -Y
     *                     :          +Z
     *                     :          -Z
     * </pre></blockquote>
     * The sub-images as laid out here must be square images; thus, {@link init()} will
     * throw an exception if the sourced image is not one of the listed dimensions.
     * 
     * @param id the {@link Identity} of this texture
     * @param unit the texture unit of this texture
     * @throws IndexOutOfBoundsException if the given unit is out of bounds
     */
    GLTextureCubemap(Identity id, int unit) {
        super(unit);
        this.pxID = id;
        this.nxID = this.pyID = this.nyID = this.pzID = this.nzID = null;
    }
    
    /**
     * Constructs the cubemap texture with a set of identities, each of which describes
     * a single face of the cubemap. These identities serve to allow for equality tests
     * with other cubemaps; two cubemaps are equal if they have the same non-null
     * identities and unit. Note that this only performs half of the initialization of the
     * texture; the {@link init()} method is expected to be then called once no duplicate
     * textures are found.<p>
     * 
     * The sourced face textures must be square images; thus, {@link init()} will throw
     * an exception if any sourced image is not square.
     * 
     * @param px the {@link Identity} of the texture for the positive-X face
     * @param nx the {@link Identity} of the texture for the negative-X face
     * @param py the {@link Identity} of the texture for the positive-Y face
     * @param ny the {@link Identity} of the texture for the negative-Y face
     * @param pz the {@link Identity} of the texture for the positive-Z face
     * @param nz the {@link Identity} of the texture for the negative-Z face
     * @param unit the texture unit of this texture
     * @throws IndexOutOfBoundsException if the given unit is out of bounds
     */
    GLTextureCubemap(Identity px, Identity nx, Identity py, Identity ny, Identity pz, Identity nz, int unit) {
        super(unit);
        
        this.pxID = px;
        this.nxID = nx;
        this.pyID = py;
        this.nyID = ny;
        this.pzID = pz;
        this.nzID = nz;
    }
    
    /**
     * Constructs the texture without an identity. Note that this performs the full
     * initialization of the texture; the {@link init()} method should not be called on
     * an instance constructed using this constructor.<p>
     * 
     * The cubemap itself is derived from the given image in 4 different ways, depending
     * on the dimensions of the image. The layout of the faces in the image are shown
     * graphically below:
     * <blockquote><pre>
     * {@code width = height * 6}     :      +X  -X  +Y  -Y  +Z  -Z
     * 
     *                     :          +Y
     * {@code width * 3 = height * 4} :      -X  +Z  +X  -Z
     *                     :          -Y
     * 
     *                     :          +Y
     * {@code width * 4 = height * 3} :      +Z  +X  -Z
     *                     :          -Y
     *                     :          -X
     * 
     *                     :          +X
     *                     :          -X
     * {@code width * 6 = height}     :          +Y
     *                     :          -Y
     *                     :          +Z
     *                     :          -Z
     * </pre></blockquote>
     * The sub-images as laid out here must be square images; thus, an exception will
     * be thrown if the given image is not one of the listed dimensions.
     * 
     * @param id the {@link Identity} of this texture
     * @param unit the texture unit of this texture
     * @throws IndexOutOfBoundsException if the given unit is out of bounds
     * @throws IllegalArgumentException if the given image is not one of the
     * dimensionalities listed above
     */
    GLTextureCubemap(BufferedImage im, int unit) {
        super(unit);
        
        int width = im.getWidth(), height = im.getHeight();
        if (!(width == height * 6 || width * 3 == height * 4 || width * 4 == height * 3 || width * 6 == height))
            throw new IllegalArgumentException(LocaleUtils.format("GLTextureCubemap.NonSquareFace"));
        
        this.pxID = this.nxID = this.pyID = this.nyID = this.pzID = this.nzID = null;
        
        texGenBegin();
        
        try {
            bufferAllFaces(im);
        } catch (IOException e) {
            // This won't reach here because of the check above, but catch and
            // close just in case
            close();
        }
        
        texGenEnd();
    }
    
    /**
     * Constructs the texture without an identity. Note that this performs the full
     * initialization of the texture; the {@link init()} method should not be called on
     * an instance constructed using this constructor.<p>
     * 
     * The given face textures must be square images.
     * 
     * @param px the {@link BufferedImage} texture for the positive-X face
     * @param nx the {@link BufferedImage} texture for the negative-X face
     * @param py the {@link BufferedImage} texture for the positive-Y face
     * @param ny the {@link BufferedImage} texture for the negative-Y face
     * @param pz the {@link BufferedImage} texture for the positive-Z face
     * @param nz the {@link BufferedImage} texture for the negative-Z face
     * @param unit the texture unit of this texture
     * @throws IndexOutOfBoundsException if the given unit is out of bounds
     * @throws IllegalArgumentException if any given image is non-square
     */
    GLTextureCubemap(BufferedImage px, BufferedImage nx, BufferedImage py, BufferedImage ny, BufferedImage pz, BufferedImage nz, int unit) {
        super(unit);
        
        if (px.getWidth() != px.getHeight() || nx.getWidth() != nx.getHeight() ||
            py.getWidth() != py.getHeight() || ny.getWidth() != ny.getHeight() ||
            pz.getWidth() != pz.getHeight() || nz.getWidth() != nz.getHeight())
            throw new IllegalArgumentException(LocaleUtils.format("GLTextureCubemap.NonSquareFace"));
        
        
        this.pxID = this.nxID = this.pyID = this.nyID = this.pzID = this.nzID = null;
        
        texGenBegin();
        
        try {
            bufferFace(px, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X);
            bufferFace(nx, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X);
            bufferFace(py, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y);
            bufferFace(ny, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y);
            bufferFace(pz, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z);
            bufferFace(nz, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z);
        } catch (IOException e) {
            // This won't reach here because of the check above, but catch and
            // close just in case
            close();
        }
        
        texGenEnd();
    }
    
    @Override
    void init() throws IOException {
        texGenBegin();
        
        try {
            if (nxID == null) {
                bufferAllFaces(pxID);

            } else {
                bufferFace(pxID, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X);
                bufferFace(nxID, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X);
                bufferFace(pyID, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y);
                bufferFace(nyID, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y);
                bufferFace(pzID, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z);
                bufferFace(nzID, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z);
            }
        } catch (IOException e) {
            close();
            throw e;
        }
        
        texGenEnd();
    }
    
    private static void bufferAllFaces(Identity id) throws IOException {
        try (InputStream stream = id.getLastInputStream("png", "texture")) {
            if (stream == null)
                throw new IOException(LocaleUtils.format("global.Exception.SourceNotFound", id.toString()));
            
            bufferAllFaces(ImageIO.read(stream));
        }
    }
    
    private static void bufferAllFaces(BufferedImage im) throws IOException {
        int width = im.getWidth(), height = im.getHeight(), s;
        BufferedImage px, py, pz, nx, ny, nz;
        
        if (width == height * 6) {            // Horizontal strip
            s = height;

            px = im.getSubimage(  0, 0, s, s);
            py = im.getSubimage(2*s, 0, s, s);
            pz = im.getSubimage(4*s, 0, s, s);
            nx = im.getSubimage(  s, 0, s, s);
            ny = im.getSubimage(3*s, 0, s, s);
            nz = im.getSubimage(5*s, 0, s, s);
                
        } else if (width * 3 == height * 4) { // Horizontal net
            s = width / 4;

            px = im.getSubimage(2*s,   s, s, s);
            py = im.getSubimage(  s,   0, s, s);
            pz = im.getSubimage(  s,   s, s, s);
            nx = im.getSubimage(  0,   s, s, s);
            ny = im.getSubimage(  s, 2*s, s, s);
            nz = im.getSubimage(3*s,   s, s, s);
            
        } else if (width * 4 == height * 3) { // Vertical net
            s = height / 4;

            px = im.getSubimage(  s,   s, s, s);
            py = im.getSubimage(  s,   0, s, s);
            pz = im.getSubimage(  0,   s, s, s);
            nx = im.getSubimage(  s, 3*s, s, s);
            ny = im.getSubimage(  s, 2*s, s, s);
            nz = im.getSubimage(2*s,   s, s, s);
                
        } else if (width * 6 == height) {     // Vertical strip
            s = width;

            px = im.getSubimage(0,   0, s, s);
            py = im.getSubimage(0, 2*s, s, s);
            pz = im.getSubimage(0, 4*s, s, s);
            nx = im.getSubimage(0,   s, s, s);
            ny = im.getSubimage(0, 3*s, s, s);
            nz = im.getSubimage(0, 5*s, s, s);
            
        } else
            throw new IOException(LocaleUtils.format("GLTextureCubemap.NonSquareFace"));
        
        bufferFace(px, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X);
        bufferFace(nx, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X);
        bufferFace(py, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y);
        bufferFace(ny, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y);
        bufferFace(pz, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z);
        bufferFace(nz, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z);
    }
    
    private static void bufferFace(Identity id, int face) throws IOException {
        try (InputStream stream = id.getLastInputStream("png", "texture")) {
            if (stream == null)
                throw new IOException(LocaleUtils.format("global.Exception.SourceNotFound", id.toString()));
            
            bufferFace(ImageIO.read(stream), face);
        }
    }
    
    private static void bufferFace(BufferedImage im, int face) throws IOException {
        int width = im.getWidth(), height = im.getHeight();
        if (width != height)
            throw new IOException(LocaleUtils.format("GLTextureCubemap.NonSquareFace"));
        
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
        
        GL11.glTexImage2D(face, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
    }
    
    private void texGenBegin() {
        GL13.glActiveTexture(unit);
        tex = GL11.glGenTextures();
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, tex);
    }
    
    private void texGenEnd() {
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        
        GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
    }
    
    @Override
    void bind() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLTexture"));
        
        GL13.glActiveTexture(unit);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, tex);
    }
    
    @Override
    void unbind() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "GLTexture"));
        
        GL13.glActiveTexture(unit);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof GLTextureCubemap) || this.pxID == null) return false;
        
        GLTextureCubemap t = (GLTextureCubemap)o;
        
        return Objects.equals(this.pxID, t.pxID) &&
               Objects.equals(this.nxID, t.nxID) &&
               Objects.equals(this.pyID, t.pyID) &&
               Objects.equals(this.nyID, t.nyID) &&
               Objects.equals(this.pzID, t.pzID) &&
               Objects.equals(this.nzID, t.nzID) &&
               this.unit == t.unit;
    }

    @Override
    public int hashCode() {
        int hash = 23;
        hash = 79 * hash + Objects.hashCode(this.pxID);
        hash = 79 * hash + Objects.hashCode(this.nxID);
        hash = 79 * hash + Objects.hashCode(this.pyID);
        hash = 79 * hash + Objects.hashCode(this.nyID);
        hash = 79 * hash + Objects.hashCode(this.pzID);
        hash = 79 * hash + Objects.hashCode(this.nzID);
        hash = 79 * hash + this.unit;
        return hash;
    }
    
    @Override
    public boolean doClose() {
        unbind();
        GL11.glDeleteTextures(tex);
        
        return true;
    }
}
