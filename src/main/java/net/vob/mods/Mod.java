package net.vob.mods;

import java.lang.reflect.InvocationTargetException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.vob.util.logging.LocaleUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Container class for the mod and every point of information it has, as was
 * contained in the {@code mod_config.json} file.
 * 
 * @author Lyn-Park
 */
public final class Mod {
    /** The JSON key to which the name of the mod is assigned in 
     * {@code mod_config.json} */
    public static final String NAME_KEY = "name";
    /** The JSON key to which the modid of the mod is assigned in 
     * {@code mod_config.json} */
    public static final String MODID_KEY = "modid";
    /** The JSON key to which the description of the mod is assigned in 
     * {@code mod_config.json} */
    public static final String DESCRIPTION_KEY = "description";
    /** The JSON key to which the version of the mod is assigned in 
     * {@code mod_config.json} */
    public static final String VERSION_KEY = "version";
    /** The JSON key to which the list of authors of the mod is assigned in 
     * {@code mod_config.json} */
    public static final String AUTHORS_KEY = "authors";
    /** The JSON key to which the load priority of the mod is assigned in 
     * {@code mod_config.json} */
    public static final String PRIORITY_KEY = "priority";
    /** The JSON key to which the name of the entry point class of the mod is 
     * assigned in {@code mod_config.json} */
    public static final String ENTRYPOINT_KEY = "entry";
    
    String name;
    /** 
     * Gets the name of the mod.
     *  @return
     */
    public String getName() {
        return name;
    }
    
    String modid;
    /** 
     * Gets the modid of the mod.
     *  @return
     */
    public String getModid() {
        return modid;
    }
    
    String description;
    /** 
     * Gets the description of the mod.
     *  @return
     */
    public String getDescription() {
        return description;
    }
    
    String version;
    /** 
     * Gets the version of the mod.
     *  @return
     */
    public String getVersion() {
        return version;
    }
    
    String[] authors;
    /** 
     * Gets the authors of the mod.
     *  @return
     */
    public String[] getAuthors() {
        return authors;
    }
    
    int priority;
    /** 
     * Gets the load priority of the mod.
     *  @return
     */
    public int getPriority() {
        return priority;
    }
    
    final ModEntrypoint entrypoint;
    
    Mod(ClassLoader loader, JarFile jar, JSONObject configuration) throws InstantiationException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        name = configuration.getString(NAME_KEY);
        modid = configuration.getString(MODID_KEY);
        description = configuration.getString(DESCRIPTION_KEY);
        version = configuration.getString(VERSION_KEY);
        
        JSONArray auth = configuration.getJSONArray(AUTHORS_KEY);
        authors = new String[auth.length()];
        for (int i = 0; i < auth.length(); ++i)
            authors[i] = auth.optString(i);
        priority = configuration.getInt(PRIORITY_KEY);
        
        String entrypointName = configuration.getString(ENTRYPOINT_KEY).replace('.', '/') + ".class";
        Class c = null;
        
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().endsWith(entrypointName)) {
                c = loader.loadClass(entry.getName().replace('/', '.').substring(0, (entry.getName().length() - 6)));
                break;
            }
        }
        
        entrypoint = (ModEntrypoint)c.getConstructor().newInstance();
    }
    
    @Override
    public String toString() {
        String authList = String.join(", ", authors);
        return LocaleUtils.format("Mod.toString.Format", name, modid, version, priority, description, authList);
    }
}
