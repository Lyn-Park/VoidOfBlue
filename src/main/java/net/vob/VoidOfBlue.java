package net.vob;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import net.vob.mods.ModManager;
import net.vob.core.graphics.GraphicsEngine;
import net.vob.util.ArrayTree;
import net.vob.util.logging.Level;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.AffineTransformation;
import net.vob.util.math.AffineTransformationImpl;
import net.vob.util.math.Maths;
import net.vob.util.math.Matrix;
import net.vob.util.math.Quaternion;
import net.vob.util.math.Vector;
import net.vob.util.math.Vector3;

/**
 * @author Lyn-Park
 */
public final class VoidOfBlue {
    private VoidOfBlue() {}
    
    private static final Logger LOG = getLogger(VoidOfBlue.class);
    
    public static final Path MAIN_DIR_PATH;
    public static final Path MOD_DIR_PATH;
    public static final Path SAVE_DIR_PATH;
    
    private static Instant startTime;
    private static double currentTime, deltaTime;
    
    private static boolean continueExecution = true;
    
    private static final Object ctSync = new Object(), dtSync = new Object(), ceSync = new Object();
    
    static {
        try {
            LogManager.getLogManager().readConfiguration(VoidOfBlue.class.getResourceAsStream("/data/logging/logging.properties"));
            
            URL jarLoc = VoidOfBlue.class.getProtectionDomain().getCodeSource().getLocation();
            
            MAIN_DIR_PATH = Paths.get(new URL(jarLoc, ".").toURI());
            MOD_DIR_PATH = MAIN_DIR_PATH.resolve("mods");
            SAVE_DIR_PATH = MAIN_DIR_PATH.resolve("saves");
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static Logger getLogger(Class clazz) {
        return Logger.getLogger(clazz.getCanonicalName(), LocaleUtils.LOGGING_BUNDLE_NAME);
    }
    
    public static Instant getStartTime() {
        return startTime;
    }
    
    public static double getCurrentTime() {
        synchronized (ctSync) {
            return currentTime;
        }
    }
    
    private static void setCurrentTime(double ct) {
        synchronized (ctSync) {
            currentTime = ct;
        }
    }
    
    public static double getDeltaTime() {
        synchronized (dtSync) {
            return deltaTime;
        }
    }
    
    private static void setDeltaTime(double dt) {
        synchronized (dtSync) {
            deltaTime = dt;
        }
    }
    
    public static void stopProgram() {
        synchronized (ceSync) {
            continueExecution = false;
        }
    }
    
    private static boolean getContinueExecution() {
        synchronized (ceSync) {
            return continueExecution;
        }
    }
    
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    private static void begin() throws Throwable {
        LOG.log(Level.INFO, "VoidOfBlue.begin.Start");
        startTime = Instant.now();
        
        GraphicsEngine.init(GraphicsEngine.VALUE_DONT_CARE, GraphicsEngine.VALUE_DONT_CARE,
                            800, 640, 100, 80.0f, 0.1f, 100f, false, 10);
        ModManager.loadModFiles();
    }
    
    private static void loop() throws Throwable {
        
        // --- TESTBED ---
        
        AffineTransformation camera = new AffineTransformationImpl(),
                                  t1 = new AffineTransformationImpl(),
                                  t2 = new AffineTransformationImpl(),
                                  b1 = new AffineTransformationImpl(),
                                  b2 = new AffineTransformationImpl();
        
        Matrix weights = Maths.packVectorsToMatrixHorizontal(new Vector(1, 0, 0),
                                                              new Vector(1, 0, 0),
                                                              new Vector(0, 1, 0),
                                                              new Vector(0, 2, 1),
                                                              new Vector(0, 1, 2),
                                                              new Vector(0, 0, 1),
                                                              new Vector(1, 0, 0),
                                                              new Vector(1, 0, 0));
        
        ArrayTree<AffineTransformation> skeleton = new ArrayTree<>(new AffineTransformationImpl());
        skeleton.add(b1);
        skeleton.add(b2);
        
        GraphicsEngine.MESSAGE_LOCK.lock();
        try {
            GraphicsEngine.msgEnableDebugMode();
            
            GraphicsEngine.msgViewportSetTransform(camera);
            GraphicsEngine.msgTextureSelectDefaultCubemap();
            GraphicsEngine.msgSkyboxSetTexture();
            GraphicsEngine.msgSkyboxEnable();
            
            GraphicsEngine.msgRenderableNew(1);
            GraphicsEngine.msgMeshSelectDefaultCube();
            GraphicsEngine.msgTextureSelectDefaultCubemap();
            GraphicsEngine.msgShaderProgramSelectDefaultCube();
            GraphicsEngine.msgRenderableAttachMesh();
            GraphicsEngine.msgRenderableAttachTexture();
            GraphicsEngine.msgShaderProgramAssignRenderable();
            
            GraphicsEngine.msgRenderableSetInstanceTransform(t1, 0);
            
            GraphicsEngine.msgRenderableNew(1);
            GraphicsEngine.msgRenderableAttachMesh();
            GraphicsEngine.msgRenderableAttachTexture();
            GraphicsEngine.msgShaderProgramAssignRenderable();
            GraphicsEngine.msgSkeletonNew(skeleton, weights);
            
            GraphicsEngine.msgRenderableSetInstanceTransform(t2, 0);
            GraphicsEngine.msgRenderableAttachSkeleton();
            
            t1.setTranslation(new Vector3(1, 0, 0));
            t2.setTranslation(new Vector3(-1, 0, 0));
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
        
        while (getContinueExecution()) {
            Instant loopStartTime = Instant.now();
            GraphicsEngine.pollEvents();
            
            // ------------------------------------------------- //
            
            double t = getCurrentTime() / 3d;
            
            double s = Math.sin(t * Math.PI) * 0.75;
            double c = Math.cos(t * Math.PI) * 0.75;
            
            camera.setTranslation(Vector3.FORWARD.rotate(Quaternion.rotationQuaternion(Vector3.UP, t)).mul(3));
            camera.setRotation(Quaternion.rotationQuaternion(camera.getTranslation().mul(-1), Vector3.ZERO, Vector3.UP));
            b1.setRotation(Quaternion.rotationQuaternion(Vector3.BACKWARD, s));
            b2.setRotation(Quaternion.rotationQuaternion(Vector3.BACKWARD, c));
            
            // ------------------------------------------------- //
            
            Instant loopEndTime = Instant.now();
            setDeltaTime(Duration.between(loopStartTime, loopEndTime).toNanos() / 1e9d);
            setCurrentTime(Duration.between(startTime, loopEndTime).toNanos() / 1e9d);
        }
        
        // --- TESTBED END ---
    }
    
    private static void end() throws Throwable {
        ModManager.unloadModFiles();
        GraphicsEngine.close();
        
        LOG.log(Level.INFO, "VoidOfBlue.end.End");
    }
    
    public static void main(String[] args) {
        int status = 0;
        
        try {
            begin();
            loop();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "VoidOfBlue.main.Exception", t);
            status |= 1;
        } finally {
            try {
                end();
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "VoidOfBlue.main.EndException", t);
                status |= 2;
            }
        }

        System.exit(status);
    }
}
