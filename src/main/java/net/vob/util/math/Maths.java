package net.vob.util.math;

import java.util.Arrays;
import net.vob.util.logging.LocaleUtils;

/**
 * A loose collection of utility methods that handle more basic mathematical and numeric
 * operations than {@link Math}, as well several operations regarding matrices and 
 * vectors.
 * 
 * @author Lyn-Park
 */
public final class Maths {
    private Maths() {}
    
    /**
     * The double value used as the threshold for the {@code approx(xx, xx)} and
     * {@code approx0(xx)} methods.
     */
    public static final double DELTA = 1e-8;
    /**
     * The double value that can be used to convert degrees to radians via
     * multiplication.
     */
    public static final double DEG_TO_RAD = Math.PI / 180d;
    /**
     * The double value that can be used to convert radians to degrees via
     * multiplication.
     */
    public static final double RAD_TO_DEG = 180d / Math.PI;
    
    /**
     * Checks if the two given {@code double} values are approximately equal. This is
     * achieved by taking the absolute value of their difference and performing a 
     * less-than comparison to the given delta value. Special cases:
     * 
     * <ul>
     *  <li> If either argument is infinite, then the result is a direct equality 
     * comparison between the two values ({@code a == b}). </li>
     *  <li> If either argument is NaN, then the result is false. </li>
     * </ul>
     * 
     * Note that this method is not suitable for use as an equivalence relation, as
     * several assumptions on such relations are not applicable; in particular, this
     * method is not transitive ({@code approx(a, b, d)} and {@code approx(b, c, d)} 
     * does <b>not</b>, in general, imply {@code approx(a, c, d)}).
     * 
     * @param a The first argument to compare
     * @param b The second argument to compare
     * @param delta The delta threshold value 
     * @return True if the two arguments are approximately equal, false otherwise
     */
    public static boolean approx(double a, double b, double delta) {
        if (Double.isInfinite(a) || Double.isInfinite(b))
            return a == b;
        if (Double.isNaN(a) || Double.isNaN(b))
            return false;
        
        return Math.abs(a - b) < delta;
    }
    
    /**
     * Checks if the two given {@code double} values are approximately equal. This is
     * achieved by taking the absolute value of their difference and performing a 
     * less-than comparison to {@link DELTA}. Special cases:
     * 
     * <ul>
     *  <li> If either argument is infinite, then the result is a direct equality 
     * comparison between the two values ({@code a == b}). </li>
     *  <li> If either argument is NaN, then the result is false. </li>
     * </ul>
     * 
     * Note that this method is not suitable for use as an equivalence relation, as
     * several assumptions on such relations are not applicable; in particular, this
     * method is not transitive ({@code approx(a, b)} and {@code approx(b, c)} does
     * <b>not</b>, in general, imply {@code approx(a, c)}).
     * 
     * @param a The first argument to compare
     * @param b The second argument to compare
     * @return True if the two arguments are approximately equal, false otherwise
     */
    public static boolean approx(double a, double b) {
        return approx(a, b, DELTA);
    }
    
    /**
     * Checks if the given vectors are approximately equal. This is achieved by taking
     * the squared distance between them and performing a less-than comparison to the
     * given delta value squared. The smallest-sized vector is padded with zeroes 
     * before the operation. As a special case, if either vector parameter is
     * {@code null}, then this method returns {@code true} only if the other vector
     * parameter is {@code null}.<p>
     * 
     * Note that this method is not suitable for use as an equivalence relation, as
     * several assumptions on such relations are not applicable; in particular, this
     * method is not transitive ({@code approx(a, b, d)} and {@code approx(b, c, d)} 
     * does <b>not</b>, in general, imply {@code approx(a, c, d)}).
     * 
     * @param vec1 The first vector operand
     * @param vec2 The second vector operand
     * @param delta The delta threshold value
     * @return True if the given vectors are approximately equal, false otherwise
     */
    public static boolean approx(Vector vec1, Vector vec2, double delta) {
        if (vec1 == null) return vec2 == null;
        else if (vec2 == null) return false;
        
        if (vec1.getSize() > vec2.getSize())
            vec2 = vec2.resize(vec1.getSize());
        if (vec1.getSize() < vec2.getSize())
            vec1 = vec1.resize(vec2.getSize());
        
        return vec1.distanceSqr(vec2) < delta * delta;
    }
    
