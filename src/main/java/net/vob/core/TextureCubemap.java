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

public final class TextureCubemap extends Texture {
    private final Identity pxID, pyID, pzID, nxID, nyID, nzID;
    private BufferedImage pxIm, pyIm, pzIm, nxIm, nyIm, nzIm;
    
    public static final TextureCubemap DEFAULT;
    
    static {
        GraphicsEngine.MESSAGE_LOCK.lock();
        
        try {
            DEFAULT = new TextureCubemap(GraphicsEngine.msgTextureSelectDefaultCubemap());
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
    }

    private TextureCubemap(Future<Integer> texID) {
        super(0);
        this.texID = texID;
        
        this.pxID = this.pyID = this.pzID = this.nxID = this.nyID = this.nzID = GraphicsEngine.DEFAULT_TEXTURE_ID;
        this.pxIm = this.pyIm = this.pzIm = this.nxIm = this.nyIm = this.nzIm = null;
    }

    public TextureCubemap(Identity id, int unit) {
        super(unit);
        
        this.pxID = id;
        this.pyID = this.pzID = this.nxID = this.nyID = this.nzID = null;
        this.pxIm = this.pyIm = this.pzIm = this.nxIm = this.nyIm = this.nzIm = null;
    }

    public TextureCubemap(BufferedImage im, int unit) {
        super(unit);
        
        this.pxID = this.pyID = this.pzID = this.nxID = this.nyID = this.nzID = null;
        this.pxIm = im;
        this.pyIm = this.pzIm = this.nxIm = this.nyIm = this.nzIm = null;
    }

    public TextureCubemap(Identity pxID, Identity pyID, Identity pzID, Identity nxID, Identity nyID, Identity nzID, int unit) {
        super(unit);
        
        this.pxID = pxID;
        this.pyID = pyID;
        this.pzID = pzID;
        this.nxID = nxID;
        this.nyID = nyID;
        this.nzID = nzID;
        this.pxIm = this.pyIm = this.pzIm = this.nxIm = this.nyIm = this.nzIm = null;
    }

    public TextureCubemap(BufferedImage pxIm, BufferedImage pyIm, BufferedImage pzIm, BufferedImage nxIm, BufferedImage nyIm, BufferedImage nzIm, int unit) {
        super(unit);
        
        this.pxID = this.pyID = this.pzID = this.nxID = this.nyID = this.nzID = null;
        this.pxIm = pxIm;
        this.pyIm = pyIm;
        this.pzIm = pzIm;
        this.nxIm = nxIm;
        this.nyIm = nyIm;
        this.nzIm = nzIm;
    }
    
    @Override
    void select() {
        try {
            if (texID == null) {
                if (pxID == null) {
                    if (pyIm == null)
                        GraphicsEngine.msgTextureCubemapNew(pxIm, unit);
                    else
                        GraphicsEngine.msgTextureCubemapNew(pxIm, nxIm, pyIm, nyIm, pzIm, nzIm, unit);
                    
                    pxIm = pyIm = pzIm = nxIm = nyIm = nzIm = null;
                } else {
                    if (pyID == null)
                        GraphicsEngine.msgTextureCubemapNew(pxID, unit);
                    else
                        GraphicsEngine.msgTextureCubemapNew(pxID, nxID, pyID, nyID, pzID, nzID, unit);
                }
            } else
                GraphicsEngine.msgTextureSelect(texID.get(5, TimeUnit.SECONDS));

        } catch (InterruptedException | ExecutionException | CancellationException | TimeoutException e) {
            throw new IllegalStateException(LocaleUtils.format("Texture.select.InitFailed"), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof TextureCubemap)) return false;
        
        TextureCubemap t = (TextureCubemap)o;
        
        return this.pxID == null ? super.equals(o) : (this.unit == t.unit &&
                                                      Objects.equals(this.pxID, t.pxID) &&
                                                      Objects.equals(this.pyID, t.pyID) &&
                                                      Objects.equals(this.pzID, t.pzID) &&
                                                      Objects.equals(this.nxID, t.nxID) &&
                                                      Objects.equals(this.nyID, t.nyID) &&
                                                      Objects.equals(this.nzID, t.nzID));
    }

    @Override
    public int hashCode() {
        if (this.pxID == null)
            return super.hashCode();
        
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.pxID);
        hash = 29 * hash + Objects.hashCode(this.pyID);
        hash = 29 * hash + Objects.hashCode(this.pzID);
        hash = 29 * hash + Objects.hashCode(this.nxID);
        hash = 29 * hash + Objects.hashCode(this.nyID);
        hash = 29 * hash + Objects.hashCode(this.nzID);
        hash = 29 * hash + Integer.hashCode(this.unit);
        return hash;
    }
}
