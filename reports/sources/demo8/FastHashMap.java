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
package org.jugsaxony.demo8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An unbounded open-addressing hash map implementation using linear probing.
 * Not thread-safe.
 * Does not permit null keys, but supports null values.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class FastHashMap<K, V>
{
    /** Default initial capacity (must be a power of two) */
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    /** Default load factor */
    private static final float DEFAULT_LOAD_FACTOR = 0.70f;

    /** Maximum capacity (2^30) */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /** Backing table holding key-value entries */
    private Entry<K, V>[] data;

    /** Current number of key-value mappings */
    private int size;

    /** Bitmask used to map mixed hash codes into array indices */
    private int mask;

    /** The load factor for the hash table */
    private final float loadFactor;

    /** The next size value at which to resize (capacity * load factor) */
    private int threshold;

    /**
     * Internal entry class holding a key and value pair.
     */
    private static class Entry<K, V>
    {
        final K key;
        V value;

        Entry(final K key, final V value)
        {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString()
        {
            return "[" + key + "=" + value + "]";
        }
    }

    /**
     * Constructs a new, empty FastHashMap with default initial capacity (16) and load factor (0.70).
     */
    public FastHashMap()
    {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new, empty FastHashMap with the specified initial capacity and default load factor (0.70).
     *
     * @param initialCapacity the initial capacity
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public FastHashMap(final int initialCapacity)
    {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new, empty FastHashMap with the specified initial capacity and load factor.
     *
     * @param initialCapacity the initial capacity
     * @param loadFactor the load factor
     * @throws IllegalArgumentException if initial capacity is negative or load factor is non-positive or NaN
     */
    @SuppressWarnings("unchecked")
    public FastHashMap(final int initialCapacity, final float loadFactor)
    {
        if (initialCapacity < 0)
        {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor) || loadFactor >= 1.0f)
        {
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        }

        final int capacity = calculateCapacity(initialCapacity);
        this.loadFactor = loadFactor;
        this.mask = capacity - 1;
        this.threshold = calculateThreshold(capacity, loadFactor);
        this.data = (Entry<K, V>[]) new Entry[capacity];
    }

    /**
     * Mixes hash code to ensure uniform distribution across table buckets.
     *
     * @param h original hash code
     * @return mixed hash code
     */
    private static int mixHash(final int h)
    {
        return h ^ (h >>> 16);
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or {@code null} if no mapping exists
     * @throws NullPointerException if the specified key is null
     */
    public V get(final K key)
    {
        Objects.requireNonNull(key, "Key must not be null");

        int ptr = mixHash(key.hashCode()) & this.mask;
        while (true)
        {
            final Entry<K, V> entry = this.data[ptr];
            if (entry == null)
            {
                return null;
            }
            if (entry.key.equals(key))
            {
                return entry.value;
            }
            ptr = (ptr + 1) & this.mask;
        }
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is replaced.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key (null allowed)
     * @return the previous value associated with key, or {@code null} if there was no mapping for key
     * @throws NullPointerException if the specified key is null
     */
    public V put(final K key, final V value)
    {
        Objects.requireNonNull(key, "Key must not be null");

        if (this.size >= this.threshold)
        {
            resize();
        }

        int ptr = mixHash(key.hashCode()) & this.mask;
        while (true)
        {
            final Entry<K, V> entry = this.data[ptr];
            if (entry == null)
            {
                this.data[ptr] = new Entry<>(key, value);
                this.size++;
                return null;
            }
            if (entry.key.equals(key))
            {
                final V oldValue = entry.value;
                entry.value = value;
                return oldValue;
            }
            ptr = (ptr + 1) & this.mask;
        }
    }

    /**
     * Removes the mapping for a key from this map if it is present.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with key, or {@code null} if there was no mapping for key
     * @throws NullPointerException if the specified key is null
     */
    public V remove(final K key)
    {
        Objects.requireNonNull(key, "Key must not be null");

        int ptr = mixHash(key.hashCode()) & this.mask;
        while (true)
        {
            final Entry<K, V> entry = this.data[ptr];
            if (entry == null)
            {
                return null;
            }
            if (entry.key.equals(key))
            {
                final V oldValue = entry.value;
                freePositionAndAdjustArray(ptr);
                return oldValue;
            }
            ptr = (ptr + 1) & this.mask;
        }
    }

    /**
     * Removes the entry at {@code ptr} and realigns subsequent cluster entries to maintain
     * linear probing search invariants.
     *
     * @param ptr index of the slot to free
     */
    private void freePositionAndAdjustArray(final int ptr)
    {
        this.data[ptr] = null;
        this.size--;

        int currentPtr = ptr;
        while (true)
        {
            currentPtr = (currentPtr + 1) & this.mask;
            final Entry<K, V> entry = this.data[currentPtr];
            if (entry == null)
            {
                break;
            }

            this.data[currentPtr] = null;
            realign(entry);
        }
    }

    /**
     * Re-inserts an entry into the table during cluster realignment.
     *
     * @param entry entry to realign
     */
    private void realign(final Entry<K, V> entry)
    {
        int ptr = mixHash(entry.key.hashCode()) & this.mask;
        while (true)
        {
            if (this.data[ptr] == null)
            {
                this.data[ptr] = entry;
                return;
            }
            ptr = (ptr + 1) & this.mask;
        }
    }

    /**
     * Resizes the backing array to twice its current capacity and rehashes all entries.
     */
    @SuppressWarnings("unchecked")
    private void resize()
    {
        final int oldCapacity = this.data.length;
        if (oldCapacity >= MAXIMUM_CAPACITY)
        {
            this.threshold = Integer.MAX_VALUE;
            return;
        }

        final int newCapacity = oldCapacity << 1;
        final Entry<K, V>[] newTable = (Entry<K, V>[]) new Entry[newCapacity];
        final int newMask = newCapacity - 1;

        for (int i = 0; i < oldCapacity; i++)
        {
            final Entry<K, V> entry = this.data[i];
            if (entry != null)
            {
                int ptr = mixHash(entry.key.hashCode()) & newMask;
                while (newTable[ptr] != null)
                {
                    ptr = (ptr + 1) & newMask;
                }
                newTable[ptr] = entry;
            }
        }

        this.data = newTable;
        this.mask = newMask;
        this.threshold = calculateThreshold(newCapacity, this.loadFactor);
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings
     */
    public int size()
    {
        return this.size;
    }

    /**
     * Returns a list of all keys currently in the map.
     *
     * @return a List of keys
     */
    public List<K> keys()
    {
        final List<K> result = new ArrayList<>(this.size);
        for (final Entry<K, V> entry : this.data)
        {
            if (entry != null)
            {
                result.add(entry.key);
            }
        }
        return result;
    }

    /**
     * Returns a list of all values currently in the map.
     *
     * @return a List of values
     */
    public List<V> values()
    {
        final List<V> result = new ArrayList<>(this.size);
        for (final Entry<K, V> entry : this.data)
        {
            if (entry != null)
            {
                result.add(entry.value);
            }
        }
        return result;
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    public void clear()
    {
        Arrays.fill(this.data, null);
        this.size = 0;
    }

    /**
     * Calculates the capacity as the smallest power of two greater than or equal to initialCapacity.
     *
     * @param initialCapacity requested capacity
     * @return capacity as a power of two
     */
    private static int calculateCapacity(final int initialCapacity)
    {
        if (initialCapacity <= 0)
        {
            return DEFAULT_INITIAL_CAPACITY;
        }
        int capacity = 1;
        while (capacity < initialCapacity && capacity < MAXIMUM_CAPACITY)
        {
            capacity <<= 1;
        }
        return Math.max(2, capacity);
    }

    /**
     * Calculates the threshold for resizing based on capacity and load factor.
     *
     * @param capacity table capacity
     * @param loadFactor table load factor
     * @return resize threshold
     */
    private static int calculateThreshold(final int capacity, final float loadFactor)
    {
        return (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY);
    }
}
