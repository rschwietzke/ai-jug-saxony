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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The test suite for {@link FastHashMap}.
 *
 * <p>Two things get verified everywhere and not just in their own test: the map never leaks a slot
 * ({@code size() == trueSize()}, which proves there are no tombstones) and no probe chain ever has a
 * gap ({@code checkChainInvariant()}, which is what makes lookups correct after a removal).
 *
 * @author René Schwietzke (Xceptance Software Technologies GmbH)
 */
class FastHashMapTest
{
    /**
     * Asserts the internal invariants of the map. Cheap enough to call after every interesting step.
     *
     * @param map the map to check
     */
    private static void assertHealthy(final FastHashMap<?, ?> map)
    {
        assertEquals(map.size(), map.trueSize(), "size and occupied slots differ");
        assertTrue(map.checkChainInvariant(), "a probe chain has a gap");
        assertEquals(1, Integer.bitCount(map.capacity()), "capacity is not a power of two");
        assertTrue(map.size() <= map.capacity() / 2, "table is fuller than the load factor allows");
    }

    /**
     * Finds a hash code whose home position is the wanted slot, so a test can place an entry on an
     * exact slot without knowing how the map hashes.
     *
     * @param map the map to ask, its current capacity decides the answer
     * @param slot the wanted home slot
     * @return a hash code that lands on that slot
     */
    private static int hashForSlot(final FastHashMap<FixedHashKey, ?> map, final int slot)
    {
        for (int hash = 0; hash < 1_000_000; hash++)
        {
            if (map.homeSlot(new FixedHashKey("probe", hash)) == slot)
            {
                return hash;
            }
        }

        throw new IllegalStateException("No hash found for slot " + slot);
    }

    /**
     * Counts occurrences, so we can compare value collections that may contain duplicates and nulls.
     *
     * @param values the values to count
     * @return a map from value to count
     */
    private static Map<String, Integer> multiset(final Collection<String> values)
    {
        final Map<String, Integer> result = new HashMap<>();
        for (final String v : values)
        {
            result.merge(v, 1, Integer::sum);
        }
        return result;
    }

    // ---------------------------------------------------------------- A. Construction & empty state

    @Nested
    @DisplayName("A. Construction and empty state")
    class ConstructionAndEmptyState
    {
        @Test
        @DisplayName("T01 a fresh map is empty and answers everything with null")
        void freshMapIsEmpty()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertTrue(map.values().isEmpty());
            assertNull(map.get("nothing"));
            assertNull(map.remove("nothing"));
            assertEquals(0, map.size());

