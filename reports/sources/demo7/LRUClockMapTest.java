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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LRUClockMap}.
 *
 * The eviction tests use keys with controlled hash codes. For hash codes
 * smaller than the table capacity, {@code mixHash} is the identity and the
 * home slot equals the hash code, which makes the clock behavior fully
 * deterministic.
 */
class LRUClockMapTest
{
    // ----------------------------------------------------------------
    // Constructor and capacity
    // ----------------------------------------------------------------

    @Test
    void constructorRejectsMaxSizeBelowFour()
    {
        assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(3));
        assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(0));
        assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(-1));
    }

    @Test
    void constructorAcceptsMinimumMaxSize()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
        assertEquals(0, map.size());
        assertEquals(8, map.occupiedSpace());
    }

    @Test
    void occupiedSpaceIsPowerOfTwoOfAtLeastDoubleMaxSize()
    {
        final int[] maxSizes = {4, 5, 7, 100, 1000};
        final int[] expectedCapacities = {8, 16, 16, 256, 2048};

        for (int i = 0; i < maxSizes.length; i++)
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(maxSizes[i]);
            final int capacity = map.occupiedSpace();

            assertEquals(expectedCapacities[i], capacity);
            assertTrue(capacity >= 2 * maxSizes[i]);
            assertTrue((capacity & (capacity - 1)) == 0, "capacity must be a power of two");
        }
    }

    // ----------------------------------------------------------------
    // Basic behavior
    // ----------------------------------------------------------------

    @Test
    void newMapIsEmpty()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);

        assertEquals(0, map.size());
        assertEquals(0, map.trueSize());
        assertTrue(map.keys().isEmpty());
        assertNull(map.get("missing"));
        assertNull(map.getRaw("missing"));
        assertNull(map.remove("missing"));
    }

    @Test
    void putStoresValueAndGetReturnsIt()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);

        assertNull(map.put("a", 1));
        assertEquals(1, map.size());
        assertEquals(1, map.get("a"));
        assertEquals(1, map.getRaw("a"));
    }

    @Test
    void putReplacesExistingValueAndReturnsOldOne()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);

        map.put("a", 1);
        assertEquals(1, map.put("a", 2));
        assertEquals(2, map.get("a"));
        assertEquals(2, map.getRaw("a"));
        assertEquals(1, map.size());
        assertEquals(1, map.trueSize());
    }

    @Test
    void getMissingKeyReturnsNull()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
        map.put("a", 1);

        assertNull(map.get("b"));
        assertNull(map.getRaw("b"));
        assertEquals(1, map.size());
    }

    // ----------------------------------------------------------------
    // Null handling (neither null keys nor null values are allowed)
    // ----------------------------------------------------------------

    @Test
    void nullKeysAreRejected()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
        map.put("a", 1);

        assertThrows(NullPointerException.class, () -> map.put(null, 1));
        assertThrows(NullPointerException.class, () -> map.get(null));
        assertThrows(NullPointerException.class, () -> map.getRaw(null));
        assertThrows(NullPointerException.class, () -> map.remove(null));
        assertEquals(1, map.size());
    }

    @Test
    void nullValuesAreRejected()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);

        assertThrows(NullPointerException.class, () -> map.put("a", null));
        assertEquals(0, map.size());
    }

    // ----------------------------------------------------------------
    // Linear probing and removal repair
    // ----------------------------------------------------------------

    @Test
    void collidingKeysAreFoundViaProbing()
    {
        final LRUClockMap<HashKey, Integer> map = new LRUClockMap<>(4);
        final HashKey x = new HashKey("X", 0);
        final HashKey y = new HashKey("Y", 0);
        final HashKey z = new HashKey("Z", 0);

        map.put(x, 1);
        map.put(y, 2);
        map.put(z, 3);

        assertEquals(3, map.size());
        assertEquals(1, map.get(x));
        assertEquals(2, map.get(y));
        assertEquals(3, map.get(z));
        assertEquals(3, map.getRaw(z));
    }

    @Test
    void removeRepairsProbeChainAfterHeadRemoval()
    {
        final LRUClockMap<HashKey, Integer> map = new LRUClockMap<>(4);
        final HashKey x = new HashKey("X", 0);
        final HashKey y = new HashKey("Y", 0);
        final HashKey z = new HashKey("Z", 0);

        map.put(x, 1);
        map.put(y, 2);
        map.put(z, 3);

        assertEquals(1, map.remove(x));
        assertEquals(2, map.size());
        assertEquals(2, map.trueSize());
        assertNull(map.get(x));
        assertEquals(2, map.get(y));
        assertEquals(3, map.get(z));
    }

    @Test
    void removeRepairsProbeChainAfterMiddleAndTailRemoval()
    {
        final LRUClockMap<HashKey, Integer> map = new LRUClockMap<>(4);
        final HashKey x = new HashKey("X", 0);
        final HashKey y = new HashKey("Y", 0);
        final HashKey z = new HashKey("Z", 0);

        map.put(x, 1);
        map.put(y, 2);
        map.put(z, 3);

        assertEquals(2, map.remove(y));
        assertEquals(2, map.size());
        assertEquals(1, map.get(x));
        assertNull(map.get(y));
        assertEquals(3, map.get(z));

        assertEquals(3, map.remove(z));
        assertEquals(1, map.size());
        assertEquals(1, map.get(x));
        assertNull(map.get(z));
    }

    @Test
    void removeReturnsValueAndShrinksMap()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
        map.put("a", 1);
        map.put("b", 2);

        assertEquals(1, map.remove("a"));
        assertEquals(1, map.size());
        assertEquals(1, map.trueSize());
        assertNull(map.get("a"));
        assertEquals(2, map.get("b"));

        assertNull(map.remove("a"));
        assertEquals(1, map.size());
    }

    // ----------------------------------------------------------------
    // Eviction (clock algorithm with second chance)
    // ----------------------------------------------------------------

    @Test
    void evictionKeepsSizeAtMaxAndDropsUntouchedEntry()
    {
        final LRUClockMap<HashKey, Integer> map = new LRUClockMap<>(4);
        final HashKey a = new HashKey("A", 0);
        final HashKey b = new HashKey("B", 1);
        final HashKey c = new HashKey("C", 2);
        final HashKey d = new HashKey("D", 3);
        final HashKey e = new HashKey("E", 4);

        map.put(a, 1);
        map.put(b, 2);
        map.put(c, 3);
        map.put(d, 4);
        assertEquals(4, map.size());

        // map is full, the clock clears all second chances on the first
        // pass, wraps around, and evicts A
        assertNull(map.put(e, 5));

        assertEquals(4, map.size());
        assertEquals(4, map.trueSize());
        assertNull(map.get(a));
        assertNull(map.getRaw(a));
        assertEquals(2, map.get(b));
        assertEquals(3, map.get(c));
        assertEquals(4, map.get(d));
        assertEquals(5, map.get(e));
    }

    @Test
    void evictionContinuesWhereClockLeftOff()
    {
        final LRUClockMap<HashKey, Integer> map = new LRUClockMap<>(4);
        final HashKey a = new HashKey("A", 0);
        final HashKey b = new HashKey("B", 1);
        final HashKey c = new HashKey("C", 2);
        final HashKey d = new HashKey("D", 3);
        final HashKey e = new HashKey("E", 4);
        final HashKey f = new HashKey("F", 5);

        map.put(a, 1);
        map.put(b, 2);
        map.put(c, 3);
        map.put(d, 4);
        map.put(e, 5); // evicts A, B/C/D lost their second chance

        // next eviction continues at the old clock position and
        // drops B without a full extra round
        map.put(f, 6);

        assertEquals(4, map.size());
        assertNull(map.get(a));
        assertNull(map.get(b));
        assertEquals(3, map.get(c));
        assertEquals(4, map.get(d));
        assertEquals(5, map.get(e));
        assertEquals(6, map.get(f));
    }

    @Test
    void secondChanceProtectsRecentlyUsedEntry()
    {
        final LRUClockMap<HashKey, Integer> map = new LRUClockMap<>(4);
        final HashKey a = new HashKey("A", 0);
        final HashKey b = new HashKey("B", 1);
        final HashKey c = new HashKey("C", 2);
        final HashKey d = new HashKey("D", 3);
        final HashKey e = new HashKey("E", 4);
        final HashKey f = new HashKey("F", 5);

        map.put(a, 1);
        map.put(b, 2);
        map.put(c, 3);
        map.put(d, 4);
        map.put(e, 5); // evicts A, flags of B/C/D are now false

        // touch B so it gets its second chance back
        assertEquals(2, map.get(b));

        // now the clock must skip B and evict C instead
        map.put(f, 6);

        assertEquals(4, map.size());
        assertEquals(2, map.get(b));
        assertNull(map.get(c));
        assertEquals(4, map.get(d));
        assertEquals(5, map.get(e));
        assertEquals(6, map.get(f));
    }

    @Test
    void updateOnFullMapDoesNotEvict()
    {
        final LRUClockMap<HashKey, Integer> map = new LRUClockMap<>(4);
        final HashKey a = new HashKey("A", 0);
        final HashKey b = new HashKey("B", 1);
        final HashKey c = new HashKey("C", 2);
        final HashKey d = new HashKey("D", 3);

        map.put(a, 1);
        map.put(b, 2);
        map.put(c, 3);
        map.put(d, 4);

        assertEquals(3, map.put(c, 99));
        assertEquals(4, map.size());
        assertEquals(99, map.getRaw(c));
        assertEquals(1, map.getRaw(a));
        assertEquals(2, map.getRaw(b));
        assertEquals(4, map.getRaw(d));
    }

    // ----------------------------------------------------------------
    // Second chance flag semantics
    // ----------------------------------------------------------------

    @Test
    void newEntriesStartWithSecondChance()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
        map.put("a", 1);

        assertTrue(flagOf(map, "a"));
    }

    @Test
    void getSetsSecondChanceWhileGetRawDoesNot()
    {
        final LRUClockMap<HashKey, Integer> map = new LRUClockMap<>(4);
        final HashKey a = new HashKey("A", 0);
        final HashKey b = new HashKey("B", 1);
        final HashKey c = new HashKey("C", 2);
        final HashKey d = new HashKey("D", 3);
        final HashKey e = new HashKey("E", 4);

        map.put(a, 1);
        map.put(b, 2);
        map.put(c, 3);
        map.put(d, 4);
        map.put(e, 5); // eviction pass clears the flag of B

        assertFalse(flagOf(map, b));

        assertEquals(2, map.getRaw(b));
        assertFalse(flagOf(map, b), "getRaw must not touch the second chance flag");

        assertEquals(2, map.get(b));
        assertTrue(flagOf(map, b), "get must restore the second chance flag");
    }

    // ----------------------------------------------------------------
    // Keys and clear
    // ----------------------------------------------------------------

    @Test
    void keysListsAllEntriesWithoutDuplicates()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
        final Set<String> expected = Set.of("a", "b", "c", "d", "e");

        for (final String key : expected)
        {
            map.put(key, 1);
        }

        final List<String> keys = map.keys();
        assertEquals(map.size(), keys.size());
        assertEquals(expected, new HashSet<>(keys));
    }

    @Test
    void clearEmptiesMapButKeepsCapacity()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
        map.put("d", 4);

        final int capacityBefore = map.occupiedSpace();
        map.clear();

        assertEquals(0, map.size());
        assertEquals(0, map.trueSize());
        assertTrue(map.keys().isEmpty());
        assertNull(map.get("a"));
        assertNull(map.getRaw("d"));
        assertEquals(capacityBefore, map.occupiedSpace());
    }

    @Test
    void mapIsFullyUsableAfterClearIncludingEviction()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
        for (int i = 0; i < 4; i++)
        {
            map.put("old-" + i, i);
        }
        map.clear();

        for (int i = 0; i < 10; i++)
        {
            map.put("new-" + i, i);
        }

        assertEquals(4, map.size());
        assertEquals(4, map.trueSize());
        // the most recently added entry must always be present
        assertEquals(9, map.getRaw("new-9"));
    }

    // ----------------------------------------------------------------
    // Randomized invariant test
    // ----------------------------------------------------------------

    @Test
    void randomOperationsKeepMapInvariants()
    {
        final int maxSize = 64;
        final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(maxSize);
        final Random random = new Random(7);
        final int keySpace = 256;
        final int operations = 50_000;

        for (int i = 0; i < operations; i++)
        {
            final int key = random.nextInt(keySpace);
            final int action = random.nextInt(4);

            if (action <= 1)
            {
                map.put(key, i);
                // a freshly put entry must always be retrievable,
                // eviction happens before the insert
                assertEquals(i, map.getRaw(key), "freshly put key must be present at op " + i);
            }
            else if (action == 2)
            {
                map.get(key);
            }
            else
            {
                if (map.remove(key) != null)
                {
                    assertNull(map.getRaw(key), "removed key must be gone at op " + i);
                }
            }

            assertTrue(map.size() <= maxSize, "size must never exceed maxSize at op " + i);
            assertEquals(map.size(), map.trueSize(), "size and trueSize must match at op " + i);
        }

        final List<Integer> keys = map.keys();
        assertEquals(map.size(), keys.size());
        assertEquals(keys.size(), new HashSet<>(keys).size(), "keys must be unique");
        for (final Integer key : keys)
        {
            assertNotNull(map.getRaw(key), "every listed key must be retrievable");
        }
    }

    // ----------------------------------------------------------------
    // toString
    // ----------------------------------------------------------------

    @Test
    void toStringContainsMapState()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
        assertTrue(map.toString().contains("LRUClockMap{"));
        assertTrue(map.toString().contains("size: 0"));

        map.put("visible", 42);
        final String dump = map.toString();
        assertTrue(dump.contains("visible"));
        assertTrue(dump.contains("size: 1"));
        assertTrue(dump.contains("maxSize: 4"));
        assertTrue(dump.contains("clockHand:"));
    }

    // ----------------------------------------------------------------
    // Test helpers
    // ----------------------------------------------------------------

    /**
     * Reads the second chance flag of a key via the debug data without
     * influencing the LRU state.
     */
    private static <K, V> boolean flagOf(final LRUClockMap<K, V> map, final K key)
    {
        for (final var wrapper : map.getDebugData())
        {
            if (wrapper != null && key.equals(wrapper.key))
            {
                return wrapper.secondChance;
            }
        }
        return fail("Key not found in debug data: " + key);
    }

    /**
     * Key with a controlled hash code. For hashes smaller than the
     * table capacity, the home slot equals the hash code.
     */
    private static final class HashKey
    {
        private final String name;
        private final int hash;

        HashKey(final String name, final int hash)
        {
            this.name = name;
            this.hash = hash;
        }

        @Override
        public int hashCode()
        {
            return hash;
        }

        @Override
        public boolean equals(final Object other)
        {
            return (other instanceof HashKey that) && this.name.equals(that.name);
        }

        @Override
        public String toString()
        {
            return name;
        }
    }
}
