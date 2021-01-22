package net.vob.core;

import java.util.concurrent.Future;
import net.vob.core.graphics.GraphicsEngine;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.AffineTransformation;
import net.vob.util.math.AffineTransformationImpl;

/**
 * An implementation class of {@link AbstractRenderable}. This class is used for
 * renderables that are part of the 2D user interface; as such, instances only take
 * a mesh and a single texture. The shader is locked to {@link Shader#DEFAULT_UI},
 * and the texture must be a diffuse (unit 0) texture.
 * 
 * @author Lyn-Park
 */
public final class UIRenderable extends AbstractRenderable {
    /**
     * The affine transform of this renderable.
     */
    public final AffineTransformation transform = new AffineTransformationImpl();

    /**
     * Builds a new renderable from the given parameters.
     * 
     * @param mesh the {@link Mesh} of the new renderable
     * @param texture the diffuse {@link Texture2D} object of the new renderable
     * @return the newly constructed {@link Renderable} instance
     * @throws NullPointerException if {@code mesh} or {@code texture} is {@code null}
     * @throws IllegalArgumentException if {@code texture} has a non-zero texture unit
     */
    public static UIRenderable build(Mesh mesh, Texture2D texture) {
        if (texture == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "texture"));
        if (texture.unit != 0)
            throw new IllegalArgumentException(LocaleUtils.format("UIRenderable.InvalidTextureUnit"));
        
        return new UIRenderable(mesh, texture);
    }
    
    private UIRenderable(Mesh mesh, Texture2D texture) {
        super(mesh, Shader.DEFAULT_UI, texture);
    }
    
    @Override
    protected Future<Integer> initialize() {
        Future<Integer> future = GraphicsEngine.msgRenderableNew(1);
        GraphicsEngine.msgRenderableSetInstanceTransform(transform, 0);
        
        getMesh().select();
        GraphicsEngine.msgRenderableAttachMesh();
        
        getTexture(0).select();
        GraphicsEngine.msgRenderableAttachTexture();
        
        GraphicsEngine.msgUIAssignRenderable();
        
        return future;
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * This method is overridden so that it performs the additional check of the texture
     * having a texture unit of 0.
     * 
     * @param texture {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}, or if {@code texture} has a
     * non-zero texture unit
     */
    @Override
    public void setTexture(Texture texture) {
        if (!hasRendID())
            return;
        if (texture == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "texture"));
        if (texture.unit != 0)
            throw new IllegalArgumentException(LocaleUtils.format("UIRenderable.InvalidTextureUnit"));
        
        super.setTexture(texture);
    }
    
    /**
     * Sets the shader of this renderable. Since this is a UI renderable, this method
     * has been overridden to always throw an {@link UnsupportedOperationException}.
     * @param shader 
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setShader(Shader shader) {
        throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.UnsupportedOperationException", "UIRenderable", "setShader"));
    }
}
