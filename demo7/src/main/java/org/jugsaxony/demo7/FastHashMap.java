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
package org.jugsaxony.demo7;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A fast and simple hash map based on open hashing (separate chaining).
 * Every bucket holds a singly linked list of entries. New entries are
 * inserted at the head of the chain, lookups compare the cached hash
 * first and call {@code equals()} only on a hash match.
 *
 * The table size is always a power of two and doubles once the load
 * factor of 0.75 is exceeded, which makes the map unbounded. The hash
 * is spread to protect against weak {@code hashCode()} implementations.
 *
 * This map is NOT thread-safe. Null keys are rejected with a
 * {@link NullPointerException}, but null values are fully supported.
 *
 * @since 1.0
 * @author René Schwietzke (Xceptance Software Technologies GmbH)
 */
public class FastHashMap<K, V>
{
    /**
     * Initial table capacity, a power of two.
     */
    private static final int INITIAL_CAPACITY = 16;

    /**
     * Resize when size exceeds capacity times this factor.
     */
    private static final float LOAD_FACTOR = 0.75f;

    /**
     * The bucket table, length is always a power of two.
     */
    private Entry<K, V>[] table;

    /**
     * Mask to calculate the bucket index from the hash.
     */
    private int mask;

    /**
     * Size threshold that triggers a resize.
     */
    private int resizeThreshold;

    /**
     * Number of key-value mappings.
     */
    private int size;

    /**
     * Creates a new empty map with the initial capacity.
     */
    public FastHashMap()
    {
        this.table = createTable(INITIAL_CAPACITY);
        this.mask = INITIAL_CAPACITY - 1;
        this.resizeThreshold = (int) (INITIAL_CAPACITY * LOAD_FACTOR);
        this.size = 0;
    }

    /**
     * Returns the value stored for the key, or null when the key
     * does not exist. A null return value can also mean that the
     * key is mapped to a null value.
     *
     * @param key the key to look up, must not be null
     * @return the value or null
     * @throws NullPointerException if the key is null
     */
    public V get(final K key)
    {
        Objects.requireNonNull(key, "key must not be null");

        final int hash = spread(key.hashCode());

        Entry<K, V> entry = table[hash & mask];
        while (entry != null)
        {
            if (entry.hash == hash && entry.key.equals(key))
            {
                return entry.value;
            }
            entry = entry.next;
        }

        return null;
    }

    /**
     * Stores the value under the given key. If the key already
     * exists, its value is replaced. Null values are allowed.
     *
     * @param key the key, must not be null
     * @param value the value, may be null
     * @return the previous value, or null if the key was new
     * @throws NullPointerException if the key is null
     */
    public V put(final K key, final V value)
    {
        Objects.requireNonNull(key, "key must not be null");

        final int hash = spread(key.hashCode());
        final int index = hash & mask;

        Entry<K, V> entry = table[index];
        while (entry != null)
        {
            if (entry.hash == hash && entry.key.equals(key))
            {
                final V previous = entry.value;
                entry.value = value;
                return previous;
            }
            entry = entry.next;
        }

        table[index] = new Entry<>(key, value, hash, table[index]);
        size++;

        if (size > resizeThreshold)
        {
            resize(table.length << 1);
        }

        return null;
    }

    /**
     * Removes the key and its value from the map.
     *
     * @param key the key to remove, must not be null
     * @return the removed value, or null if the key did not exist
     * @throws NullPointerException if the key is null
     */
    public V remove(final K key)
    {
        Objects.requireNonNull(key, "key must not be null");

        final int hash = spread(key.hashCode());
        final int index = hash & mask;

        Entry<K, V> entry = table[index];
        Entry<K, V> previous = null;

        while (entry != null)
        {
            if (entry.hash == hash && entry.key.equals(key))
            {
                if (previous == null)
                {
                    table[index] = entry.next;
                }
                else
                {
                    previous.next = entry.next;
                }
                size--;

                return entry.value;
            }

            previous = entry;
            entry = entry.next;
        }

        return null;
    }

    /**
     * Returns the number of key-value mappings.
     *
     * @return the size of the map
     */
    public int size()
    {
        return size;
    }

    /**
     * Returns a new list containing all keys. The order is unspecified.
     *
     * @return all keys, empty list for an empty map
     */
    public List<K> keys()
    {
        final ArrayList<K> result = new ArrayList<>(size);

        for (final Entry<K, V> head : table)
        {
            Entry<K, V> entry = head;
            while (entry != null)
            {
                result.add(entry.key);
                entry = entry.next;
            }
        }

        return result;
    }

    /**
     * Returns a new list containing all values. The order is unspecified.
     * Null values are included.
     *
     * @return all values, empty list for an empty map
     */
    public List<V> values()
    {
        final ArrayList<V> result = new ArrayList<>(size);

        for (final Entry<K, V> head : table)
        {
            Entry<K, V> entry = head;
            while (entry != null)
            {
                result.add(entry.value);
                entry = entry.next;
            }
        }

        return result;
    }

    /**
     * Removes all mappings and resets the table to the initial capacity.
     */
    public void clear()
    {
        this.table = createTable(INITIAL_CAPACITY);
        this.mask = INITIAL_CAPACITY - 1;
        this.resizeThreshold = (int) (INITIAL_CAPACITY * LOAD_FACTOR);
        this.size = 0;
    }

    /**
     * Current table capacity. Package-private for testing only.
     *
     * @return the number of buckets
     */
    int capacity()
    {
        return table.length;
    }

    /**
     * Doubles the table and rehashes all entries.
     *
     * @param newCapacity the new capacity, a power of two
     */
    private void resize(final int newCapacity)
    {
        final Entry<K, V>[] newTable = createTable(newCapacity);
        final int newMask = newCapacity - 1;

        for (final Entry<K, V> head : table)
        {
            Entry<K, V> entry = head;
            while (entry != null)
            {
                final Entry<K, V> next = entry.next;
                final int index = entry.hash & newMask;

                entry.next = newTable[index];
                newTable[index] = entry;

                entry = next;
            }
        }

        this.table = newTable;
        this.mask = newMask;
        this.resizeThreshold = (int) (newCapacity * LOAD_FACTOR);
    }

    /**
     * Spreads the hash code to reduce collisions in the lower bits.
     *
     * @param h the original hash code
     * @return the spread hash
     */
    private static int spread(final int h)
    {
        return h ^ (h >>> 16);
    }

    /**
     * Creates a new bucket table.
     *
     * @param capacity the table length
     * @return the new table
     */
    @SuppressWarnings("unchecked")
    private static <K, V> Entry<K, V>[] createTable(final int capacity)
    {
        return new Entry[capacity];
    }

    /**
     * A single key-value entry in a bucket chain.
     */
    private static final class Entry<K, V>
    {
        final K key;
        V value;
        final int hash;
        Entry<K, V> next;

        Entry(final K key, final V value, final int hash, final Entry<K, V> next)
        {
            this.key = key;
            this.value = value;
            this.hash = hash;
            this.next = next;
        }
    }
}
