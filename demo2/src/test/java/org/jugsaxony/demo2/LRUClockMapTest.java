package org.jugsaxony.demo2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test suite for {@link LRUClockMap}. Uses plain JUnit 6 assertions only.
 * <p>
 * Many tests exploit the deterministic internal layout: with maxSize 4 the
 * backing array has capacity 8 (mask 7), and small non-negative Integer keys
 * hash to themselves, so key i lands in slot i. This makes the clock eviction
 * order fully predictable.
 */
class LRUClockMapTest
{
    /**
     * Reads the second-chance flag of a key via the package-private debug view.
     * Fails the test if the key is not present.
     */
    private static <K, V> boolean secondChanceOf(final LRUClockMap<K, V> map, final K key)
    {
        for (final var dw : map.getDebugData())
        {
            if (dw != null && dw.key.equals(key))
            {
                return dw.secondChance;
            }
        }
        throw new AssertionError("key not found in map: " + key);
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation
    {
        @Test
        @DisplayName("maxSize below 4 throws IllegalArgumentException")
        void belowMinimumThrows()
        {
            assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<String, Integer>(3));
            assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<String, Integer>(0));
            assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<String, Integer>(-1));
            assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<String, Integer>(Integer.MIN_VALUE));
        }

        @Test
        @DisplayName("maxSize of 4 is accepted")
        void minimumAccepted()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("Backing array is a power of two at 50% load")
        void capacityIsPowerOfTwoAtHalfLoad()
        {
            // arraySize(expected, 0.5) = nextPowerOfTwo(ceil(expected / 0.5))
            assertEquals(8, new LRUClockMap<String, Integer>(4).occupiedSpace());
            assertEquals(16, new LRUClockMap<String, Integer>(5).occupiedSpace());
            assertEquals(16, new LRUClockMap<String, Integer>(8).occupiedSpace());
            assertEquals(256, new LRUClockMap<String, Integer>(100).occupiedSpace());
        }
    }

    @Nested
    @DisplayName("Basic operations")
    class BasicOperations
    {
        @Test
        @DisplayName("Newly created map is empty")
        void emptyOnCreate()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);

            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
            assertTrue(map.keys().isEmpty());
            assertNull(map.get("anything"));
            assertNull(map.getRaw("anything"));
        }

        @Test
        @DisplayName("Single put and get")
        void putGetSingle()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);

            assertNull(map.put("apple", 100));
            assertEquals(100, map.get("apple"));
            assertEquals(100, map.getRaw("apple"));
            assertEquals(1, map.size());
            assertEquals(1, map.trueSize());
        }

        @Test
        @DisplayName("Overwrite returns old value and keeps size")
        void putOverwrite()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(8);
            map.put("k", "v1");

            assertEquals("v1", map.put("k", "v2"));
            assertEquals("v2", map.get("k"));
            assertEquals(1, map.size());
            assertEquals(1, map.trueSize());
        }

        @Test
        @DisplayName("get of missing key returns null")
        void getMissing()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            map.put("a", 1);

            assertNull(map.get("b"));
            assertNull(map.getRaw("b"));
        }

        @Test
        @DisplayName("size tracks distinct keys only")
        void sizeTracking()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(16);
            for (int i = 0; i < 10; i++)
            {
                map.put(i, i);
            }
            assertEquals(10, map.size());

            // overwrite all, size must not change
            for (int i = 0; i < 10; i++)
            {
                map.put(i, i * 2);
            }
            assertEquals(10, map.size());
            assertEquals(18, map.get(9).intValue());
        }

        @Test
        @DisplayName("keys() contains exactly the current keys")
        void keysContent()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(16);
            final Set<String> expected = new HashSet<>();
            for (int i = 0; i < 10; i++)
            {
                map.put("k" + i, i);
                expected.add("k" + i);
            }

            final List<String> keys = map.keys();
            assertEquals(expected.size(), keys.size());
            assertEquals(expected, new HashSet<>(keys));
        }

        @Test
        @DisplayName("Mutating the returned keys list does not affect the map")
        void keysListIsACopy()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            map.put("a", 1);

            final List<String> keys = map.keys();
            keys.clear();

            assertEquals(1, map.size());
            assertEquals(1, map.keys().size());
        }
    }

    @Nested
    @DisplayName("Null key handling")
    class NullKeyHandling
    {
        // Null key support was removed per class contract; hashCode() on the
        // key is dereferenced immediately, so a NullPointerException is expected.

        @Test
        @DisplayName("put with null key throws NullPointerException")
        void putNullKeyThrows()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            assertThrows(NullPointerException.class, () -> map.put(null, 1));
        }

        @Test
        @DisplayName("get with null key throws NullPointerException")
        void getNullKeyThrows()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            assertThrows(NullPointerException.class, () -> map.get(null));
        }

        @Test
        @DisplayName("getRaw with null key throws NullPointerException")
        void getRawNullKeyThrows()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            assertThrows(NullPointerException.class, () -> map.getRaw(null));
        }

        @Test
        @DisplayName("remove with null key throws NullPointerException")
        void removeNullKeyThrows()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            assertThrows(NullPointerException.class, () -> map.remove(null));
        }
    }

    @Nested
    @DisplayName("remove")
    class RemoveOperations
    {
        @Test
        @DisplayName("remove returns old value, deletes mapping, decrements size")
        void removeExisting()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            map.put("a", 1);
            map.put("b", 2);

            assertEquals(1, map.remove("a"));
            assertNull(map.get("a"));
            assertEquals(2, map.get("b"));
            assertEquals(1, map.size());
            assertEquals(1, map.trueSize());
        }

        @Test
        @DisplayName("remove of missing key returns null and keeps size")
        void removeMissing()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            map.put("a", 1);

            assertNull(map.remove("missing"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("Removing the same key twice returns null the second time")
        void removeTwice()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            map.put("a", 1);

            assertEquals(1, map.remove("a"));
            assertNull(map.remove("a"));
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("remove keeps the linear probe chain intact")
        void removeKeepsProbeChainIntact()
        {
            // capacity 8, mask 7: keys 0 and 8 share home slot 0, key 1 has home 1
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            map.put(0, 0);      // slot 0
            map.put(8, 80);     // slot 1 (collision)
            map.put(1, 10);     // slot 2 (pushed by 8)

            // remove the middle of the cluster
            assertEquals(80, map.remove(8));

            // the realigned entries must still be reachable
            assertEquals(0, map.get(0).intValue());
            assertEquals(10, map.get(1).intValue());
            assertNull(map.get(8));
            assertEquals(2, map.size());
            assertEquals(2, map.trueSize());
        }

        @Test
        @DisplayName("Entries can be reinserted after removal")
        void removeThenReinsert()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            map.put("a", 1);
            map.remove("a");

            assertNull(map.put("a", 99));
            assertEquals(99, map.get("a"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("Removing all entries empties the map")
        void removeAll()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(16);
            for (int i = 0; i < 10; i++)
            {
                map.put(i, i);
            }
            for (int i = 0; i < 10; i++)
            {
                assertEquals(i, map.remove(i).intValue());
            }

            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
            assertTrue(map.keys().isEmpty());
            for (int i = 0; i < 10; i++)
            {
                assertNull(map.get(i));
            }
        }
    }

    @Nested
    @DisplayName("clear")
    class ClearOperation
    {
        @Test
        @DisplayName("clear empties the map but keeps the backing array")
        void clearEmptiesMap()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(8);
            for (int i = 0; i < 8; i++)
            {
                map.put(i, i);
            }
            final int capacityBefore = map.occupiedSpace();

            map.clear();

            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
            assertTrue(map.keys().isEmpty());
            // structure is reused, not reallocated
            assertEquals(capacityBefore, map.occupiedSpace());
            for (int i = 0; i < 8; i++)
            {
                assertNull(map.get(i));
            }
        }

        @Test
        @DisplayName("Map is fully reusable after clear")
        void reusableAfterClear()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            map.put("a", 1);
            map.clear();

            assertNull(map.put("a", 2));
            assertEquals(2, map.get("a"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("clear on an empty map is a no-op")
        void clearOnEmpty()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            map.clear();

            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
        }
    }

    @Nested
    @DisplayName("Collisions and probing")
    class CollisionHandling
    {
        @Test
        @DisplayName("Keys sharing a home slot form a working cluster")
        void collisionCluster()
        {
            // capacity 8, mask 7: keys 0, 8, 16, 24 all have home slot 0
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            map.put(0, 0);
            map.put(8, 8);
            map.put(16, 16);
            map.put(24, 24);

            assertEquals(4, map.size());
            assertEquals(0, map.get(0).intValue());
            assertEquals(8, map.get(8).intValue());
            assertEquals(16, map.get(16).intValue());
            assertEquals(24, map.get(24).intValue());
        }

        @Test
        @DisplayName("Probing wraps around the end of the backing array")
        void wrapAroundProbing()
        {
            // capacity 8, mask 7: key 7 has home slot 7, key 15 also (15 & 7 = 7)
            // so 15 must wrap to slot 0
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            map.put(7, 70);
            map.put(15, 150);

            assertEquals(70, map.get(7).intValue());
            assertEquals(150, map.get(15).intValue());
            assertEquals(2, map.size());
        }

        @Test
        @DisplayName("Keys are compared with equals, not identity")
        void equalsBasedLookup()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(8);
            map.put(new String("dynamic"), 1);

            assertEquals(1, map.get(new String("dynamic")));
            assertEquals(1, map.getRaw(new String("dynamic")));
        }
    }

    @Nested
    @DisplayName("Eviction")
    class Eviction
    {
        @Test
        @DisplayName("size never exceeds maxSize under heavy insert load")
        void neverExceedsMaxSize()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(8);
            for (int i = 0; i < 100; i++)
            {
                map.put(i, i);
                assertTrue(map.size() <= 8, "size exceeded maxSize at insert " + i);
                assertEquals(map.size(), map.trueSize());
            }

            assertEquals(8, map.size());

            // exactly 8 of the 100 inserted keys survive
            int present = 0;
            for (int i = 0; i < 100; i++)
            {
                if (map.getRaw(i) != null)
                {
                    present++;
                }
            }
            assertEquals(8, present);
        }

        @Test
        @DisplayName("Inserting into a full map evicts exactly one entry")
        void evictsExactlyOne()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(8);
            final Set<Integer> remaining = new HashSet<>();
            for (int i = 0; i < 8; i++)
            {
                map.put(i, i);
                remaining.add(i);
            }

            map.put(8, 8);

            assertEquals(8, map.size());
            assertTrue(map.getRaw(8) != null, "newly inserted key must be present");

            int survivors = 0;
            for (int i = 0; i < 8; i++)
            {
                if (map.getRaw(i) != null)
                {
                    survivors++;
                }
            }
            assertEquals(7, survivors, "exactly one original entry must have been evicted");
        }

        @Test
        @DisplayName("Deterministic clock eviction: oldest untouched slot is evicted first")
        void deterministicClockOrder()
        {
            // capacity 8, mask 7; keys 0..3 land in slots 0..3
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            for (int i = 0; i < 4; i++)
            {
                map.put(i, i * 10);
            }

            // All entries still have their initial second chance. The first evict
            // sweeps the whole circle clearing flags, then evicts the first
            // occupied slot from the clock hand's start: key 0 at slot 0.
            assertNull(map.put(4, 40));

            assertNull(map.getRaw(0), "key 0 must have been evicted");
            assertEquals(10, map.getRaw(1));
            assertEquals(20, map.getRaw(2));
            assertEquals(30, map.getRaw(3));
            assertEquals(40, map.getRaw(4));
            assertEquals(4, map.size());
            assertEquals(4, map.trueSize());
        }

        @Test
        @DisplayName("get() grants a second chance, getRaw() does not")
        void secondChanceProtection()
        {
            // capacity 8, mask 7; keys 0..3 land in slots 0..3
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            for (int i = 0; i < 4; i++)
            {
                map.put(i, i * 10);
            }

            // first eviction: clears all flags, evicts key 0
            map.put(4, 40);
            assertFalse(secondChanceOf(map, 1));
            assertFalse(secondChanceOf(map, 2));
            assertFalse(secondChanceOf(map, 3));

            // touch 1 and 2 via get(), 3 only via getRaw()
            assertEquals(10, map.get(1));
            assertEquals(20, map.get(2));
            assertEquals(30, map.getRaw(3));

            assertTrue(secondChanceOf(map, 1));
            assertTrue(secondChanceOf(map, 2));
            assertFalse(secondChanceOf(map, 3), "getRaw must not set the second chance flag");

            // second eviction: the clock clears 1 and 2 (second chance) and
            // evicts key 3, the first entry without a second chance
            map.put(5, 50);

            assertNull(map.getRaw(3), "key 3 must have been evicted");
            assertEquals(10, map.getRaw(1));
            assertEquals(20, map.getRaw(2));
            assertEquals(40, map.getRaw(4));
            assertEquals(50, map.getRaw(5));
            assertEquals(4, map.size());
            assertEquals(4, map.trueSize());
        }

        @Test
        @DisplayName("Updating an existing key in a full map does not evict")
        void updateWhenFullDoesNotEvict()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            for (int i = 0; i < 4; i++)
            {
                map.put(i, i * 10);
            }

            // overwrite key 1 while the map is full
            assertEquals(10, map.put(1, 111));

            assertEquals(4, map.size());
            assertEquals(4, map.trueSize());
            assertEquals(111, map.get(1));
            // nobody was evicted
            for (int i = 0; i < 4; i++)
            {
                assertNotNull(map.getRaw(i), "key " + i + " must survive the update");
            }
        }

        @Test
        @DisplayName("Eviction followed by more inserts keeps the map consistent")
        void evictionChurnStaysConsistent()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(16);

            for (int round = 0; round < 20; round++)
            {
                // insert twice the capacity worth of fresh keys
                for (int i = 0; i < 32; i++)
                {
                    map.put(round * 1000 + i, i);
                }

                assertEquals(16, map.size());
                assertEquals(16, map.trueSize());

                // every reported key must be retrievable
                for (final Integer k : map.keys())
                {
                    assertNotNull(map.getRaw(k), "key " + k + " listed but not found");
                }
            }
        }
    }

    @Nested
    @DisplayName("Second chance flag mechanics")
    class SecondChanceFlags
    {
        @Test
        @DisplayName("New entries are created with a second chance")
        void flagsTrueAfterInsert()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            map.put(0, 0);
            map.put(1, 10);

            assertTrue(secondChanceOf(map, 0));
            assertTrue(secondChanceOf(map, 1));
        }

        @Test
        @DisplayName("get() sets the flag, getRaw() leaves it untouched")
        void flagSemantics()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            for (int i = 0; i < 4; i++)
            {
                map.put(i, i);
            }
            // one eviction sweep clears all flags (key 0 is evicted)
            map.put(4, 40);
            assertFalse(secondChanceOf(map, 1));

            // getRaw leaves the flag false
            assertEquals(1, map.getRaw(1));
            assertFalse(secondChanceOf(map, 1));

            // get sets it
            assertEquals(1, map.get(1));
            assertTrue(secondChanceOf(map, 1));
        }

        @Test
        @DisplayName("Overwrite via put refreshes the second chance")
        void overwriteRefreshesFlag()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            for (int i = 0; i < 4; i++)
            {
                map.put(i, i);
            }
            map.put(4, 40); // clears all flags, evicts key 0
            assertFalse(secondChanceOf(map, 1));

            map.put(1, 111); // overwrite
            assertTrue(secondChanceOf(map, 1));
            assertEquals(111, map.getRaw(1));
        }
    }

    @Nested
    @DisplayName("Debug views")
    class DebugViews
    {
        @Test
        @DisplayName("toString contains the essential state")
        void toStringSmoke()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            map.put(0, 0);

            final String s = map.toString();
            assertNotNull(s);
            assertTrue(s.contains("LRUClockMap{"), s);
            assertTrue(s.contains("size:"), s);
            assertTrue(s.contains("maxSize:"), s);
            assertTrue(s.contains("clockHand:"), s);
            assertTrue(s.contains("FREE"), s);
        }

        @Test
        @DisplayName("getDebugData aligns with size and positions")
        void debugDataConsistency()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(4);
            for (int i = 0; i < 4; i++)
            {
                map.put(i, i);
            }

            final var debugData = map.getDebugData();
            assertEquals(map.occupiedSpace(), debugData.size());

            int nonNull = 0;
            for (int i = 0; i < debugData.size(); i++)
            {
                final var dw = debugData.get(i);
                if (dw != null)
                {
                    nonNull++;
                    assertEquals(i, dw.currentPosition, "currentPosition must match the array index");
                    assertTrue(dw.truePosition >= 0 && dw.truePosition < map.occupiedSpace(),
                               "truePosition must be a valid slot");
                    // for Integer keys 0..3 in a capacity-8 array, home slot == key
                    assertEquals(dw.key.intValue(), dw.truePosition);
                }
            }
            assertEquals(map.size(), nonNull);
        }
    }

    @Nested
    @DisplayName("Stress and invariants")
    class StressInvariants
    {
        @Test
        @DisplayName("Randomized operations keep size and value invariants")
        void randomizedInvariantCheck()
        {
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(16);
            final Map<Integer, Integer> lastValue = new HashMap<>(); // last put value per key
            final Random random = new Random(42L);

            for (int step = 0; step < 5_000; step++)
            {
                final int key = random.nextInt(64);

                if (random.nextInt(10) < 7)
                {
                    map.put(key, step);
                    lastValue.put(key, step);
                }
                else
                {
                    map.remove(key);
                    lastValue.remove(key);
                }

                assertTrue(map.size() <= 16, "size exceeded maxSize at step " + step);
                assertEquals(map.size(), map.trueSize(), "size/trueSize mismatch at step " + step);
            }

            // every key reported by keys() exists and carries the last written value
            final List<Integer> keys = map.keys();
            assertEquals(map.size(), keys.size());
            for (final Integer k : keys)
            {
                assertEquals(lastValue.get(k), map.getRaw(k), "stale value for key " + k);
            }

            // every reference key is either evicted or has the correct value,
            // and the number of surviving reference keys matches size()
            int survivors = 0;
            for (final Map.Entry<Integer, Integer> e : lastValue.entrySet())
            {
                final Integer v = map.getRaw(e.getKey());
                if (v != null)
                {
                    assertEquals(e.getValue(), v, "value mismatch for key " + e.getKey());
                    survivors++;
                }
            }
            assertEquals(map.size(), survivors, "map holds keys outside the reference set");
        }
    }
}
