package net.vob.core.graphics;

import net.vob.util.math.Maths;
import net.vob.util.math.Matrix;

/**
 * Container class for the various window options. This includes the dimensions of
 * the window, the FOV, the Z-clipping planes, etc.
 */
public final class WindowOptions {
    private int windowWidth, windowHeight, windowDepth;
    private float fov, zNearDist, zFarDist;
    
    private final Matrix projectionMatrix = new Matrix(4);
    private boolean dirty = true;
    
    /**
     * Initializes the window options using the various setters for each parameter.
     * See the javadocs on each getter for more information on the meaning of each
     * parameter, and each setter for more information on clamping of the values to
     * within certain ranges.
     * 
     * @param windowWidth the width of the window
     * @param windowHeight the height of the window
     * @param windowDepth the depth of the window
     * @param fov the FOV
     * @param zNearDist the near clipping plane distance
     * @param zFarDist the far clipping plane distance
     */
    public WindowOptions(int windowWidth, int windowHeight, int windowDepth, float fov, float zNearDist, float zFarDist) {
        setWindowWidth(windowWidth);
        setWindowHeight(windowHeight);
        setWindowDepth(windowDepth);
        setFOV(fov);
        setZDists(zNearDist, zFarDist);
    }
    
    /**
     * Gets the width of the window. This is the full graphical width of the window
     * measured in pixels.
     * @return the width of the window
     */
    public int getWindowWidth() { return windowWidth; }
    /**
     * Gets the height of the window. This is the full graphical height of the window
     * measured in pixels.
     * @return the height of the window
     */
    public int getWindowHeight() { return windowHeight; }
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
    public int getWindowDepth() { return windowDepth; }
    /**
     * Gets the window FOV. This Field-Of-View is defined to be the angle, in degrees,
     * between the two planes that define the horizontal left-right bounds of the
     * camera viewing frustum.
     * @return the FOV
     */
    public float getFOV() { return fov; }
    /**
     * Gets the distance of the near clipping plane. The near clipping plane is
     * parallel to the camera viewing plane, and only fragments 'behind' it (from the
     * viewpoint of the camera) are rendered; any other fragments are discarded
     * (clipped). The distance is measured from the camera to this plane.
     * 
     * @return the distance of the near clipping plane
     */
    public float getZNearDist() { return zNearDist; }
    /**
     * Gets the distance of the far clipping plane. The far clipping plane is parallel
     * to the camera viewing plane, and only fragments 'in front' of it (from the
     * viewpoint of the camera) are rendered; any other fragments are discarded 
     * (clipped). The distance is measured from the camera to this plane.
     * 
     * @return the distance of the far clipping plane
     */
    public float getZFarDist() { return zFarDist; }
    
    /**
     * Sets the width of the window. This value is clamped such that it is always
     * greater than or equal to {@link GraphicsEngine#CONSTANT_MIN_WINDOW_WIDTH}.
     * @param windowWidth the new window width
     */
    public synchronized void setWindowWidth(int windowWidth) {
        if (windowWidth < GraphicsEngine.CONSTANT_MIN_WINDOW_WIDTH)
            windowWidth = GraphicsEngine.CONSTANT_MIN_WINDOW_WIDTH;
        
        this.windowWidth = windowWidth;
        this.dirty = true;
    }
    
    /**
     * Sets the height of the window. This value is clamped such that it is always
     * greater than or equal to {@link GraphicsEngine#CONSTANT_MIN_WINDOW_HEIGHT}.
     * @param windowHeight the new window height
     */
    public synchronized void setWindowHeight(int windowHeight) {
        if (windowHeight < GraphicsEngine.CONSTANT_MIN_WINDOW_HEIGHT)
            windowHeight = GraphicsEngine.CONSTANT_MIN_WINDOW_HEIGHT;
        
        this.windowHeight = windowHeight;
        this.dirty = true;
    }
    
    /**
     * Sets the depth of the window. This value is clamped such that it is always
     * greater than or equal to 1.
     * @param windowDepth the new window depth
     */
    public synchronized void setWindowDepth(int windowDepth) {
        if (windowDepth < 1)
            windowDepth = 1;
        
        this.windowDepth = windowDepth;
    }
    
    /**
     * Sets the FOV of the window. This value is clamped such that it is in the range
     * [{@link GraphicsEngine#CONSTANT_MIN_FOV}, {@link GraphicsEngine#CONSTANT_MAX_FOV}],
     * inclusive.
     * @param fov the new window FOV
     */
    public synchronized void setFOV(float fov) {
        this.fov = Maths.clamp(GraphicsEngine.CONSTANT_MIN_FOV, fov, GraphicsEngine.CONSTANT_MAX_FOV);
        this.dirty = true;
    }
    
