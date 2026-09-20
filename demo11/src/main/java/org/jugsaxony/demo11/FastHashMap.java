package org.jugsaxony.demo11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Fast open-addressing (open hashing) hash map implementation with linear probing.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Flat parallel arrays ({@code keys} and {@code values}) for optimal CPU cache locality.</li>
 *   <li>MurmurHash3 32-bit finalizer hash mixing for uniform bucket distribution.</li>
 *   <li>Power-of-two table sizing for fast bitwise masking.</li>
 *   <li>Tombstone-free backward-shift deletion (Knuth's Algorithm R) preserving compact clusters.</li>
 *   <li>Rejects {@code null} keys (throws {@link NullPointerException}), supports {@code null} values.</li>
 * </ul>
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class FastHashMap<K, V> {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.65f;
    static final int MAXIMUM_CAPACITY = 1 << 20;

    private K[] keys;
    private V[] values;
    private int size;
    private int threshold;
    private int mask;
    private final float loadFactor;

    /**
     * Constructs an empty {@code FastHashMap} with default initial capacity (16)
     * and default load factor (0.65).
     */
    public FastHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs an empty {@code FastHashMap} with the specified initial capacity
     * and default load factor (0.65).
     *
     * @param initialCapacity the initial capacity
     */
    public FastHashMap(final int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs an empty {@code FastHashMap} with the specified initial capacity
     * and load factor.
     *
     * @param initialCapacity the initial capacity
     * @param loadFactor      the load factor
     */
    @SuppressWarnings("unchecked")
    public FastHashMap(final int initialCapacity, final float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (initialCapacity > MAXIMUM_CAPACITY) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor) || loadFactor >= 1.0f) {
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        }

        final int capacity = tableSizeFor(initialCapacity);
        this.loadFactor = loadFactor;
        this.keys = (K[]) new Object[capacity];
        this.values = (V[]) new Object[capacity];
        this.mask = capacity - 1;
        this.threshold = (int) (capacity * loadFactor);
        this.size = 0;
    }

    int capacity() {
        return keys.length;
    }

    int threshold() {
        return threshold;
    }

    Object[] keysArray() {
        return keys;
    }

    Object[] valuesArray() {
        return values;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or {@code null} if absent or mapped to null
     * @throws NullPointerException if {@code key} is null
     */
    public V get(final K key) {
        Objects.requireNonNull(key, "Key cannot be null");

        final int mask = this.mask;
        int idx = hash(key) & mask;
        K curr;
        while ((curr = keys[idx]) != null) {
            if (curr == key || curr.equals(key)) {
                return values[idx];
            }
            idx = (idx + 1) & mask;
        }
        return null;
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is replaced.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key (can be {@code null})
     * @return the previous value associated with {@code key}, or {@code null} if there was no mapping or old value was null
     * @throws NullPointerException if {@code key} is null
     */
    public V put(final K key, final V value) {
        Objects.requireNonNull(key, "Key cannot be null");

        int mask = this.mask;
        int idx = hash(key) & mask;
        K curr;

        // Check if key already exists in table
        while ((curr = keys[idx]) != null) {
            if (curr == key || curr.equals(key)) {
                final V oldVal = values[idx];
                values[idx] = value;
                return oldVal;
            }
            idx = (idx + 1) & mask;
        }

        // New key insertion: check if table requires expansion
        if (size >= threshold) {
            resize();
            mask = this.mask;
            idx = hash(key) & mask;
            while (keys[idx] != null) {
                idx = (idx + 1) & mask;
            }
        }

        keys[idx] = key;
        values[idx] = value;
        size++;
        return null;
    }

    /**
     * Removes the mapping for a key from this map if it is present.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with {@code key}, or {@code null} if there was no mapping
     * @throws NullPointerException if {@code key} is null
     */
    public V remove(final K key) {
        Objects.requireNonNull(key, "Key cannot be null");

        final int mask = this.mask;
        int i = hash(key) & mask;
        K curr;

        while ((curr = keys[i]) != null) {
            if (curr == key || curr.equals(key)) {
                final V removedValue = values[i];
                keys[i] = null;
                values[i] = null;
                size--;

                // Realign subsequent cluster entries to maintain linear probing chains
                int j = i;
                while (true) {
                    j = (j + 1) & mask;
                    final K k = keys[j];
                    if (k == null) {
                        break;
                    }
                    final V v = values[j];
                    keys[j] = null;
                    values[j] = null;
                    reinsert(k, v);
                }

                return removedValue;
            }
            i = (i + 1) & mask;
        }

        return null;
    }

    private void reinsert(final K key, final V value) {
        final int mask = this.mask;
        int idx = hash(key) & mask;
        while (keys[idx] != null) {
            idx = (idx + 1) & mask;
        }
        keys[idx] = key;
        values[idx] = value;
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return size;
    }

    /**
     * Returns a {@link List} containing all keys currently present in this map.
     *
     * @return a snapshot list containing all keys in this map
     */
    public List<K> keys() {
        final List<K> list = new ArrayList<>(size);
        for (int i = 0; i < keys.length; i++) {
            final K key = keys[i];
            if (key != null) {
                list.add(key);
            }
        }
        return list;
    }

    /**
     * Returns a {@link List} containing all values currently present in this map.
     *
     * @return a snapshot list containing all values in this map
     */
    public List<V> values() {
        final List<V> list = new ArrayList<>(size);
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null) {
                list.add(values[i]);
            }
        }
        return list;
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    public void clear() {
        Arrays.fill(keys, null);
        Arrays.fill(values, null);
        size = 0;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        final int oldCapacity = keys.length;
        final int newCapacity = oldCapacity << 1;

        final K[] oldKeys = this.keys;
        final V[] oldValues = this.values;
        final K[] newKeys = (K[]) new Object[newCapacity];
        final V[] newValues = (V[]) new Object[newCapacity];
        final int newMask = newCapacity - 1;

        for (int i = 0; i < oldCapacity; i++) {
            final K key = oldKeys[i];
            if (key != null) {
                int idx = hash(key) & newMask;
                while (newKeys[idx] != null) {
                    idx = (idx + 1) & newMask;
                }
                newKeys[idx] = key;
                newValues[idx] = oldValues[i];
            }
        }

        this.keys = newKeys;
        this.values = newValues;
        this.mask = newMask;
        this.threshold = (int) (newCapacity * loadFactor);
    }

    static int mixHash(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    private int hash(final Object key) {
        return mixHash(key.hashCode());
    }

    static int tableSizeFor(final int cap) {
        return 1 << (32 - Integer.numberOfLeadingZeros(cap - 1));
    }
}

