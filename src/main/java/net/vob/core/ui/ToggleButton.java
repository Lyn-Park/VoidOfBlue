package net.vob.core.ui;

import java.util.function.Consumer;
import net.vob.core.Texture2D;
import net.vob.util.Identity;
import net.vob.util.Input;
import net.vob.util.Input.Source;
import net.vob.util.math.Rectangle;
import org.lwjgl.glfw.GLFW;

public final class ToggleButton extends UIElement {
    private static final Texture2D OFF_TEX = new Texture2D(new Identity("toggle_off").partial("ui"), 0);
    private static final Texture2D ON_TEX = new Texture2D(new Identity("toggle_on").partial("ui"), 0);
    
    private boolean state = false;
    private final Consumer<Boolean> func;
    
    public ToggleButton(Rectangle boundingBox, Consumer<Boolean> func) {
        super(boundingBox, OFF_TEX);
        
        this.func = func;
    }
    
    @Override
    protected Result handleInput(Input input) {
        if (!getAbsoluteBoundingBox().contains(input.position))
            return Result.STOP;
        
        if (input.source == Source.MOUSE && input.code == 1 && input.action == GLFW.GLFW_PRESS && input.key == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            state ^= true;
            setTexture(state ? ON_TEX : OFF_TEX);
            func.accept(state);
            
            return Result.COMPLETE;
        }
        
        return Result.PASS;
    }
}
