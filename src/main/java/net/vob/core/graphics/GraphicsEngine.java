package net.vob.core.graphics;

import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.vob.VoidOfBlue;
import net.vob.util.logging.LoggerOutputStream;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import net.vob.util.logging.Level;
import net.vob.util.Identity;
import net.vob.util.math.Vector2;
import net.vob.util.math.Vector3;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.AffineTransformation;
import net.vob.core.ui.InputManager;
import net.vob.util.Input;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The static manager class for the window. This extends to not only handling GLFW and
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
 * effect is most prominent with textures).<p>
 * 
 * GL objects exist in a hierarchy, of sorts. This hierarchy closely follows theoretical
 * relations between objects, and serves to simplify the operation of the
 * {@code GraphicsManager} and the rendering loop. The hierarchy is as follows:
 * <blockquote><pre>
 *                     <b>Mesh</b> (single)     (multiple renderables        <b>Vertex Shader</b> (mandatory)
 *                        \u2198                   per program)             \u2199
 *      <b>2D Texture</b>      <b>Renderable Object</b>      \u27a1     <b>Shader Program</b> \u2b05 <b>Geometry Shader</b> (optional)
 *              \u2198         \u2197                                            \u2196
 *               <b>Textures</b> (multiple textures                          <b>Fragment Shader</b> (mandatory)
 *              \u2197            per renderable)
 * <b>Cubemap Texture</b>
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
 * classes and systems, specializing in rendering the background sky texture.</li>
 *  <li>The <i>UI system</i>; a special system for 2D UI renderables, which guarantees that
 * renderables will be drawn on top of the rest of the scene without being occluded.</li>
 *  <li>Renderable <i>instances</i>; a parameter that allows for the GPU to render multiple
 * copies of the same renderable with a single rendering call, which can speed up rendering
 * in cases where large numbers of the same renderable must be drawn at once; e.g. grass or
 * leaves.</li>
 * </ul>
 * <b>Note:</b>
 * <ul>
 *  <li>This manager must be initialized before any use, and closed before the program is
 * shutdown to free up system resources and prevent memory leaking.</li>
 *  <li>The graphics engine, when passed an {@link AffineTransformation} via any messaging
 * function, relies on the dirty flags of these transformations to know when to update the
 * GPU buffers; thus, outside threads should take care when calling one of the
 * transformation matrix methods of these instances, as that will clear the dirty flag.</li>
 * </ul> 
 * <b>Warning: the manager cannot be reopened after being closed. Thus, only specific
 * external classes should attempt to close this manager.</b>
 */
public final class GraphicsEngine {
    private GraphicsEngine() {}
    
    private static final Logger LOG = VoidOfBlue.getLogger(GraphicsEngine.class);
    
    public static final int VALUE_NULL = 0;
    public static final int VALUE_DONT_CARE = GLFW_DONT_CARE;
    public static final int VALUE_FALSE = GLFW_FALSE;
    public static final int VALUE_TRUE = GLFW_TRUE;
    
    public static final int CONSTANT_MIN_WINDOW_WIDTH = 64;
    public static final int CONSTANT_MIN_WINDOW_HEIGHT = 64;
    public static final float CONSTANT_MIN_FOV = 10f;
    public static final float CONSTANT_MAX_FOV = 170f;
    public static final float CONSTANT_MIN_ZNEAR_DIST = 0.01f;
    public static final float CONSTANT_MAX_ZFAR_DIST = 10000f;
    public static final float CONSTANT_MIN_Z_DIST_SEPARATION = 1f;
    
    public static final int CURSOR_ARROW = GLFW_ARROW_CURSOR;
    public static final int CURSOR_IBEAM = GLFW_IBEAM_CURSOR;
    public static final int CURSOR_CROSSHAIR = GLFW_CROSSHAIR_CURSOR;
    public static final int CURSOR_HAND = GLFW_HAND_CURSOR;
    public static final int CURSOR_HRESIZE = GLFW_HRESIZE_CURSOR;
    public static final int CURSOR_VRESIZE = GLFW_VRESIZE_CURSOR;
    
    public static final Identity DEFAULT_TEXTURE_ID = new Identity("default_texture").partial("core");
    public static final Identity DEFAULT_SHADER_2D_ID = new Identity("default_shader_2D").partial("core");
    public static final Identity DEFAULT_SHADER_CUBE_ID = new Identity("default_shader_cubemap").partial("core");
    public static final Identity DEFAULT_SHADER_UI_ID = new Identity("default_shader_ui").partial("core");
    
    public static final int STATUS_INITIALIZED = 1;
    public static final int STATUS_INITIALIZABLE = 2;
    public static final int STATUS_VSYNC = 4;
    
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
    
    private static final ReentrantLock STATUS_LOCK = new ReentrantLock();
    private static byte status = STATUS_INITIALIZABLE;
    private static long window, cursor;
    
    private final static Vector2 cursorPos = new Vector2();
    
