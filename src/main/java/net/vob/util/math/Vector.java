package net.vob.util.math;

import java.util.Arrays;
import java.util.stream.IntStream;
import net.vob.util.logging.LocaleUtils;

/**
 * A sub-class of {@code Matrix} that has only 1 column.
 * 
 * @author Lyn-Park
 */
public class Vector extends Matrix {
    /**
     * Constructs an empty vector of the given size.
     * @param size 
     */
    public Vector(int size) {
        super(size, 1);
    }
    
    /**
     * Constructs a new vector from the given elements. These elements will be the
     * elements of the vector, and the vector's size will be set to the number of
     * elements.
     * @param elements 
     */
    public Vector(double... elements) {
        super(elements.length, 1, elements, false);
    }
    
    /**
     * Copy constructor. The new vector is functionally identical to the given one.
     * Note that the vector parameter is passed as a {@link Matrix} instance for
     * convenience.
     * @param mat The vector to copy
     * @throws IllegalArgumentException If the given matrix is not a vector, as it
     * has multiple columns
     */
    public Vector(Matrix mat) {
        super(mat);
        
        if (mat.columns != 1)
            throw new IllegalArgumentException(LocaleUtils.format("Vector.NonVectorInput", mat.columns));
    }
    
    /**
     * Gets the size of the vector. Functionally identical to {@link getRows()},
     * but this method was added for increased readability.
     * @return 
     */
    public int getSize() {
        return rows;
    }
    
    /**
     * Gets the element at the given position in the vector. Functionally identical
     * to {@link #getElement(int, int) getElement(i, 0)}, but this method was added
     * for increased readability.
     * @param i
     * @return 
     */
    public double getElement(int i) {
        return elements[i];
    }
    
    /**
     * Sets the element at the given position in the vector. Functionally identical
     * to {@link #setElement(int, int, double) setElement(i, 0, value)}, but this 
     * method was added for increased readability.
     * @param i
     * @param value
     * @throws IllegalStateException if this vector is readonly
     */
    public void setElement(int i, double value) {
        setElement(i, 0, value);
    }
    
    /**
     * Resizes the vector to the given size. This is achieved by copying the 
     * internal array to a new vector; the array is either padded with zeroes or
     * truncated to fit the new size.
     * @param newSize The size of the new vector
     * @return The resized copy of this vector
     */
    public Vector resize(int newSize) {
        if (newSize == rows)
            return new Vector(this);
        else
            return new Vector(Arrays.copyOf(elements, newSize));
    }
    
    /**
     * Calculates the squared magnitude of this vector.
     * @return The magnitude squared
     */
    public double magnitudeSqr() {
        return Arrays.stream(elements).map((e) -> e * e).sum();
    }
    
    /**
     * Calculates the magnitude of this vector.
     * @return The magnitude
     */
    public double magnitude() {
        return Math.sqrt(magnitudeSqr());
    }
    
    /**
     * Calculates the normalized version of this vector.
     * @return The normalized vector
     * @throws ArithmeticException if the magnitude of this vector is 0
     */
    public Vector normalized() {
        if (Maths.approx0(magnitudeSqr()))
            throw new ArithmeticException(LocaleUtils.format("global.Math.DivideByZero"));
        
        return mul(1 / magnitude());
    }
    
    /**
     * Calculates the dot product between this vector and the given vector. Note
     * that the vector parameter is passed as a {@link Matrix} instance for
     * convenience.
     * @param mat The other vector operand
     * @return The dot product
     * @throws IllegalArgumentException If the given matrix is not a vector, as it
     * has multiple columns, or the given vector is of incompatible size with this
     * vector
     */
    public double dot(Matrix mat) {
        if (mat.columns != 1)
            throw new IllegalArgumentException(LocaleUtils.format("Vector.NonVectorInput", mat.columns));
        if (rows != mat.rows)
            throw new IllegalArgumentException(LocaleUtils.format("global.Math.IllegalMatrixRowNumber", rows, mat.rows));
        
        return IntStream.range(0, rows).mapToDouble((i) -> elements[i] * mat.elements[i]).sum();
    }
    
    /**
     * Calculates the squared distance between this vector and the given vector,
     * which is defined as the squared magnitude of the difference of the two vectors.
     * Note that the vector parameter is passed as a {@link Matrix} instance for
     * convenience.
     * @param vec The other vector operand
     * @return The distance squared
     */
    public double distanceSqr(Matrix vec) {
        return sub(vec).magnitudeSqr();
    }
    
    /**
     * Calculates the distance between this vector and the given vector, which is 
     * defined as the magnitude of the difference of the two vectors. Note that the 
     * vector parameter is passed as a {@link Matrix} instance for convenience.
     * @param vec The other vector operand
     * @return The distance
     */
    public double distance(Matrix vec) {
        return sub(vec).magnitude();
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * Note that this method is overridden to return an instance of this class, as 
     * it will never return any other type of matrix if it does not throw an 
     * exception.
     * 
     * @param mat
     * @return
     * @throws IllegalArgumentException If the given matrix parameter is of
     * incompatible size with this matrix
     */
    @Override
    public Vector add(Matrix mat) {
        return new Vector(super.add(mat));
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * Note that this method is overridden to return an instance of this class, as 
     * it will never return any other type of matrix if it does not throw an 
     * exception.
     * 
     * @param mat
     * @return
     * @throws IllegalArgumentException If the given matrix parameter is of
     * incompatible size with this matrix
     */
    @Override
    public Vector sub(Matrix mat) {
        return new Vector(super.sub(mat));
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * Note that this method is overridden to return an instance of this class, as 
     * it will never return any other type of matrix if it does not throw an 
     * exception.
     * 
     * @param scalar
     * @return
     * @throws IllegalArgumentException If the given matrix parameter is of
     * incompatible size with this matrix
     */
    @Override
    public Vector mul(double scalar) {
        return new Vector(super.mul(scalar));
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * Note that this method is overridden to return an instance of this class, as 
     * it will never return any other type of matrix if it does not throw an 
     * exception.
     * 
     * @param mat
     * @return
     * @throws IllegalArgumentException If the given matrix parameter is of
     * incompatible size with this matrix
     */
    @Override
    public Vector elementMul(Matrix mat) {
        return new Vector(super.elementMul(mat));
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * Note that this method is overridden to return an instance of this class, as 
     * it will never return any other type of matrix if it does not throw an 
     * exception.
     * 
     * @return The result of the inversion
     */
    @Override
    public Vector elementInv() {
        return new Vector(super.elementInv());
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * Note that this method is overridden to return an instance of this class, as 
     * it will never return any other type of matrix if it does not throw an 
     * exception.
     * 
     * @return The result of the negation
     */
    @Override
    public Vector elementNeg() {
        return new Vector(super.elementNeg());
    }
    
    @Override
    public String toString() {
        return "Vector (" + rows + ")" + Arrays.toString(elements);
    }
}
