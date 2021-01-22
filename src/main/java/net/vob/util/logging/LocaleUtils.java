package net.vob.util.logging;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import net.vob.mods.ModpackManager;

/**
 * Utility class for formatting messages based off of the current locale. It is
 * preferable (but not enforced) that logging messages are logged via
 * {@link format(String, Object...) format(messageKey, args)}, as it is this
 * method that performs the localization.<p>
 * 
 * The localization files are located at {@link LOGGING_BUNDLE_NAME} and
 * {@link LEVEL_BUNDLE_NAME} in the resources folder.
 * 
 * @author Lyn-Park
 */
public final class LocaleUtils {
    private LocaleUtils() {}
    
    /** Name of the resource bundle used for log messages. */
    public static final String LOGGING_BUNDLE_NAME = "data.logging.log.locale";
    /** Name of the resource bundle used for level names. */
    public static final String LEVEL_BUNDLE_NAME = "data.logging.level.locale";
    
    private static ResourceBundle BUNDLE = ResourceBundle.getBundle(LOGGING_BUNDLE_NAME);
    private static boolean reloaded = false;
    
    /**
     * Formats the log message using the given parameters.<p>
     * 
     * The given message key is used to look up the appropriate message string from
     * the {@link ResourceBundle} locale property files located at
     * {@link LOGGING_BUNDLE_NAME}, using the current system locale. The returned
     * message string is then formatted using the given arguments. The formatting
     * method used is {@link MessageFormat#format(String, Object...)}.<p>
     * 
     * Note that calls to any {@link java.util.logging.Logger Logger} logging
     * functions, <i>assuming the {@code Logger} instance was initially sourced from
     * {@link net.vob.VoidOfBlue#getLogger(java.lang.Class) VoidOfBlue.getLogger(..)}</i>,
     * does <b>not</b> require a call to this function, as the {@code Logger}
     * performs the lookup automatically. This function can still be used with a
     * {@code Logger} instance, however.<p>
     * 
     * Also, note that the message key will be used as the message string if the key
     * could not be found in any of the appropriate locale files.
     * 
     * @param messageKey the key for the desired message string
     * @param args the objects to format into the string
     * @return the final formatted string
     */
    public static String format(String messageKey, Object... args) {
        if (!reloaded && ModpackManager.areModsLoaded()) {
            reloaded = true;
            BUNDLE = new MultiPropertyResourceBundle(LOGGING_BUNDLE_NAME);
        }
        
        return MessageFormat.format(BUNDLE.getString(messageKey), args);
    }
    
    private static final class MultiPropertyResourceBundle extends ResourceBundle {
        protected static final Control CONTROL = new MPRBControl();
        private Properties properties = null;
        
        public MultiPropertyResourceBundle(String baseName) {
            setParent(ResourceBundle.getBundle(baseName, CONTROL));
        }
        
        protected MultiPropertyResourceBundle(Properties properties) {
            this.properties = properties;
        }
        
        @Override
        protected Object handleGetObject(String key) {
            return properties == null ? parent.getObject(key) : properties.get(key);
        }

        @Override
        public Enumeration<String> getKeys() {
            return properties == null ? parent.getKeys() : (Enumeration<String>)properties.propertyNames();
        }
        
        private static final class MPRBControl extends Control {
            @Override
            public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                    throws IOException, IllegalAccessException, InstantiationException
            {
                switch (format) {
                    case "java.properties":
                        Properties properties = loadProperties(toBundleName(baseName, locale));
                        if (properties.isEmpty())
                            return null;

                        return new MultiPropertyResourceBundle(properties);
                        
                    default:
                        return super.newBundle(baseName, locale, format, loader, reload);
                }
            }
            
            private Properties loadProperties(String baseName) throws IOException {
                Properties properties = new Properties();
                Enumeration<URL> urls = ModpackManager.getResources(baseName + ".properties");
                
                while (urls.hasMoreElements()) {
                    try (InputStream stream = urls.nextElement().openStream()) {
                        properties.load(stream);
                    }
                }
                
                return properties;
            }
        }
    }
}
