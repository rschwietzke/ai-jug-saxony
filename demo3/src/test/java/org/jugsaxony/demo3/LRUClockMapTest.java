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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LRUClockMapTest
{
    @Nested
    class Construction
    {
        @Test
        void rejectsSizesBelowFour()
        {
            assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(-1));
            assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(0));
            assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(3));
        }

        @Test
        void rejectsSizesThatNeedAnUnsupportedBackingArray()
        {
            assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(Integer.MAX_VALUE));
        }

        @Test
        void createsPowerOfTwoStorageAtNoMoreThanHalfLoad()
        {
            final int[] maximumSizes = {4, 5, 8, 9, 31, 100};

            for (final int maximumSize : maximumSizes)
            {
                final LRUClockMap<Integer, String> map = new LRUClockMap<>(maximumSize);
                final int occupiedSpace = map.occupiedSpace();

                assertEquals(0, map.size());
                assertEquals(0, map.trueSize());
                assertTrue(occupiedSpace >= maximumSize * 2);
                assertEquals(0, occupiedSpace & (occupiedSpace - 1));
            }
        }
    }

    @Nested
    class BasicOperations
    {
        @Test
        void putGetAndGetRawReturnStoredValues()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);

            assertNull(map.put("one", 1));
            assertNull(map.put("two", 2));
            assertEquals(1, map.get("one"));
            assertEquals(2, map.getRaw("two"));
            assertNull(map.get("missing"));
            assertNull(map.getRaw("missing"));
            assertEquals(2, map.size());
            assertEquals(2, map.trueSize());
        }

        @Test
        void replacingAMappingReturnsTheOldValueWithoutChangingSize()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
            map.put(new String("key"), 1);

            assertEquals(1, map.put(new String("key"), 2));
            assertEquals(1, map.size());
            assertEquals(1, map.trueSize());
            assertEquals(2, map.getRaw("key"));
        }

        @Test
        void replacingAtMaximumSizeDoesNotEvictAnotherMapping()
        {
            final LRUClockMap<Integer, Integer> map = filledMapOfFour();
            map.put(4, 40); // evicts key 0 and leaves keys 1, 2, and 3 cold

            assertEquals(10, map.put(1, 100));

            assertEquals(4, map.size());
            assertEquals(Set.of(1, 2, 3, 4), new HashSet<>(map.keys()));
            assertEquals(100, map.getRaw(1));
            assertEquals(20, map.getRaw(2));
            assertEquals(30, map.getRaw(3));
            assertEquals(40, map.getRaw(4));

            map.put(5, 50);
            assertNull(map.getRaw(2));
            assertEquals(Set.of(1, 3, 4, 5), new HashSet<>(map.keys()));
        }

        @Test
        void removeReturnsOldValueAndUpdatesBothSizeMeasures()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(6);
            map.put("one", 1);
            map.put("two", 2);

            assertEquals(1, map.remove("one"));
            assertNull(map.getRaw("one"));
            assertEquals(2, map.getRaw("two"));
            assertEquals(1, map.size());
            assertEquals(1, map.trueSize());

            assertNull(map.remove("one"));
            assertNull(map.remove("absent"));
            assertEquals(1, map.size());
        }

        @Test
        void equalButNonIdenticalKeysAddressTheSameMapping()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);

            map.put(new String("same"), 1);
            assertEquals(1, map.getRaw(new String("same")));
            assertEquals(1, map.remove(new String("same")));
            assertEquals(0, map.size());
        }
    }

    @Nested
    class NullHandling
    {
        @Test
        void rejectsNullKeysWithoutChangingExistingMappings()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
            map.put("existing", 1);

            assertThrows(NullPointerException.class, () -> map.put(null, 2));
            assertThrows(NullPointerException.class, () -> map.get(null));
            assertThrows(NullPointerException.class, () -> map.getRaw(null));
            assertThrows(NullPointerException.class, () -> map.remove(null));

            assertEquals(1, map.size());
            assertEquals(1, map.getRaw("existing"));
        }

        @Test
        void rejectsNullValuesWithoutChangingExistingMappings()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
            map.put("existing", 1);

            assertThrows(NullPointerException.class, () -> map.put("new", null));
            assertThrows(NullPointerException.class, () -> map.put("existing", null));

            assertEquals(1, map.size());
            assertEquals(1, map.getRaw("existing"));
            assertNull(map.getRaw("new"));
        }
    }

    @Nested
    class Collisions
    {
        @Test
        void collidingKeysCanBeInsertedRetrievedAndReplaced()
        {
            final LRUClockMap<ControlledHashKey, Integer> map = new LRUClockMap<>(20);

            for (int i = 0; i < 20; i++)
            {
                assertNull(map.put(new ControlledHashKey(i, 7), i));
            }
            assertEquals(20, map.size());
            assertEquals(20, map.trueSize());

            for (int i = 0; i < 20; i++)
            {
                assertEquals(i, map.getRaw(new ControlledHashKey(i, 7)));
            }

            assertEquals(10, map.put(new ControlledHashKey(10, 7), 1_000));
            assertEquals(20, map.size());
            assertEquals(1_000, map.getRaw(new ControlledHashKey(10, 7)));
        }

        @Test
        void removingHeadMiddleAndTailRepairsCollisionCluster()
        {
            final LRUClockMap<ControlledHashKey, Integer> map = new LRUClockMap<>(20);
            final Set<ControlledHashKey> remaining = new HashSet<>();

            for (int i = 0; i < 12; i++)
            {
                final ControlledHashKey key = new ControlledHashKey(i, 15);
                remaining.add(key);
                map.put(key, i);
            }

            removeAndVerify(new ControlledHashKey(0, 15), map, remaining);
            removeAndVerify(new ControlledHashKey(6, 15), map, remaining);
            removeAndVerify(new ControlledHashKey(11, 15), map, remaining);
        }

        @Test
        void removalRepairsAClusterThatWrapsAroundStorageBoundary()
        {
            final LRUClockMap<ControlledHashKey, Integer> map = new LRUClockMap<>(8);
            final Set<ControlledHashKey> remaining = new HashSet<>();

            // An eight-entry map has 16 slots; hash 15 starts at the final slot.
            for (int i = 0; i < 8; i++)
            {
                final ControlledHashKey key = new ControlledHashKey(i, 15);
                remaining.add(key);
                map.put(key, i);
            }

            removeAndVerify(new ControlledHashKey(0, 15), map, remaining);
            removeAndVerify(new ControlledHashKey(3, 15), map, remaining);
            removeAndVerify(new ControlledHashKey(7, 15), map, remaining);
        }

        @Test
        void arbitraryCollisionRemovalOrderPreservesRemainingMappings()
        {
            final LRUClockMap<ControlledHashKey, Integer> map = new LRUClockMap<>(60);
            final List<ControlledHashKey> removalOrder = new ArrayList<>();

            for (int i = 0; i < 60; i++)
            {
                final ControlledHashKey key = new ControlledHashKey(i, i % 3);
                removalOrder.add(key);
                map.put(key, i);
            }
            java.util.Collections.shuffle(removalOrder, new Random(71L));

            for (int i = 0; i < removalOrder.size(); i++)
            {
                final ControlledHashKey removed = removalOrder.get(i);
                assertEquals(removed.id(), map.remove(removed));
                assertEquals(removalOrder.size() - i - 1, map.size());
                assertEquals(map.size(), map.trueSize());

                for (int j = i + 1; j < removalOrder.size(); j++)
                {
                    final ControlledHashKey key = removalOrder.get(j);
                    assertEquals(key.id(), map.getRaw(key));
                }
            }
        }
    }

    @Nested
    class Eviction
    {
        @Test
        void addingToAFullMapEvictsOneEntryAndNeverExceedsMaximumSize()
        {
            final LRUClockMap<Integer, Integer> map = filledMapOfFour();

            assertNull(map.put(4, 40));

            assertEquals(4, map.size());
            assertEquals(4, map.trueSize());
            assertNull(map.getRaw(0));
            assertEquals(Set.of(1, 2, 3, 4), new HashSet<>(map.keys()));
        }

        @Test
        void getGivesAColdEntryASecondChance()
        {
            final LRUClockMap<Integer, Integer> map = filledMapOfFour();
            map.put(4, 40); // key 0 is evicted; keys 1, 2, and 3 become cold

            assertFalse(debugEntry(map, 1).secondChance);
            assertEquals(10, map.get(1));
            assertTrue(debugEntry(map, 1).secondChance);

            map.put(5, 50);

            assertEquals(10, map.getRaw(1));
            assertNull(map.getRaw(2));
            assertEquals(Set.of(1, 3, 4, 5), new HashSet<>(map.keys()));
        }

        @Test
        void getRawDoesNotGiveAColdEntryASecondChance()
        {
            final LRUClockMap<Integer, Integer> map = filledMapOfFour();
            map.put(4, 40);

            assertFalse(debugEntry(map, 1).secondChance);
            assertEquals(10, map.getRaw(1));
            assertFalse(debugEntry(map, 1).secondChance);

            map.put(5, 50);

            assertNull(map.getRaw(1));
            assertEquals(Set.of(2, 3, 4, 5), new HashSet<>(map.keys()));
        }

        @Test
        void updatingAColdEntryGivesItASecondChance()
        {
            final LRUClockMap<Integer, Integer> map = filledMapOfFour();
            map.put(4, 40);

            assertFalse(debugEntry(map, 1).secondChance);
            assertEquals(10, map.put(1, 100));
            assertTrue(debugEntry(map, 1).secondChance);

            map.put(5, 50);

            assertEquals(100, map.getRaw(1));
            assertNull(map.getRaw(2));
        }

        @Test
        void repeatedInsertionsKeepTheConfiguredMaximum()
        {
            final int maximumSize = 17;
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(maximumSize);

            for (int i = 0; i < 5_000; i++)
            {
                map.put(i, i);
                assertTrue(map.size() <= maximumSize);
                assertEquals(map.size(), map.trueSize());
                assertEquals(map.size(), map.keys().size());
                assertEquals(i, map.getRaw(i));
            }

            assertEquals(maximumSize, map.size());
        }
    }

    @Nested
    class ViewsAndClear
    {
        @Test
        void keysReturnsAMutableSnapshotIndependentOfTheMap()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(5);
            map.put("one", 1);
            map.put("two", 2);

            final List<String> snapshot = map.keys();
            assertEquals(Set.of("one", "two"), new HashSet<>(snapshot));
            assertDoesNotThrow(snapshot::clear);
            assertDoesNotThrow(() -> snapshot.add("local"));

            assertEquals(2, map.size());
            assertEquals(1, map.getRaw("one"));
            assertEquals(2, map.getRaw("two"));

            final List<String> oldSnapshot = map.keys();
            map.put("three", 3);
            map.remove("one");
            assertTrue(oldSnapshot.contains("one"));
            assertFalse(oldSnapshot.contains("three"));
        }

        @Test
        void clearRemovesEverythingAndAllowsReuseWithoutChangingCapacity()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(7);
            for (int i = 0; i < 7; i++)
            {
                map.put(i, i);
            }
            final int occupiedSpace = map.occupiedSpace();

            map.clear();

            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
            assertTrue(map.keys().isEmpty());
            assertEquals(occupiedSpace, map.occupiedSpace());
            for (int i = 0; i < 7; i++)
            {
                assertNull(map.getRaw(i));
            }

            assertNull(map.put(100, 1_000));
            assertEquals(1_000, map.get(100));
            assertEquals(1, map.size());
        }

        @Test
        void clearOnAnEmptyMapIsSafe()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);

            assertDoesNotThrow(map::clear);
            assertDoesNotThrow(map::clear);
            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
        }

        @Test
        void debugDataMatchesOccupiedSlotsAndCalculatedHomePositions()
        {
            final LRUClockMap<ControlledHashKey, String> map = new LRUClockMap<>(5);
            final ControlledHashKey first = new ControlledHashKey(1, 3);
            final ControlledHashKey second = new ControlledHashKey(2, 3);
            map.put(first, "first");
            map.put(second, "second");

            final List<LRUClockMap<ControlledHashKey, String>.DebugWrapper<ControlledHashKey, String>> debugData =
                    map.getDebugData();

            assertEquals(map.occupiedSpace(), debugData.size());
            assertEquals(2, debugData.stream().filter(java.util.Objects::nonNull).count());
            for (final LRUClockMap<ControlledHashKey, String>.DebugWrapper<ControlledHashKey, String> entry : debugData)
            {
                if (entry != null)
                {
                    assertEquals(3, entry.truePosition);
                    assertTrue(entry.currentPosition >= 0);
                    assertTrue(entry.currentPosition < map.occupiedSpace());
                    assertEquals(map.getRaw(entry.key), entry.value);
                    assertTrue(entry.secondChance);
                }
            }
        }

        @Test
        void toStringProvidesBoundedDiagnosticState()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
            map.put("one", 1);
            map.put("two", 2);

            final String text = map.toString();

            assertTrue(text.startsWith("LRUClockMap{"));
            assertTrue(text.contains("clockHand: "));
            assertTrue(text.contains("size: 2"));
            assertTrue(text.contains("maxSize: 4"));
            assertTrue(text.contains("one"));
            assertTrue(text.contains("two"));
        }
    }

    @Test
    void randomizedOperationsPreservePublicInvariants()
    {
        final int maximumSize = 31;
        final LRUClockMap<ControlledHashKey, Integer> map = new LRUClockMap<>(maximumSize);
        final Random random = new Random(0xC10C5EEDL);

        for (int step = 0; step < 20_000; step++)
        {
            final int id = random.nextInt(120);
            final ControlledHashKey key = new ControlledHashKey(id, id % 7);
            final Set<ControlledHashKey> beforeKeys = new HashSet<>(map.keys());
            final Integer oldValue = map.getRaw(key);
            final int oldSize = map.size();

            switch (random.nextInt(20))
            {
                case 0 -> map.clear();
                case 1, 2, 3, 4 ->
                {
                    assertEquals(oldValue, map.remove(key), "remove at step " + step);
                    assertEquals(oldValue == null ? oldSize : oldSize - 1, map.size());
                }
                case 5, 6, 7, 8 -> assertEquals(oldValue, map.get(key), "get at step " + step);
                case 9, 10 -> assertEquals(oldValue, map.getRaw(key), "getRaw at step " + step);
                default ->
                {
                    final int value = random.nextInt();
                    assertEquals(oldValue, map.put(key, value), "put at step " + step);

                    final Set<ControlledHashKey> afterKeys = new HashSet<>(map.keys());
                    if (oldValue != null)
                    {
                        assertEquals(beforeKeys, afterKeys);
                        assertEquals(oldSize, map.size());
                    }
                    else if (oldSize < maximumSize)
                    {
                        beforeKeys.add(key);
                        assertEquals(beforeKeys, afterKeys);
                        assertEquals(oldSize + 1, map.size());
                    }
                    else
                    {
                        assertEquals(maximumSize, map.size());
                        assertTrue(afterKeys.contains(key));
                        beforeKeys.retainAll(afterKeys);
                        assertEquals(maximumSize - 1, beforeKeys.size());
                    }
                }
            }

            assertInvariants(map, maximumSize, step);
        }
    }

    private static LRUClockMap<Integer, Integer> filledMapOfFour()
    {
        final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
        map.put(0, 0);
        map.put(1, 10);
        map.put(2, 20);
        map.put(3, 30);
        return map;
    }

    private static void removeAndVerify(final ControlledHashKey key,
                                        final LRUClockMap<ControlledHashKey, Integer> map,
                                        final Set<ControlledHashKey> remaining)
    {
        assertEquals(key.id(), map.remove(key));
        remaining.remove(key);
        assertEquals(remaining.size(), map.size());
        assertEquals(map.size(), map.trueSize());
        assertEquals(remaining, new HashSet<>(map.keys()));
        for (final ControlledHashKey remainingKey : remaining)
        {
            assertEquals(remainingKey.id(), map.getRaw(remainingKey));
        }
    }

    private static <K, V> LRUClockMap<K, V>.DebugWrapper<K, V> debugEntry(final LRUClockMap<K, V> map,
                                                                           final K key)
    {
        return map.getDebugData().stream()
                .filter(java.util.Objects::nonNull)
                .filter(entry -> entry.key.equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static <K, V> void assertInvariants(final LRUClockMap<K, V> map, final int maximumSize,
                                                 final int step)
    {
        final List<K> keys = map.keys();
        assertTrue(map.size() <= maximumSize, "maximum size at step " + step);
        assertEquals(map.size(), map.trueSize(), "true size at step " + step);
        assertEquals(map.size(), keys.size(), "key count at step " + step);
        assertEquals(keys.size(), new HashSet<>(keys).size(), "unique keys at step " + step);
        for (final K key : keys)
        {
            assertNotNull(map.getRaw(key), "reachable key at step " + step + ": " + key);
        }
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
