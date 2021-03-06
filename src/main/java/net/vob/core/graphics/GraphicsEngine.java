package net.vob.core.graphics;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.vob.VoidOfBlue;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import net.vob.util.logging.Level;
import net.vob.util.Identity;
import net.vob.util.math.Vector2;
import net.vob.util.math.Vector3;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.AffineTransformation;
import net.vob.util.Input;
import net.vob.util.Tree;
import net.vob.util.Trees;
import net.vob.util.math.Maths;
import net.vob.util.math.Matrix;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;

import static org.lwjgl.glfw.GLFW.*;
import org.lwjgl.opengl.GL11;

/**
 * The static manager class for a single window. This extends to not only handling GLFW and
 * its functionality, but also providing several methods to allow other threads to
 * interface with the {@code GraphicsManager} class in a thread-safe way; essentially,
 * this class is the public interface of the graphics engine through which all external
 * classes must go through (hence the name).<p>
 * 
 * The {@code GraphicsEngine} is based around handling the window in which graphics are
 * drawn. It mainly concerns itself with handling the {@code GLFW} library, and
 * additionally contains constants sourced from that library. This class also contains
 * methods that communicate with the {@code GraphicsManager} via a messaging system,
 * sending individual <i>messages</i> to an internal synchronized queue which the graphical
 * thread can then pull from and handle. This allows external threads and classes to
 * communicate effectively with the engine without compromising thread-safety. These
 * messaging methods return {@link CompletableFuture} objects, to allow for asynchronous
 * two-way communication.<p>
 * 
 * The {@code GraphicsManager} is the internal manager class for actually performing
 * rendering, and focuses on calls to the {@code GL} library as opposed to {@code GLFW}.
 * The two managers have been separated because of this distinction between {@code GL} and
 * {@code GLFW}. {@code GL} handles any rendering to the window, and most/all calls to it
 * require the calling thread to have the 'context' of the window; as such, {@code GL}
 * invocations should only be made internally, as only the graphical thread has the window
 * context. By comparison, {@code GLFW} handles the window itself, and while it does have a
 * few methods for rendering purposes, it mostly has methods and functionalities that should
 * remain decoupled from the graphics side of the engine; most prominently, all inputs to
 * the window are piped through {@code GLFW}.<p>
 * 
 * <i>GL objects</i> are what the {@code GraphicsManager} uses for rendering. All of them
 * are stored in {@link Registry} instances as opposed to being stored in a simple map or
 * list; this is to allow identical GL objects to be collapsed into a single instance instead
 * of remaining as duplicate instances (e.g. 2 textures sourced using the same 
 * {@link Identity} will not instantiate 2 texture objects, but will instead be the same
 * texture data shared between them). This has the effect of saving memory on the GPU (this
 * effect is most prominent with textures). It also allows for external classes to handle
 * GL objects by passing around integer ID values, as opposed to exposing these objects as
 * public.<p>
 * 
 * GL objects exist in a hierarchy, of sorts. This hierarchy closely follows theoretical
 * relations between objects, and serves to simplify the operation of the
 * {@code GraphicsManager} and the rendering loop. The hierarchy is as follows:
 * <blockquote><pre>
 *                     <b>Mesh</b>              (multiple renderables        <b>Vertex Shader</b> (mandatory)
 *                        \u2198                   per program)             \u2199
 *      <b>2D Texture</b>      <b>Renderable Object</b>      \u27a1     <b>Shader Program</b> \u2b05 <b>Geometry Shader</b> (optional)
 *              \u2198         \u2197                                            \u2196
 *               <b>Textures</b>                                             <b>Fragment Shader</b> (mandatory)
 *              \u2197     (only one type of texture
 * <b>Cubemap Texture</b>       per renderable)
 * </pre></blockquote>
 * External threads and classes must use the messaging functions this class provides to
 * communicate with the graphics thread, and build up the desired object(s) as needed. For
 * example, an external thread may want to instantiate a new shader. To do so, it must
 * instantiate a {@code ShaderProgram}, then instantiate each component {@code Shader}
 * stage and attach each {@code Shader} to the {@code ShaderProgram} in turn. This
 * {@code ShaderProgram} can then have one or more {@code Renderable} objects assigned to
 * it, and they will use that {@code ShaderProgram} for rendering from then on. Be sure to
 * read through the javadocs of all the messaging methods for more information.<p>
 * 
 * In addition to these standard GL objects, there also exist other, more specialized GL
 * objects, parameters and pipelines, which can all be manipulated from outside the graphics
 * thread through the messaging system. This is the complete list of them:
 * <ul>
 *  <li>The <i>skybox</i>; a singleton object that is independent of the surrounding
 * classes and systems, which is specialized towards rendering the background sky texture.</li>
 *  <li>The <i>UI system</i>; a special system for 2D UI renderables, which guarantees that
 * renderables within it will be drawn on top of the rest of the scene without being
 * occluded.</li>
 *  <li>Renderable <i>instances</i>; a parameter that allows for the GPU to render multiple
 * copies of the same renderable with a single rendering call, which can speed up rendering
 * in cases where large numbers of the same renderable must be drawn at once; e.g. grass or
 * leaves.</li>
 *  <li>Renderable <i>skeletons</i>; a special system to allow individual vertices of a
 * renderable's mesh to be offset according to some number of affine transformation 'bones',
 * alongside a weighted mapping of vertices to bones. This allows the graphics engine to
 * perform animations via manipulation of the bones, without needing to change or alter the
 * meshes.</li>
 * </ul>
 * <b>Note:</b>
 * <ul>
 *  <li>This manager must be initialized before any use, and closed before the program is
 * shutdown to free up system resources and prevent memory leaking.</li>
 *  <li>The projection matrix used for rendering and any related parameters are contained
 * here rather than in {@code GraphicsManager}. This design choice was chosen to separate
 * certain elements from each other to reduce confusion; specifically, this design separates
 * the projection matrix (found here) from the view matrix (found in {@code GraphicsManager}).
 * This is also due to the intended purpose of this class, which is to contain any
 * information regarding the window/viewport itself separately from any information used
 * directly for rendering (as the projection matrix is built entirely on the parameters of
 * the viewport).</li>
 *  <li>The graphics engine, when passed an {@link AffineTransformation} via any messaging
 * function, relies on the dirty flags of these transformations to know when to update the
 * GPU buffers; thus, outside threads should take care when calling one of the
 * transformation matrix methods of these instances, as that will clear the dirty flag.</li>
 *  <li>Renderable instances share <i>everything</i> except for their origin point
 * transformation; this includes meshes, textures, shaders and skeleton transformations. If
 * any of these are required or expected to be independent of one another, then users should
 * be using separate renderables instead of instances.</li>
 * </ul> 
 * <b>Warning: the manager cannot be reopened after being closed. Thus, only specific
 * external classes should attempt to close this manager.</b>
 * 
 * @author Lyn-Park
 */
public final class GraphicsEngine {
    /**
     * An empty, private constructor. This prevents the class from the being instantiated,
     * as it is designed to be a container class for static methods and fields.
     */
    private GraphicsEngine() {}
    
    
    
    // --- CONSTANTS ---
    
    
    
    private static final Logger LOG = VoidOfBlue.getLogger(GraphicsEngine.class);
    
    /**
     * A value that certain function parameters can take, which signals that the
     * parameter should be ignored.
     */
    public static final int VALUE_DONT_CARE = GLFW_DONT_CARE;
    
    /**
     * The minimum window width that an instance of {@link WindowOptions} can have.
     */
    public static final int CONSTANT_MIN_WINDOW_WIDTH = 64;
    /**
     * The minimum window height that an instance of {@link WindowOptions} can have.
     */
    public static final int CONSTANT_MIN_WINDOW_HEIGHT = 64;
    /**
     * The minimum FOV that an instance of {@link WindowOptions} can have.
     */
    public static final float CONSTANT_MIN_FOV = 10f;
    /**
     * The maximum FOV that an instance of {@link WindowOptions} can have.
     */
    public static final float CONSTANT_MAX_FOV = 170f;
    /**
     * The minimum distance to the near Z-clipping plane that an instance of 
     * {@link WindowOptions} can have.
     */
    public static final float CONSTANT_MIN_ZNEAR_DIST = 0.01f;
    /**
     * The maximum distance to the far Z-clipping plane that an instance of 
     * {@link WindowOptions} can have.
     */
    public static final float CONSTANT_MAX_ZFAR_DIST = 10000f;
    /**
     * The minimum distance between the Z-clipping planes that an instance of 
     * {@link WindowOptions} can have.
     */
    public static final float CONSTANT_MIN_Z_DIST_SEPARATION = 1f;

    /** The value corresponding to an arrow shaped cursor. */
    public static final int CURSOR_ARROW = GLFW_ARROW_CURSOR;
    /** The value corresponding to an I-beam shaped cursor. */
    public static final int CURSOR_IBEAM = GLFW_IBEAM_CURSOR;
    /** The value corresponding to a crosshair shaped cursor. */
    public static final int CURSOR_CROSSHAIR = GLFW_CROSSHAIR_CURSOR;
    /** The value corresponding to a hand shaped cursor. */
    public static final int CURSOR_HAND = GLFW_HAND_CURSOR;
    /** The value corresponding to a horizontal resizing shaped cursor. */
    public static final int CURSOR_HRESIZE = GLFW_HRESIZE_CURSOR;
    /** The value corresponding to a vertical resizing shaped cursor. */
    public static final int CURSOR_VRESIZE = GLFW_VRESIZE_CURSOR;
    
    /** The {@link Identity} corresponding to the default texture. */
    public static final Identity DEFAULT_TEXTURE_ID = new Identity("default_texture").partial("core");
    /** The {@link Identity} corresponding to the default 2D shader. */
    public static final Identity DEFAULT_SHADER_2D_ID = new Identity("default_shader_2d").partial("core");
    /** The {@link Identity} corresponding to the default cubemap shader. */
    public static final Identity DEFAULT_SHADER_CUBE_ID = new Identity("default_shader_cubemap").partial("core");
    /** The {@link Identity} corresponding to the default UI shader. */
    public static final Identity DEFAULT_SHADER_UI_ID = new Identity("default_shader_ui").partial("core");
    /** The {@link Identity} corresponding to the skybox shader. */
    public static final Identity SKYBOX_SHADER_ID = new Identity("skybox_shader").partial("core");
    
    /** Status flag for if the graphics engine is currently initialized. */
    public static final int STATUS_INITIALIZED = 1;
    /** Status flag for if the graphics engine can be (but currently isn't) initialized. */
    public static final int STATUS_INITIALIZABLE = 2;
    /** Status flag for if V-Sync is enabled. */
    public static final int STATUS_VSYNC = 4;
    
    /** Status flag for if the projection matrix must be recalculated. */
    private static final int STATUS_PROJECTION_DIRTY = 8;
    /** Status flag for if the GLFW window resizing function should be called. */
    private static final int STATUS_UPDATE_WINDOW_SIZE = 16;
    /** Status flag for if the window is in fullscreen mode. */
    private static final int STATUS_FULLSCREEN = 32;
    /** Status flag for if the fullscreen mode was changed on the previous render cycle. */
    private static final int STATUS_FULLSCREEN_DIRTY = 64;
    
