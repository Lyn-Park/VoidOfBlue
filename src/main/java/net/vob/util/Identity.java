package net.vob.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.vob.VoidOfBlue;
import net.vob.util.logging.Level;
import net.vob.mods.ModManager;
import net.vob.util.logging.LocaleUtils;

/**
 * Class for identifying a set of resource files.<p>
 * 
 * An identity holds 2 string parameters; the superid and id. Each identity can
 * then be used to reference any files with the format:
 * <blockquote>
 *  {@code resources/<superid>/.../path/to/file/<id>}
 * </blockquote>
 * The identity is queried at runtime with the names of the intervening folders,
 * allowing one identity to reference multiple files.<p>
 * 
 * For example, querying the file of an {@code Identity("main", "data.json")} with
 * the folder names {@code "asset", "tile"} will return a file pointing to
 * {@code resources/main/asset/tile/data.json}.<p>
 * 
 * <b>Note</b>: the superid and id strings are converted to lowercase prior to
 * being stored. This means <i>a resource file must have a strictly lowercase
 * name</i> in order to allow an identity to reference it.
 * 
 * @author Lyn-Park
 */
public class Identity {
    private static final Logger LOG = VoidOfBlue.getLogger(Identity.class);
    
    /**
     * The system-independent separator character for resource paths. This is
     * initialized to {@code '/'} to conform to the requirements imposed by
     * {@link ClassLoader#getResources(String)} and related functions.
     */
    public static final char resourceSeparatorChar = '/';
    /**
     * The system-independent separator character for resource paths represented
     * as a string for convenience. This string contains a single character, namely,
     * {@code resourceSeparatorChar}.
     */
    public static final String resourceSeparator = "/";
    
    protected final String superID;
    protected final String id;
    
    private Identity(String[] ids) {
        superID = ids[0] == null ? "vob" : ids[0].toLowerCase();
        id = ids[1].toLowerCase();
        
        if (ids[1] == null || ids[1].isEmpty())
            throw new IllegalArgumentException(LocaleUtils.format("Identity._cinit_.EmptyID"));
    }
    
    /**
     * Constructs an identity with the given superid and id strings.
     * @param superID the superid of the identity
     * @param id the id of the identity
     * @throws IllegalArgumentException if the id string is null or empty
     */
    public Identity(String superID, String id) {
        this(new String[] { superID, id });
    }
    
    /**
     * Constructs an identity by splitting the given string using the regex 
     * {@code ":"}. Thus, {@code new Identity("super:id")} is equivalent to 
     * {@code new Identity("super", "id")}.
     * @param id the id string of the identity
     * @throws IllegalArgumentException if the id string is null or empty
     */
    public Identity(String id) {
        this(splitID(id));
    }
    
    private static String[] splitID(String id) {
        String[] split = id.split(":", 2);
        
        if (split.length == 1)
            return new String[] { null, id };
        
        return split;
    }
    
    /**
     * Returns a <i>partial</i> version of this identity.<p>
     * 
     * A <i>partial identity</i> is defined to be a regular identity, but with an
     * additional sequence of folders attached to it. When the identity is queried
     * for a file, this sequence of folders is prepended to the folder sequence given
     * by the query; thus, the folders passed to this method can be described as a
     * <i>partial path</i> which is completed with the additional information given
     * by future queries. The default implementation is thus:
     * 
     * <blockquote><pre>
     *      {@code new }{@link Identity(String, String) Identity}{@code ("super", "id").partial("extra", "path").}{@link getPath(String, String...) getPath}{@code ("ext", "to", "file") ==
     *              "super/extra/path/to/file/id.ext"}
     * </pre></blockquote>
     * 
     * Let {@code id} be an {@code Identity}, and let {@code ==} denote equal
     * identities based on the path they produce. Then any implementation of this
     * method must abide by the following rules:
     * <ul>
     *  <li>Composition: {@code id.partial(a).partial(b) == id.partial(a, b)}</li>
     *  <li>Identity: {@code id.partial("") == id.partial(null) == id}</li>
     * </ul>
     * Note that passing {@code null} returns this instance; any other parameter,
     * including the empty string {@code ""}, returns a new instance.
     * 
     * @param folders the partial sequence of folders to assign to the partial 
     * identity
     * @return the partial identity
     */
    public Identity partial(String... folders) {
        if (folders == null)
            return this;
        
        return new PartialIdentity(this, folders);
    }
    