    /**
     * Checks if the given vectors are approximately equal. This is achieved by taking
     * the squared distance between them and performing a less-than comparison to 
     * {@link DELTA} squared. The smallest-sized vector is padded with zeroes before 
     * the operation. As a special case, if either vector parameter is {@code null},
     * then this method returns {@code true} only if the other vector parameter is
     * {@code null}.<p>
     * 
     * Note that this method is not suitable for use as an equivalence relation, as
     * several assumptions on such relations are not applicable; in particular, this
     * method is not transitive ({@code approx(a, b)} and {@code approx(b, c)} does
     * <b>not</b>, in general, imply {@code approx(a, c)}).
     * 
     * @param vec1 The first vector operand
     * @param vec2 The second vector operand
     * @return True if the given vectors are approximately equal, false otherwise
     */
    public static boolean approx(Vector vec1, Vector vec2) {
        return approx(vec1, vec2, DELTA);
    }
    
    /**
     * Checks if the given rectangles are approximately equal. This is achieved by
     * taking the absolute difference of each X and Y coordinate within the rectangles
     * and performing less-than comparisons to the given delta value. As a special case,
     * if either rectangle parameter is {@code null}, then this method returns
     * {@code true} only if the other rectangle parameter is {@code null}.<p>
     * 
     * Note that this method is not suitable for use as an equivalence relation, as
     * several assumptions on such relations are not applicable; in particular, this
     * method is not transitive ({@code approx(a, b, d)} and {@code approx(b, c, d)} 
     * does <b>not</b>, in general, imply {@code approx(a, c, d)}).
     * 
     * @param rect1 The first rectangle operand
     * @param rect2 The second rectangle operand
     * @param delta The delta threshold value
     * @return True if the given rectangles are approximately equal, false otherwise
     */
    public static boolean approx(Rectangle rect1, Rectangle rect2, double delta) {
        if (rect1 == null) return rect2 == null;
        else if (rect2 == null) return false;
        
        return Math.abs(rect1.getLowerX() - rect2.getLowerX()) < delta &&
               Math.abs(rect1.getLowerY() - rect2.getLowerY()) < delta &&
               Math.abs(rect1.getUpperX() - rect2.getUpperX()) < delta &&
               Math.abs(rect1.getUpperY() - rect2.getUpperY()) < delta;
    }
    
    /**
     * Checks if the given rectangles are approximately equal. This is achieved by
     * taking the absolute difference of each X and Y coordinate within the rectangles
     * and performing less-than comparisons to {@link DELTA}. As a special case, if
     * either rectangle parameter is {@code null}, then this method returns
     * {@code true} only if the other rectangle parameter is {@code null}.<p>
     * 
     * Note that this method is not suitable for use as an equivalence relation, as
     * several assumptions on such relations are not applicable; in particular, this
     * method is not transitive ({@code approx(a, b)} and {@code approx(b, c)} does
     * <b>not</b>, in general, imply {@code approx(a, c)}).
     * 
     * @param rect1 The first rectangle operand
     * @param rect2 The second rectangle operand
     * @return True if the given rectangles are approximately equal, false otherwise
     */
    public static boolean approx(Rectangle rect1, Rectangle rect2) {
        return approx(rect1, rect2, DELTA);
    }
    
