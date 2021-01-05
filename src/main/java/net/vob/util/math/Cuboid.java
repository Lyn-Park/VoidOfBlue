package net.vob.util.math;

import net.vob.util.logging.LocaleUtils;

/**
 * Class for cuboids. A cuboid is defined by 6 double values, indicating the position
 * of the 6 sides of the cuboid. It also has width, height, depth, surface area and
 * volume, each of which have their standard mathematical definitions.
 */
public final class Cuboid {
    private double xL, yL, zL, xH, yH, zH;
    private boolean readonly = false;
    
    /**
     * Constructs a cuboid with the given parameters.
     * @param xL the smaller of the two X coordinates
     * @param yL the smaller of the two Y coordinates
     * @param zL the smaller of the two Z coordinates
     * @param xH the larger of the two X coordinates
     * @param yH the larger of the two Y coordinates
     * @param zH the larger of the two Z coordinates
     */
    public Cuboid(double xL, double yL, double zL, double xH, double yH, double zH) {
        this.xL = Math.min(xL, xH);
        this.yL = Math.min(yL, yH);
        this.zL = Math.min(zL, zH);
        this.xH = Math.max(xL, xH);
        this.yH = Math.max(yL, yH);
        this.zL = Math.max(zL, zH);
    }
    
    /**
     * Copy constructor. The new cuboid is functionally identical to the given one.
     * @param cubd the cuboid to copy
     */
    public Cuboid(Cuboid cubd) {
        this.xL = cubd.xL;
        this.yL = cubd.yL;
        this.zL = cubd.zL;
        this.xH = cubd.xH;
        this.yH = cubd.yH;
        this.zH = cubd.zH;
    }
    
    /**
     * Sets this instance to be read-only. Note that this is a one-way function; the
     * only way to convert the cuboid back to mutability is through either copying it
     * using the {@linkplain Cuboid(Cuboid) copy constructor}, or via reflection.
     */
    public void readonly() {
        this.readonly = true;
    }
    
    /**
     * Gets the lower X bound of the cuboid.
     * @return the lower X coordinate
     */
    public double getLowerX() {
        return xL;
    }
    
    /**
     * Gets the lower Y bound of the cuboid.
     * @return the lower Y coordinate
     */
    public double getLowerY() {
        return yL;
    }
    
    /**
     * Gets the lower Z bound of the cuboid.
     * @return the lower Z coordinate
     */
    public double getLowerZ() {
        return zL;
    }
    
    /**
     * Gets the upper X bound of the cuboid.
     * @return the upper X coordinate
     */
    public double getUpperX() {
        return xH;
    }
    
    /**
     * Gets the upper Y bound of the cuboid.
     * @return the upper Y coordinate
     */
    public double getUpperY() {
        return yH;
    }
    
    /**
     * Gets the upper Z bound of the cuboid.
     * @return the lower Z coordinate
     */
    public double getUpperZ() {
        return zH;
    }
    
    /**
     * Gets the midpoint X point of the cuboid.
     * @return the midpoint X coordinate
     */
    public double getMidpointX() {
        return (xL + xH) / 2D;
    }
    
    /**
     * Gets the midpoint Y point of the cuboid.
     * @return the midpoint Y coordinate
     */
    public double getMidpointY() {
        return (yL + yH) / 2D;
    }
    
    /**
     * Gets the midpoint Z point of the cuboid.
     * @return the midpoint Z coordinate
     */
    public double getMidpointZ() {
        return (zL + zH) / 2D;
    }
    
    /**
     * Gets the width of the cuboid.
     * @return the width
     */
    public double getWidth() {
        return xH - xL;
    }
    
    /**
     * Gets the height of the cuboid.
     * @return the height
     */
    public double getHeight() {
        return yH - yL;
    }
    
    /**
     * Gets the depth of the cuboid.
     * @return the depth
     */
    public double getDepth() {
        return zH - zL;
    }
    
    /**
     * Gets the surface area of the cuboid.
     * @return the surface area
     */
    public double getSurfaceArea() {
        double w = xH - xL, h = yH - yL, d = zH - zL;
        
        return 2 * ((w * h) + (w * d) + (h * d));
    }
    
    /**
     * Gets the volume of the cuboid.
     * @return the volume
     */
    public double getVolume() {
        return (xH - xL) * (yH - yL) * (zH - zL);
    }
    
