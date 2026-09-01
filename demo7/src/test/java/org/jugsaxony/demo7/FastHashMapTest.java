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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FastHashMap}.
 */
class FastHashMapTest
{
    private FastHashMap<String, Integer> map;

    @BeforeEach
    void setUp()
    {
        map = new FastHashMap<>();
    }

    // ----------------------------------------------------------------
    // 6.1 Basic behavior
    // ----------------------------------------------------------------

    @Test
    void newMapIsEmpty()
    {
        assertEquals(0, map.size());
        assertTrue(map.keys().isEmpty());
        assertTrue(map.values().isEmpty());
        assertNull(map.get("anything"));
    }

    @Test
    void putNewKeyReturnsNullAndStoresValue()
    {
        assertNull(map.put("a", 1));
        assertEquals(1, map.size());
        assertEquals(1, map.get("a"));
    }

    @Test
    void putExistingKeyReturnsOldValueAndReplaces()
    {
        map.put("a", 1);
        assertEquals(1, map.put("a", 2));
        assertEquals(2, map.get("a"));
        assertEquals(1, map.size());
    }

    @Test
    void getMissingKeyReturnsNull()
    {
        map.put("a", 1);
        assertNull(map.get("b"));
    }

    @Test
    void removeExistingKeyReturnsValueAndShrinksSize()
    {
        map.put("a", 1);
        map.put("b", 2);

        assertEquals(1, map.remove("a"));
        assertEquals(1, map.size());
        assertNull(map.get("a"));
        assertEquals(2, map.get("b"));
    }

    @Test
    void removeMissingKeyReturnsNullAndKeepsSize()
    {
        map.put("a", 1);
        assertNull(map.remove("missing"));
        assertEquals(1, map.size());
    }

    @Test
    void putAndRemoveManyKeysInSameBucketOrderIndependently()
    {
        for (int i = 0; i < 100; i++)
        {
            map.put("key-" + i, i);
        }
        for (int i = 0; i < 100; i += 2)
        {
            assertEquals(i, map.remove("key-" + i));
        }
        assertEquals(50, map.size());
        for (int i = 0; i < 100; i++)
        {
            if (i % 2 == 0)
            {
                assertNull(map.get("key-" + i));
            }
            else
            {
                assertEquals(i, map.get("key-" + i));
            }
        }
    }

    // ----------------------------------------------------------------
    // 6.2 Null handling
    // ----------------------------------------------------------------

    @Test
    void nullKeyIsRejected()
    {
        assertThrows(NullPointerException.class, () -> map.put(null, 1));
        assertThrows(NullPointerException.class, () -> map.get(null));
        assertThrows(NullPointerException.class, () -> map.remove(null));
        assertEquals(0, map.size());
    }

    @Test
    void nullValuesAreAllowed()
    {
        assertNull(map.put("a", null));
        assertEquals(1, map.size());
        assertNull(map.get("a"));
        assertTrue(map.keys().contains("a"));
        assertTrue(map.values().contains(null));

        assertNull(map.put("a", 5));
        assertEquals(5, map.get("a"));

        map.put("a", null);
        assertEquals(1, map.size());
        assertNull(map.remove("a"));
        assertEquals(0, map.size());
    }

    // ----------------------------------------------------------------
    // 6.3 Collection views
    // ----------------------------------------------------------------

    @Test
    void keysAndValuesMatchReferenceMap()
    {
        final HashMap<String, Integer> reference = new HashMap<>();
        for (int i = 0; i < 500; i++)
        {
            final String key = "key-" + i;
            final Integer value = (i % 7 == 0) ? null : i;
            map.put(key, value);
            reference.put(key, value);
        }

        assertEquals(new HashSet<>(reference.keySet()), new HashSet<>(map.keys()));

        final List<Integer> expectedValues = new ArrayList<>(reference.values());
        final List<Integer> actualValues = new ArrayList<>(map.values());
        Collections.sort(expectedValues, new NullSafeComparator());
        Collections.sort(actualValues, new NullSafeComparator());
        assertEquals(expectedValues, actualValues);
    }

