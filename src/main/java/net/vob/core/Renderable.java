package net.vob.core;

import java.util.concurrent.Future;
import net.vob.core.graphics.GraphicsEngine;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.AffineTransformation;
import net.vob.util.math.AffineTransformationImpl;

/**
 * An implementation class of {@link AbstractRenderable}. This class can take the
 * full range of inputs (instance number, mesh, shader, and a number of textures)
 * that a renderable can have.
 */
public final class Renderable extends AbstractRenderable {
    public final AffineTransformation[] transforms;
    
    /**
     * Builds a new renderable from the given parameters.
     * 
     * @param numInstances the initial number of instances of the new renderable
     * @param mesh the {@link Mesh} of the new renderable
     * @param shader the {@link Shader} of the new renderable
     * @param textures the list of {@link TextureCubemap} objects of the new renderable
     * @return the newly constructed {@link Renderable} instance
     * @throws NullPointerException if {@code mesh} or {@code shader} is {@code null}
     * @throws IllegalArgumentException if {@code shader} does not have a mode of
     * {@code false}
     */
    public static Renderable build(int numInstances, Mesh mesh, Shader shader, Texture2D... textures) {
        if (numInstances < 1)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>", "numInstances", numInstances, 0));
        
        return new Renderable(numInstances, mesh, shader, textures);
    }
    
    /**
     * Builds a new renderable from the given parameters.
     * 
     * @param numInstances the initial number of instances of the new renderable
     * @param mesh the {@link Mesh} of the new renderable
     * @param shader the {@link Shader} of the new renderable
     * @param textures the list of {@link TextureCubemap} objects of the new renderable
     * @return the newly constructed {@link Renderable} instance
     * @throws NullPointerException if {@code mesh} or {@code shader} is {@code null}
     * @throws IllegalArgumentException if {@code numInstances} is less than 1, or 
     * {@code shader} does not have a mode of {@code true}
     */
    public static Renderable build(int numInstances, Mesh mesh, Shader shader, TextureCubemap... textures) {
        if (numInstances < 1)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>", "numInstances", numInstances, 0));
        
        return new Renderable(numInstances, mesh, shader, textures);
    }
    
    private Renderable(int numInstances, Mesh mesh, Shader shader, Texture2D... textures) {
        super(mesh, shader, textures);
        
        this.transforms = new AffineTransformation[numInstances];
        for (int i = 0; i < numInstances; ++i)
            this.transforms[i] = new AffineTransformationImpl();
    }
    
    private Renderable(int numInstances, Mesh mesh, Shader shader, TextureCubemap... textures) {
        super(mesh, shader, textures);
        
        this.transforms = new AffineTransformation[numInstances];
        for (int i = 0; i < numInstances; ++i)
            this.transforms[i] = new AffineTransformationImpl();
    }
    
    @Override
    protected Future<Integer> initialize() {
        Future<Integer> future = GraphicsEngine.msgRenderableNew(transforms);
        
        getMesh().select();
        GraphicsEngine.msgRenderableAttachMesh();
        
        for (Texture texture : getTextures()) {
            texture.select();
            GraphicsEngine.msgRenderableAttachTexture();
        }
        
        getShader().select();
        GraphicsEngine.msgShaderProgramAssignRenderable();
        
        return future;
    }
}