    /**
     * Checks if the given {@code double} value is approximately equal to 0. This is
     * achieved by performing a less-than comparison between the absolute value of the
     * argument and the absolute value of the given delta. As a special case, if the
     * argument is infinite or NaN, then the result is {@code false}.
     * 
     * @param a The argument to compare to 0
     * @param delta The delta threshold value
     * @return True if the argument is approximately 0, false otherwise
     */
    public static boolean approx0(double a, double delta) {
        if (Double.isInfinite(a) || Double.isNaN(a))
            return false;
        
        return Math.abs(a) < Math.abs(delta);
    }
    
    /**
     * Checks if the given {@code double} value is approximately equal to 0. This is
     * achieved by performing a less-than comparison between the absolute value of the
     * argument and {@link DELTA}. As a special case, if the argument is infinite or
     * NaN, then the result is {@code false}.
     * 
     * @param a The argument to compare to 0
     * @return True if the argument is approximately 0, false otherwise
     */
    public static boolean approx0(double a) {
        return approx0(a, DELTA);
    }
    
    /**
     * Checks if the given vector is approximately equal to the zero vector. This is
     * achieved by taking the square magnitude and performing a less-than comparison 
     * to the given delta value squared. As a special case, if the vector parameter
     * is {@code null}, then this method returns {@code false}.
     * 
     * @param vec The vector operand to compare to 0
     * @param delta The delta threshold value
     * @return True if the given vector is approximately 0, false otherwise
     */
    public static boolean approx0(Vector vec, double delta) {
        return vec != null && vec.magnitudeSqr() < delta * delta;
    }
    
    /**
     * Checks if the given vector is approximately equal to the zero vector. This is
     * achieved by taking the square magnitude and performing a less-than comparison 
     * to {@link DELTA} squared. As a special case, if the vector parameter is
     * {@code null}, then this method returns {@code false}.
     * 
     * @param vec The vector operand to compare to 0
     * @return True if the given vector is approximately 0, false otherwise
     */
    public static boolean approx0(Vector vec) {
        return approx0(vec, DELTA);
    }
    
    /**
     * Checks if the given rectangle is approximately equal to the zero rectangle.
     * This is achieved by taking the area of the rectangle and performing a
     * less-than comparison to the given delta. As a special case, if the rectangle
     * parameter is {@code null}, then this method returns {@code false}.
     * 
     * @param rect The rectangle operand to compare to 0
     * @param delta The delta threshold value
     * @return True if the given rectangle is approximately 0, false otherwise
     */
    public static boolean approx0(Rectangle rect, double delta) {
        return rect != null && rect.getArea() < delta;
    }
    
    /**
     * Checks if the given rectangle is approximately equal to the zero rectangle.
     * This is achieved by taking the area of the rectangle and performing a
     * less-than comparison to {@link DELTA}. As a special case, if the rectangle
     * parameter is {@code null}, then this method returns {@code false}.
     * 
     * @param rect The rectangle operand to compare to 0
     * @return True if the given rectangle is approximately 0, false otherwise
     */
    public static boolean approx0(Rectangle rect) {
        return approx0(rect, DELTA);
    }
    
    /**
     * Clamps the given value to within two bound values, using their natural
     * ordering. As a special case, passing invalid bounds (i.e. {@code lowerBound}
     * is comparably greater than {@code upperBound}) will simply return
     * {@code value} without modification.
     * 
     * @param lowerBound The lower bound of the clamp operation
     * @param value The value to clamp
     * @param upperBound The upper bound of the clamp operation
     * @return The clamped value
     */
    public static int clamp(int lowerBound, int value, int upperBound) {
        if (lowerBound > upperBound)
            return value;
        if (lowerBound > value)
            return lowerBound;
        if (upperBound < value)
            return upperBound;
        
        return value;
    }
    
    /**
     * Clamps the given value to within two bound values, using their natural
     * ordering. As a special case, passing invalid bounds (i.e. {@code lowerBound}
     * is comparably greater than {@code upperBound}) will simply return
     * {@code value} without modification.
     * 
     * @param lowerBound The lower bound of the clamp operation
     * @param value The value to clamp
     * @param upperBound The upper bound of the clamp operation
     * @return The clamped value
     */
    public static long clamp(long lowerBound, long value, long upperBound) {
        if (lowerBound > upperBound)
            return value;
        if (lowerBound > value)
            return lowerBound;
        if (upperBound < value)
            return upperBound;
        
        return value;
    }
    
