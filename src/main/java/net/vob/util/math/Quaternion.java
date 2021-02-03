package net.vob.util.math;

import net.vob.util.logging.LocaleUtils;

/**
 * A special sub-class of {@code Vector} that has exactly 4 elements. An instance
 * of this class represents the mathematical quaternion {@code w + ix + jy + kz},
 * where {@code w, x, y, z} are the 4 elements of this vector.
 * 
 * @author Lyn-Park
 */
public final class Quaternion extends Vector {
    /** The read-only zero quaternion. Corresponds to (0, 0, 0, 0). */
    public static final Quaternion ZERO =  new Quaternion();
    /** Unit read-only quaternion. Corresponds to (1, 0, 0, 0). */
    public static final Quaternion POS_W = new Quaternion(1, 0, 0, 0);
    /** Unit read-only quaternion. Corresponds to (-1, 0, 0, 0). */
    public static final Quaternion NEG_W = new Quaternion(-1, 0, 0, 0);
    /** Unit read-only quaternion. Corresponds to (0, 1, 0, 0). */
    public static final Quaternion POS_X = new Quaternion(0, 1, 0, 0);
    /** Unit read-only quaternion. Corresponds to (0, -1, 0, 0). */
    public static final Quaternion NEG_X = new Quaternion(0, -1, 0, 0);
    /** Unit read-only quaternion. Corresponds to (0, 0, 1, 0). */
    public static final Quaternion POS_Y = new Quaternion(0, 0, 1, 0);
    /** Unit read-only quaternion. Corresponds to (0, 0, -1, 0). */
    public static final Quaternion NEG_Y = new Quaternion(0, 0, -1, 0);
    /** Unit read-only quaternion. Corresponds to (0, 0, 0, 1). */
    public static final Quaternion POS_Z = new Quaternion(0, 0, 0, 1);
    /** Unit read-only quaternion. Corresponds to (0, 0, 0, -1). */
    public static final Quaternion NEG_Z = new Quaternion(0, 0, 0, -1);
    /** The read-only ones quaternion. Corresponds to (1, 1, 1, 1). */
    public static final Quaternion ONES =  new Quaternion(1, 1, 1, 1);
    
    static {
        ZERO.immutable();
        POS_W.immutable();
        NEG_W.immutable();
        POS_X.immutable();
        NEG_X.immutable();
        POS_Y.immutable();
        NEG_Y.immutable();
        POS_Z.immutable();
        NEG_Z.immutable();
        ONES.immutable();
    }
    
    /**
     * Constructs an empty vector of size 4.
     */
    public Quaternion() {
        super(4);
    }
    
    /**
     * Constructs a new quaternion from the given elements.
     * @param w The first element
     * @param x The second element
     * @param y The third element
     * @param z The fourth element
     */
    public Quaternion(double w, double x, double y, double z) {
        super(new double[] { w, x, y, z });
    }
    
    /**
     * Constructs a new quaternion from the given array of elements.
     * @param els The elements to use for the quaternion
     * @throws IllegalArgumentException If the given array does not have exactly 4
     * elements
     */
    public Quaternion(double[] els) {
        super(els);
        
        if (els.length != 4)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", els.length, 4));
    }
    
    /**
     * Constructs a new quaternion using the given scalar and vector parts.
     * @param scalar The scalar part of the new quaternion
     * @param vector The vector part of the new quaternion
     */
    public Quaternion(double scalar, Vector3 vector) {
        super(new double[] { scalar, vector.getX(), vector.getY(), vector.getZ() });
    }
    
    /**
     * Copy constructor. The new quaternion is functionally identical to the given 
     * one. Note that the quaternion parameter is passed as a {@link Matrix} 
     * instance for convenience.
     * @param mat The quaternion to copy
     * @throws IllegalArgumentException If the given matrix is not a vector, as it
     * has multiple columns, or if the given vector does not have exactly 4 elements
     */
    public Quaternion(Matrix mat) {
        super(mat);
        
        if (mat.rows != 4)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.InvalidArrayLength", mat.rows, 4));
    }
    
