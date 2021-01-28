package net.vob.util.math;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import net.vob.util.logging.LocaleUtils;

/**
 * Base class for 2D matrices and vectors.
 * 
 * @author Lyn-Park
 */
public class Matrix {
    protected final int rows, columns;
    protected final double[] elements;
    private boolean immutable;
    
    /**
     * Constructs an empty square matrix of the given size.
     * @param size 
     */
    public Matrix(int size) {
        this(size, size, new double[size * size], false);
    }
    
    /**
     * Constructs an empty rectangular matrix of the given sizes.
     * @param rows
     * @param columns 
     */
    public Matrix(int rows, int columns) {
        this(rows, columns, new double[rows * columns], false);
    }
    
    /**
     * Copy constructor. The new matrix is functionally identical to the given one,
     * apart from that the new matrix is not immutable, regardless of whether the given
     * matrix was. All operations on the new matrix do not affect the original instance,
     * and vice versa.
     * 
     * @param mat 
     */
    public Matrix(Matrix mat) {
        this(mat.rows, mat.columns, Arrays.copyOf(mat.elements, mat.elements.length), false);
    }
    
    protected Matrix(int rows, int columns, double[] elements, boolean immutable) {
        if (rows <= 0)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>=", "rows", rows, 1));
        if (columns <= 0)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>=", "columns", columns, 1));
        if (elements.length != rows * columns)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", elements.length, rows * columns));
        
        this.rows = rows;
        this.columns = columns;
        this.elements = elements;
        this.immutable = immutable;
    }
    
    /**
     * Sets this instance to be read-only. Note that this is a one-way function; the
     * only way to convert the matrix back to mutability is through either copying it
     * using the {@linkplain Matrix(Matrix) copy constructor}, or via reflection.
     * Also note that this immutability only affects {@link setElement(int, int, double)};
     * all other operations function as normal.
     */
    public void immutable() {
        immutable = true;
    }
    
    /**
     * Gets the full array of elements this matrix is holding. The element array is
     * returned in row-major order.
     * @return 
     */
    public double[] getElements() {
        return elements;
    }
    
    /**
     * Gets the element at the given position in the matrix.
     * @param row
     * @param column
     * @return 
     */
    public double getElement(int row, int column) {
        return elements[column + (row * columns)];
    }
    
    /**
     * Sets the element at the given position in the matrix.
     * @param row
     * @param column
     * @param value 
     * @throws IllegalStateException if this matrix is read-only
     */
    public void setElement(int row, int column, double value) {
        if (immutable)
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Readonly", "Matrix"));
        
        elements[column + (row * columns)] = value;
    }
    
    /**
     * Gets the number of rows in this matrix.
     * @return 
     */
    public int getNumRows() {
        return rows;
    }
    
    /**
     * Gets the number of columns in this matrix.
     * @return 
     */
    public int getNumColumns() {
        return columns;
    }
    
    /**
     * Performs standard matrix addition with this matrix and the given matrix.
     * @param mat The other matrix operand
     * @return The result of the addition
     * @throws IllegalArgumentException If the given matrix parameter is of
     * incompatible size with this matrix
     */
    public Matrix add(Matrix mat) {
        if (rows != mat.rows)
            throw new IllegalArgumentException(LocaleUtils.format("Matrix.InvalidRowNumber", mat.rows, rows));
        if (columns != mat.columns)
            throw new IllegalArgumentException(LocaleUtils.format("Matrix.InvalidColumnNumber", mat.columns, columns));
        
        double[] newEls = new double[rows * columns];
        
        for (int i = 0; i < newEls.length; i++)
            newEls[i] = elements[i] + mat.elements[i];
        
        return new Matrix(rows, columns, newEls, false);
    }
    
