package net.vob.core;

import java.util.concurrent.Future;
import net.vob.core.graphics.GraphicsEngine;
import net.vob.util.Closable;
import net.vob.util.logging.LocaleUtils;

/**
 * The abstract parent class for graphical textures. This is kept distinct from the
 * internal texture objects in the graphics engine to promote decoupling between the 
 * internal graphical state and the external program, as well as to prevent confusion.
 * 
 * @author Lyn-Park
 */
public abstract class Texture extends Closable {
    final int unit;
    
    protected Future<Integer> texID = null;
    
    protected Texture(int unit) {
        if (unit < 0 || unit >= GraphicsEngine.getMaxCombinedTextureUnit())
            throw new IndexOutOfBoundsException(LocaleUtils.format("global.Exception.OutOfRange.<=x<", "unit", unit, 0, GraphicsEngine.getMaxCombinedTextureUnit()));
        
        this.unit = unit;
    }
    
    abstract void select();
    
    @Override
    protected final boolean doClose() {
        if (texID != null) {
            GraphicsEngine.MESSAGE_LOCK.lock();

            try {
                select();
                GraphicsEngine.msgTextureClose();

            } catch (IllegalStateException e) {
                return false;
                
            } finally {
                GraphicsEngine.MESSAGE_LOCK.unlock();
            }
        }
        
        return true;
    }
}