    /**
     * Gets the first element of the quaternion. Functionally identical to 
     * {@link getElement(int) getElement(0)}, but this method was added for increased 
     * readability.
     * @return 
     */
    public double getW() {
        return elements[0];
    }
    
    /**
     * Gets the second element of the quaternion. Functionally identical to 
     * {@link getElement(int) getElement(1)}, but this method was added for increased 
     * readability.
     * @return 
     */
    public double getX() {
        return elements[1];
    }
    
    /**
     * Gets the third element of the quaternion. Functionally identical to 
     * {@link getElement(int) getElement(2)}, but this method was added for increased 
     * readability.
     * @return 
     */
    public double getY() {
        return elements[2];
    }
    
    /**
     * Gets the fourth element of the quaternion. Functionally identical to 
     * {@link getElement(int) getElement(3)}, but this method was added for increased 
     * readability.
     * @return 
     */
    public double getZ() {
        return elements[3];
    }
    
    /**
     * Gets the scalar part of the quaternion. Functionally identical to 
     * {@link #getW()}.
     * @return
     */
    public double getScalar() {
        return elements[0];
    }
    
    /**
     * Gets the vector part of the quaternion. Functionally identical to
     * {@code new Vector3(getX(), getY(), getZ())}.
     * @return 
     */
    public Vector3 getVector() {
        return new Vector3(elements[1], elements[2], elements[3]);
    }
    
    /**
     * Sets the first element of the quaternion. Functionally identical to 
     * {@link setElement(int, double) setElement(0, w)}, but this method was added for 
     * increased readability.
     * 
     * @param w The new value of the first element
     * @throws IllegalStateException if this quaternion is read-only
     */
    public void setW(double w) {
        setElement(0, 0, w);
    }
    
    /**
     * Sets the second element of the quaternion. Functionally identical to 
     * {@link setElement(int, double) setElement(1, x)}, but this method was added for 
     * increased readability.
     * @param x The new value of the second element
     * @throws IllegalStateException if this quaternion is read-only
     */
    public void setX(double x) {
        setElement(1, 0, x);
    }
    
    /**
     * Sets the third element of the quaternion. Functionally identical to 
     * {@link setElement(int, double) setElement(2, y)}, but this method was added for 
     * increased readability.
     * @param y The new value of the third element
     * @throws IllegalStateException if this quaternion is read-only
     */
    public void setY(double y) {
        setElement(2, 0, y);
    }
    
    /**
     * Sets the fourth element of the quaternion. Functionally identical to 
     * {@link setElement(int, double) setElement(3, z)}, but this method was added for 
     * increased readability.
     * @param z The new value of the fourth element
     * @throws IllegalStateException if this quaternion is read-only
     */
    public void setZ(double z) {
        setElement(3, 0, z);
    }
    
    /**
     * Calculates the inverse of this quaternion {@code P}, which is the unique 
     * quaternion {@code Q} such that {@code P x Q = Q x P = 1}, where {@code x}
     * is the Hamiltonian product.
     * @return The inverse of this quaternion with respect to the Hamiltonian product
     */
    @Override
    public Quaternion inverse() {
        double sqrMag = magnitudeSqr();
        return new Quaternion( elements[0] / sqrMag,
                              -elements[1] / sqrMag,
                              -elements[2] / sqrMag,
                              -elements[3] / sqrMag);
    }
    
    /**
     * Calculates the conjugate of this quaternion, which is the quaternion with the
     * same scalar component and a negated vector component. Not to be confused with
     * {@link conjugation(Quaternion)}.
     * @return 
     */
    public Quaternion conjugate() {
        return new Quaternion(elements[0], -elements[1], -elements[2], -elements[3]);
    }
    
    /**
     * Conjugates this quaternion by the given quaternion.<p>
     * 
     * Given 2 quaternions {@code P} and {@code Q}, the conjugation of {@code P} by 
     * {@code Q} is given by {@code Q x P x Q'}, where {@code x} is the Hamiltonian
     * product operation, and {@code Q'} is the inverse of {@code Q} (in this formula,
     * {@code Q} is the given quaternion). Not to be confused with {@link conjugate()}.
     * 
     * @param q
     * @return 
     */
    public Quaternion conjugation(Quaternion q) {
        return q.product(this).product(q.inverse());
    }
    
