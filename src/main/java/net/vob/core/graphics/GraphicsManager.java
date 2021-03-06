package net.vob.core.graphics;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.vob.VoidOfBlue;
import net.vob.util.logging.Level;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.AffineTransformation;
import net.vob.util.math.AffineTransformationImpl;
import net.vob.util.math.Matrix;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLDebugMessageCallback;

/**
 * The internal manager class that handles the actual rendering of objects. Contains
 * several internal OpenGL constants (some of which may not be initialized until the
 * manager itself is), the graphical thread and associated callbacks, and other
 * miscellaneous items such as the currently selected objects.
 * 
 * @author Lyn-Park
 */
final class GraphicsManager {
    private GraphicsManager() {}
    
    // --- CONSTANTS ---
    private static final Logger LOG = VoidOfBlue.getLogger(GraphicsManager.class);
    
    /** The static, global SSBO containing 4 bytes, with all set to 0. */
    static int SHADER_STORAGE_BUFFER_OBJECT_ZERO;
    
    /** The index for the position attribute of vertices as stored in an array buffer object. */
    static final int SHADER_ATTRIBUTE_POSITION_INDEX = 0;
    /** The index for the texture coordinate attribute of vertices as stored in an array buffer object. */
    static final int SHADER_ATTRIBUTE_UV_INDEX = 1;
    /** The index for the normal vector attribute of vertices as stored in an array buffer object. */
    static final int SHADER_ATTRIBUTE_NORMAL_INDEX = 2;
    /** The index for the model matrix attribute of instances as stored in an array buffer object. */
    static final int SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_INDEX = 8;
    /** The index for the projection/view/model matrix attribute of instances as stored in an array buffer object. */
    static final int SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_INDEX = 12;
    
    /** The string variable name shaders must use for referencing the position attribute of vertices. */
    static final String SHADER_ATTRIBUTE_POSITION_NAME = "in_Position";
    /** The string variable name shaders must use for referencing the texture coordinate attribute of vertices. */
    static final String SHADER_ATTRIBUTE_UV_NAME = "in_TexCoord";
    /** The string variable name shaders must use for referencing the normal vector attribute of vertices. */
    static final String SHADER_ATTRIBUTE_NORMAL_NAME = "in_VertNormal";
    /** The string variable name shaders must use for referencing the model matrix attribute of instances. */
    static final String SHADER_INSTANCE_ATTRIBUTE_MODEL_MATRIX_NAME = "instance_ModelMatrix";
    /** The string variable name shaders must use for referencing the projection/view/model matrix attribute of instances. */
    static final String SHADER_INSTANCE_ATTRIBUTE_PROJECTION_VIEW_MODEL_MATRIX_NAME = "instance_ProjectionViewModelMatrix";
    
    /** The string variable name shaders must use for referencing the window size vector uniform variable. */
    static final String SHADER_UNIFORM_WINDOW_SIZE_NAME = "windowSize";
    /** The string variable name shaders must use for referencing the view matrix uniform variable. */
    static final String SHADER_UNIFORM_VIEW_MATRIX_NAME = "viewMatrix";
    /** The string variable name shaders must use for referencing the projection matrix uniform variable. */
    static final String SHADER_UNIFORM_PROJECTION_MATRIX_NAME = "projectionMatrix";
    /** The string name shaders must use for referencing the SSBO struct containing the vertex-bone weight values of skeletons. */
    static final String SHADER_UNIFORM_SKELETON_WEIGHTS_NAME = "weightSSBO";
    /** The string name shaders must use for referencing the SSBO struct containing the affine transformations of skeletons. */
    static final String SHADER_UNIFORM_SKELETON_TRANSFORMS_NAME = "skeletonSSBO";
    
    /** The minor version number of the OpenGL API supported in the current execution environment. */
    static int MINOR_GL_VERSION_NUMBER;
    /** The major version number of the OpenGL API supported in the current execution environment. */
    static int MAJOR_GL_VERSION_NUMBER;
    
