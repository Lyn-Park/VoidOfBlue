package net.vob.util.math;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Base interface for affine transformations.<p>
 * 
 * Affine transformations are the types of transformations that provide translation,
 * rotation and scaling operations within a 3D space. They are primarily used for
 * graphical rendering, and are converted to a 4x4 matrix form for this purpose.
 * All implementations of affine transformations should therefore ensure
 * thread-safety for all public methods using the returned {@link ReentrantLock}
 * from {@link getLock()}.
 * 
 * @author Lyn-Park
 */
public interface AffineTransformation {
    /**
     * Flag indicating that the translation component should not be written to the
     * transformation matrix at all.
     */
    public static final int FLAG_IGNORE_TRANSLATION = 1;
    /**
     * Flag indicating that the rotation component should not be written to the
     * transformation matrix at all.
     */
    public static final int FLAG_IGNORE_ROTATION = 2;
    /**
     * Flag indicating that the scaling component should not be written to the
     * transformation matrix at all.
     */
    public static final int FLAG_IGNORE_SCALING = 4;
    /**
     * Flag indicating that the translation component should be inverted prior to
     * writing to the transformation matrix. Has no effect if
     * {@link FLAG_IGNORE_TRANSLATION} is present.
     */
    public static final int FLAG_INVERT_TRANSLATION = 8;
    /**
     * Flag indicating that the rotation component should be inverted prior to
     * writing to the transformation matrix. Has no effect if
     * {@link FLAG_IGNORE_ROTATION} is present.
     */
    public static final int FLAG_INVERT_ROTATION = 16;
    /**
     * Flag indicating that the scaling component should be inverted prior to
     * writing to the transformation matrix. Has no effect if
     * {@link FLAG_IGNORE_SCALING} is present.
     */
    public static final int FLAG_INVERT_SCALING = 32;
    /**
     * Flag indicating that the transformation components should be written to
     * the transformation matrix in inverse order. Normally the order of
     * transformations is:
     * <blockquote>
     * {@code matrix = translationMatrix * rotationMatrix * scalingMatrix}
     * </blockquote>
     */
    public static final int FLAG_INVERT_TRANSFORM_ORDER = 64;
    
    /**
     * Gets the current translation of this transformation.
     * @return the current translation vector
     */
    public Vector3 getTranslation();
    /**
     * Gets the current rotation of this transformation.
     * @return the current rotation quaternion
     */
    public Quaternion getRotation();
    /**
     * Gets the current scale factor of this transformation.
     * @return the current scale factor vector
     */
    public Vector3 getScale();
    
    /**
     * Sets the translation of this transformation.
     * @param newTranslation the new translation vector
     * @return this transformation, for chaining
     */
    public AffineTransformation setTranslation(Vector3 newTranslation);
    /**
     * Sets the rotation of this transformation
     * @param newRotation the new rotation quaternion
     * @return this transformation, for chaining
     */
    public AffineTransformation setRotation(Quaternion newRotation);
    /**
     * Sets the scale factor of this transformation.
     * @param newScale the new scale factor vector
     * @return this transformation, for chaining
     */
    public AffineTransformation setScale(Vector3 newScale);
    
    /**
     * Appends the given vector to the current translation. This will have the effect
     * of the transformation translating an object by the current translation vector,
     * and then translating again by the given offset.
     * @param appendOffset the vector to offset the current translation by
     * @return this transformation, for chaining
     */
    public AffineTransformation appendTranslation(Vector3 appendOffset);
    /**
     * Appends the given quaternion to the current rotation. This will have the effect
     * of the transformation rotating an object by the current rotation quaternion,
     * and then rotating again by the given offset.
     * @param appendOffset the quaternion to offset the current rotation by
     * @return this transformation, for chaining
     */
    public AffineTransformation appendRotation(Quaternion appendOffset);
    /**
     * Prepends the given quaternion to the current rotation. This will have the effect
     * of the transformation rotating an object by the given offset, and then rotating 
     * again by the current rotation quaternion.
     * @param prependOffset the quaternion to offset the current rotation by
     * @return this transformation, for chaining
     */
    public AffineTransformation prependRotation(Quaternion prependOffset);
    /**
     * Appends the given vector to the current scale factor. This will have the
     * effect of the transformation scaling an object by the current scale vector,
     * and then scaling again by the given vector.
     * @param appendOffset the vector to offset the current scale factor by
     * @return this transformation, for chaining
     */
    public AffineTransformation appendScale(Vector3 appendOffset);
    
