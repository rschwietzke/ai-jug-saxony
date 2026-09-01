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
package org.jugsaxony.demo5;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A fast hash map based on open addressing with linear probing.
 * Not thread-safe! Null keys are rejected, null values are allowed.
 * The map is unbounded and grows automatically via rehashing once the
 * load factor threshold is exceeded.
 *
 * The backing array always has a power-of-two capacity so that the bucket
 * index can be computed with a bit mask instead of a modulo operation.
 * Removals repair the probe chain by shifting subsequent entries back into
 * the hole, keeping lookups simple and tombstone-free.
 *
 * @since 1.0
 * @author René Schwietzke (Xceptance Software Technologies GmbH)
 */
public class FastHashMap<K, V>
{
    /** Default initial capacity, a power of two. */
    private static final int DEFAULT_CAPACITY = 16;

    /** Load factor that triggers growth/rehash. */
    private static final float LOAD_FACTOR = 0.75f;

    /** Maximum capacity, 2^30. */
    private static final int MAX_CAPACITY = 1 << 30;

    /**
     * The backing array holding the entries. A null slot is an empty slot.
     */
    private Entry<K, V>[] data;

    /**
     * Current number of mappings.
     */
    private int size;

    /**
     * Current capacity, always a power of two.
     */
    private int capacity;

    /**
     * Mask to compute the bucket index: mask == capacity - 1.
     */
    private int mask;

    /**
     * Number of entries that triggers a resize.
     */
    private int threshold;

    /**
     * Creates a new empty map.
     */
    @SuppressWarnings("unchecked")
    public FastHashMap()
    {
        this.capacity = DEFAULT_CAPACITY;
        this.mask = this.capacity - 1;
        this.threshold = (int) (this.capacity * LOAD_FACTOR);
        this.data = (Entry<K, V>[]) new Entry[this.capacity];
    }

    /**
     * Mixes the hash to improve the distribution of the low bits.
     * Inspired by OpenJDK HashMap.
     *
     * @param h the original hash
     * @return the mixed hash
     */
    private static int mix(final int h)
    {
        return h ^ (h >>> 16);
    }

    /**
     * Computes the bucket index for the given mixed hash.
     *
     * @param hash the mixed hash
     * @return the bucket index
     */
    private int index(final int hash)
    {
        return hash & this.mask;
    }

    /**
     * Returns the value for the given key, or null if the key is not present.
     * Because null values are allowed, a null result does not imply absence.
     *
     * @param key the key to look up, must not be null
     * @return the value for the key or null
     */
    public V get(final K key)
    {
        Objects.requireNonNull(key, "Key must not be null");

        final int hash = mix(key.hashCode());
        int idx = index(hash);
        final Entry<K, V>[] localData = this.data;

        while (true)
        {
            final Entry<K, V> entry = localData[idx];
            if (entry == null)
            {
                return null;
            }
            if (entry.hash == hash && entry.key.equals(key))
            {
                return entry.value;
            }

            idx = (idx + 1) & this.mask;
        }
    }

    /**
     * Associates the given value with the given key. If the map already
     * contains a mapping for the key, the old value is replaced.
     *
     * @param key the key, must not be null
     * @param value the value, may be null
     * @return the previous value for the key, or null if there was no mapping
     */
    public V put(final K key, final V value)
    {
        Objects.requireNonNull(key, "Key must not be null");

        final int hash = mix(key.hashCode());
        int idx = index(hash);

        while (true)
        {
            final Entry<K, V> entry = this.data[idx];
            if (entry == null)
            {
                // we found a free slot, this is going to be an insert
                if (this.size + 1 > this.threshold)
                {
                    resize();
                    idx = index(hash);
                    while (this.data[idx] != null)
                    {
                        idx = (idx + 1) & this.mask;
                    }
                }

                this.data[idx] = new Entry<>(key, value, hash);
                this.size++;
                return null;
            }
            else if (entry.hash == hash && entry.key.equals(key))
            {
                // key already present, replace the value
                final V oldValue = entry.value;
                entry.value = value;
                return oldValue;
            }

            idx = (idx + 1) & this.mask;
        }
    }

    /**
     * Removes the mapping for the given key, if present.
     * The probe chain is repaired by shifting subsequent entries back.
     *
     * @param key the key, must not be null
     * @return the previous value for the key, or null if there was no mapping
     */
    public V remove(final K key)
    {
        Objects.requireNonNull(key, "Key must not be null");

        final int hash = mix(key.hashCode());
        int idx = index(hash);

        while (true)
        {
            final Entry<K, V> entry = this.data[idx];
            if (entry == null)
            {
                return null;
            }
            if (entry.hash == hash && entry.key.equals(key))
            {
                final V oldValue = entry.value;
                this.data[idx] = null;
                this.size--;
                shiftBack(idx);
                return oldValue;
            }

            idx = (idx + 1) & this.mask;
        }
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of mappings
     */
    public int size()
    {
        return this.size;
    }

    /**
     * Returns a snapshot of all keys in this map. The order is not specified.
     *
     * @return a list of all keys
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
     * Returns a snapshot of all values in this map. The order is not specified
     * and null values are included.
     *
     * @return a list of all values
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
     * Removes all mappings from this map. The backing storage is reused.
     */
    public void clear()
    {
        Arrays.fill(this.data, null);
        this.size = 0;
    }

    /**
     * Doubles the capacity and rehashes all entries into the new array.
     */
    private void resize()
    {
        if (this.capacity >= MAX_CAPACITY)
        {
            throw new IllegalStateException("Map is too large to grow further");
        }

        final int newCapacity = this.capacity << 1;
        @SuppressWarnings("unchecked")
        final Entry<K, V>[] newData = (Entry<K, V>[]) new Entry[newCapacity];
        final int newMask = newCapacity - 1;

        for (final Entry<K, V> entry : this.data)
        {
            if (entry != null)
            {
                int idx = entry.hash & newMask;
                while (newData[idx] != null)
                {
                    idx = (idx + 1) & newMask;
                }
                newData[idx] = entry;
            }
        }

        this.data = newData;
        this.capacity = newCapacity;
        this.mask = newMask;
        this.threshold = (int) (newCapacity * LOAD_FACTOR);
    }

    /**
     * Repairs the probe chain after a removal at the given position.
     * Subsequent entries that can move back into the hole are shifted,
     * so that lookups never have to skip over deleted slots.
     *
     * @param pos the position that was just removed
     */
    private void shiftBack(final int pos)
    {
        int last = pos;
        int current = pos;

        while (true)
        {
            current = (current + 1) & this.mask;
            final Entry<K, V> entry = this.data[current];
            if (entry == null)
            {
                this.data[last] = null;
                return;
            }

            final int slot = entry.hash & this.mask;
            final boolean inInterval;
            if (last <= current)
            {
                inInterval = slot > last && slot <= current;
            }
            else
            {
                inInterval = slot > last || slot <= current;
            }

            if (!inInterval)
            {
                this.data[last] = entry;
                last = current;
            }
        }
    }

    /**
     * A single key-value mapping. The mixed hash is stored to avoid
     * recomputing it on every lookup.
     */
    private static final class Entry<K, V>
    {
        final K key;
        final int hash;
        V value;

        Entry(final K key, final V value, final int hash)
        {
            this.key = key;
            this.value = value;
            this.hash = hash;
        }
    }
}
