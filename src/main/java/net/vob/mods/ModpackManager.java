package net.vob.mods;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import net.vob.VoidOfBlue;
import net.vob.util.logging.Level;
import net.vob.util.logging.LocaleUtils;

/**
 * This class is used to search for, check, and load mods into the program, as
 * well as allowing access to their resources and information. Note that the
 * loading of mods can only occur a maximum of once in the program's lifetime; thus,
 * reloading of mods will require the program to be restarted.
 * 
 * @author Lyn-Park
 */
public final class ModpackManager {
    private ModpackManager() {}
    
    private static final Logger LOG = VoidOfBlue.getLogger(ModpackManager.class);
    
    private static URLClassLoader LOADER = buildClassLoader(new URL[0]);
    private static boolean modsLoaded = false;
    
    /**
     * The full list of mods in the manager. Various points of information can be
     * found here, as loaded from the {@code resources/mod_config.json} file of
     * each mod. Only mods that were successfully loaded via their 
     * {@code mod_config} are found here.
     */
    public static final LinkedHashMap<String, Mod> MODS = new LinkedHashMap<String, Mod>();
    
    /**
     * Loads the mods as found in the mod directory. Alternatively, creates the
     * mod directory if it didn't exist. Note that this function can only be
     * properly called once in the program's lifecycle - attempting to invoke it
     * a second time will result in it returning silently and not altering 
     * anything.<p>
     * 
     * Each mod is expected to be packaged within their own compiled JAR file.
     * In addition, each mod must have a JSON file called {@code mod_config.json},
     * which must be placed within the root {@code resources} folder. The loading
     * algorithm uses this file to load every relevant piece of information
     * regarding the mod into the program, and must include the fields:
     * <ul>
     *  <li>{@code name}: The user-readable name of the mod.</li>
     *  <li>{@code modid}: The unique string id of the mod, for identification 
     *                      purposes.</li>
     *  <li>{@code description}: A descriptive explanation of the mod.</li>
     *  <li>{@code version}: A string representing the version of the mod.</li>
     *  <li>{@code authors}: An array of strings, with each element being the 
     *                        name of an author of the mod.</li>
     *  <li>{@code priority}: An integer value (must be non-negative) signifying 
     *                         the priority of the mod. Once the mods have been 
     *                         pre-loaded, they are sorted according to their 
     *                         priority before actual loading; thus, mods with 
     *                         smaller integer values are loaded first. Mods that 
     *                         have the same priority do not have a well-defined 
     *                         loading order.</li>
     *  <li>{@code entry}: A string value representing the canonical name of the
     *                     class that should be used as the entry point of the mod. 
     *                     This class must have a nullary constructor and 
     *                     implement the 
     *                     {@link net.vob.mods.ModEntrypoint ModEntrypoint} 
     *                     interface to be valid; if it is valid, then the mod is
     *                     loaded by instantiating this class and using the method
     *                     hooks defined in {@code ModEntrypoint} to commence
     *                     loading.</li>
     * </ul>
     */
    public static void loadModFiles() {
        if (modsLoaded) return;
        
        File dir = VoidOfBlue.MOD_DIR_PATH.toFile();
        
        if (!dir.exists()) 
        {
            dir.mkdirs();
            LOG.log(Level.INFO, "ModpackManager.loadModFiles.FolderNotFound", dir);
        }
        else
        {
            List<URL> urls = new ArrayList<>();
            
            LOG.log(Level.INFO, "ModpackManager.loadModFiles.StartDiscovery");
            for (File file : dir.listFiles()) 
            {
                String name = file.getName();
                if (name != null && name.endsWith(".jar")) 
                {
                    try (JarFile jar = new JarFile(file)) 
                    {
                        Enumeration<JarEntry> entries = jar.entries();
                        ClassLoader loader = buildClassLoader(new URL[] { file.toURI().toURL() });
                        JSONObject config = null;
                        ErrorContainer configCheck = new ErrorContainer(-1);
        
                        while (entries.hasMoreElements()) 
                        {
                            JarEntry entry = entries.nextElement();
                            if (entry.getName().equals("mod_config.json")) 
                            {
                                config = new JSONObject(new JSONTokener(new InputStreamReader(jar.getInputStream(entry))));
                                configCheck = checkModConfigJson(config, loader, jar);
                                break;
                            }
                        }
                        
                        switch (configCheck.status) {
                            case ErrorContainer.NO_ERROR:
                                Mod mod = new Mod(loader, jar, config);
                                
                                if (MODS.containsKey(mod.modid)) {
                                    Mod collided = MODS.get(mod.modid);
                                    
                                    int i = 0;
                                    while (MODS.containsKey(mod.modid + "." + i)) 
                                        ++i;
                                    
                                    String newModid = mod.modid + "." + i;
                                    LOG.log(Level.WARNING, "ModpackManager.loadModFiles.ModIDCollision", new Object[] { mod.name, collided.name, newModid });
                                    mod.modid = newModid;
                                }
                                
                                MODS.put(mod.modid, mod);
                                
                                urls.add(file.toURI().toURL());
                                break;
                            case ErrorContainer.CONFIG_NOT_FOUND:
                                LOG.log(Level.WARNING, "ModpackManager.loadModFiles.ConfigNotFound", name);
                                break;
                            case ErrorContainer.ENTRY_MISSING:
                                LOG.log(Level.WARNING, "ModpackManager.loadModFiles.EntryMissing", new Object[] { name, configCheck.args[0] });
                                break;
                            case ErrorContainer.ENTRY_INCORRECT_TYPE:
                                LOG.log(Level.WARNING, "ModpackManager.loadModFiles.EntryIncorrectType", new Object[] { name, configCheck.args[0], configCheck.args[1] });
                                break;
                            case ErrorContainer.ENTRYPOINT_NOT_FOUND:
                                LOG.log(Level.WARNING, "ModpackManager.loadModFiles.EntrypointNotFound", new Object[] { name, configCheck.args[0] });
                                break;
                            case ErrorContainer.ENTRYPOINT_NO_INTERFACE:
                                LOG.log(Level.WARNING, "ModpackManager.loadModFiles.EntrypointNoInterface", new Object[] { name, configCheck.args[0], ModEntrypoint.class.getCanonicalName() });
                                break;
                            default:
                                LOG.log(Level.WARNING, "ModpackManager.loadModFiles.UnknownConfigError", new Object[] { name, configCheck.status, String.join(", ", configCheck.args) });
                        }
                        
                    } catch (JSONException e) {
                        LOG.log(Level.WARNING, LocaleUtils.format("ModpackManager.loadModFiles.JSONException", name), e);
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, LocaleUtils.format("ModpackManager.loadModFiles.IOException", name), e);
                    } catch (InstantiationException | NoSuchMethodException e) {
                        LOG.log(Level.WARNING, LocaleUtils.format("ModpackManager.loadModFiles.NoNullaryConstructor", name), e);
                    } catch (IllegalAccessException e) {
                        LOG.log(Level.WARNING, LocaleUtils.format("ModpackManager.loadModFiles.InaccessbileNullaryConstructor", name), e);
                    } catch (ClassNotFoundException e) {
                        LOG.log(Level.WARNING, LocaleUtils.format("ModpackManager.loadModFiles.ClassNotFoundException", name), e);
                    } catch (InvocationTargetException e) {
                        LOG.log(Level.WARNING, LocaleUtils.format("ModpackManager.loadModFiles.ClassConstructorException", name), e);
                    }
                }
            }
            
            URL[] u = new URL[urls.size()];
            LOADER = buildClassLoader(urls.toArray(u));
            modsLoaded = true;
            
            LOG.log(Level.INFO, "ModpackManager.loadModFiles.ModsDiscovered", urls.size());
            final int[] i = new int[1];
            
            MODS.entrySet()
                .stream()
                .sorted((e1, e2) -> Integer.compare(e1.getValue().priority, e2.getValue().priority))
                .forEachOrdered((entry) -> {
                    Mod mod = entry.getValue();
                    try {
                        mod.entrypoint.mainEntry();
                        ++i[0];
                    } catch (Throwable e) {
                        LOG.log(Level.FINER, LocaleUtils.format("ModpackManager.loadModFiles.ModException", mod.name, mod.modid, mod.version), e);
                    }
            });
            
            LOG.log(Level.INFO, "ModpackManager.loadModFiles.End", i[0]);
        }
    }
    
    private static ErrorContainer checkModConfigJson(JSONObject config, ClassLoader loader, JarFile jar) throws IOException, JSONException {
        if (!config.has(Mod.NAME_KEY))          return new ErrorContainer(ErrorContainer.ENTRY_MISSING, Mod.NAME_KEY);
        if (!config.has(Mod.MODID_KEY))         return new ErrorContainer(ErrorContainer.ENTRY_MISSING, Mod.MODID_KEY);
        if (!config.has(Mod.DESCRIPTION_KEY))   return new ErrorContainer(ErrorContainer.ENTRY_MISSING, Mod.DESCRIPTION_KEY);
        if (!config.has(Mod.VERSION_KEY))       return new ErrorContainer(ErrorContainer.ENTRY_MISSING, Mod.VERSION_KEY);
        if (!config.has(Mod.AUTHORS_KEY))       return new ErrorContainer(ErrorContainer.ENTRY_MISSING, Mod.AUTHORS_KEY);
        if (!config.has(Mod.PRIORITY_KEY))      return new ErrorContainer(ErrorContainer.ENTRY_MISSING, Mod.PRIORITY_KEY);
        if (!config.has(Mod.ENTRYPOINT_KEY))    return new ErrorContainer(ErrorContainer.ENTRY_MISSING, Mod.ENTRYPOINT_KEY);
        
        if (config.optString(Mod.NAME_KEY, null) == null)           return new ErrorContainer(ErrorContainer.ENTRY_INCORRECT_TYPE, Mod.NAME_KEY, LocaleUtils.format("ModpackManager.checkModConfigJSON.StringType"));
        if (config.optString(Mod.MODID_KEY, null) == null)          return new ErrorContainer(ErrorContainer.ENTRY_INCORRECT_TYPE, Mod.MODID_KEY, LocaleUtils.format("ModpackManager.checkModConfigJSON.StringType"));
        if (config.optString(Mod.DESCRIPTION_KEY, null) == null)    return new ErrorContainer(ErrorContainer.ENTRY_INCORRECT_TYPE, Mod.DESCRIPTION_KEY, LocaleUtils.format("ModpackManager.checkModConfigJSON.StringType"));
        if (config.optString(Mod.VERSION_KEY, null) == null)        return new ErrorContainer(ErrorContainer.ENTRY_INCORRECT_TYPE, Mod.VERSION_KEY, LocaleUtils.format("ModpackManager.checkModConfigJSON.StringType"));
        if (config.optJSONArray(Mod.AUTHORS_KEY) == null)           return new ErrorContainer(ErrorContainer.ENTRY_INCORRECT_TYPE, Mod.AUTHORS_KEY, LocaleUtils.format("ModpackManager.checkModConfigJSON.ArrayType"));
        if (config.optInt(Mod.PRIORITY_KEY, -1) < 0)                return new ErrorContainer(ErrorContainer.ENTRY_INCORRECT_TYPE, Mod.PRIORITY_KEY, LocaleUtils.format("ModpackManager.checkModConfigJSON.PriorityType"));
        if (config.optString(Mod.ENTRYPOINT_KEY, null) == null)     return new ErrorContainer(ErrorContainer.ENTRY_INCORRECT_TYPE, Mod.ENTRYPOINT_KEY, LocaleUtils.format("ModpackManager.checkModConfigJSON.StringType"));
        
        JSONArray authArr = config.getJSONArray(Mod.AUTHORS_KEY);
        try {
            for (int i = 0; i < authArr.length(); ++i) 
                authArr.getString(i);
        } catch (JSONException e) {
            return new ErrorContainer(ErrorContainer.ENTRY_INCORRECT_TYPE, Mod.AUTHORS_KEY, LocaleUtils.format("ModpackManager.checkModConfigJSON.StringArrayType"));
        }
        
        try {
            boolean foundEntry = false;
            boolean foundEntrypoint = false;
            String entrypointName = config.getString(Mod.ENTRYPOINT_KEY).replace('.', '/') + ".class";
            
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                
                if (!entry.isDirectory() && entry.getName().endsWith(entrypointName)) {
                    foundEntry = true;
                    
                    Class c = loader.loadClass(entry.getName().replace('/', '.').substring(0, (entry.getName().length() - 6)));
                    if (Arrays.asList(c.getInterfaces()).contains(ModEntrypoint.class))
                        foundEntrypoint = true;
                    
                    break;
                }
            }
            
            if (!foundEntry)
                return new ErrorContainer(ErrorContainer.ENTRYPOINT_NOT_FOUND, config.getString(Mod.ENTRYPOINT_KEY));
            else if (!foundEntrypoint)
                return new ErrorContainer(ErrorContainer.ENTRYPOINT_NO_INTERFACE, config.getString(Mod.ENTRYPOINT_KEY));
            
        } catch (ClassNotFoundException e) {
            return new ErrorContainer(ErrorContainer.ENTRYPOINT_NOT_FOUND, config.getString(Mod.ENTRYPOINT_KEY));
        }
        
        return new ErrorContainer(ErrorContainer.NO_ERROR);
    }
    
    private static class ErrorContainer {
        public static final int CONFIG_NOT_FOUND = -1;
        public static final int NO_ERROR = 0;
        public static final int ENTRY_MISSING = 1;
        public static final int ENTRY_INCORRECT_TYPE = 2;
        public static final int ENTRYPOINT_NOT_FOUND = 3;
        public static final int ENTRYPOINT_NO_INTERFACE = 4;
        
        public final int status;
        public final String[] args;
        
        public ErrorContainer(int status, String... args) {
            this.status = status;
            this.args = args;
        }
    }
    
    private static URLClassLoader buildClassLoader(URL[] urls) {
        return new URLClassLoader(urls, VoidOfBlue.class.getClassLoader());
    }
    
    /**
     * Checks if {@link loadModFiles()} has been invoked yet.
     * @return {@code false} if {@code loadModFiles()} has yet to be invoked,
     * {@code true} otherwise
     */
    public static boolean areModsLoaded() {
        return modsLoaded;
    }
    
    /**
     * Gets the class loader the program is currently using. Note that the returned
     * class loader may be invalidated by a call to {@link loadModFiles()} if it
     * hasn't yet been invoked.
     * @return the {@link URLClassLoader} used to load resources from both the
     * vanilla {@code JAR} file and any mod {@code JAR} files
     */
    public static URLClassLoader getClassLoader() {
        return LOADER;
    }
    
    /**
     * Gets the resources that lie on the given path as URLs. Each returned URL
     * corresponds to a resource in either the main program class loader, or the
     * class loader of a currently loaded mod JAR file.
     * @param path The path of the resource to get
     * @return An enumeration of the URLs of all found matching resources
     * @throws IOException if an I/O exception occurs during the search
     */
    public static Enumeration<URL> getResources(String path) throws IOException {
        return LOADER.getResources(path);
    }
    
    /**
     * Returns an unmodifiable view of the set of loaded modids.
     * @return The set of modids currently loaded into the application
     */
    public static Set<String> getModids() {
        return Collections.unmodifiableSet(MODS.keySet());
    }
    
    /**
     * Returns the mod associated with the given modid. May return {@code null}
     * if such a mod does not exist.
     * @param modid The modid of the mod to get
     * @return The mod with the given modid, or {@code null} if no mod with this
     * modid is loaded
     */
    public static @Nullable Mod getModByID(String modid) {
        return MODS.get(modid);
    }
}
