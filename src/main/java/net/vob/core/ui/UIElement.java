package net.vob.core.ui;

import net.vob.util.Input;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.vob.core.Mesh;
import net.vob.core.Texture2D;
import net.vob.core.UIRenderable;
import net.vob.util.Closable;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.Maths;
import net.vob.util.math.Rectangle;
import net.vob.util.math.Vector2;
import net.vob.util.math.Vector3;

/**
 * A {@code UIElement} is an object that represents a part of a user interface. They
 * can be graphical or non-graphical, and they can take mouse inputs or inputs from
 * the program itself. They also exist in a tree structure, with each element having
 * 1 parent and any number of children elements. Inputs are handled according to this
 * tree structure.<p>
 * 
 * Each element takes a bounding box as a parameter on construction. These bounding
 * boxes define the size, position and possibly the input handling of the element: the
 * lower-left corner of the bounding box is regarded as the 'origin point' of the
 * element.<p>
 * 
 * Note that conceptually, the window itself is a ui element that passes all inputs
 * to it's children; any instance of this class that has {@code null} as it's parent
 * signifies that it is a child of the window itself.
 */
public abstract class UIElement extends Closable {
    private UIElement parent = null;
    private final Set<UIElement> children = new HashSet<>();
    
    private Rectangle boundingBox;
    private final UIRenderable graphics;
    
    /**
     * Instantiates a new {@code UIElement} instance using the given parameters.
     * This constructor doesn't construct any graphics for the element.
     * 
     * @param boundingBox the bounding box of this element
     * @throws NullPointerException if {@code boundingBox} is {@code null}
     * @throws IllegalArgumentException if {@code boundingBox} has an area of
     * (approximately) 0
     */
    public UIElement(Rectangle boundingBox) {
        if (boundingBox == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "boundingBox"));
        if (Maths.approx0(boundingBox.getArea()))
            throw new IllegalArgumentException(LocaleUtils.format("global.Math.Rectangle.ZeroArea", "boundingBox"));
        
        this.boundingBox = new Rectangle(boundingBox);
        
