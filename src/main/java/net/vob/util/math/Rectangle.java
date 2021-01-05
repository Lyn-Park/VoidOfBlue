package net.vob.util.math;

import net.vob.util.logging.LocaleUtils;

/**
 * Class for rectangles. A rectangle is defined by 4 double values, indicating the
 * position of the 4 sides of the rectangle. It also has width, height and area, which
 * have their standard mathematical definitions.
 */
public final class Rectangle {
    private double xL, yL, xH, yH;
    private boolean readonly = false;
    
    /**
     * Constructs a rectangle with the given parameters.
     * @param xL the smaller of the two X coordinates
     * @param yL the smaller of the two Y coordinates
     * @param xH the larger of the two X coordinates
     * @param yH the larger of the two Y coordinates
     */
    public Rectangle(double xL, double yL, double xH, double yH) {
        this.xL = Math.min(xL, xH);
        this.yL = Math.min(yL, yH);
        this.xH = Math.max(xL, xH);
        this.yH = Math.max(yL, yH);
    }
    
    /**
     * Copy constructor. The new rectangle is functionally identical to the given one.
     * @param rect the rectangle to copy
     */
    public Rectangle(Rectangle rect) {
        this.xL = rect.xL;
        this.yL = rect.yL;
        this.xH = rect.xH;
        this.yH = rect.yH;
    }
    
    /**
     * Sets this instance to be read-only. Note that this is a one-way function; the
     * only way to convert the rectangle back to mutability is through either copying it
     * using the {@linkplain Rectangle(Rectangle) copy constructor}, or via reflection.
     */
    public void readonly() {
        this.readonly = true;
    }
    
    /**
     * Gets the lower X bound of the rectangle.
     * @return the lower X coordinate
     */
    public double getLowerX() {
        return xL;
    }
    
    /**
     * Gets the lower Y bound of the rectangle.
     * @return the lower Y coordinate
     */
    public double getLowerY() {
        return yL;
    }
    
    /**
     * Gets the upper X bound of the rectangle.
     * @return the upper X coordinate
     */
    public double getUpperX() {
        return xH;
    }
    
    /**
     * Gets the upper Y bound of the rectangle.
     * @return the upper Y coordinate
     */
    public double getUpperY() {
        return yH;
    }
    
    /**
     * Gets the midpoint X point of the rectangle.
     * @return the midpoint X coordinate
     */
    public double getMidpointX() {
        return (xL + xH) / 2D;
    }
    
    /**
     * Gets the midpoint Y point of the rectangle.
     * @return the midpoint Y coordinate
     */
    public double getMidpointY() {
        return (yL + yH) / 2D;
    }
    
    /**
     * Gets the width of the rectangle.
     * @return the width
     */
    public double getWidth() {
        return xH - xL;
    }
    
    /**
     * Gets the height of the rectangle.
     * @return the height
     */
    public double getHeight() {
        return yH - yL;
    }
    
    /**
     * Gets the area of the rectangle.
     * @return the area
     */
    public double getArea() {
        return (xH - xL) * (yH - yL);
    }
    
    /**
     * Sets the lower X bound of the rectangle. Note that if the given value is larger
     * than the current upper X bound, then the two values will also be swapped
     * internally (so the current upper X bound becomes the new lower X bound and
     * vice-versa).
     * 
     * @param xL the new lower X coordinate
     * @throws IllegalStateException if this rectangle is read-only
     */
    public void setLowerX(double xL) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Rectangle"));
        
