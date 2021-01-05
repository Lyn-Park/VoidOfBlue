package net.vob.core;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.vob.core.graphics.GraphicsEngine;
import net.vob.util.Identity;
import net.vob.util.logging.LocaleUtils;

/**
 * Container class for a graphical 2D texture. This is kept distinct from the internal 
 * texture objects in the graphics engine to promote decoupling between the internal
 * graphical state and the external program, as well as to prevent confusion.
 */
public final class Texture2D extends Texture {
    private final Identity id;
    private BufferedImage im;
    
    public static final Texture2D DEFAULT;
    
    static {
        GraphicsEngine.MESSAGE_LOCK.lock();
        
        try {
            DEFAULT = new Texture2D(GraphicsEngine.msgTextureSelectDefault2D());
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
    }
    
    private Texture2D(Future<Integer> texID) {
        super(0);
        this.texID = texID;
        
        this.id = GraphicsEngine.DEFAULT_TEXTURE_ID;
        this.im = null;
    }
    
    /**
     * Instantiates a texture with the given parameters. Note that the {@link Identity}
     * is {@linkplain Identity#partial(java.lang.String...) partially completed} with
     * the single parameter {@code "texture"} before it is assigned to this texture.
     * @param id the {@code Identity} of the texture
     * @param unit the texture unit of the texture
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IndexOutOfBoundsException if {@code unit} is less than 0 or greater than
     * {@link GraphicsEngine#getMaxCombinedTextureUnit()}
     */
    public Texture2D(Identity id, int unit) {
        super(unit);
        
        if (id == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "id"));
        
        this.id = id;
        this.im = null;
    }
    
    /**
     * Instantiates a texture with the given parameters. Note that using this constructor
     * will result in {@link getID()} returning {@code null}, and that this instance
     * will not hold any reference to the given image once it has been passed to the
     * graphics engine.
     * @param im the texture image
     * @param unit the texture unit of the texture
     * @throws NullPointerException if {@code im} is {@code null}
     * @throws IndexOutOfBoundsException if {@code unit} is less than 0 or greater than
     * {@link GraphicsEngine#getMaxCombinedTextureUnit()}
     */
    public Texture2D(BufferedImage im, int unit) {
        super(unit);
        
        if (im == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "im"));
        
        this.id = null;
        this.im = im;
    }
    
    @Override
    void select() {
        try {
            if (texID == null) {
                if (id == null) {
                    texID = GraphicsEngine.msgTexture2DNew(im, unit);
                    im = null;
                } else
                    texID = GraphicsEngine.msgTexture2DNew(id, unit);
            } else
                GraphicsEngine.msgTextureSelect(texID.get(5, TimeUnit.SECONDS));

        } catch (InterruptedException | ExecutionException | CancellationException | TimeoutException e) {
            throw new IllegalStateException(LocaleUtils.format("Texture.select.InitFailed"), e);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Texture2D)) return false;
        
        Texture2D t = (Texture2D)o;
        
        return this.id == null ? super.equals(o) : (this.unit == t.unit && this.id.equals(t.id));
    }

    @Override
    public int hashCode() {
        if (this.id == null)
            return super.hashCode();
        
        int hash = 5;
        hash = 71 * hash + Objects.hashCode(this.id);
        hash = 71 * hash + Integer.hashCode(this.unit);
        return hash;
    }
}
