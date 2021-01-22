package net.vob.util.logging;

/**
 * An extension of {@link java.util.logging.Level Level}.<p>
 * 
 * The static instances of this class are identical in both level names and their
 * integer values to the standard Java levels, except that they use a custom
 * localization resource name given by {@link LocaleUtils#LEVEL_BUNDLE_NAME}. Thus,
 * use of this class is preferable to the standard {@code Level} class, as the level
 * names of this class will be passed to a resource localization bundle prior to
 * being written to a log.
 * 
 * @author Lyn-Park
 */
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
    
    protected Level(String name, int value) {
        super(name, value, LocaleUtils.LEVEL_BUNDLE_NAME);
    }
}