    /** The maximum number of combined texture units the current GL implementation can handle. */
    static int MAX_COMBINED_TEXTURE_UNITS;
    /** The maximum number of texture units a vertex shader in the current GL implementation can handle. */
    static int MAX_VERTEX_TEXTURE_UNITS;
    /** The maximum number of texture units a geometry shader in the current GL implementation can handle. */
    static int MAX_GEOMETRY_TEXTURE_UNITS;
    /** The maximum number of texture units a fragment shader in the current GL implementation can handle. */
    static int MAX_FRAGMENT_TEXTURE_UNITS;
    
    /** The maximum texture size the current GL implementation can handle. */
    static int MAX_TEXTURE_SIZE;
    /** The maximum number of layers in an array texture the current GL implementation can handle. */
    static int MAX_ARRAY_TEXTURE_LAYERS;
    /** The maximum number of texture mipmap levels the current GL implementation can handle. */
    static int MAX_TEXTURE_MIPMAP_LEVELS;
    /** The maximum number of shader vertex attributes the current GL implementation can handle. */
    static int MAX_VERTEX_ATTRIBS;
    
    /** The number of floating-point position coordinates for each vertex in a mesh. */
    static final int NUM_POSITION_COMPONENTS_PER_VERTEX = 3;
    /** The number of floating-point texture uv coordinates for each vertex in a mesh. */
    static final int NUM_UV_COMPONENTS_PER_VERTEX = 3;
    /** The number of floating-point normal coordinates for each vertex in a mesh. */
    static final int NUM_NORMAL_COMPONENTS_PER_VERTEX = 3;
    /** The offset of the position attribute in a mesh vertex, in bytes. */
    static final int VERTEX_POSITION_OFFSET = 0;
    /** The offset of the texture uv coordinates in a mesh vertex, in bytes. */
    static final int VERTEX_UV_OFFSET = VERTEX_POSITION_OFFSET + (NUM_POSITION_COMPONENTS_PER_VERTEX * Float.BYTES);
    /** The offset of the normal coordinates in a mesh vertex, in bytes. */
    static final int VERTEX_NORMAL_OFFSET = VERTEX_UV_OFFSET + (NUM_UV_COMPONENTS_PER_VERTEX * Float.BYTES);
    /** The total stride of a mesh vertex, in bytes. */
    static final int VERTEX_STRIDE = VERTEX_NORMAL_OFFSET + (NUM_NORMAL_COMPONENTS_PER_VERTEX * Float.BYTES);
    /** The total stride of a 'semi' vertex, which is defined as a vertex without any normal vector. This stride is measured in bytes. */
    static final int SEMI_VERTEX_STRIDE = VERTEX_UV_OFFSET + (NUM_UV_COMPONENTS_PER_VERTEX * Float.BYTES);
    
    /** The offset of the model matrix attribute of a mesh instance, in bytes. */
    static final int INSTANCE_MODEL_MATRIX_OFFSET = 0;
    /** The offset of the projection/view/model matrix attribute of a mesh instance, in bytes. */
    static final int INSTANCE_PROJECTION_VIEW_MODEL_MATRIX_OFFSET = INSTANCE_MODEL_MATRIX_OFFSET + (16 * Float.BYTES);
    /** The total stride of a mesh instance, in bytes. */
    static final int INSTANCE_STRIDE = INSTANCE_PROJECTION_VIEW_MODEL_MATRIX_OFFSET + (16 * Float.BYTES);
    
    /** Status flag for if the graphical thread has completed initialization. */
    static final int STATUS_INITIALIZED = 1;
    /** Status flag for if the projection and view matrices, as well as the window options, have changed. */
    static final int STATUS_MATRICES_CHANGED = 2;
    /** Status flag for whether to render the skybox. */
    static final int STATUS_DO_SKYBOX_RENDER = 4;
    /** Status flag for whether to render the UI. */
    static final int STATUS_DO_UI_RENDER = 8;
    