    /**
     * Performs standard matrix subtraction with this matrix and the given matrix. 
     * Note the given matrix parameter acts as the right-hand operand.
     * @param mat The other matrix operand
     * @return The result of the subtraction
     * @throws IllegalArgumentException If the given matrix parameter is of
     * incompatible size with this matrix
     */
    public Matrix sub(Matrix mat) {
        if (rows != mat.rows)
            throw new IllegalArgumentException(LocaleUtils.format("Matrix.InvalidRowNumber", mat.rows, rows));
        if (columns != mat.columns)
            throw new IllegalArgumentException(LocaleUtils.format("Matrix.InvalidColumnNumber", mat.columns, columns));
        
        double[] newEls = new double[rows * columns];
        
        for (int i = 0; i < newEls.length; i++)
            newEls[i] = elements[i] - mat.elements[i];
        
        return new Matrix(rows, columns, newEls, false);
    }
    
    /**
     * Performs standard matrix-scalar multiplication with this matrix and the given
     * scalar.
     * @param scalar The scalar operand
     * @return The result of the multiplication
     */
    public Matrix mul(double scalar) {
        double[] newEls = new double[rows * columns];
        
        for (int i = 0; i < newEls.length; i++)
            newEls[i] = elements[i] * scalar;
        
        return new Matrix(rows, columns, newEls, false);
    }
    
