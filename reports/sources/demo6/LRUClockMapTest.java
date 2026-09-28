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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The test suite for {@link LRUClockMap}.
 *
 * <p>Two groups of properties are under test here. The hash map part is exact and can be pinned
 * down: what goes in comes out, the size is bounded by maxSize, no slot ever leaks and no probe
 * chain ever gets a gap. The LRU part is deliberately approximate, the class says so itself, so it
 * is tested for the guarantees the clock algorithm actually gives instead of for strict LRU order:
 * an untouched entry dies within one turnover, a hot working set is kept, and a read marks an entry
 * while {@code getRaw} does not.
 *
 * <p>One measured limit is worth knowing before reading L17 and L18: the protection a read buys only
 * works from roughly 24 entries upwards. Below that the clock hand wraps around so quickly that it
 * can clear an entry's flag and evict it in the same sweep, so on a map of 4 or 8 a hot entry is not
 * kept at all. That is the "not really predictable" the class doc warns about, and the tests assert
 * the strong property only for the sizes where it actually holds.
 *
 * @author René Schwietzke (Xceptance Software Technologies GmbH)
 */
class LRUClockMapTest
{
    /**
     * Asserts the internal invariants. Cheap enough to call after every interesting step.
     *
     * <p>The chain check is the important one. The map probes linearly, so every entry must be
     * reachable from its home position without running into a free slot on the way. A removal or an
     * eviction that forgets to shift the followers back would break exactly this.
     *
     * @param map the map to check
     * @param maxSize the maximum size the map was built with
     */
    private static <K, V> void assertHealthy(final LRUClockMap<K, V> map, final int maxSize)
    {
        assertEquals(map.size(), map.trueSize(), "size and the number of occupied slots differ");
        assertTrue(map.size() <= maxSize, "the map holds more than maxSize entries");
        assertEquals(1, Integer.bitCount(map.occupiedSpace()), "the capacity is not a power of two");
        assertTrue(map.size() < map.occupiedSpace(), "the table is completely full, probing cannot terminate");

        final List<LRUClockMap<K, V>.DebugWrapper<K, V>> slots = map.getDebugData();
        final int length = slots.size();

        for (int i = 0; i < length; i++)
        {
            final LRUClockMap<K, V>.DebugWrapper<K, V> w = slots.get(i);
            if (w == null)
            {
                continue;
            }

            int ptr = w.truePosition;
            while (ptr != i)
            {
                assertNotNull(slots.get(ptr), "the chain from " + w.truePosition + " to " + i + " has a gap at " + ptr);
                ptr = (ptr + 1) % length;
            }
        }

        // the keys the map reports must be exactly the occupied slots, and they must be unique
        final List<K> keys = map.keys();
        assertEquals(map.size(), keys.size(), "keys() does not match the size");
        assertEquals(map.size(), new HashSet<>(keys).size(), "keys() contains duplicates");
    }

    /**
     * Reads the second chance flag of a key.
     *
     * @param map the map to look into
     * @param key the key to look for, must be in the map
     * @return the second chance flag of that entry
     */
    private static <K, V> boolean secondChanceOf(final LRUClockMap<K, V> map, final K key)
    {
        for (final LRUClockMap<K, V>.DebugWrapper<K, V> w : map.getDebugData())
        {
            if (w != null && w.key.equals(key))
            {
                return w.secondChance;
            }
        }

        throw new AssertionError("Key " + key + " is not in the map");
    }

    /**
     * The home position of a key, which is where its probe chain starts.
     *
     * @param map the map to look into
     * @param key the key to look for, must be in the map
     * @return the home position of that entry
     */
    private static <K, V> int homeOf(final LRUClockMap<K, V> map, final K key)
    {
        for (final LRUClockMap<K, V>.DebugWrapper<K, V> w : map.getDebugData())
        {
            if (w != null && w.key.equals(key))
            {
                return w.truePosition;
            }
        }

        throw new AssertionError("Key " + key + " is not in the map");
    }

