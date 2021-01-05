package net.vob.core.ui;

import java.util.HashMap;
import java.util.Map;
import net.vob.core.Mesh;
import net.vob.util.Input;
import net.vob.util.math.Rectangle;
import net.vob.util.math.Vector2;
import net.vob.util.math.Vector3;

public final class BitmapText extends UIElement {

    public BitmapText(String text, BitmapFont font, Vector2 origin) {
        super(initRect(text, font, origin));
        
        int x = 0, y = text.split("\n").length - 1;
        
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                x = 0;
                --y;
                continue;
            }
            
            BitmapChar bmc = new BitmapChar(c, font, origin, x, y);
            bmc.setParent(this);
            
            ++x;
        }
    }
    
    private static Rectangle initRect(String text, BitmapFont font, Vector2 origin) {
        Rectangle rect = font.getStringBounds(text);
        
        rect.offsetX(origin.getX());
        rect.offsetY(origin.getY());
        
        return rect;
    }
    
    @Override
    protected Result handleInput(Input input) {
        return Result.STOP;
    }
    
    private static class BitmapChar extends UIElement {
        private static final Map<Long, Mesh> MESH_MAP = new HashMap<>();
        
        public BitmapChar(char charCode, BitmapFont font, Vector2 initOrigin, int posX, int posY)
        {
            super(initRect(font, initOrigin, posX, posY),
                  initMesh(charCode, font),
                  font.texture);
        }
        
        private static Rectangle initRect(BitmapFont font, Vector2 origin, int posX, int posY) {
            Rectangle rect = font.getCharBounds(posX, posY);
            
            rect.offsetX(origin.getX());
            rect.offsetY(origin.getY());
            
            return rect;
        }
        
        private static Mesh initMesh(char charCode, BitmapFont font) {
            long hash = ((long)font.hashCode() << 16) | font.checkChar(charCode);
            
            Mesh mesh;
            
            if (MESH_MAP.containsKey(hash)) 
                mesh = MESH_MAP.get(hash);
            else
            {
                Rectangle r = font.getCharUVS(charCode);

                mesh = new Mesh(new Vector3[] {
                                    new Vector3(0, 1, 0),
                                    new Vector3(0, 0, 0),
                                    new Vector3(1, 0, 0),
                                    new Vector3(1, 1, 0)
                                 }, new Vector3[] {
                                    new Vector3(r.getLowerX(), r.getLowerY(), 0),
                                    new Vector3(r.getLowerX(), r.getUpperY(), 0),
                                    new Vector3(r.getUpperX(), r.getUpperY(), 0),
                                    new Vector3(r.getUpperX(), r.getLowerY(), 0)
                                 }, null, new int[] {
                                    0, 1, 2, 0, 2, 3
                                 });
                
                MESH_MAP.put(hash, mesh);
            }
            
            return mesh;
        }
        
        @Override
        protected Result handleInput(Input input) {
            return Result.STOP;
        }
    }
}
