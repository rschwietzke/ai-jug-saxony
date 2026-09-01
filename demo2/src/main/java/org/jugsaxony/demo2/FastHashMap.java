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
package org.jugsaxony.demo2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A general-purpose hash map based on open addressing with linear probing.
 * <p>
 * The backing storage consists of two parallel arrays (keys and values) whose
 * length is always a power of two. The map grows automatically (doubling its
 * capacity) when the load factor of 0.75 is exceeded, so it is unbounded.
 * Deletion is performed with backward-shift cluster repair, so no tombstones
 * are used and lookup performance does not degrade after removals.
 * <p>
 * Null keys are not permitted and cause a {@link NullPointerException}.
 * Null values are permitted. A {@code null} return value from {@link #get(Object)}
 * therefore means either "no mapping" or "mapped to null" (same semantics as
 * {@link java.util.HashMap}).
 * <p>
 * This class is <b>not thread-safe</b>.
 *
 * @param <K> the key type
 * @param <V> the value type
 *
 * @author René Schwietzke (Xceptance Software Technologies GmbH)
 */
public class FastHashMap<K, V>
{
    /**
     * Initial capacity of the backing arrays. Must be a power of two.
     */
    private static final int DEFAULT_CAPACITY = 16;

    /**
     * Load factor at which the map grows.
     */
    private static final float LOAD_FACTOR = 0.75f;

    /**
     * The keys. A {@code null} slot means "empty".
     */
    private K[] keys;

    /**
     * The values. Only meaningful where {@link #keys} is non-null; may itself be null.
     */
    private V[] values;

    /**
     * Number of mappings in the map.
     */
    private int size;

    /**
     * Mask for index calculations, always {@code keys.length - 1}.
     */
    private int mask;

    /**
     * Creates an empty map with default initial capacity (16) and load factor (0.75).
     */
    public FastHashMap()
    {
        this.keys = newArray(DEFAULT_CAPACITY);
        this.values = newArray(DEFAULT_CAPACITY);
        this.mask = DEFAULT_CAPACITY - 1;
        this.size = 0;
    }

    /**
     * Returns the value mapped to {@code key}, or {@code null} if there is no mapping
     * (or the mapping is {@code null}).
     *
     * @param key the key to look up, must not be null
     * @return the associated value or {@code null}
     * @throws NullPointerException if {@code key} is null
     */
    public V get(final K key)
    {
        Objects.requireNonNull(key, "key must not be null");

        int pos = index(key);
        K k = keys[pos];

        while (k != null)
        {
            if (k.equals(key))
            {
                return values[pos];
            }
            pos = (pos + 1) & mask;
            k = keys[pos];
        }

        return null;
    }

    /**
     * Associates {@code value} with {@code key}. Replaces an existing mapping.
     * Grows the map when the load factor is exceeded.
     *
     * @param key the key, must not be null
     * @param value the value, may be null
     * @return the previous value associated with {@code key}, or {@code null}
     * @throws NullPointerException if {@code key} is null
     */
    public V put(final K key, final V value)
    {
        Objects.requireNonNull(key, "key must not be null");

        int pos = index(key);
        K k = keys[pos];

        while (k != null)
        {
            if (k.equals(key))
            {
                final V oldValue = values[pos];
                values[pos] = value;
                return oldValue;
            }
            pos = (pos + 1) & mask;
            k = keys[pos];
        }

        // empty slot found -> insert
        keys[pos] = key;
        values[pos] = value;
        size++;

        if (size > (int)(keys.length * LOAD_FACTOR))
        {
            resize(keys.length << 1);
        }

        return null;
    }

    /**
     * Removes the mapping for {@code key} if present. Uses backward-shift
     * cluster repair so no tombstones remain and probe chains stay intact.
     *
     * @param key the key to remove, must not be null
     * @return the previous value associated with {@code key}, or {@code null}
     * @throws NullPointerException if {@code key} is null
     */
    public V remove(final K key)
    {
        Objects.requireNonNull(key, "key must not be null");

        int pos = index(key);
        K k = keys[pos];

        while (k != null)
        {
            if (k.equals(key))
            {
                final V oldValue = values[pos];
                backwardShift(pos);
                size--;
                return oldValue;
            }
            pos = (pos + 1) & mask;
            k = keys[pos];
        }

        return null;
    }

    /**
     * Returns the number of mappings in this map.
     *
     * @return the number of mappings
     */
    public int size()
    {
        return size;
    }

    /**
     * Returns a new list of all keys in internal slot order. The order is
     * unspecified and may change after any mutation. Mutating the returned
     * list does not affect the map.
     *
     * @return a new list containing all keys
     */
    public List<K> keys()
    {
        final List<K> result = new ArrayList<>(size);

        for (int i = 0; i < keys.length; i++)
        {
            if (keys[i] != null)
            {
                result.add(keys[i]);
            }
        }

        return result;
    }

    /**
     * Returns a new list of all values in the same internal order as {@link #keys()}.
     * The list may contain {@code null} elements. Mutating the returned list does
     * not affect the map.
     *
     * @return a new list containing all values
     */
    public List<V> values()
    {
        final List<V> result = new ArrayList<>(size);

        for (int i = 0; i < keys.length; i++)
        {
            if (keys[i] != null)
            {
                result.add(values[i]);
            }
        }

        return result;
    }

    /**
     * Removes all mappings and resets the capacity to the default. The map
     * remains fully usable afterwards.
     */
    public void clear()
    {
        this.keys = newArray(DEFAULT_CAPACITY);
        this.values = newArray(DEFAULT_CAPACITY);
        this.mask = DEFAULT_CAPACITY - 1;
        this.size = 0;
    }

    /**
     * Spreads the hash (same approach as {@link java.util.HashMap}) and maps it
     * to a slot index.
     *
     * @param key the (non-null) key
     * @return the starting slot index
     */
    private int index(final K key)
    {
        final int h = key.hashCode();
        return (h ^ (h >>> 16)) & mask;
    }

    /**
     * Doubles the capacity and rehashes all entries.
     *
     * @param newCapacity the new capacity, must be a power of two
     */
    private void resize(final int newCapacity)
    {
        final K[] oldKeys = this.keys;
        final V[] oldValues = this.values;

        this.keys = newArray(newCapacity);
        this.values = newArray(newCapacity);
        this.mask = newCapacity - 1;
        // size stays the same; entries are reinserted without duplication checks

        for (int i = 0; i < oldKeys.length; i++)
        {
            final K k = oldKeys[i];
            if (k != null)
            {
                int pos = index(k);
                while (keys[pos] != null)
                {
                    pos = (pos + 1) & mask;
                }
                keys[pos] = k;
                values[pos] = oldValues[i];
            }
        }
    }

    /**
     * Deletes the entry at {@code gap} and repairs the probe cluster by moving
     * subsequent entries backward when their home position requires it.
     *
     * @param gap the slot that is being freed
     */
    private void backwardShift(int gap)
    {
        int pos = gap;

        while (true)
        {
            pos = (pos + 1) & mask;

            final K k = keys[pos];
            if (k == null)
            {
                // end of the cluster, close the gap
                keys[gap] = null;
                values[gap] = null;
                return;
            }

            final int home = index(k);
            // If the home slot is not within (gap, pos] (cyclic), the entry at pos
            // would no longer be found if gap stayed empty -> move it into the gap.
            if (!isWithinCyclicRange(gap, pos, home))
            {
                keys[gap] = k;
                values[gap] = values[pos];
                gap = pos;
            }
        }
    }

    /**
     * Returns true if {@code home} lies in the cyclic range ({@code gap}, {@code pos}],
     * i.e. strictly after {@code gap} and up to and including {@code pos} when walking
     * forward with wrap-around. In that case the entry at {@code pos} probes past
     * {@code gap} anyway and must not be moved.
     */
    private boolean isWithinCyclicRange(final int gap, final int pos, final int home)
    {
        if (gap < pos)
        {
            return home > gap && home <= pos;
        }
        // gap wrapped around the end
        return home > gap || home <= pos;
    }

    /**
     * Creates a generic array of the given length.
     *
     * @param length the array length
     * @return the new array
     */
    @SuppressWarnings("unchecked")
    private static <T> T[] newArray(final int length)
    {
        return (T[]) new Object[length];
    }
}
