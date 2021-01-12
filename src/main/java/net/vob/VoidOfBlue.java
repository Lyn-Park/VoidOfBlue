package net.vob;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javafx.embed.swing.JFXPanel;
import javax.swing.SwingUtilities;
import net.vob.mods.ModpackManager;
import net.vob.core.graphics.GraphicsEngine;
import net.vob.core.graphics.WindowOptions;
import net.vob.util.AbstractTree;
import net.vob.util.ArrayTree;
import net.vob.util.logging.Level;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.AffineTransformation;
import net.vob.util.math.AffineTransformationImpl;
import net.vob.util.math.Quaternion;
import net.vob.util.math.Vector3;

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
        
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            new JFXPanel();
            latch.countDown();
        });
        
        GraphicsEngine.init(new WindowOptions(800, 640, 100, 80.0f, 0.1f, 100f), 10);
        
        ModpackManager.loadModFiles();
        latch.await();
    }
    
    private static void loop() throws Throwable {
        
        // --- TESTBED ---
        
        AffineTransformation camera = new AffineTransformationImpl(),
                                  t1 = new AffineTransformationImpl(),
                                  t2 = new AffineTransformationImpl();
        
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
            
            GraphicsEngine.msgRenderableSetInstanceTransform(t2, 0);
            
            t1.setTranslation(new Vector3(1, 0, 0));
            t2.setTranslation(new Vector3(-1, 0, 0));
            
        } finally {
            GraphicsEngine.MESSAGE_LOCK.unlock();
        }
        
        // --- TESTBED END ---
        
        while (getContinueExecution()) {
            Instant loopStartTime = Instant.now();
            GraphicsEngine.pollEvents();
            
            // ------------------------------------------------- //
            
            double s = Math.sin(getCurrentTime() / 3d) * 3;
            double c = Math.cos(getCurrentTime() / 3d) * 3;
            
            camera.setTranslation(new Vector3(s, 0, -c));
            camera.setRotation(Quaternion.rotationQuaternion(Vector3.DOWN, getCurrentTime() / 3d)).appendRotation(Quaternion.POS_Y);
            
            // ------------------------------------------------- //
            
            Instant loopEndTime = Instant.now();
            setDeltaTime(Duration.between(loopStartTime, loopEndTime).toNanos() / 1e9d);
            setCurrentTime(Duration.between(startTime, loopEndTime).toNanos() / 1e9d);
        }
    }
    
    private static void end() throws Throwable {
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
