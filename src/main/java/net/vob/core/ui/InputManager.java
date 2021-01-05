package net.vob.core.ui;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import net.vob.util.Input;
import net.vob.util.logging.LocaleUtils;

/**
 * Manager class for inputs and the UI.<p>
 * 
 * The elements that make up the UI are registered here. Each one exists in a tree
 * structure; taking the window itself to be the root node (conceptually), each
 * {@link UIElement} instance is a child node of some other element (or the window).
 * This tree structure is used for sorting and handling inputs, as it allows for
 * different inputs to propagate through different branches in the tree. Note that
 * this propagation uses a
 * <a href="https://en.wikipedia.org/wiki/Breadth-first_search">breadth-first traversal</a>
 * algorithm. Also note that {@code UIElement} instances must be registered here for
 * them to function; failure to do so will result in these elements not receiving any
 * inputs, and they cannot be used in any of the other methods here.<p>
 * 
 * Any coordinates that the manager uses for the UI or any inputs uses 'window space'.
 * This is defined to be the XY plane which the window renders. The origin point of this
 * space is in the lower-left corner of the window, and the axes are measured in pixels
 * - thus, the upper-right corner has coordinates {@code (width, height)}, where 
 * {@code width} and {@code height} are obviously the width and height of the window.
 */
public final class InputManager {
    static final Set<UIElement> ELEMENTS = new HashSet<>();
    static final Set<UIElement> ROOT_ELEMENTS = new HashSet<>();
    static UIElement FOCUSED_ELEMENT = null;
    
    /**
     * Registers the given element with the manager. This is a necessary step to allow
     * the element to receive inputs, as well as allowing it to be used with the other
     * methods in this manager.<p>
     * 
     * Note that the element initially has no parent; if it needs one, it must be set
     * after this method has been invoked.
     * 
     * @param element the {@link UIElement} to register
     * @throws NullPointerException if {@code element} is {@code null}
     */
    public static void registerElement(UIElement element) {
        if (element == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "element"));
    
