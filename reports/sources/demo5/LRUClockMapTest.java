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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link LRUClockMap}.
 */
public class LRUClockMapTest
{
    /** A key type that always hashes to the same value to force collisions. */
    private static final class CollisionKey
    {
        final int id;

        CollisionKey(final int id)
        {
            this.id = id;
        }

        @Override
        public int hashCode()
        {
            return 42;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (!(o instanceof CollisionKey other))
            {
                return false;
            }
            return this.id == other.id;
        }
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    @Test
    public void constructorRejectsMaxSizeBelowFour()
    {
        for (final int bad : new int[]
        {
            -1, 0, 1, 2, 3
        })
        {
            assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(bad), "maxSize=" + bad);
        }
    }

    @Test
    public void constructorAcceptsMaxSizeFour()
    {
        assertNotNull(new LRUClockMap<String, String>(4));
    }

    // ------------------------------------------------------------------
    // Basic behavior
    // ------------------------------------------------------------------

    @Test
    public void emptyMap()
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(8);
        assertEquals(0, map.size());
        assertEquals(0, map.trueSize());
        assertNull(map.get("a"));
        assertNull(map.getRaw("a"));
        assertNull(map.remove("a"));
        assertTrue(map.keys().isEmpty());
    }

    @Test
    public void putAndGet()
    {
        final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        assertEquals(3, map.size());
        assertEquals(3, map.trueSize());
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
        assertEquals(3, map.get("c"));
        assertNull(map.get("d"));
    }

    @Test
    public void putReturnsPreviousValue()
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(8);
        assertNull(map.put("a", "1"));
        assertEquals("1", map.put("a", "2"));
        assertEquals("2", map.get("a"));
        assertEquals(1, map.size());
        assertEquals(1, map.trueSize());
    }

    @Test
    public void getRawReadsWithoutLruEffect()
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(8);
        map.put("a", "1");
        assertEquals("1", map.getRaw("a"));
        assertEquals("1", map.getRaw("a"));
        assertEquals(1, map.size());
        assertNull(map.getRaw("missing"));
    }

    // ------------------------------------------------------------------
    // Null handling
    // ------------------------------------------------------------------

    @Test
    public void nullKeyRejectedByAllMethods()
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(8);
        assertThrows(NullPointerException.class, () -> map.get(null));
        assertThrows(NullPointerException.class, () -> map.getRaw(null));
        assertThrows(NullPointerException.class, () -> map.put(null, "x"));
        assertThrows(NullPointerException.class, () -> map.remove(null));
    }

    @Test
    public void nullValueRejectedByPut()
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(8);
        assertThrows(NullPointerException.class, () -> map.put("a", null));
        assertEquals(0, map.size());
    }

    // ------------------------------------------------------------------
    // Removal
    // ------------------------------------------------------------------

    @Test
    public void removeExistingEntry()
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(8);
        map.put("a", "1");
        map.put("b", "2");

        assertEquals("1", map.remove("a"));
        assertEquals(1, map.size());
        assertEquals(1, map.trueSize());
        assertNull(map.get("a"));
        assertEquals("2", map.get("b"));
    }

    @Test
    public void removeAbsentEntry()
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(8);
        map.put("a", "1");
        assertNull(map.remove("missing"));
        assertEquals(1, map.size());
    }

    @Test
    public void removeManyEntriesKeepsMapIntact()
    {
        final LRUClockMap<Integer, String> map = new LRUClockMap<>(256);
        for (int i = 0; i < 200; i++)
        {
            map.put(i, "v" + i);
        }

        for (int i = 0; i < 200; i += 2)
        {
            assertEquals("v" + i, map.remove(i));
        }

        assertEquals(100, map.size());
        for (int i = 0; i < 200; i++)
        {
            if (i % 2 == 0)
            {
                assertNull(map.get(i));
            }
            else
            {
                assertEquals("v" + i, map.get(i));
            }
        }
    }

    // ------------------------------------------------------------------
    // Size and clear
    // ------------------------------------------------------------------

    @Test
    public void clearEmptiesMapAndReusesStorage()
    {
        final LRUClockMap<Integer, String> map = new LRUClockMap<>(8);
        for (int i = 0; i < 8; i++)
        {
            map.put(i, "v" + i);
        }
        final int occupiedBefore = map.occupiedSpace();

        map.clear();

        assertEquals(0, map.size());
        assertEquals(0, map.trueSize());
        assertTrue(map.keys().isEmpty());
        assertNull(map.get(3));
        assertEquals(occupiedBefore, map.occupiedSpace(), "clear must reuse the backing array");

        // still usable after clear
        map.put(1, "one");
        assertEquals(1, map.size());
        assertEquals("one", map.get(1));
    }

    @Test
    public void keysReflectCurrentContents()
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(8);
        map.put("a", "1");
        map.put("b", "2");

        assertEquals(2, map.keys().size());
        assertEquals(Set.of("a", "b"), new HashSet<>(map.keys()));
        assertEquals(2, map.size());
        assertEquals(map.size(), map.trueSize());
    }

    // ------------------------------------------------------------------
    // LRU / second-chance semantics
    // ------------------------------------------------------------------

    /** Finds a present key whose stored second-chance flag has the given value. */
    private static <K, V> K findPresentKeyWithFlag(final LRUClockMap<K, V> map, final boolean flag)
    {
        for (final LRUClockMap<K, V>.DebugWrapper<K, V> wrapper : map.getDebugData())
        {
            if (wrapper != null && wrapper.secondChance == flag)
            {
                return wrapper.key;
            }
        }
        throw new AssertionError("no present entry with secondChance=" + flag);
    }

    /** Returns the stored second-chance flag of the given key. */
    private static <K, V> boolean flagOf(final LRUClockMap<K, V> map, final K key)
    {
        for (final LRUClockMap<K, V>.DebugWrapper<K, V> wrapper : map.getDebugData())
        {
            if (wrapper != null && wrapper.key.equals(key))
            {
                return wrapper.secondChance;
            }
        }
        throw new AssertionError("key not present: " + key);
    }

    @Test
    public void newEntriesStartWithSecondChance()
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(8);
        map.put("a", "1");
        assertTrue(flagOf(map, "a"));
    }

    @Test
    public void getSetsSecondChanceButGetRawDoesNot()
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(4);
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        map.put("d", "4");

        // force several evictions so the clock clears some second-chance flags
        map.put("e", "5");
        map.put("f", "6");
        map.put("g", "7");
        map.put("h", "8");
        map.put("i", "9");

        // there must be at least one present entry with a cleared flag
        final String key = findPresentKeyWithFlag(map, false);
        assertFalse(flagOf(map, key));

        // getRaw must NOT set the flag
        assertNotNull(map.getRaw(key));
        assertFalse(flagOf(map, key));

        // get must set the flag
        assertNotNull(map.get(key));
        assertTrue(flagOf(map, key));

        // getRaw must NOT clear an already-set flag
        assertNotNull(map.getRaw(key));
        assertTrue(flagOf(map, key));
    }

    @Test
    public void evictionKeepsSizeBounded()
    {
        final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(8);
        for (int i = 0; i < 1_000; i++)
        {
            map.put(i, i);
            assertTrue(map.size() <= 8, "size exceeded maxSize at iteration " + i);
        }
        assertEquals(8, map.size());
        assertEquals(8, map.trueSize());
    }

    @Test
    public void lastInsertedEntryAlwaysSurvives()
    {
        final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(8);
        for (int i = 0; i < 1_000; i++)
        {
            map.put(i, i);
            // the just-inserted entry is always present
            assertEquals(Integer.valueOf(i), map.get(i));
            assertEquals(Math.min(8, i + 1), map.size());
        }
    }

    @Test
    public void accessedEntrySurvivesEviction()
    {
        final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(8);
        for (int i = 0; i < 8; i++)
        {
            map.put(i, i);
        }

        // trigger an eviction: this clears the second-chance flag of the surviving entries
        map.put(8, 8);
        assertEquals(8, map.size());

        // pick a surviving entry whose flag the clock has cleared
        final int survivor = findPresentKeyWithFlag(map, false);

        // access it: this must refresh its second-chance flag
        assertNotNull(map.get(survivor));
        assertTrue(flagOf(map, survivor));

        // the next eviction must evict some OTHER (cold) entry, not the accessed one
        map.put(9, 9);
        assertEquals(8, map.size());
        assertEquals(Integer.valueOf(9), map.get(9));
        assertNotNull(map.get(survivor));
    }

    @Test
    public void evictionRemovesExactlyOneEntryPerInsertAtCapacity()
    {
        final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(8);
        for (int i = 0; i < 8; i++)
        {
            map.put(i, i);
        }
        assertEquals(8, map.size());

        // the first insert at capacity evicts exactly one of the original entries
        map.put(8, 8);
        assertEquals(8, map.size());
        assertEquals(Integer.valueOf(8), map.get(8));

        // a second insert evicts exactly one more original entry
        map.put(9, 9);
        assertEquals(8, map.size());
        assertEquals(Integer.valueOf(9), map.get(9));

        // two of the eight original entries must be gone now
        int originalsPresent = 0;
        for (int i = 0; i < 8; i++)
        {
            if (map.get(i) != null)
            {
                originalsPresent++;
            }
        }
        assertEquals(6, originalsPresent);
    }

    // ------------------------------------------------------------------
    // Collision handling
    // ------------------------------------------------------------------

    @Test
    public void collidingKeysAreStoredAndRemovedCorrectly()
    {
        final LRUClockMap<CollisionKey, String> map = new LRUClockMap<>(2_000);
        final java.util.List<CollisionKey> keys = new java.util.ArrayList<>();
        final int count = 1_500;

        for (int i = 0; i < count; i++)
        {
            final CollisionKey key = new CollisionKey(i);
            keys.add(key);
            map.put(key, "v" + i);
        }

        assertEquals(count, map.size());
        for (int i = 0; i < count; i++)
        {
            assertEquals("v" + i, map.get(keys.get(i)));
        }

        // remove every third key and verify the probe chain stays intact
        for (int i = 0; i < count; i += 3)
        {
            assertEquals("v" + i, map.remove(keys.get(i)));
        }

        for (int i = 0; i < count; i++)
        {
            if (i % 3 == 0)
            {
                assertNull(map.get(keys.get(i)));
            }
            else
            {
                assertEquals("v" + i, map.get(keys.get(i)));
            }
        }
    }

    // ------------------------------------------------------------------
    // Large capacity
    // ------------------------------------------------------------------

    @Test
    public void largeCapacityMap()
    {
        final LRUClockMap<Integer, String> map = new LRUClockMap<>(100_000);
        final int count = 60_000;

        for (int i = 0; i < count; i++)
        {
            map.put(i, "v" + i);
        }

        assertEquals(count, map.size());
        for (int i = 0; i < count; i++)
        {
            assertEquals("v" + i, map.get(i));
        }
    }

    // ------------------------------------------------------------------
    // Storage layout
    // ------------------------------------------------------------------

    @Test
    public void occupiedSpaceIsPowerOfTwoAndHasHeadroom()
    {
        for (final int maxSize : new int[]
        {
            4, 5, 10, 100, 1_000, 100_000
        })
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(maxSize);
            final int space = map.occupiedSpace();
            assertTrue(space >= 1, "maxSize=" + maxSize);
            assertEquals(0, space & (space - 1), "occupiedSpace must be a power of two for maxSize=" + maxSize);
            assertTrue(space >= 2 * maxSize, "occupiedSpace must leave headroom for maxSize=" + maxSize);
        }
    }

    // ------------------------------------------------------------------
    // Randomized / oracle tests
    // ------------------------------------------------------------------

    @Test
    public void randomOperationsMatchHashMapWhenNotFull()
    {
        final Random random = new Random(42);
        // large enough maxSize so no eviction ever happens -> exact oracle match
        final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(50_000);
        final Map<Integer, Integer> reference = new HashMap<>();

        for (int i = 0; i < 30_000; i++)
        {
            final int key = random.nextInt(3_000);
            final int op = random.nextInt(10);

            if (op < 5)
            {
                final int value = random.nextInt(1_000_000);
                final Integer expected = reference.put(key, value);
                final Integer actual = map.put(key, value);
                assertEquals(expected, actual, "put mismatch at iteration " + i);
            }
            else if (op < 8)
            {
                final Integer expected = reference.get(key);
                final Integer actual = map.get(key);
                assertEquals(expected, actual, "get mismatch at iteration " + i);
            }
            else
            {
                final Integer expected = reference.remove(key);
                final Integer actual = map.remove(key);
                assertEquals(expected, actual, "remove mismatch at iteration " + i);
            }
        }

        assertEquals(reference.size(), map.size());
        assertEquals(reference.keySet(), new HashSet<>(map.keys()));
        for (final Map.Entry<Integer, Integer> e : reference.entrySet())
        {
            assertEquals(e.getValue(), map.get(e.getKey()));
        }
    }

    @Test
    public void randomCollisionOperationsMatchHashMapWhenNotFull()
    {
        final Random random = new Random(7);
        final LRUClockMap<CollisionKey, Integer> map = new LRUClockMap<>(50_000);
        final Map<CollisionKey, Integer> reference = new HashMap<>();

        for (int i = 0; i < 20_000; i++)
        {
            final CollisionKey key = new CollisionKey(random.nextInt(1_500));
            final int op = random.nextInt(10);

            if (op < 5)
            {
                final int value = random.nextInt(1_000_000);
                final Integer expected = reference.put(key, value);
                final Integer actual = map.put(key, value);
                assertEquals(expected, actual, "put mismatch at iteration " + i);
            }
            else if (op < 8)
            {
                final Integer expected = reference.get(key);
                final Integer actual = map.get(key);
                assertEquals(expected, actual, "get mismatch at iteration " + i);
            }
            else
            {
                final Integer expected = reference.remove(key);
                final Integer actual = map.remove(key);
                assertEquals(expected, actual, "remove mismatch at iteration " + i);
            }
        }

        assertEquals(reference.size(), map.size());
        for (final Map.Entry<CollisionKey, Integer> e : reference.entrySet())
        {
            assertEquals(e.getValue(), map.get(e.getKey()));
        }
    }

    @Test
    public void randomEvictionStaysBounded()
    {
        final Random random = new Random(123);
        final int maxSize = 16;
        final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(maxSize);

        for (int i = 0; i < 50_000; i++)
        {
            final int key = random.nextInt(100);
            if (random.nextBoolean())
            {
                map.put(key, random.nextInt(1_000_000));
            }
            else
            {
                map.remove(key);
            }

            assertTrue(map.size() <= maxSize, "size exceeded maxSize at iteration " + i);

            // every present key must be retrievable
            for (final Integer presentKey : map.keys())
            {
                assertNotNull(map.get(presentKey), "present key not retrievable at iteration " + i);
            }
        }
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    @Test
    public void toStringDoesNotThrow()
    {
        final LRUClockMap<Integer, String> map = new LRUClockMap<>(8);
        map.put(1, "a");
        map.put(2, "b");
        assertNotNull(map.toString());
    }
}
