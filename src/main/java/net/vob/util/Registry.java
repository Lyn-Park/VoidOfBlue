package net.vob.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.vob.util.logging.LocaleUtils;

/**
 * Class for registries of objects, which are essentially one-to-one maps between
 * registered objects and their automatically generated integer key values.<p>
 * 
 * Registries allow for quick, universal serialization to buffers and streams and 
 * then back again, assuming that the deserializing registry is in the same state 
 * (i.e. has the same object/integer mappings) as the serializing registry. Since
 * the integer key generation is non-random, 2 registries are guaranteed to have
 * the same state if the order in which entries were added/removed remains the
 * same.<p>
 * 
 * Internally, a registry maintains 2 maps mapping between objects and integer 
 * keys; each map is the inverse of the other. This allows the registry to 
 * maintain a one-to-one relation for each object-key pair. Note that null objects
 * are not permitted to be registered.<p>
 * 
 * In addition, a registry maintains synchronization using an internal 
 * {@link ReentrantLock} object. Thus, registries are fully thread-safe (apart from
 * their iterators). However, if 2 registries in arbitrary locations are required to
 * have the same state, then these registries should be initialized and populated
 * within a single thread to ensure the ordering of addition/removal operations, and
 * thus matching registry entries.
 * 
 * @param <T> The type of object this registry maintains
 */
public class Registry<T> implements Iterable<T> {
    private final TreeMap<Integer, T> int2Obj;
    private final Map<T, Integer> obj2Int;
    
    protected final ReentrantLock LOCK = new ReentrantLock();
    
    private int nextkey = 0;
    
    /**
     * Constructs an empty {@code Registry} with the specified initial capacity
     * and load factor.
     * @param capacity The initial capacity
     * @param loadFactor The load factor
     * @throws IllegalArgumentException if the initial capacity is negative or
     * the load factor is non-positive
     */
    public Registry(int capacity, float loadFactor) {
        int2Obj = new TreeMap<>();
        obj2Int = new HashMap<>(capacity, loadFactor);
    }
    
    /**
     * Constructs an empty {@code Registry} with the specified initial capacity
     * and the default load factor (0.75).
     * @param capacity The initial capacity
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public Registry(int capacity) {
        this(capacity, 0.75f);
    }
    
    /**
     * Constructs an empty {@code Registry} with the default initial capacity 
     * (16) and the default load factor (0.75).
     */
    public Registry() {
        this(16);
    }
    
    /**
     * Constructs a new {@code Registry} as a shallow copy of the given 
     * {@code Registry}.
     * @param registry The registry to be copied
     */
    public Registry(Registry<? extends T> registry) {
        int2Obj = new TreeMap<>(registry.int2Obj);
        obj2Int = new HashMap<>(registry.obj2Int);
        nextkey = registry.nextkey;
    }
    
    /**
     * Factory method for constructing a new {@code Registry} using the entries
     * of the given map.
     * @param <T> The type of the objects to register
     * @param map The map to be copied
     * @return The new registry instance
     * @throws NullPointerException if there exist any entries in the map for 
     * which either the key or the value is {@code null}
     * @throws IllegalArgumentException If there exist any duplicate integer
     * keys in the given map
     */
    public static <T> Registry<T> createFromObj2IntMap(Map<? extends T, Integer> map) {
        Registry<T> reg = new Registry<>(map.size());
        map.entrySet().forEach((entry) -> reg.put(entry.getKey(), entry.getValue()));
        return reg;
    }
    
    /**
     * Factory method for constructing a new {@code Registry} using the entries
     * of the given map.
     * @param <T> The type of the objects to register
     * @param map The map to be copied
     * @return The new registry instance
     * @throws NullPointerException if there exist any entries in the map for 
     * which either the key or the value is {@code null}
     * @throws IllegalArgumentException If there exist any duplicate integer
     * keys in the given map
     */
    public static <T> Registry<T> createFromInt2ObjMap(Map<Integer, ? extends T> map) {
        Registry<T> reg = new Registry<>(map.size());
        map.entrySet().forEach((entry) -> reg.put(entry.getValue(), entry.getKey()));
        return reg;
    }
    