        this.graphics = null;
    }
    
    /**
     * Instantiates a new {@code UIElement} instance using the given parameters.
     * This constructor creates some basic graphics using a quad and the given texture,
     * unless the given texture is {@code null}.
     * 
     * @param boundingBox the bounding box of this element
     * @param texture the texture this element initially uses
     * @throws NullPointerException if {@code boundingBox} is {@code null}
     * @throws IllegalArgumentException if {@code boundingBox} has an area of
     * (approximately) 0
     */
    public UIElement(Rectangle boundingBox, Texture2D texture) {
        if (boundingBox == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "boundingBox"));
        if (Maths.approx0(boundingBox.getArea()))
            throw new IllegalArgumentException(LocaleUtils.format("global.Math.Rectangle.ZeroArea", "boundingBox"));
        
        this.boundingBox = new Rectangle(boundingBox);
        
        if (texture == null)
            this.graphics = null;
        else {
            this.graphics = UIRenderable.build(Mesh.DEFAULT_QUAD, texture);
            this.graphics.transform.setScale(new Vector3(boundingBox.getWidth(), boundingBox.getHeight(), 1.0));
            this.graphics.transform.setTranslation(new Vector3(boundingBox.getMidpointX(), boundingBox.getMidpointY(), 0.0));
        }
    }
    
    /**
     * Instantiates a new {@code UIElement} instance using the given parameters.
     * This constructor creates some basic graphics using the given mesh and texture,
     * unless the given mesh or texture is {@code null}. Note that the mesh cannot
     * be accessed or altered if the sub-class does not retain a reference to it.
     * 
     * @param boundingBox the bounding box of this element
     * @param mesh the mesh this element uses
     * @param texture the texture this element initially uses
     * @throws NullPointerException if {@code boundingBox} is {@code null}
     * @throws IllegalArgumentException if {@code boundingBox} has an area of
     * (approximately) 0
     */
    public UIElement(Rectangle boundingBox, Mesh mesh, Texture2D texture) {
        if (boundingBox == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "boundingBox"));
        if (Maths.approx0(boundingBox.getArea()))
            throw new IllegalArgumentException(LocaleUtils.format("global.Math.Rectangle.ZeroArea", "boundingBox"));
        
        this.boundingBox = new Rectangle(boundingBox);
        
        if (mesh == null || texture == null)
            this.graphics = null;
        else {
            this.graphics = UIRenderable.build(mesh, texture);
            this.graphics.transform.setScale(new Vector3(boundingBox.getWidth(), boundingBox.getHeight(), 1.0));
            this.graphics.transform.setTranslation(new Vector3(boundingBox.getMidpointX(), boundingBox.getMidpointY(), 0.0));
        }
    }
    
    /**
     * Gets the parent UI element of this element. A return value of {@code null}
     * indicates that this element has no parent.
     * @return the {@code UIElement} that this element is a child of, or {@code null}
     * if no parent exists
     */
    public final @Nullable UIElement getParent() {
        return this.parent;
    }
    
    /**
     * Gets the child UI elements of this element. The returned collection is an
     * unmodifiable view of the internal collection of children.
     * 
     * @return the unmodifiable collection of {@code UIElement} instances that this
     * element is the parent of
     */
    public final Collection<UIElement> getChildren() {
        return Collections.unmodifiableCollection(this.children);
    }
    
    /**
     * Sets a texture of this DEFAULT_UI element, if it has any graphics.
     * @param texture the texture to add to the element graphics
     */
    protected final void setTexture(Texture2D texture) {
        if (this.graphics != null)
            this.graphics.setTexture(texture);
    }
    
    /**
     * Gets the relative position of the origin point of this element. The origin point 
     * of an element is defined to be lower-left corner of the bounding box of that
     * element.<p>
     * 
     * The relative position is defined to be relative to the origin point of the parent
     * element. If this element has no parent, then the window origin is used instead;
     * thus, this is identical to {@link getAbsoluteOrigin()} in this case.<p>
     * 
     * This is measured in window space; see the javadocs for {@link InputManager} for
     * more information.
     * 
     * @return the origin of this element relative to it's parent
     */
    public final Vector2 getRelativeOrigin() {
        return new Vector2(this.boundingBox.getLowerX(), this.boundingBox.getLowerY());
    }
    
    /**
     * Sets the relative position of the origin point of this element.
     * 
     * @see getRelativeOrigin() - for more information
     * @param relativeOrigin the new relative position of the origin
     */
    protected final void setRelativeOrigin(Vector2 relativeOrigin) {
        this.boundingBox.offsetX(relativeOrigin.getX() - this.boundingBox.getLowerX());
        this.boundingBox.offsetY(relativeOrigin.getY() - this.boundingBox.getLowerY());
    
        doSetPos(parent == null ? Vector2.ZERO : parent.getAbsoluteOrigin());
    }
    
    /**
     * Gets the absolute position of the origin point of this element. The origin point 
     * of an element is defined to be lower-left corner of the bounding box of that
     * element.<p>
     * 
     * This is measured in window space; see the javadocs for {@link InputManager} for
     * more information.
     * 
     * @return the origin of this element relative to the window space origin
     */
    public final Vector2 getAbsoluteOrigin() {
        if (this.parent == null)
            return getRelativeOrigin();
        
        return this.parent.getAbsoluteOrigin().add(getRelativeOrigin());
    }
    
    /**
     * Gets the bounding box of this element, which is defined to be the area which the
     * element takes up on the screen, and is used to handle user mouse inputs and
     * collision calculations with other UI elements. This is relative to the origin
     * point of the parent element. If this element has no parent, then the window
     * origin is used instead; thus, this is identical to {@link getAbsoluteBoundingBox()}
     * in this case.<p>
     * 
     * The bounding box uses window space; see the javadocs for {@link InputManager} for
     * more information.
     * 
     * @return the relative {@link Rectangle} bounding box of this element
     */
    public final Rectangle getRelativeBoundingBox() {
        return new Rectangle(this.boundingBox);
    }
    
    /**
     * Sets the relative bounding box of this element.
     * 
     * @see getRelativeBoundingBox() - for more information
     * @param boundingBox the new relative bounding box
     * @throws IllegalArgumentException if {@code boundingBox} has an area of
     * (approximately) 0
     */
    protected final void setRelativeBoundingBox(Rectangle boundingBox) {
        if (Maths.approx0(boundingBox.getArea()))
            throw new IllegalArgumentException(LocaleUtils.format("global.Math.Rectangle.ZeroArea", "boundingBox"));
        
        this.boundingBox = new Rectangle(boundingBox);
        
        if (this.graphics != null)
            this.graphics.transform.setScale(new Vector3(this.boundingBox.getWidth(), this.boundingBox.getHeight(), 1.0));
    
        doSetPos(parent == null ? Vector2.ZERO : parent.getAbsoluteOrigin());
    }
    
    /**
     * Gets the absolute bounding box of the element.<p>
     * 
     * The bounding box uses window space; see the javadocs for {@link InputManager} for
     * more information.
     * 
     * @return the absolute {@link Rectangle} bounding box of this element
     */
    public final Rectangle getAbsoluteBoundingBox() {
        Vector2 absOrigin = getAbsoluteOrigin();
        
        return new Rectangle(absOrigin.getX(), absOrigin.getY(), absOrigin.getX() + this.boundingBox.getWidth(), absOrigin.getY() + this.boundingBox.getHeight());
    }
    
    @Override
    protected final boolean doClose() {
        if (this.graphics != null)
            this.graphics.close();
        
        this.children.forEach(Closable::close);
        
        InputManager.ELEMENTS.remove(this);
        InputManager.ROOT_ELEMENTS.remove(this);
        if (InputManager.FOCUSED_ELEMENT == this)
            InputManager.FOCUSED_ELEMENT = null;
        
        return true;
    }
    
    /**
     * Handles an input.<p>
     * 
     * Once the handling operations are complete, the return value indicates how to
     * proceed next with the input; a value of {@link Result#PASS} indicates that the 
     * input should be passed to the children of this instance. The children are placed
     * and held in a queue where they will process the input in the same manner; hence,
     * this algorithm traverses the element tree using
     * <a href="https://en.wikipedia.org/wiki/Breadth-first_search">breadth-first traversal</a>.
     * Implementations of this method should thus not be concerned with any other DEFAULT_UI
     * element.<p>
     * 
     * Note that it is considered an error for an implementation to return {@code null}.
     * 
     * @param input the {@link Input} to handle
     * @return the {@link Result} of the handling algorithm
     */
    protected abstract Result handleInput(Input input);
    
    protected final void setParent(UIElement parent) {
        if (parent == this.parent) return;
        
        if (this.parent == null) InputManager.ROOT_ELEMENTS.remove(this);
        else                     this.parent.children.remove(this);
        
        this.parent = parent;
        
        if (this.parent == null) InputManager.ROOT_ELEMENTS.add(this);
        else                     this.parent.children.add(this);
        
        doSetPos(parent.getAbsoluteOrigin());
    }
    
    private void doSetPos(Vector2 parentOrigin) {
        Vector2 origin = getRelativeOrigin().add(parentOrigin);
        
        if (this.graphics != null)
            this.graphics.transform.setTranslation(new Vector3(origin.getX() + (this.boundingBox.getWidth() / 2), origin.getY() + (this.boundingBox.getHeight() / 2), 0.0));
        
        children.forEach((child) -> child.doSetPos(origin));
    }
}
