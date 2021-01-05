package net.vob.core;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.vob.VoidOfBlue;
import net.vob.core.graphics.GraphicsEngine;
import net.vob.util.Closable;
import net.vob.util.logging.Level;
import net.vob.util.logging.LocaleUtils;

/**
 * Base abstract class for objects that handle graphics. Implementing classes are expected
 * to facilitate heavy communication with the graphics engine - most basic functionality
 * has been implemented here, however.<p>
 * 
 * Note that renderable objects submit tasks to a worker pool of threads as part of their
 * initialization; thus, the default implementation of the various setters are designed to
 * return early without altering anything if the initialization of the object has yet to
 * fully complete, or if this part of the initialization fails. See
 * {@link AbstractRenderable(Mesh, Shader, Texture...)} for more information on this worker
 * task, as well as any considerations that implementing classes/constructors must take.
 */
public abstract class AbstractRenderable extends Closable {
    private final static Logger LOG = VoidOfBlue.getLogger(AbstractRenderable.class);
    private final static ExecutorService WORKER_POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        private final ThreadGroup threadGroup = new ThreadGroup("renderable-workers");
        
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(threadGroup, r);
        }
    });
    
    private Mesh mesh;
    private Shader shader;
    private final Texture[] textures = new Texture[GraphicsEngine.getMaxCombinedTextureUnit()];
    
    private static final Object rendIDLock = new Object();
    private Integer rendID = null;
    
    /**
     * Constructs the renderable.<p>
     * 
     * This constructor performs basic checks on the parameters, and assigns them to the
     * fields of this instance. It then submits a task to the internal pool of worker
     * threads to asynchronously instantiate the renderable within the graphics engine, and
     * then assign the resulting ID of the renderable to this instance. This instantiation
     * in the graphics engine is achieved through a call to {@link initialize()}.<p>
     * 
     * <b>Warning</b>: implementing classes should not, <i>under any circumstances</i>,
     * throw exceptions within their constructors! This is due to the worker thread that
     * runs the asynchronous task of instantiating the renderable - throwing an exception
     * will cause the submitted task to become an unmanaged resource holding a reference
     * to this instance. Thus, not only will the task continue to interact with this
     * partially constructed instance even after its constructor fails, but it will also
     * instantiate and manipulate other unmanaged resources in the graphics engine, causing
     * memory leaks. Exceptional circumstances other than what is addressed here should be
     * properly addressed with other mechanisms, such as the use of factory methods.
     * 
     * @param mesh the {@link Mesh} of the new renderable
     * @param shader the {@link Shader} of the new renderable
     * @param textures the set of {@link Texture} objects of the new renderable. This is
     * iterated over sequentially, and each texture is assigned to the appropriate slot in
     * the renderable according to the texture unit; hence, later textures will overwrite
     * previous ones if their texture units match. This may be {@code null} to indicate
     * no textures
     * @throws NullPointerException if {@code mesh} or {@code shader} is {@code null}
     * @throws IllegalArgumentException if any of the textures in {@code textures} is of
     * an incompatible type with the mode of {@code shader}
     */
    @SuppressWarnings("UseSpecificCatch")
    protected AbstractRenderable(Mesh mesh, Shader shader, Texture... textures) {
        if (mesh == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "mesh"));
        if (shader == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "shader"));
        
        if (textures != null) {
            for (Texture texture : textures) {
                if (texture != null) {
                    if (!shader.isCompatible(texture))
                        throw new IllegalArgumentException(LocaleUtils.format("AbstractRenderable._cinit_.InvalidTextureType"));
                    
                    this.textures[texture.unit] = texture;
                }
            }
        }
        
        this.mesh = mesh;
        this.shader = shader;
        
        WORKER_POOL.submit(this::workerThreadCallback);
    }
    
    private void workerThreadCallback() {
        try {
            Future<Integer> future;

            GraphicsEngine.MESSAGE_LOCK.lock();
            try {
                future = initialize();
            } finally {
                GraphicsEngine.MESSAGE_LOCK.unlock();
            }

            Integer r = null;

            if (future != null)
                r = future.get(5, TimeUnit.SECONDS);

            if (r != null) {
                synchronized (rendIDLock) {
                    rendID = r;
                }
            } else
                LOG.log(Level.WARNING, "AbstractRenderable._cinit_.InitFailed");

        } catch (Throwable t) {
            LOG.log(Level.WARNING, LocaleUtils.format("AbstractRenderable._cinit_.InitFailed"), t);
        }
    }
    
    /**
     * Performs the asynchronous initialization of this renderable. This completes the
     * construction of this instance by initializing the renderable within the graphics 
     * engine, and is invoked asynchronously by a worker thread within the constructor.<p>
     * 
     * Implementations of this method must guarantee that upon returning, an appropriate
     * and complete sequence of messages to the graphics engine have been queued such that
     * they will <i>fully</i> initialize the renderable (this includes attaching the
     * renderable to a shader, setting the transform of the renderable, etc).
     * Implementations may make use of any resource to accomplish this, including the
     * parameters that were passed to the constructor (which can be accessed with their
     * appropriate getter methods). In return, implementations of this method can safely
     * make the assumption that {@link GraphicsEngine#MESSAGE_LOCK} has already been
     * acquired by the calling thread (and will be released upon return).<p>
     * 
     * The returned {@link Future} should contain the ID value of the new renderable
     * object as returned from the graphics engine; this will then be used by the calling
     * worker thread to finish initialization of this object, by assigning the returned
     * value to {@code rendID} (see {@link hasRendID()} and {@link getRendID()}).
     *
     * @return a {@link Future} instance containing the ID of the renderable in the
     * graphics engine, or {@code null} to signal an error
     */
    protected abstract @Nullable Future<Integer> initialize();
    
    /**
     * Checks if this renderable has an ID value. This method returns {@code true}
     * if-and-only-if this renderable's asynchronous initialization has completed
     * successfully; thus, this method can be used to prevent certain functions from
     * occurring before the initialization is complete.
     * 
     * @return {@code true} if the renderable ID is non-{@code null}, {@code false}
     * otherwise
     */
    protected final boolean hasRendID() {
        synchronized (rendIDLock) {
            return rendID != null;
        }
    }
    
    /**
     * Returns the ID value of this renderable. This is defined to be the unique
     * identifying integer that can be passed to
     * {@link GraphicsEngine#msgRenderableSelect(int)} in order to select the renderable
     * and perform operations on it.<p>
     * 
     * Note that this method returns {@code null} if-and-only-if {@link hasRendID()}
     * returns {@code false}; hence, checking for {@code null} (or using
     * {@code hasRendID()}) is heavily recommended to prevent any exceptions from
     * occurring.
     * 
     * @return the ID value of this renderable, or {@code null} if the renderable hasn't
     * been given one yet
     */
    protected final @Nullable Integer getRendID() {
        synchronized (rendIDLock) {
            return rendID;
        }
    }
    
    /**
     * Sets the mesh of this renderable.<p>
     * 
     * Note that this method returns early without altering anything if this renderable
     * has not yet been fully initialized in the graphics engine, or the given mesh is
     * already assigned to this renderable.
     * 
     * @param mesh the new mesh
     * @throws NullPointerException if {@code mesh} is {@code null}
     */
    public void setMesh(Mesh mesh) {
        if (!hasRendID() || this.mesh.equals(mesh))
            return;
        if (mesh == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "mesh"));
        
        this.mesh = mesh;
        
        GraphicsEngine.MESSAGE_LOCK.lock();
        
        try {
            GraphicsEngine.msgRenderableSelect(getRendID());
            mesh.select();
            GraphicsEngine.msgRenderableAttachMesh();
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
    }
    
    /**
     * @return the current mesh of the renderable
     */
    public Mesh getMesh() {
        return this.mesh;
    }
    
    /**
     * Sets a texture of this renderable.<p>
     * 
     * Note that this method returns early without altering anything if this renderable
     * has not yet been fully initialized in the graphics engine, or the given texture
     * is already assigned to this renderable.
     * 
     * @param texture the new texture
     * @throws NullPointerException if {@code texture} is {@code null}
     * @throws IllegalArgumentException if {@code texture} is of an incompatible type
     * with the mode of the current shader
     */
    public void setTexture(Texture texture) {
        if (!hasRendID())
            return;
        if (texture == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "texture"));
        if (!shader.isCompatible(texture))
            throw new IllegalArgumentException(LocaleUtils.format("AbstractRenderable.setTexture.InvalidTextureType"));
        if (this.textures[texture.unit].equals(texture))
            return;
        
        this.textures[texture.unit] = texture;
        
        GraphicsEngine.MESSAGE_LOCK.lock();
        
        try {
            GraphicsEngine.msgRenderableSelect(getRendID());
            texture.select();
            GraphicsEngine.msgRenderableAttachTexture();
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
    }
    
    /**
     * Removes a texture of this renderable.<p>
     * 
     * Note that this method returns early without altering anything if this renderable
     * has not yet been fully initialized in the graphics engine, or the given texture
     * unit already has no assigned texture.
     * 
     * @param unit the texture unit to clear
     * @throws IndexOutOfBoundsException if {@code unit} is less than 0 or greater
     * than {@link GraphicsEngine#getMaxCombinedTextureUnit()}
     */
    public void removeTexture(int unit) {
        if (!hasRendID())
            return;
        if (unit < 0 || unit >= this.textures.length)
            throw new IndexOutOfBoundsException(LocaleUtils.format("global.Exception.OutOfRange.<=x<", "unit", unit, 0, textures.length));
        if (this.textures[unit] == null)
            return;
        
        this.textures[unit] = null;
        
        GraphicsEngine.MESSAGE_LOCK.lock();
        
        try {
            GraphicsEngine.msgRenderableSelect(getRendID());
            GraphicsEngine.msgRenderableDetachTexture(unit);
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
    }
    
    /**
     * @return the current textures of the renderable
     */
    public Texture[] getTextures() {
        return this.textures;
    }
    
    /**
     * @param unit the texture unit to get the texture of
     * @return the current texture of the renderable
     */
    public Texture getTexture(int unit) {
        return this.textures[unit];
    }
    
    /**
     * Sets the shader of this renderable. Removes all textures from the renderable if
     * the new shader has a different mode to the current one.<p>
     * 
     * Note that this method returns early without altering anything if this renderable
     * has not yet been fully initialized in the graphics engine, or the given shader
     * is already assigned to this renderable.
     * 
     * @param shader the new shader
     * @throws NullPointerException if {@code shader} is {@code null}
     */
    public void setShader(Shader shader) {
        if (!hasRendID() || this.shader.equals(shader))
            return;
        if (shader == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "shader"));
        
        if (this.shader.mode ^ shader.mode)
            for (int i = 0; i < textures.length; ++i)
                textures[i] = null;
        
        GraphicsEngine.MESSAGE_LOCK.lock();
        
        try {
            GraphicsEngine.msgRenderableSelect(getRendID());
            shader.select();
            GraphicsEngine.msgShaderProgramAssignRenderable();
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
    }
    
    /**
     * @return the current shader of the renderable
     */
    public Shader getShader() {
        return this.shader;
    }
    
    @Override
    protected final boolean doClose() {
        if (!hasRendID())
            return false;
        
        GraphicsEngine.MESSAGE_LOCK.lock();
        
        try {
            GraphicsEngine.msgRenderableSelect(getRendID());
            GraphicsEngine.msgRenderableClose();
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
        
        return true;
    }
}