    // --- VARIABLES ---
    
    /**
     * The {@link ScheduledExecutorService} containing the dedicated graphical thread.
     */
    private static final ScheduledExecutorService THREAD = Executors.newSingleThreadScheduledExecutor((r) -> new Thread(r, "Graphics"));
    /**
     * The {@link Future} for the graphical loop. This can be cancelled when it is time for
     * the loop to be terminated.
     */
    private static Future<?> LOOP_FUTURE;
    /**
     * The {@link BlockingQueue} messaging queue for the graphical thread. This is used for
     * inter-thread communication using {@link Message} instances.
     */
    static final BlockingQueue<Message> MESSAGE_QUEUE = new LinkedBlockingQueue<>(100);
    
    /**
     * The {@link AffineTransformation} corresponding to the camera position in world space.
     */
    static AffineTransformation VIEW_TRANSFORM = AffineTransformationImpl.IDENTITY;
    static Matrix VIEW_MATRIX = Matrix.identity(4);
    static Matrix PROJ_VIEW_MATRIX = Matrix.identity(4);
    
    /**
     * The {@link CountDownLatch} used to make the {@link GraphicsEngine} await the
     * initialization of the graphics thread before continuing.
     */
    static final CountDownLatch INIT_LATCH = new CountDownLatch(1);
    
    /**
     * The core rendering map. This maps shader programs to a set of renderables, and is
     * where all render operations originate. If for any reason a mapping between a shader
     * program and a renderable becomes invalid, then the mapping will be removed
     */
    private static final Map<GLShaderProgram, Set<GLRenderable>> RENDERING_MAP = new HashMap<>();
    private static byte STATUS = 0;
    static double DELTA_TIME = 0;
    private static Instant LOOP_TIMER;
    
    static final Set<GLRenderable> UI_RENDERABLES = new HashSet<>();
    static GLSkybox SKYBOX;
    
    static GLRenderable SELECTED_RENDERABLE = null;
    static GLMesh SELECTED_MESH = null;
    static GLShader SELECTED_SHADER = null;
    static GLShaderProgram SELECTED_PROGRAM = null;
    static GLTexture SELECTED_TEXTURE = null;
    static GLSkeleton SELECTED_SKELETON = null;
    
    // --- PACKAGE PRIVATE FUNCTIONS ---
    