        this.xL = Math.min(xL, xH);
        this.xH = Math.max(xL, xH);
    }
    
    /**
     * Sets the lower Y bound of the rectangle. Note that if the given value is larger
     * than the current upper Y bound, then the two values will also be swapped
     * internally (so the current upper Y bound becomes the new lower Y bound and
     * vice-versa).
     * 
     * @param yL the new lower Y coordinate
     * @throws IllegalStateException if this rectangle is read-only
     */
    public void setLowerY(double yL) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Rectangle"));
        
        this.yL = Math.min(yL, yH);
        this.yH = Math.max(yL, yH);
    }
    
    /**
     * Sets the upper X bound of the rectangle. Note that if the given value is smaller
     * than the current lower X bound, then the two values will also be swapped
     * internally (so the current lower X bound becomes the new upper X bound and
     * vice-versa).
     * 
     * @param xH the new upper X coordinate
     * @throws IllegalStateException if this rectangle is read-only
     */
    public void setUpperX(double xH) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Rectangle"));
        
        this.xL = Math.min(xL, xH);
        this.xH = Math.max(xL, xH);
    }
    
    /**
     * Sets the upper Y bound of the rectangle. Note that if the given value is smaller
     * than the current lower Y bound, then the two values will also be swapped
     * internally (so the current lower Y bound becomes the new upper Y bound and
     * vice-versa).
     * 
     * @param yH the new upper Y coordinate
     * @throws IllegalStateException if this rectangle is read-only
     */
    public void setUpperY(double yH) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Rectangle"));
        
        this.yL = Math.min(yL, yH);
        this.yH = Math.max(yL, yH);
    }
    
    /**
     * Sets the width of the rectangle. This keeps the lower X bound, and sets the
     * upper X bound such that the width of the rectangle is equal to the given value.
     * @param w the new width
     * @throws IllegalStateException if this rectangle is read-only
     * @throws IllegalArgumentException if {@code w} is less than 0
     */
    public void setWidth(double w) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Rectangle"));
        if (w < 0)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>=", "w", w, 0));
        
        this.xH = xL + w;
    }
    
    /**
     * Sets the height of the rectangle. This keeps the lower Y bound, and sets the
     * upper Y bound such that the height of the rectangle is equal to the given value.
     * @param h the new height
     * @throws IllegalStateException if this rectangle is read-only
     * @throws IllegalArgumentException if {@code h} is less than 0
     */
    public void setHeight(double h) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Rectangle"));
        if (h < 0)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>=", "h", h, 0));
        
        this.yH = yL + h;
    }
    
    /**
     * Offsets the rectangle along the X axis.
     * @param off the amount to offset by
     * @throws IllegalStateException if this rectangle is read-only
     */
    public void offsetX(double off) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Rectangle"));
        
        this.xL += off;
        this.xH += off;
    }
    
    /**
     * Offsets the rectangle along the Y axis.
     * @param off the amount to offset by
     * @throws IllegalStateException if this rectangle is read-only
     */
    public void offsetY(double off) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Rectangle"));
        
        this.yL += off;
        this.yH += off;
    }
    
    /**
     * Checks if the given vector is contained within this rectangle. As a special
     * case, passing {@code null} as the parameter returns {@code false}.
     * @param vec the vector to check
     * @return {@code true} if {@code vec} is non-{@code null} and is contained
     * within the rectangle, {@code false} otherwise
     */
    public boolean contains(Vector2 vec) {
        return vec != null &&
               this.xL <= vec.getX() && this.xH >= vec.getX() &&
               this.yL <= vec.getY() && this.yH >= vec.getY();
    }
    
    /**
     * Checks if the given rectangle is fully contained within this rectangle. This
     * means that all the bounds of the given rectangle do not lie outside of this
     * rectangle. As a special case, passing {@code null} as the parameter returns
     * {@code false}.
     * 
     * @param rect the rectangle to check
     * @return {@code true} if {@code rect} is non-{@code null} and is contained
     * within the rectangle, {@code false} otherwise
     */
    public boolean contains(Rectangle rect) {
        return rect != null &&
               this.xL <= rect.xL && this.xH >= rect.xH &&
               this.yL <= rect.yL && this.yH >= rect.yH;
    }
    
    /**
     * Checks if the given rectangle intersects this rectangle. This means that any 
     * part of the given rectangle lies inside this rectangle. As a special case,
     * passing {@code null} as the parameter returns {@code false}.
     * 
     * @param rect the rectangle to check
     * @return {@code true} if {@code rect} is non-{@code null} and any part of 
     * the given rectangle (including the edges) intersects with this rectangle,
     * {@code false} otherwise
     */
    public boolean areIntersecting(Rectangle rect) {
        return rect != null &&
               this.xL <= rect.xH && this.xH >= rect.xL &&
               this.yL <= rect.yH && this.yH >= rect.yL;
    }
    
    /**
     * Gets the intersection of this rectangle and the given rectangle. This is
     * defined as the largest rectangle such that it is entirely contained within
     * both rectangles. Note that if the given rectangle is {@code null}, or the
     * two rectangles are not intersecting, then {@code null} is returned instead.
     * 
     * @param rect the rectangle to intersect with
     * @return the {@code Rectangle} representing the intersection of the two
     * rectangles, or {@code null} if no such rectangle exists or {@code rect} is
     * {@code null}
     */
    public Rectangle intersection(Rectangle rect) {
        if (areIntersecting(rect))
            return new Rectangle(Math.max(this.xL, rect.xL), Math.max(this.yL, rect.yL),
                                 Math.min(this.xH, rect.xH), Math.min(this.yH, rect.yH));
        
        return null;
    }
    
    /**
     * Gets the union of this rectangle and the given rectangle. This is defined as
     * the smallest rectangle such that both rectangles are entirely contained within
     * it. Note that if the given rectangle is {@code null}, then {@code null} is
     * returned instead.
     * 
     * @param rect the rectangle to union with
     * @return the {@code Rectangle} representing the union of the two rectangles,
     * or {@code null} if {@code rect} is {@code null}
     */
    public Rectangle union(Rectangle rect) {
        if (rect != null)
            return new Rectangle(Math.min(this.xL, rect.xL), Math.min(this.yL, rect.yL),
                                 Math.max(this.xH, rect.xH), Math.max(this.yH, rect.yH));
        
        return null;
    }
}
