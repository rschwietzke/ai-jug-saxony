/*
 * Copyright (c) 2005-2026 Xceptance Software Technologies GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jugsaxony.demo9;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * High-performance, open-addressing hash map with linear probing and backward-shift deletion.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Not thread-safe.</li>
 *   <li>Dynamic resizing (unbound capacity).</li>
 *   <li>Parallel flat arrays for keys and values (zero per-entry heap allocations).</li>
 *   <li>Rejects null keys with {@link NullPointerException}.</li>
 *   <li>Fully supports null values.</li>
 *   <li>Backward-shift deletion (Knuth's Algorithm 6.4R) without tombstone degradation.</li>
 * </ul>
 *
 * @param <K> the type of keys maintained by this map (nulls not permitted)
 * @param <V> the type of mapped values (nulls permitted)
 *
 * @author René Schwietzke (Xceptance Software Technologies GmbH)
 */
public class FastHashMap<K, V>
{
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.60f;
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * Backing array of keys. A null entry signifies an empty slot.
     */
    private K[] keys;

    /**
     * Backing array of values. Valid values (including null) correspond to non-null keys.
     */
    private V[] values;

    /**
     * Current number of key-value pairs in the map.
     */
    private int size;

    /**
     * Bitmask used to calculate array indices (capacity - 1).
     */
    private int mask;

    /**
     * Number of elements at or above which the map will resize.
     */
    private int threshold;

    /**
     * Load factor determining when the map expands.
     */
    private final float loadFactor;