    @Test
    void returnedListsAreIndependentCopies()
    {
        map.put("a", 1);
        map.put("b", 2);

        final List<String> keys = map.keys();
        keys.clear();
        keys.add("forged");

        final List<Integer> values = map.values();
        values.clear();

        assertEquals(2, map.size());
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
    }

    // ----------------------------------------------------------------
    // 6.4 Clear
    // ----------------------------------------------------------------

    @Test
    void clearEmptiesTheMap()
    {
        for (int i = 0; i < 100; i++)
        {
            map.put("key-" + i, i);
        }

        map.clear();

        assertEquals(0, map.size());
        for (int i = 0; i < 100; i++)
        {
            assertNull(map.get("key-" + i));
        }
        assertTrue(map.keys().isEmpty());
        assertTrue(map.values().isEmpty());
    }

    @Test
    void mapIsFullyUsableAfterClear()
    {
        for (int i = 0; i < 100; i++)
        {
            map.put("key-" + i, i);
        }
        map.clear();

        for (int i = 0; i < 200; i++)
        {
            map.put("new-" + i, i);
        }
        assertEquals(200, map.size());
        for (int i = 0; i < 200; i++)
        {
            assertEquals(i, map.get("new-" + i));
        }
    }

    // ----------------------------------------------------------------
    // 6.5 Growth and rehashing
    // ----------------------------------------------------------------

    @Test
    void manyEntriesSurviveMultipleResizes()
    {
        final int count = 10_000;
        final int capacityBefore = map.capacity();

        for (int i = 0; i < count; i++)
        {
            assertNull(map.put("key-" + i, i));
        }

        assertEquals(count, map.size());
        assertTrue(map.capacity() > capacityBefore);
        assertTrue((map.capacity() & (map.capacity() - 1)) == 0, "capacity must stay a power of two");

        for (int i = 0; i < count; i++)
        {
            assertEquals(i, map.get("key-" + i));
        }
        for (int i = 0; i < count; i++)
        {
            assertEquals(i, map.remove("key-" + i));
        }
        assertEquals(0, map.size());
    }

    @Test
    void sizeStaysCorrectAcrossGrowthAndReplacement()
    {
        for (int i = 0; i < 1000; i++)
        {
            map.put("key-" + i, i);
        }
        assertEquals(1000, map.size());

        for (int i = 0; i < 1000; i++)
        {
            map.put("key-" + i, i + 1);
        }
        assertEquals(1000, map.size());
        for (int i = 0; i < 1000; i++)
        {
            assertEquals(i + 1, map.get("key-" + i));
        }
    }

    // ----------------------------------------------------------------
    // 6.6 Collision robustness
    // ----------------------------------------------------------------

    @Test
    void allKeysCollidingStillWork()
    {
        final FastHashMap<ConstantHashKey, Integer> collisionMap = new FastHashMap<>();
        final int count = 200;

        for (int i = 0; i < count; i++)
        {
            assertNull(collisionMap.put(new ConstantHashKey(i), i));
        }
        assertEquals(count, collisionMap.size());

        for (int i = 0; i < count; i++)
        {
            assertEquals(i, collisionMap.get(new ConstantHashKey(i)));
        }

        assertEquals(17, collisionMap.put(new ConstantHashKey(17), -1));
        assertEquals(-1, collisionMap.get(new ConstantHashKey(17)));
        assertEquals(count, collisionMap.size());

        for (int i = 0; i < count; i++)
        {
            final Integer expected = (i == 17) ? -1 : i;
            assertEquals(expected, collisionMap.remove(new ConstantHashKey(i)));
        }
        assertEquals(0, collisionMap.size());
    }

    @Test
    void sameHashButDifferentKeysAreStoredSeparately()
    {
        final FastHashMap<ConstantHashKey, String> collisionMap = new FastHashMap<>();

        collisionMap.put(new ConstantHashKey(1), "one");
        collisionMap.put(new ConstantHashKey(2), "two");

        assertEquals(2, collisionMap.size());
        assertEquals("one", collisionMap.get(new ConstantHashKey(1)));
        assertEquals("two", collisionMap.get(new ConstantHashKey(2)));
    }