            // clearing an empty map is a no-op and leaves it usable
            map.clear();
            assertEquals(0, map.size());
            assertHealthy(map);
        }
    }

    // ------------------------------------------------------------------------- B. Basic put and get

    @Nested
    @DisplayName("B. Basic put and get")
    class PutAndGet
    {
        @Test
        @DisplayName("T02 a single put can be read back")
        void singleRoundTrip()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();

            map.put("key", "value");

            assertEquals("value", map.get("key"));
            assertEquals(1, map.size());
            assertHealthy(map);
        }

        @Test
        @DisplayName("T03 put returns the previous value and does not grow the size on an overwrite")
        void putReturnsPreviousValue()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();

            assertNull(map.put("k", "v1"), "a new key has no previous value");
            assertEquals(1, map.size());

            assertEquals("v1", map.put("k", "v2"));
            assertEquals(1, map.size(), "an overwrite must not change the size");
            assertEquals("v2", map.get("k"));

            assertEquals("v2", map.put("k", "v3"));
            assertEquals("v3", map.get("k"));
            assertEquals(1, map.size());
            assertHealthy(map);
        }

        @Test
        @DisplayName("T04 many distinct keys are all retrievable")
        void manyDistinctKeys()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();

            for (int i = 0; i < 1000; i++)
            {
                assertNull(map.put("key" + i, i));
            }

            assertEquals(1000, map.size());
            for (int i = 0; i < 1000; i++)
            {
                assertEquals(i, map.get("key" + i), "key" + i + " is gone");
            }
            assertHealthy(map);
        }

        @Test
        @DisplayName("T05 an equal but not identical key finds the entry")
        void equalButNotIdenticalKey()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();

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
        @DisplayName("T06 an unknown key returns null")
        void unknownKey()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("a", "1");
            map.put("b", "2");

            assertNull(map.get("c"));
            assertEquals(2, map.size());
        }
    }

    // ------------------------------------------------------------------------------ C. Null handling

    @Nested
    @DisplayName("C. Null handling")
    class NullHandling
    {
        @Test
        @DisplayName("T07 a null value is a real mapping and counts")
        void nullValueIsAMapping()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();

            assertNull(map.put("k", null));

            assertEquals(1, map.size(), "a null value is still an entry");
            assertNull(map.get("k"));
            assertEquals(List.of("k"), map.keys());

            final List<String> values = map.values();
            assertEquals(1, values.size());
            assertNull(values.get(0));
            assertHealthy(map);
        }

        @Test
        @DisplayName("T08 null values can be overwritten in both directions")
        void overwriteNullValues()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();

            assertNull(map.put("k", null));
            assertNull(map.put("k", "v"), "the previous value was null");
            assertEquals("v", map.get("k"));

            assertEquals("v", map.put("k", null));
            assertNull(map.get("k"));
            assertEquals(1, map.size(), "all of this was one and the same entry");
            assertHealthy(map);
        }

        @Test
        @DisplayName("T09 removing a null valued entry returns null but decrements the size")
        void removeNullValuedEntry()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("k", null);

            assertNull(map.remove("k"));
            assertEquals(0, map.size(), "the size is the only way to see that something was removed");
            assertHealthy(map);
        }

        @Test
        @DisplayName("T10 null keys are rejected by get, put and remove")
        void nullKeysAreRejected()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();

            final var getException = assertThrows(NullPointerException.class, () -> map.get(null));
            assertEquals("Key must not be null", getException.getMessage());

            final var putException = assertThrows(NullPointerException.class, () -> map.put(null, "v"));
            assertEquals("Key must not be null", putException.getMessage());

            final var removeException = assertThrows(NullPointerException.class, () -> map.remove(null));
            assertEquals("Key must not be null", removeException.getMessage());

            assertEquals(0, map.size(), "a rejected call must not change the map");
        }

        @Test
        @DisplayName("T11 a null value does not turn its slot into a free slot")
        void nullValueDoesNotFreeTheSlot()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();

            // both share a home position, so b sits behind a in the same probe chain
            final FixedHashKey a = new FixedHashKey("a", 5);
            final FixedHashKey b = new FixedHashKey("b", 5);

            map.put(a, null);
            map.put(b, "b-value");

            // if a null value was mistaken for an empty slot, the probe would stop at a and miss b
            assertEquals("b-value", map.get(b));
            assertEquals(2, map.size());
            assertEquals(2, map.trueSize());
            assertHealthy(map);
        }
    }

    // --------------------------------------------------------------- D. Removal and chain repair

    @Nested
    @DisplayName("D. Removal and chain repair")
    class Removal
    {
        @Test
        @DisplayName("T12 removing an existing key returns its value and shrinks the map")
        void removeExisting()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("a", "1");
            map.put("b", "2");

            assertEquals("1", map.remove("a"));
            assertEquals(1, map.size());
            assertNull(map.get("a"));
            assertEquals("2", map.get("b"), "the other entry is untouched");
            assertHealthy(map);
        }

        @Test
        @DisplayName("T13 removing an unknown key changes nothing")
        void removeUnknown()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("a", "1");

            assertNull(map.remove("b"));
            assertEquals(1, map.size());
            assertEquals("1", map.get("a"));
            assertHealthy(map);
        }

        @Test
        @DisplayName("T14 removing twice returns null the second time")
        void removeTwice()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("a", "1");

            assertEquals("1", map.remove("a"));
            assertNull(map.remove("a"));
            assertEquals(0, map.size());
            assertHealthy(map);
        }

        @Test
        @DisplayName("T15 removing in the middle of a collision chain keeps the rest reachable")
        void removeInTheMiddleOfAChain()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();

            // all four share a home position and therefore occupy four consecutive slots
            final FixedHashKey a = new FixedHashKey("a", 5);
            final FixedHashKey b = new FixedHashKey("b", 5);
            final FixedHashKey c = new FixedHashKey("c", 5);
            final FixedHashKey d = new FixedHashKey("d", 5);

            map.put(a, "1");
            map.put(b, "2");
            map.put(c, "3");
            map.put(d, "4");

            assertEquals("2", map.remove(b));

            assertEquals(3, map.size());
            assertEquals("1", map.get(a));
            assertNull(map.get(b));
            assertEquals("3", map.get(c), "c must have been shifted back");
            assertEquals("4", map.get(d), "d must have been shifted back");
            assertHealthy(map);
        }

        @Test
        @DisplayName("T16 the first and the last element of a chain can be removed")
        void removeFirstAndLastOfAChain()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();

            final FixedHashKey a = new FixedHashKey("a", 3);
            final FixedHashKey b = new FixedHashKey("b", 3);
            final FixedHashKey c = new FixedHashKey("c", 3);

            map.put(a, "1");
            map.put(b, "2");
            map.put(c, "3");

            // the head of the chain, this is the one that forces the shifting
            assertEquals("1", map.remove(a));
            assertEquals("2", map.get(b));
            assertEquals("3", map.get(c));
            assertHealthy(map);

            // the tail of the chain, nothing has to move here
            assertEquals("3", map.remove(c));
            assertEquals("2", map.get(b));
            assertEquals(1, map.size());
            assertHealthy(map);
        }

        @Test
        @DisplayName("T17 a cluster that wraps around the end of the table survives a removal")
        void removeInsideAWrappedCluster()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();

            // put them all on the very last slot, so the cluster wraps around to 0, 1 and 2
            final int hash = hashForSlot(map, map.capacity() - 1);
            final FixedHashKey a = new FixedHashKey("a", hash);
            final FixedHashKey b = new FixedHashKey("b", hash);
            final FixedHashKey c = new FixedHashKey("c", hash);
            final FixedHashKey d = new FixedHashKey("d", hash);

            map.put(a, "1");
            map.put(b, "2");
            map.put(c, "3");
            map.put(d, "4");

            // b sits on slot 0, so the repair has to shift across the array boundary
            assertEquals(map.capacity() - 1, map.homeSlot(b), "the test needs a wrapping cluster");
            assertEquals("2", map.remove(b));

            assertEquals("1", map.get(a));
            assertEquals("3", map.get(c));
            assertEquals("4", map.get(d));
            assertHealthy(map);

            // and now the entry on the very last slot
            assertEquals("1", map.remove(a));
            assertEquals("3", map.get(c));
            assertEquals("4", map.get(d));
            assertEquals(2, map.size());
            assertHealthy(map);
        }

        @Test
        @DisplayName("T18 a removed key can be inserted again")
        void reinsertAfterRemoval()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();

            final FixedHashKey a = new FixedHashKey("a", 7);
            final FixedHashKey b = new FixedHashKey("b", 7);

            map.put(a, "1");
            map.put(b, "2");
            map.remove(a);

            assertNull(map.put(a, "1again"), "after the removal this is a fresh insert");
            assertEquals("1again", map.get(a));
            assertEquals("2", map.get(b));
            assertEquals(2, map.size());
            assertHealthy(map);
        }

        @Test
        @DisplayName("T19 heavy churn does not leak slots and does not grow the table")
        void churnDoesNotLeakSlots()
        {
            final FastHashMap<Integer, String> map = new FastHashMap<>();

            // fill up to a stable working set first
            for (int i = 0; i < 50; i++)
            {
                map.put(i, "v" + i);
            }
            final int capacityAfterFill = map.capacity();

            // now add and remove forever on the same key space, a tombstone based map would
            // slowly fill up here and be forced to grow
            for (int round = 0; round < 100_000; round++)
            {
                final int key = round % 50;
                assertEquals("v" + key, map.remove(key));
                assertNull(map.put(key, "v" + key));
            }

            assertEquals(50, map.size());
            assertEquals(50, map.trueSize(), "occupancy must equal the size, so no tombstones");
            assertEquals(capacityAfterFill, map.capacity(), "churn must not grow the table");
            assertHealthy(map);
        }
    }

    // ------------------------------------------------------------------------------------ E. Growth

    @Nested
    @DisplayName("E. Growth")
    class Growth
    {
        @Test
        @DisplayName("T20 crossing the resize threshold keeps all entries")
        void crossTheThreshold()
        {
            final FastHashMap<Integer, String> map = new FastHashMap<>();
            final int startCapacity = map.capacity();

            // the default capacity is 16 with a load factor of 0.5, so this forces a resize
            for (int i = 0; i < 20; i++)
            {
                map.put(i, "v" + i);
                assertHealthy(map);
            }

            assertTrue(map.capacity() > startCapacity, "the map should have grown");
            assertEquals(20, map.size());
            for (int i = 0; i < 20; i++)
            {
                assertEquals("v" + i, map.get(i));
            }
        }

        @Test
        @DisplayName("T21 several resizes keep the table a power of two and within the load factor")
        void severalResizes()
        {
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();

            for (int i = 0; i < 100_000; i++)
            {
                map.put(i, i);
            }

            assertEquals(100_000, map.size());
            assertEquals(1, Integer.bitCount(map.capacity()));
            assertTrue(map.size() <= map.capacity() / 2);

            for (int i = 0; i < 100_000; i++)
            {
                assertEquals(i, map.get(i));
            }
            assertHealthy(map);
        }

        @Test
        @DisplayName("T22 growing preserves null values")
        void growthPreservesNullValues()
        {
            final FastHashMap<Integer, String> map = new FastHashMap<>();

            for (int i = 0; i < 100; i++)
            {
                map.put(i, i % 2 == 0 ? null : "v" + i);
            }

            assertEquals(100, map.size());
            for (int i = 0; i < 100; i++)
            {
                if (i % 2 == 0)
                {
                    assertNull(map.get(i), "the null value of " + i + " got lost");
                }
                else
                {
                    assertEquals("v" + i, map.get(i));
                }
            }
            assertEquals(100, map.trueSize(), "null valued entries must survive a resize as entries");
            assertHealthy(map);
        }

        @Test
        @DisplayName("T23 growing repairs the chains of colliding keys")
        void growthKeepsChainsIntact()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();

            // 200 keys on only 8 different home slots, so there are long chains to rebuild
            for (int i = 0; i < 200; i++)
            {
                map.put(new FixedHashKey("k" + i, i % 8), "v" + i);
                assertHealthy(map);
            }

            for (int i = 0; i < 200; i++)
            {
                assertEquals("v" + i, map.get(new FixedHashKey("k" + i, i % 8)));
            }
        }
    }

    // -------------------------------------------------------------------------------- F. Hashing

    @Nested
    @DisplayName("F. Hash behaviour")
    class Hashing
    {
        @Test
        @DisplayName("T24 keys that all hash to zero still work")
        void allKeysCollide()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();

            final List<FixedHashKey> keys = new ArrayList<>();
            for (int i = 0; i < 100; i++)
            {
                final FixedHashKey key = new FixedHashKey("k" + i, 0);
                keys.add(key);
                map.put(key, "v" + i);
            }

            assertEquals(100, map.size());
            for (int i = 0; i < 100; i++)
            {
                assertEquals("v" + i, map.get(keys.get(i)));
            }

            // and removal has to walk that one long chain as well
            for (int i = 0; i < 100; i += 2)
            {
                assertEquals("v" + i, map.remove(keys.get(i)));
                assertHealthy(map);
            }
            assertEquals(50, map.size());
            for (int i = 1; i < 100; i += 2)
            {
                assertEquals("v" + i, map.get(keys.get(i)));
            }
        }

        @Test
        @DisplayName("T25 keys that differ only in their high bits are spread out")
        void highBitsOnlyKeys()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();

            // without mixing, all of these would share the same home slot for small capacities
            final List<FixedHashKey> keys = new ArrayList<>();
            for (int i = 0; i < 64; i++)
            {
                final FixedHashKey key = new FixedHashKey("k" + i, i << 16);
                keys.add(key);
                map.put(key, "v" + i);
            }

            assertEquals(64, map.size());
            for (int i = 0; i < 64; i++)
            {
                assertEquals("v" + i, map.get(keys.get(i)));
            }
            assertHealthy(map);
        }

        @Test
        @DisplayName("T40 a dense range of sequential keys does not degenerate into one cluster")
        void sequentialKeysDoNotFormOneCluster()
        {
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();

            for (int i = 0; i < 100_000; i++)
            {
                map.put(i, i);
            }

            // a hash function that maps consecutive keys to consecutive slots puts all of them into
            // one single run. Lookups would still be fine, but every removal has to repair that run,
            // which turns a bulk removal into O(n^2). Keep the runs short.
            assertTrue(map.longestCluster() < 100, "longest cluster is " + map.longestCluster());

            // removing them in ascending order is the worst case for the chain repair
            for (int i = 0; i < 100_000; i++)
            {
                assertEquals(i, map.remove(i));
            }
            assertEquals(0, map.size());
            assertHealthy(map);
        }

        @Test
        @DisplayName("T26 negative hash codes do not produce a negative slot")
        void negativeHashCodes()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();

            final FixedHashKey min = new FixedHashKey("min", Integer.MIN_VALUE);
            final FixedHashKey minusOne = new FixedHashKey("minusOne", -1);
            final FixedHashKey minusBig = new FixedHashKey("minusBig", -123456789);

            map.put(min, "1");
            map.put(minusOne, "2");
            map.put(minusBig, "3");

            assertEquals("1", map.get(min));
            assertEquals("2", map.get(minusOne));
            assertEquals("3", map.get(minusBig));
            assertEquals("2", map.remove(minusOne));
            assertHealthy(map);
        }
    }

    // ---------------------------------------------------------------------------- G. keys and values

    @Nested
    @DisplayName("G. keys() and values()")
    class KeysAndValues
    {
        @Test
        @DisplayName("T27 the content matches a reference map")
        void contentMatchesReference()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            final Map<String, String> reference = new HashMap<>();

            for (int i = 0; i < 500; i++)
            {
                map.put("k" + i, "v" + (i % 100));
                reference.put("k" + i, "v" + (i % 100));
            }
            for (int i = 0; i < 500; i += 3)
            {
                map.remove("k" + i);
                reference.remove("k" + i);
            }

            assertEquals(reference.keySet(), new HashSet<>(map.keys()));
            assertEquals(multiset(reference.values()), multiset(map.values()));
        }

        @Test
        @DisplayName("T28 both lists have exactly the size of the map")
        void listSizes()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            for (int i = 0; i < 40; i++)
            {
                map.put("k" + i, "v" + i);
            }
            map.remove("k0");

            assertEquals(map.size(), map.keys().size());
            assertEquals(map.size(), map.values().size());
        }

        @Test
        @DisplayName("T29 keys and values line up by index on an unmodified map")
        void keysAndValuesCorrespond()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            final Map<String, String> reference = new HashMap<>();

            for (int i = 0; i < 100; i++)
            {
                final String value = i % 5 == 0 ? null : "v" + i;
                map.put("k" + i, value);
                reference.put("k" + i, value);
            }

            final List<String> keys = map.keys();
            final List<String> values = map.values();

            assertEquals(keys.size(), values.size());
            for (int i = 0; i < keys.size(); i++)
            {
                assertEquals(reference.get(keys.get(i)), values.get(i), "index " + i + " does not line up");
            }
        }

        @Test
        @DisplayName("T30 values keeps duplicates and nulls")
        void valuesKeepsDuplicatesAndNulls()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("a", "same");
            map.put("b", "same");
            map.put("c", null);
            map.put("d", null);
            map.put("e", "unique");

            final List<String> values = map.values();

            assertEquals(5, values.size());
            assertEquals(2, values.stream().filter("same"::equals).count());
            assertEquals(2, values.stream().filter(v -> v == null).count());
            assertEquals(1, values.stream().filter("unique"::equals).count());
        }

        @Test
        @DisplayName("T31 the returned lists are independent snapshots")
        void listsAreSnapshots()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("a", "1");
            map.put("b", "2");

            final List<String> keys = map.keys();
            final List<String> values = map.values();

            // changing the list does not change the map
            keys.clear();
            values.clear();
            assertEquals(2, map.size());
            assertEquals("1", map.get("a"));

            // and changing the map does not change an earlier list
            final List<String> keysBefore = map.keys();
            map.put("c", "3");
            map.remove("a");
            assertEquals(2, keysBefore.size());
            assertTrue(keysBefore.contains("a"));
            assertFalse(keysBefore.contains("c"));
        }

        @Test
        @DisplayName("T32 keys has no nulls and no duplicates")
        void keysAreUniqueAndNotNull()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            for (int i = 0; i < 200; i++)
            {
                map.put("k" + (i % 50), "v" + i);
            }

            final List<String> keys = map.keys();

            assertEquals(50, keys.size());
            assertEquals(50, new HashSet<>(keys).size(), "keys must be unique");
            assertFalse(keys.contains(null), "keys must never contain null");
        }
    }

    // -------------------------------------------------------------------------------------- H. clear

    @Nested
    @DisplayName("H. clear()")
    class Clear
    {
        @Test
        @DisplayName("T33 clear empties the map completely")
        void clearEmptiesEverything()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            for (int i = 0; i < 100; i++)
            {
                map.put("k" + i, "v" + i);
            }

            map.clear();

            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
            assertTrue(map.keys().isEmpty());
            assertTrue(map.values().isEmpty());
            for (int i = 0; i < 100; i++)
            {
                assertNull(map.get("k" + i));
            }
            assertHealthy(map);
        }

        @Test
        @DisplayName("T34 the map is fully usable after a clear")
        void usableAfterClear()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("a", "1");
            map.clear();

            assertNull(map.put("a", "2"), "after a clear this is a fresh insert");
            assertEquals("2", map.get("a"));
            assertEquals("2", map.remove("a"));
            assertEquals(0, map.size());

            for (int i = 0; i < 100; i++)
            {
                map.put("k" + i, "v" + i);
            }
            assertEquals(100, map.size());
            assertHealthy(map);
        }

        @Test
        @DisplayName("T35 clear keeps the capacity and drops all references")
        void clearKeepsCapacity()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            for (int i = 0; i < 100; i++)
            {
                map.put("k" + i, "v" + i);
            }
            final int capacityBefore = map.capacity();

            map.clear();

            assertEquals(capacityBefore, map.capacity(), "the table is kept for the next fill");

            // checkChainInvariant also verifies that no free slot holds on to a value
            assertTrue(map.checkChainInvariant(), "clear must null the values as well");
        }
    }

    // ------------------------------------------------------------------------- I. Randomized tests

    @Nested
    @DisplayName("I. Randomized comparison against java.util.HashMap")
    class Randomized
    {
        /**
         * How many operations one run performs.
         */
        private static final int OPERATIONS = 200_000;

        /**
         * The key space. Small on purpose, so that hits, misses and repeated removals actually happen.
         */
        private static final int KEY_SPACE = 2_000;

        /**
         * Runs random operations against the map and a reference HashMap and compares every single
         * answer. This is the test that catches broken chain repair, because it mixes inserts,
         * updates and removals in an order nobody would write by hand.
         *
         * @param seed the seed for the random generator
         * @param withNullValues whether null values are part of the value space
         */
        private void compareAgainstReference(final long seed, final boolean withNullValues)
        {
            final Random random = new Random(seed);
            final FastHashMap<Integer, String> map = new FastHashMap<>();
            final Map<Integer, String> reference = new HashMap<>();

            for (int i = 0; i < OPERATIONS; i++)
            {
                final Integer key = random.nextInt(KEY_SPACE);
                final int operation = random.nextInt(10);

                if (operation < 6)
                {
                    final String value = withNullValues && random.nextInt(4) == 0 ? null : "v" + random.nextInt(50);
                    assertEquals(reference.put(key, value), map.put(key, value), "put " + key + " in round " + i);
                }
                else if (operation < 8)
                {
                    assertEquals(reference.get(key), map.get(key), "get " + key + " in round " + i);
                }
                else
                {
                    assertEquals(reference.remove(key), map.remove(key), "remove " + key + " in round " + i);
                }

                assertEquals(reference.size(), map.size(), "size differs in round " + i);
            }

            assertEquals(reference.keySet(), new HashSet<>(map.keys()));
            assertEquals(multiset(reference.values()), multiset(map.values()));
            assertHealthy(map);
        }

        @ParameterizedTest(name = "T36 seed {0}")
        @ValueSource(longs = {1L, 42L, 4711L, 987654321L})
        @DisplayName("T36 random operations match a reference map")
        void randomOperations(final long seed)
        {
            compareAgainstReference(seed, false);
        }

        @ParameterizedTest(name = "T37 seed {0}")
        @ValueSource(longs = {7L, 2026L})
        @DisplayName("T37 random operations with null values match a reference map")
        void randomOperationsWithNullValues(final long seed)
        {
            compareAgainstReference(seed, true);
        }

        @Test
        @DisplayName("T38 records and other ordinary key types work")
        void ordinaryKeyTypes()
        {
            record Point(int x, int y)
            {
            }

            final FastHashMap<Point, String> map = new FastHashMap<>();

            for (int x = 0; x < 30; x++)
            {
                for (int y = 0; y < 30; y++)
                {
                    map.put(new Point(x, y), x + "/" + y);
                }
            }

            assertEquals(900, map.size());
            assertEquals("7/9", map.get(new Point(7, 9)), "an equal record must find its entry");
            assertEquals("7/9", map.remove(new Point(7, 9)));
            assertNull(map.get(new Point(7, 9)));
            assertHealthy(map);
        }
    }

    // ----------------------------------------------------------------------------------- J. Volume

    @Nested
    @DisplayName("J. Volume")
    class Volume
    {
        @Test
        @Tag("slow")
        @DisplayName("T39 one million entries can be inserted, read and removed")
        void oneMillionEntries()
        {
            final int count = 1_000_000;
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();

            for (int i = 0; i < count; i++)
            {
                map.put(i, i);
            }
            assertEquals(count, map.size());

            for (int i = 0; i < count; i++)
            {
                assertEquals(i, map.get(i));
            }

            for (int i = 0; i < count; i++)
            {
                assertEquals(i, map.remove(i));
            }

            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
            assertTrue(map.checkChainInvariant());
        }
    }
}