        ELEMENTS.add(element);
        ROOT_ELEMENTS.add(element);
    }
    
    /**
     * Brings the given element into focus. A focused element can receive certain
     * inputs, such as keyboard events.<p>
     * 
     * Note that {@code element} can be {@code null}; this will essentially disable
     * any inputs that require a focused element.
     * 
     * @param element the {@link UIElement} to focus
     * @throws IllegalArgumentException if {@code element} is non-{@code null} and
     * it is not currently registered with this manager
     */
    public static void focusElement(UIElement element) {
        if (element != null && !ELEMENTS.contains(element))
            throw new IllegalArgumentException(LocaleUtils.format("UIManager.UnregisteredElement", "element"));
        
        FOCUSED_ELEMENT = element;
    }
    
    /**
     * Creates a parent-child relationship between the two given elements. This
     * relationship simplifies operations on the UI, for 2 reasons:
     * <ul>
     *  <li>Parents can be moved around the window, which will in turn move all its
     *      children around as well; this simplifies movement operations greatly.</li>
     *  <li>Parents are passed inputs first; there, they can decide if an input should
     *      be passed to any child elements. This 'filtering' streamlines the input
     *      handling process by limiting the number of elements that do the handling.</li>
     * </ul>
     * Note that {@code parent} can be {@code null}; this will remove any
     * parent-child relationship that {@code child} currently has, and it will behave
     * as if it is a child of the window itself.
     * 
     * @param parent the new {@link UIElement} parent
     * @param child the {@link UIElement} child
     * @throws NullPointerException if {@code child} is {@code null}
     * @throws IllegalArgumentException if {@code parent} is non-{@code null} and
     * it is not currently registered with this manager, or if {@code child} is not
     * currently registered with this manager
     */
    public static void setParent(UIElement parent, UIElement child) {
        if (parent != null && !ELEMENTS.contains(parent))
            throw new IllegalArgumentException(LocaleUtils.format("UIManager.UnregisteredElement", "parent"));
        if (child == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "child"));
        if (!ELEMENTS.contains(child))
            throw new IllegalArgumentException(LocaleUtils.format("UIManager.UnregisteredElement", "child"));
        
        child.setParent(parent);
    }
    
    /**
     * Pushes the input to the root UI elements.
     * 
     * @see pushInputToElements(Input, Collection&lt;UIElement&gt;) - for more 
     * information
     * @param input the {@link Input} to handle
     * @return {@code true} if any handling element returns {@link Result#COMPLETE},
     * {@code false} otherwise
     * @throws NullPointerException if {@code input} is {@code null}
     */
    public static boolean pushInputToRootUIElements(Input input) {
        return pushInputToElements(input, ROOT_ELEMENTS);
    }
    
    /**
     * Pushes the input to the currently focused UI element. Returns {@code false}
     * immediately if there is no currently focused element.
     * 
     * @see pushInputToElements(Input, Collection&lt;UIElement&gt;) - for more 
     * information
     * @param input the {@link Input} to handle
     * @return {@code true} if any handling element returns {@link Result#COMPLETE},
     * {@code false} otherwise
     * @throws NullPointerException if {@code input} is {@code null}
     */
    public static boolean pushInputToFocusedUIElement(Input input) {
        if (FOCUSED_ELEMENT == null) return false;
        return pushInputToElements(input, FOCUSED_ELEMENT);
    }
    
    /**
     * Pushes the input to the given elements. This is equivalent to, and directly
     * calls, <br>
     * {@link pushInputToElements(Input, Collection) pushInputToElements}{@code (input, }{@link Arrays#asList(Object...) Arrays.asList}{@code (elements))}.
     * 
     * @param input the {@link Input} to handle
     * @param elements the array of {@link UIElement} instances to visit
     * @return {@code true} if any handling element returns {@link Result#COMPLETE},
     * {@code false} otherwise
     * @throws NullPointerException if {@code input} or {@code elements} is
     * {@code null}
     * @throws IllegalArgumentException if any element in {@code elements} is not
     * currently registered with this manager
     */
    public static boolean pushInputToElements(Input input, UIElement... elements) {
        return pushInputToElements(input, Arrays.asList(elements));
    }
    
    /**
     * Pushes the input to the given collection of elements.<p>
     * 
     * Each element will be visited in turn using a {@link Queue}, and they will
     * handle the input using {@link UIElement#handleInput(Input) handleInput(input)}.
     * The returned value then dictates what happens next:
     * <ul>
     *  <li>A return value of {@link Result#COMPLETE} indicates that this method
     *      will return {@code true}.</li>
     *  <li>A return value of {@link Result#PASS} causes the children of the handler
     *      element to be pushed to the handling queue.</li>
     *  <li>A return value of {@link Result#STOP} prevents either of these two cases
     *      from occuring.</li>
     * </ul>
     * Note that the collection of elements need not contain root elements only; they
     * need only be registered to be valid. However, this method also guarantees that
     * a single invocation will only visit any given element a single time at maximum
     * (the order of visiting is not guaranteed, however).
     * 
     * @param input the {@link Input} to handle
     * @param elements the collection of {@link UIElement} instances to visit
     * @return {@code true} if any handling element returns {@link Result#COMPLETE},
     * {@code false} otherwise
     * @throws NullPointerException if {@code input} or {@code elements} is
     * {@code null}, or if any handling element returns {@code null} as opposed to a
     * {@link Result} instance
     * @throws IllegalArgumentException if any element in {@code elements} is not
     * currently registered with this manager
     */
    public static boolean pushInputToElements(Input input, Collection<? extends UIElement> elements) {
        if (input == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "input"));
        if (elements == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "elements"));
        if (elements.stream().anyMatch(e -> !ELEMENTS.contains(e)))
            throw new IllegalArgumentException(LocaleUtils.format("UIManager.UnregisteredCollectionElement", "elements"));
        
        Set<UIElement> visited = new HashSet<>();
        Queue<UIElement> handlers = new LinkedList<>();
        handlers.addAll(elements);
        boolean completed = false;
        
        while (!handlers.isEmpty()) {
            UIElement handler = handlers.poll();
            
            if (handler != null && !visited.contains(handler)) {
                visited.add(handler);
                Result result = handler.handleInput(input);
                
                if (result == null)
                    throw new NullPointerException(LocaleUtils.format("UIManager.pushInputToElements.NullResult"));
                
                switch (result) {
                    case PASS:      handlers.addAll(handler.getChildren()); break;
                    case COMPLETE:  completed = true;                       break;
                }
            }
        }
        
        return completed;
    }
}