    /**
     * Sets the lower X bound of the cuboid. Note that if the given value is larger
     * than the current upper X bound, then the two values will also be swapped
     * internally (so the current upper X bound becomes the new lower X bound and
     * vice-versa).
     * 
     * @param xL the new lower X coordinate
     * @throws IllegalStateException if this cuboid is read-only
     */
    public void setLowerX(double xL) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        
        this.xL = Math.min(xL, xH);
        this.xH = Math.max(xL, xH);
    }
    
    /**
     * Sets the lower Y bound of the cuboid. Note that if the given value is larger
     * than the current upper Y bound, then the two values will also be swapped
     * internally (so the current upper Y bound becomes the new lower Y bound and
     * vice-versa).
     * 
     * @param yL the new lower Y coordinate
     * @throws IllegalStateException if this cuboid is read-only
     */
    public void setLowerY(double yL) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        
        this.yL = Math.min(yL, yH);
        this.yH = Math.max(yL, yH);
    }
    
    /**
     * Sets the lower Z bound of the cuboid. Note that if the given value is larger
     * than the current upper Z bound, then the two values will also be swapped
     * internally (so the current upper Z bound becomes the new lower Z bound and
     * vice-versa).
     * 
     * @param zL the new lower Z coordinate
     * @throws IllegalStateException if this cuboid is read-only
     */
    public void setLowerZ(double zL) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        
        this.zL = Math.min(zL, zH);
        this.zH = Math.max(zL, zH);
    }
    
    /**
     * Sets the upper X bound of the cuboid. Note that if the given value is smaller
     * than the current lower X bound, then the two values will also be swapped
     * internally (so the current lower X bound becomes the new upper X bound and
     * vice-versa).
     * 
     * @param xH the new upper X coordinate
     * @throws IllegalStateException if this cuboid is read-only
     */
    public void setUpperX(double xH) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        
        this.xL = Math.min(xL, xH);
        this.xH = Math.max(xL, xH);
    }
    
    /**
     * Sets the upper Y bound of the cuboid. Note that if the given value is smaller
     * than the current lower Y bound, then the two values will also be swapped
     * internally (so the current lower Y bound becomes the new upper Y bound and
     * vice-versa).
     * 
     * @param yH the new upper Y coordinate
     * @throws IllegalStateException if this cuboid is read-only
     */
    public void setUpperY(double yH) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        
        this.yL = Math.min(yL, yH);
        this.yH = Math.max(yL, yH);
    }
    
    /**
     * Sets the upper Z bound of the cuboid. Note that if the given value is smaller
     * than the current lower Z bound, then the two values will also be swapped
     * internally (so the current lower Z bound becomes the new upper Z bound and
     * vice-versa).
     * 
     * @param zH the new upper Z coordinate
     * @throws IllegalStateException if this cuboid is read-only
     */
    public void setUpperZ(double zH) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        
        this.zL = Math.min(zL, zH);
        this.zH = Math.max(zL, zH);
    }
    
    /**
     * Sets the width of the cuboid. This keeps the lower X bound, and sets the
     * upper X bound such that the width of the cuboid is equal to the given value.
     * @param w the new width
     * @throws IllegalStateException if this cuboid is read-only
     * @throws IllegalArgumentException if {@code w} is less than 0
     */
    public void setWidth(double w) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        if (w < 0)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>=", "w", w, 0));
        
        this.xH = xL + w;
    }
    
    /**
     * Sets the height of the cuboid. This keeps the lower Y bound, and sets the
     * upper Y bound such that the height of the cuboid is equal to the given value.
     * @param h the new height
     * @throws IllegalStateException if this cuboid is read-only
     * @throws IllegalArgumentException if {@code h} is less than 0
     */
    public void setHeight(double h) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        if (h < 0)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>=", "h", h, 0));
        
        this.yH = yL + h;
    }
    
    /**
     * Sets the depth of the cuboid. This keeps the lower Z bound, and sets the
     * upper Z bound such that the depth of the cuboid is equal to the given value.
     * @param d the new depth
     * @throws IllegalStateException if this cuboid is read-only
     * @throws IllegalArgumentException if {@code d} is less than 0
     */
    public void setDepth(double d) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        if (d < 0)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>=", "d", d, 0));
        
        this.zH = zL + d;
    }
    
    /**
     * Offsets the cuboid along the X axis.
     * @param off the amount to offset by
     * @throws IllegalStateException if this cuboid is read-only
     */
    public void offsetX(double off) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        
        this.xL += off;
        this.xH += off;
    }
    
    /**
     * Offsets the cuboid along the Y axis.
     * @param off the amount to offset by
     * @throws IllegalStateException if this cuboid is read-only
     */
    public void offsetY(double off) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        
        this.yL += off;
        this.yH += off;
    }
    
    /**
     * Offsets the cuboid along the Z axis.
     * @param off the amount to offset by
     * @throws IllegalStateException if this cuboid is read-only
     */
    public void offsetZ(double off) {
        if (readonly)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Cuboid"));
        
        this.zL += off;
        this.zH += off;
    }
    
    /**
     * Checks if the given vector is contained within this cuboid. As a special
     * case, passing {@code null} as the parameter returns {@code false}.
     * @param vec the vector to check
     * @return {@code true} if {@code vec} is non-{@code null} and is contained
     * within the cuboid, {@code false} otherwise
     */
    public boolean contains(Vector3 vec) {
        return vec != null &&
               this.xL <= vec.getX() && this.xH >= vec.getX() &&
               this.yL <= vec.getY() && this.yH >= vec.getY() &&
               this.zL <= vec.getZ() && this.zH >= vec.getZ();
    }
    
    /**
     * Checks if the given cuboid is fully contained within this cuboid. This
     * means that all the bounds of the given cuboid do not lie outside of this
     * cuboid. As a special case, passing {@code null} as the parameter returns
     * {@code false}.
     * 
     * @param cubd the cuboid to check
     * @return {@code true} if {@code rect} is non-{@code null} and is contained
     * within the cuboid, {@code false} otherwise
     */
    public boolean contains(Cuboid cubd) {
        return cubd != null &&
               this.xL <= cubd.xL && this.xH >= cubd.xH &&
               this.yL <= cubd.yL && this.yH >= cubd.yH &&
               this.zL <= cubd.zL && this.zH >= cubd.zH;
    }
    
    /**
     * Checks if the given cuboid intersects this cuboid. This means that any 
     * part of the given cuboid lies inside this cuboid. As a special case,
     * passing {@code null} as the parameter returns {@code false}.
     * 
     * @param cubd the cuboid to check
     * @return {@code true} if {@code rect} is non-{@code null} and any part of 
     * the given cuboid (including the edges) intersects with this cuboid,
     * {@code false} otherwise
     */
    public boolean areIntersecting(Cuboid cubd) {
        return cubd != null &&
               this.xL <= cubd.xH && this.xH >= cubd.xL &&
               this.yL <= cubd.yH && this.yH >= cubd.yL &&
               this.zL <= cubd.zH && this.zH >= cubd.zL;
    }
    
    /**
     * Gets the intersection of this cuboid and the given cuboid. This is
     * defined as the largest cuboid such that it is entirely contained within
     * both cuboids. Note that if the given cuboid is {@code null}, or the
     * two cuboids are not intersecting, then {@code null} is returned instead.
     * 
     * @param cubd the cuboid to intersect with
     * @return the {@code Cuboid} representing the intersection of the two
     * cuboids, or {@code null} if no such cuboid exists or {@code rect} is
     * {@code null}
     */
    public Cuboid intersection(Cuboid cubd) {
        if (areIntersecting(cubd))
            return new Cuboid(Math.max(this.xL, cubd.xL), Math.max(this.yL, cubd.yL), Math.max(this.zL, cubd.zL),
                              Math.min(this.xH, cubd.xH), Math.min(this.yH, cubd.yH), Math.min(this.zH, cubd.zH));
        
        return null;
    }
    
    /**
     * Gets the union of this cuboid and the given cuboid. This is defined as
     * the smallest cuboid such that both cuboids are entirely contained within
     * it. Note that if the given cuboid is {@code null}, then {@code null} is
     * returned instead.
     * 
     * @param cubd the cuboid to union with
     * @return the {@code Cuboid} representing the union of the two cuboids,
     * or {@code null} if {@code rect} is {@code null}
     */
    public Cuboid union(Cuboid cubd) {
        if (cubd != null)
            return new Cuboid(Math.min(this.xL, cubd.xL), Math.min(this.yL, cubd.yL), Math.min(this.zL, cubd.zL),
                              Math.max(this.xH, cubd.xH), Math.max(this.yH, cubd.yH), Math.max(this.zH, cubd.zH));
        
        return null;
    }
}