    @Test
    void zeroAndNegativeHashCodesWork()
    {
        final FastHashMap<FixedHashKey, Integer> oddMap = new FastHashMap<>();

        oddMap.put(new FixedHashKey(0), 0);
        oddMap.put(new FixedHashKey(-1), 1);
        oddMap.put(new FixedHashKey(Integer.MIN_VALUE), 2);
        oddMap.put(new FixedHashKey(Integer.MAX_VALUE), 3);

        assertEquals(4, oddMap.size());
        assertEquals(0, oddMap.get(new FixedHashKey(0)));
        assertEquals(1, oddMap.get(new FixedHashKey(-1)));
        assertEquals(2, oddMap.get(new FixedHashKey(Integer.MIN_VALUE)));
        assertEquals(3, oddMap.get(new FixedHashKey(Integer.MAX_VALUE)));

        assertEquals(2, oddMap.remove(new FixedHashKey(Integer.MIN_VALUE)));
        assertEquals(3, oddMap.size());
        assertNull(oddMap.get(new FixedHashKey(Integer.MIN_VALUE)));
    }

    // ----------------------------------------------------------------
    // 6.7 Randomized differential test against java.util.HashMap
    // ----------------------------------------------------------------

    @Test
    void randomOperationsMatchJavaHashMap()
    {
        final FastHashMap<String, Integer> fastMap = new FastHashMap<>();
        final HashMap<String, Integer> oracle = new HashMap<>();
        final Random random = new Random(42);

        final int operations = 100_000;
        final int keySpace = 1_000;

        for (int i = 0; i < operations; i++)
        {
            final String key = "key-" + random.nextInt(keySpace);
            final int action = random.nextInt(3);

            if (action == 0)
            {
                final Integer value = (random.nextInt(10) == 0) ? null : random.nextInt();
                assertEquals(oracle.put(key, value), fastMap.put(key, value), "put return mismatch at op " + i);
            }
            else if (action == 1)
            {
                assertEquals(oracle.get(key), fastMap.get(key), "get mismatch at op " + i);
            }
            else
            {
                final Integer expected = oracle.remove(key);
                final Integer actual = fastMap.remove(key);
                assertEquals(expected, actual, "remove return mismatch at op " + i);
            }

            if (i % 1000 == 0)
            {
                assertEquals(oracle.size(), fastMap.size(), "size mismatch at op " + i);
            }
        }

        assertEquals(oracle.size(), fastMap.size());
        assertEquals(new HashSet<>(oracle.keySet()), new HashSet<>(fastMap.keys()));
        for (final String key : oracle.keySet())
        {
            assertEquals(oracle.get(key), fastMap.get(key));
        }
    }

    // ----------------------------------------------------------------
    // Test helpers
    // ----------------------------------------------------------------

    /**
     * Key whose hashCode is constant, forcing every entry into the
     * same bucket.
     */
    private static final class ConstantHashKey
    {
        private final int id;

        ConstantHashKey(final int id)
        {
            this.id = id;
        }

        @Override
        public int hashCode()
        {
            return 42;
        }

        @Override
        public boolean equals(final Object other)
        {
            return (other instanceof ConstantHashKey that) && this.id == that.id;
        }
    }

    /**
     * Key with a fixed hash code, used to test zero and negative
     * hash values.
     */
    private static final class FixedHashKey
    {
        private final int hash;

        FixedHashKey(final int hash)
        {
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
            return (other instanceof FixedHashKey that) && this.hash == that.hash;
        }
    }

    /**
     * Null-safe comparator to sort value lists for comparison.
     */
    private static final class NullSafeComparator implements java.util.Comparator<Integer>
    {
        @Override
        public int compare(final Integer a, final Integer b)
        {
            if (Objects.equals(a, b))
            {
                return 0;
            }
            if (a == null)
            {
                return -1;
            }
            if (b == null)
            {
                return 1;
            }
            return Integer.compare(a, b);
        }
    }
}