    /**
     * Reverts the identity. This discards all partial folders the identity was given
     * via {@link partial(String...)}, leaving it as it was when the original instance
     * was first constructed.<p>
     * 
     * Note that calling {@code revert()} on the original instance will return that
     * instance; calling it on any other instance as constructed by 
     * {@code partial(String...)} will return a new instance that is equivalent to the
     * original instance.
     * 
     * @return 
     */
    public Identity revert() {
        return this;
    }
    
    /**
     * Returns the string path this identity points to, including the file and it's
     * extension. Subclasses can override this to change the overall behaviour of the
     * identity, as the returned value is used whenever the identity attempts to get
     * a resource.
     * 
     * @param extension the extension the file uses, not including the preceding 
     * {@code '.'} character
     * @param folders the sequence of folders this identity is querying
     * @return the path to the appropriate file
     */
    protected String getPath(String extension, String... folders) {
        String path = superID;
        
        for (String folder : folders)
            if (folder != null && !folder.isEmpty())
                path += resourceSeparator + folder;
        
        path = path + resourceSeparator + id;
        if (extension != null && !extension.isEmpty()) 
            path += "." + extension;
        return path;
    }
    
    /**
     * Gets the URLs this identity points to, by querying the main class loader 
     * and the mod class loaders using the path:
     * <blockquote>
     *  {@code <superid>/.../path/to/file/<id>.<extension>}
     * </blockquote>
     * ...where the intervening folders are the given folder name parameters, in
     * order. The extension isn't included in the path if it is null or empty, and
     * similarly for any null or empty folder names. Note that this path is altered
     * according to the current implementation of {@link partial(String...)}, if
     * it was invoked prior to this method.<p>
     * 
     * @param extension The extension to use for the files
     * @param folders The folder names to use for the path
     * @return An {@code Iterable} holding all the resource URLs that were
     * successfully loaded, or {@code null} if the resource URLs could not be
     * loaded
     */
    public final @Nullable Iterable<URL> getAsURLs(String extension, String... folders) {
        String path = getPath(extension, folders);
        Enumeration<URL> urls;
        
        try {
            urls = ModManager.getResources(path);
        } catch (IOException e) {
            LOG.log(Level.FINER, LocaleUtils.format("Identity.ResourceURLException", path), e);
            return null;
        }
        
        List<URL> urlsList = new ArrayList<>();
        
        while (urls.hasMoreElements())
            urlsList.add(urls.nextElement());
        
        return urlsList;
    }
    
    /**
     * Convenience method, returns only the first URL in the returned iterable
     * from {@link #getAsURLs(String, String...) getAsURLs(extension, folders)}.
     * In general, this means discarding any mod resources, and returning a URL
     * from a vanilla resource if one exists.<p>
     * 
     * Note that this method returns the first URL that <i>was successfully
     * loaded</i>, so even if a vanilla resource exists, it isn't necessarily a
     * guarantee that such a URL is the one returned.
     * 
     * @param extension The extension to use for the files
     * @param folders The folder names to use for the path
     * @return The first URL found on the path, or {@code null} if no URL could
     * be loaded
     */
    public final @Nullable URL getFirstURL(String extension, String... folders) {
        Iterable<URL> urls = getAsURLs(extension, folders);
        if (urls == null)
            return null;
        
        Iterator<URL> urlItr = urls.iterator();
        if (!urlItr.hasNext())
            return null;
        
        return urlItr.next();
    }
    
