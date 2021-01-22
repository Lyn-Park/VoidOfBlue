package net.vob.util;

/**
 * Basic abstract class for closable objects. This provides the basic methods for
 * closing the instance, as well as the ability to check if the instance is closed.
 * 
 * @author Lyn-Park
 */
public abstract class Closable {
    private boolean closed = false;
    
    /**
     * Checks if the instance is closed. If the return value is {@code true},
     * extending classes should ensure that any methods that depend on the internal
     * state of this instance throw or terminate gracefully.
     * 
     * @return {@code true} if the instance is closed, {@code false} otherwise
     */
    public final boolean isClosed() { return closed; }
    
    /**
     * Closes the instance. Performs the closing operations specific to an
     * implementation, and then sets the closed flag of the instance. If the instance
     * is already closed, immediately returns and does nothing.
     */
    public final void close() {
        if (closed || !doClose())
            return;
        
        closed = true;
    }
    
    /**
     * Performs subclass-specific closing operations. Implementations should ensure
     * that any resources still held by this instance have been released prior to
     * returning from this method.
     * 
     * @return {@code true} if the object was successfully closed, {@code false}
     * otherwise
     */
    protected abstract boolean doClose();
}