    /**
     * The messaging lock for the window manager.<p>
     * 
     * For inter-thread communication with the {@code GraphicsManager}, the graphics
     * engine utilizes a message queue; messages are appended to the end of the queue as
     * they are invoked, and then they are handled (in the same order they were queued)
     * by the graphics thread at the beginning of the next graphical rendering cycle. If
     * multiple threads are attempting to queue messages like this simultaneously, they
     * can end up interfering with each other's operations.<p>
     * 
     * As such, threads wishing to communicate with the {@code GraphicsManager} can use
     * this static {@link ReentrantLock} to achieve thread-safety, by locking it before
     * a series of messages are queued and releasing it once all desired messages have
     * been submitted. It is up to the callers to use this lock, if needed.
     */
    public static final ReentrantLock MESSAGE_LOCK = new ReentrantLock();
    
    /** The {@link ReentrantLock} used by the status mutating/inquiring methods. */
    private static final ReentrantLock STATUS_LOCK = new ReentrantLock();
    
    /** The {@link ReentrantLock} used by the projection matrix mutating/inquiring methods. */
    private static final ReentrantLock PROJECTION_LOCK = new ReentrantLock();
    
    
    
    // --- FIELDS ---
    
    
    
    /** The status code. */
    private static byte status = STATUS_INITIALIZABLE | STATUS_PROJECTION_DIRTY | STATUS_UPDATE_WINDOW_SIZE;
    /** The long value corresponding to the window. */
    private static long window;
    /** The long value corresponding to the cursor. */
    private static long cursor;
    /** The int value for the X-coordinate of the window. */
    private static int _x;
    /** The int value for the Y-coordinate of the window. */
    private static int _y;
    
    /** The int value for the width of the window in screen coordinates. */
    private static int _width;
    /** The int value for the height of the window in screen coordinates. */
    private static int _height;
    /** The int value for the depth of the window in screen coordinates. */
    private static int _depth;
    /** The float value for the FOV of the viewport. */
    private static float _fov;
    /** The float value for the distance from the camera of the near Z-clipping plane. */
    private static float _zNearDist;
    /** The float value for the distance from the camera of the far Z-clipping plane. */
    private static float _zFarDist;
    
    /** The projection matrix instance. */
    static final Matrix PROJ_MATRIX = new Matrix(4);
    
    /**
     * The 2D position of the cursor, relative to the upper-left corner of the window. The
     * units of these coordinates are measured in pixels. 
     */
    private static final Vector2 cursorPos = new Vector2();
    
    /** The variable for containing the current thread that has the GL context. */
    private static Thread CONTEXT = null;
    
    
    
    // --- FUNCTIONS ---
    
    
    
    /**
     * Initializes the graphics engine, including this manager and {@code GraphicsManager}.<p>
     * 
     * The status flags of this manager are initially set to {@link STATUS_INITIALIZABLE}.
     * Once the initialization is fully complete, then the {@code STATUS_INITIALIZABLE}
     * flag is cleared, and the {@link STATUS_INITIALIZED} flag is set. In the event that
     * the {@code STATUS_INITIALIZABLE} flag was not set on this method's invocation, then
     * this method returns immediately without performing any function (this includes
     * changing the internal status flags). Similarly, any errors that occur with
     * initializing GLFW or the window will cause an immediate return without changing
     * the status flags.<p>
     * 
     * The internal state of the {@code GraphicsManager} is initialized to perform the
     * graphical rendering algorithm with a periodicity of {@code graphicsLoopPeriodMS};
     * in other words, this is the target time between the scheduling of two consecutive
     * rendering invocations. In addition, it is initialized to have no currently selected
     * objects (see the javadocs of the {@code msgXXXSelect(index)} functions for more
     * information on object selecting).<p>
     * 
     * Other than {@code graphicsLoopPeriodMS}, each parameter defines the initial status
     * of the window. See the javadocs on the getter methods for more information on the
     * meaning of each parameter, and each setter methods for more information on clamping
     * of the values to within certain ranges.
     * 
     * @param windowX the positional X-coordinate of the window, or {@link VALUE_DONT_CARE}
     * to signal that the window should be aligned with the centre of the screen along the
     * X-axis
     * @param windowY the positional Y-coordinate of the window, or {@link VALUE_DONT_CARE}
     * to signal that the window should be aligned with the centre of the screen along the
     * Y-axis
     * @param windowWidth the width of the window
     * @param windowHeight the height of the window
     * @param windowDepth the depth of the window
     * @param fov the FOV of the viewport
     * @param zNearDist the near clipping plane distance
     * @param zFarDist the far clipping plane distance
     * @param fullscreen {@code true} is these options are in fullscreen mode,
     * {@code false} if they are in windowed mode
     * @param graphicsLoopPeriodMS the target length, in milliseconds, between invocations
     * of the graphical loop in the graphics thread
     * @throws InterruptedException if this thread is interrupted while waiting for the
     * graphics thread to be initialized
     */
    public static void init(int windowX,
                            int windowY,
                            int windowWidth,
                            int windowHeight,
                            int windowDepth,
                            float fov,
                            float zNearDist,
                            float zFarDist,
                            boolean fullscreen,
                            int graphicsLoopPeriodMS) throws InterruptedException
    {
        if (!getStatus(STATUS_INITIALIZABLE))
            return;
        
        LOG.log(Level.FINEST, "global.Status.Init.Start", "Graphics engine");
        
        glfwSetErrorCallback((error, description) -> LOG.log(Level.FINER, String.format("[0x%X] %s", error, GLFWErrorCallback.getDescription(description))));
        
        if (!glfwInit()) {
            LOG.log(Level.SEVERE, "global.Status.Init.Failed", "GLFW");
            return;
        }
        
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        
        window = glfwCreateWindow(windowWidth, windowHeight, "Void of Blue", fullscreen ? glfwGetPrimaryMonitor() : 0, 0);
        if (window == 0) {
            LOG.log(Level.SEVERE, "global.Status.Init.Failed", "Window");
            return;
        }
        
        glfwSetWindowSizeLimits(window, CONSTANT_MIN_WINDOW_WIDTH, CONSTANT_MIN_WINDOW_HEIGHT, GLFW_DONT_CARE, GLFW_DONT_CARE);
        
        int[] w = new int[1], h = new int[1];
        glfwGetWindowSize(window, w, h);
        
        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(window, windowX == VALUE_DONT_CARE ? (vidMode.width() - w[0]) / 2 : windowX,
                                 windowY == VALUE_DONT_CARE ? (vidMode.height() - h[0]) / 2 : windowY);
        
        LOG.log(Level.FINEST, "GraphicsEngine.init.WindowInit", new Object[] { w[0], h[0] });
        
        glfwSetWindowCloseCallback(window, (_window) -> {
            glfwSetWindowShouldClose(window, false);
            VoidOfBlue.stopProgram();
        });
        
        glfwSetWindowPosCallback(window, (_window, xpos, ypos) -> {
            _x = xpos;
            _y = ypos;
        });
        
        glfwSetWindowSizeCallback(window, (_window, width, height) -> {
            if (!getStatus(STATUS_FULLSCREEN)) {
                setWindowWidth(width, false);
                setWindowHeight(height, false);
            }
        });
        
        glfwSetCursorPosCallback(window, (_window, xpos, ypos) -> {
            cursorPos.setX(xpos);
            cursorPos.setY(getWindowHeight() - ypos);
            
            Input input = new Input(Input.Source.MOUSE, cursorPos, 0, 0, 0);
            
            // TODO - pass input to other areas
        });
        
        glfwSetMouseButtonCallback(window, (_window, button, action, mods) -> {
            Input input = new Input(Input.Source.MOUSE, cursorPos, button, 1, action);
            
            // TODO - pass input to other areas
        });
        
        glfwSetKeyCallback(window, (_window, key, scancode, action, mods) -> {
            Input input = new Input(Input.Source.KEY, null, key, scancode, action);
            
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE)
                VoidOfBlue.stopProgram();
            if (key == GLFW.GLFW_KEY_F && action == GLFW.GLFW_RELEASE) {
                PROJECTION_LOCK.lock();
                
                try {
                    toggleStatus(STATUS_FULLSCREEN);
                    setStatus(STATUS_PROJECTION_DIRTY | STATUS_FULLSCREEN_DIRTY, true);
                } finally {
                    PROJECTION_LOCK.unlock();
                }
            }
            // TODO - pass input to other areas
        });
        
        glfwSetCharCallback(window, (_window, codepoint) -> {
            Input input = new Input(Input.Source.CHAR, null, codepoint, 0, 0);
            
            // TODO - pass input to other areas
        });
        
        cursor = glfwCreateStandardCursor(CURSOR_ARROW);
        glfwSetCursor(window, cursor);
        glfwShowWindow(window);
        
        setStatus(STATUS_INITIALIZABLE, false);
        setStatus(STATUS_INITIALIZED, true);
        
        _x = windowX;
        _y = windowY;
        _width = windowWidth;
        _height = windowHeight;
        _depth = windowDepth;
        _fov = fov;
        _zNearDist = zNearDist;
        _zFarDist = zFarDist;
        setStatus(STATUS_FULLSCREEN, fullscreen);
        
        GraphicsManager.init(graphicsLoopPeriodMS);
        GraphicsManager.INIT_LATCH.await();
        
