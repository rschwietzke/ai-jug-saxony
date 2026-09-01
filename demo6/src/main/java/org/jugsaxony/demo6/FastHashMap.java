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
package org.jugsaxony.demo6;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A fast, unbounded hash map. Not thread-safe!
 *
 * <p>This is open addressing with linear probing on top of two parallel arrays, one for the keys and
 * one for the values. There is no entry object, so inserting or updating does not allocate anything,
 * only growing does. Probing only touches the key array, which keeps the hot loop small and CPU cache
 * friendly. The capacity is always a power of two, so wrapping a probe around the end of the table
 * is a mask operation instead of a modulo. The home position of a key comes from Fibonacci hashing,
 * see {@link #homeSlot(Object)} for why that matters so much for a probing map.
 *
 * <p>Removal uses backward shifting instead of tombstones. Tombstones would slowly poison a long
 * living map because heavy add/remove churn fills the table with markers that only a resize can clean
 * up. Backward shifting keeps the occupancy identical to the size at all times, so churn stays flat
 * forever. That is the same approach {@link LRUClockMap} uses.
 *
 * <p><b>Null handling:</b> keys must not be null, values may be null. A null key slot is therefore an
 * unambiguous "this slot is free" marker and null values need no sentinel. The price is that a null
 * return value is ambiguous, because the API has no containsKey:
 *
 * <table border="1">
 * <caption>What a null return value can mean</caption>
 * <tr><th>Call</th><th>null means either ...</th><th>... or</th></tr>
 * <tr><td>{@link #get(Object)}</td><td>there is no mapping for the key</td><td>the key maps to null</td></tr>
 * <tr><td>{@link #put(Object, Object)}</td><td>there was no previous mapping</td><td>the previous value was null</td></tr>
 * <tr><td>{@link #remove(Object)}</td><td>nothing was removed</td><td>a null value was removed</td></tr>
 * </table>
 *
 * <p>Watching {@link #size()} around the call is the only way to tell these cases apart.
 *
 * <p>The iteration order of {@link #keys()} and {@link #values()} is the internal slot order. It is
 * deterministic for a given map state but unspecified to the caller, so never rely on insertion order.
 *
 * <p>As with any hash map, the hash code and the equality of a key must not change while the key is
 * in the map. A mutated key makes its entry unreachable. This is not enforced.
 *
 * @param <K> the type of the keys, null is not permitted
 * @param <V> the type of the values, null is permitted
 *
 * @author René Schwietzke (Xceptance Software Technologies GmbH)
 */
public class FastHashMap<K, V>
{
    /**
     * The capacity we start with. Small enough to stay cheap for tiny maps, large enough
     * to save the first two growth rounds.
     */
    private static final int DEFAULT_CAPACITY = 16;

    /**
     * How full we let the table get before we grow. Linear probing degrades sharply beyond
     * 0.7, 0.5 keeps the average probe length at about 1.5. We deliberately pay memory for speed.
     */
    private static final float LOAD_FACTOR = 0.5f;

    /**
     * The largest power of two array we can allocate.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The golden ratio as a 64 bit fixed point number, the multiplier for Fibonacci hashing.
     */
    private static final long FIBONACCI = 0x9E3779B97F4A7C15L;

    /**
     * The keys. A null entry means the slot is free. Because null keys are not permitted,
     * this is never ambiguous.
     */
    private Object[] keys;

    /**
     * The values, same length and same index as {@link #keys}. May hold null for an
     * occupied slot, because null values are permitted.
     */
    private Object[] values;

    /**
     * The number of entries in this map.
     */
    private int size;

    /**
     * Mask to wrap a probe position around the end of the table, always capacity - 1.
     */
    private int mask;

    /**
     * How many bits of the hash we use, capacity is always 1 &lt;&lt; shift.
     */
    private int shift;

    /**
     * When the size reaches this, we grow. Precalculated to keep put free of float math.
     */
    private int threshold;

    /**
     * Creates a new empty map with the default capacity. The map grows as needed.
     */
    public FastHashMap()
    {
        this.keys = new Object[DEFAULT_CAPACITY];
        this.values = new Object[DEFAULT_CAPACITY];
        this.mask = DEFAULT_CAPACITY - 1;
        this.shift = Integer.numberOfTrailingZeros(DEFAULT_CAPACITY);
        this.threshold = (int) (DEFAULT_CAPACITY * LOAD_FACTOR);
    }

    /**
     * Calculates the home position of a key, which is where its probe chain starts.
     *
     * <p>This is Fibonacci hashing: multiply by the golden ratio and keep the top bits of the
     * product, which is one multiplication and one shift. Taking the top bits matters, the low bits
     * of a product are barely mixed at all.
     *
     * <p>A cheap fold like {@code h ^ (h >>> 16)} plus a mask is not good enough here. It maps
     * consecutive keys to consecutive slots, which is perfect for a chaining map but poison for
     * linear probing: a dense key range then forms one single cluster covering the whole occupied
     * area, and removing from it degenerates to O(n) because the chain repair has to walk that
     * cluster. Fibonacci hashing spreads consecutive keys evenly over the table, so clusters stay
     * short no matter how regular the keys are.
     *
     * @param key the key to place, must not be null
     * @return the home position of that key in the current table
     */
    int homeSlot(final Object key)
    {
        return (int) ((key.hashCode() * FIBONACCI) >>> (64 - this.shift));
    }

    /**
     * Returns the value the given key is mapped to.
     *
     * @param key the key to look for, must not be null
     * @return the value for the key, null if there is no mapping or the mapping is to null
     * @throws NullPointerException if the key is null
     */
    @SuppressWarnings("unchecked")
    public V get(final K key)
    {
        Objects.requireNonNull(key, "Key must not be null");

        final int mask = this.mask;
        int ptr = homeSlot(key);

        while (true)
        {
            final Object k = this.keys[ptr];

            if (k == null)
            {
                // a free slot ends every probe chain, so the key cannot be behind it
                return null;
            }

            // identity first, that is the cheap path for interned strings, enums and cached boxes
            if (k == key || k.equals(key))
            {
                return (V) this.values[ptr];
            }

            ptr = (ptr + 1) & mask;
        }
    }

    /**
     * Associates the given value with the given key. An existing mapping is replaced.
     *
     * @param key the key, must not be null
     * @param value the value, may be null
     * @return the previous value, null if there was no mapping or the previous value was null
     * @throws NullPointerException if the key is null
     * @throws IllegalStateException if the map has to grow beyond the maximum capacity
     */
    @SuppressWarnings("unchecked")
    public V put(final K key, final V value)
    {
        Objects.requireNonNull(key, "Key must not be null");

        // grow first, so we never insert into a table that is about to be thrown away and
        // never have to redo the search afterwards. This may grow one update too early, which
        // is a rare and cheap mistake compared to keeping the position valid across a resize.
        if (this.size + 1 > this.threshold)
        {
            grow();
        }

        final int mask = this.mask;
        int ptr = homeSlot(key);

        while (true)
        {
            final Object k = this.keys[ptr];

            if (k == null)
            {
                // free slot, this is an insert
                this.keys[ptr] = key;
                this.values[ptr] = value;
                this.size++;

                return null;
            }

            if (k == key || k.equals(key))
            {
                // key is already here, this is an update, the size does not change
                final V oldValue = (V) this.values[ptr];
                this.values[ptr] = value;

                return oldValue;
            }

            ptr = (ptr + 1) & mask;
        }
    }

    /**
     * Removes the mapping for the given key if it is present.
     *
     * @param key the key to remove, must not be null
     * @return the previous value, null if there was no mapping or the removed value was null
     * @throws NullPointerException if the key is null
     */
    @SuppressWarnings("unchecked")
    public V remove(final K key)
    {
        Objects.requireNonNull(key, "Key must not be null");

        final int mask = this.mask;
        int ptr = homeSlot(key);

        while (true)
        {
            final Object k = this.keys[ptr];

            if (k == null)
            {
                // not here
                return null;
            }

            if (k == key || k.equals(key))
            {
                final V oldValue = (V) this.values[ptr];

                // free this entry and repair the probe chain
                freePositionAndAdjustArray(ptr);

                return oldValue;
            }

            ptr = (ptr + 1) & mask;
        }
    }

    /**
     * Frees a position and repairs the probe chains behind it. Because we probe linearly, a hole
     * in the middle of a cluster would cut off everything behind it. So we lift every entry of the
     * run that follows the hole out and let it fall back into the earliest free slot from its home
     * position. An entry either keeps its place or moves into the hole, and the hole moves along
     * with us until the cluster ends. No tombstones needed.
     *
     * @param ptr the position to free
     */
    private void freePositionAndAdjustArray(final int ptr)
    {
        this.keys[ptr] = null;
        this.values[ptr] = null;
        this.size--;

        int currentPtr = ptr;
        while (true)
        {
            currentPtr = (currentPtr + 1) & this.mask;

            final Object k = this.keys[currentPtr];
            if (k == null)
            {
                // end of the cluster, all chains are intact again
                break;
            }

            final Object v = this.values[currentPtr];

            // lift it out, so the reinsert can also use this very slot again
            this.keys[currentPtr] = null;
            this.values[currentPtr] = null;

            reinsert(k, v);
        }
    }

    /**
     * Puts an entry into the first free slot starting at its home position. We do not compare keys
     * here because the caller guarantees that this key is currently not in the map, either because
     * it was just lifted out of it or because we are refilling a fresh table.
     *
     * @param key the key to place
     * @param value the value to place
     */
    private void reinsert(final Object key, final Object value)
    {
        int ptr = homeSlot(key);

        while (this.keys[ptr] != null)
        {
            ptr = (ptr + 1) & this.mask;
        }

        this.keys[ptr] = key;
        this.values[ptr] = value;
    }

    /**
     * Doubles the capacity. We only ever grow, never shrink, because a map that shrinks on removal
     * would thrash under add and remove churn.
     *
     * @throws IllegalStateException if we would have to grow beyond the maximum capacity
     */
    private void grow()
    {
        final int newCapacity = this.keys.length << 1;

        if (newCapacity > MAXIMUM_CAPACITY || newCapacity <= 0)
        {
            throw new IllegalStateException("Map is too large, cannot grow beyond " + MAXIMUM_CAPACITY + " slots");
        }

        resize(newCapacity);
    }

    /**
     * Moves all entries into a new table of the given capacity.
     *
     * @param newCapacity the new capacity, must be a power of two
     */
    private void resize(final int newCapacity)
    {
        final Object[] oldKeys = this.keys;
        final Object[] oldValues = this.values;

        this.keys = new Object[newCapacity];
        this.values = new Object[newCapacity];
        this.mask = newCapacity - 1;
        this.shift = Integer.numberOfTrailingZeros(newCapacity);
        this.threshold = (int) (newCapacity * LOAD_FACTOR);

        final int length = oldKeys.length;
        for (int i = 0; i < length; i++)
        {
            final Object k = oldKeys[i];
            if (k != null)
            {
                reinsert(k, oldValues[i]);
            }
        }
    }

    /**
     * Returns the number of key-value mappings in this map. Entries with a null value count too.
     *
     * @return the number of entries in the map
     */
    public int size()
    {
        return this.size;
    }

    /**
     * Returns all keys as a new list. The list is a snapshot, changing it does not change the map
     * and later changes to the map do not change the list. The order is the internal slot order and
     * hence unspecified. The list never contains null and never contains duplicates.
     *
     * @return a new list with all keys
     */
    @SuppressWarnings("unchecked")
    public List<K> keys()
    {
        final List<K> result = new ArrayList<>(this.size);

        final int length = this.keys.length;
        for (int i = 0; i < length; i++)
        {
            final Object k = this.keys[i];
            if (k != null)
            {
                result.add((K) k);
            }
        }

        return result;
    }

    /**
     * Returns all values as a new list. The list is a snapshot, see {@link #keys()}. It may contain
     * null and it may contain duplicates. As long as the map is not modified in between, the value
     * at index i belongs to the key at index i of {@link #keys()}, because both walk the table in
     * the same slot order.
     *
     * @return a new list with all values
     */
    @SuppressWarnings("unchecked")
    public List<V> values()
    {
        final List<V> result = new ArrayList<>(this.size);

        final int length = this.keys.length;
        for (int i = 0; i < length; i++)
        {
            // the key decides whether a slot is occupied, the value may legally be null
            if (this.keys[i] != null)
            {
                result.add((V) this.values[i]);
            }
        }

        return result;
    }

    /**
     * Removes all entries. The current capacity is kept, because a cleared map is usually refilled
     * and we would only have to grow again. Both arrays are cleared, so no key or value is kept
     * alive by this map.
     */
    public void clear()
    {
        Arrays.fill(this.keys, null);
        Arrays.fill(this.values, null);
        this.size = 0;
    }

    /**
     * The size of the backing arrays. For testing purposes.
     *
     * @return the current capacity
     */
    int capacity()
    {
        return this.keys.length;
    }

    /**
     * Counts the occupied slots. Must always match {@link #size()}, otherwise we leaked a slot
     * somewhere. For testing purposes.
     *
     * @return the number of occupied slots
     */
    int trueSize()
    {
        int count = 0;

        for (final Object k : this.keys)
        {
            if (k != null)
            {
                count++;
            }
        }

        return count;
    }

    /**
     * The length of the longest run of occupied slots. This is the number the cost of a removal
     * depends on, because repairing the chains has to walk the run behind the freed slot. A healthy
     * table has short runs, a table with a bad hash function has one huge one. For testing purposes.
     *
     * @return the length of the longest cluster, capacity if the table were completely full
     */
    int longestCluster()
    {
        final int length = this.keys.length;

        // start at a free slot, otherwise we would cut a wrapping cluster in two
        int start = 0;
        while (start < length && this.keys[start] != null)
        {
            start++;
        }
        if (start == length)
        {
            return length;
        }

        int longest = 0;
        int current = 0;
        for (int i = 1; i <= length; i++)
        {
            if (this.keys[(start + i) & this.mask] != null)
            {
                current++;
                longest = Math.max(longest, current);
            }
            else
            {
                current = 0;
            }
        }

        return longest;
    }

    /**
     * Verifies that no probe chain has a gap, which is the invariant that makes lookups correct.
     * For every occupied slot, all slots from its home position up to it must be occupied as well.
     * Also verifies that a free slot does not keep a value reference alive. For testing purposes.
     *
     * @return true if all chains are intact
     */
    boolean checkChainInvariant()
    {
        final int length = this.keys.length;

        for (int i = 0; i < length; i++)
        {
            final Object k = this.keys[i];

            if (k == null)
            {
                // a free slot must not hold on to a value
                if (this.values[i] != null)
                {
                    return false;
                }
                continue;
            }

            int ptr = homeSlot(k);
            while (ptr != i)
            {
                if (this.keys[ptr] == null)
                {
                    // a gap between home position and the real position, this entry is unreachable
                    return false;
                }
                ptr = (ptr + 1) & this.mask;
            }
        }

        return true;
    }

    /**
     * For debugging purposes. Caps the output for large maps.
     */
    @Override
    public String toString()
    {
        final var sj = new StringJoiner(",\n", "FastHashMap{\n", "\n}");

        final int length = Math.min(this.keys.length, 1024);
        for (int i = 0; i < length; i++)
        {
            final Object k = this.keys[i];
            if (k == null)
            {
                sj.add(i + " FREE");
            }
            else
            {
                sj.add(i + " [" + k + ", " + this.values[i] + ", home " + homeSlot(k) + "]");
            }
        }
        sj.add("size: " + this.size);
        sj.add("capacity: " + this.keys.length);

        return sj.toString();
    }
}