    /**
     * Clamps the given value to within two bound values, using their natural
     * ordering. As special cases, passing invalid bounds (i.e. {@code lowerBound}
     * is comparably greater than {@code upperBound}, or either bound is passed
     * {@link Float#NaN NaN}) will simply return {@code value} without modification.
     * 
     * @param lowerBound The lower bound of the clamp operation
     * @param value The value to clamp
     * @param upperBound The upper bound of the clamp operation
     * @return The clamped value
     */
    public static float clamp(float lowerBound, float value, float upperBound) {
        if (lowerBound > upperBound || lowerBound == Float.NaN || upperBound == Float.NaN)
            return value;
        if (lowerBound > value)
            return lowerBound;
        if (upperBound < value)
            return upperBound;
        
        return value;
    }
    
    /**
     * Clamps the given value to within two bound values, using their natural
     * ordering. As special cases, passing invalid bounds (i.e. {@code lowerBound}
     * is comparably greater than {@code upperBound}, or either bound is passed
     * {@link Double#NaN NaN}) will simply return {@code value} without modification.
     * 
     * @param lowerBound The lower bound of the clamp operation
     * @param value The value to clamp
     * @param upperBound The upper bound of the clamp operation
     * @return The clamped value
     */
    public static double clamp(double lowerBound, double value, double upperBound) {
        if (lowerBound > upperBound || lowerBound == Double.NaN || upperBound == Double.NaN)
            return value;
        if (lowerBound > value)
            return lowerBound;
        if (upperBound < value)
            return upperBound;
        
        return value;
    }
    
    /**
     * A generic helper method that clamps the given value to within two bound values,
     * using their natural ordering. This isn't strictly a numerical method as it can
     * take any {@link Comparable} type parameter.<p>
     * 
     * Note that, as special cases:
     * <ul>
     *  <li>Passing {@code null} for any parameter will simply return {@code value}
     *      without modification.</li>
     *  <li>Otherwise, passing invalid bounds (i.e. {@code lowerBound} is comparably
     *      greater than {@code upperBound}) will return {@code null}.</li>
     * </ul>
     * 
     * @param <T> The {@link Comparable} type of the arguments
     * @param lowerBound The lower bound of the clamp operation
     * @param value The value to clamp
     * @param upperBound The upper bound of the clamp operation
     * @return The clamped value
     */
    public static <T extends Comparable> T clamp(T lowerBound, T value, T upperBound) {
        if (lowerBound == null || upperBound == null || value == null)
            return value;
        
        if (lowerBound.compareTo(upperBound) > 0)
            return null;
        if (lowerBound.compareTo(value) > 0)
            return lowerBound;
        if (upperBound.compareTo(value) < 0)
            return upperBound;
        
        return value;
    }
    
    /**
     * Gets the mathematical modulus of the given value and modulo arguments.<p>
     * 
     * The modulus of two values, denoted {@code a mod b}, is defined to be the unique
     * number {@code x} that satisfies {@code 0 <= x < b} and {@code a = y*b + x} for
     * some arbitrary integer {@code y}. Note that this definition differentiates the
     * modulus from the Java {@code %} operator <i>and</i> the 
     * {@link Math#floorMod(int, int)} method, as both of these may return a negative 
     * value depending on the signs of the inputs; in contrast, this method is always
     * guaranteed to return a positive value.<p>
     * 
     * Note that passing a mod value of 0 will result in a divide-by-zero
     * {@link ArithmeticException} being thrown.
     * 
     * @param value The value to take the modulo of
     * @param mod The modulus to use
     * @return The appropriate modulo value
     */
    public static int modulus(int value, int mod) {
        if (mod == 0)
            throw new ArithmeticException(LocaleUtils.format("global.Math.DivideByZero"));
        
        int m = value % mod;
        if (m < 0) m += Math.abs(mod);
        return m;
    }
    
