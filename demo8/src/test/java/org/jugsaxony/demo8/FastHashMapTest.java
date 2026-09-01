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
package org.jugsaxony.demo8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class FastHashMapTest
{
    private FastHashMap<String, String> map;

    @BeforeEach
    public void setUp()
    {
        map = new FastHashMap<>();
    }

    // =========================================================================
    // Construction & Initialization
    // =========================================================================

    @Test
    public void newMapIsEmpty()
    {
        assertEquals(0, map.size());
        assertTrue(map.keys().isEmpty());
        assertTrue(map.values().isEmpty());
        assertNull(map.get("anyKey"));
    }

    @Test
    public void constructorWithInitialCapacity()
    {
        final FastHashMap<Integer, String> customMap = new FastHashMap<>(32);
        assertEquals(0, customMap.size());
        customMap.put(1, "one");
        assertEquals("one", customMap.get(1));
        assertEquals(1, customMap.size());
    }

    @Test
    public void constructorWithInitialCapacityAndLoadFactor()
    {
        final FastHashMap<Integer, String> customMap = new FastHashMap<>(8, 0.5f);
        assertEquals(0, customMap.size());
        for (int i = 0; i < 20; i++)
        {
            customMap.put(i, "val" + i);
        }
        assertEquals(20, customMap.size());
        for (int i = 0; i < 20; i++)
        {
            assertEquals("val" + i, customMap.get(i));
        }
    }

    @Test
    public void constructorInvalidArgumentsThrow()
    {
        assertThrows(IllegalArgumentException.class, () -> new FastHashMap<>(-1));
        assertThrows(IllegalArgumentException.class, () -> new FastHashMap<>(16, 0.0f));
        assertThrows(IllegalArgumentException.class, () -> new FastHashMap<>(16, -0.5f));
        assertThrows(IllegalArgumentException.class, () -> new FastHashMap<>(16, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> new FastHashMap<>(16, 1.5f));
        assertThrows(IllegalArgumentException.class, () -> new FastHashMap<>(16, Float.NaN));
    }

    // =========================================================================
    // Basic Put, Get, Remove Operations
    // =========================================================================

    @Test
    public void putAndGetSingleEntry()
    {
        assertNull(map.put("k1", "v1"));
        assertEquals(1, map.size());
        assertEquals("v1", map.get("k1"));
    }

    @Test
    public void putAndGetMultipleEntries()
    {
        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");

        assertEquals(3, map.size());
        assertEquals("v1", map.get("k1"));
        assertEquals("v2", map.get("k2"));
        assertEquals("v3", map.get("k3"));
        assertNull(map.get("nonexistent"));
    }

    @Test
    public void putOverwritesExistingValueAndReturnsOldValue()
    {
        assertNull(map.put("k1", "v1"));
        assertEquals("v1", map.put("k1", "v2"));
        assertEquals(1, map.size());
        assertEquals("v2", map.get("k1"));

        assertEquals("v2", map.put("k1", "v3"));
        assertEquals(1, map.size());
        assertEquals("v3", map.get("k1"));
    }

    @Test
    public void removeExistingEntryReturnsOldValue()
    {
        map.put("k1", "v1");
        map.put("k2", "v2");

        assertEquals("v1", map.remove("k1"));
        assertEquals(1, map.size());
        assertNull(map.get("k1"));
        assertEquals("v2", map.get("k2"));
    }

    @Test
    public void removeNonExistingEntryReturnsNull()
    {
        map.put("k1", "v1");
        assertNull(map.remove("unknown"));
        assertEquals(1, map.size());
    }

    @Test
    public void removeOnEmptyMapReturnsNull()
    {
        assertNull(map.remove("k1"));
        assertEquals(0, map.size());
    }

    @Test
    public void reinsertRemovedKey()
    {
        map.put("k1", "v1");
        assertEquals("v1", map.remove("k1"));
        assertNull(map.get("k1"));
        assertEquals(0, map.size());

        assertNull(map.put("k1", "v2"));
        assertEquals(1, map.size());
        assertEquals("v2", map.get("k1"));
    }

    // =========================================================================
    // Null Keys & Values Handling
    // =========================================================================

    @Test
    public void putNullKeyThrowsNullPointerException()
    {
        assertThrows(NullPointerException.class, () -> map.put(null, "value"));
    }

    @Test
    public void getNullKeyThrowsNullPointerException()
    {
        assertThrows(NullPointerException.class, () -> map.get(null));
    }

    @Test
    public void removeNullKeyThrowsNullPointerException()
    {
        assertThrows(NullPointerException.class, () -> map.remove(null));
    }

    @Test
    public void putNullValueAllowed()
    {
        assertNull(map.put("k1", null));
        assertEquals(1, map.size());
        assertNull(map.get("k1"));

        // Replace null with non-null
        assertNull(map.put("k1", "nonNull"));
        assertEquals("nonNull", map.get("k1"));

        // Replace non-null with null
        assertEquals("nonNull", map.put("k1", null));
        assertNull(map.get("k1"));
        assertEquals(1, map.size());

        // Remove key having null value
        assertNull(map.remove("k1"));
        assertEquals(0, map.size());
        assertNull(map.get("k1"));
    }

    // =========================================================================
    // Collision Handling & Linear Probing Integrity
    // =========================================================================

    private static class CollidingKey
    {
        final String id;
        final int fixedHash;

        CollidingKey(final String id, final int fixedHash)
        {
            this.id = id;
            this.fixedHash = fixedHash;
        }

        @Override
        public int hashCode()
        {
            return fixedHash;
        }

        @Override
        public boolean equals(final Object obj)
        {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            final CollidingKey other = (CollidingKey) obj;
            return Objects.equals(this.id, other.id);
        }

        @Override
        public String toString()
        {
            return "Key(" + id + ", hash=" + fixedHash + ")";
        }
    }

    @Test
    public void collisionLinearProbingLookup()
    {
        final FastHashMap<CollidingKey, String> colMap = new FastHashMap<>(16, 0.9f);
        final CollidingKey k1 = new CollidingKey("A", 42);
        final CollidingKey k2 = new CollidingKey("B", 42);
        final CollidingKey k3 = new CollidingKey("C", 42);
        final CollidingKey k4 = new CollidingKey("D", 42);

        colMap.put(k1, "valA");
        colMap.put(k2, "valB");
        colMap.put(k3, "valC");
        colMap.put(k4, "valD");

        assertEquals(4, colMap.size());
        assertEquals("valA", colMap.get(k1));
        assertEquals("valB", colMap.get(k2));
        assertEquals("valC", colMap.get(k3));
        assertEquals("valD", colMap.get(k4));
    }

    @Test
    public void collisionRemovalFromBeginningOfCluster()
    {
        final FastHashMap<CollidingKey, String> colMap = new FastHashMap<>(16, 0.9f);
        final CollidingKey k1 = new CollidingKey("A", 5);
        final CollidingKey k2 = new CollidingKey("B", 5);
        final CollidingKey k3 = new CollidingKey("C", 5);

        colMap.put(k1, "valA");
        colMap.put(k2, "valB");
        colMap.put(k3, "valC");

        // Remove first element in collision chain
        assertEquals("valA", colMap.remove(k1));
        assertEquals(2, colMap.size());
        assertNull(colMap.get(k1));
        assertEquals("valB", colMap.get(k2));
        assertEquals("valC", colMap.get(k3));
    }

    @Test
    public void collisionRemovalFromMiddleOfCluster()
    {
        final FastHashMap<CollidingKey, String> colMap = new FastHashMap<>(16, 0.9f);
        final CollidingKey k1 = new CollidingKey("A", 5);
        final CollidingKey k2 = new CollidingKey("B", 5);
        final CollidingKey k3 = new CollidingKey("C", 5);
        final CollidingKey k4 = new CollidingKey("D", 5);

        colMap.put(k1, "valA");
        colMap.put(k2, "valB");
        colMap.put(k3, "valC");
        colMap.put(k4, "valD");

        // Remove middle element in collision chain
        assertEquals("valB", colMap.remove(k2));
        assertEquals(3, colMap.size());
        assertEquals("valA", colMap.get(k1));
        assertNull(colMap.get(k2));
        assertEquals("valC", colMap.get(k3));
        assertEquals("valD", colMap.get(k4));
    }

    @Test
    public void collisionRemovalFromEndOfCluster()
    {
        final FastHashMap<CollidingKey, String> colMap = new FastHashMap<>(16, 0.9f);
        final CollidingKey k1 = new CollidingKey("A", 5);
        final CollidingKey k2 = new CollidingKey("B", 5);
        final CollidingKey k3 = new CollidingKey("C", 5);

        colMap.put(k1, "valA");
        colMap.put(k2, "valB");
        colMap.put(k3, "valC");

        // Remove last element in collision chain
        assertEquals("valC", colMap.remove(k3));
        assertEquals(2, colMap.size());
        assertEquals("valA", colMap.get(k1));
        assertEquals("valB", colMap.get(k2));
        assertNull(colMap.get(k3));
    }

    @Test
    public void collisionRemovalAllSequentially()
    {
        final FastHashMap<CollidingKey, String> colMap = new FastHashMap<>(16, 0.9f);
        final int n = 10;
        final List<CollidingKey> keys = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            final CollidingKey k = new CollidingKey("K" + i, 100);
            keys.add(k);
            colMap.put(k, "val" + i);
        }
        assertEquals(n, colMap.size());

        // Remove from middle iteratively
        while (!keys.isEmpty())
        {
            final int midIndex = keys.size() / 2;
            final CollidingKey keyToRemove = keys.remove(midIndex);
            assertNotNull(colMap.remove(keyToRemove));
            assertNull(colMap.get(keyToRemove));
            assertEquals(keys.size(), colMap.size());

            // Check that all remaining keys are still accessible
            for (final CollidingKey remainingKey : keys)
            {
                assertNotNull(colMap.get(remainingKey), "Failed to find remaining key " + remainingKey);
            }
        }
        assertEquals(0, colMap.size());
    }

    // =========================================================================
    // Dynamic Growth, Rehashing & Unbounded Behavior
    // =========================================================================

    @Test
    public void mapGrowsDynamicallyAcrossMultipleResizes()
    {
        final FastHashMap<Integer, Integer> intMap = new FastHashMap<>(4, 0.5f);
        final int count = 10_000;

        for (int i = 0; i < count; i++)
        {
            assertNull(intMap.put(i, i * 2));
            assertEquals(i + 1, intMap.size());
        }

        // Verify all entries are accessible
        for (int i = 0; i < count; i++)
        {
            assertEquals(i * 2, intMap.get(i));
        }

        // Remove every second key
        for (int i = 0; i < count; i += 2)
        {
            assertEquals(i * 2, intMap.remove(i));
        }
        assertEquals(count / 2, intMap.size());

        // Verify remaining entries
        for (int i = 0; i < count; i++)
        {
            if (i % 2 == 0)
            {
                assertNull(intMap.get(i));
            }
            else
            {
                assertEquals(i * 2, intMap.get(i));
            }
        }
    }

    // =========================================================================
    // keys() and values() Views
    // =========================================================================

    @Test
    public void keysAndValuesOnEmptyMap()
    {
        final List<String> keys = map.keys();
        final List<String> values = map.values();
        assertNotNull(keys);
        assertNotNull(values);
        assertTrue(keys.isEmpty());
        assertTrue(values.isEmpty());
    }

    @Test
    public void keysAndValuesContainAllEntries()
    {
        final int count = 50;
        final Set<String> expectedKeys = new HashSet<>();
        final Set<String> expectedValues = new HashSet<>();

        for (int i = 0; i < count; i++)
        {
            final String k = "key_" + i;
            final String v = "val_" + i;
            map.put(k, v);
            expectedKeys.add(k);
            expectedValues.add(v);
        }

        final List<String> actualKeys = map.keys();
        final List<String> actualValues = map.values();

        assertEquals(count, actualKeys.size());
        assertEquals(count, actualValues.size());
        assertEquals(expectedKeys, new HashSet<>(actualKeys));
        assertEquals(expectedValues, new HashSet<>(actualValues));
    }

    @Test
    public void keysAndValuesWithNullValues()
    {
        map.put("k1", "v1");
        map.put("k2", null);
        map.put("k3", "v3");

        final List<String> keys = map.keys();
        final List<String> values = map.values();

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
        assertTrue(keys.contains("k1"));
        assertTrue(keys.contains("k2"));
        assertTrue(keys.contains("k3"));
        assertTrue(values.contains("v1"));
        assertTrue(values.contains(null));
        assertTrue(values.contains("v3"));
    }

    @Test
    public void keysAndValuesReflectRemovals()
    {
        map.put("k1", "v1");
        map.put("k2", "v2");
        map.remove("k1");

        final List<String> keys = map.keys();
        final List<String> values = map.values();

        assertEquals(1, keys.size());
        assertEquals(1, values.size());
        assertFalse(keys.contains("k1"));
        assertTrue(keys.contains("k2"));
        assertFalse(values.contains("v1"));
        assertTrue(values.contains("v2"));
    }

    // =========================================================================
    // Clear Operation
    // =========================================================================

    @Test
    public void clearEmptiesTheMap()
    {
        for (int i = 0; i < 100; i++)
        {
            map.put("key" + i, "val" + i);
        }
        assertEquals(100, map.size());

        map.clear();

        assertEquals(0, map.size());
        assertTrue(map.keys().isEmpty());
        assertTrue(map.values().isEmpty());
        for (int i = 0; i < 100; i++)
        {
            assertNull(map.get("key" + i));
        }

        // Can insert again after clear
        map.put("newKey", "newVal");
        assertEquals(1, map.size());
        assertEquals("newVal", map.get("newKey"));
    }

    @Test
    public void clearOnEmptyMap()
    {
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.keys().isEmpty());
        assertTrue(map.values().isEmpty());
    }

    // =========================================================================
    // Randomized Stress Test
    // =========================================================================

    @Test
    public void randomizedStressTestAgainstReferenceMap()
    {
        final FastHashMap<Integer, Integer> testMap = new FastHashMap<>(8, 0.6f);
        final java.util.Map<Integer, Integer> refMap = new java.util.HashMap<>();
        final Random random = new Random(123456789L);

        final int operations = 50_000;
        final int keyRange = 2_000;

        for (int i = 0; i < operations; i++)
        {
            final int action = random.nextInt(100);
            final int key = random.nextInt(keyRange);

            if (action < 50)
            {
                // 50% put (some null values)
                final Integer val = (random.nextInt(10) == 0) ? null : random.nextInt(100_000);
                final Integer testOld = testMap.put(key, val);
                final Integer refOld = refMap.put(key, val);
                assertEquals(refOld, testOld);
            }
            else if (action < 80)
            {
                // 30% get
                final Integer testVal = testMap.get(key);
                final Integer refVal = refMap.get(key);
                assertEquals(refVal, testVal);
            }
            else
            {
                // 20% remove
                final Integer testRemoved = testMap.remove(key);
                final Integer refRemoved = refMap.remove(key);
                assertEquals(refRemoved, testRemoved);
            }

            assertEquals(refMap.size(), testMap.size());
        }

        // Final sanity verification
        for (final java.util.Map.Entry<Integer, Integer> entry : refMap.entrySet())
        {
            assertEquals(entry.getValue(), testMap.get(entry.getKey()));
        }
    }
}
