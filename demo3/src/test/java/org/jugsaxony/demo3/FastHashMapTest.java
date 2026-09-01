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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FastHashMapTest
{
    @Nested
    class BasicOperations
    {
        @Test
        void newMapIsEmpty()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertTrue(map.values().isEmpty());
            assertNull(map.get("missing"));
            assertNull(map.remove("missing"));
        }

        @Test
        void putGetReplaceAndRemoveFollowMapSemantics()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();

            assertNull(map.put("one", 1));
            assertNull(map.put("two", 2));
            assertEquals(2, map.size());
            assertEquals(1, map.get("one"));
            assertEquals(2, map.get("two"));

            assertEquals(1, map.put("one", 11));
            assertEquals(2, map.size());
            assertEquals(11, map.get("one"));

            assertEquals(2, map.remove("two"));
            assertEquals(1, map.size());
            assertNull(map.get("two"));
            assertNull(map.remove("two"));
            assertEquals(1, map.size());
        }

        @Test
        void equalButNonIdenticalKeysAddressTheSameMapping()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();

            assertNull(map.put(new String("key"), 1));
            assertEquals(1, map.get(new String("key")));
            assertEquals(1, map.put(new String("key"), 2));
            assertEquals(1, map.size());
            assertEquals(2, map.remove(new String("key")));
            assertEquals(0, map.size());
        }

        @Test
        void allIntegerHashBitPatternsAreUsable()
        {
            final FastHashMap<ControlledHashKey, String> map = new FastHashMap<>();
            final int[] hashes = {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x12340000, 0x00001234};

            for (int i = 0; i < hashes.length; i++)
            {
                map.put(new ControlledHashKey(i, hashes[i]), "value-" + i);
            }

            assertEquals(hashes.length, map.size());
            for (int i = 0; i < hashes.length; i++)
            {
                assertEquals("value-" + i, map.get(new ControlledHashKey(i, hashes[i])));
            }
        }
    }

    @Nested
    class NullHandling
    {
        @Test
        void nullKeysAreRejectedWithoutChangingTheMap()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("existing", 7);

            assertThrows(NullPointerException.class, () -> map.get(null));
            assertThrows(NullPointerException.class, () -> map.put(null, 9));
            assertThrows(NullPointerException.class, () -> map.remove(null));

            assertEquals(1, map.size());
            assertEquals(7, map.get("existing"));
            assertEquals(List.of("existing"), map.keys());
        }

        @Test
        void nullValuesCanBeInsertedReplacedListedAndRemoved()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();

            assertNull(map.put("nullable", null));
            assertEquals(1, map.size());
            assertNull(map.get("nullable"));
            assertEquals(List.of("nullable"), map.keys());
            assertEquals(1, map.values().size());
            assertNull(map.values().getFirst());

            assertNull(map.put("nullable", 8));
            assertEquals(8, map.get("nullable"));
            assertEquals(8, map.put("nullable", null));
            assertEquals(1, map.size());

            assertNull(map.remove("nullable"));
            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertTrue(map.values().isEmpty());
        }
    }

    @Nested
    class CollisionHandling
    {
        @Test
        void unequalKeysWithTheSameHashRemainDistinct()
        {
            final FastHashMap<ControlledHashKey, Integer> map = new FastHashMap<>();

            for (int i = 0; i < 80; i++)
            {
                assertNull(map.put(new ControlledHashKey(i, 3), i * 10));
            }

            assertEquals(80, map.size());
            for (int i = 0; i < 80; i++)
            {
                assertEquals(i * 10, map.get(new ControlledHashKey(i, 3)));
            }
        }

        @Test
        void deletingHeadMiddleAndTailPreservesCollisionCluster()
        {
            final FastHashMap<ControlledHashKey, Integer> map = new FastHashMap<>();
            final Map<ControlledHashKey, Integer> expected = new HashMap<>();

            for (int i = 0; i < 10; i++)
            {
                final ControlledHashKey key = new ControlledHashKey(i, 4);
                map.put(key, i);
                expected.put(key, i);
            }

            removeAndCheck(new ControlledHashKey(0, 4), map, expected);
            removeAndCheck(new ControlledHashKey(5, 4), map, expected);
            removeAndCheck(new ControlledHashKey(9, 4), map, expected);
        }

        @Test
        void probingAndDeletionWorkAcrossArrayBoundary()
        {
            final FastHashMap<ControlledHashKey, Integer> map = new FastHashMap<>();
            final Map<ControlledHashKey, Integer> expected = new HashMap<>();

            // Hash 15 starts at the final slot of the initial 16-slot table.
            for (int i = 0; i < 10; i++)
            {
                final ControlledHashKey key = new ControlledHashKey(i, 15);
                map.put(key, i);
                expected.put(key, i);
            }

            removeAndCheck(new ControlledHashKey(0, 15), map, expected);
            removeAndCheck(new ControlledHashKey(4, 15), map, expected);
            removeAndCheck(new ControlledHashKey(9, 15), map, expected);
        }

        @Test
        void collisionClusterCanBeRemovedInArbitraryOrder()
        {
            final FastHashMap<ControlledHashKey, Integer> map = new FastHashMap<>();
            final List<ControlledHashKey> removalOrder = new ArrayList<>();

            for (int i = 0; i < 60; i++)
            {
                final ControlledHashKey key = new ControlledHashKey(i, i % 3);
                removalOrder.add(key);
                map.put(key, i);
            }

            java.util.Collections.shuffle(removalOrder, new Random(73L));
            for (int i = 0; i < removalOrder.size(); i++)
            {
                final ControlledHashKey key = removalOrder.get(i);
                assertEquals(key.id(), map.remove(key));
                assertEquals(removalOrder.size() - i - 1, map.size());

                for (int j = i + 1; j < removalOrder.size(); j++)
                {
                    final ControlledHashKey remaining = removalOrder.get(j);
                    assertEquals(remaining.id(), map.get(remaining));
                }
            }

            assertTrue(map.keys().isEmpty());
        }
    }

    @Nested
    class Growth
    {
        @Test
        void mappingsSurviveSeveralResizes()
        {
            final FastHashMap<Integer, String> map = new FastHashMap<>();

            for (int i = 0; i < 10_000; i++)
            {
                map.put(i, i % 11 == 0 ? null : "value-" + i);
            }

            assertEquals(10_000, map.size());
            for (int i = 0; i < 10_000; i++)
            {
                assertEquals(i % 11 == 0 ? null : "value-" + i, map.get(i));
            }
            assertEquals(10_000, map.keys().size());
            assertEquals(10_000, map.values().size());
        }

        @Test
        void replacementAtInitialThresholdDoesNotAddAMapping()
        {
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();

            for (int i = 0; i < 12; i++)
            {
                map.put(i, i);
            }

            assertEquals(5, map.put(5, 500));
            assertEquals(12, map.size());
            assertEquals(500, map.get(5));

            assertNull(map.put(12, 12));
            assertEquals(13, map.size());
            for (int i = 0; i <= 12; i++)
            {
                assertEquals(i == 5 ? 500 : i, map.get(i));
            }
        }

        @Test
        void removalsAfterSeveralResizesPreserveRemainingMappings()
        {
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();

            for (int i = 0; i < 2_000; i++)
            {
                map.put(i, -i);
            }
            for (int i = 0; i < 2_000; i += 3)
            {
                assertEquals(-i, map.remove(i));
            }

            assertEquals(1_333, map.size());
            for (int i = 0; i < 2_000; i++)
            {
                if (i % 3 == 0)
                {
                    assertNull(map.get(i));
                }
                else
                {
                    assertEquals(-i, map.get(i));
                }
            }
        }
    }

    @Nested
    class Snapshots
    {
        @Test
        void keyAndValueSnapshotsCorrespondByIndex()
        {
            final FastHashMap<ControlledHashKey, String> map = new FastHashMap<>();
            final Map<ControlledHashKey, String> expected = new HashMap<>();

            for (int i = 0; i < 40; i++)
            {
                final ControlledHashKey key = new ControlledHashKey(i, i % 5);
                final String value = i % 7 == 0 ? null : "value-" + (i % 9);
                map.put(key, value);
                expected.put(key, value);
            }

            final List<ControlledHashKey> keys = map.keys();
            final List<String> values = map.values();
            assertEquals(expected.size(), keys.size());
            assertEquals(expected.size(), values.size());
            assertEquals(expected.keySet(), new HashSet<>(keys));

            for (int i = 0; i < keys.size(); i++)
            {
                assertEquals(expected.get(keys.get(i)), values.get(i));
            }
            assertEquals(frequencies(expected.values()), frequencies(values));
        }

        @Test
        void snapshotsAreMutableAndIndependentFromTheMap()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("one", 1);
            map.put("two", 2);

            final List<String> oldKeys = map.keys();
            final List<Integer> oldValues = map.values();
            assertDoesNotThrow(() -> oldKeys.add("local"));
            assertDoesNotThrow(oldValues::clear);

            assertEquals(2, map.size());
            assertEquals(Set.of("one", "two"), new HashSet<>(map.keys()));
            assertEquals(Set.of(1, 2), new HashSet<>(map.values()));

            map.put("three", 3);
            map.remove("one");
            assertTrue(oldKeys.contains("one"));
            assertFalse(oldKeys.contains("three"));
            assertTrue(oldValues.isEmpty());
            assertEquals(Set.of("two", "three"), new HashSet<>(map.keys()));
        }

        @Test
        void duplicateAndNullValuesKeepTheirMultiplicity()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", 1);
            map.put("b", 1);
            map.put("c", null);
            map.put("d", null);

            assertEquals(Map.of(1, 2), frequencies(map.values()).entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            assertEquals(2, frequencies(map.values()).get(null));
        }
    }

    @Nested
    class Clear
    {
        @Test
        void clearEmptiesAGrownMapAndAllowsReuse()
        {
            final FastHashMap<Integer, String> map = new FastHashMap<>();

            for (int i = 0; i < 1_000; i++)
            {
                map.put(i, i % 13 == 0 ? null : "old-" + i);
            }
            final List<Integer> snapshot = map.keys();

            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertTrue(map.values().isEmpty());
            assertEquals(1_000, snapshot.size());
            for (int i = 0; i < 1_000; i++)
            {
                assertNull(map.get(i));
            }

            assertNull(map.put(10, "new"));
            assertNull(map.put(20, null));
            assertEquals(2, map.size());
            assertEquals("new", map.get(10));
            assertTrue(map.keys().contains(20));
        }

        @Test
        void clearingAnEmptyMapIsSafe()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();

            assertDoesNotThrow(map::clear);
            assertDoesNotThrow(map::clear);
            assertEquals(0, map.size());
            assertNull(map.put("usable", "yes"));
            assertEquals("yes", map.get("usable"));
        }
    }

    @Test
    void seededOperationsMatchHashMap()
    {
        final FastHashMap<ControlledHashKey, Integer> actual = new FastHashMap<>();
        final Map<ControlledHashKey, Integer> expected = new HashMap<>();
        final Random random = new Random(0x5EEDC0DEL);

        for (int step = 0; step < 30_000; step++)
        {
            final int id = random.nextInt(600);
            final ControlledHashKey key = new ControlledHashKey(id, id % 13);

            switch (random.nextInt(20))
            {
                case 0 ->
                {
                    actual.clear();
                    expected.clear();
                }
                case 1, 2, 3, 4, 5 -> assertEquals(expected.remove(key), actual.remove(key), "remove at step " + step);
                case 6, 7, 8, 9 -> assertEquals(expected.get(key), actual.get(key), "get at step " + step);
                default ->
                {
                    final Integer value = random.nextInt(8) == 0 ? null : random.nextInt();
                    assertEquals(expected.put(key, value), actual.put(key, value), "put at step " + step);
                }
            }

            assertEquals(expected.size(), actual.size(), "size at step " + step);
            if (step % 257 == 0)
            {
                assertMapContents(expected, actual);
            }
        }

        assertMapContents(expected, actual);
    }

    private static <K, V> void removeAndCheck(final K key, final FastHashMap<K, V> actual,
                                               final Map<K, V> expected)
    {
        assertEquals(expected.remove(key), actual.remove(key));
        assertMapContents(expected, actual);
    }

    private static <K, V> void assertMapContents(final Map<K, V> expected, final FastHashMap<K, V> actual)
    {
        assertEquals(expected.size(), actual.size());

        final List<K> keys = actual.keys();
        final List<V> values = actual.values();
        assertEquals(expected.size(), keys.size());
        assertEquals(expected.size(), values.size());
        assertEquals(expected.keySet(), new HashSet<>(keys));

        for (int i = 0; i < keys.size(); i++)
        {
            final K key = keys.get(i);
            assertEquals(expected.get(key), values.get(i));
            assertEquals(expected.get(key), actual.get(key));
        }
    }

    private static <T> Map<T, Integer> frequencies(final Iterable<T> values)
    {
        final Map<T, Integer> result = new HashMap<>();
        for (final T value : values)
        {
            result.merge(value, 1, Integer::sum);
        }
        return result;
    }

    private record ControlledHashKey(int id, int forcedHash)
    {
        @Override
        public int hashCode()
        {
            return forcedHash;
        }
    }
}