    /**
     * Gets the mathematical modulus of the given value and modulo arguments.<p>
     * 
     * The modulus of two values, denoted {@code a mod b}, is defined to be the unique
     * number {@code x} that satisfies {@code 0 <= x < b} and {@code a = y*b + x} for
     * some arbitrary integer {@code y}. Note that this definition differentiates the
     * modulus from the Java {@code %} operator <i>and</i> the
     * {@link Math#floorMod(int, int)} method, as both of these may return a negative
     * value depending on the signs of the inputs; in contrast, this method is always
     * guaranteed to return a positive value.<p>
     * 
     * Note that passing a mod value of 0 will result in a divide-by-zero
     * {@link ArithmeticException} being thrown.
     * 
     * @param value The value to take the modulo of
     * @param mod The modulus to use
     * @return The appropriate modulo value
     */
    public static long modulus(long value, long mod) {
        if (mod == 0)
            throw new ArithmeticException(LocaleUtils.format("global.Math.DivideByZero"));
        
        long m = value % mod;
        if (m < 0) m += Math.abs(mod);
        return m;
    }
    
    /**
     * Gets the mathematical modulus of the given value and modulo arguments.<p>
     * 
     * The modulus of two values, denoted {@code a mod b}, is defined to be the unique
     * number {@code x} that satisfies {@code 0 <= x < b} and {@code a = y*b + x} for
     * some arbitrary integer {@code y}. Note that this definition differentiates the
     * modulus from the Java {@code %} operator <i>and</i> the
     * {@link Math#floorMod(int, int)} method, as both of these may return a negative
     * value depending on the signs of the inputs; in contrast, this method is always
     * guaranteed to return a positive value.<p>
     * 
     * Special cases:
     * <ul>
     *  <li> If either of the input parameters is NaN, then NaN is returned. </li>
     *  <li> If the value parameter is infinite, then 0 is returned. </li>
     *  <li> If the mod parameter is infinite, then the value parameter is returned. </li>
     *  <li> If the mod parameter is 0, then a divide-by-zero {@link ArithmeticException} 
     *       is thrown. </li>
     * </ul>
     * 
     * @param value The value to take the modulo of
     * @param mod The modulus to use
     * @return The appropriate modulo value
     */
    public static float modulus(float value, float mod) {
        if (Float.isNaN(value) || Float.isNaN(mod))
            return Float.NaN;
        if (Float.isInfinite(value))
            return 0;
        if (Float.isInfinite(mod))
            return value;
        if (mod == 0)
            throw new ArithmeticException(LocaleUtils.format("global.Math.DivideByZero"));
        
        float m = value % mod;
        if (m < 0) m += Math.abs(mod);
        return m;
    }
    
    /**
     * Gets the mathematical modulus of the given value and modulo arguments.<p>
     * 
     * The modulus of two values, denoted {@code a mod b}, is defined to be the unique
     * number {@code x} that satisfies {@code 0 <= x < b} and {@code a = y*b + x} for
     * some arbitrary integer {@code y}. Note that this definition differentiates the
     * modulus from the Java {@code %} operator <i>and</i> the
     * {@link Math#floorMod(int, int)} method, as both of these may return a negative
     * value depending on the signs of the inputs; in contrast, this method is always
     * guaranteed to return a positive value.<p>
     * 
     * Special cases:
     * <ul>
     *  <li> If either of the input parameters is NaN, then NaN is returned. </li>
     *  <li> If the value parameter is infinite, then 0 is returned. </li>
     *  <li> If the mod parameter is infinite, then the value parameter is returned. </li>
     *  <li> If the mod parameter is 0, then a divide-by-zero {@link ArithmeticException} 
     *       is thrown. </li>
     * </ul>
     * 
     * @param value The value to take the modulo of
     * @param mod The modulus to use
     * @return The appropriate modulo value
     */
    public static double modulus(double value, double mod) {
        if (Double.isNaN(value) || Double.isNaN(mod))
            return Double.NaN;
        if (Double.isInfinite(value))
            return 0;
        if (Double.isInfinite(mod))
            return value;
        if (mod == 0)
            throw new ArithmeticException(LocaleUtils.format("global.Math.DivideByZero"));
        
        double m = value % mod;
        if (m < 0) m += Math.abs(mod);
        return m;
    }