    /**
     * Initializes the manager. This means initializing the graphical thread, and scheduling
     * the main rendering loop.
     * @param loopPeriod the minimum amount of time between invocations of the graphical loop,
     * in milliseconds
     */
    static void init(long loopPeriod) {
        THREAD.schedule(GraphicsManager::threadInitCallback, 0, TimeUnit.MILLISECONDS);
        LOOP_FUTURE = THREAD.scheduleAtFixedRate(GraphicsManager::threadLoopCallback, 0, loopPeriod, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Closes the manager, by terminating the graphical loop and scheduling the thread for
     * any finishing operations.
     */
    static void close() {
        LOOP_FUTURE.cancel(false);
        THREAD.schedule(GraphicsManager::threadFinishCallback, 0, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Applies a render mapping between the given program and the given renderable. This
     * places the given renderable within {@link RENDERING_MAP} using the program as the
     * key, then updates the renderable to hold a reference to the program.<p>
     * 
     * Note that this method does <i>not</i> check to ensure the renderable is in
     * {@link RENDERING_MAP} already.
     * 
     * @param program the {@link GLShaderProgram} to use as the new key
     * @param renderable the {@link GLRenderable} to use as the new value
     */
    static void applyRenderingMap(GLShaderProgram program, GLRenderable renderable) {
        if (program != null) {
            if (RENDERING_MAP.containsKey(program))
                RENDERING_MAP.get(program).add(renderable);
            else
                RENDERING_MAP.put(program, Sets.newHashSet(renderable));
        }
        
        renderable.program = program;
    }
    
    /**
     * Applies a render mapping between the given program and the given renderables. This
     * places each given renderable within {@link RENDERING_MAP} using the program as the
     * key, then updates the renderables to hold a reference to the program.<p>
     * 
     * Note that this method does <i>not</i> check to ensure the renderables are in
     * {@link RENDERING_MAP} already.
     * 
     * @param program the {@link GLShaderProgram} to use as the new key
     * @param renderable the set of {@link GLRenderable} instances to use as the new
     * values
     */
    static void applyRenderingMap(GLShaderProgram program, Set<GLRenderable> renderables) {
        if (RENDERING_MAP.containsKey(program))
            RENDERING_MAP.get(program).addAll(renderables);
        else
            RENDERING_MAP.put(program, renderables);
        
        renderables.forEach((renderable) -> renderable.program = program);
    }
    
    /**
     * Removes the render mapping of the renderable, if it has one. Rather than searching
     * the entire {@link RENDERING_MAP} for the renderable, this method uses the program
     * reference held within the renderable to know which key to look under; thus, the
     * renderable is required to have this reference be up-to-date for this method to work
     * correctly.
     * 
     * @param renderable the {@link GLRenderable} instance to remove from the render map
     */
    static void removeRenderingMap(GLRenderable renderable) {
        if (renderable.program != null && RENDERING_MAP.containsKey(renderable.program)) {
            RENDERING_MAP.get(renderable.program).remove(renderable);
            if (RENDERING_MAP.get(renderable.program).isEmpty())
                RENDERING_MAP.remove(renderable.program);
            renderable.program = null;
        }
    }
    
    static void setStatus(int code) {
        STATUS |= code;
    }
    
    static void clearStatus(int code) {
        STATUS &= ~code;
    }
    
    static boolean getStatus(int code) {
        return (STATUS & code) > 0;
    }
    
    static void handleMessage(Message message) {
        try {
            message.handle();
        } catch (Throwable t) {
            LOG.log(Level.FINER, "GraphicsManager.threadLoopCallback.MessageException", t);
            message.future.cancel(false);
        }
    }
    
    static FloatBuffer getInstanceMatrixBuffer(int size) {
        return BufferUtils.createFloatBuffer(32 * size);
    }
    
    static void vertexAttribPointerMatrix(int baseindex, int rowsize, boolean normalized, int stride, int baseoffset) {
        GL20.glVertexAttribPointer(baseindex, rowsize, GL11.GL_FLOAT, normalized, stride, baseoffset);
        GL20.glVertexAttribPointer(baseindex+1, rowsize, GL11.GL_FLOAT, normalized, stride, baseoffset + (rowsize * Float.BYTES));
        GL20.glVertexAttribPointer(baseindex+2, rowsize, GL11.GL_FLOAT, normalized, stride, baseoffset + (2 * rowsize * Float.BYTES));
        GL20.glVertexAttribPointer(baseindex+3, rowsize, GL11.GL_FLOAT, normalized, stride, baseoffset + (3 * rowsize * Float.BYTES));
    }
    
    static void vertexAttribDivisorMatrix(int baseindex, int divisor) {
        GL33.glVertexAttribDivisor(baseindex, divisor);
        GL33.glVertexAttribDivisor(baseindex+1, divisor);
        GL33.glVertexAttribDivisor(baseindex+2, divisor);
        GL33.glVertexAttribDivisor(baseindex+3, divisor);
    }
    
    static void enableVertexAttribArrayMatrix(int baseindex) {
        GL20.glEnableVertexAttribArray(baseindex);
        GL20.glEnableVertexAttribArray(baseindex+1);
        GL20.glEnableVertexAttribArray(baseindex+2);
        GL20.glEnableVertexAttribArray(baseindex+3);
    }
    
    static void disableVertexAttribArrayMatrix(int baseindex) {
        GL20.glDisableVertexAttribArray(baseindex);
        GL20.glDisableVertexAttribArray(baseindex+1);
        GL20.glDisableVertexAttribArray(baseindex+2);
        GL20.glDisableVertexAttribArray(baseindex+3);
    }
    
    // --- PRIVATE FUNCTIONS ---
    
    @SuppressWarnings("UseSpecificCatch")
    private static void threadInitCallback() {
        try {
            // Log and initiate the GL context and capabilities
            LOG.log(Level.FINEST, "global.Status.Init.Start", "Graphics thread");
            GraphicsEngine.makeContextCurrent();
            GL.createCapabilities();
            
            GL43.glDebugMessageCallback((source, type, id, severity, length, message, userParam) -> {
                String m = String.format("%s (%d)", GLDebugMessageCallback.getMessage(length, message), id);
                
                switch (type) {
                    case GL43.GL_DEBUG_TYPE_ERROR:       LOG.log(GLLevel.GL_DEBUG_ERROR, m); break;
                    case GL43.GL_DEBUG_TYPE_PERFORMANCE: LOG.log(GLLevel.GL_DEBUG_PERFORMANCE, m); break;
                }
            }, 0);

            // Initialize the GL constants
            MINOR_GL_VERSION_NUMBER = GL11.glGetInteger(GL30.GL_MINOR_VERSION);
            MAJOR_GL_VERSION_NUMBER = GL11.glGetInteger(GL30.GL_MAJOR_VERSION);
            MAX_COMBINED_TEXTURE_UNITS = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
            MAX_VERTEX_TEXTURE_UNITS = GL11.glGetInteger(GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS);
            MAX_GEOMETRY_TEXTURE_UNITS = GL11.glGetInteger(GL32.GL_MAX_GEOMETRY_TEXTURE_IMAGE_UNITS);
            MAX_FRAGMENT_TEXTURE_UNITS = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);
            MAX_TEXTURE_SIZE = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            MAX_ARRAY_TEXTURE_LAYERS = GL11.glGetInteger(GL30.GL_MAX_ARRAY_TEXTURE_LAYERS);
            MAX_TEXTURE_MIPMAP_LEVELS = (int)Math.floor(Math.log(MAX_TEXTURE_SIZE) / Math.log(2));
            MAX_VERTEX_ATTRIBS = GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS);

            // Post the constants to the logger
            LOG.log(Level.FINE, "GraphicsManager.threadInitCallback.OpenGLVersion", new Object[] { MAJOR_GL_VERSION_NUMBER, MINOR_GL_VERSION_NUMBER });
            LOG.log(Level.FINEST, "GraphicsManager.threadInitCallback.MaxTextureSize", MAX_TEXTURE_SIZE);
            LOG.log(Level.FINEST, "GraphicsManager.threadInitCallback.MaxCombinedTextureUnit", MAX_COMBINED_TEXTURE_UNITS);
            LOG.log(Level.FINEST, "GraphicsManager.threadInitCallback.MaxVertexTextureUnit", MAX_VERTEX_TEXTURE_UNITS);
            LOG.log(Level.FINEST, "GraphicsManager.threadInitCallback.MaxGeometryTextureUnit", MAX_GEOMETRY_TEXTURE_UNITS);
            LOG.log(Level.FINEST, "GraphicsManager.threadInitCallback.MaxFragmentTextureUnit", MAX_FRAGMENT_TEXTURE_UNITS);
            LOG.log(Level.FINEST, "GraphicsManager.threadInitCallback.MaxArrayTextureLayer", MAX_ARRAY_TEXTURE_LAYERS);
            LOG.log(Level.FINEST, "GraphicsManager.threadInitCallback.MaxMipmapLevel", MAX_TEXTURE_MIPMAP_LEVELS);
            LOG.log(Level.FINEST, "GraphicsManager.threadInitCallback.MaxVertexAttribute", MAX_VERTEX_ATTRIBS);
        
            // Initialize the defaults for each class of GL object
            GLMesh.getDefaults().forEach((mesh) -> {
                GLMesh.REGISTRY.register(mesh);
                mesh.setAsReadonly();
                mesh.init();
            });
            
            for (GLShader shader : GLShader.getDefaults()) {
                GLShader.REGISTRY.register(shader);
                shader.init();
            }
            
            for (GLShaderProgram program : GLShaderProgram.getDefaults()) {
                GLShaderProgram.REGISTRY.register(program);
                program.init();
                
                String e = program.linkAndValidate();
                if (program.getStatus(GLShaderProgram.STATUS_LINK_FAILED))
                    throw new IOException(e);
                
                program.setAsReadonly();
            }
            
            for (GLTexture2D tex : GLTexture2D.getDefaults()) {
                GLTexture2D.REGISTRY.register(tex);
                tex.init();
            }
            
            for (GLTextureCubemap tex : GLTextureCubemap.getDefaults()) {
                GLTextureCubemap.REGISTRY.register(tex);
                tex.init();
            }
        
            // Enable the GL options and functions
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL13.GL_TEXTURE_CUBE_MAP);

            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glClearColor(0.0f, 0.3f, 0.6f, 1.0f);
            GraphicsEngine.msgDisableVSync();
            GraphicsEngine.msgDisableDebugMode();

            // Initialize the skybox
            SKYBOX = new GLSkybox(GLTextureCubemap.DEFAULT);
            SKYBOX.init();
            
            // Initialize the SSBO containing a single 0 int value
            SHADER_STORAGE_BUFFER_OBJECT_ZERO = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, SHADER_STORAGE_BUFFER_OBJECT_ZERO);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, new int[]{ 0 }, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            
            setStatus(STATUS_INITIALIZED);
            LOOP_TIMER = Instant.now();

            LOG.log(Level.FINEST, "global.Status.Init.End", "Graphics thread");
            
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, LocaleUtils.format("global.Status.Init.Failed", "Graphics thread"), t);
            GraphicsEngine.close();
            
        } finally {
            INIT_LATCH.countDown();
        }
    }
    
    @SuppressWarnings("UseSpecificCatch")
    private static void threadLoopCallback() {
        try {
            DELTA_TIME = Duration.between(LOOP_TIMER, Instant.now()).toNanos() / 1e9d;
            LOOP_TIMER = Instant.now();
            
            // Listen for and handle messages on the message queue
            List<Message> messages = new ArrayList<>();
            
            if (GraphicsEngine.MESSAGE_LOCK.tryLock(0, TimeUnit.SECONDS)) {
                try {
                    if (!MESSAGE_QUEUE.isEmpty()) 
                        MESSAGE_QUEUE.drainTo(messages);
                } finally {
                    GraphicsEngine.MESSAGE_LOCK.unlock();
                }
            }
            
            messages.forEach((message) -> handleMessage(message));
            
            // If the view transform or projection settings are dirty, reset the
            // view and projection matrices
            if (VIEW_TRANSFORM.isDirty() || GraphicsEngine.isProjectionDirty()) {
                GraphicsEngine.flushProjectionMatrix();
                VIEW_MATRIX = VIEW_TRANSFORM.getTransformationMatrix(AffineTransformation.FLAG_IGNORE_SCALING |
                                                                     AffineTransformation.FLAG_INVERT_TRANSLATION |
                                                                     AffineTransformation.FLAG_INVERT_ROTATION |
                                                                     AffineTransformation.FLAG_INVERT_TRANSFORM_ORDER);

                PROJ_VIEW_MATRIX = GraphicsEngine.PROJ_MATRIX.mul(VIEW_MATRIX);
                setStatus(STATUS_MATRICES_CHANGED);
            }
            
            // Clear the color and depth bits
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            
            // Render the skybox, if necessary
            if (getStatus(STATUS_DO_SKYBOX_RENDER))
                SKYBOX.render();
            
            // Render the objects; if any shader programs fail for any reason, store
            // references to them for later
            Set<GLShaderProgram> failedProgs = new HashSet<>();
            
            RENDERING_MAP.forEach((program, renderables) -> {
                // Check if the program is failed or closed
                if (program.isClosed() || program.getStatus(GLShaderProgram.STATUS_LINK_FAILED)) {
                    failedProgs.add(program);
                    return;
                }
                
                // If the program isn't linked yet, attempt to link it
                if (!program.getStatus(GLShaderProgram.STATUS_LINKED)) {
                    String errorMessage = program.linkAndValidate();
                    if (errorMessage != null)
                        LOG.log(Level.WARNING, errorMessage);
                }
                
                // If the program is now linked, render with it
                // Otherwise, it has failed to link
                if (program.getStatus(GLShaderProgram.STATUS_LINKED))
                    doRenderWithProgram(program, renderables);
                else
                    failedProgs.add(program);
            });
            
            // For failed programs, remove the renderables from it in the rendering
            // map and add those renderables to the appropriate default program
            failedProgs.forEach((prog) -> {
                if (prog.getStatus(GLShaderProgram.STATUS_MODE))
                    GraphicsManager.applyRenderingMap(GLShaderProgram.DEFAULT_CUBE, RENDERING_MAP.remove(prog));
                else
                    GraphicsManager.applyRenderingMap(GLShaderProgram.DEFAULT_2D, RENDERING_MAP.remove(prog));
            });
            
            // Render the UI, if necessary (note that this doesn't disable depth testing,
            // but merely clears the depth bits)
            if (getStatus(STATUS_DO_UI_RENDER)) {
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                doRenderWithProgram(GLShaderProgram.DEFAULT_UI, UI_RENDERABLES);
            }
            
            // Swap the buffers
            GraphicsEngine.swapBuffers();
            clearStatus(STATUS_MATRICES_CHANGED);
            
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "GraphicsManager.threadLoopCallback.UnhandledException", t);
            close();
        }
    }
    
    private static void threadFinishCallback() {
        LOG.log(Level.FINEST, "global.Status.Close.Start", "Graphics thread");
        
        try {
            GLMesh.REGISTRY.forEach(GLObject::close);
            GLShader.REGISTRY.forEach(GLObject::close);
            GLShaderProgram.REGISTRY.forEach(GLObject::close);
            GLTexture.REGISTRY.forEach(GLObject::close);
            GLSkeleton.REGISTRY.forEach(GLObject::close);

            GLMesh.REGISTRY.clear();
            GLShader.REGISTRY.clear();
            GLShaderProgram.REGISTRY.clear();
            GLTexture.REGISTRY.clear();
            GLSkeleton.REGISTRY.clear();

            SKYBOX.close();
            
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            GL15.glDeleteBuffers(SHADER_STORAGE_BUFFER_OBJECT_ZERO);
            
            clearStatus(STATUS_INITIALIZED);
            
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, LocaleUtils.format("global.Status.Close.Failed", "Graphics thread"), t);
        }
        
        LOG.log(Level.FINEST, "global.Status.Close.End", "Graphics thread");
    }
    
    private static void doRenderWithProgram(GLShaderProgram program, Set<GLRenderable> renderables) {
        program.bind();
        
        // 'Global' uniform values, these do not change over the course of the use
        // of this program
        if (getStatus(STATUS_MATRICES_CHANGED)) {
            program.uniform3ui(GraphicsEngine.getWindowWidth(),
                               GraphicsEngine.getWindowHeight(),
                               GraphicsEngine.getWindowDepth(),
                               SHADER_UNIFORM_WINDOW_SIZE_NAME);

            program.uniformMatrix4(GraphicsEngine.PROJ_MATRIX, SHADER_UNIFORM_PROJECTION_MATRIX_NAME);
            program.uniformMatrix4(VIEW_MATRIX, SHADER_UNIFORM_VIEW_MATRIX_NAME);
        }
        
        // Do the rendering passes for each renderable
        renderables.forEach((renderable) -> {
            if (!renderable.render(program))
                LOG.log(Level.FINER, "GraphicsManager.doRenderWithProgram.MeshInvalidParams");
        });
        
        program.unbind();
    }
}
