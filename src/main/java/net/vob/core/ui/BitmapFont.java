package net.vob.core.ui;

import java.util.Arrays;
import net.vob.core.Texture2D;
import net.vob.util.Identity;
import net.vob.util.math.Rectangle;

public class BitmapFont {
    final Texture2D texture;
    final double wSize, hSize, tracking;
    final int maxX, maxY;
    
    public BitmapFont(Identity id, double wSize, double hwRatio, double tracking, int maxX, int maxY) {
        this.texture = new Texture2D(id, 0);
        this.wSize = wSize;
        this.hSize = wSize * hwRatio;
        this.tracking = tracking;
        this.maxX = maxX;
        this.maxY = maxY;
    }
    
    private double getCharXPos(int x) {
        return x * (wSize + tracking);
    }
    
    private double getCharYPos(int y) {
        return y * hSize;
    }
    
    public Rectangle getStringBounds(String text) {
        String[] split = text.split("\\R", -1);
        
        int width = Arrays.stream(split).mapToInt(String::length).reduce(0, Math::max);
        int height = split.length;
        
        return new Rectangle(0, 0, getCharXPos(width), getCharYPos(height));
    }
    
    char checkChar(char charCode) {
        if (charCode < 0 || charCode >= maxX * maxY) {
            // If character code isn't supported by this font...
            
            if (0x1A < maxX * maxY)
                charCode = 0x1A; // Hexadecimal for substitute character (SUB)
            else
                charCode = 0;    // Fallback if this font doesn't support SUB character
        }
        
        return charCode;
    }
    
    Rectangle getCharBounds(int posX, int posY) {
        double x = getCharXPos(posX);
        double y = getCharYPos(posY);
        
        return new Rectangle(x, y, x + wSize, y + hSize);
    }
    
    Rectangle getCharUVS(char charCode) {
        charCode = checkChar(charCode);
        
        int x = charCode % maxX;
        int y = charCode / maxX;

        double u0 = (double)x / (double)maxX;
        double v0 = (double)y / (double)maxY;
        double u1 = (double)(x+1) / (double)maxX;
        double v1 = (double)(y+1) / (double)maxY;
        
        return new Rectangle(u0, v0, u1, v1);
    }
}