    /**
     * Computes the Hamiltonian product of this quaternion and the given quaternion.
     * Note that the given quaternion parameter acts as the right-hand operand. For 2 
     * quaternions, {@code P} and {@code Q}, their Hamiltonian product is given by:
     * 
     * <blockquote><pre>
     *  {@code P = p1 + p2*i + p3*j + p4*k},
     *  {@code Q = q1 + q2*i + q3*j + q4*k},
     *  {@code P x Q = 
     *      (p1*q1 + p2*q2 + p3*q3 + p4*q4)
     *    + (p1*q2 + p2*q1 + p3*q4 - p4*q3)*i
     *    + (p1*q3 - p2*q4 + p3*q1 + p4*q2)*j
     *    + (p1*q4 + p2*q3 - p3*q2 + p4*q1)*k}
     * </pre></blockquote>
     *
     * Note that this is a separate function and operator from {@link #mul(Matrix)},
     * which instead performs matrix multiplication, for the purposes of readability
     * and simplification.
     * @param q The other quaternion operand
     * @return The Hamiltonian product of the two quaternions
     */
    public Quaternion product(Quaternion q) {
        double a1 = elements[0],   b1 = elements[1],   c1 = elements[2],   d1 = elements[3];
        double a2 = q.elements[0], b2 = q.elements[1], c2 = q.elements[2], d2 = q.elements[3];
        
        return new Quaternion((a1 * a2) - (b1 * b2) - (c1 * c2) - (d1 * d2),
                              (a1 * b2) + (b1 * a2) + (c1 * d2) - (d1 * c2),
                              (a1 * c2) - (b1 * d2) + (c1 * a2) + (d1 * b2),
                              (a1 * d2) + (b1 * c2) - (c1 * b2) + (d1 * a2));
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * Note that this method is overridden to return an instance of {@code Quaternion}, 
     * as it will never return any other type of vector if it does not throw an 
     * exception.
     * 
     * @return {@inheritDoc}
     * @throws ArithmeticException {@inheritDoc}
     */
    @Override
    public Quaternion normalized() {
        return new Quaternion(super.normalized());
    }
    
    @Override
    public Quaternion add(Matrix mat) {
        return new Quaternion(super.add(mat));
    }
    
    @Override
    public Quaternion sub(Matrix mat) {
        return new Quaternion(super.sub(mat));
    }
    
    @Override
    public Quaternion mul(double scalar) {
        return new Quaternion(super.mul(scalar));
    }
    
    @Override
    public Quaternion elementMul(Matrix mat) {
        return new Quaternion(super.elementMul(mat));
    }
    
    @Override
    public Quaternion elementInv() {
        return new Quaternion(super.elementInv());
    }
    
    @Override
    public Quaternion elementNeg() {
        return new Quaternion(super.elementNeg());
    }
    
    /**
     * Gets the quaternion that can be used to perform a 3D rotation operation 
     * about the given axis and by the given angle. The axis is normalized
     * automatically and is in world space, and the rotation itself uses the 
     * <a href="http://en.wikipedia.org/wiki/Right-hand_rule">right-hand rule</a>.
     * 
     * @param axis The axis to rotate by
     * @param angle The angle to rotate by, in radians
     * @return The appropriate unit rotation quaternion
     */
    public static Quaternion rotationQuaternion(Vector3 axis, double angle) {
        axis = axis.normalized();
        double halfang = angle / 2;
        return new Quaternion(Math.cos(halfang), axis.mul(Math.sin(halfang)));
    }
    
    /**
     * Gets the quaternion representing the given Euler angle rotation operation.
     * The Euler rotation is taken to be the rotation around the positive X-axis
     * (roll), followed by rotation around the positive Y-axis (pitch), and finally
     * rotation around the positive Z-axis (yaw). All rotations occur in world space
     * and use the
     * <a href="http://en.wikipedia.org/wiki/Right-hand_rule">right-hand rule</a>.
     * 
     * @param roll The amount to rotate about the X-axis, in radians
     * @param pitch The amount to rotate about the Y-axis, in radians
     * @param yaw The amount to rotate about the Z-axis, in radians
     * @return The appropriate unit rotation quaternion
     */
    public static Quaternion rotationQuaternion(double roll, double pitch, double yaw) {
        double cr = Math.cos(roll / 2d);
        double sr = Math.sin(roll / 2d);
        double cp = Math.cos(pitch / 2d);
        double sp = Math.sin(pitch / 2d);
        double cy = Math.cos(yaw / 2d);
        double sy = Math.sin(yaw / 2d);
        
        return new Quaternion((cr * cp * cy) + (sr * sp * sy),
                              (sr * cp * cy) - (cr * sp * sy),
                              (cr * sp * cy) + (sr * cp * sy),
                              (cr * cp * sy) - (sr * sp * cy));
    }
    
    /**
     * Gets the quaternion representing the rotation needed for an object positioned at
     * {@code source} to be rotated to where its relative forward vector is pointing
     * directly towards {@code dest}, while respecting the upwards axis given by
     * {@code up}.<p>
     * 
     * Note that this function may return the read-only quaternion {@link POS_W} if
     * one of the calculated/required vectors becomes approximately zero at any point in
     * the algorithm (see the return values section for more information).
     * 
     * @param source the point about which to rotate
     * @param dest the point to rotate towards
     * @param up the upwards axis the rotation should respect
     * @return the rotation quaternion that rotates a coordinate system centred on
     * {@code source} such that it respects {@code up} and {@code dest} lies on its
     * positive Z axis, or {@link POS_W} if:
     * <ul>
     *  <li>{@code up} is approximately zero</li>
     *  <li>{@code source} and {@code dest} are approximately equal</li>
     *  <li>{@code up} and the vector between {@code source} and {@code dest} are
     * parallel (i.e. their cross product is approximately zero)</li>
     * </ul>
     */
    public static Quaternion rotationQuaternion(Vector3 source, Vector3 dest, Vector3 up) {
        if (Maths.approx0(up))
            return POS_W;
        
        up = up.normalized();
        
        Vector3 relativeForward = dest.sub(source);
        if (Maths.approx0(relativeForward))
            return POS_W;
        
        relativeForward = relativeForward.normalized();
        
        Vector3 relativeSide = up.cross(relativeForward);
        if (Maths.approx0(relativeSide))
            return POS_W;
        
        relativeSide = relativeSide.normalized();
        
        Vector3 relativeUp = relativeForward.cross(relativeSide);
        
        Quaternion q = new Quaternion();
        
        double fx = relativeForward.getX(), fy = relativeForward.getY(), fz = relativeForward.getZ();
        double rx = relativeSide.getX(), ry = relativeSide.getY(), rz = relativeSide.getZ();
        double ux = relativeUp.getX(), uy = relativeUp.getY(), uz = relativeUp.getZ();
        
        double trace = rx + uy + fz, s;
        if (trace > 0) {
            s = Math.sqrt(trace + 1);
            
            q.setW(s / 2);
            q.setX((uz - fy) / (s * 2));
            q.setY((fx - rz) / (s * 2));
            q.setZ((ry - ux) / (s * 2));
            
        } else {
            if (rx > uy && rx > fz) {
                s = Math.sqrt(1 + rx - uy - fz);
                
                q.setW((uz - fy) / (s * 2));
                q.setX(s / 2);
                q.setY((ry + ux) / (s * 2));
                q.setZ((fx + rz) / (s * 2));
                
            } else if (uy > fz) {
                s = Math.sqrt(1 + uy - rx - fz);
                
                q.setW((fx - rz) / (s * 2));
                q.setX((ry + ux) / (s * 2));
                q.setY(s / 2);
                q.setZ((uz + fy) / (s * 2));
                
            } else {
                s = Math.sqrt(1 + fz - rx - uy);
                
                q.setW((ry - ux) / (s * 2));
                q.setX((fx + rz) / (s * 2));
                q.setY((uz + fy) / (s * 2));
                q.setZ(s / 2);
            }
        }
        
        return q;
    }
}