    /**
     * Constructs a new, empty FastHashMap with default initial capacity (16)
     * and default load factor (0.60).
     */
    public FastHashMap()
    {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new, empty FastHashMap with the specified initial capacity
     * and default load factor (0.60).
     *
     * @param initialCapacity the initial capacity
     * @throws IllegalArgumentException if initialCapacity is negative
     */
    public FastHashMap(final int initialCapacity)
    {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new, empty FastHashMap with the specified initial capacity
     * and load factor.
     *
     * @param initialCapacity the initial capacity
     * @param loadFactor the load factor
     * @throws IllegalArgumentException if initialCapacity is negative or loadFactor is invalid
     */
    @SuppressWarnings("unchecked")
    public FastHashMap(final int initialCapacity, final float loadFactor)
    {
        if (initialCapacity < 0)
        {
            throw new IllegalArgumentException("Initial capacity must be non-negative: " + initialCapacity);
        }
        if (loadFactor <= 0.0f || Float.isNaN(loadFactor) || loadFactor >= 1.0f)
        {
            throw new IllegalArgumentException("Load factor must be between 0 (exclusive) and 1 (exclusive): " + loadFactor);
        }

        this.loadFactor = loadFactor;
        final int capacity = calculateCapacity(initialCapacity);
        this.mask = capacity - 1;
        this.threshold = calculateThreshold(capacity, loadFactor);

        this.keys = (K[]) new Object[capacity];
        this.values = (V[]) new Object[capacity];
        this.size = 0;
    }

    /**
     * Mix the hash code to distribute entropy across bits.
     *
     * @param h the raw hash code
     * @return the mixed hash
     */
    private static int mixHash(final int h)
    {
        return h ^ (h >>> 16);
    }

    /**
     * Retrieves the value associated with the specified key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value associated with the key, or {@code null} if no mapping exists
     * @throws NullPointerException if key is null
     */
    public V get(final K key)
    {
        Objects.requireNonNull(key, "Key must not be null");

        int ptr = mixHash(key.hashCode()) & this.mask;

        while (true)
        {
            final K k = this.keys[ptr];
            if (k == null)
            {
                return null;
            }
            if (k.equals(key))
            {
                return this.values[ptr];
            }

            ptr = (ptr + 1) & this.mask;
        }
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is replaced.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with key (may be null)
     * @return previous value associated with key, or {@code null} if there was no mapping
     * @throws NullPointerException if key is null
     */
    public V put(final K key, final V value)
    {
        Objects.requireNonNull(key, "Key must not be null");

        int ptr = mixHash(key.hashCode()) & this.mask;

        while (true)
        {
            final K k = this.keys[ptr];
            if (k == null)
            {
                // New entry insertion
                if (this.size >= this.threshold)
                {
                    rehash(this.keys.length << 1);
                    // Recalculate insertion slot after table expansion
                    ptr = mixHash(key.hashCode()) & this.mask;
                    while (this.keys[ptr] != null)
                    {
                        ptr = (ptr + 1) & this.mask;
                    }
                }

                this.keys[ptr] = key;
                this.values[ptr] = value;
                this.size++;

                return null;
            }

            if (k.equals(key))
            {
                final V oldValue = this.values[ptr];
                this.values[ptr] = value;
                return oldValue;
            }

            ptr = (ptr + 1) & this.mask;
        }
    }

    /**
     * Removes the mapping for a key from this map if it is present.
     *
     * @param key key whose mapping is to be removed
     * @return previous value associated with key, or {@code null} if there was no mapping
     * @throws NullPointerException if key is null
     */
    public V remove(final K key)
    {
        Objects.requireNonNull(key, "Key must not be null");

        int ptr = mixHash(key.hashCode()) & this.mask;

        while (true)
        {
            final K k = this.keys[ptr];
            if (k == null)
            {
                return null;
            }

            if (k.equals(key))
            {
                final V oldValue = this.values[ptr];
                this.size--;

                // Backward-shift deletion (Knuth's Algorithm 6.4R)
                int hole = ptr;
                int curr = ptr;

                while (true)
                {
                    curr = (curr + 1) & this.mask;
                    final K currKey = this.keys[curr];
                    if (currKey == null)
                    {
                        // End of cluster reached, clear the remaining hole
                        this.keys[hole] = null;
                        this.values[hole] = null;
                        return oldValue;
                    }

                    final int h = mixHash(currKey.hashCode()) & this.mask;
                    // Check if hole lies cyclically in [h, curr)
                    if (((hole - h) & this.mask) < ((curr - h) & this.mask))
                    {
                        this.keys[hole] = currKey;
                        this.values[hole] = this.values[curr];
                        hole = curr;
                    }
                }
            }

            ptr = (ptr + 1) & this.mask;
        }
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size()
    {
        return this.size;
    }

    /**
     * Returns a snapshot list containing all keys currently present in this map.
     *
     * @return a list of all keys in this map
     */
    public List<K> keys()
    {
        final List<K> list = new ArrayList<>(this.size);
        final K[] kArr = this.keys;
        final int len = kArr.length;

        for (int i = 0; i < len; i++)
        {
            final K k = kArr[i];
            if (k != null)
            {
                list.add(k);
            }
        }

        return list;
    }

    /**
     * Returns a snapshot list containing all values currently present in this map.
     * If a key maps to {@code null}, the returned list will contain {@code null} at that position.
     *
     * @return a list of all values in this map
     */
    public List<V> values()
    {
        final List<V> list = new ArrayList<>(this.size);
        final K[] kArr = this.keys;
        final V[] vArr = this.values;
        final int len = kArr.length;

        for (int i = 0; i < len; i++)
        {
            if (kArr[i] != null)
            {
                list.add(vArr[i]);
            }
        }

        return list;
    }

    /**
     * Removes all key-value mappings from this map.
     * The map will be empty after this call. Array references are cleared for garbage collection.
     */
    public void clear()
    {
        Arrays.fill(this.keys, null);
        Arrays.fill(this.values, null);
        this.size = 0;
    }

    /**
     * Returns the current capacity of the backing arrays.
     * Package-private for testing and inspection.
     *
     * @return current capacity
     */
    int capacity()
    {
        return this.keys.length;
    }

    /**
     * Doubles the capacity and rehashes all existing entries.
     *
     * @param newCapacity the new backing array capacity
     */
    @SuppressWarnings("unchecked")
    private void rehash(final int newCapacity)
    {
        if (newCapacity > MAXIMUM_CAPACITY)
        {
            if (this.threshold == MAXIMUM_CAPACITY - 1)
            {
                throw new IllegalStateException("Maximum map capacity exceeded: " + this.size);
            }
            this.threshold = MAXIMUM_CAPACITY - 1;
            return;
        }

        final K[] oldKeys = this.keys;
        final V[] oldValues = this.values;
        final int oldCapacity = oldKeys.length;

        this.keys = (K[]) new Object[newCapacity];
        this.values = (V[]) new Object[newCapacity];
        this.mask = newCapacity - 1;
        this.threshold = calculateThreshold(newCapacity, this.loadFactor);

        for (int i = 0; i < oldCapacity; i++)
        {
            final K k = oldKeys[i];
            if (k != null)
            {
                int ptr = mixHash(k.hashCode()) & this.mask;
                while (this.keys[ptr] != null)
                {
                    ptr = (ptr + 1) & this.mask;
                }
                this.keys[ptr] = k;
                this.values[ptr] = oldValues[i];
            }
        }
    }

    /**
     * Calculates the power-of-two capacity for a requested capacity.
     *
     * @param expected the requested initial capacity
     * @return the power-of-two capacity
     */
    private static int calculateCapacity(final int expected)
    {
        if (expected <= 4)
        {
            return 4;
        }
        int cap = Integer.highestOneBit(expected);
        if (cap < expected)
        {
            cap <<= 1;
        }
        return Math.min(cap, MAXIMUM_CAPACITY);
    }

    /**
     * Computes the resize threshold, guaranteeing at least one empty slot remains.
     *
     * @param capacity current capacity
     * @param loadFactor current load factor
     * @return threshold count
     */
    private static int calculateThreshold(final int capacity, final float loadFactor)
    {
        final int t = (int) (capacity * loadFactor);
        return (t >= capacity) ? capacity - 1 : Math.max(1, t);
    }
}

