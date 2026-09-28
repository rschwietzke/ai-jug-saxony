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
package org.jugsaxony.demo3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A dynamically growing hash map using open addressing with linear probing.
 * Null keys are rejected, null values are supported, and instances are not
 * thread-safe.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public final class FastHashMap<K, V>
{
    private static final int DEFAULT_CAPACITY = 16;

    private static final int MAX_CAPACITY = 1 << 30;

    private Object[] keys;

    private Object[] values;

    private int size;

    private int mask;

    private int resizeThreshold;

    /**
     * Creates an empty map.
     */
    public FastHashMap()
    {
        initializeStorage();
    }

    /**
     * Returns the value associated with the key, or {@code null} if there is no
     * mapping or the mapped value is null.
     *
     * @param key the key to find
     * @return the mapped value, or {@code null}
     * @throws NullPointerException if the key is null
     */
    public V get(final K key)
    {
        Objects.requireNonNull(key, "key must not be null");

        int index = initialIndex(key, mask);
        while (keys[index] != null)
        {
            if (keys[index].equals(key))
            {
                return valueAt(index);
            }
            index = nextIndex(index, mask);
        }

        return null;
    }

    /**
     * Adds or replaces a mapping.
     *
     * @param key the key to add
     * @param value the value to associate with the key; may be null
     * @return the previous value, or {@code null} if there was none
     * @throws NullPointerException if the key is null
     */
    public V put(final K key, final V value)
    {
        Objects.requireNonNull(key, "key must not be null");

        int index = findKeyOrEmptySlot(key);
        if (keys[index] != null)
        {
            final V previousValue = valueAt(index);
            values[index] = value;
            return previousValue;
        }

        if (size == resizeThreshold)
        {
            grow();
            index = findKeyOrEmptySlot(key);
        }

        keys[index] = key;
        values[index] = value;
        size++;

        return null;
    }

    /**
     * Removes a mapping if present.
     *
     * @param key the key to remove
     * @return the removed value, or {@code null} if absent or mapped to null
     * @throws NullPointerException if the key is null
     */
    public V remove(final K key)
    {
        Objects.requireNonNull(key, "key must not be null");

        int index = initialIndex(key, mask);
        while (keys[index] != null)
        {
            if (keys[index].equals(key))
            {
                final V previousValue = valueAt(index);
                closeDeletionGap(index);
                size--;
                return previousValue;
            }
            index = nextIndex(index, mask);
        }

        return null;
    }

    /**
     * Returns the number of mappings.
     *
     * @return the number of mappings
     */
    public int size()
    {
        return size;
    }

    /**
     * Returns a mutable snapshot of the current keys in internal slot order.
     *
     * @return the current keys
     */
    public List<K> keys()
    {
        final List<K> result = new ArrayList<>(size);
        for (final Object key : keys)
        {
            if (key != null)
            {
                result.add(castKey(key));
            }
        }
        return result;
    }

    /**
     * Returns a mutable snapshot of the current values in the same internal
     * slot order as {@link #keys()}.
     *
     * @return the current values
     */
    public List<V> values()
    {
        final List<V> result = new ArrayList<>(size);
        for (int i = 0; i < keys.length; i++)
        {
            if (keys[i] != null)
            {
                result.add(valueAt(i));
            }
        }
        return result;
    }

    /**
     * Removes all mappings and restores the default backing capacity.
     */
    public void clear()
    {
        initializeStorage();
    }

    private void initializeStorage()
    {
        final Object[] emptyKeys = new Object[DEFAULT_CAPACITY];
        final Object[] emptyValues = new Object[DEFAULT_CAPACITY];

        keys = emptyKeys;
        values = emptyValues;
        size = 0;
        mask = DEFAULT_CAPACITY - 1;
        resizeThreshold = thresholdFor(DEFAULT_CAPACITY);
    }

    private int findKeyOrEmptySlot(final K key)
    {
        int index = initialIndex(key, mask);
        while (keys[index] != null && !keys[index].equals(key))
        {
            index = nextIndex(index, mask);
        }
        return index;
    }

    private void grow()
    {
        final int oldCapacity = keys.length;
        if (oldCapacity == MAX_CAPACITY)
        {
            if (resizeThreshold < MAX_CAPACITY - 1)
            {
                // No larger power-of-two array exists, but the remaining slots
                // can still be used while one empty probe terminator is kept.
                resizeThreshold = MAX_CAPACITY - 1;
                return;
            }
            throw new IllegalStateException("Maximum hash table capacity reached");
        }

        final int newCapacity = oldCapacity << 1;
        final int newMask = newCapacity - 1;
        final Object[] newKeys = new Object[newCapacity];
        final Object[] newValues = new Object[newCapacity];

        for (int i = 0; i < oldCapacity; i++)
        {
            final Object key = keys[i];
            if (key != null)
            {
                int destination = initialIndex(key, newMask);
                while (newKeys[destination] != null)
                {
                    destination = nextIndex(destination, newMask);
                }
                newKeys[destination] = key;
                newValues[destination] = values[i];
            }
        }

        keys = newKeys;
        values = newValues;
        mask = newMask;
        resizeThreshold = thresholdFor(newCapacity);
    }

    private void closeDeletionGap(int gap)
    {
        int candidate = nextIndex(gap, mask);

        while (keys[candidate] != null)
        {
            final int home = initialIndex(keys[candidate], mask);
            final int distanceToGap = (gap - home) & mask;
            final int distanceToCandidate = (candidate - home) & mask;

            if (distanceToGap < distanceToCandidate)
            {
                keys[gap] = keys[candidate];
                values[gap] = values[candidate];
                gap = candidate;
            }
            candidate = nextIndex(candidate, mask);
        }

        keys[gap] = null;
        values[gap] = null;
    }

    private static int thresholdFor(final int capacity)
    {
        return capacity - (capacity >>> 2);
    }

    private static int initialIndex(final Object key, final int indexMask)
    {
        final int hash = key.hashCode();
        return (hash ^ (hash >>> 16)) & indexMask;
    }

    private static int nextIndex(final int index, final int indexMask)
    {
        return (index + 1) & indexMask;
    }

    @SuppressWarnings("unchecked")
    private K castKey(final Object key)
    {
        return (K) key;
    }

    @SuppressWarnings("unchecked")
    private V valueAt(final int index)
    {
        return (V) values[index];
    }
}
