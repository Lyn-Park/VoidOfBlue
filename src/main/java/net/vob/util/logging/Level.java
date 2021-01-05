package net.vob.util.logging;

@SuppressWarnings("FieldNameHidesFieldInSuperclass")
public class Level extends java.util.logging.Level {
    
    public static final Level OFF = new Level("OFF", Integer.MAX_VALUE);
    public static final Level SEVERE = new Level("SEVERE", 1000);
    public static final Level WARNING = new Level("WARNING", 900);
    public static final Level INFO = new Level("INFO", 800);
    public static final Level CONFIG = new Level("CONFIG", 700);
    public static final Level FINE = new Level("FINE", 500);
    public static final Level FINER = new Level("FINER", 400);
    public static final Level FINEST = new Level("FINEST", 300);
    public static final Level ALL = new Level("ALL", Integer.MIN_VALUE);
    
    public static final Level DEBUG = new Level("DEBUG", 450);
    
    protected Level(String name, int value) {
        super(name, value, LocaleUtils.LEVEL_BUNDLE_NAME);
    }
}
