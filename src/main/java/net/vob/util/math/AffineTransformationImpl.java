package net.vob.util.math;

import java.util.concurrent.locks.ReentrantLock;
import net.vob.util.logging.LocaleUtils;

/**
 * Implementation of {@link AffineTransformation} that uses internal vectors and
 * quaternions to encode transformations.
 * 
 * @author Lyn-Park
 */
public class AffineTransformationImpl implements AffineTransformation {
    /**
     * An unmodifiable affine transformation, representing the identity transform;
     * i.e. this transform maps any given point, vector, etc. to itself.
     */
    public static final AffineTransformation IDENTITY = new AffineTransformationImpl().getAsUnmodifiable(true);
    
    protected Vector3 translation = Vector3.ZERO;
    protected Quaternion rotation = Quaternion.POS_W;
    protected Vector3 scale = Vector3.ONES;
    
    private Matrix matrix = Matrix.identity(4);
    private Matrix invMatrix = Matrix.identity(4);
    private boolean dirty = true;
    
    private final ReentrantLock lock = new ReentrantLock();
    
    public AffineTransformationImpl() {
        matrix.readonly();
        invMatrix.readonly();
    }
    
    public AffineTransformationImpl(AffineTransformation transform) {
        translation = transform.getTranslation();
        rotation = transform.getRotation();
        scale = transform.getScale();
    }
    
