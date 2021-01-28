package net.vob.util.math;

import net.vob.util.logging.LocaleUtils;

/**
 * A special sub-class of {@code Vector} that has exactly 2 elements.
 * 
 * @author Lyn-Park
 */
public final class Vector2 extends Vector {
    /** The read-only zero vector. Corresponds to (0, 0). */
    public static final Vector2 ZERO =  new Vector2();
    /** Unit cartesian read-only coordinate vector. Corresponds to (1, 0), i.e. the positive-X axis. */
    public static final Vector2 RIGHT = new Vector2(1, 0);
    /** Unit cartesian read-only coordinate vector. Corresponds to (-1, 0), i.e. the negative-X axis. */
    public static final Vector2 LEFT =  new Vector2(-1, 0);
    /** Unit cartesian read-only coordinate vector. Corresponds to (0, 1), i.e. the positive-Y axis. */
    public static final Vector2 UP =    new Vector2(0, 1);
    /** Unit cartesian read-only coordinate vector. Corresponds to (0, -1), i.e. the negative-Y axis. */
    public static final Vector2 DOWN =  new Vector2(0, -1);
    /** The read-only ones vector. Corresponds to (1, 1). */
    public static final Vector2 ONES = new Vector2(1, 1);
    
    static {
        ZERO.immutable();
        RIGHT.immutable();
        LEFT.immutable();
        UP.immutable();
        DOWN.immutable();
        ONES.immutable();
    }
    
    /**
     * Constructs an empty vector of size 3.
     */
    public Vector2() {
        super(2);
    }
    
    /**
     * Constructs a new vector from the given elements.
     * @param x The first element
     * @param y The second element
     */
    public Vector2(double x, double y) {
        super(new double[]{ x, y });
    }
    
    /**
     * Constructs a new vector from the given array of elements.
     * @param els The elements to use for the vector
     * @throws IllegalArgumentException If the given array does not have exactly 2
     * elements
     */
    public Vector2(double[] els) {
        super(els);
        
        if (els.length != 2)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", els.length, 2));
    }
    
    /**
     * Copy constructor. The new vector is functionally identical to the given one.
     * Note that the vector parameter is passed as a {@link Matrix} instance for
     * convenience.
     * @param mat The vector to copy
     * @throws IllegalArgumentException If the given matrix is not a vector, as it
     * has multiple columns, or if the given vector does not have exactly 2 elements
     */
    public Vector2(Matrix mat) {
        super(mat);
        
        if (mat.rows != 2)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", mat.rows, 2));
    }
    
    /**
     * Gets the first element of the vector. Functionally identical to 
     * {@link getElement(int) getElement(0)}, but this method was added for increased 
     * readability.
     * @return 
     */
    public double getX() {
        return elements[0];
    }
    
    /**
     * Gets the second element of the vector. Functionally identical to 
     * {@link getElement(int) getElement(1)}, but this method was added for increased 
     * readability.
     * @return 
     */
    public double getY() {
        return elements[1];
    }
    
    /**
     * Sets the first element of the vector. Functionally identical to 
     * {@link setElement(int, double) setElement(0, x)}, but this method was added for 
     * increased readability.
     * @param x The new value of the first element
     * @throws IllegalStateException if this vector is readonly
     */
    public void setX(double x) {
        setElement(0, 0, x);
    }
    
    /**
     * Sets the second element of the vector. Functionally identical to 
     * {@link setElement(int, double) setElement(1, x)}, but this method was added for 
     * increased readability.
     * @param y The new value of the second element
     * @throws IllegalStateException if this vector is readonly
     */
    public void setY(double y) {
        setElement(1, 0, y);
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * Note that this method is overridden to return an instance of {@code Vector2}, 
     * as it will never return any other type of vector if it does not throw an 
     * exception.
     * 
     * @return {@inheritDoc}
     * @throws ArithmeticException {@inheritDoc}
     */
    @Override
    public Vector2 normalized() {
        return new Vector2(super.normalized());
    }
    
    @Override
    public Vector2 add(Matrix mat) {
        return new Vector2(super.add(mat));
    }
    
    @Override
    public Vector2 sub(Matrix mat) {
        return new Vector2(super.sub(mat));
    }
    
    @Override
    public Vector2 mul(double scalar) {
        return new Vector2(super.mul(scalar));
    }
    
    @Override
    public Vector2 elementMul(Matrix mat) {
        return new Vector2(super.elementMul(mat));
    }
    
    @Override
    public Vector2 elementInv() {
        return new Vector2(super.elementInv());
    }
    
    @Override
    public Vector2 elementNeg() {
        return new Vector2(super.elementNeg());
    }
}
