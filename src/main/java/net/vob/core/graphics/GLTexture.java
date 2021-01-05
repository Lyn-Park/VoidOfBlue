package net.vob.core.graphics;

import net.vob.util.Registry;
import net.vob.util.logging.LocaleUtils;
import org.lwjgl.opengl.GL13;

/**
 * Abstract base class for texture objects.
 */
abstract class GLTexture extends GLObject {
    static final Registry<GLTexture> REGISTRY = new Registry<>();
    
    final int unit;
    
    protected GLTexture(int unit) {
        if (unit < 0 || unit >= GraphicsManager.MAX_COMBINED_TEXTURE_UNITS)
            throw new IndexOutOfBoundsException(LocaleUtils.format("global.Exception.OutOfRange.<=x<", "unit", unit, 0, GraphicsManager.MAX_COMBINED_TEXTURE_UNITS));
        
        this.unit = GL13.GL_TEXTURE0 + unit;
    }
    
    /**
     * Binds this texture in OpenGL.
     * @throws IllegalStateException if this texture is closed
     */
    abstract void bind();
    /**
     * Unbinds this texture in OpenGL.
     * @throws IllegalStateException if this texture is closed
     */
    abstract void unbind();
}