    @Override
    public Vector3 getTranslation() {
        lock.lock();
        try {
            return translation;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public AffineTransformationImpl setTranslation(Vector3 newTranslation) {
        lock.lock();
        try {
            translation = new Vector3(newTranslation);
            dirty = true;
        } finally {
            lock.unlock();
        }
        
        return this;
    }
    
    @Override
    public AffineTransformationImpl appendTranslation(Vector3 appendOffset) {
        lock.lock();
        try {
            translation = translation.add(appendOffset);
            dirty = true;
        } finally {
            lock.unlock();
        }
        
        return this;
    }
    
    @Override
    public AffineTransformationImpl resetTranslation() {
        lock.lock();
        try {
            translation = Vector3.ZERO;
            dirty = true;
        } finally {
            lock.unlock();
        }
        
        return this;
    }
    
    @Override
    public Quaternion getRotation() {
        lock.lock();
        try {
            return rotation;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public AffineTransformationImpl setRotation(Quaternion newRotation) {
        lock.lock();
        try {
            rotation = newRotation.normalized();
            dirty = true;
        } finally {
            lock.unlock();
        }
        
        return this;
    }
    
    @Override
    public AffineTransformationImpl appendRotation(Quaternion appendOffset) {
        lock.lock();
        try {
            rotation = appendOffset.normalized().product(rotation);
            dirty = true;
        } finally {
            lock.unlock();
        }
        
        return this;
    }
    
    @Override
    public AffineTransformationImpl prependRotation(Quaternion prependOffset) {
        lock.lock();
        try {
            rotation = rotation.product(prependOffset.normalized());
            dirty = true;
        } finally {
            lock.unlock();
        }
        
        return this;
    }
    
    @Override
    public AffineTransformationImpl resetRotation() {
        lock.lock();
        try {
            rotation = Quaternion.POS_W;
            dirty = true;
        } finally {
            lock.unlock();
        }
        
        return this;
    }
    
    @Override
    public Vector3 getScale() {
        lock.lock();
        try {
            return scale;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public AffineTransformationImpl setScale(Vector3 newScale) {
        lock.lock();
        try {
            scale = new Vector3(newScale);
            dirty = true;
        } finally {
            lock.unlock();
        }
        
        return this;
    }
    
    @Override
    public AffineTransformationImpl appendScale(Vector3 appendOffset) {
        lock.lock();
        try {
            scale = scale.elementMul(appendOffset);
            dirty = true;
        } finally {
            lock.unlock();
        }
        
        return this;
    }
    
    @Override
    public AffineTransformationImpl resetScale() {
        lock.lock();
        try {
            scale = Vector3.ONES;
            dirty = true;
        } finally {
            lock.unlock();
        }
        
        return this;
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * The readonly transformation matrix is stored internally, which is returned by
     * this method. If this transformation has been flagged as dirty by any of the
     * component-altering methods, then the internal matrices are reloaded using the
     * new components prior to returning.<p>
     * 
     * Note that the scaling and translation operations are done with respect to the
     * world space, rather than the object space.
     * 
     * @return an readonly {@code Matrix} instance representing this affine
     * transformation
     */
    @Override
    public Matrix getTransformationMatrix() {
        lock.lock();
        try {
            if (dirty) {
                dirty = false;
                
                matrix = Matrix.getTranslationMatrix(translation)
                               .mul(Matrix.getRotationMatrix(rotation))
                               .mul(Matrix.getScalingMatrix(scale));
                matrix.readonly();
                
                invMatrix = Matrix.getScalingMatrix(scale.elementInv())
                                  .mul(Matrix.getRotationMatrix(rotation.conjugate()))
                                  .mul(Matrix.getTranslationMatrix(translation.mul(-1)));
                invMatrix.readonly();
            }
            
            return matrix;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * The readonly inverse transformation matrix is stored internally, which is
     * returned by this method. If this transformation has been flagged as dirty by
     * any of the component-altering methods, then the internal matrices are reloaded
     * using the new components prior to returning.<p>
     * 
     * Note that the scaling and translation operations are done with respect to the
     * world space, rather than the object space.
     * 
     * @return an readonly {@code Matrix} instance representing the inverse of this
     * affine transformation
     */
    @Override
    public Matrix getInverseTransformationMatrix() {
        lock.lock();
        try {
            if (dirty) {
                dirty = false;
                
                matrix = Matrix.getTranslationMatrix(translation)
                               .mul(Matrix.getRotationMatrix(rotation))
                               .mul(Matrix.getScalingMatrix(scale));
                matrix.readonly();
                
                invMatrix = Matrix.getScalingMatrix(scale.elementInv())
                                  .mul(Matrix.getRotationMatrix(rotation.conjugate()))
                                  .mul(Matrix.getTranslationMatrix(translation.mul(-1)));
                invMatrix.readonly();
            }
            
            return invMatrix;
            
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Vector3 transformVector(Vector3 vec) {
        lock.lock();
        try {
            Vector3 temp = vec.elementMul(scale);
            temp = new Quaternion(0, temp).conjugation(rotation).getVector();
            temp = temp.add(translation);
            
            return temp;
            
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Vector3 inverseTransformVector(Vector3 vec) {
        lock.lock();
        try {
            Vector3 temp = vec.sub(translation);
            temp = new Quaternion(0, temp).conjugation(rotation.conjugate()).getVector();
            temp = temp.elementMul(new Vector3(1 / scale.getX(), 1 / scale.getY(), 1 / scale.getZ()));
            
            return temp;
            
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public boolean isDirty() {
        lock.lock();
        try {
            return dirty;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public ReentrantLock getLock() {
        return lock;
    }
    
    @Override
    public AffineTransformation getAsUnmodifiable(boolean allowMatrixQuery) {
        return new Unmodifiable(this, allowMatrixQuery);
    }
    
    private static class Unmodifiable implements AffineTransformation {
        private final AffineTransformationImpl wrapped;
        private final boolean allowMatrixQuery;
        
        public Unmodifiable(AffineTransformationImpl wrapped, boolean allowMatrixQuery) {
            this.wrapped = wrapped;
            this.allowMatrixQuery = allowMatrixQuery;
        }
        
        @Override
        public Vector3 getTranslation() {
            return wrapped.getTranslation();
        }
        @Override
        public Quaternion getRotation() {
            return wrapped.getRotation();
        }
        @Override
        public Vector3 getScale() {
            return wrapped.getScale();
        }
        
        /**
         * {@inheritDoc}<p>
         * 
         * This method has been overridden to always throw an
         * {@link UnsupportedOperationException}.
         * 
         * @param newTranslation
         */
        @Override
        public AffineTransformation setTranslation(Vector3 newTranslation) {
            throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.Readonly","Unmodifiable AffineTransformation"));
        }
        /**
         * {@inheritDoc}<p>
         * 
         * This method has been overridden to always throw an
         * {@link UnsupportedOperationException}.
         * 
         * @param newRotation
         */
        @Override
        public AffineTransformation setRotation(Quaternion newRotation) {
            throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.Readonly","Unmodifiable AffineTransformation"));
        }
        /**
         * {@inheritDoc}<p>
         * 
         * This method has been overridden to always throw an
         * {@link UnsupportedOperationException}.
         * 
         * @param newScale
         */
        @Override
        public AffineTransformation setScale(Vector3 newScale) {
            throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.Readonly","Unmodifiable AffineTransformation"));
        }
        
        /**
         * {@inheritDoc}<p>
         * 
         * This method has been overridden to always throw an
         * {@link UnsupportedOperationException}.
         * 
         * @param appendOffset
         */
        @Override
        public AffineTransformation appendTranslation(Vector3 appendOffset) {
            throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.Readonly","Unmodifiable AffineTransformation"));
        }
        /**
         * {@inheritDoc}<p>
         * 
         * This method has been overridden to always throw an
         * {@link UnsupportedOperationException}.
         * 
         * @param appendOffset
         */
        @Override
        public AffineTransformation appendRotation(Quaternion appendOffset) {
            throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.Readonly","Unmodifiable AffineTransformation"));
        }
        /**
         * {@inheritDoc}<p>
         * 
         * This method has been overridden to always throw an
         * {@link UnsupportedOperationException}.
         * 
         * @param prependOffset
         */
        @Override
        public AffineTransformation prependRotation(Quaternion prependOffset) {
            throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.Readonly","Unmodifiable AffineTransformation"));
        }
        /**
         * {@inheritDoc}<p>
         * 
         * This method has been overridden to always throw an
         * {@link UnsupportedOperationException}.
         * 
         * @param appendOffset
         */
        @Override
        public AffineTransformation appendScale(Vector3 appendOffset) {
            throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.Readonly","Unmodifiable AffineTransformation"));
        }
        
        /**
         * {@inheritDoc}<p>
         * 
         * This method has been overridden to always throw an
         * {@link UnsupportedOperationException}.
         */
        @Override
        public AffineTransformation resetTranslation() {
            throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.Readonly","Unmodifiable AffineTransformation"));
        }
        /**
         * {@inheritDoc}<p>
         * 
         * This method has been overridden to always throw an
         * {@link UnsupportedOperationException}.
         */
        @Override
        public AffineTransformation resetRotation() {
            throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.Readonly","Unmodifiable AffineTransformation"));
        }
        /**
         * {@inheritDoc}<p>
         * 
         * This method has been overridden to always throw an
         * {@link UnsupportedOperationException}.
         */
        @Override
        public AffineTransformation resetScale() {
            throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.Readonly","Unmodifiable AffineTransformation"));
        }
        
        /**
        * {@inheritDoc}<p>
        * 
        * If this view instance does not allow for querying of the matrix, then this
        * method throws an {@link UnsupportedOperationException}. Otherwise, the call
        * passes through to the wrapped instance.
        * 
        * @return an readonly {@code Matrix} instance representing this affine
        * transformation
        */
        @Override
        public Matrix getTransformationMatrix() {
            if (allowMatrixQuery)
                return wrapped.getTransformationMatrix();
            
            throw new UnsupportedOperationException(LocaleUtils.format("AffineTransformationImpl.MatrixQueryUnsupported"));
        }
        
        /**
        * {@inheritDoc}<p>
        * 
        * If this view instance does not allow for querying of the matrix, then this
        * method throws an {@link UnsupportedOperationException}. Otherwise, the call
        * passes through to the wrapped instance.
        * 
        * @return an readonly {@code Matrix} instance representing the inverse of 
        * this affine transformation
        */
        @Override
        public Matrix getInverseTransformationMatrix() {
            if (allowMatrixQuery)
                return wrapped.getInverseTransformationMatrix();
            
            throw new UnsupportedOperationException(LocaleUtils.format("AffineTransformationImpl.MatrixQueryUnsupported"));
        }
        
        @Override
        public Vector3 transformVector(Vector3 vec) {
            return wrapped.transformVector(vec);
        }
        
        @Override
        public Vector3 inverseTransformVector(Vector3 vec) {
            return wrapped.inverseTransformVector(vec);
        }
        
        @Override
        public boolean isDirty() {
            return wrapped.isDirty();
        }
        
        @Override
        public ReentrantLock getLock() {
            return wrapped.getLock();
        }
        
        @Override
        public AffineTransformation getAsUnmodifiable(boolean allowMatrixQuery) {
            return new Unmodifiable(wrapped, allowMatrixQuery);
        }
    }
}
