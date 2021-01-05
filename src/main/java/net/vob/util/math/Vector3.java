package net.vob.util.math;

import net.vob.util.logging.LocaleUtils;

/**
 * A special sub-class of {@code Vector} that has exactly 3 elements.
 */
public final class Vector3 extends Vector {
    /** The read-only zero vector. Corresponds to (0, 0, 0). */
    public static final Vector3 ZERO =     new Vector3();
    /** Unit cartesian read-only coordinate vector. Corresponds to (1, 0, 0), i.e. the positive-X axis. */
    public static final Vector3 RIGHT =    new Vector3(1, 0, 0);
    /** Unit cartesian read-only coordinate vector. Corresponds to (-1, 0, 0), i.e. the negative-X axis. */
    public static final Vector3 LEFT =     new Vector3(-1, 0, 0);
    /** Unit cartesian read-only coordinate vector. Corresponds to (0, 1, 0), i.e. the positive-Y axis. */
    public static final Vector3 UP =       new Vector3(0, 1, 0);
    /** Unit cartesian read-only coordinate vector. Corresponds to (0, -1, 0), i.e. the negative-Y axis. */
    public static final Vector3 DOWN =     new Vector3(0, -1, 0);
    /** Unit cartesian read-only coordinate vector. Corresponds to (0, 0, 1), i.e. the positive-Z axis. */
    public static final Vector3 FORWARD =  new Vector3(0, 0, 1);
    /** Unit cartesian read-only coordinate vector. Corresponds to (0, 0, -1), i.e. the negative-Z axis. */
    public static final Vector3 BACKWARD = new Vector3(0, 0, -1);
    /** The read-only ones vector. Corresponds to (1, 1, 1). */
    public static final Vector3 ONES =     new Vector3(1, 1, 1);
    
    static {
        ZERO.readonly();
        RIGHT.readonly();
        LEFT.readonly();
        UP.readonly();
        DOWN.readonly();
        FORWARD.readonly();
        BACKWARD.readonly();
        ONES.readonly();
    }
    
    /**
     * Constructs an empty vector of size 3.
     */
    public Vector3() {
        super(3);
    }
    
    /**
     * Constructs a new vector from the given elements.
     * @param x The first element
     * @param y The second element
     * @param z The third element
     */
    public Vector3(double x, double y, double z) {
        super(new double[]{ x, y, z });
    }
    
    /**
     * Constructs a new vector from the given array of elements.
     * @param els The elements to use for the vector
     * @throws IllegalArgumentException If the given array does not have exactly 3
     * elements
     */
    public Vector3(double[] els) {
        super(els);
        
        if (els.length != 3)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", els.length, 3));
    }
    
    /**
     * Copy constructor. The new vector is functionally identical to the given one.
     * Note that the vector parameter is passed as a {@link Matrix} instance for
     * convenience.
     * @param mat The vector to copy
     * @throws IllegalArgumentException If the given matrix is not a vector, as it
     * has multiple columns, or if the given vector does not have exactly 3 elements
     */
    public Vector3(Matrix mat) {
        super(mat);
        
        if (mat.rows != 3)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", mat.rows, 3));
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
     * Gets the third element of the vector. Functionally identical to 
     * {@link getElement(int) getElement(2)}, but this method was added for increased 
     * readability.
     * @return 
     */
    public double getZ() {
        return elements[2];
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
     * Sets the third element of the vector. Functionally identical to 
     * {@link setElement(int, double) setElement(2, x)}, but this method was added for 
     * increased readability.
     * @param z The new value of the third element
     * @throws IllegalStateException if this vector is readonly
     */
    public void setZ(double z) {
        setElement(2, 0, z);
    }
    
    /**
     * Calculates the cross product between this vector and the given vector. Note that
     * the given vector parameter acts as the right-hand operand.
     * @param mat The other vector operand
     * @return The cross product
     */
    public Vector3 cross(Vector3 mat) {
        return new Vector3((elements[1] * mat.elements[2]) - (elements[2] * mat.elements[1]),
                           (elements[2] * mat.elements[0]) - (elements[0] * mat.elements[2]),
                           (elements[0] * mat.elements[1]) - (elements[1] * mat.elements[0]));
    }
    
    /**
     * Rotates this vector by the rotation encoded as the given quaternion. The quaternion
     * is normalized prior to it's use.
     * @param qua The rotation quaternion to use
     * @return The result of the rotation
     */
    public Vector3 rotate(Quaternion qua) {
        return new Quaternion(0, this).conjugation(qua.normalized()).getVector();
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * Note that this method is overridden to return an instance of {@code Vector3}, 
     * as it will never return any other type of vector if it does not throw an 
     * exception.
     * 
     * @return {@inheritDoc}
     * @throws ArithmeticException {@inheritDoc}
     */
    @Override
    public Vector3 normalized() {
        return new Vector3(super.normalized());
    }
    
    @Override
    public Vector3 add(Matrix mat) {
        return new Vector3(super.add(mat));
    }
    
    @Override
    public Vector3 sub(Matrix mat) {
        return new Vector3(super.sub(mat));
    }
    
    @Override
    public Vector3 mul(double scalar) {
        return new Vector3(super.mul(scalar));
    }
    
    @Override
    public Vector3 elementMul(Matrix mat) {
        return new Vector3(super.elementMul(mat));
    }
    
    @Override
    public Vector3 elementInv() {
        return new Vector3(super.elementInv());
    }
    
    @Override
    public Vector3 elementNeg() {
        return new Vector3(super.elementNeg());
    }
}
