package net.vob.core.ui;

/**
 * A set of result values for returning from {@link UIElement#handleInput(Input)}.
 * See the javadocs for each individual value for more information.
 */
public enum Result {
    /**
     * Result value for when the input has been handled successfully by the element.<p>
     * 
     * Returning this value will result in the input not being handled by any child
     * elements, and the input will not be passed to other areas of the program for
     * further handling.
     */
    COMPLETE,
    /**
     * Result value for when the input is not to be handled by this element.<p>
     * 
     * Returning this value will result in the input being passed to all child
     * elements for further processing; if there aren't any child elements, this is
     * the same as returning {@link STOP}.
     */
    PASS,
    /**
     * Result value for when the input is not to be further handled by this element or
     * any children.<p>
     * 
     * Returning this value will result in the input not being passed to any child
     * elements, and (assuming that no other element returns {@link COMPLETE}) the
     * input will be passed to other areas of the program for further handling.
     */
    STOP
}
