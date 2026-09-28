package org.jugsaxony.demo2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
 * Test suite for {@link FastHashMap}. Uses plain JUnit 6 assertions only.
 */
class FastHashMapTest
{
    /**
     * Key with a forced, constant hash code to deterministically create collisions.
     */
    static final class FixedHashKey
    {
        private final String id;
        private final int forcedHash;

        FixedHashKey(final String id, final int forcedHash)
        {
            this.id = id;
            this.forcedHash = forcedHash;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (!(o instanceof FixedHashKey other))
            {
                return false;
            }
            return id.equals(other.id);
        }

        @Override
        public int hashCode()
        {
            return forcedHash;
        }

        @Override
        public String toString()
        {
            return "FixedHashKey[" + id + "]";
        }
    }

    @Nested
    @DisplayName("Initial state")
    class InitialState
    {
        @Test
        @DisplayName("Newly created map is empty")
        void newInstanceIsEmpty()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertTrue(map.values().isEmpty());
        }
    }

    @Nested
    @DisplayName("Null key handling")
    class NullKeys
    {
        @Test
        @DisplayName("put with null key throws NullPointerException")
        void putNullKeyThrows()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            assertThrows(NullPointerException.class, () -> map.put(null, 1));
        }

        @Test
        @DisplayName("get with null key throws NullPointerException")
        void getNullKeyThrows()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            assertThrows(NullPointerException.class, () -> map.get(null));
        }

        @Test
        @DisplayName("remove with null key throws NullPointerException")
        void removeNullKeyThrows()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            assertThrows(NullPointerException.class, () -> map.remove(null));
        }
    }

    @Nested
    @DisplayName("Null value handling")
    class NullValues
    {
        @Test
        @DisplayName("Null value can be stored")
        void putNullValueIsStored()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();

            assertNull(map.put("a", null));
            assertEquals(1, map.size());
            assertNull(map.get("a"));
        }

        @Test
        @DisplayName("Null value appears in values()")
        void nullValueAppearsInValues()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", null);

            assertEquals(1, map.values().size());
            assertTrue(map.values().contains(null));
        }

        @Test
        @DisplayName("Replacing a value with null returns the old value")
        void replaceValueWithNull()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", 42);

            assertEquals(42, map.put("a", null));
            assertNull(map.get("a"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("Replacing a null value with a real value returns null")
        void replaceNullWithValue()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", null);

            assertNull(map.put("a", 7));
            assertEquals(7, map.get("a"));
            assertEquals(1, map.size());
        }
    }

    @Nested
    @DisplayName("put / get basics")
    class PutGet
    {
        @Test
        @DisplayName("put returns null for a new key")
        void putReturnsNullForNewKey()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            assertNull(map.put("k", 1));
        }

        @Test
        @DisplayName("put returns old value on replace and keeps size")
        void putReturnsOldValueOnReplace()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("k", 1);

            assertEquals(1, map.put("k", 2));
            assertEquals(1, map.size());
            assertEquals(2, map.get("k"));
        }

        @Test
        @DisplayName("get of absent key returns null")
        void getAbsentKeyReturnsNull()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            assertNull(map.get("missing"));
        }

        @Test
        @DisplayName("get returns values for several entries")
        void getAfterPut()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", 1);
            map.put("b", 2);
            map.put("c", 3);

            assertEquals(1, map.get("a"));
            assertEquals(2, map.get("b"));
            assertEquals(3, map.get("c"));
            assertEquals(3, map.size());
        }

        @Test
        @DisplayName("size tracks distinct keys only")
        void sizeTracksPuts()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            for (int i = 0; i < 50; i++)
            {
                map.put("k" + i, i);
            }
            assertEquals(50, map.size());

            // overwrite all again, size must not change
            for (int i = 0; i < 50; i++)
            {
                map.put("k" + i, i * 2);
            }
            assertEquals(50, map.size());
            assertEquals(98, map.get("k49"));
        }
    }

    @Nested
    @DisplayName("remove")
    class Remove
    {
        @Test
        @DisplayName("remove returns old value, deletes mapping, decrements size")
        void removeExistingKeyReturnsOldValue()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", 1);
            map.put("b", 2);

            assertEquals(1, map.remove("a"));
            assertNull(map.get("a"));
            assertEquals(2, map.get("b"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("remove of absent key returns null and keeps size")
        void removeAbsentKeyReturnsNull()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", 1);

            assertNull(map.remove("missing"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("remove does not break probe chains")
        void removeDoesNotBreakProbeChain()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();

            // all keys share one hash code -> one big collision cluster
            final List<FixedHashKey> keys = new ArrayList<>();
            for (int i = 0; i < 10; i++)
            {
                final FixedHashKey k = new FixedHashKey("key" + i, 12345);
                keys.add(k);
                map.put(k, "v" + i);
            }

            // remove from the middle of the cluster
            assertEquals("v4", map.remove(keys.get(4)));
            assertEquals("v5", map.remove(keys.get(5)));

            assertEquals(8, map.size());

            // every remaining entry must still be reachable
            for (int i = 0; i < 10; i++)
            {
                if (i == 4 || i == 5)
                {
                    assertNull(map.get(keys.get(i)), "removed key " + i + " must be gone");
                }
                else
                {
                    assertEquals("v" + i, map.get(keys.get(i)), "key " + i + " must survive cluster repair");
                }
            }
        }

        @Test
        @DisplayName("Entries can be reinserted after removal")
        void removeThenReinsert()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", 1);
            map.remove("a");

            assertNull(map.put("a", 99));
            assertEquals(99, map.get("a"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("Removing all entries empties the map")
        void removeAllEntries()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            final List<String> keys = new ArrayList<>();
            for (int i = 0; i < 100; i++)
            {
                keys.add("k" + i);
                map.put("k" + i, i);
            }

            for (final String k : keys)
            {
                assertNotNull(map.remove(k));
            }

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertTrue(map.values().isEmpty());
            for (final String k : keys)
            {
                assertNull(map.get(k));
            }
        }

        @Test
        @DisplayName("Removal of every second entry keeps the rest intact")
        void removeEverySecondEntry()
        {
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();
            for (int i = 0; i < 200; i++)
            {
                map.put(i, i * 10);
            }

            for (int i = 0; i < 200; i += 2)
            {
                assertEquals(i * 10, map.remove(i));
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
                    assertEquals(i * 10, map.get(i));
                }
            }
        }
    }

    @Nested
    @DisplayName("Collisions")
    class Collisions
    {
        @Test
        @DisplayName("Many keys with identical hash codes are handled")
        void collisionsAreHandled()
        {
            final FastHashMap<FixedHashKey, Integer> map = new FastHashMap<>();

            final int count = 12;
            for (int i = 0; i < count; i++)
            {
                assertNull(map.put(new FixedHashKey("k" + i, 42), i));
            }

            assertEquals(count, map.size());
            for (int i = 0; i < count; i++)
            {
                assertEquals(i, map.get(new FixedHashKey("k" + i, 42)));
            }

            final Set<FixedHashKey> keySet = new HashSet<>(map.keys());
            assertEquals(count, keySet.size());
            for (int i = 0; i < count; i++)
            {
                assertTrue(keySet.contains(new FixedHashKey("k" + i, 42)));
            }
        }

        @Test
        @DisplayName("Overwriting within a collision cluster works")
        void overwriteWithinCollisionCluster()
        {
            final FastHashMap<FixedHashKey, Integer> map = new FastHashMap<>();
            for (int i = 0; i < 8; i++)
            {
                map.put(new FixedHashKey("k" + i, 7), i);
            }

            assertEquals(3, map.put(new FixedHashKey("k3", 7), 333));
            assertEquals(8, map.size());
            assertEquals(333, map.get(new FixedHashKey("k3", 7)));
        }

        @Test
        @DisplayName("Probing wraps around the end of the backing array")
        void wrapAroundProbing()
        {
            // hash 0xFFFF... spreads to a slot near the end of the small backing array,
            // forcing probes to wrap to index 0
            final int endHash = 0xFFFFFFFF;
            final FastHashMap<FixedHashKey, Integer> map = new FastHashMap<>();

            final int count = 10;
            for (int i = 0; i < count; i++)
            {
                map.put(new FixedHashKey("w" + i, endHash), i);
            }

            assertEquals(count, map.size());
            for (int i = 0; i < count; i++)
            {
                assertEquals(i, map.get(new FixedHashKey("w" + i, endHash)));
            }

            // remove some and verify the wrap-around chain still works
            map.remove(new FixedHashKey("w3", endHash));
            map.remove(new FixedHashKey("w7", endHash));
            assertEquals(count - 2, map.size());
            for (int i = 0; i < count; i++)
            {
                if (i == 3 || i == 7)
                {
                    assertNull(map.get(new FixedHashKey("w" + i, endHash)));
                }
                else
                {
                    assertEquals(i, map.get(new FixedHashKey("w" + i, endHash)));
                }
            }
        }
    }

    @Nested
    @DisplayName("Resize")
    class Resize
    {
        @Test
        @DisplayName("All entries survive multiple resizes")
        void resizePreservesAllEntries()
        {
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();

            final int count = 100;
            for (int i = 0; i < count; i++)
            {
                map.put(i, i * 3);
            }

            assertEquals(count, map.size());
            for (int i = 0; i < count; i++)
            {
                assertEquals(i * 3, map.get(i));
            }
        }

        @Test
        @DisplayName("Collision clusters survive resizes")
        void resizeWithCollisions()
        {
            final FastHashMap<FixedHashKey, Integer> map = new FastHashMap<>();

            final int count = 60; // forces several resizes from capacity 16
            for (int i = 0; i < count; i++)
            {
                map.put(new FixedHashKey("c" + i, 1), i);
            }

            assertEquals(count, map.size());
            for (int i = 0; i < count; i++)
            {
                assertEquals(i, map.get(new FixedHashKey("c" + i, 1)));
            }
        }

        @Test
        @DisplayName("Map grows to hold many entries")
        void resizeManyEntries()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();

            final int count = 1_000;
            for (int i = 0; i < count; i++)
            {
                map.put("key-" + i, i);
            }

            assertEquals(count, map.size());
            for (int i = 0; i < count; i++)
            {
                assertEquals(i, map.get("key-" + i));
            }
        }

        @Test
        @DisplayName("Remove after resize works on rehashed layout")
        void removeAfterResize()
        {
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();
            for (int i = 0; i < 100; i++)
            {
                map.put(i, i);
            }

            for (int i = 0; i < 100; i += 3)
            {
                assertEquals(i, map.remove(i));
            }

            for (int i = 0; i < 100; i++)
            {
                if (i % 3 == 0)
                {
                    assertNull(map.get(i));
                }
                else
                {
                    assertEquals(i, map.get(i));
                }
            }
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear
    {
        @Test
        @DisplayName("clear empties the map completely")
        void clearEmptiesMap()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            for (int i = 0; i < 50; i++)
            {
                map.put("k" + i, i);
            }

            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertTrue(map.values().isEmpty());
            for (int i = 0; i < 50; i++)
            {
                assertNull(map.get("k" + i));
            }
        }

        @Test
        @DisplayName("Map is fully reusable after clear")
        void mapIsReusableAfterClear()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", 1);
            map.clear();

            assertNull(map.put("a", 2));
            assertEquals(2, map.get("a"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("clear on an empty map is a no-op")
        void clearOnEmptyMap()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
        }
    }

    @Nested
    @DisplayName("keys / values views")
    class KeysValues
    {
        @Test
        @DisplayName("keys() contains exactly the inserted keys")
        void keysContainsAllInsertedKeys()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            final Set<String> expected = new HashSet<>();
            for (int i = 0; i < 40; i++)
            {
                map.put("k" + i, i);
                expected.add("k" + i);
            }

            final List<String> keys = map.keys();
            assertEquals(expected.size(), keys.size());
            assertEquals(expected, new HashSet<>(keys));
        }

        @Test
        @DisplayName("values() contains exactly the inserted values")
        void valuesContainsAllInsertedValues()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            final Set<Integer> expected = new HashSet<>();
            for (int i = 0; i < 40; i++)
            {
                map.put("k" + i, i);
                expected.add(i);
            }

            final List<Integer> values = map.values();
            assertEquals(expected.size(), values.size());
            assertEquals(expected, new HashSet<>(values));
        }

        @Test
        @DisplayName("keys() and values() are positionally consistent")
        void keysAndValuesAreConsistent()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            for (int i = 0; i < 30; i++)
            {
                map.put("k" + i, i * 11);
            }

            final List<String> keys = map.keys();
            final List<Integer> values = map.values();

            assertEquals(keys.size(), values.size());
            for (int i = 0; i < keys.size(); i++)
            {
                assertEquals(map.get(keys.get(i)), values.get(i),
                             "keys()[" + i + "] must map to values()[" + i + "]");
            }
        }

        @Test
        @DisplayName("Mutating returned lists does not affect the map")
        void mutatingReturnedListsDoesNotAffectMap()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", 1);
            map.put("b", 2);

            final List<String> keys = map.keys();
            final List<Integer> values = map.values();
            keys.clear();
            values.clear();
            keys.add("bogus");

            assertEquals(2, map.size());
            assertEquals(1, map.get("a"));
            assertEquals(2, map.get("b"));
            assertEquals(2, map.keys().size());
            assertEquals(2, map.values().size());
        }

        @Test
        @DisplayName("keys() and values() of an empty map are empty")
        void keysValuesOnEmptyMapReturnEmptyLists()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();

            assertNotNull(map.keys());
            assertNotNull(map.values());
            assertTrue(map.keys().isEmpty());
            assertTrue(map.values().isEmpty());
        }

        @Test
        @DisplayName("keys() contains no nulls even when values are null")
        void keysContainNoNulls()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", null);
            map.put("b", 1);

            for (final String k : map.keys())
            {
                assertNotNull(k);
            }
            assertEquals(2, map.keys().size());
        }
    }

    @Nested
    @DisplayName("Reference comparison")
    class ReferenceComparison
    {
        @Test
        @DisplayName("Randomized operations match java.util.HashMap")
        void randomizedOperationSequence()
        {
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();
            final Map<Integer, Integer> reference = new HashMap<>();
            final Random random = new Random(42L);

            for (int step = 0; step < 20_000; step++)
            {
                final int key = random.nextInt(500); // limited key space -> collisions in key reuse
                final int op = random.nextInt(10);

                switch (op)
                {
                    case 0, 1, 2, 3, 4, 5 -> // 60% put
                    {
                        final Integer value = random.nextInt(10) == 0 ? null : random.nextInt(10_000);
                        assertEquals(reference.put(key, value), map.put(key, value),
                                     "put mismatch at step " + step);
                    }
                    case 6, 7 -> // 20% remove
                    {
                        // reference map holds no null values via put above? it may,
                        // but HashMap.remove returns the value either way
                        assertEquals(reference.remove(key), map.remove(key),
                                     "remove mismatch at step " + step);
                    }
                    case 8 -> // 10% clear
                    {
                        reference.clear();
                        map.clear();
                    }
                    default -> // 10% get
                    {
                        assertEquals(reference.get(key), map.get(key),
                                     "get mismatch at step " + step);
                    }
                }

                assertEquals(reference.size(), map.size(), "size mismatch at step " + step);
            }

            // final full comparison
            assertEquals(reference.size(), map.size());
            assertEquals(reference.keySet(), new HashSet<>(map.keys()));
            for (final Map.Entry<Integer, Integer> e : reference.entrySet())
            {
                assertEquals(e.getValue(), map.get(e.getKey()));
            }
        }

        @Test
        @DisplayName("Reference comparison with colliding keys")
        void randomizedCollidingKeySequence()
        {
            final FastHashMap<FixedHashKey, Integer> map = new FastHashMap<>();
            final Map<FixedHashKey, Integer> reference = new HashMap<>();
            final Random random = new Random(1337L);

            for (int step = 0; step < 5_000; step++)
            {
                // hash derives deterministically from the id (only 4 distinct hashes)
                // to honor the equals/hashCode contract while forcing collisions
                final int id = random.nextInt(100);
                final FixedHashKey key = new FixedHashKey("k" + id, id % 4);

                if (random.nextBoolean())
                {
                    final Integer value = random.nextInt(20) == 0 ? null : random.nextInt();
                    assertEquals(reference.put(key, value), map.put(key, value));
                }
                else
                {
                    assertEquals(reference.remove(key), map.remove(key));
                }

                assertEquals(reference.size(), map.size(), "size mismatch at step " + step);
            }

            for (final FixedHashKey k : reference.keySet())
            {
                assertEquals(reference.get(k), map.get(k));
            }
        }
    }

    @Nested
    @DisplayName("General behavior")
    class GeneralBehavior
    {
        @Test
        @DisplayName("Works with various realistic key types")
        void distinctStringKeys()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();

            for (int i = 0; i < 500; i++)
            {
                map.put("user:" + i + "@example.org", "payload-" + i);
            }

            assertEquals(500, map.size());
            for (int i = 0; i < 500; i++)
            {
                assertEquals("payload-" + i, map.get("user:" + i + "@example.org"));
            }
        }

        @Test
        @DisplayName("Keys with equal hashCode but unequal identity are distinct entries")
        void equalHashUnequalKeys()
        {
            final FixedHashKey a = new FixedHashKey("a", 5);
            final FixedHashKey b = new FixedHashKey("b", 5);

            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>();
            map.put(a, "A");
            map.put(b, "B");

            assertEquals(2, map.size());
            assertEquals("A", map.get(a));
            assertEquals("B", map.get(b));
        }

        @Test
        @DisplayName("Keys are compared with equals, not identity")
        void equalsBasedKeyLookup()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put(new String("dynamic"), 1);

            assertEquals(1, map.get(new String("dynamic")));
        }

        @Test
        @DisplayName("toString of keys is never required by the map itself")
        void keysWithoutToString()
        {
            final FastHashMap<Object, Integer> map = new FastHashMap<>();
            final Object k1 = new Object()
            {
                @Override
                public int hashCode()
                {
                    return 9;
                }
            };
            final Object k2 = new Object()
            {
                @Override
                public int hashCode()
                {
                    return 9;
                }
            };

            map.put(k1, 1);
            map.put(k2, 2);

            assertEquals(2, map.size());
            assertEquals(1, map.get(k1));
            assertEquals(2, map.get(k2));
        }

        @Test
        @DisplayName("Map state stays consistent under heavy mutation")
        void heavyMutation()
        {
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();

            for (int round = 0; round < 10; round++)
            {
                for (int i = 0; i < 100; i++)
                {
                    map.put(i, round * 1000 + i);
                }
                for (int i = 0; i < 100; i += 2)
                {
                    map.remove(i);
                }
                for (int i = 0; i < 100; i += 2)
                {
                    map.put(i, round * 1000 + i);
                }

                assertEquals(100, map.size(), "round " + round);
                for (int i = 0; i < 100; i++)
                {
                    assertEquals(round * 1000 + i, map.get(i), "round " + round + " key " + i);
                }
            }
        }

        @Test
        @DisplayName("Objects used as keys rely on equals/hashCode contract")
        void keyContractSanity()
        {
            final FastHashMap<FixedHashKey, Integer> map = new FastHashMap<>();
            map.put(new FixedHashKey("x", 1), 10);

            // equal object, different identity
            assertEquals(10, map.get(new FixedHashKey("x", 1)));
            assertFalse(map.keys().isEmpty());
            assertTrue(map.keys().contains(new FixedHashKey("x", 1)));
        }
    }
}
