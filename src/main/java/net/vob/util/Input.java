package net.vob.util;

import net.vob.util.math.Vector2;

/**
 * Container class for various types of input.<p>
 * 
 * This class is designed to contain various inputs from different sources, which
 * can be queried using the {@code source} variable. Depending on the source of
 * the input, the parameters in this class may take differing meanings:
 * <ul>
 *  <li>If {@code source == }{@link Source#MOUSE}, then {@code position} contains
 *      the current position of the mouse. {@code code} will contain either a 0 or
 *      a 1; a value of 0 indicates that this is a mouse movement event (and thus
 *      the remaining parameters are all also 0), while a value of 1 indicates that
 *      this is a mouse click event (in which case {@code key} indicates the button
 *      and {@code action} indicates the action).</li>
 *  <li>If {@code source == }{@link Source#KEY}, then {@code position} is
 *      {@code null}, {@code key} indicates the key, {@code code} indicates the
 *      scancode and {@code action} indicates the action.</li>
 *  <li>If {@code source == }{@link Source#CHAR}, then {@code key} holds the
 *      codepoint of the input character. All other parameters are either 0 or
 *      {@code null}.</li>
 *  <li>Otherwise, {@code source == }{@link Source#OTHER}, and the parameters can
 *      potentially take on any value and meaning. This source type should be used
 *      carefully, as the unknown nature of the parameters means this could lead to
 *      unforeseen and undesirable behaviour.</li>
 * </ul>
 * 
 * @author Lyn-Park
 */
public final class Input {
    public final Source source;
    public final Vector2 position;
    public final int key, code, action;
    
    public Input(Source source, Vector2 position, int key, int code, int action) {
        this.source = source;
        this.position = position;
        this.key = key;
        this.code = code;
        this.action = action;
    }
    
    public static enum Source {
        /** 
         * Source type indicating that this is a mouse event.
         */
        MOUSE,
        /** 
         * Source type indicating that this is a keyboard event. Note that this is
         * different from {@link CHAR}, as character events take into account the
         * keyboard layout and modifiers.
         */
        KEY,
        /**
         * Source type indicating that this is a character input event.
         */
        CHAR,
        /**
         * Source type indicating that this event came from somewhere else.
         */
        OTHER
    }
}