    static WindowOptions windowOptions;
    private static Thread CONTEXT = null;
    
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
     * objects (see the javadocs of the {@code wmXXXSelect(index)} functions for more
     * information on object selecting).
     * 
     * @param initialWindowOptions the {@link WindowOptions} object to use for the initial
     * window options
     * @param graphicsLoopPeriodMS the target length, in milliseconds, between invocations
     * of the graphical loop in the graphics thread
     * @throws InterruptedException if this thread is interrupted while waiting for the
     * graphics thread to be initialized
     */
    public static void init(WindowOptions initialWindowOptions, int graphicsLoopPeriodMS) throws InterruptedException {
        if (!getStatus(STATUS_INITIALIZABLE))
            return;
        
        LOG.log(Level.FINEST, "global.Status.Init.Start", "Graphics engine");
        
        glfwSetErrorCallback(GLFWErrorCallback.createPrint(new PrintStream(new LoggerOutputStream(LOG, Level.FINER))));
        
        if (!glfwInit()) {
            LOG.log(Level.SEVERE, "global.Status.Init.Failed", "GLFW");
            return;
        }
        
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, VALUE_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, VALUE_TRUE);
        
        window = glfwCreateWindow(initialWindowOptions.getWindowWidth(), initialWindowOptions.getWindowHeight(), "Void of Blue", VALUE_NULL, VALUE_NULL);
        if (window == VALUE_NULL) {
            LOG.log(Level.SEVERE, "global.Status.Init.Failed", "Window");
            return;
        }
        
        glfwSetWindowSizeLimits(window, CONSTANT_MIN_WINDOW_WIDTH, CONSTANT_MIN_WINDOW_HEIGHT, VALUE_DONT_CARE, VALUE_DONT_CARE);
        
        int[] w = new int[1], h = new int[1];
        glfwGetWindowSize(window, w, h);
        
        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(window, (vidMode.width() - w[0]) / 2, (vidMode.height() - h[0]) / 2);
        
        LOG.log(Level.FINEST, "GraphicsEngine.init.WindowInit", new Object[] { w[0], h[0] });
        
        glfwSetWindowCloseCallback(window, (_window) -> {
            glfwSetWindowShouldClose(window, false);
            VoidOfBlue.stopProgram();
        });
        
        glfwSetWindowSizeCallback(window, (_window, width, height) -> {
            windowOptions.setWindowWidth(width);
            windowOptions.setWindowHeight(height);
        });
        
        glfwSetCursorPosCallback(window, (_window, xpos, ypos) -> {
            cursorPos.setX(xpos);
            cursorPos.setY(windowOptions.getWindowHeight() - ypos);
            
            Input input = new Input(Input.Source.MOUSE, cursorPos, 0, 0, 0);
            InputManager.pushInputToRootUIElements(input);
            // TODO - pass input to other areas
        });
        
        glfwSetMouseButtonCallback(window, (_window, button, action, mods) -> {
            Input input = new Input(Input.Source.MOUSE, cursorPos, button, 1, action);
            InputManager.pushInputToRootUIElements(input);
            // TODO - pass input to other areas
        });
        
        glfwSetKeyCallback(window, (_window, key, scancode, action, mods) -> {
            Input input = new Input(Input.Source.KEY, null, key, scancode, action);
            InputManager.pushInputToFocusedUIElement(input);
            // TODO - pass input to other areas
        });
        
        glfwSetCharCallback(window, (_window, codepoint) -> {
            Input input = new Input(Input.Source.CHAR, null, codepoint, 0, 0);
            InputManager.pushInputToFocusedUIElement(input);
            // TODO - pass input to other areas
        });
        
        cursor = glfwCreateStandardCursor(CURSOR_ARROW);
        glfwSetCursor(window, cursor);
        
        windowOptions = initialWindowOptions;
        glfwShowWindow(window);
        
        setStatus(STATUS_INITIALIZABLE, false);
        setStatus(STATUS_INITIALIZED, true);
        
        GraphicsManager.init(graphicsLoopPeriodMS);
        GraphicsManager.INIT_LATCH.await();
        
        if (GraphicsEngine.getStatus(GraphicsEngine.STATUS_INITIALIZED))
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
    
    static void setWindowDims(int windowWidth, int windowHeight) {
        glfwSetWindowSize(window, windowWidth, windowHeight);
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
     * Sets the window options to use.<p>
     * 
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @param options the new {@link WindowOptions} to use for the window
     * @return a {@link CompletableFuture} object that completes with a value of 0 on
     * success, or is cancelled if:
     * <ul>
     *  <li>the thread experienced an {@link InterruptedException} while waiting for space 
     * in the message queue</li>
     *  <li>{@code options} is {@code null}</li>
     * </ul>
     * @throws IllegalStateException if the manager has not been initialized, i.e. the
     * {@link STATUS_INITIALIZED} status flag is not set
     */
    public static CompletableFuture<Integer> msgWindowOptionsSet(WindowOptions options) {
        return enqueueMessage(new Message(Message.Type.WINDOW_OPTIONS_SET, options));
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
     * @param index the index of the texture to select
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
     * Note that this method communicates with the graphics thread, so it may have to
     * wait for space in the message queue. In addition, the returned
     * {@link CompletableFuture} should not be completed outside of the graphics thread.
     * 
     * @return a {@link CompletableFuture} object that completes with the unique id value
     * of the selected shader program upon success (or completes with -1 if the selected 
     * shader program is {@code null}), or is cancelled if:
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
     * UI shader selected.<p>
     * 
     * This method automatically unassigns the renderable from it's previous shader program,
     * if any. Any textures that are not of a compatible type with the UI shader program
     * will be automatically detached from the renderable upon rendering. Furthermore, the
     * renderable will not indicate that it is assigned to a shader program; so usage of
     * messages such as {@link msgRenderableCopy()} or
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