    /**
     * Packs the given vectors to a matrix. The resulting matrix has a size of m x n,
     * and the following structure:
     *
     * <blockquote><pre> {@code x1 x2 x3 \u2026 xn}
     * {@code y1 y2 y3 \u2026 yn}
     * {@code z1 z2 z3 \u2026 zn}
     * {@code \u22ee  \u22ee  \u22ee  \u22f1  \u22ee}
     * {@code m1 m2 m3 \u2026 mn}</pre></blockquote>
     *
     * ...where m is the number of vectors and n is the size of the largest vector.
     * The other vectors are padded with zeroes to this size.
     *
     * @param vecs The vectors to pack
     * @return The matrix containing the vectors
     */
    public static Matrix packVectorsToMatrixHorizontal(Vector... vecs) {
        int size = Arrays.stream(vecs).map((vec) -> vec.getSize()).reduce(0, Math::max);
        
        for (int i = 0; i < vecs.length; i++)
            vecs[i] = vecs[i].resize(size);
        
        Matrix mat = new Matrix(vecs.length, size);
        for (int r = 0; r < vecs.length; r++)
            for (int c = 0; c < size; c++)
                mat.setElement(r, c, vecs[r].getElement(c));
                
        return mat;
    }

    /**
     * Packs the given vectors to a matrix. The resulting matrix has a size of n x m,
     * and the following structure:
     *
     * <blockquote><pre> {@code x1 y1 z1 \u2026 m1}
     * {@code x2 y2 z2 \u2026 m2}
     * {@code x3 y3 z3 \u2026 m3}
     * {@code \u22ee  \u22ee  \u22ee  \u22f1  \u22ee}
     * {@code xn yn zn \u2026 mn}</pre></blockquote>
     *
     * ...where m is the number of vectors and n is the size of the largest vector.
     * The other vectors are padded with zeroes to this size.
     *
     * @param vecs The vectors to pack
     * @return The matrix containing the vectors
     */
    public static Matrix packVectorsToMatrixVertical(Vector... vecs) {
        int size = Arrays.stream(vecs).map((vec) -> vec.getSize()).reduce(0, Math::max);
        
        for (int i = 0; i < vecs.length; i++)
            vecs[i] = vecs[i].resize(size);
        
        Matrix mat = new Matrix(size, vecs.length);
        for (int c = 0; c < vecs.length; c++)
            for (int r = 0; r < size; r++)
                mat.setElement(r, c, vecs[c].getElement(r));
                
        return mat;
    }
    
    /**
     * Checks if the set of vector points are collinear. As a special case, a vector
     * array of length 2 or less always returns true, since 2 points will always be
     * collinear.
     * 
     * @param vecs The set of point vectors
     * @return True if the points are collinear, false otherwise
     */
    public static boolean areCollinearPoints(Vector... vecs) {
        if (vecs.length <= 2) return true;
        
        return Maths.packVectorsToMatrixHorizontal(vecs).rank() <= 1;
    }
    
    /**
     * Checks if the set of vector points are coplanar. As a special case, a vector
     * array of length 3 or less always returns true, since 3 points will always be
     * coplanar.
     * 
     * @param vecs The set of point vectors
     * @return True if the points are coplanar, false otherwise
     */
    public static boolean areCoplanarPoints(Vector... vecs) {
        if (vecs.length <= 3) return true;
        
        return Maths.packVectorsToMatrixHorizontal(vecs).rank() <= 2;
    }
}