        if (GraphicsManager.getStatus(GraphicsManager.STATUS_INITIALIZED))
            LOG.log(Level.FINEST, "global.Status.Init.End", "Graphics engine");
    }
    
    /**
     * Closes the graphics engine, terminating the graphics thread and GLFW, and freeing
     * any system resources held by still-active GL objects.<p>
     * 
     * This method clears the {@link STATUS_INITIALIZED} flag once the closing operation
     * is complete. In the event the {@code STATUS_INITIALIZED} flag was not set on this
     * method's invocation, then this method returns immediately without performing any
     * function.
     */
    public static void close() {
        if (!getStatus(STATUS_INITIALIZED))
            return;
        
        LOG.log(Level.FINEST, "global.Status.Close.Start", "Graphics engine");
        
        GraphicsManager.close();
        
        glfwDestroyCursor(cursor);
        glfwDestroyWindow(window);
        
        glfwTerminate();
        
        setStatus(STATUS_INITIALIZED, false);
        LOG.log(Level.FINE, "global.Status.Close.End", "Graphics engine");
    }
    
    /**
     * Gets the boolean status associated with the given status flag(s). This method
     * permits a parameter that is the bitwise-OR of multiple status flags; in this
     * case, the returned boolean is a bitwise-OR of the associated boolean statuses.
     * 
     * @param statusCode one of {@link STATUS_INITIALIZED}, {@link STATUS_INITIALIZABLE}
     * or {@link STATUS_VSYNC}, or a bitwise-OR combination of these values
     * @return {@code true} if one of the queried statuses has a value of {@code true},
     * {@code false} otherwise
     */
    public static boolean getStatus(int statusCode) {
        STATUS_LOCK.lock();
        try {
            return (status & statusCode) > 0;
        } finally {
            STATUS_LOCK.unlock();
        }
    }
    
    static void setStatus(int statusCode, boolean value) {
        STATUS_LOCK.lock();
        try {
            if (value) status |= statusCode;
            else       status &= ~statusCode;
        } finally {
            STATUS_LOCK.unlock();
        }
    }
    
    static void toggleStatus(int statusCode) {
        STATUS_LOCK.lock();
        try {
            status ^= statusCode;
        } finally {
            STATUS_LOCK.unlock();
        }
    }
    
    static void makeContextCurrent() {
        glfwMakeContextCurrent(window);
        CONTEXT = Thread.currentThread();
    }
    
    static boolean hasCurrentContext() {
        return CONTEXT != null && CONTEXT.equals(Thread.currentThread());
    }
    
    static void swapBuffers() {
        glfwSwapBuffers(window);
    }
    
    static void setWindowSize(int windowWidth, int windowHeight) {
        glfwSetWindowSize(window, windowWidth, windowHeight);
    }
    
    static long getCurrentMonitor(boolean ignoreFullscreen) {
        if (!ignoreFullscreen && getIsFullscreen())
            return glfwGetWindowMonitor(window);
        
        PointerBuffer monitors = glfwGetMonitors();
        if (monitors.remaining() == 0)
            return -1;
        else if (monitors.remaining() == 1)
            return monitors.get(0);
        
        int ww, wh;
        int[] mx = new int[1], my = new int[1];
        int mw, mh;
        int overlap, bestOverlap = 0;
        
        long bestMatch = -1;
        GLFWVidMode mode;
        
        ww = getWindowWidth();
        wh = getWindowHeight();
        
        for (int i = 0; i < monitors.remaining(); ++i) {
            mode = glfwGetVideoMode(monitors.get(i));
            glfwGetMonitorPos(monitors.get(i), mx, my);
            mw = mode.width(); mh = mode.height();
            
            overlap = Math.max(0, Math.min(_x + ww, mx[0] + mw) - Math.max(_x, mx[0])) *
                      Math.max(0, Math.min(_y + wh, my[0] + mh) - Math.max(_y, my[0]));
            
            if (bestOverlap < overlap) {
                bestOverlap = overlap;
                bestMatch = monitors.get(i);
            }
        }
        
        return bestMatch;
    }
    
    static void getMonitorSize(long monitor, int[] width, int[] height) {
        GLFWVidMode mode = glfwGetVideoMode(monitor);
        width[0] = mode.width();
        height[0] = mode.height();
    }
    
    static void doEnableFullscreen() {
        long monitor = getCurrentMonitor(true);
        GLFWVidMode mode = glfwGetVideoMode(monitor);
        glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), GLFW_DONT_CARE);
    }
    
    static void doDisableFullscreen() {
        glfwSetWindowMonitor(window, 0, _x, _y, getWindowWidth(), getWindowHeight(), GLFW_DONT_CARE);
    }
    
    static void doEnableVSync() {
        glfwSwapInterval(1);
        setStatus(STATUS_VSYNC, true);
    }
    
    static void doDisableVSync() {
        glfwSwapInterval(0);
        setStatus(STATUS_VSYNC, false);
    }
    
    /**
     * Polls the GLFW events system. This processes events that are currently held in
     * the event queue, and returns immediately. Regular polling using this method or
     * {@link waitEvents()} is necessary to prevent the window from appearing to
     * become unresponsive.
     * 
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void pollEvents() {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
            
        glfwPollEvents();
    }
    
    /**
     * Waits for an event on the GLFW events system. This puts the thread to sleep until
     * at least 1 event is available; once an event is available, this method behaves
     * exactly like {@link pollEvents()}.
     * 
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void waitEvents() {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
            
        glfwWaitEvents();
    }
    
    /**
     * Waits with a given timeout duration for an event on the GLFW events system. This
     * waits for an event exactly like {@link waitEvents()}; however, the thread is
     * also awakened if the timeout limit is reached without processing any events.
     * 
     * @param timeout the maximum amount of time the thread can wait, in seconds
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     * @throws IllegalArgumentException if {@code timeout} is not a positive finite
     * number
     */
    public static void waitEvents(double timeout) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        if (Double.isInfinite(timeout) || timeout <= 0)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>", "timeout", timeout, 0));
        
        glfwWaitEventsTimeout(timeout);
    }
    
    /**
     * Posts an empty event to the GLFW events system. This event does nothing, and only
     * serves as way to cause threads waiting on {@link waitEvents()} or
     * {@link waitEvents(double)} to awaken and return.
     * 
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void postEmptyEvent() {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        glfwPostEmptyEvent();
    }
    
    /**
     * Gets the width of the window. This is the full graphical width of the window
     * measured in pixels, <i>when the window is in windowed mode</i>. This means that
     * this method will not return the true width of the window when it is in
     * fullscreen mode.
     * @return the width of the window
     */
    public static int getWindowWidth() {
        PROJECTION_LOCK.lock();
        
        try {
            return _width;
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Gets the height of the window. This is the full graphical height of the window
     * measured in pixels, <i>when the window is in windowed mode</i>. This means that
     * this method will not return the true height of the window when it is in
     * fullscreen mode.
     * @return the height of the window
     */
    public static int getWindowHeight() {
        PROJECTION_LOCK.lock();
        
        try {
            return _height;
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Gets the depth of the window. This depth is visible (along with the width and
     * height) within shader programs as a {@code vec3} uniform value under the name
     * {@link GraphicsManager#SHADER_UNIFORM_WINDOW_SIZE_NAME}; the exact meaning of
     * the window 'depth' is thus shader-dependent (for example, the vanilla default
     * UI shader uses these dimensions to define the window space as
     * {@code [(0,0,0), (width, height, depth)]}).
     * 
     * @return the depth of the window
     */
    public static int getWindowDepth() {
        PROJECTION_LOCK.lock();
        
        try {
            return _depth;
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Gets the window FOV. The viewing area of the window is shaped like a
     * <a href="https://en.wikipedia.org/wiki/Frustum">frustum</a>, with the camera
     * itself placed at its pinnacle. The FOV, or Field-Of-View, is defined to be the
     * angle between the faces of the viewing frustum corresponding to the top and bottom
     * edges of the viewport.
     * @return the FOV of the window
     */
    public static float getFOV() {
        PROJECTION_LOCK.lock();
        
        try {
            return _fov;
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Gets the distance of the near clipping plane. The near clipping plane is
     * parallel to the camera viewing plane, and only fragments 'behind' it (from the
     * viewpoint of the camera) are rendered; any other fragments are discarded
     * (clipped). The distance is measured from the camera to this plane.
     * 
     * @return the distance of the near clipping plane
     */
    public static float getZNearDist() {
        PROJECTION_LOCK.lock();
        
        try {
            return _zNearDist;
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Gets the distance of the far clipping plane. The far clipping plane is parallel
     * to the camera viewing plane, and only fragments 'in front' of it (from the
     * viewpoint of the camera) are rendered; any other fragments are discarded 
     * (clipped). The distance is measured from the camera to this plane.
     * 
     * @return the distance of the far clipping plane
     */
    public static float getZFarDist() {
        PROJECTION_LOCK.lock();
        
        try {
            return _zFarDist;
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Gets whether the window is fullscreen or not.
     * @return {@code true} if the window is in fullscreen mode, {@code false}
     * otherwise
     */
    public static boolean getIsFullscreen() {
        PROJECTION_LOCK.lock();
        
        try {
            return getStatus(STATUS_FULLSCREEN);
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Sets the width of the window. This value is clamped such that it is always
     * greater than or equal to {@link CONSTANT_MIN_WINDOW_WIDTH}.
     * @param width the new window width
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void setWindowWidth(int width) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        setWindowWidth(width, true);
    }
    
    static void setWindowWidth(int width, boolean updateWindow) {
        PROJECTION_LOCK.lock();
        
        try {
            if (width < CONSTANT_MIN_WINDOW_WIDTH)
                width = CONSTANT_MIN_WINDOW_WIDTH;

            _width = width;
            setStatus(STATUS_PROJECTION_DIRTY, true);
            setStatus(STATUS_UPDATE_WINDOW_SIZE, updateWindow);
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Sets the height of the window. This value is clamped such that it is always
     * greater than or equal to {@link CONSTANT_MIN_WINDOW_HEIGHT}.
     * @param height the new window height
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void setWindowHeight(int height) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        setWindowHeight(height, true);
    }
    
    static void setWindowHeight(int height, boolean updateWindow) {
        PROJECTION_LOCK.lock();
        
        try {
            if (height < CONSTANT_MIN_WINDOW_HEIGHT)
                height = CONSTANT_MIN_WINDOW_HEIGHT;

            _height = height;
            setStatus(STATUS_PROJECTION_DIRTY, true);
            setStatus(STATUS_UPDATE_WINDOW_SIZE, updateWindow);
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Sets the depth of the window. This value is clamped such that it is always
     * greater than or equal to 1.
     * @param depth the new window depth
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void setWindowDepth(int depth) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        PROJECTION_LOCK.lock();
        
        try {
            if (depth < 1)
                depth = 1;

            _depth = depth;
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Sets the FOV of the window. This value is clamped such that it is in the range
     * [{@link CONSTANT_MIN_FOV}, {@link CONSTANT_MAX_FOV}], inclusive.
     * @param fov the new window FOV
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void setFOV(float fov) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        PROJECTION_LOCK.lock();
        
        try {
            _fov = Maths.clamp(CONSTANT_MIN_FOV, fov, CONSTANT_MAX_FOV);
            setStatus(STATUS_PROJECTION_DIRTY, true);
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Sets the distances of the clipping planes of the window. Initially, each distance
     * is clamped such that:
     * <blockquote><pre>
     *  {@link CONSTANT_MIN_ZNEAR_DIST} {@code <= zNearDist <= }{@link CONSTANT_MAX_ZFAR_DIST} {@code - }{@link CONSTANT_MIN_Z_DIST_SEPARATION}
     *  {@link CONSTANT_MIN_ZNEAR_DIST} {@code + }{@link CONSTANT_MIN_Z_DIST_SEPARATION} {@code <= zFarDist <= }{@link CONSTANT_MAX_ZFAR_DIST}</pre></blockquote>
     * Then {@code zNearDist} is further clamped such that it is always less than or
     * equal to {@code zFarDist - }{@link CONSTANT_MIN_Z_DIST_SEPARATION}.
     * 
     * @param zNearDist the new near clipping plane distance
     * @param zFarDist the new far clipping plane distance
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void setZDists(float zNearDist, float zFarDist) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        PROJECTION_LOCK.lock();
        
        try {
            zNearDist = Maths.clamp(CONSTANT_MIN_ZNEAR_DIST, zNearDist, CONSTANT_MAX_ZFAR_DIST - CONSTANT_MIN_Z_DIST_SEPARATION);
            zFarDist = Maths.clamp(CONSTANT_MIN_ZNEAR_DIST + CONSTANT_MIN_Z_DIST_SEPARATION, zFarDist, CONSTANT_MAX_ZFAR_DIST);

            if (zNearDist > zFarDist - CONSTANT_MIN_Z_DIST_SEPARATION)
                zNearDist = zFarDist - CONSTANT_MIN_Z_DIST_SEPARATION;

            _zNearDist = zNearDist;
            _zFarDist = zFarDist;
            setStatus(STATUS_PROJECTION_DIRTY, true);
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Sets the distance of the near clipping plane. This value is clamped such that it
     * is in the range
     * [{@link CONSTANT_MIN_ZNEAR_DIST}, {@code zFarDist - }{@link CONSTANT_MIN_Z_DIST_SEPARATION}],
     * inclusive.
     * 
     * @param zNearDist the new near clipping plane distance
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void setZNearDist(float zNearDist) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        PROJECTION_LOCK.lock();
        
        try {
            _zNearDist = Maths.clamp(CONSTANT_MIN_ZNEAR_DIST, zNearDist, _zFarDist - CONSTANT_MIN_Z_DIST_SEPARATION);
            setStatus(STATUS_PROJECTION_DIRTY, true);
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Sets the distance of the far clipping plane. This value is clamped such that it
     * is in the range
     * [{@code zNearDist + }{@link CONSTANT_MIN_Z_DIST_SEPARATION}, {@link CONSTANT_MAX_ZFAR_DIST}],
     * inclusive.
     * 
     * @param zFarDist the new far clipping plane distance
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void setZFarDist(float zFarDist) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        PROJECTION_LOCK.lock();
        
        try {
            _zFarDist = Maths.clamp(_zNearDist + CONSTANT_MIN_Z_DIST_SEPARATION, zFarDist, CONSTANT_MAX_ZFAR_DIST);
            setStatus(STATUS_PROJECTION_DIRTY, true);
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Sets whether the window is fullscreen.
     * @param isFullscreen {@code true} to set these options to fullscreen mode,
     * {@code false} otherwise
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void setIsFullscreen(boolean isFullscreen) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        PROJECTION_LOCK.lock();
        
        try {
            setStatus(STATUS_FULLSCREEN, isFullscreen);
            setStatus(STATUS_PROJECTION_DIRTY, true);
            setStatus(STATUS_FULLSCREEN_DIRTY, true);
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Toggles whether the window is fullscreen.
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void toggleIsFullscreen() {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        PROJECTION_LOCK.lock();
        
        try {
            toggleStatus(STATUS_FULLSCREEN);
            setStatus(STATUS_PROJECTION_DIRTY, true);
            setStatus(STATUS_FULLSCREEN_DIRTY, true);
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    static boolean isProjectionDirty() {
        PROJECTION_LOCK.lock();
        
        try {
            return getStatus(STATUS_PROJECTION_DIRTY);
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Flushes any changes to the projection matrix and to the window.<p>
     * 
     * If the projection matrix is flagged as dirty, then the internally held projection
     * matrix will be reloaded using the current window parameters; this will also flush
     * any changes to the window width/height to GLFW so that the dimensions and viewport
     * of the actual window match the current parameters.
     */
    static void flushProjectionMatrix() {
        PROJECTION_LOCK.lock();
        
        try {
            if (getStatus(STATUS_PROJECTION_DIRTY)) {
                setStatus(STATUS_PROJECTION_DIRTY, false);

                int width = _width, height = _height;

                if (getStatus(STATUS_FULLSCREEN)) {
                    int[] monitorWidth = new int[1], monitorHeight = new int[1];
                    GraphicsEngine.getMonitorSize(GraphicsEngine.getCurrentMonitor(getStatus(STATUS_FULLSCREEN_DIRTY)), monitorWidth, monitorHeight);

                    width = monitorWidth[0];
                    height = monitorHeight[0];
                }

                double tanfov = Math.tan(Math.PI * _fov / 360d);
                double frustrumlength = _zFarDist - _zNearDist;

                PROJ_MATRIX.setElement(0, 0, height / (tanfov * width));
                PROJ_MATRIX.setElement(1, 1, 1 / tanfov);
                PROJ_MATRIX.setElement(2, 2, -(_zFarDist + _zNearDist) / frustrumlength);
                PROJ_MATRIX.setElement(2, 3, -(2 * _zFarDist * _zNearDist) / frustrumlength);
                PROJ_MATRIX.setElement(3, 2, -1);

                if (getStatus(STATUS_FULLSCREEN_DIRTY)) {
                    if (getStatus(STATUS_FULLSCREEN))
                        doEnableFullscreen();
                    else
                        doDisableFullscreen();

                    setStatus(STATUS_FULLSCREEN_DIRTY, false);
                }

                GL11.glViewport(0, 0, width, height);
                if (getStatus(STATUS_UPDATE_WINDOW_SIZE))
                    setWindowSize(width, height);

                setStatus(STATUS_UPDATE_WINDOW_SIZE, true);
            }
            
        } finally {
            PROJECTION_LOCK.unlock();
        }
    }
    
    /**
     * Sets the position of the window on the screen. This is defined to be the position,
     * in screen coordinates, of the upper-left corner of the content area of the window.<p>
     * 
     * Note that this function will remember the given position, but will not directly
     * affect the window if the window is in fullscreen mode. The graphics engine will
     * instead restore the window to this position when it switches to windowed mode.
     * 
     * @param x the new X-coordinate of the window, or {@link VALUE_DONT_CARE} to keep
     * the previous X-coordinate
     * @param y the new Y-coordinate of the window, or {@link VALUE_DONT_CARE} to keep
     * the previous Y-coordinate
     */
    public static void setWindowPos(int x, int y) {
        glfwSetWindowPos(window, x, y);
    }
    
    /**
     * Sets the cursor being used for the window. This destroys the previous cursor.
     * 
     * @param shape one of {@link CURSOR_ARROW}, {@link CURSOR_IBEAM}, 
     * {@link CURSOR_CROSSHAIR}, {@link CURSOR_HAND}, {@link CURSOR_HRESIZE}, or 
     * {@link CURSOR_VRESIZE}
     * @throws IllegalArgumentException if the given parameter is not one of the accepted
     * values
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static void setCursor(int shape) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
            
        if (shape < CURSOR_ARROW || shape > CURSOR_VRESIZE)
            throw new IllegalArgumentException(LocaleUtils.format("GraphicsEngine.InvalidCursorParam", shape));
        
        glfwDestroyCursor(cursor);
        cursor = glfwCreateStandardCursor(shape);
        glfwSetCursor(window, cursor);
    }
    
    /**
     * Gets the maximum texture unit value. This is the total number of texture units an
     * active shader program can have, summed over all of the attached shader stages.<p>
     * 
     * Messages that handle texture units can take any value between 0 (inclusive) and
     * this method's return value (exclusive). Texture unit arguments outside of this
     * range will cause the message to fail.
     * 
     * @return the maximum allowed texture unit
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static int getMaxCombinedTextureUnit() {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        return GraphicsManager.MAX_COMBINED_TEXTURE_UNITS;
    }
    
    private static CompletableFuture<Integer> enqueueMessage(Message message) {
        if (!getStatus(STATUS_INITIALIZED))
            throw new IllegalStateException(LocaleUtils.format("GraphicsEngine.NotInitialized"));
        
        if (hasCurrentContext()) 
        {
            GraphicsManager.handleMessage(message);
        }
        else
        {
            try {
                GraphicsManager.MESSAGE_QUEUE.put(message);
            } catch (InterruptedException e) {
                LOG.log(Level.WARNING, "GraphicsEngine.enqueueMessage.EnqueueFailed", e);
                message.future.cancel(false);
            }
        }
        
        return message.future;
    }
    
    /**
     * Enables v-sync. By default, v-sync is disabled.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgEnableVSync() {
        return enqueueMessage(new Message(Message.Type.ENABLE_VSYNC));
    }
    
    /**
     * Disables v-sync. By default, v-sync is disabled.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgDisableVSync() {
        return enqueueMessage(new Message(Message.Type.DISABLE_VSYNC));
    }
    
    /**
     * Enables debug mode. By default, debug mode is disabled.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgEnableDebugMode() {
        return enqueueMessage(new Message(Message.Type.ENABLE_DEBUGGING));
    }
    
    /**
     * Disables debug mode. By default, debug mode is disabled.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgDisableDebugMode() {
        return enqueueMessage(new Message(Message.Type.DISABLE_DEBUGGING));
    }
    
    /**
     * Sets the affine transformation of the viewport to an unmodifiable view of the given
     * transform. Changes to the original affine transformation are visible within the
     * graphics engine.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param transform the new transform of the viewport
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>{@code transform} is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgViewportSetTransform(AffineTransformation transform) {
        return enqueueMessage(new Message(Message.Type.VIEWPORT_SET_TRANSFORM, transform));
    }
    
    /**
     * Enables rendering of the skybox.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgSkyboxEnable() {
        return enqueueMessage(new Message(Message.Type.SKYBOX_ENABLE));
    }
    
    /**
     * Disables rendering of the skybox.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgSkyboxDisable() {
        return enqueueMessage(new Message(Message.Type.SKYBOX_DISABLE));
    }
    
    /**
     * Sets the texture of the skybox to the currently selected texture. The texture is
     * expected to be a diffuse texture, and a cubemap.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected texture is {@code null}</li>
     *  <li>the currently selected texture has a unit other than 0</li>
     *  <li>the currently selected texture is not a cubemap</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgSkyboxSetTexture() {
        return enqueueMessage(new Message(Message.Type.SKYBOX_SET_TEXTURE));
    }
    
    /**
     * Instantiates and registers a new mesh object. This mesh will contain the positions,
     * uv coordinates, normal vectors, and triangle indices for a renderable. The new mesh
     * is also automatically selected, allowing for further processing.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param positions the array of positions vectors for the vertices of the mesh
     * @param uvs the array of uv coordinate vectors for the vertices of the mesh. Must be
     * of the same length as {@code positions}
     * @param normals the array of normal vectors for the vertices of the mesh. If this is
     * {@code null}, then the normals of the mesh will be automatically generated from the
     * other parameters; otherwise, this must be of the same length as {@code positions}
     * @param triangles the triangle indices of the mesh. Each triplet of indices defines a
     * triangle using the indexed vertices, in counter-clockwise order. Must be of length
     * evenly divisible by 3, and each entry must be greater than or equal to 0 and less than 
     * {@code positions.length}
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the new mesh upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>{@code positions}, {@code uvs} or {@code triangles} is {@code null}</li>
     *  <li>the set of parameters passed to the new mesh is invalid for any reason</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshNew(Vector3[] positions, Vector3[] uvs, @Nullable Vector3[] normals, int[] triangles) {
        return enqueueMessage(new Message(Message.Type.MESH_NEW, positions, uvs, normals, triangles));
    }
    
    /**
     * Selects a mesh. This loads the indexed mesh into the state machine of the
     * {@code GraphicsManager} class to allow for further processing; an invalid index will
     * instead set the selected mesh to {@code null}. This message does not, on it's
     * own, affect the state of the {@code GraphicsManager} class in any other way.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param index the index of the mesh to select
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshSelect(int index) {
        return enqueueMessage(new Message(Message.Type.MESH_SELECT, index));
    }
    
    /**
     * Selects the default mesh representing a quad. This is a mesh with 4 vertices
     * bounding and defining a unit square, with the appropriate triangle indices and
     * normal vectors. The uv coordinates are standard 2D coordinates. The selected
     * mesh is read-only, and it's origin point lies in the centre of the square.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the quad mesh on success, or is cancelled if the thread experienced an
     * {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshSelectDefaultQuad() {
        return enqueueMessage(new Message(Message.Type.MESH_SELECT_QUAD));
    }
    
    /**
     * Selects the default mesh representing a cube. This is a mesh with 8 vertices
     * bounding and defining a unit cube, with the appropriate triangle indices and
     * normal vectors. The uv coordinates are 3D cubemap coordinates. The selected mesh
     * is read-only, and it's origin point lies in the centre of the cube.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the cube mesh on success, or is cancelled if the thread experienced an
     * {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshSelectDefaultCube() {
        return enqueueMessage(new Message(Message.Type.MESH_SELECT_CUBE));
    }
    
    /**
     * Selects the default mesh representing an inverse cube. This is defined to be an
     * identical mesh to that returned by {@link #msgMeshSelectDefaultCube()}, including
     * the read-only and origin point properties, except with the triangle orientations
     * reversed (so previously forward-facing triangles are now backward-facing, and
     * vice-versa). The normals are also reversed because of this.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the inverse cube mesh on success, or is cancelled if the thread experienced an
     * {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshSelectDefaultInvCube() {
        return enqueueMessage(new Message(Message.Type.MESH_SELECT_INV_CUBE));
    }
    
    /**
     * Flags the currently selected mesh for rebuffering.<p>
     * 
     * Meshes create and handle their own data buffers on the GPU. Whenever an attribute
     * is changed by these messages, they will automatically update their data buffer with
     * this new data. However, they can also be flagged via this message so that when they
     * next update, they instead delete their data buffer and create a new one rather than
     * update the existing buffer.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected mesh is {@code null}</li>
     *  <li>the currently selected mesh is read-only</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshRebuffer() {
        return enqueueMessage(new Message(Message.Type.MESH_REBUFFER));
    }
    
    /**
     * Closes the currently selected mesh. This removes it from the internal registry,
     * and sets the selected mesh to {@code null}. Note that it is not possible for an
     * external thread to close one of the default meshes using this message; attempting
     * to do so will result in the message failing.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected mesh is {@code null}</li>
     *  <li>the currently selected mesh is one of the default meshes</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshClose() {
        return enqueueMessage(new Message(Message.Type.MESH_CLOSE));
    }
    
    /**
     * Overwrites the attributes of the currently selected mesh. A parameter that is set to
     * {@code null} will cause the associated mesh attribute to remain unchanged. If the
     * passed attributes are invalid for any reason, then the mesh will be reverted to its
     * previous valid attributes, but the message itself will not fail.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param positions the new array of position vectors for the mesh vertices, or
     * {@code null} to leave the mesh positions unchanged
     * @param uvs the new array of uv coordinate vectors for the mesh vertices, or
     * {@code null} to leave the mesh uv coordinates unchanged. Must have the same length as
     * {@code positions} to be valid
     * @param normals the new array of normal vector for the mesh vertices, or {@code null}
     * to leave the mesh normals unchanged. Must have the same length as {@code positions}
     * to be valid
     * @param triangles the new array of triangle indices for the mesh, or {@code null} to
     * leave the mesh triangles unchanged. Must have a length evenly divisible by 3, and each
     * entry must be greater than or equal to 0 and less than {@code positions.length} to be
     * valid
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected mesh is {@code null}</li>
     *  <li>the currently selected mesh is read-only</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshSetAttributes(@Nullable Vector3[] positions, @Nullable Vector3[] uvs, @Nullable Vector3[] normals, @Nullable int[] triangles) {
        return enqueueMessage(new Message(Message.Type.MESH_SET_ATTRIBUTES, positions, uvs, normals, triangles));
    }
    
    /**
     * Recalculates the vertex normals of the currently selected mesh. This is performed by
     * calculating the (non-normalized) face normals of each triangle, and then for each
     * vertex in turn, summing the appropriate face normals and normalizing; not normalizing
     * the face normals prior to the summing operation provides a natural weighting to each
     * normal depending on the triangle sizes. Should the vertex normal become the zero
     * vector (or approximately close to it) at the end of this process, then it will be
     * replaced with {@code (1, 0, 0)}.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected mesh is {@code null}</li>
     *  <li>the currently selected mesh is read-only</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshRecalculateNormals() {
        return enqueueMessage(new Message(Message.Type.MESH_RECALC_NORMALS));
    }
    
    /**
     * Sets the currently selected mesh to be read-only. This prevents rebuffering and
     * any alterations to the mesh attributes.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected mesh is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshSetReadonly() {
        return enqueueMessage(new Message(Message.Type.MESH_SET_READONLY));
    }
    
    /**
     * Gets the unique integer id of the currently selected mesh.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the selected mesh upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected mesh is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgMeshGetID() {
        return enqueueMessage(new Message(Message.Type.MESH_GET_ID));
    }
    
    /**
     * Instantiates and registers a new vertex shader object. This is one of 3 potential
     * shader stages that a fully linked shader program can have, but it does not act as a
     * shader on it's own; it must be linked to a shader program to have any effect. The new
     * shader is also automatically selected, allowing for further processing.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param id the {@link Identity} to get the shader source code from. Note that the
     * folder path has {@code /shader} appended to it, and the source code file is expected
     * to have an extension of {@code .vert}
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the new shader upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>{@code id} is {@code null}</li>
     *  <li>the source file could not be located</li>
     *  <li>an {@link IOException} occurred during the shader instantiation</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderNewVertex(Identity id) {
        return enqueueMessage(new Message(Message.Type.SHADER_NEW_VERT, id));
    }
    
    /**
     * Instantiates and registers a new fragment shader object. This is one of 3 potential
     * shader stages that a fully linked shader program can have, but it does not act as a
     * shader on it's own; it must be linked to a shader program to have any effect. The new
     * shader is also automatically selected, allowing for further processing.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param id the {@link Identity} to get the shader source code from. Note that the
     * folder path has {@code /shader} appended to it, and the source code file is expected
     * to have an extension of {@code .frag}
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the new shader upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>{@code id} is {@code null}</li>
     *  <li>the source file could not be located</li>
     *  <li>an {@link IOException} occurred during the shader instantiation</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderNewFragment(Identity id) {
        return enqueueMessage(new Message(Message.Type.SHADER_NEW_FRAG, id));
    }
    
    /**
     * Instantiates and registers a new geometry shader object. This is one of 3 potential
     * shader stages that a fully linked shader program can have, but it does not act as a
     * shader on it's own; it must be linked to a shader program to have any effect. The new
     * shader is also automatically selected, allowing for further processing.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param id the {@link Identity} to get the shader source code from. Note that the
     * folder path has {@code /shader} appended to it, and the source code file is expected
     * to have an extension of {@code .geom}
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the new shader upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>{@code id} is {@code null}</li>
     *  <li>the source file could not be located</li>
     *  <li>an {@link IOException} occurred during the shader instantiation</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderNewGeometry(Identity id) {
        return enqueueMessage(new Message(Message.Type.SHADER_NEW_GEOM, id));
    }
    
    /**
     * Selects a shader. This loads the indexed shader into the state machine of the
     * {@code GraphicsManager} class to allow for further processing; an invalid index will
     * instead set the selected shader to {@code null}. This message does not, on it's
     * own, affect the state of the {@code GraphicsManager} class in any other way.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param index the index of the shader to select
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderSelect(int index) {
        return enqueueMessage(new Message(Message.Type.SHADER_SELECT, index));
    }
    
    /**
     * Selects the default 2D vertex shader. This is the vertex shader that the default
     * 2D texture shader program uses.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default vertex shader on success, or is cancelled if the thread experienced an
     * {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderSelectDefaultVert2D() {
        return enqueueMessage(new Message(Message.Type.SHADER_SELECT_DEFAULT_VERT_2D));
    }
    
    /**
     * Selects the default 2D fragment shader. This is the fragment shader that the default
     * 2D texture shader program uses.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default fragment shader on success, or is cancelled if the thread experienced
     * an {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderSelectDefaultFrag2D() {
        return enqueueMessage(new Message(Message.Type.SHADER_SELECT_DEFAULT_FRAG_2D));
    }
    
    /**
     * Selects the default cubemap vertex shader. This is the vertex shader that the default
     * cubemap texture shader program uses.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default vertex shader on success, or is cancelled if the thread experienced an
     * {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderSelectDefaultVertCube() {
        return enqueueMessage(new Message(Message.Type.SHADER_SELECT_DEFAULT_VERT_CUBE));
    }
    
    /**
     * Selects the default cubemap fragment shader. This is the fragment shader that the
     * default cubemap texture shader program uses.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default fragment shader on success, or is cancelled if the thread experienced
     * an {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderSelectDefaultFragCube() {
        return enqueueMessage(new Message(Message.Type.SHADER_SELECT_DEFAULT_FRAG_CUBE));
    }
    
    /**
     * Selects the default UI vertex shader. This is the vertex shader that the default UI
     * shader program uses.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default vertex shader on success, or is cancelled if the thread experienced an
     * {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderSelectDefaultVertUI() {
        return enqueueMessage(new Message(Message.Type.SHADER_SELECT_DEFAULT_VERT_UI));
    }
    
    /**
     * Selects the default UI fragment shader. This is the fragment shader that the default
     * UI shader program uses.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default fragment shader on success, or is cancelled if the thread experienced
     * an {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderSelectDefaultFragUI() {
        return enqueueMessage(new Message(Message.Type.SHADER_SELECT_DEFAULT_FRAG_UI));
    }
    
    /**
     * Closes the currently selected shader. This removes it from the internal registry,
     * and sets the selected shader to {@code null}. Note that it is not possible for an
     * external thread to close one of the default shaders using this message; attempting
     * to do so will result in the message failing.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected shader is {@code null}</li>
     *  <li>the currently selected shader is one of the default shaders</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderClose() {
        return enqueueMessage(new Message(Message.Type.SHADER_CLOSE));
    }
    
    /**
     * Gets the unique integer id of the currently selected shader.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the selected shader upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected shader is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderGetID() {
        return enqueueMessage(new Message(Message.Type.SHADER_GET_ID));
    }
    
    /**
     * Instantiates and registers a new shader program. This is an empty program into
     * which a vertex shader, a fragment shader, and an optional geometry shader can be
     * attached; once these are attached, the new shader program is ready for rendering. The
     * program also takes a single parameter indicating the type of texture the program is
     * intended to process; this doesn't constrain the stages that can be attached in any
     * way, but a mismatch between the stages and the parameter may cause errors upon a
     * rendering attempt. This parameter is known as the program's <i>mode</i>. The new
     * shader program is automatically selected, allowing for further processing.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param mode {@code true} if the new shader program is intended to take cubemap
     * textures, or {@code false} for 2D textures
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the new shader upon success, or is cancelled if the thread experienced an
     * {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramNew(boolean mode) {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_NEW, mode));
    }
    
    /**
     * Selects a shader program. This loads the indexed shader program into the state
     * machine of the {@code GraphicsManager} class to allow for further processing; an
     * invalid index will instead set the selected shader program to {@code null}. This
     * message does not, on it's own, affect the state of the {@code GraphicsManager}
     * class in any other way.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param index the index of the shader program to select
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramSelect(int index) {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_SELECT, index));
    }
    
    /**
     * Selects the default 2D shader program. This is the read-only shader that renderables
     * using 2D textures default to if their assigned shader fails or is closed for any
     * reason.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default shader program on success, or is cancelled if the thread experienced
     * an {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramSelectDefault2D() {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_SELECT_DEFAULT_2D));
    }
    
    /**
     * Selects the default cubemap shader program. This is the read-only shader that
     * renderables using cubemap textures default to if their assigned shader fails or is
     * closed for any reason.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default shader program on success, or is cancelled if the thread experienced
     * an {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramSelectDefaultCube() {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_SELECT_DEFAULT_CUBE));
    }
    
    /**
     * Selects the default UI shader program. This is the read-only shader that renderables
     * assigned to the UI default to. Note that this program has it's mode set to
     * {@code false} (see {@link msgShaderProgramNew(boolean)} for more information on the
     * mode of a program).<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default shader program on success, or is cancelled if the thread experienced
     * an {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramSelectDefaultUI() {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_SELECT_DEFAULT_UI));
    }
    
    /**
     * Closes the currently selected shader program. This removes it from the internal
     * registry, and sets the selected shader program to {@code null}. Any renderables
     * currently assigned to it will automatically be reassigned to the default shader
     * program when they are next rendered. Note that it is not possible for an external
     * thread to close a default shader program using this message; attempting to do
     * so will result in the message failing.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>the currently selected shader program is {@code null}</li>
     *  <li>the currently selected shader program is one of the default shader programs</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramClose() {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_CLOSE));
    }
    
    /**
     * Sets the currently selected shader program to be read-only. This prevents any
     * additional shaders from being attached to the program; care should be taken to
     * ensure sure the program links and validates correctly before invoking this message,
     * as it may result in the shader program becoming unusable for the rest of it's
     * lifetime.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on 
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>the currently selected shader program is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramSetReadonly() {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_SET_READONLY));
    }
    
    /**
     * Attaches the currently selected shader to the currently selected shader program.
     * This sets the shader to be one of the stages of the program; which stage depends
     * on the type of the shader. Only 1 shader of each type 
     * ({@code vert}, {@code geom}, {@code frag}) can be attached to the shader
     * program at any one time; attaching a second shader of the same type will remove
     * the first shader. Shader programs must have at least a {@code vert} and a
     * {@code frag} shader attached before they can be linked, validated, and used for
     * rendering.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>the currently selected shader program is {@code null}</li>
     *  <li>the currently selected shader program is read-only</li>
     *  <li>the currently selected shader is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramAttachShader() {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_ATTACH));
    }
    
    /**
     * Gets the unique integer id of the currently selected shader program.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the selected shader program upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected shader program is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramGetID() {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_GET_ID));
    }
    
    /**
     * Instantiates and registers a new 2D texture. This is a graphical image loaded onto
     * the GPU for use in rendering geometry surfaces. Each texture is assigned an integer
     * 'unit' value, which shaders can use to differentiate between multiple textures
     * assigned to a renderable; the effects of specific texture units is shader-dependent.
     * The only guideline for texture units is that the graphics engine was built with the
     * assumption that unit 0 is reserved for diffuse textures, i.e. textures that provide
     * the base color image - this is neither required nor enforced, however. The new
     * texture is automatically selected, allowing for further processing.<p>
     * 
     * This message takes an {@link Identity} value, from which the image is sourced.
     * Therefore, this message is preferred over the 
     * {@link #msgTexture2DNew(BufferedImage, int) other message} as the {@code Identity}
     * value can and is used for identifying the texture within the internal registry,
     * preventing possible duplicates of the same texture. Note that duplicates of the same
     * texture image can still exist, as long as they are assigned different texture units.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param id the {@link Identity} to get the texture image data from. Note that the
     * folder path has {@code /texture} appended to it, and the source file is expected
     * to have an extension of {@code .png}
     * @param unit the texture unit this texture is assigned to. Must be greater than or
     * equal to 0 and less than {@link GraphicsManager#MAX_COMBINED_TEXTURE_UNITS}
     * @return a {@link CompletableFuture} object that completes with the unique id value of
     * the new texture upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>{@code id} is {@code null}</li>
     *  <li>the given unit is out of range</li>
     *  <li>the source file could not be located</li>
     *  <li>an {@link IOException} occurred during the texture instantiation</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTexture2DNew(Identity id, int unit) {
        return enqueueMessage(new Message(Message.Type.TEXTURE_ID_NEW_2D, id, unit));
    }
    
    /**
     * Instantiates and registers a new 2D texture. This is a graphical image loaded onto
     * the GPU for use in rendering geometry surfaces. Each texture is assigned an integer
     * 'unit' value, which shaders can use to differentiate between multiple textures
     * assigned to a renderable; the effects of specific texture units is shader-dependent.
     * The only guideline for texture units is that the graphics engine was built with the
     * assumption that unit 0 is reserved for diffuse textures, i.e. textures that provide
     * the base color image - this is neither required nor enforced, however. The new
     * texture is automatically selected, allowing for further processing.<p>
     * 
     * This message takes a {@link BufferedImage} value, from which the image is sourced.
     * Therefore, the {@link #msgTexture2DNew(BufferedImage, int) other message} is
     * preferred over this message, as the lack of an {@code Identity} means the texture
     * cannot be equated with other textures in the internal registry. This causes a
     * potential for duplicate textures being instantiated and used instead of a single,
     * shared texture. The {@code BufferedImage} is not stored in the graphics manager,
     * thus any references to it can be discarded to free up memory space (if it is not
     * needed anymore).<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param im the {@link BufferedImage} to get the texture image data from
     * @param unit the texture unit this texture is assigned to. Must be greater than or
     * equal to 0 and less than {@link GraphicsManager#MAX_COMBINED_TEXTURE_UNITS}
     * @return a {@link CompletableFuture} object that completes with the unique id value of
     * the new texture upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>{@code im} is {@code null}</li>
     *  <li>the given unit is out of range</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTexture2DNew(BufferedImage im, int unit) {
        return enqueueMessage(new Message(Message.Type.TEXTURE_IM_NEW_2D, im, unit));
    }
    
    /**
     * Instantiates and registers a new cubemap texture. This is a graphical image loaded
     * onto the GPU for use in rendering geometry surfaces. Each texture is assigned an
     * integer 'unit' value, which shaders can use to differentiate between multiple textures
     * assigned to a renderable; the effects of specific texture units is shader-dependent.
     * The only guideline for texture units is that the graphics engine was built with the
     * assumption that unit 0 is reserved for diffuse textures, i.e. textures that provide
     * the base color image - this is neither required nor enforced, however. The new
     * texture is automatically selected, allowing for further processing.<p>
     * 
     * This message takes an {@link Identity} value, from which the image is sourced.
     * Therefore, this message is preferred over the 
     * {@link #msgTextureCubemapNew(BufferedImage, int) other message} as the {@code Identity}
     * value can and is used for identifying the texture within the internal registry,
     * preventing possible duplicates of the same texture. Note that duplicates of the same
     * texture cubemap can still exist, as long as they are assigned different texture units.<p>
     * 
     * The cubemap itself sources each face texture from the image in one of several ways,
     * depending on the dimensions of the image. These various ways of laying out the faces of
     * the cubemap are explained in more detail in the javadocs of the cubemap
     * {@linkplain GLTextureCubemap#GLTextureCubemap(Identity, int) constructor}.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param id the {@link Identity} to get the texture image data from. Note that the
     * folder path has {@code /texture} appended to it, and the source file is expected
     * to have an extension of {@code .png}
     * @param unit the texture unit this texture is assigned to. Must be greater than or
     * equal to 0 and less than {@link GraphicsManager#MAX_COMBINED_TEXTURE_UNITS}
     * @return a {@link CompletableFuture} object that completes with the unique id value of
     * the new texture upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>{@code id} is {@code null}</li>
     *  <li>the given unit is out of range</li>
     *  <li>the sourced image is of an invalid dimensionality as detailed in the cubemap
     * {@linkplain GLTextureCubemapGLTextureCubemap(Identity, int) constructor}</li>
     *  <li>an {@link IOException} occurred during the texture instantiation</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTextureCubemapNew(Identity id, int unit) {
        return enqueueMessage(new Message(Message.Type.TEXTURE_ID_NEW_CUBE, id, unit));
    }
    
    /**
     * Instantiates and registers a new cubemap texture. This is a graphical image loaded
     * onto the GPU for use in rendering geometry surfaces. Each texture is assigned an
     * integer 'unit' value, which shaders can use to differentiate between multiple textures
     * assigned to a renderable; the effects of specific texture units is shader-dependent.
     * The only guideline for texture units is that the graphics engine was built with the
     * assumption that unit 0 is reserved for diffuse textures, i.e. textures that provide
     * the base color image - this is neither required nor enforced, however. The new
     * texture is automatically selected, allowing for further processing.<p>
     * 
     * This message takes a {@link BufferedImage} value, from which the image is sourced.
     * Therefore, the {@link #msgTexture2DNew(BufferedImage, int) other message} is
     * preferred over this message, as the lack of an {@code Identity} means the texture
     * cannot be equated with other textures in the internal registry. This causes a
     * potential for duplicate textures being instantiated and used instead of a single,
     * shared texture. The {@code BufferedImage} is not stored in the graphics manager,
     * thus any references to it can be discarded to free up memory space (if it is not
     * needed anymore).<p>
     * 
     * The cubemap itself sources each face texture from the image in one of several ways,
     * depending on the dimensions of the image. These various ways of laying out the faces of
     * the cubemap are explained in more detail in the javadocs of the cubemap
     * {@linkplain GLTextureCubemap#GLTextureCubemap(BufferedImage, int) constructor}.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param im the {@link BufferedImage} to get the texture image data from
     * @param unit the texture unit this texture is assigned to. Must be greater than or
     * equal to 0 and less than {@link GraphicsManager#MAX_COMBINED_TEXTURE_UNITS}
     * @return a {@link CompletableFuture} object that completes with the unique id value of
     * the new texture upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>{@code im} is {@code null}</li>
     *  <li>the given unit is out of range</li>
     *  <li>the given image is of an invalid dimensionality as detailed in the cubemap
     * {@linkplain GLTextureCubemapGLTextureCubemap(BufferedImage, int) constructor}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTextureCubemapNew(BufferedImage im, int unit) {
        return enqueueMessage(new Message(Message.Type.TEXTURE_IM_NEW_CUBE, im, unit));
    }
    
    /**
     * Instantiates and registers a new cubemap texture. This is a graphical image loaded
     * onto the GPU for use in rendering geometry surfaces. Each texture is assigned an
     * integer 'unit' value, which shaders can use to differentiate between multiple textures
     * assigned to a renderable; the effects of specific texture units is shader-dependent.
     * The only guideline for texture units is that the graphics engine was built with the
     * assumption that unit 0 is reserved for diffuse textures, i.e. textures that provide
     * the base color image - this is neither required nor enforced, however. The new
     * texture is automatically selected, allowing for further processing.<p>
     * 
     * This message takes several {@link Identity} values, from which each face image is
     * sourced. Therefore, this message is preferred over the 
     * {@link #msgTextureCubemapNew(BufferedImage, BufferedImage, BufferedImage, BufferedImage, BufferedImage, BufferedImage, int) other message}
     * as the {@code Identity} values can and are used for identifying the texture within the
     * internal registry, preventing possible duplicates of the same texture. Note that
     * duplicates of the same texture cubemap can still exist, as long as they are assigned
     * different texture units.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param pxID the {@link Identity} to get the texture image data for the positive-X face
     * of the cubemap from. Note that the folder path has {@code /texture} appended to it, and 
     * the source file is expected to have an extension of {@code .png}
     * @param nxID the {@link Identity} to get the texture image data for the negative-X face
     * of the cubemap from. Note that the folder path has {@code /texture} appended to it, and 
     * the source file is expected to have an extension of {@code .png}
     * @param pyID the {@link Identity} to get the texture image data for the positive-Y face
     * of the cubemap from. Note that the folder path has {@code /texture} appended to it, and 
     * the source file is expected to have an extension of {@code .png}
     * @param nyID the {@link Identity} to get the texture image data for the negative-Y face
     * of the cubemap from. Note that the folder path has {@code /texture} appended to it, and 
     * the source file is expected to have an extension of {@code .png}
     * @param pzID the {@link Identity} to get the texture image data for the positive-Z face
     * of the cubemap from. Note that the folder path has {@code /texture} appended to it, and 
     * the source file is expected to have an extension of {@code .png}
     * @param nzID the {@link Identity} to get the texture image data for the negative-Z face
     * of the cubemap from. Note that the folder path has {@code /texture} appended to it, and 
     * the source file is expected to have an extension of {@code .png}
     * @param unit the texture unit this texture is assigned to. Must be greater than or
     * equal to 0 and less than {@link GraphicsManager#MAX_COMBINED_TEXTURE_UNITS}
     * @return a {@link CompletableFuture} object that completes with the unique id value of
     * the new texture upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>any of the given {@code Identity} parameters are {@code null}</li>
     *  <li>the given unit is out of range</li>
     *  <li>any of the sourced images are non-square</li>
     *  <li>an {@link IOException} occurred during the texture instantiation</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTextureCubemapNew(Identity pxID, Identity nxID, Identity pyID, Identity nyID, Identity pzID, Identity nzID, int unit) {
        return enqueueMessage(new Message(Message.Type.TEXTURE_ID6_NEW_CUBE, pxID, nxID, pyID, nyID, pzID, nzID, unit));
    }
    
    /**
     * Instantiates and registers a new cubemap texture. This is a graphical image loaded
     * onto the GPU for use in rendering geometry surfaces. Each texture is assigned an
     * integer 'unit' value, which shaders can use to differentiate between multiple textures
     * assigned to a renderable; the effects of specific texture units is shader-dependent.
     * The only guideline for texture units is that the graphics engine was built with the
     * assumption that unit 0 is reserved for diffuse textures, i.e. textures that provide
     * the base color image - this is neither required nor enforced, however. The new
     * texture is automatically selected, allowing for further processing.<p>
     * 
     * This message takes several {@link BufferedImage} values, from which each face image is
     * sourced. Therefore, the
     * {@link #msgTextureCubemapNew(Identity, Identity, Identity, Identity, Identity, Identity, int) other message}
     * is preferred over this message, as the lack of an {@code Identity} means the texture
     * cannot be equated with other textures in the internal registry. This causes a
     * potential for duplicate textures being instantiated and used instead of a single,
     * shared texture. The {@code BufferedImage} is not stored in the graphics manager,
     * thus any references to it can be discarded to free up memory space (if it is not
     * needed anymore).<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param pxIm the {@link BufferedImage} to get the image data for the positive-X face
     * of the cubemap from
     * @param nxIm the {@link BufferedImage} to get the image data for the negative-X face
     * of the cubemap from
     * @param pyIm the {@link BufferedImage} to get the image data for the positive-Y face
     * of the cubemap from
     * @param nyIm the {@link BufferedImage} to get the image data for the negative-Y face
     * of the cubemap from
     * @param pzIm the {@link BufferedImage} to get the image data for the positive-Z face
     * of the cubemap from
     * @param nzIm the {@link BufferedImage} to get the image data for the negative-Z face
     * of the cubemap from
     * @param unit the texture unit this texture is assigned to. Must be greater than or
     * equal to 0 and less than {@link GraphicsManager#MAX_COMBINED_TEXTURE_UNITS}
     * @return a {@link CompletableFuture} object that completes with the unique id value of
     * the new texture upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>any of the given {@code BufferedImage} parameters are {@code null}</li>
     *  <li>the given unit is out of range</li>
     *  <li>any of the given images are non-square</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTextureCubemapNew(BufferedImage pxIm, BufferedImage nxIm, BufferedImage pyIm, BufferedImage nyIm, BufferedImage pzIm, BufferedImage nzIm, int unit) {
        return enqueueMessage(new Message(Message.Type.TEXTURE_IM6_NEW_CUBE, pxIm, nxIm, pyIm, nyIm, pzIm, nzIm, unit));
    }
    
    /**
     * Selects a texture. This loads the indexed texture into the state machine of the
     * {@code GraphicsManager} class to allow for further processing; an invalid index will
     * instead set the selected texture to {@code null}. This message does not, on it's
     * own, affect the state of the {@code GraphicsManager} class in any other way.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param index the index of the texture to select
     * @return a {@link CompletableFuture} object that completes with a value of 0 on 
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTextureSelect(int index) {
        return enqueueMessage(new Message(Message.Type.TEXTURE_SELECT, index));
    }
    
    /**
     * Selects the default 2D texture. This is the texture that 2D renderables use by default
     * for texture unit 0.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default texture on success, or is cancelled if the thread experienced an
     * {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTextureSelectDefault2D() {
        return enqueueMessage(new Message(Message.Type.TEXTURE_SELECT_DEFAULT_2D));
    }
    
    /**
     * Selects the default cubemap texture. This is the texture that cubemap renderables use
     * by default for texture unit 0.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the default texture on success, or is cancelled if the thread experienced an
     * {@link InterruptedException} while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTextureSelectDefaultCubemap() {
        return enqueueMessage(new Message(Message.Type.TEXTURE_SELECT_DEFAULT_CUBE));
    }
    
    /**
     * Closes the currently selected texture. This removes it from the internal registry,
     * and sets the selected texture to {@code null}. Note that it is not possible for
     * an external thread to close a default texture using this message; attempting to 
     * do so will result in the message failing.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on 
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>the currently selected texture is {@code null}</li>
     *  <li>the currently selected texture is one of the default textures</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTextureClose() {
        return enqueueMessage(new Message(Message.Type.TEXTURE_CLOSE));
    }
    
    /**
     * Gets the unique integer id of the currently selected texture.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the selected texture upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected texture is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgTextureGetID() {
        return enqueueMessage(new Message(Message.Type.TEXTURE_GET_ID));
    }
    
    /**
     * Instantiates and registers a new skeleton from the given parameters. This is defined
     * to be a tree of {@link AffineTransformation} instances representing the 'bones' of
     * the skeleton. These bones dictate how each vertex in a mesh should be transformed
     * relative to the parent bone. Each vertex in the mesh is also assigned a series of
     * 'weights' via the given matrix, which are used to create a weighted map between
     * vertices and bones (e.g. a vertex on a joint may be under equal influence from both
     * bones of the joint, and thus would have each corresponding weight set to 0.5). The
     * weights are normalized so that each row sums to 1 prior to rendering. The new
     * skeleton is automatically selected, allowing for further processing.<p><p>
     * 
     * This method thus allows meshes and renderables to be used for animation purposes.
     * However, the exact method of combining the skeleton, weights and vertices together
     * is left up to the renderable's current shader to perform. For reference, the
     * graphics engine combines each skeleton transformation with it's parent <i>prior</i>
     * to uploading to the GPU, and the weights are uploaded in row-major order.<p>
     * 
     * The skeleton will be automatically removed from any renderable it is attached to
     * during a rendering cycle if the weight matrix is determined to be of invalid size for
     * the renderable's mesh (the weight matrix must have an equal number of rows as the
     * number of vertices in the mesh). In addition, the tree is set to be unmodifiable via
     * {@link Trees#unmodifiableTree(Tree)} and the matrix is copied, so the weights and
     * the structure of the skeleton cannot be altered (note however that the
     * {@code AffineTransformation} instances within the skeleton can still be altered by
     * outside threads).<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param skeleton the tree of affine transformation bones to use as the new skeleton
     * @param weights the vertex-bone weights of the new skeleton. Each row of the weights
     * corresponds to a vertex within the renderable's mesh, and each column of weights
     * corresponds to a bone within the skeleton, where the bones are ordered using a
     * {@linkplain Tree#preOrderWalk() pre-order walk}
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the new skeleton upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>{@code skeleton} is {@code null}</li>
     *  <li>{@code weights} is {@code null}</li>
     *  <li>{@code weights} is an empty matrix (i.e. it has either 0 rows or 0 columns)</li>
     *  <li>The number of columns in {@code weights} does not match the size of
     * {@code skeleton}</li>
     *  <li>Any row in {@code weights} sums to 0</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgSkeletonNew(Tree<? extends AffineTransformation, ?> skeleton, Matrix weights) {
        return enqueueMessage(new Message(Message.Type.SKELETON_NEW, skeleton, weights));
    }
    
    /**
     * Selects a skeleton. This loads the indexed skeleton into the state machine of the
     * {@code GraphicsManager} class to allow for further processing; an invalid index will
     * instead set the selected skeleton to {@code null}. This message does not, on it's
     * own, affect the state of the {@code GraphicsManager} class in any other way.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param index the index of the skeleton to select
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgSkeletonSelect(int index) {
        return enqueueMessage(new Message(Message.Type.SKELETON_SELECT, index));
    }
    
    /**
     * Closes the currently selected skeleton. This removes it from the internal registry,
     * and sets the selected skeleton to {@code null}.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected skeleton is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgSkeletonClose() {
        return enqueueMessage(new Message(Message.Type.SKELETON_CLOSE));
    }
    
    /**
     * Gets the unique integer id of the currently selected skeleton.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the selected skeleton upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected skeleton is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgSkeletonGetID() {
        return enqueueMessage(new Message(Message.Type.SKELETON_GET_ID));
    }
    
    /**
     * Instantiates and registers a new renderable. This is a container class that the
     * rendering loop uses to actually perform the rendering, and contains a mesh and
     * an array of textures. The new renderable can then be assigned to a shader program.
     * Note that the mesh is initially {@code null}, and one must be attached first before
     * any rendering will occur. The new renderable is automatically selected, allowing for
     * further processing.<p>
     * 
     * Renderables contain 1 or more <i>instances</i>. An instance acts as a way of rendering
     * the renderable multiple times in a single rendering call, with each instance having
     * their own affine transformation. This can greatly speed up graphical processing when
     * attempting to render large numbers of a simple mesh; for instance, blades of grass. See
     * <a href="https://learnopengl.com/Advanced-OpenGL/Instancing">the documentation on instancing</a>
     * for more information.<p>
     * 
     * Internally, the instances are represented as an affine transformation array, with a
     * single affine transformation per instance; thus, this array defines both the number
     * and transforms of all instances. This message instantiates the new renderable with
     * this internal array having length {@code numInstances}; each element of this array is
     * set to {@link net.vob.util.math.AffineTransformationImpl#IDENTITY IDENTITY}.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param numInstances the affine transformations for each renderable instance
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the new renderable upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>{@code numInstances} is less than 1</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableNew(int numInstances) {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_NEW, numInstances));
    }
    
    /**
     * Instantiates and registers a new renderable. This is a container class that the
     * rendering loop uses to actually perform the rendering, and contains a mesh and
     * an array of textures. The new renderable can then be assigned to a shader program.
     * Note that the mesh is initially {@code null}, and one must be attached first before
     * any rendering will occur. The new renderable is automatically selected, allowing for
     * further processing.<p>
     * 
     * Renderables contain 1 or more <i>instances</i>. An instance acts as a way of rendering
     * the renderable multiple times in a single rendering call, with each instance having
     * their own affine transformation. This can greatly speed up graphical processing when
     * attempting to render large numbers of a simple mesh; for instance, blades of grass. See
     * <a href="https://learnopengl.com/Advanced-OpenGL/Instancing">the documentation on instancing</a>
     * for more information.<p>
     * 
     * Internally, the instances are represented as an affine transformation array, with a
     * single affine transformation per instance; thus, this array defines both the number
     * and transforms of all instances. This message instantiates the new renderable with
     * this internal array having length {@code transforms.length}; each element of this
     * array is an unmodifiable view of the corresponding element in {@code transforms}.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param transforms the affine transformations for each renderable instance
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the new renderable upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>{@code transforms} is empty or {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableNew(AffineTransformation[] transforms) {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_NEW_TRANSFORMS, (Object)transforms));
    }
    
    /**
     * Selects a renderable. This loads the indexed renderable into the state machine of
     * the {@code GraphicsManager} class to allow for further processing; an invalid index will
     * instead set the selected renderable to {@code null}. This message does not, on it's
     * own, affect the state of the {@code GraphicsManager} class in any other way.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param index the index of the renderable to select
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableSelect(int index) {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_SELECT, index));
    }
    
    /**
     * Closes the currently selected renderable. This removes it from the internal 
     * registry, and sets the selected renderable to {@code null}. This message also
     * removes it's reference in the rendering map, thereby ensuring it will not be
     * rendered and that it will be flagged for garbage collection (as no further 
     * references will exist at that point). Be aware that the internal objects (mesh,
     * textures, etc.) will <b><i>not</i></b> be closed by this message.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableClose() {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_CLOSE));
    }
    
    /**
     * Attaches the currently selected mesh to the currently selected renderable. The
     * renderable will use this mesh for rendering purposes up until it is replaced by
     * another invocation of this message, or the mesh is closed. This will replace any
     * previously attached mesh.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     *  <li>the currently selected mesh is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableAttachMesh() {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_ATTACH_MESH));
    }
    
    /**
     * Attaches the currently selected texture to the currently selected renderable. The
     * renderable will use this texture for rendering purposes up until it is replaced by
     * another invocation of this message, the texture is closed, or the texture is
     * manually detached by one of the appropriate messages. This will replace any 
     * previously attached texture that is using the same texture unit.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     *  <li>the currently selected texture is {@code null}</li>
     *  <li>the program of the currently selected renderable does not allow the currently
     * selected texture to be attached</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableAttachTexture() {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_ATTACH_TEXTURE));
    }
    
    /**
     * Attempts to detach the currently selected texture from the currently selected
     * renderable. The message will fail if the selected texture is not attached to the
     * selected renderable.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     *  <li>the currently selected texture is {@code null}</li>
     *  <li>the currently selected texture is not attached to the currently selected
     * renderable at the time when this message is handled</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableDetachTexture() {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_DETACH_TEXTURE));
    }
    
    /**
     * Detaches the texture at the given texture unit from the currently selected
     * renderable. This message does not use the currently selected texture; it will
     * simply detach any texture it finds at the given unit in the renderable.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param unit the texture unit this texture is assigned to. Must be greater than or
     * equal to 0 and less than or equal to {@link GraphicsManager#MAX_COMBINED_TEXTURE_UNITS}
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     *  <li>the given unit is out of range</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableDetachTexture(int unit) {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_DETACH_TEXTURE_UNIT, unit));
    }
    
    /**
     * Sets the number of instances in the currently selected renderable. This
     * discards/creates instances by discarding affine transformations at the end of an 
     * internal transform array, or by padding the end of the array with
     * {@link net.vob.util.math.AffineTransformationImpl#IDENTITY IDENTITY}.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned 
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param numInstances the new number of instances for the renderable
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     *  <li>{@code numInstances} is less than 1</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableSetInstanceTransforms(int numInstances) {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_SET_INSTANCES, numInstances));
    }
    
    /**
     * Sets the number of instances in the currently selected renderable. This
     * discards/creates instances by discarding the internal instance transformation array,
     * and then setting it to an array of unmodifiable instances of the given
     * transformations.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned 
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param transforms the new number of instances for the renderable
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     *  <li>{@code transforms} is empty or {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableSetInstanceTransforms(AffineTransformation... transforms) {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_SET_INSTANCE_TRANSFORMS, (Object)transforms));
    }
    
    /**
     * Sets the affine transformation of an instance of the currently selected renderable
     * to an unmodifiable view of the given transform. Changes to the original affine
     * transformation are visible within the graphics engine.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param transform the new affine transformation of the renderable
     * @param instance the index of the instance
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     *  <li>{@code transform} is {@code null}</li>
     *  <li>{@code instance} is less then 0, or greater than or equal to the number of
     * instances the currently selected renderable has</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableSetInstanceTransform(AffineTransformation transform, int instance) {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_SET_INSTANCE_TRANSFORM, transform.getAsUnmodifiable(true), instance));
    }
    
    /**
     * Attaches the currently selected skeleton to the currently selected renderable.
     * Removes the previous skeleton, if any.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     *  <li>the currently selected skeleton is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableAttachSkeleton() {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_ATTACH_SKELETON));
    }
    
    /**
     * Removes the current skeleton (if any) from the currently selected renderable.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableDetachSkeleton() {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_DETACH_SKELETON));
    }
    
    /**
     * Selects the current mesh of the currently selected renderable.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the selected mesh upon success (or completes with -1 if the selected mesh is
     * {@code null}), or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableSelectMesh() {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_SELECT_MESH));
    }
    
    /**
     * Selects one of the current textures of the currently selected renderable.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param unit the texture unit being queried. Must be greater than or equal to 0
     * and less than or equal to {@link GraphicsManager#MAX_COMBINED_TEXTURE_UNITS}
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the selected texture upon success (or completes with -1 if the selected texture
     * is {@code null}), or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for
     * space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     *  <li>the given unit is out of range</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableSelectTexture(int unit) {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_SELECT_TEXTURE, unit));
    }
    
    /**
     * Selects the shader program that the currently selected renderable is assigned to.<p>
     * 
     * Users must be careful when invoking this message on renderables assigned to the UI
     * via {@link msgUIAssignRenderable()}, as a renderable that is part of the UI will
     * <i>not</i> select the UI shader program. In other words, renderables within the UI
     * system will be treated by this message as if they are not currently assigned to any
     * shader program at all.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the selected shader program upon success (or -1 if the selected shader program
     * is {@code null}), or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableSelectShaderProgram() {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_SELECT_SHADER_PROGRAM));
    }
    
    /**
     * Copies the currently selected renderable.<p>
     * 
     * This instantiates a new renderable to which the mesh reference is copied from the
     * selected renderable's mesh reference, and similarly for the texture references.
     * The new renderable is also assigned to the same shader program as the old renderable,
     * and is then automatically selected to allow for further processing. The only things
     * that are not copied are the instance affine transformations of the renderable, as the
     * caller of this message will likely wish to set the instance number and transforms
     * themselves.<p>
     * 
     * Users must be careful when invoking this message on renderables assigned to the UI
     * via {@link msgUIAssignRenderable()}, as a renderable that is part of the UI will
     * <i>not</i> copy the UI shader program to the new instance. In other words,
     * renderables within the UI system will be treated by this message as if they are not
     * currently assigned to any shader program at all.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the new renderable upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableCopy() {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_COPY));
    }
    
    /**
     * Gets the unique integer id of the currently selected renderable.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the selected renderable upon success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgRenderableGetID() {
        return enqueueMessage(new Message(Message.Type.RENDERABLE_GET_ID));
    }
    
    /**
     * Assigns the currently selected renderable to the currently selected shader program.
     * This puts the renderable into an internal rendering map, through which all rendering
     * operations are done, with the shader program as the key. Due to the use of sets as
     * the map values, multiple renderables can be assigned to a shader program.<p>
     * 
     * This method automatically unassigns the renderable from it's previous shader
     * program, if any. Any textures that are not of a compatible type with the new shader
     * program will be automatically detached from the renderable upon rendering.
     * Furthermore, this message automatically unassigns the renderable from the UI system
     * if needed.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     *  <li>the currently selected shader program is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramAssignRenderable() {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_ASSIGN_RENDERABLE));
    }
    
    /**
     * Unassigns the currently selected renderable from it's current assigned shader
     * program, if it has one. This does not unassign the renderable from the UI system;
     * use {@link msgUIUnassignRenderable()} for that.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgShaderProgramUnassignRenderable() {
        return enqueueMessage(new Message(Message.Type.SHADER_PROGRAM_UNASSIGN_RENDERABLE));
    }
    
    /**
     * Enables rendering of the UI.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgUIEnable() {
        return enqueueMessage(new Message(Message.Type.UI_ENABLE));
    }
    
    /**
     * Disables rendering of the UI.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if the thread experienced an {@link InterruptedException}
     * while waiting for space in the message queue
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgUIDisable() {
        return enqueueMessage(new Message(Message.Type.UI_DISABLE));
    }
    
    /**
     * Assigns the currently selected renderable to the UI system of the graphics engine.
     * This puts the renderable into an internal set, which will be rendered towards the
     * end of the rendering cycle using the default UI shader. The depth bit buffer is 
     * cleared directly prior to the rendering of the UI, so any renderable assigned to the
     * UI is always drawn over the top of the scene; for this reason, usage of this message
     * is preferred over using {@link msgShaderProgramAssignRenderable()} with the default
     * UI shader.<p>
     * 
     * This method automatically unassigns the renderable from it's previous shader program,
     * if any. Any textures that are not of a compatible type with the UI shader program
     * will be automatically detached from the renderable upon rendering. Furthermore, the
     * renderable will not indicate that it is assigned to a shader program (as if it is not
     * assigned at all); therefore, usage of messages such as {@link msgRenderableCopy()} or
     * {@link msgRenderableSelectShaderProgram()} should be made with caution.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgUIAssignRenderable() {
        return enqueueMessage(new Message(Message.Type.UI_ASSIGN_RENDERABLE));
    }
    
    /**
     * Unassigns the currently selected renderable from the UI system, if it is currently
     * assigned to the UI.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with a value of 0
     * on success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while
     * waiting for space in the message queue</li>
     *  <li>the currently selected renderable is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgUIUnassignRenderable() {
        return enqueueMessage(new Message(Message.Type.UI_UNASSIGN_RENDERABLE));
    }
}