    private void put(T obj, Integer key) {
        if (obj == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "obj"));
        if (key == null)
            throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "key"));
        if (int2Obj.containsKey(key))
            throw new IllegalArgumentException(LocaleUtils.format("Registry.put.DuplicateValue"));
        if (obj2Int.containsKey(obj))
            throw new IllegalArgumentException(LocaleUtils.format("Registry.put.DuplicateKey"));
        
        int2Obj.put(key, obj);
        obj2Int.put(obj, key);
    }
    
    /**
     * Registers the given object to the registry, if it hasn't already.
     * @param obj The object to register
     * @return The integer key the object was registered with
     * @throws NullPointerException if the given object is {@code null}
     * @throws IllegalStateException if the registry already contains 2^32 - 1
     * entries, and thus no suitable integer key was found
     */
    public int register(T obj) {
        LOCK.lock();
        try {
            if (obj == null)
                throw new NullPointerException(LocaleUtils.format("global.Exception.Null", "obj"));
            if (obj2Int.containsKey(obj))
                return obj2Int.get(obj);

            int k = nextkey - 1;
            while (int2Obj.containsKey(nextkey) && ++nextkey != k) {}

            if (nextkey == k)
                throw new IllegalStateException(LocaleUtils.format("Registry.register.CapacityReached"));

            int2Obj.put(nextkey, obj);
            obj2Int.put(obj, nextkey);

            return nextkey++;
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Registers the given object to the registry. If the object has already been 
     * registered, removes the previous mapping and re-registers it.
     * @param obj The object to register
     * @return The integer key the object was registered with
     * @throws NullPointerException if the given object is {@code null}
     * @throws IllegalStateException if the object was not previously registered
     * and the registry already contains 2^32 - 1 entries, and thus no suitable
     * integer key was found
     */
    public int registerForce(T obj) {
        LOCK.lock();
        try {
            if (obj2Int.containsKey(obj))
                int2Obj.remove(obj2Int.remove(obj));

            return register(obj);
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Removes the given object's mapping, if it is registered.
     * @param obj The object to remove
     * @return The integer key the object was previously mapped with. If there
     * was no such mapping, {@code null} is returned instead
     */
    public @Nullable Integer deregister(T obj) {
        LOCK.lock();
        try {
            if (obj2Int.containsKey(obj)) {
                int key = obj2Int.remove(obj);
                int2Obj.remove(key);
                return key;
            }
        } finally {
            LOCK.unlock();
        }
        
        return null;
    }
    
    /**
     * Removes the given integer key's mapping, if one exists.
     * @param key The integer key to remove
     * @return The object the key was previously mapped with. If there was no
     * such mapping, {@code null} is returned instead
     */
    public @Nullable T deregister(int key) {
        LOCK.lock();
        try {
            if (int2Obj.containsKey(key)) {
                T obj = int2Obj.remove(key);
                obj2Int.remove(obj);
                return obj;
            }
        } finally {
            LOCK.unlock();
        }
        
        return null;
    }
    
    /**
     * Clears the registry, removing all mappings. The registry will be empty after
     * this method returns.
     */
    public void clear() {
        LOCK.lock();
        
        try {
            int2Obj.clear();
            obj2Int.clear();
            nextkey = 0;
            
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Checks whether the registry is empty.
     * @return {@code true} if the registry currently has no registered objects,
     * {@code false} otherwise
     */
    public boolean isEmpty() {
        LOCK.lock();
        try {
            return int2Obj.isEmpty();
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Checks whether the given integer key is registered in this registry.
     * @param key The integer key whose presence is to be tested
     * @return {@code true} if the given integer key has a mapping in this 
     * registry, {@code false} otherwise
     */
    public boolean isRegistered(int key) {
        LOCK.lock();
        try {
            return int2Obj.containsKey(key);
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Checks whether the given object is registered in this registry.
     * @param obj The object whose presence is to be tested
     * @return {@code true} if the given object is registered in this registry,
     * {@code false} otherwise
     */
    public boolean isRegistered(T obj) {
        LOCK.lock();
        try {
            return obj2Int.containsKey(obj);
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Gets the object that the given integer key maps to.
     * @param key The integer key to get the associated object of
     * @return The appropriate object from the registry, or {@code null} if the
     * given key has no mapping in the registry
     */
    public @Nullable T get(int key) {
        LOCK.lock();
        try {
            return int2Obj.getOrDefault(key, null);
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Gets the integer key that the given object maps to.
     * @param obj The object to get the associated integer key of
     * @return The appropriate integer key, or {@code null} if the given object
     * has no mapping in the registry
     */
    public @Nullable Integer get(T obj) {
        LOCK.lock();
        try {
            return obj2Int.getOrDefault(obj, null);
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Gets the object that the given integer key maps to, or the default value if
     * the given key is not registered.
     * @param key The integer key to get the associated object of
     * @param def The default value to use
     * @return The appropriate object from the registry, or {@code def} if the
     * given key has no mapping in the registry
     */
    public @Nullable T getOrDefault(int key, T def) {
        LOCK.lock();
        try {
            return int2Obj.getOrDefault(key, def);
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Gets the integer key that the given object maps to, or the default value if
     * the given object is not registered.
     * @param obj The object to get the associated integer key of
     * @param def The default value to use
     * @return The appropriate integer key, or {@code def} if the given object
     * has no mapping in the registry
     */
    public @Nullable Integer getOrDefault(T obj, Integer def) {
        LOCK.lock();
        try {
            return obj2Int.getOrDefault(obj, def);
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Gets a copy of the set of currently registered objects.
     * @return A copy of the set of objects in this registry
     */
    public Set<T> getObjectSet() {
        LOCK.lock();
        try {
            return new HashSet<>(obj2Int.keySet());
        } finally {
            LOCK.unlock();
        }
    }
    
    /**
     * Returns whether any elements of this registry match the given predicate. May not
     * evaluate the predicate on all elements if not necessary for determining the result.
     * If the registry is empty, then this method returns {@code false} and does not
     * evaluate the predicate.
     * 
     * @param predicate the predicate to evaluate on this registry
     * @return {@code true} if any elements in the registry evaluate to {@code true}
     * using the given predicate, {@code false} otherwise
     */
    public boolean anyMatch(Predicate<T> predicate) {
        return getObjectSet().stream().anyMatch(predicate);
    }
    
    /**
     * Returns whether all elements of this registry match the given predicate. May not
     * evaluate the predicate on all elements if not necessary for determining the result.
     * If the registry is empty, then this method returns {@code true} and does not
     * evaluate the predicate.
     * 
     * @param predicate the predicate to evaluate on this registry
     * @return {@code true} if all elements in the registry evaluate to {@code true}
     * using the given predicate, {@code false} otherwise
     */
    public boolean allMatch(Predicate<T> predicate) {
        return getObjectSet().stream().allMatch(predicate);
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * This iterator returns the registry elements in the order of their integer
     * id values.
     * 
     * @return an iterator over the registered objects in this registry
     */
    @Override
    public Iterator<T> iterator() {
        return new RegistryIterator();
    }
    
    /**
     * Creates a {@code Spliterator} as defined by this {@code Iterable}. This
     * spliterator reports the characteristics {@link Spliterator#DISTINCT},
     * {@link Spliterator#NONNULL}, {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED} and {@link Spliterator#ORDERED}, and is not 
     * <em><a href="Spliterator.html#binding">late-binding</a></em>.<p>
     * 
     * @return a {@code Spliterator} over the registered objects in this registry
     */
    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(iterator(), obj2Int.size(), Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.ORDERED);
    }
    
    private class RegistryIterator implements Iterator<T> {
        Iterator<Entry<Integer, T>> innerIter = int2Obj.entrySet().iterator();
        T current;
        
        @Override
        public boolean hasNext() {
            return innerIter.hasNext();
        }

        @Override
        public T next() {
            return (current = innerIter.next().getValue());
        }

        @Override
        public void remove() {
            innerIter.remove();
            obj2Int.remove(current);
        }
    }
}
