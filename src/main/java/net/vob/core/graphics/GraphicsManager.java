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
 */
final class GraphicsManager {
    private GraphicsManager() {}
    
    // --- CONSTANTS ---
    private static final Logger LOG = VoidOfBlue.getLogger(GraphicsManager.class);
    
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
    /** The string variable name shaders must use for referencing the SSBO variable containing the vertex-bone weight values of skeletons. */
    static final String SHADER_UNIFORM_SKELETON_WEIGHTS_NAME = "skeletonWeights";
    /** The string variable name shaders must use for referencing the SSBO variable containing the affine transformations of skeletons. */
    static final String SHADER_UNIFORM_SKELETON_TRANSFORMS_NAME = "skeletonTransforms";
    
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
    
    /** The number of rows/columns in the model matrix for each instance of a mesh. */
    static final int NUM_MODEL_MATRIX_ROWS_PER_INSTANCE = 4;
    /** The number of rows/columns in the projection/view/model matrix for each instance of a mesh. */
    static final int NUM_PROJECTION_VIEW_MODEL_MATRIX_ROWS_PER_INSTANCE = 4;
    /** The offset of the model matrix attribute of a mesh instance, in bytes. */
    static final int INSTANCE_MODEL_MATRIX_OFFSET = 0;
    /** The offset of the projection/view/model matrix attribute of a mesh instance, in bytes. */
    static final int INSTANCE_PROJECTION_VIEW_MODEL_MATRIX_OFFSET = INSTANCE_MODEL_MATRIX_OFFSET + (NUM_MODEL_MATRIX_ROWS_PER_INSTANCE * NUM_MODEL_MATRIX_ROWS_PER_INSTANCE * Float.BYTES);
    /** The total stride of a mesh instance, in bytes. */
    static final int INSTANCE_STRIDE = INSTANCE_PROJECTION_VIEW_MODEL_MATRIX_OFFSET + (NUM_PROJECTION_VIEW_MODEL_MATRIX_ROWS_PER_INSTANCE * NUM_PROJECTION_VIEW_MODEL_MATRIX_ROWS_PER_INSTANCE * Float.BYTES);
    
    /** Status flag for if the projection and view matrices, as well as the window options, have changed. */
    static final int STATUS_MATRICES_CHANGED = 1;
    /** Status flag for whether to render the skybox. */
    static final int STATUS_DO_SKYBOX_RENDER = 2;
    /** Status flag for whether to render the UI. */
    static final int STATUS_DO_UI_RENDER = 4;
    
    // --- VARIABLES ---
    
    private static final ScheduledExecutorService THREAD = Executors.newSingleThreadScheduledExecutor((r) -> new Thread(r, "Graphics"));
    private static Future<?> LOOP_FUTURE;
    
    static final BlockingQueue<Message> MESSAGE_QUEUE = new LinkedBlockingQueue<>(1000);
    static AffineTransformation VIEW_TRANSFORM = AffineTransformationImpl.IDENTITY;
    static Matrix VIEW_MATRIX = Matrix.identity(4);
    static Matrix PROJ_MATRIX = Matrix.identity(4);
    static Matrix PROJ_VIEW_MATRIX = Matrix.identity(4);
    
    static final CountDownLatch INIT_LATCH = new CountDownLatch(1);
    
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
    
    // --- PACKAGE PRIVATE FUNCTIONS ---
    
    static void init(long loopPeriod) {
        THREAD.schedule(GraphicsManager::threadInitCallback, 0, TimeUnit.MILLISECONDS);
        LOOP_FUTURE = THREAD.scheduleAtFixedRate(GraphicsManager::threadLoopCallback, 0, loopPeriod, TimeUnit.MILLISECONDS);
    }
    
    static void close() {
        LOOP_FUTURE.cancel(false);
        THREAD.schedule(GraphicsManager::threadFinishCallback, 0, TimeUnit.MILLISECONDS);
    }
    
    static void applyRenderingMap(GLShaderProgram program, GLRenderable renderable) {
        if (program != null) {
            if (RENDERING_MAP.containsKey(program))
                RENDERING_MAP.get(program).add(renderable);
            else
                RENDERING_MAP.put(program, Sets.newHashSet(renderable));
        }
        
        renderable.program = program;
    }
    
    static void applyRenderingMap(GLShaderProgram program, Set<GLRenderable> renderables) {
        if (RENDERING_MAP.containsKey(program))
            RENDERING_MAP.get(program).addAll(renderables);
        else
            RENDERING_MAP.put(program, renderables);
        
        renderables.forEach((renderable) -> renderable.program = program);
    }
    
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
        int s1 = NUM_MODEL_MATRIX_ROWS_PER_INSTANCE * NUM_MODEL_MATRIX_ROWS_PER_INSTANCE;
        int s2 = NUM_PROJECTION_VIEW_MODEL_MATRIX_ROWS_PER_INSTANCE * NUM_PROJECTION_VIEW_MODEL_MATRIX_ROWS_PER_INSTANCE;
        return BufferUtils.createFloatBuffer((s1 + s2) * size);
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
            if (VIEW_TRANSFORM.isDirty() || GraphicsEngine.windowOptions.isDirty()) {
                PROJ_MATRIX = GraphicsEngine.windowOptions.flushAndGetProjectionMatrix();
                VIEW_MATRIX = VIEW_TRANSFORM.getInverseTransformationMatrix();

                PROJ_VIEW_MATRIX = PROJ_MATRIX.mul(VIEW_MATRIX);
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
            
            // Swap the buffers, and log any errors that occurred
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

            GLMesh.REGISTRY.clear();
            GLShader.REGISTRY.clear();
            GLShaderProgram.REGISTRY.clear();
            GLTexture.REGISTRY.clear();

            SKYBOX.close();
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
            program.uniform3ui(GraphicsEngine.windowOptions.getWindowWidth(),
                               GraphicsEngine.windowOptions.getWindowHeight(),
                               GraphicsEngine.windowOptions.getWindowDepth(),
                               SHADER_UNIFORM_WINDOW_SIZE_NAME);

            program.uniformMatrix4(PROJ_MATRIX, SHADER_UNIFORM_PROJECTION_MATRIX_NAME);
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