    /**
     * The length of the longest run of occupied slots. This is what the cost of a removal and of an
     * eviction depends on, because repairing the probe chains has to walk the run behind the freed
     * slot. A healthy table has short runs.
     *
     * @param map the map to measure
     * @return the length of the longest cluster
     */
    private static <K, V> int longestCluster(final LRUClockMap<K, V> map)
    {
        final List<LRUClockMap<K, V>.DebugWrapper<K, V>> slots = map.getDebugData();
        final int length = slots.size();

        // start at a free slot, otherwise we would cut a wrapping cluster in two
        int start = 0;
        while (start < length && slots.get(start) != null)
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
            if (slots.get((start + i) % length) != null)
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
     * Fills a map with maxSize entries named k0..k(maxSize-1) with values v0..v(maxSize-1).
     *
     * @param maxSize the size of the map to build
     * @return a full map
     */
    private static LRUClockMap<String, String> filled(final int maxSize)
    {
        final LRUClockMap<String, String> map = new LRUClockMap<>(maxSize);

        for (int i = 0; i < maxSize; i++)
        {
            map.put("k" + i, "v" + i);
        }

        return map;
    }

    // -------------------------------------------------------------------------------- A. Construction

    @Nested
    @DisplayName("A. Construction")
    class Construction
    {
        @ParameterizedTest(name = "L01 maxSize {0}")
        @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, 2, 3})
        @DisplayName("L01 a maxSize below four is rejected")
        void tooSmallMaxSize(final int maxSize)
        {
            final var exception = assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<String, String>(maxSize));
            assertEquals("MaxSize must be at least 4", exception.getMessage());
        }

        @Test
        @DisplayName("L02 four is the smallest legal maxSize and works")
        void smallestLegalMaxSize()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);

            for (int i = 0; i < 4; i++)
            {
                map.put("k" + i, "v" + i);
            }

            assertEquals(4, map.size());
            for (int i = 0; i < 4; i++)
            {
                assertEquals("v" + i, map.get("k" + i));
            }
            assertHealthy(map, 4);
        }

        @ParameterizedTest(name = "L03 maxSize {0}")
        @ValueSource(ints = {4, 5, 7, 8, 9, 16, 100, 1000})
        @DisplayName("L03 the table is a power of two with room for maxSize at a load of 0.5")
        void tableSize(final int maxSize)
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(maxSize);

            assertEquals(1, Integer.bitCount(map.occupiedSpace()), "capacity is not a power of two");
            assertTrue(map.occupiedSpace() >= 2 * maxSize, "the table is too small for a load factor of 0.5");
        }

        @Test
        @DisplayName("L04 a fresh map is empty and answers everything with null")
        void freshMapIsEmpty()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);

            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
            assertTrue(map.keys().isEmpty());
            assertNull(map.get("nothing"));
            assertNull(map.getRaw("nothing"));
            assertNull(map.remove("nothing"));
            assertHealthy(map, 8);
        }
    }

    // ------------------------------------------------------------------------------ B. Put and get

    @Nested
    @DisplayName("B. Put and get")
    class PutAndGet
    {
        @Test
        @DisplayName("L05 a single put can be read back by both getters")
        void singleRoundTrip()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);

            assertNull(map.put("key", "value"), "a new key has no previous value");

            assertEquals("value", map.get("key"));
            assertEquals("value", map.getRaw("key"));
            assertEquals(1, map.size());
            assertHealthy(map, 8);
        }

        @Test
        @DisplayName("L06 put returns the previous value and an update does not change the size")
        void putReturnsPreviousValue()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);

            assertNull(map.put("k", "v1"));
            assertEquals("v1", map.put("k", "v2"));
            assertEquals("v2", map.put("k", "v3"));

            assertEquals("v3", map.get("k"));
            assertEquals(1, map.size(), "all of this was one and the same entry");
            assertHealthy(map, 8);
        }

        @Test
        @DisplayName("L07 many entries below maxSize are all retrievable")
        void manyEntries()
        {
            final int maxSize = 1000;
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(maxSize);

            for (int i = 0; i < maxSize; i++)
            {
                assertNull(map.put("key" + i, i));
            }

            assertEquals(maxSize, map.size());
            for (int i = 0; i < maxSize; i++)
            {
                assertEquals(i, map.get("key" + i), "key" + i + " is gone");
            }
            assertHealthy(map, maxSize);
        }

        @Test
        @DisplayName("L08 an equal but not identical key finds the entry")
        void equalButNotIdenticalKey()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);

            final String inserted = "abc";
            final String lookup = new String("abc");
            assertNotSame(inserted, lookup, "the test needs two different instances");

            map.put(inserted, "value");

            assertEquals("value", map.get(lookup));
            assertEquals("value", map.put(lookup, "other"), "the equal key must hit the same entry");
            assertEquals(1, map.size());
            assertSame(inserted, map.keys().get(0), "the originally stored key instance is kept");
        }

        @Test
        @DisplayName("L09 an unknown key returns null from both getters")
        void unknownKey()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);
            map.put("a", "1");
            map.put("b", "2");

            assertNull(map.get("c"));
            assertNull(map.getRaw("c"));
            assertEquals(2, map.size(), "a miss must not change the map");
        }
    }

    // ---------------------------------------------------------------------------- C. Null handling

    @Nested
    @DisplayName("C. Null handling")
    class NullHandling
    {
        @Test
        @DisplayName("L10 a null key is rejected everywhere")
        void nullKeysAreRejected()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);

            assertEquals("Key must not be null",
                         assertThrows(NullPointerException.class, () -> map.get(null)).getMessage());
            assertEquals("Key must not be null",
                         assertThrows(NullPointerException.class, () -> map.getRaw(null)).getMessage());
            assertEquals("Key must not be null",
                         assertThrows(NullPointerException.class, () -> map.put(null, "v")).getMessage());
            assertEquals("Key must not be null",
                         assertThrows(NullPointerException.class, () -> map.remove(null)).getMessage());

            assertEquals(0, map.size(), "a rejected call must not change the map");
        }

        @Test
        @DisplayName("L11 a null value is rejected")
        void nullValuesAreRejected()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);

            assertEquals("Value must not be null",
                         assertThrows(NullPointerException.class, () -> map.put("k", null)).getMessage());

            assertEquals(0, map.size());

            // and also for a key that is already there
            map.put("k", "v");
            assertThrows(NullPointerException.class, () -> map.put("k", null));
            assertEquals("v", map.get("k"), "the old value must survive a rejected update");
            assertEquals(1, map.size());
        }
    }

    // ------------------------------------------------------------------------ D. Bounds and eviction

    @Nested
    @DisplayName("D. Bounds and eviction")
    class BoundsAndEviction
    {
        @Test
        @DisplayName("L12 one entry too many evicts exactly one entry")
        void oneTooMany()
        {
            final int maxSize = 8;
            final LRUClockMap<String, String> map = filled(maxSize);

            map.put("newcomer", "new");

            assertEquals(maxSize, map.size(), "the map must stay at maxSize");
            assertEquals("new", map.get("newcomer"), "the entry we just inserted must be there");

            // exactly one of the original keys is gone
            int survivors = 0;
            for (int i = 0; i < maxSize; i++)
            {
                if (map.getRaw("k" + i) != null)
                {
                    survivors++;
                }
            }
            assertEquals(maxSize - 1, survivors, "exactly one original entry should have been evicted");
            assertHealthy(map, maxSize);
        }

        @ParameterizedTest(name = "L13 maxSize {0}")
        @ValueSource(ints = {4, 5, 8, 17, 64})
        @DisplayName("L13 the size never exceeds maxSize, no matter how much we insert")
        void sizeStaysBounded(final int maxSize)
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(maxSize);

            for (int i = 0; i < 20 * maxSize; i++)
            {
                map.put("k" + i, "v" + i);

                assertTrue(map.size() <= maxSize, "the map grew beyond maxSize in round " + i);
                assertEquals(map.size(), map.trueSize(), "a slot leaked in round " + i);
            }

            assertEquals(maxSize, map.size(), "the map should be full");
            assertHealthy(map, maxSize);
        }

        @Test
        @DisplayName("L14 every key the map still reports is retrievable with the right value")
        void survivorsAreConsistent()
        {
            final int maxSize = 16;
            final LRUClockMap<String, String> map = new LRUClockMap<>(maxSize);

            for (int i = 0; i < 500; i++)
            {
                map.put("k" + i, "v" + i);
            }

            final List<String> keys = map.keys();
            assertEquals(maxSize, keys.size());

            for (final String key : keys)
            {
                final String expected = "v" + key.substring(1);
                assertEquals(expected, map.getRaw(key), "the survivor " + key + " has a wrong value");
            }
            assertHealthy(map, maxSize);
        }

        @Test
        @DisplayName("L15 updating an existing key on a full map does not evict anything")
        void updateOnFullMapDoesNotEvict()
        {
            final int maxSize = 8;
            final LRUClockMap<String, String> map = filled(maxSize);

            assertEquals("v3", map.put("k3", "updated"), "this is an update, not an insert");

            assertEquals(maxSize, map.size());
            assertEquals("updated", map.getRaw("k3"));

            // nobody was thrown out for an update
            for (int i = 0; i < maxSize; i++)
            {
                assertNotNull(map.getRaw("k" + i), "k" + i + " was evicted by a plain update");
            }
            assertHealthy(map, maxSize);
        }

        @Test
        @DisplayName("L16 an untouched entry is gone within one turnover")
        void untouchedEntriesDie()
        {
            final int maxSize = 8;
            final LRUClockMap<String, String> map = filled(maxSize);

            // one full turnover, every round evicts exactly one entry, so nothing of the
            // original fill can survive this
            for (int i = 0; i < maxSize; i++)
            {
                map.put("new" + i, "n" + i);
            }

            for (int i = 0; i < maxSize; i++)
            {
                assertNull(map.getRaw("k" + i), "k" + i + " survived a complete turnover without being touched");
            }
            assertEquals(maxSize, map.size());
            assertHealthy(map, maxSize);
        }

        /**
         * Builds a map that holds a hot set, an equally large cold set and some filler, then streams
         * new keys through it while reading the hot set on every round.
         *
         * @param maxSize the size of the map to use
         * @param rounds how many new keys to push through
         * @return how many of the hot and how many of the cold entries survived
         */
        private int[] hotAndColdSurvivors(final int maxSize, final int rounds)
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(maxSize);
            final int hotCount = maxSize / 4;

            for (int i = 0; i < hotCount; i++)
            {
                map.put("hot" + i, "h" + i);
                map.put("cold" + i, "c" + i);
            }
            for (int i = 0; i < maxSize - 2 * hotCount; i++)
            {
                map.put("filler" + i, "f" + i);
            }

            for (int round = 0; round < rounds; round++)
            {
                // touch the hot set before the insert, so the entries are marked when the
                // eviction happens
                for (int i = 0; i < hotCount; i++)
                {
                    map.get("hot" + i);
                }
                map.put("new" + round, "n" + round);
            }

            int hot = 0;
            int cold = 0;
            for (int i = 0; i < hotCount; i++)
            {
                if (map.getRaw("hot" + i) != null)
                {
                    hot++;
                }
                if (map.getRaw("cold" + i) != null)
                {
                    cold++;
                }
            }

            assertHealthy(map, maxSize);

            return new int[] {hot, cold};
        }

        @ParameterizedTest(name = "L17 maxSize {0}")
        @ValueSource(ints = {24, 32, 128, 1000})
        @DisplayName("L17 a hot working set survives while untouched entries are evicted")
        void aHotWorkingSetSurvives(final int maxSize)
        {
            final int hotCount = maxSize / 4;
            final int[] survivors = hotAndColdSurvivors(maxSize, 2000);

            assertEquals(hotCount, survivors[0], "the hot working set should have been kept completely");
            assertEquals(0, survivors[1], "the untouched entries should be long gone");
        }

        @ParameterizedTest(name = "L18 maxSize {0}")
        @ValueSource(ints = {4, 8, 12, 16, 20})
        @DisplayName("L18 on a small map the clock protects less, but never less than nothing")
        void smallMapsGiveWeakerProtection(final int maxSize)
        {
            // This pins down the limit the class documents as "not really predictable". On a small
            // map the hand needs only a few steps to wrap around, so it can clear an entry's flag
            // and come back to evict it within the very same sweep, before the entry had a chance
            // to be read again. Measured: up to a maxSize of 8 a hot entry gets no protection at
            // all, from 24 upwards it is kept reliably, in between it is partial. So all a small
            // map guarantees is that touching an entry never hurts.
            final int[] survivors = hotAndColdSurvivors(maxSize, 2000);

            assertTrue(survivors[0] >= survivors[1],
                       "touching entries made things worse, hot " + survivors[0] + " cold " + survivors[1]);
        }
    }

    // -------------------------------------------------------------------- E. The second chance flag

    @Nested
    @DisplayName("E. The second chance flag")
    class SecondChance
    {
        @Test
        @DisplayName("L19 a fresh entry starts with its second chance")
        void freshEntryHasSecondChance()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);
            map.put("k", "v");

            assertTrue(secondChanceOf(map, "k"), "a new entry should not be evicted right away");
        }

        @Test
        @DisplayName("L20 get sets the flag, getRaw does not")
        void getMarksAndGetRawDoesNot()
        {
            final int maxSize = 8;
            final LRUClockMap<String, String> map = filled(maxSize);

            // force a clock sweep, it clears the flags of everything the hand passes
            map.put("newcomer", "new");

            final String cleared = findKeyWithoutSecondChance(map);

            // a raw read must leave the flag alone, that is the whole point of getRaw
            assertNotNull(map.getRaw(cleared));
            assertFalse(secondChanceOf(map, cleared), "getRaw must not give a second chance");

            // a normal read gives the entry its second chance back
            assertNotNull(map.get(cleared));
            assertTrue(secondChanceOf(map, cleared), "get must give a second chance");
        }

        @Test
        @DisplayName("L21 an update through put sets the flag as well")
        void putMarks()
        {
            final int maxSize = 8;
            final LRUClockMap<String, String> map = filled(maxSize);
            map.put("newcomer", "new");

            final String cleared = findKeyWithoutSecondChance(map);

            map.put(cleared, "updated");

            assertTrue(secondChanceOf(map, cleared), "writing to an entry must give it a second chance");
            assertEquals("updated", map.getRaw(cleared));
        }

        @Test
        @DisplayName("L22 the clock clears flags as it sweeps")
        void theClockClearsFlags()
        {
            final int maxSize = 8;
            final LRUClockMap<String, String> map = filled(maxSize);

            // everything that was just inserted carries its second chance
            for (int i = 0; i < maxSize; i++)
            {
                assertTrue(secondChanceOf(map, "k" + i), "k" + i + " should still have its second chance");
            }

            // the first eviction has to sweep the whole table to find a victim, so it clears
            // the flags on the way
            map.put("newcomer", "new");

            int cleared = 0;
            for (final String key : map.keys())
            {
                if (!secondChanceOf(map, key))
                {
                    cleared++;
                }
            }
            assertTrue(cleared > 0, "the clock sweep did not clear a single flag");
        }

        /**
         * Finds a key whose second chance flag is not set.
         *
         * @param map the map to search
         * @return the first key without a second chance
         */
        private String findKeyWithoutSecondChance(final LRUClockMap<String, String> map)
        {
            for (final String key : map.keys())
            {
                if (!secondChanceOf(map, key))
                {
                    return key;
                }
            }

            throw new AssertionError("No entry without a second chance found");
        }
    }

    // -------------------------------------------------------------- F. Removal and chain repair

    @Nested
    @DisplayName("F. Removal and chain repair")
    class Removal
    {
        @Test
        @DisplayName("L23 removing an existing key returns its value and shrinks the map")
        void removeExisting()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);
            map.put("a", "1");
            map.put("b", "2");

            assertEquals("1", map.remove("a"));

            assertEquals(1, map.size());
            assertNull(map.get("a"));
            assertEquals("2", map.get("b"), "the other entry is untouched");
            assertHealthy(map, 8);
        }

        @Test
        @DisplayName("L24 removing an unknown key changes nothing")
        void removeUnknown()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);
            map.put("a", "1");

            assertNull(map.remove("b"));
            assertEquals(1, map.size());
            assertEquals("1", map.get("a"));
        }

        @Test
        @DisplayName("L25 removing twice returns null the second time")
        void removeTwice()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);
            map.put("a", "1");

            assertEquals("1", map.remove("a"));
            assertNull(map.remove("a"));
            assertEquals(0, map.size());
            assertHealthy(map, 8);
        }

        @Test
        @DisplayName("L26 removing in the middle of a collision chain keeps the rest reachable")
        void removeInTheMiddleOfAChain()
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);

            // all four share a home position, so they occupy four consecutive slots
            final FixedHashKey a = new FixedHashKey("a", 2);
            final FixedHashKey b = new FixedHashKey("b", 2);
            final FixedHashKey c = new FixedHashKey("c", 2);
            final FixedHashKey d = new FixedHashKey("d", 2);

            map.put(a, "1");
            map.put(b, "2");
            map.put(c, "3");
            map.put(d, "4");
            assertEquals(homeOf(map, a), homeOf(map, d), "the test needs colliding keys");

            assertEquals("2", map.remove(b));

            assertEquals(3, map.size());
            assertEquals("1", map.getRaw(a));
            assertNull(map.getRaw(b));
            assertEquals("3", map.getRaw(c), "c must have been shifted back");
            assertEquals("4", map.getRaw(d), "d must have been shifted back");
            assertHealthy(map, 4);
        }

        @Test
        @DisplayName("L27 the head and the tail of a chain can be removed")
        void removeHeadAndTailOfAChain()
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);

            final FixedHashKey a = new FixedHashKey("a", 3);
            final FixedHashKey b = new FixedHashKey("b", 3);
            final FixedHashKey c = new FixedHashKey("c", 3);

            map.put(a, "1");
            map.put(b, "2");
            map.put(c, "3");

            // the head, this is the one that forces the shifting
            assertEquals("1", map.remove(a));
            assertEquals("2", map.getRaw(b));
            assertEquals("3", map.getRaw(c));
            assertHealthy(map, 4);

            // the tail, nothing has to move here
            assertEquals("3", map.remove(c));
            assertEquals("2", map.getRaw(b));
            assertEquals(1, map.size());
            assertHealthy(map, 4);
        }

        @Test
        @DisplayName("L28 a cluster that wraps around the end of the table survives a removal")
        void removeInsideAWrappedCluster()
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);

            // put them all on the very last slot, so the cluster wraps around to 0, 1 and 2
            final int hash = map.occupiedSpace() - 1;
            final FixedHashKey a = new FixedHashKey("a", hash);
            final FixedHashKey b = new FixedHashKey("b", hash);
            final FixedHashKey c = new FixedHashKey("c", hash);
            final FixedHashKey d = new FixedHashKey("d", hash);

            map.put(a, "1");
            map.put(b, "2");
            map.put(c, "3");
            map.put(d, "4");
            assertEquals(map.occupiedSpace() - 1, homeOf(map, a), "the test needs a wrapping cluster");

            // b sits on slot 0, so the repair has to shift across the array boundary
            assertEquals("2", map.remove(b));
            assertEquals("1", map.getRaw(a));
            assertEquals("3", map.getRaw(c));
            assertEquals("4", map.getRaw(d));
            assertHealthy(map, 4);

            // and now the entry on the very last slot
            assertEquals("1", map.remove(a));
            assertEquals("3", map.getRaw(c));
            assertEquals("4", map.getRaw(d));
            assertEquals(2, map.size());
            assertHealthy(map, 4);
        }

        @Test
        @DisplayName("L29 a removed key can be inserted again")
        void reinsertAfterRemoval()
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);

            final FixedHashKey a = new FixedHashKey("a", 5);
            final FixedHashKey b = new FixedHashKey("b", 5);

            map.put(a, "1");
            map.put(b, "2");
            map.remove(a);

            assertNull(map.put(a, "1again"), "after the removal this is a fresh insert");
            assertEquals("1again", map.getRaw(a));
            assertEquals("2", map.getRaw(b));
            assertEquals(2, map.size());
            assertHealthy(map, 4);
        }

        @Test
        @DisplayName("L30 heavy add and remove churn does not leak slots")
        void churnDoesNotLeakSlots()
        {
            final int maxSize = 32;
            final LRUClockMap<Integer, String> map = new LRUClockMap<>(maxSize);

            for (int i = 0; i < maxSize; i++)
            {
                map.put(i, "v" + i);
            }

            for (int round = 0; round < 20_000; round++)
            {
                final int key = round % maxSize;

                assertEquals("v" + key, map.remove(key));
                assertNull(map.put(key, "v" + key));
                assertEquals(maxSize, map.size(), "the working set changed in round " + round);
            }

            assertEquals(maxSize, map.trueSize(), "occupancy must equal the size");
            assertHealthy(map, maxSize);
        }
    }

    // --------------------------------------------------------------------------- G. keys and clear

    @Nested
    @DisplayName("G. keys() and clear()")
    class KeysAndClear
    {
        @Test
        @DisplayName("L31 keys reports exactly what is in the map")
        void keysReportsTheContent()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(100);
            final Map<String, String> reference = new HashMap<>();

            for (int i = 0; i < 50; i++)
            {
                map.put("k" + i, "v" + i);
                reference.put("k" + i, "v" + i);
            }
            for (int i = 0; i < 50; i += 3)
            {
                map.remove("k" + i);
                reference.remove("k" + i);
            }

            assertEquals(reference.keySet(), new HashSet<>(map.keys()));
            assertEquals(reference.size(), map.size());
            assertFalse(map.keys().contains(null), "keys must never contain null");
        }

        @Test
        @DisplayName("L32 the list keys returns is a snapshot")
        void keysIsASnapshot()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);
            map.put("a", "1");
            map.put("b", "2");

            final List<String> keys = map.keys();
            keys.clear();

            assertEquals(2, map.size(), "clearing the returned list must not clear the map");

            final List<String> before = map.keys();
            map.put("c", "3");
            map.remove("a");

            assertEquals(2, before.size(), "an earlier snapshot must not change");
            assertTrue(before.contains("a"));
            assertFalse(before.contains("c"));
        }

        @Test
        @DisplayName("L33 clear empties the map and keeps the capacity")
        void clearEmptiesTheMap()
        {
            final int maxSize = 16;
            final LRUClockMap<String, String> map = filled(maxSize);
            final int capacityBefore = map.occupiedSpace();

            map.clear();

            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
            assertTrue(map.keys().isEmpty());
            assertEquals(capacityBefore, map.occupiedSpace(), "the table is kept for the next fill");

            for (int i = 0; i < maxSize; i++)
            {
                assertNull(map.getRaw("k" + i));
            }
            assertHealthy(map, maxSize);
        }

        @Test
        @DisplayName("L34 the map is fully usable after a clear, eviction included")
        void usableAfterClear()
        {
            final int maxSize = 8;
            final LRUClockMap<String, String> map = filled(maxSize);
            map.clear();

            assertNull(map.put("k0", "again"), "after a clear this is a fresh insert");
            assertEquals("again", map.get("k0"));
            assertEquals("again", map.remove("k0"));
            assertEquals(0, map.size());

            // and the clock still works after a clear
            for (int i = 0; i < 5 * maxSize; i++)
            {
                map.put("n" + i, "v" + i);
                assertTrue(map.size() <= maxSize, "the map grew beyond maxSize in round " + i);
            }
            assertEquals(maxSize, map.size());
            assertHealthy(map, maxSize);
        }

        @Test
        @DisplayName("L35 toString describes the table without blowing up")
        void toStringWorks()
        {
            final LRUClockMap<String, String> map = filled(8);
            map.remove("k1");

            final String text = map.toString();

            assertTrue(text.startsWith("LRUClockMap{"), "unexpected start: " + text);
            assertTrue(text.contains("size: 7"), "the size is missing: " + text);
            assertTrue(text.contains("FREE"), "the free slots are missing: " + text);
            assertTrue(text.contains("k0"), "the entries are missing: " + text);
        }
    }

    // ------------------------------------------------------------------------- H. Mixed workloads

    @Nested
    @DisplayName("H. Mixed workloads")
    class MixedWorkloads
    {
        @Test
        @DisplayName("L36 colliding keys and eviction work together")
        void collisionsAndEviction()
        {
            final int maxSize = 8;
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(maxSize);

            // every single key lands on the same home position, so the table holds one long chain
            // that the eviction has to cut holes into and repair again
            for (int i = 0; i < 200; i++)
            {
                map.put(new FixedHashKey("k" + i, 0), "v" + i);

                assertTrue(map.size() <= maxSize, "the map grew beyond maxSize in round " + i);
                assertEquals(map.size(), map.trueSize(), "a slot leaked in round " + i);
            }

            assertEquals(maxSize, map.size());
            for (final FixedHashKey key : map.keys())
            {
                assertNotNull(map.getRaw(key), "the survivor " + key + " is not reachable any more");
            }
            assertHealthy(map, maxSize);
        }

        @ParameterizedTest(name = "L37 seed {0}")
        @ValueSource(longs = {1L, 42L, 4711L})
        @DisplayName("L37 a long random workload keeps the map consistent")
        void randomWorkload(final long seed)
        {
            final int maxSize = 64;
            final int keySpace = 500;

            final Random random = new Random(seed);
            final LRUClockMap<Integer, String> map = new LRUClockMap<>(maxSize);

            // we cannot mirror this into a HashMap, because we never know which entry the clock
            // picks. What we can check on every step: the map stays within its bounds, nothing
            // leaks, and everything it still reports is correct and reachable
            for (int i = 0; i < 20_000; i++)
            {
                final Integer key = random.nextInt(keySpace);
                final int operation = random.nextInt(10);

                if (operation < 6)
                {
                    map.put(key, "v" + key);
                }
                else if (operation < 8)
                {
                    final String value = map.get(key);
                    if (value != null)
                    {
                        assertEquals("v" + key, value, "wrong value for " + key + " in round " + i);
                    }
                }
                else
                {
                    final String value = map.remove(key);
                    if (value != null)
                    {
                        assertEquals("v" + key, value, "wrong value removed for " + key + " in round " + i);
                    }
                    assertNull(map.getRaw(key), "the removed key " + key + " is still there in round " + i);
                }

                assertTrue(map.size() <= maxSize, "the map grew beyond maxSize in round " + i);
                assertEquals(map.size(), map.trueSize(), "a slot leaked in round " + i);
            }

            // and after all of that every survivor is still reachable through the probe chains
            final List<Integer> keys = map.keys();
            for (final Integer key : keys)
            {
                assertEquals("v" + key, map.getRaw(key), "the survivor " + key + " is not reachable any more");
            }
            assertHealthy(map, maxSize);
        }

        @Test
        @DisplayName("L38 a working set that fits is never evicted")
        void aFittingWorkingSetSurvives()
        {
            final int maxSize = 16;
            final LRUClockMap<String, String> map = new LRUClockMap<>(maxSize);

            for (int i = 0; i < maxSize; i++)
            {
                map.put("k" + i, "v" + i);
            }

            // reading and rewriting the same set of entries must never push one of them out,
            // there is simply no reason to evict anything
            for (int round = 0; round < 1000; round++)
            {
                final String key = "k" + (round % maxSize);

                assertEquals("v" + (round % maxSize), map.get(key), "the working set lost " + key);
                map.put(key, "v" + (round % maxSize));
            }

            assertEquals(maxSize, map.size());
            assertHealthy(map, maxSize);
        }

        @Test
        @Tag("slow")
        @DisplayName("L39 a large map stays bounded and consistent")
        void largeMap()
        {
            final int maxSize = 100_000;
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(maxSize);

            // string keys on purpose, their hash codes are spread out. With a dense range of
            // Integer keys this test would not finish, see L40
            for (int i = 0; i < 500_000; i++)
            {
                map.put("key" + i, i);
            }

            assertEquals(maxSize, map.size());
            assertEquals(maxSize, map.trueSize());

            for (final String key : new ArrayList<>(map.keys()))
            {
                assertEquals(Integer.valueOf(key.substring(3)), map.getRaw(key),
                             "the survivor " + key + " is not reachable any more");
            }
            assertHealthy(map, maxSize);
        }

        @Test
        @Disabled("Known defect, remove this line to reproduce. A dense range of keys collapses into "
                  + "one single cluster, which makes every eviction walk the whole map.")
        @DisplayName("L40 a dense range of keys must not collapse into one single cluster")
        void sequentialKeysMustNotFormOneCluster()
        {
            final int maxSize = 10_000;
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(maxSize);

            for (int i = 0; i < maxSize; i++)
            {
                map.put(i, i);
            }

            // mixHash folds the high bits down and the result is masked, so consecutive keys get
            // consecutive slots. For a chaining map that is ideal, for this linear probing map it
            // means a dense key range forms one contiguous run as long as the map itself. Lookups
            // are still fine, but evict() and remove() have to repair that run every single time,
            // so the cost of an eviction grows with maxSize instead of staying constant.
            //
            // Measured with sequential Integer keys, 10.000 evicting inserts:
            //   maxSize   1.000 -> cluster  1.000, 44 ms
            //   maxSize  10.000 -> cluster 10.000, 350 ms
            //   maxSize  50.000 -> cluster 50.000, 7.067 ms
            //
            // The fix would be a hash that spreads consecutive keys, for example Fibonacci
            // hashing as used by FastHashMap.homeSlot in this package.
            assertTrue(longestCluster(map) < 100,
                       "the longest cluster is " + longestCluster(map) + " slots for " + map.size() + " entries");
        }
    }
}
