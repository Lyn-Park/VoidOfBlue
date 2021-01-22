package net.vob.core.graphics;

import net.vob.util.Closable;

/**
 * Abstract class for a GL object. Provides a basic framework to build classes that
 * interface with the GPU around.
 * 
 * @author Lyn-Park
 */
abstract class GLObject extends Closable {
    /**
     * Initializes this GL object. Typically, this method is used for invoking OpenGL
     * methods that cannot be invoked in the constructor, for any particular set of 
     * reasons.<p>
     * 
     * Note that implementations are expected to replace the exception type with more
     * specific types suited to them.
     * 
     * @throws Exception if the implementation of this method throws an exception
     */
    abstract void init() throws Exception;
}