    /**
     * Performs standard matrix-matrix multiplication with this matrix and the given
     * matrix. Note that the given matrix parameter acts as the right-hand operand.
     * @param mat The other matrix operand
     * @return The result of the multiplication
     * @throws IllegalArgumentException If the given matrix parameter is of
     * incompatible size with this matrix
     */
    public Matrix mul(Matrix mat) {
        if (columns != mat.rows)
            throw new IllegalArgumentException(LocaleUtils.format("Matrix.InvalidRowNumber", mat.rows, columns));
        
        Matrix out = new Matrix(rows, mat.columns);
        
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < mat.columns; c++) {
                double element = 0;
                
                for (int z = 0; z < columns; z++)
                    element += getElement(r, z) * mat.getElement(z, c);
                
                out.setElement(r, c, element);
            }
        }
        
        return out;
    }
    
    /**
     * Performs element-wise multiplication with this matrix and the given matrix,
     * also known as the Hadamard product.
     * @param mat The other matrix operand
     * @return The result of the multiplication
     * @throws IllegalArgumentException If the given matrix parameter is of
     * incompatible size with this matrix
     */
    public Matrix elementMul(Matrix mat) {
        if (rows != mat.rows)
            throw new IllegalArgumentException(LocaleUtils.format("Matrix.InvalidRowNumber", mat.rows, rows));
        if (columns != mat.columns)
            throw new IllegalArgumentException(LocaleUtils.format("Matrix.InvalidColumnNumber", mat.columns, columns));
        
        Matrix out = new Matrix(rows, columns);
        
        for (int i = 0; i < elements.length; ++i)
            out.elements[i] = elements[i] * mat.elements[i];
        
        return out;
    }
    
    /**
     * Performs an element-wise inversion of this matrix. This is defined as a
     * matrix with the multiplicative inverses of the elements of this matrix; i.e.
     * for any element in this matrix {@code i}, the corresponding element in the
     * returned matrix will be {@code 1 / i}.
     * 
     * @return The result of the inversion
     */
    public Matrix elementInv() {
        Matrix out = new Matrix(rows, columns);
        
        for (int i = 0; i < elements.length; ++i)
            out.elements[i] = 1d / elements[i];
        
        return out;
    }
    
    /**
     * Performs an element-wise negation of this matrix. This is defined as a
     * matrix with the additive inverses of the elements of this matrix; i.e. for
     * any element in this matrix {@code i}, the corresponding element in the
     * returned matrix will be {@code -i}.
     * 
     * @return The result of the negation
     */
    public Matrix elementNeg() {
        Matrix out = new Matrix(rows, columns);
        
        for (int i = 0; i < elements.length; ++i)
            out.elements[i] = -elements[i];
        
        return out;
    }
    
    /**
     * Calculates the transpose of this matrix. Note that this calculation is done
     * using a simple naive algorithm, and thus will be fairly slow.
     * @return The transposed matrix
     */
    public Matrix transpose() {
        Matrix out = new Matrix(columns, rows);
        
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < columns; c++)
                out.setElement(c, r, getElement(r, c));
        
        return out;
    }
    
    /**
     * Calculates the trace of this matrix, which is the sum of all entries on the
     * leading diagonal.
     * @return The trace of the matrix
     * @throws IllegalStateException If this matrix is not setW square matrix
     */
    public double trace() {
        if (rows != columns)
            throw new IllegalStateException(LocaleUtils.format("Matrix.trace.NonSquareMatrix"));
        
        double trace = 0;
        for (int i = 0; i < rows; i++)
            trace += getElement(i, i);
        
        return trace;
    }
    
    /**
     * Calculates the rank of this matrix, which is the number of linearly
     * independent rows, or equivalently the number of linearly independent columns.<p>
     * 
     * Note that due to the various floating arithmetic operations that occur,
     * {@link Maths#approx0(double)} is used in place of equality comparisons.
     * 
     * @return The rank of the matrix
     */
    public int rank() {
        Matrix A = new Matrix(this);
        int R = 0;
        
        for (int c = 0; c < columns; c++) {
            int r = R;
            
            while (r < rows && Maths.approx0(A.getElement(r, c)))
                r++;
            
            if (r == rows)
                continue;
            
            swapRow(A, r, R);
            
            double a = A.getElement(R, c);
            mulRow(A, R, 1/a);
            
            for (r = R+1; r < rows; r++) {
                a = A.getElement(r, c);
                addRow(A, r, R, -a);
            }
            
            R++;
        }
        
        R = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (!Maths.approx0(A.getElement(r, c))) {
                    R++;
                    break;
                }
            }
        }
        
        return R;
    }
    
    /**
     * Calculates the inverse of this matrix, which is the unique matrix {@code B}
     * such that {@code AB = BA = I}, where {@code A} is this matrix and {@code I}
     * is the identity matrix.<p>
     * 
     * Note that due to the various floating arithmetic operations that occur,
     * {@link Maths#approx0(double)} is used in place of equality comparisons when
     * checking for zero values. If equality comparisons are preferred instead, use 
     * {@link inverseExact()}.
     * 
     * @return The inverse of this matrix, or null if this matrix is singular
     * (i.e. not invertible)
     * @throws IllegalStateException If this matrix is not setW square matrix
     */
    public Matrix inverse() {
        if (rows != columns)
            throw new IllegalStateException(LocaleUtils.format("Matrix.InverseNonSquareMatrix"));
        
        Matrix A = new Matrix(this), B = identity(rows);
        
        for (int c = 0; c < rows; c++) {
            int r = c;
            
            while (r < rows && Maths.approx0(A.getElement(r, c)))
                r++;
            
            if (r == rows)
                return null;
            
            swapRow(A, r, c); swapRow(B, r, c);
            
            double a = A.getElement(c, c);
            mulRow(A, c, 1/a); mulRow(B, c, 1/a);
            
            for (r = 0; r < rows; r++) {
                if (r == c) continue;
                
                a = A.getElement(r, c);
                addRow(A, r, c, -a); addRow(B, r, c, -a);
            }
        }
        
        return B;
    }
    
    /**
     * Calculates the inverse of this matrix, which is the unique matrix {@code B}
     * such that {@code AB = BA = I}, where {@code A} is this matrix and {@code I}
     * is the identity matrix.<p>
     * 
     * Note that when checking for zero values, equality comparisons are used in place 
     * of approximation functions such as {@link Maths#approx0(double)}. If 
     * approximation comparisons are preferable instead, use {@link inverse()}.
     * 
     * @return The inverse of this matrix, or null if this matrix is singular
     * (i.e. not invertible)
     * @throws IllegalStateException If this matrix is not setW square matrix
     */
    public Matrix inverseExact() {
        if (rows != columns)
            throw new IllegalStateException(LocaleUtils.format("Matrix.InverseNonSquareMatrix"));
        
        Matrix A = new Matrix(this), B = identity(rows);
        
        for (int c = 0; c < rows; c++) {
            int r = c;
            
            while (r < rows && A.getElement(r, c) == 0)
                r++;
            
            if (r == rows)
                return null;
            
            swapRow(A, r, c); swapRow(B, r, c);
            
            double a = A.getElement(c, c);
            mulRow(A, c, 1/a); mulRow(B, c, 1/a);
            
            for (r = 0; r < rows; r++) {
                if (r == c) continue;
                
                a = A.getElement(r, c);
                addRow(A, r, c, -a); addRow(B, r, c, -a);
            }
        }
        
        return B;
    }
    
    /**
     * Writes the matrix elements to the given buffer. The buffer is not flipped
     * by this operation. Note that this does not perform any writing of the matrix
     * sizes.<p>
     * 
     * The {@code transpose} parameter allows the matrix to be transposed during
     * writing; this transposition is thus more efficient than the use of
     * {@link transpose()}.
     * 
     * @param buf The buffer to write to
     * @param transpose {@code false} if the matrix should be written to the
     * buffer in row-major order, or {@code true} for column-major order
     */
    public void writeToDoubleBuffer(DoubleBuffer buf, boolean transpose) {
        if (transpose)
            for (int j = 0; j < columns; ++j)
                for (int i = 0; i < rows; i++)
                    buf.put(getElement(i, j));
            
        else
            for (int i = 0; i < rows; ++i)
                for (int j = 0; j < columns; j++)
                    buf.put(getElement(i, j));
    }
    
    /**
     * Writes the matrix elements to the given buffer. The buffer is not flipped
     * by this operation. Note that this does not perform any writing of the matrix
     * sizes.<p>
     * 
     * The {@code transpose} parameter allows the matrix to be transposed during
     * writing; this transposition is thus more efficient than the use of
     * {@link transpose()}.
     * 
     * @param buf The buffer to write to
     * @param transpose {@code false} if the matrix should be written to the
     * buffer in row-major order, or {@code true} for column-major order
     */
    public void writeToFloatBuffer(FloatBuffer buf, boolean transpose) {
        if (transpose)
            for (int j = 0; j < columns; ++j)
                for (int i = 0; i < rows; i++)
                    buf.put((float)getElement(i, j));
            
        else
            for (int i = 0; i < rows; ++i)
                for (int j = 0; j < columns; j++)
                    buf.put((float)getElement(i, j));
    }
    
    /**
     * Writes the matrix elements to the given buffer. The buffer is not flipped
     * by this operation. Note that this does not perform any writing of the matrix
     * sizes.<p>
     * 
     * The {@code transpose} parameter allows the matrix to be transposed during
     * writing; this transposition is thus more efficient than the use of
     * {@link transpose()}.
     * 
     * @param buf The buffer to write to
     * @param transpose {@code false} if the matrix should be written to the
     * buffer in row-major order, or {@code true} for column-major order
     */
    public void writeToDoubleBuffer(ByteBuffer buf, boolean transpose) {
        if (transpose)
            for (int j = 0; j < columns; ++j)
                for (int i = 0; i < rows; i++)
                    buf.putDouble(getElement(i, j));
            
        else
            for (int i = 0; i < rows; ++i)
                for (int j = 0; j < columns; j++)
                    buf.putDouble(getElement(i, j));
    }
    
    /**
     * Writes the matrix elements to the given buffer. The buffer is not flipped
     * by this operation. Note that this does not perform any writing of the matrix
     * sizes.<p>
     * 
     * The {@code transpose} parameter allows the matrix to be transposed during
     * writing; this transposition is thus more efficient than the use of
     * {@link transpose()}.
     * 
     * @param buf The buffer to write to
     * @param transpose {@code false} if the matrix should be written to the
     * buffer in row-major order, or {@code true} for column-major order
     */
    public void writeToFloatBuffer(ByteBuffer buf, boolean transpose) {
        if (transpose)
            for (int j = 0; j < columns; ++j)
                for (int i = 0; i < rows; i++)
                    buf.putFloat((float)getElement(i, j));
            
        else
            for (int i = 0; i < rows; ++i)
                for (int j = 0; j < columns; j++)
                    buf.putFloat((float)getElement(i, j));
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Matrix)) return false;
        
        Matrix m = (Matrix)o;
        
        return rows == m.rows && columns == m.columns &&
               Arrays.equals(elements, m.elements);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + this.rows;
        hash = 61 * hash + this.columns;
        hash = 61 * hash + Arrays.hashCode(this.elements);
        return hash;
    }
    
    @Override
    public String toString() {
        return "Matrix (" + rows + "," + columns + ")" + Arrays.toString(elements);
    }
    
    /**
     * Constructs an identity matrix of the given size.
     * @param size The size of the new matrix
     * @return The identity matrix
     */
    public static Matrix identity(int size) {
        Matrix I = new Matrix(size);
        
        for (int i = 0; i < size; i++)
            I.setElement(i, i, 1);
        
        return I;
    }
    
    /**
     * Constructs setW 4x4 matrix representing the given 3D translation operation. 
     * @param translation The amount to translate by
     * @return The appropriate translation matrix
     */
    public static Matrix getTranslationMatrix(Vector3 translation) {
        Matrix m = identity(4);
        
        m.setElement(0, 3, translation.getX());
        m.setElement(1, 3, translation.getY());
        m.setElement(2, 3, translation.getZ());
        
        return m;
    }
    
    /**
     * Constructs a 4x4 matrix representing the given 3D rotation operation. Note
     * that the quaternion is normalized beforehand.
     * @param rotation The quaternion representing the rotation
     * @return The appropriate rotation matrix
     */
    public static Matrix getRotationMatrix(Quaternion rotation) {
        rotation = rotation.normalized();
        Matrix m = identity(4);
        
        double x = rotation.getW(), y = rotation.getX(), z = rotation.getY(), w = rotation.getZ();
        double xy = x * y, xz = x * z, xw = x * w;
        double yy = y * y, yz = y * z, yw = y * w;
        double zz = z * z, zw = z * w;
        double ww = w * w;
        
        m.setElement(0, 0, 1 - (2 * (zz + ww)));
        m.setElement(1, 1, 1 - (2 * (yy + ww)));
        m.setElement(2, 2, 1 - (2 * (yy + zz)));
        m.setElement(0, 1, 2 * (yz - xw));
        m.setElement(0, 2, 2 * (yw + xz));
        m.setElement(1, 0, 2 * (yz + xw));
        m.setElement(1, 2, 2 * (zw - xy));
        m.setElement(2, 0, 2 * (yw - xz));
        m.setElement(2, 1, 2 * (zw + xy));
        
        return m;
    }
    
    /**
     * Constructs setW 4x4 matrix representing the given 3D scaling operation.
     * @param scale The amount to scale by
     * @return The appropriate scaling matrix
     */
    public static Matrix getScalingMatrix(Vector3 scale) {
        Matrix m = identity(4);
        
        m.setElement(0, 0, scale.getX());
        m.setElement(1, 1, scale.getY());
        m.setElement(2, 2, scale.getZ());
        
        return m;
    }
    
    private static void addRow(Matrix m, int r1, int r2, double s) {
        if (s == 0) return;
        
        for (int c = 0; c < m.columns; c++) {
            double z = m.getElement(r1, c) + (m.getElement(r2, c) * s);
            m.setElement(r1, c, z);
        }
    }
    
    private static void mulRow(Matrix m, int r, double s) {
        if (s == 1) return;
        
        for (int c = 0; c < m.columns; c++)
            m.setElement(r, c, m.getElement(r, c) * s);
    }
    
    private static void swapRow(Matrix m, int r1, int r2) {
        if (r1 == r2) return;
        
        for (int c = 0; c < m.columns; c++) {
            double t = m.getElement(r1, c);
            m.setElement(r1, c, m.getElement(r2, c));
            m.setElement(r2, c, t);
        }
    }
}
