package net.vob.core.graphics;

import net.vob.util.logging.Level;

final class GLLevel extends Level {
    static final Level GL_DEBUG_ERROR = new GLLevel("GL_DEBUG_ERROR", 301);
    static final Level GL_DEBUG_PERFORMANCE = new GLLevel("GL_DEBUG_PERFORMANCE", 310);
    
    protected GLLevel(String name, int value) {
        super(name, value);
    }
}
