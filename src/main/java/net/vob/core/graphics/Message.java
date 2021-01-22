package net.vob.core.graphics;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import net.vob.util.Identity;
import net.vob.util.Tree;
import net.vob.util.math.AffineTransformation;
import net.vob.util.math.AffineTransformationImpl;
import net.vob.util.math.Matrix;
import net.vob.util.math.Vector3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

/**
 * Messaging class. This class is used for communicating with the graphics thread, and
 * also contains a {@link Future} object for two-way communication.
 * 
 * @author Lyn-Park
 */
final class Message {
    private final Type type;
    private final Object[] args;
    final CompletableFuture<Integer> future = new CompletableFuture<>();
    
    Message(Type type, Object... args) {
        this.type = type;
        this.args = args;
    }
    
    @SuppressWarnings("element-type-mismatch")
    void handle() throws Throwable {
        AffineTransformation AFFINE_TRANSFORMATION;
        AffineTransformation[] AFFINE_TRANSFORMATION_ARR;
        Tree<AffineTransformation, ?> AFFINE_TRANSFORMATION_TREE;
        Matrix MATRIX;
        WindowOptions WINDOW_OPTIONS;
        Identity IDENTITY_0, IDENTITY_1, IDENTITY_2, IDENTITY_3, IDENTITY_4, IDENTITY_5;
        BufferedImage BUFFERED_IMAGE_0, BUFFERED_IMAGE_1, BUFFERED_IMAGE_2, BUFFERED_IMAGE_3, BUFFERED_IMAGE_4, BUFFERED_IMAGE_5;
        Vector3[] VECTOR3_ARR_0, VECTOR3_ARR_1, VECTOR3_ARR_2;
        int INT;
        int[] INT_ARR;
        boolean BOOLEAN;
        
        
        Integer o = 0;

        switch (type) {
            case WINDOW_OPTIONS_SET:
                WINDOW_OPTIONS = (WindowOptions)args[0];
                
                if (WINDOW_OPTIONS == null)
                    o = null;
                else {
                    GraphicsEngine.windowOptions = WINDOW_OPTIONS;
                    GraphicsEngine.setWindowDims(WINDOW_OPTIONS.getWindowWidth(), WINDOW_OPTIONS.getWindowHeight());
                }
                break;
                
            case ENABLE_VSYNC:
                GraphicsEngine.doEnableVSync();
                break;
                
            case DISABLE_VSYNC:
                GraphicsEngine.doDisableVSync();
                break;
                
            case ENABLE_DEBUGGING:
                GL11.glEnable(GL43.GL_DEBUG_OUTPUT);
                break;
                
            case DISABLE_DEBUGGING:
                GL11.glDisable(GL43.GL_DEBUG_OUTPUT);
                break;
                
            case VIEWPORT_SET_TRANSFORM:
                AFFINE_TRANSFORMATION = (AffineTransformation)args[0];
                
                if (AFFINE_TRANSFORMATION == null)
                    o = null;
                else
                    GraphicsManager.VIEW_TRANSFORM = AFFINE_TRANSFORMATION;
                break;
                
            case SKYBOX_ENABLE:
                GraphicsManager.setStatus(GraphicsManager.STATUS_DO_SKYBOX_RENDER);
                break;
                
            case SKYBOX_DISABLE:
                GraphicsManager.clearStatus(GraphicsManager.STATUS_DO_SKYBOX_RENDER);
                break;
                
            case SKYBOX_SET_TEXTURE:
                if (GraphicsManager.SELECTED_TEXTURE == null || GraphicsManager.SELECTED_TEXTURE.unit != GL13.GL_TEXTURE0 || !(GraphicsManager.SELECTED_TEXTURE instanceof GLTextureCubemap))
                    o = null;
                else
                    GraphicsManager.SKYBOX.tex = (GLTextureCubemap)GraphicsManager.SELECTED_TEXTURE;
                break;
                
            case MESH_NEW:
                VECTOR3_ARR_0 = (Vector3[])args[0];
                VECTOR3_ARR_1 = (Vector3[])args[1];
                VECTOR3_ARR_2 = (Vector3[])args[2];
                INT_ARR = (int[])args[3];
                
                if (VECTOR3_ARR_0 == null || VECTOR3_ARR_1 == null || INT_ARR == null)
                    o = null;
                else {
                    GLMesh mesh = new GLMesh(VECTOR3_ARR_0, VECTOR3_ARR_1, VECTOR3_ARR_2, INT_ARR);
                    if (!GLMesh.REGISTRY.isRegistered(mesh))
                        mesh.init();

                    o = GLMesh.REGISTRY.register(mesh);
                    GraphicsManager.SELECTED_MESH = mesh;
                }
                break;

            case MESH_SELECT:
                INT = (int)args[0];
                
                GraphicsManager.SELECTED_MESH = GLMesh.REGISTRY.get(INT);
                break;
                
            case MESH_SELECT_QUAD:
                GraphicsManager.SELECTED_MESH = GLMesh.DEFAULT_QUAD;
                o = GLMesh.REGISTRY.get(GLMesh.DEFAULT_QUAD);
                break;
                
            case MESH_SELECT_CUBE:
                GraphicsManager.SELECTED_MESH = GLMesh.DEFAULT_CUBE;
                o = GLMesh.REGISTRY.get(GLMesh.DEFAULT_CUBE);
                break;
                
            case MESH_SELECT_INV_CUBE:
                GraphicsManager.SELECTED_MESH = GLMesh.DEFAULT_INV_CUBE;
                o = GLMesh.REGISTRY.get(GLMesh.DEFAULT_INV_CUBE);
                break;

            case MESH_REBUFFER:
                if (GraphicsManager.SELECTED_MESH == null || GraphicsManager.SELECTED_MESH.isReadonly())
                    o = null;
                else
                    GraphicsManager.SELECTED_MESH.setRebuffer();
                break;

            case MESH_CLOSE:
                if (GraphicsManager.SELECTED_MESH == null || GLMesh.getDefaults().contains(GraphicsManager.SELECTED_MESH))
                    o = null;
                else {
                    GLMesh.REGISTRY.deregister(GraphicsManager.SELECTED_MESH);
                    GraphicsManager.SELECTED_MESH = null;
                }
                break;

            case MESH_SET_ATTRIBUTES:
                VECTOR3_ARR_0 = (Vector3[])args[0];
                VECTOR3_ARR_1 = (Vector3[])args[1];
                VECTOR3_ARR_2 = (Vector3[])args[2];
                INT_ARR = (int[])args[3];
                
                if (GraphicsManager.SELECTED_MESH == null || GraphicsManager.SELECTED_MESH.isReadonly())
                    o = null;
                else {
                    if (VECTOR3_ARR_0 != null) GraphicsManager.SELECTED_MESH.setPositions(VECTOR3_ARR_0);
                    if (VECTOR3_ARR_1 != null) GraphicsManager.SELECTED_MESH.setUVs(VECTOR3_ARR_1);
                    if (VECTOR3_ARR_2 != null) GraphicsManager.SELECTED_MESH.setNormals(VECTOR3_ARR_2);
                    if (INT_ARR != null) GraphicsManager.SELECTED_MESH.setTriangles(INT_ARR);
                }
                break;
                
            case MESH_RECALC_NORMALS:
                if (GraphicsManager.SELECTED_MESH == null || GraphicsManager.SELECTED_MESH.isReadonly())
                    o = null;
                else
                    GraphicsManager.SELECTED_MESH.recalculateNormals();
                break;
                
            case MESH_SET_READONLY:
                if (GraphicsManager.SELECTED_MESH == null)
                    o = null;
                else
                    GraphicsManager.SELECTED_MESH.setAsReadonly();
                break;
                
            case MESH_GET_ID:
                if (GraphicsManager.SELECTED_MESH == null)
                    o = null;
                else
                    o = GLMesh.REGISTRY.get(GraphicsManager.SELECTED_MESH);
                break;
                
            case SHADER_NEW_VERT:
                IDENTITY_0 = (Identity)args[0];
                
                if (IDENTITY_0 == null)
                    o = null;
                else {
                    GLShader vShader = new GLShader(IDENTITY_0, GL20.GL_VERTEX_SHADER);
                    if (!GLShader.REGISTRY.isRegistered(vShader))
                        vShader.init();

                    o = GLShader.REGISTRY.register(vShader);
                    GraphicsManager.SELECTED_SHADER = vShader;
                }
                break;
                
            case SHADER_NEW_FRAG:
                IDENTITY_0 = (Identity)args[0];
                
                if (IDENTITY_0 == null)
                    o = null;
                else {
                    GLShader fShader = new GLShader(IDENTITY_0, GL20.GL_FRAGMENT_SHADER);
                    if (!GLShader.REGISTRY.isRegistered(fShader))
                        fShader.init();

                    o = GLShader.REGISTRY.register(fShader);
                    GraphicsManager.SELECTED_SHADER = fShader;
                }
                break;
                
            case SHADER_NEW_GEOM:
                IDENTITY_0 = (Identity)args[0];
                
                if (IDENTITY_0 == null)
                    o = null;
                else {
                    GLShader gShader = new GLShader(IDENTITY_0, GL32.GL_GEOMETRY_SHADER);
                    if (!GLShader.REGISTRY.isRegistered(gShader))
                        gShader.init();

                    o = GLShader.REGISTRY.register(gShader);
                    GraphicsManager.SELECTED_SHADER = gShader;
                }
                break;
                
            case SHADER_SELECT:
                INT = (int)args[0];
                GraphicsManager.SELECTED_SHADER = GLShader.REGISTRY.get(INT);
                break;
                
            case SHADER_SELECT_DEFAULT_VERT_2D:
                GraphicsManager.SELECTED_SHADER = GLShader.DEFAULT_VERT_2D;
                o = GLShader.REGISTRY.get(GLShader.DEFAULT_VERT_2D);
                break;
                
            case SHADER_SELECT_DEFAULT_FRAG_2D:
                GraphicsManager.SELECTED_SHADER = GLShader.DEFAULT_FRAG_2D;
                o = GLShader.REGISTRY.get(GLShader.DEFAULT_FRAG_2D);
                break;
                
            case SHADER_SELECT_DEFAULT_VERT_CUBE:
                GraphicsManager.SELECTED_SHADER = GLShader.DEFAULT_VERT_CUBE;
                o = GLShader.REGISTRY.get(GLShader.DEFAULT_VERT_CUBE);
                break;
                
            case SHADER_SELECT_DEFAULT_FRAG_CUBE:
                GraphicsManager.SELECTED_SHADER = GLShader.DEFAULT_FRAG_CUBE;
                o = GLShader.REGISTRY.get(GLShader.DEFAULT_FRAG_CUBE);
                break;
                
            case SHADER_SELECT_DEFAULT_VERT_UI:
                GraphicsManager.SELECTED_SHADER = GLShader.DEFAULT_VERT_UI;
                o = GLShader.REGISTRY.get(GLShader.DEFAULT_VERT_UI);
                break;
                
            case SHADER_SELECT_DEFAULT_FRAG_UI:
                GraphicsManager.SELECTED_SHADER = GLShader.DEFAULT_FRAG_UI;
                o = GLShader.REGISTRY.get(GLShader.DEFAULT_FRAG_UI);
                break;
                
            case SHADER_CLOSE:
                if (GraphicsManager.SELECTED_SHADER == null || GLShader.getDefaults().contains(GraphicsManager.SELECTED_SHADER))
                    o = null;
                else {
                    GLShader.REGISTRY.deregister(GraphicsManager.SELECTED_SHADER);
                    GraphicsManager.SELECTED_SHADER.close();
                    GraphicsManager.SELECTED_SHADER = null;
                }
                break;
                
            case SHADER_GET_ID:
                if (GraphicsManager.SELECTED_SHADER == null)
                    o = null;
                else
                    o = GLShader.REGISTRY.get(GraphicsManager.SELECTED_SHADER);
                break;
                
            case SHADER_PROGRAM_NEW:
                BOOLEAN = (boolean)args[0];
                
                GLShaderProgram prog = new GLShaderProgram(BOOLEAN);
                if (!GLShaderProgram.REGISTRY.isRegistered(prog))
                    prog.init();
                
                o = GLShaderProgram.REGISTRY.register(prog);
                GraphicsManager.SELECTED_PROGRAM = prog;
                break;
                
            case SHADER_PROGRAM_SELECT:
                INT = (int)args[0];
                
                GraphicsManager.SELECTED_PROGRAM = GLShaderProgram.REGISTRY.get(INT);
                break;
                
            case SHADER_PROGRAM_SELECT_DEFAULT_2D:
                GraphicsManager.SELECTED_PROGRAM = GLShaderProgram.DEFAULT_2D;
                o = GLShaderProgram.REGISTRY.get(GLShaderProgram.DEFAULT_2D);
                break;
                
            case SHADER_PROGRAM_SELECT_DEFAULT_CUBE:
                GraphicsManager.SELECTED_PROGRAM = GLShaderProgram.DEFAULT_CUBE;
                o = GLShaderProgram.REGISTRY.get(GLShaderProgram.DEFAULT_CUBE);
                break;
                
            case SHADER_PROGRAM_SELECT_DEFAULT_UI:
                GraphicsManager.SELECTED_PROGRAM = GLShaderProgram.DEFAULT_UI;
                o = GLShaderProgram.REGISTRY.get(GLShaderProgram.DEFAULT_UI);
                break;
                
            case SHADER_PROGRAM_CLOSE:
                if (GraphicsManager.SELECTED_PROGRAM == null || GLShaderProgram.getDefaults().contains(GraphicsManager.SELECTED_PROGRAM))
                    o = null;
                else {
                    GLShaderProgram.REGISTRY.deregister(GraphicsManager.SELECTED_PROGRAM);
                    GraphicsManager.SELECTED_PROGRAM.close();
                    GraphicsManager.SELECTED_PROGRAM = null;
                }
                break;
                
            case SHADER_PROGRAM_SET_READONLY:
                if (GraphicsManager.SELECTED_PROGRAM == null)
                    o = null;
                else
                    GraphicsManager.SELECTED_PROGRAM.setAsReadonly();
                break;
                
            case SHADER_PROGRAM_ATTACH:
                if (GraphicsManager.SELECTED_PROGRAM == null || GraphicsManager.SELECTED_PROGRAM.isReadonly() || GraphicsManager.SELECTED_SHADER == null)
                    o = null;
                else
                    GraphicsManager.SELECTED_PROGRAM.attachShader(GraphicsManager.SELECTED_SHADER);
                break;
                
            case SHADER_PROGRAM_GET_ID:
                if (GraphicsManager.SELECTED_PROGRAM == null)
                    o = null;
                else
                    o = GLShaderProgram.REGISTRY.get(GraphicsManager.SELECTED_PROGRAM);
                break;
                
            case TEXTURE_ID_NEW_2D:
                IDENTITY_0 = (Identity)args[0];
                INT = (int)args[1];
                
                GLTexture2D texID2D = new GLTexture2D(IDENTITY_0, INT);
                if (!GLTexture.REGISTRY.isRegistered(texID2D))
                    texID2D.init();
                
                o = GLTexture.REGISTRY.register(texID2D);
                GraphicsManager.SELECTED_TEXTURE = texID2D;
                break;
                
            case TEXTURE_IM_NEW_2D:
                BUFFERED_IMAGE_0 = (BufferedImage)args[0];
                INT = (int)args[1];
                
                GLTexture2D texIm2D = new GLTexture2D(BUFFERED_IMAGE_0, INT);
                o = GLTexture.REGISTRY.register(texIm2D);
                GraphicsManager.SELECTED_TEXTURE = texIm2D;
                break;
            
            case TEXTURE_ID_NEW_CUBE:
                IDENTITY_0 = (Identity)args[0];
                INT = (int)args[1];
                
                GLTextureCubemap texIDCube = new GLTextureCubemap(IDENTITY_0, INT);
                if (!GLTexture.REGISTRY.isRegistered(texIDCube))
                    texIDCube.init();
                
                o = GLTexture.REGISTRY.register(texIDCube);
                GraphicsManager.SELECTED_TEXTURE = texIDCube;
                break;
            
            case TEXTURE_ID6_NEW_CUBE:
                IDENTITY_0 = (Identity)args[0];
                IDENTITY_1 = (Identity)args[1];
                IDENTITY_2 = (Identity)args[2];
                IDENTITY_3 = (Identity)args[3];
                IDENTITY_4 = (Identity)args[4];
                IDENTITY_5 = (Identity)args[5];
                INT = (int)args[6];
                
                GLTextureCubemap texID6Cube = new GLTextureCubemap(IDENTITY_0, IDENTITY_1, IDENTITY_2, IDENTITY_3, IDENTITY_4, IDENTITY_5, INT);
                if (!GLTexture.REGISTRY.isRegistered(texID6Cube))
                    texID6Cube.init();
                
                o = GLTexture.REGISTRY.register(texID6Cube);
                GraphicsManager.SELECTED_TEXTURE = texID6Cube;
                break;
            
            case TEXTURE_IM_NEW_CUBE:
                BUFFERED_IMAGE_0 = (BufferedImage)args[0];
                INT = (int)args[1];
                
                GLTextureCubemap texImCube = new GLTextureCubemap(BUFFERED_IMAGE_0, INT);
                o = GLTexture.REGISTRY.register(texImCube);
                GraphicsManager.SELECTED_TEXTURE = texImCube;
                break;
                
            case TEXTURE_IM6_NEW_CUBE:
                BUFFERED_IMAGE_0 = (BufferedImage)args[0];
                BUFFERED_IMAGE_1 = (BufferedImage)args[1];
                BUFFERED_IMAGE_2 = (BufferedImage)args[2];
                BUFFERED_IMAGE_3 = (BufferedImage)args[3];
                BUFFERED_IMAGE_4 = (BufferedImage)args[4];
                BUFFERED_IMAGE_5 = (BufferedImage)args[5];
                INT = (int)args[6];
                
                GLTextureCubemap texIm6Cube = new GLTextureCubemap(BUFFERED_IMAGE_0, BUFFERED_IMAGE_1, BUFFERED_IMAGE_2, BUFFERED_IMAGE_3, BUFFERED_IMAGE_4, BUFFERED_IMAGE_5, INT);
                if (!GLTexture.REGISTRY.isRegistered(texIm6Cube))
                    texIm6Cube.init();
                
                o = GLTexture.REGISTRY.register(texIm6Cube);
                GraphicsManager.SELECTED_TEXTURE = texIm6Cube;
                break;
                
            case TEXTURE_SELECT:
                INT = (int)args[0];
                
                GraphicsManager.SELECTED_TEXTURE = GLTexture.REGISTRY.get(INT);
                break;
                
            case TEXTURE_SELECT_DEFAULT_2D:
                GraphicsManager.SELECTED_TEXTURE = GLTexture2D.DEFAULT;
                o = GLTexture.REGISTRY.get(GLTexture2D.DEFAULT);
                break;
            
            case TEXTURE_SELECT_DEFAULT_CUBE:
                GraphicsManager.SELECTED_TEXTURE = GLTextureCubemap.DEFAULT;
                o = GLTexture.REGISTRY.get(GLTextureCubemap.DEFAULT);
                break;
            
            case TEXTURE_CLOSE:
                if (GraphicsManager.SELECTED_TEXTURE == null || GLTexture2D.getDefaults().contains(GraphicsManager.SELECTED_TEXTURE) || GLTextureCubemap.getDefaults().contains(GraphicsManager.SELECTED_TEXTURE))
                    o = null;
                else {
                    GLTexture.REGISTRY.deregister(GraphicsManager.SELECTED_TEXTURE);
                    GraphicsManager.SELECTED_TEXTURE.close();
                    GraphicsManager.SELECTED_TEXTURE = null;
                }
                break;
            
            case TEXTURE_GET_ID:
                if (GraphicsManager.SELECTED_TEXTURE == null)
                    o = null;
                else
                    o = GLTexture.REGISTRY.get(GraphicsManager.SELECTED_TEXTURE);
                break;

            case RENDERABLE_NEW:
                INT = (int)args[0];
                
                if (INT < 1)
                    o = null;
                else {
                    GLRenderable renderable = new GLRenderable(INT);
                    o = GLRenderable.REGISTRY.register(renderable);
                    GraphicsManager.SELECTED_RENDERABLE = renderable;
                }
                break;
                
            case RENDERABLE_NEW_TRANSFORMS:
                AFFINE_TRANSFORMATION_ARR = (AffineTransformation[])args[0];
                
                if (AFFINE_TRANSFORMATION_ARR == null || AFFINE_TRANSFORMATION_ARR.length == 0)
                    o = null;
                else {
                    GLRenderable renderable = new GLRenderable(AFFINE_TRANSFORMATION_ARR);
                    o = GLRenderable.REGISTRY.register(renderable);
                    GraphicsManager.SELECTED_RENDERABLE = renderable;
                }
                break;

            case RENDERABLE_SELECT:
                INT = (int)args[0];
                GraphicsManager.SELECTED_RENDERABLE = GLRenderable.REGISTRY.get(INT);
                break;

            case RENDERABLE_CLOSE:
                if (GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else {
                    GLRenderable.REGISTRY.deregister(GraphicsManager.SELECTED_RENDERABLE);
                    GraphicsManager.removeRenderingMap(GraphicsManager.SELECTED_RENDERABLE);
                    GraphicsManager.SELECTED_RENDERABLE = null;
                }
                break;

            case RENDERABLE_ATTACH_MESH:
                if (GraphicsManager.SELECTED_RENDERABLE == null || GraphicsManager.SELECTED_MESH == null)
                    o = null;
                else
                    GraphicsManager.SELECTED_RENDERABLE.mesh = GraphicsManager.SELECTED_MESH;
                break;
                
            case RENDERABLE_ATTACH_TEXTURE:
                if (GraphicsManager.SELECTED_RENDERABLE == null || GraphicsManager.SELECTED_TEXTURE == null || (GraphicsManager.SELECTED_RENDERABLE.program != null && !GraphicsManager.SELECTED_RENDERABLE.program.isCompatible(GraphicsManager.SELECTED_TEXTURE)))
                    o = null;
                else {
                    int index = GraphicsManager.SELECTED_TEXTURE.unit - GL13.GL_TEXTURE0;
                    GraphicsManager.SELECTED_RENDERABLE.textures[index] = GraphicsManager.SELECTED_TEXTURE;
                }
                break;
                
            case RENDERABLE_DETACH_TEXTURE:
                if (GraphicsManager.SELECTED_RENDERABLE == null || GraphicsManager.SELECTED_TEXTURE == null)
                    o = null;
                else {
                    int index = GraphicsManager.SELECTED_TEXTURE.unit - GL13.GL_TEXTURE0;
                    if (GraphicsManager.SELECTED_RENDERABLE.textures[index] == GraphicsManager.SELECTED_TEXTURE)
                        GraphicsManager.SELECTED_RENDERABLE.textures[index] = null;
                    else
                        o = null;
                }
                break;
                
            case RENDERABLE_DETACH_TEXTURE_UNIT:
                INT = (int)args[0];
                if (GraphicsManager.SELECTED_RENDERABLE == null || INT < 0 || INT > GraphicsManager.MAX_COMBINED_TEXTURE_UNITS)
                    o = null;
                else
                    GraphicsManager.SELECTED_RENDERABLE.textures[INT] = null;
                break;
                
            case RENDERABLE_SET_INSTANCES:
                INT = (int)args[0];
                
                if (GraphicsManager.SELECTED_RENDERABLE == null || INT < 1)
                    o = null;
                else {
                    if (INT == GraphicsManager.SELECTED_RENDERABLE.instanceTransforms.length)
                        break;
                    
                    AffineTransformation[] newTransforms = new AffineTransformation[INT];
                    
                    int copied = Math.min(GraphicsManager.SELECTED_RENDERABLE.instanceTransforms.length, newTransforms.length);
                    System.arraycopy(GraphicsManager.SELECTED_RENDERABLE.instanceTransforms, 0, newTransforms, 0, copied);
                    for (int i = copied; i < newTransforms.length; ++i)
                        newTransforms[i] = AffineTransformationImpl.IDENTITY;
                    
                    GraphicsManager.SELECTED_RENDERABLE.instanceTransforms = newTransforms;
                    GraphicsManager.SELECTED_RENDERABLE.instanceNumDirty = true;
                }
                break;
                
            case RENDERABLE_SET_INSTANCE_TRANSFORMS:
                AFFINE_TRANSFORMATION_ARR = (AffineTransformation[])args[0];
                
                if (GraphicsManager.SELECTED_RENDERABLE == null || AFFINE_TRANSFORMATION_ARR == null || AFFINE_TRANSFORMATION_ARR.length == 0)
                    o = null;
                else {
                    AffineTransformation[] newTransforms = new AffineTransformation[AFFINE_TRANSFORMATION_ARR.length];
                    
                    for (int i = 0; i < AFFINE_TRANSFORMATION_ARR.length; ++i)
                        newTransforms[i] = AFFINE_TRANSFORMATION_ARR[i].getAsUnmodifiable(true);
                    
                    GraphicsManager.SELECTED_RENDERABLE.instanceTransforms = newTransforms;
                    GraphicsManager.SELECTED_RENDERABLE.instanceNumDirty = true;
                }
                break;
                
            case RENDERABLE_SET_INSTANCE_TRANSFORM:
                AFFINE_TRANSFORMATION = (AffineTransformation)args[0];
                INT = (int)args[1];
                
                if (GraphicsManager.SELECTED_RENDERABLE == null || AFFINE_TRANSFORMATION == null || INT < 0 || INT >= GraphicsManager.SELECTED_RENDERABLE.instanceTransforms.length)
                    o = null;
                else
                    GraphicsManager.SELECTED_RENDERABLE.instanceTransforms[INT] = AFFINE_TRANSFORMATION.getAsUnmodifiable(true);
                break;
                
            case RENDERABLE_SET_SKELETON:
                AFFINE_TRANSFORMATION_TREE = (Tree<AffineTransformation, ?>)args[0];
                MATRIX = (Matrix)args[1];
                
                if (GraphicsManager.SELECTED_RENDERABLE == null || AFFINE_TRANSFORMATION_TREE == null || MATRIX == null)
                    o = null;
                else {
                    GraphicsManager.SELECTED_RENDERABLE.skeleton = AFFINE_TRANSFORMATION_TREE;
                    GraphicsManager.SELECTED_RENDERABLE.weights = new Matrix(MATRIX);
                    GraphicsManager.SELECTED_RENDERABLE.weights.readonly();
                }
                break;
                
            case RENDERABLE_REMOVE_SKELETON:
                if (GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else {
                    GraphicsManager.SELECTED_RENDERABLE.skeleton = null;
                    GraphicsManager.SELECTED_RENDERABLE.weights = null;
                }
                break;
                
            case RENDERABLE_SELECT_MESH:
                if (GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else {
                    GraphicsManager.SELECTED_MESH = GraphicsManager.SELECTED_RENDERABLE.mesh;
                    o = GLMesh.REGISTRY.getOrDefault(GraphicsManager.SELECTED_MESH, -1);
                }
                break;
                
            case RENDERABLE_SELECT_TEXTURE:
                INT = (int)args[0];
                
                if (GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else {
                    GraphicsManager.SELECTED_TEXTURE = GraphicsManager.SELECTED_RENDERABLE.textures[INT];
                    o = GLTexture2D.REGISTRY.getOrDefault(GraphicsManager.SELECTED_TEXTURE, -1);
                }
                break;
                
            case RENDERABLE_SELECT_SHADER_PROGRAM:
                if (GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else {
                    GraphicsManager.SELECTED_PROGRAM = GraphicsManager.SELECTED_RENDERABLE.program;
                    o = GLShaderProgram.REGISTRY.getOrDefault(GraphicsManager.SELECTED_PROGRAM, -1);
                }
                break;
                
            case RENDERABLE_COPY:
                if (GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else {
                    GLRenderable r = new GLRenderable(GraphicsManager.SELECTED_RENDERABLE);
                    GraphicsManager.applyRenderingMap(GraphicsManager.SELECTED_RENDERABLE.program, r);
                    o = GLRenderable.REGISTRY.register(r);
                    GraphicsManager.SELECTED_RENDERABLE = r;
                }
                break;
                
            case RENDERABLE_GET_ID:
                if (GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else
                    o = GLRenderable.REGISTRY.get(GraphicsManager.SELECTED_RENDERABLE);
                break;
                
            case SHADER_PROGRAM_ASSIGN_RENDERABLE:
                if (GraphicsManager.SELECTED_PROGRAM == null || GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else {
                    GraphicsManager.removeRenderingMap(GraphicsManager.SELECTED_RENDERABLE);
                    GraphicsManager.UI_RENDERABLES.remove(GraphicsManager.SELECTED_RENDERABLE);
                    GraphicsManager.applyRenderingMap(GraphicsManager.SELECTED_PROGRAM, GraphicsManager.SELECTED_RENDERABLE);
                }
                break;
                
            case SHADER_PROGRAM_UNASSIGN_RENDERABLE:
                if (GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else
                    GraphicsManager.removeRenderingMap(GraphicsManager.SELECTED_RENDERABLE);
                break;
                
            case UI_ENABLE:
                GraphicsManager.setStatus(GraphicsManager.STATUS_DO_UI_RENDER);
                break;
                
            case UI_DISABLE:
                GraphicsManager.clearStatus(GraphicsManager.STATUS_DO_UI_RENDER);
                break;
                
            case UI_ASSIGN_RENDERABLE:
                if (GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else {
                    GraphicsManager.removeRenderingMap(GraphicsManager.SELECTED_RENDERABLE);
                    GraphicsManager.UI_RENDERABLES.add(GraphicsManager.SELECTED_RENDERABLE);
                }
                break;
                
            case UI_UNASSIGN_RENDERABLE:
                if (GraphicsManager.SELECTED_RENDERABLE == null)
                    o = null;
                else
                    GraphicsManager.UI_RENDERABLES.remove(GraphicsManager.SELECTED_RENDERABLE);
                break;
        }

        if (o == null) future.cancel(false);
        else           future.complete(o);
    }
    
    static enum Type {
        WINDOW_OPTIONS_SET, ENABLE_VSYNC, DISABLE_VSYNC, ENABLE_DEBUGGING,
        DISABLE_DEBUGGING, VIEWPORT_SET_TRANSFORM, SKYBOX_ENABLE,
        SKYBOX_DISABLE, SKYBOX_SET_TEXTURE,
        
        MESH_NEW, MESH_SELECT, MESH_SELECT_QUAD, MESH_SELECT_CUBE,
        MESH_SELECT_INV_CUBE, MESH_REBUFFER, MESH_CLOSE,
        MESH_SET_ATTRIBUTES, MESH_RECALC_NORMALS, MESH_SET_READONLY,
        MESH_GET_ID,
        
        SHADER_NEW_VERT, SHADER_NEW_FRAG, SHADER_NEW_GEOM,
        SHADER_SELECT, SHADER_SELECT_DEFAULT_VERT_2D,
        SHADER_SELECT_DEFAULT_FRAG_2D, SHADER_SELECT_DEFAULT_VERT_CUBE,
        SHADER_SELECT_DEFAULT_FRAG_CUBE, SHADER_SELECT_DEFAULT_VERT_UI,
        SHADER_SELECT_DEFAULT_FRAG_UI, SHADER_CLOSE, SHADER_GET_ID,
        
        SHADER_PROGRAM_NEW, SHADER_PROGRAM_SELECT, 
        SHADER_PROGRAM_SELECT_DEFAULT_2D,
        SHADER_PROGRAM_SELECT_DEFAULT_CUBE,
        SHADER_PROGRAM_SELECT_DEFAULT_UI, SHADER_PROGRAM_CLOSE,
        SHADER_PROGRAM_SET_READONLY, SHADER_PROGRAM_ATTACH,
        SHADER_PROGRAM_GET_ID,
        
        TEXTURE_ID_NEW_2D, TEXTURE_IM_NEW_2D, TEXTURE_ID_NEW_CUBE,
        TEXTURE_ID6_NEW_CUBE, TEXTURE_IM_NEW_CUBE, TEXTURE_IM6_NEW_CUBE,
        TEXTURE_SELECT, TEXTURE_SELECT_DEFAULT_2D, 
        TEXTURE_SELECT_DEFAULT_CUBE, TEXTURE_CLOSE, TEXTURE_GET_ID,
        
        RENDERABLE_NEW, RENDERABLE_NEW_TRANSFORMS, RENDERABLE_SELECT,
        RENDERABLE_CLOSE, RENDERABLE_ATTACH_MESH,
        RENDERABLE_ATTACH_TEXTURE, RENDERABLE_DETACH_TEXTURE,
        RENDERABLE_DETACH_TEXTURE_UNIT, RENDERABLE_SET_INSTANCES,
        RENDERABLE_SET_INSTANCE_TRANSFORMS, RENDERABLE_SET_INSTANCE_TRANSFORM,
        RENDERABLE_SET_SKELETON, RENDERABLE_REMOVE_SKELETON,
        RENDERABLE_SELECT_MESH, RENDERABLE_SELECT_TEXTURE,
        RENDERABLE_SELECT_SHADER_PROGRAM, RENDERABLE_COPY, RENDERABLE_GET_ID,
        
        SHADER_PROGRAM_ASSIGN_RENDERABLE,
        SHADER_PROGRAM_UNASSIGN_RENDERABLE,
        
        UI_ENABLE, UI_DISABLE, UI_ASSIGN_RENDERABLE,
        UI_UNASSIGN_RENDERABLE
    }
}