    /**
     * Resets the translation of this transformation. Implementations should ensure
     * that the value to reset to remains constant.
     * @return this transformation, for chaining
     */
    public AffineTransformation resetTranslation();
    /**
     * Resets the rotation of this transformation. Implementations should ensure
     * that the value to reset to remains constant.
     * @return this transformation, for chaining
     */
    public AffineTransformation resetRotation();
    /**
     * Resets the scale factor of this transformation. Implementations should ensure
     * that the value to reset to remains constant.
     * @return this transformation, for chaining
     */
    public AffineTransformation resetScale();
    
    /**
     * Gets the transformation matrix representing this affine transformation.<p>
     * 
     * The transformation matrix itself, when applied to an object, should represent
     * (in order):
     * <ol>
     *  <li>A scaling of the object by the scale factor vector.</li>
     *  <li>A rotation of the object by the rotation quaternion.</li>
     *  <li>A translation of the object by the translation vector.</li>
     * </ol>
     * The flags argument can be used to make any extra modifications to the 3
     * transformation components prior to writing to the transformation matrix.<p>
     * 
     * Implementations of this method should not alter the stored translation,
     * rotation, or scaling vectors in any way, and should ensure the behaviour
     * defined by the input flags is respected.
     * 
     * @param flags a field of bit flags. Can be 0, or any bit-wise combination of
     * {@link FLAG_IGNORE_TRANSLATION}, {@link FLAG_IGNORE_ROTATION},
     * {@link FLAG_IGNORE_SCALING}, {@link FLAG_INVERT_TRANSLATION},
     * {@link FLAG_INVERT_ROTATION}, {@link FLAG_INVERT_SCALING}, and
     * {@link FLAG_INVERT_TRANSFORM_ORDER}. Bits outside these bit flags are
     * ignored
     * @return a {@code Matrix} instance representing this affine transformation
     */
    public Matrix getTransformationMatrix(int flags);
    
    /**
     * Transforms the given vector by this affine transformation.<p>
     * 
     * Implementations of this method must guarantee that they transform the vector
     * such that it conforms to the function of {@link getTransformationMatrix()};
     * i.e. mathematical matrix-vector multiplication of the given vector with the
     * transformation matrix should yield the same result as this method.<p>
     * 
     * Implementations of this method and {@link inverseTransformVector(Vector3)}
     * must also guarantee that they are inverses of each other; in other words, for
     * some {@code Vector3 vec}:
     * <blockquote><pre>      {@code vec =
     *          transformVector(inverseTransformVector(vec)) =
     *          inverseTransformVector(transformVector(vec))}</pre></blockquote>
     * 
     * @param vec the vector to transform
     * @return the transformed vector
     */
    public Vector3 transformVector(Vector3 vec);
    
    /**
     * Inverse transforms the given vector by this affine transformation.<p>
     * 
     * Implementations of this method must guarantee that they transform the vector
     * such that it conforms to the function of {@link getTransformationMatrix()};
     * i.e. mathematical matrix-vector multiplication of the given vector with the
     * inverse of the transformation matrix should yield the same result as this
     * method.<p>
     * 
     * Implementations of this method and {@link transformVector(Vector3)} must also
     * guarantee that they are inverses of each other; in other words, for some
     * {@code Vector3 vec}:
     * <blockquote><pre>      {@code vec =
     *          transformVector(inverseTransformVector(vec)) =
     *          inverseTransformVector(transformVector(vec))}</pre></blockquote>
     * 
     * @param vec the vector to transform
     * @return the inverse transformed vector
     */
    public Vector3 inverseTransformVector(Vector3 vec);
    
    /**
     * Gets if this transformation is dirty, i.e. any of it's components has changed
     * since the last time {@link getTransformationMatrix()} or
     * {@link getInverseTransformationMatrix()} was called. It is preferable for
     * implementations of this method to return {@code true} for new instances, i.e.
     * when neither of the aforementioned matrix methods have yet been called on this
     * instance.
     * 
     * @return {@code true} if this transformation is dirty, {@code false} otherwise
     */
    public boolean isDirty();
    
    /**
     * Gets the lock this transformation uses for thread-safety and synchronization.
     * @return the {@link ReentrantLock} lock for this instance
     */
    public ReentrantLock getLock();
    
    /**
     * Returns an unmodifiable view of this instance. Implementations should ensure
     * that any attempt to write into the returned view is rejected, and that any
     * changes to this instance will be reflected in the returned instance.
     * 
     * @param allowMatrixQuery whether to allow the returned view to query the
     * transformation matrix with {@link getTransformationMatrix()}. A value of
     * {@code true} will allow this view to recalculate the matrix (if needed) and
     * return it; a value of {@code false} will cause the method to reject the
     * attempt
     * @return an unmodifiable view of this affine transformation
     */
    public AffineTransformation getAsUnmodifiable(boolean allowMatrixQuery);
}