    /**
     * Convenience method, returns only the last URL in the returned iterable
     * from {@link #getAsURLs(String, String...) getAsURLs(extension, folders)}.
     * In general, this means discarding any vanilla resources, and returning a 
     * URL from a mod if one exists.<p>
     * 
     * Note that this method returns the last URL that <i>was successfully
     * loaded</i>, so even if a mod resource exists, it isn't necessarily a 
     * guarantee that such a URL is the one returned.
     * 
     * @param extension The extension to use for the files
     * @param folders The folder names to use for the path
     * @return The last URL found on the path, or {@code null} if no URL could
     * be loaded
     */
    public final @Nullable URL getLastURL(String extension, String... folders) {
        Iterable<URL> urls = getAsURLs(extension, folders);
        if (urls == null)
            return null;
        
        Iterator<URL> urlItr = urls.iterator();
        URL out = null;
        while (urlItr.hasNext())
            out = urlItr.next();
        
        return out;
    }
    
    /**
     * Gets the files this identity points to, by querying the main class loader 
     * and the mod class loaders using the path:
     * <blockquote>
     *  {@code <superid>/.../path/to/file/<id>.<extension>}
     * </blockquote>
     * ...where the intervening folders are the given folder name parameters, in
     * order. The extension isn't included in the path if it is null or empty, and
     * similarly for any null or empty folder names. Note that this path is altered
     * according to the current implementation of {@link partial(String...)}, if
     * it was invoked prior to this method.<p>
     * 
     * @param extension The extension to use for the files
     * @param folders The folder names to use for the path
     * @return An {@code Iterable} holding all the resource files that were
     * successfully loaded, or {@code null} if the resource URLs could not be
     * loaded
     */
    public final @Nullable Iterable<File> getAsFiles(String extension, String... folders) {
        String path = getPath(extension, folders);
        Enumeration<URL> urls;
        
        try {
            urls = ModManager.getResources(path);
        } catch (IOException e) {
            LOG.log(Level.FINER, LocaleUtils.format("Identity.ResourceURLException", path), e);
            return null;
        }
        
        List<File> files = new ArrayList<>();
        
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            
            try {
                files.add(new File(url.toURI()));
            } catch (URISyntaxException e) {
                LOG.log(Level.FINER, LocaleUtils.format("Identity.getAsFiles.URISyntaxException", url.toString()), e);
            }
        }
        