    /**
     * Sets the distances of the clipping planes of the window. Initially, each distance
     * is clamped such that:
     * <blockquote><pre>
     *  {@link GraphicsEngine#CONSTANT_MIN_ZNEAR_DIST CONSTANT_MIN_ZNEAR_DIST} {@code <= zNearDist <= }{@link GraphicsEngine#CONSTANT_MAX_ZFAR_DIST CONSTANT_MAX_ZFAR_DIST} {@code - }{@link GraphicsEngine#CONSTANT_MIN_Z_DIST_SEPARATION CONSTANT_MIN_Z_DIST_SEPARATION}
     *  {@link GraphicsEngine#CONSTANT_MIN_ZNEAR_DIST CONSTANT_MIN_ZNEAR_DIST} {@code + }{@link GraphicsEngine#CONSTANT_MIN_Z_DIST_SEPARATION CONSTANT_MIN_Z_DIST_SEPARATION} {@code <= zFarDist <= }{@link GraphicsEngine#CONSTANT_MAX_ZFAR_DIST CONSTANT_MAX_ZFAR_DIST}</pre></blockquote>
     * Then {@code zNearDist} is further clamped such that it is always less than or
     * equal to
     * {@code zFarDist + }{@link GraphicsEngine#CONSTANT_MIN_Z_DIST_SEPARATION CONSTANT_MIN_Z_DIST_SEPARATION}.
     * 
     * @param zNearDist the new near clipping plane distance
     * @param zFarDist the new far clipping plane distance
     */
    public synchronized void setZDists(float zNearDist, float zFarDist) {
        zNearDist = Maths.clamp(GraphicsEngine.CONSTANT_MIN_ZNEAR_DIST, zNearDist, GraphicsEngine.CONSTANT_MAX_ZFAR_DIST - GraphicsEngine.CONSTANT_MIN_Z_DIST_SEPARATION);
        zFarDist = Maths.clamp(GraphicsEngine.CONSTANT_MIN_ZNEAR_DIST + GraphicsEngine.CONSTANT_MIN_Z_DIST_SEPARATION, zFarDist, GraphicsEngine.CONSTANT_MAX_ZFAR_DIST);
        
        if (zNearDist > zFarDist + GraphicsEngine.CONSTANT_MIN_Z_DIST_SEPARATION)
            zNearDist = zFarDist + GraphicsEngine.CONSTANT_MIN_Z_DIST_SEPARATION;
        
        this.zNearDist = zNearDist;
        this.zFarDist = zFarDist;
        this.dirty = true;
    }
    
    /**
     * Sets the distance of the near clipping plane. This value is clamped such that it
     * is in the range
     * [{@link GraphicsEngine#CONSTANT_MIN_ZNEAR_DIST}, {@code zFarDist - }{@link GraphicsEngine#CONSTANT_MIN_Z_DIST_SEPARATION}],
     * inclusive.
     * 
     * @param zNearDist the new near clipping plane distance
     */
    public synchronized void setZNearDist(float zNearDist) {
        this.zNearDist = Maths.clamp(GraphicsEngine.CONSTANT_MIN_ZNEAR_DIST, zNearDist, zFarDist - GraphicsEngine.CONSTANT_MIN_Z_DIST_SEPARATION);
        this.dirty = true;
    }
    
    /**
     * Sets the distance of the far clipping plane. This value is clamped such that it
     * is in the range
     * [{@code zNearDist + }{@link GraphicsEngine#CONSTANT_MIN_Z_DIST_SEPARATION}, {@link GraphicsEngine#CONSTANT_MAX_ZFAR_DIST}],
     * inclusive.
     * 
     * @param zFarDist the new far clipping plane distance
     */
    public synchronized void setZFarDist(float zFarDist) {
        this.zFarDist = Maths.clamp(zNearDist + GraphicsEngine.CONSTANT_MIN_Z_DIST_SEPARATION, zFarDist, GraphicsEngine.CONSTANT_MAX_ZFAR_DIST);
        this.dirty = true;
    }
    
    /**
     * Flags these options as dirty.
     */
    synchronized void setDirty() {
        dirty = true;
    }
    
    /**
     * @return {@code true} if these options are dirty
     */
    synchronized boolean isDirty() {
        return dirty;
    }
    
    /**
     * Gets the projection matrix.<p>
     * 
     * If these options are are dirty, then the internally held projection matrix is
     * reloaded using the current options; this will also flush any changes to the
     * window width/height to GLFW so that the dimensions of the actual window match
     * the current options.
     * 
     * @return the projection matrix for this set of options
     */
    synchronized Matrix flushAndGetProjectionMatrix() {
        if (dirty) {
            dirty = false;
            
            double tanfov = Math.tan(Math.PI * fov / 360d);
            double frustrumlength = zFarDist - zNearDist;
            
            projectionMatrix.setElement(0, 0, windowHeight / (tanfov * windowWidth));
            projectionMatrix.setElement(1, 1, 1 / tanfov);
            projectionMatrix.setElement(2, 2, -(zFarDist + zNearDist) / frustrumlength);
            projectionMatrix.setElement(2, 3, -(2 * zFarDist * zNearDist) / frustrumlength);
            projectionMatrix.setElement(3, 2, -1);
            
            GraphicsEngine.setWindowDims(windowWidth, windowHeight);
        }
        
        return projectionMatrix;
    }
}