        return files;
    }
    
    /**
     * Convenience method, returns only the first file in the returned iterable
     * from {@link #getAsFiles(String, String...) getAsFiles(extension, folders)}.
     * In general, this means discarding any mod resources, and returning a file
     * from a vanilla resource if one exists.<p>
     * 
     * Note that this method returns the first file that <i>was successfully
     * loaded</i>, so even if a vanilla resource exists, it isn't necessarily a
     * guarantee that such a file is the one returned.
     * 
     * @param extension The extension to use for the files
     * @param folders The folder names to use for the path
     * @return The first file found on the path, or {@code null} if no file
     * could be loaded
     */
    public final @Nullable File getFirstFile(String extension, String... folders) {
        Iterable<File> files = getAsFiles(extension, folders);
        if (files == null)
            return null;
        
        Iterator<File> fileItr = files.iterator();
        if (!fileItr.hasNext())
            return null;
        
        return fileItr.next();
    }
    
    /**
     * Convenience method, returns only the last file in the returned iterable
     * from {@link #getAsFiles(String, String...) getAsFiles(extension, folders)}.
     * In general, this means discarding any vanilla resources, and returning a 
     * file from a mod if one exists.<p>
     * 
     * Note that this method returns the last file that <i>was successfully
     * loaded</i>, so even if a mod resource exists, it isn't necessarily a 
     * guarantee that such a file is the one returned.
     * 
     * @param extension The extension to use for the files
     * @param folders The folder names to use for the path
     * @return The last file found on the path, or {@code null} if no file
     * could be loaded
     */
    public final @Nullable File getLastFile(String extension, String... folders) {
        Iterable<File> files = getAsFiles(extension, folders);
        if (files == null)
            return null;
        
        Iterator<File> fileItr = files.iterator();
        File out = null;
        while (fileItr.hasNext())
            out = fileItr.next();
        
        return out;
    }
    
    /**
     * Gets the files this identity points to as input streams, by querying the 
     * main class loader and the mod class loaders using the path:
     * <blockquote>
     *  {@code <superid>/.../path/to/file/<id>.<extension>}
     * </blockquote>
     * ...where the intervening folders are the given folder name parameters, in
     * order. The extension isn't included in the path if it is null or empty, and
     * similarly for any null or empty folder names. Note that this path is altered
     * according to the current implementation of {@link partial(String...)}, if
     * it was invoked prior to this method.<p>
     * 
     * <b>Warning</b>: All input streams are passed to the caller in an opened
     * state. It is up to the caller to handle and ultimately close these streams.
     * 
     * @param extension The extension to use for the streams
     * @param folders The folder names to use for the path
     * @return An {@code Iterable} holding all the resource streams that were
     * successfully loaded, or {@code null} if the resource URLs could not be
     * loaded
     */
    public final @Nullable Iterable<InputStream> getAsInputStreams(String extension, String... folders) {
        String path = getPath(extension, folders);
        Enumeration<URL> urls;
        
        try {
            urls = ModManager.getResources(path);
        } catch (IOException e) {
            LOG.log(Level.FINER, LocaleUtils.format("Identity.ResourceURLException", path), e);
            return null;
        }
        
        List<InputStream> streams = new ArrayList<>();
        
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            
            try {
                streams.add(url.openStream());
            } catch (IOException e) {
                LOG.log(Level.FINER, LocaleUtils.format("Identity.getAsInputStreams.URISyntaxException", url.toString()), e);
            }
        }
        
        return streams;
    }
    
    /**
     * Convenience method, returns only the first input stream in the returned 
     * iterable from 
     * {@link #getAsInputStreams(String, String...) getAsInputStreams(extension, folders)}.
     * All other streams in the iterable are automatically closed, unless they throw
     * an exception during the close operation. In general, this means discarding
     * any mod resources, and returning a stream to a vanilla resource if one exists.<p>
     * 
     * Note that this method returns the first stream that <i>was successfully
     * loaded</i>, so even if a vanilla resource exists, it isn't necessarily a
     * guarantee that such a stream is the one returned.
     * 
     * @param extension The extension to use for the files
     * @param folders The folder names to use for the path
     * @return The first stream found on the path, or {@code null} if no stream
     * could be loaded
     */
    public final @Nullable InputStream getFirstInputStream(String extension, String... folders) {
        Iterable<InputStream> streams = getAsInputStreams(extension, folders);
        if (streams == null)
            return null;
        
        Iterator<InputStream> streamItr = streams.iterator();
        if (!streamItr.hasNext())
            return null;
        
        InputStream str = streamItr.next();
        while (streamItr.hasNext()) {
            try {
                streamItr.next().close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, LocaleUtils.format("global.Status.Close.Failed", "InputStream"), e);
            }
        }
        
        return str;
    }
    
    /**
     * Convenience method, returns only the last input stream in the returned 
     * iterable from 
     * {@link #getAsInputStreams(String, String...) getAsInputStreams(extension, folders)}.
     * All other streams in the iterable are automatically closed, unless they throw
     * an exception during the close operation. In general, this means discarding 
     * any vanilla resources, and returning a stream to a mod resource if one exists.<p>
     * 
     * Note that this method returns the last stream that <i>was successfully
     * loaded</i>, so even if a mod resource exists, it isn't necessarily a 
     * guarantee that such a stream is the one returned.
     * 
     * @param extension The extension to use for the files
     * @param folders The folder names to use for the path
     * @return The last stream found on the path, or {@code null} if no stream
     * could be loaded
     */
    public final @Nullable InputStream getLastInputStream(String extension, String... folders) {
        Iterable<InputStream> streams = getAsInputStreams(extension, folders);
        if (streams == null)
            return null;
        
        Iterator<InputStream> streamItr = streams.iterator();
        if (!streamItr.hasNext())
            return null;
        
        InputStream str = streamItr.next();
        while (streamItr.hasNext()) {
            try {
                str.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, LocaleUtils.format("global.Status.Close.Failed", "InputStream"), e);
            }
            
            str = streamItr.next();
        }
        
        return str;
    }
    
    @Override
    public String toString() {
        return String.format("%s:%s", superID, id);
    }
    
    /**
     * Returns a string representation of the path this identity represents.
     * @param extension The extension to use for the file
     * @param folders The folder names to use for the path
     * @return The path string
     */
    public final String toString(String extension, String... folders) {
        return getPath(extension, folders);
    }
    
    /**
     * Compares this identity to the specified object. The result is {@code true}
     * if and only if  the argument is not {@code null}, is an {@code Identity}
     * object and holds the same superid and id values. Note that in this
     * implementation, a {@linkplain #partial(String...) partial identity}) also
     * compares it's partial path against the given object as well.
     * 
     * @param o The object to compare this {@code Identity} against
     * @return {@code true} if the given object is an {@code Identity}
     */
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Identity)) 
            return false;
        
        if (o instanceof PartialIdentity && !((PartialIdentity)o).hasEmptyPath())
            return false;
        
        Identity i = (Identity)o;
        
        return superID.equals(i.superID) && id.equals(i.id);
    }
    
    @Override
    public int hashCode() {
        int superhash = superID.hashCode();
        int idhash = id.hashCode();
        
        return ((superhash << 16) + superhash) ^ idhash;
    }
    
    private class PartialIdentity extends Identity {
        private final String[] extraFolders;
        
        PartialIdentity(Identity id, String... extraFolders) {
            super(id.superID, id.id);
            this.extraFolders = extraFolders;
        }
        
        @Override
        public Identity partial(String... folders) {
            return super.partial(concatFolders(folders));
        }
        
        @Override
        public Identity revert() {
            return new Identity(superID, id);
        }
        
        @Override
        protected String getPath(String extension, String... folders) {
            return super.getPath(extension, concatFolders(folders));
        }
        
        private String[] concatFolders(String[] folders) {
            int e = extraFolders.length;
            int f = folders.length;
            
            String[] strs = new String[e + f];
            
            System.arraycopy(extraFolders, 0, strs, 0, e);
            System.arraycopy(folders, 0, strs, e, f);
            
            return strs;
        }
        
        private boolean equateFolders(String[] folders) {
            int i1 = 0, i2 = 0;
            String f1 = null, f2 = null;

            while (true) {
                while (emptyStr(f1) && i1 < extraFolders.length)
                    f1 = extraFolders[i1++];
                while (emptyStr(f2) && i2 < folders.length)
                    f2 = folders[i2++];

                if ((i1 == extraFolders.length) && (i2 == folders.length))
                    return emptyStr(f1) ? emptyStr(f2) : f1.equals(f2);
                
                if (!(emptyStr(f1) ? emptyStr(f2) : f1.equals(f2)))
                    return false;
                
                f1 = f2 = null;
            }
        }
        
        private boolean emptyStr(String str) {
            return str == null || str.isEmpty();
        }
        
        public boolean hasEmptyPath() {
            return equateFolders(new String[0]);
        }
        
        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof Identity)) return false;
            
            if (!hasEmptyPath() && (!(o instanceof PartialIdentity) || ((PartialIdentity)o).hasEmptyPath()))
                return false;
            
            PartialIdentity pi = (PartialIdentity)o;
            return superID.equals(pi.superID) && id.equals(pi.id) && equateFolders(pi.extraFolders);
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ Arrays.deepHashCode(this.extraFolders);
        }
        
        @Override
        public String toString() {
            String s = "";
            for(String f : extraFolders)
                if (!emptyStr(f))
                    s += f + "/";
            
            if (s.isEmpty())
                return super.toString();
            else
                return String.format("%s:%s%s", superID, s, id);
        }
    }
}
