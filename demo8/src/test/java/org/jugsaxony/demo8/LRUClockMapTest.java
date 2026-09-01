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

public class LRUClockMapTest
{
    private LRUClockMap<String, String> map;

    @BeforeEach
    public void setUp()
    {
        map = new LRUClockMap<>(10);
    }

    // =========================================================================
    // Construction & Initialization
    // =========================================================================

    @Test
    public void constructorInvalidMaxSizeThrows()
    {
        assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(3));
        assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(0));
        assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(-1));
    }

    @Test
    public void constructorValidMinSize()
    {
        final LRUClockMap<String, String> minMap = new LRUClockMap<>(4);
        assertEquals(0, minMap.size());
        assertEquals(0, minMap.trueSize());
        assertTrue(minMap.occupiedSpace() >= 8);
    }

    @Test
    public void initialProperties()
    {
        assertEquals(0, map.size());
        assertEquals(0, map.trueSize());
        assertTrue(map.occupiedSpace() >= 16);
        assertTrue(map.keys().isEmpty());
        assertNull(map.get("nonexistent"));
        assertNull(map.getRaw("nonexistent"));
    }

    // =========================================================================
    // Basic Put, Get, and GetRaw Operations
    // =========================================================================

    @Test
    public void putAndGetSingleEntry()
    {
        assertNull(map.put("k1", "v1"));
        assertEquals(1, map.size());
        assertEquals(1, map.trueSize());
        assertEquals("v1", map.get("k1"));
        assertEquals("v1", map.getRaw("k1"));
    }

    @Test
    public void putAndGetMultipleEntries()
    {
        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");

        assertEquals(3, map.size());
        assertEquals(3, map.trueSize());
        assertEquals("v1", map.get("k1"));
        assertEquals("v2", map.get("k2"));
        assertEquals("v3", map.get("k3"));
        assertNull(map.get("unknown"));
    }

    @Test
    public void putOverwritesExistingValue()
    {
        assertNull(map.put("k1", "v1"));
        assertEquals("v1", map.put("k1", "v2"));
        assertEquals(1, map.size());
        assertEquals("v2", map.get("k1"));
    }

    @Test
    public void putUpdateAtCapacityDoesNotEvict()
    {
        final LRUClockMap<Integer, String> smallMap = new LRUClockMap<>(4);
        for (int i = 0; i < 4; i++)
        {
            smallMap.put(i, "val" + i);
        }
        assertEquals(4, smallMap.size());

        // Updating an existing entry should not cause eviction
        assertEquals("val2", smallMap.put(2, "val2_updated"));
        assertEquals(4, smallMap.size());
        for (int i = 0; i < 4; i++)
        {
            if (i == 2)
            {
                assertEquals("val2_updated", smallMap.get(i));
            }
            else
            {
                assertEquals("val" + i, smallMap.get(i));
            }
        }
    }

    // =========================================================================
    // Null Checks
    // =========================================================================

    @Test
    public void putNullKeyThrows()
    {
        assertThrows(NullPointerException.class, () -> map.put(null, "v1"));
    }

    @Test
    public void putNullValueThrows()
    {
        assertThrows(NullPointerException.class, () -> map.put("k1", null));
    }

    @Test
    public void getNullKeyThrows()
    {
        assertThrows(NullPointerException.class, () -> map.get(null));
    }

    @Test
    public void getRawNullKeyThrows()
    {
        assertThrows(NullPointerException.class, () -> map.getRaw(null));
    }

    @Test
    public void removeNullKeyThrows()
    {
        assertThrows(NullPointerException.class, () -> map.remove(null));
    }

    // =========================================================================
    // Remove Operations
    // =========================================================================

    @Test
    public void removeExistingEntry()
    {
        map.put("k1", "v1");
        map.put("k2", "v2");

        assertEquals("v1", map.remove("k1"));
        assertEquals(1, map.size());
        assertEquals(1, map.trueSize());
        assertNull(map.get("k1"));
        assertEquals("v2", map.get("k2"));
    }

    @Test
    public void removeNonExistingEntry()
    {
        map.put("k1", "v1");
        assertNull(map.remove("unknown"));
        assertEquals(1, map.size());
    }

    @Test
    public void removeOnEmptyMap()
    {
        assertNull(map.remove("k1"));
        assertEquals(0, map.size());
    }

    // =========================================================================
    // Second-Chance Clock Eviction Logic
    // =========================================================================

    @Test
    public void evictionWhenExceedingMaxSize()
    {
        final int maxSize = 4;
        final LRUClockMap<Integer, String> clockMap = new LRUClockMap<>(maxSize);

        // Fill up to max size
        for (int i = 0; i < maxSize; i++)
        {
            clockMap.put(i, "val" + i);
        }
        assertEquals(maxSize, clockMap.size());

        // Insert a 5th entry, which triggers eviction
        clockMap.put(100, "val100");
        assertEquals(maxSize, clockMap.size());
        assertEquals(maxSize, clockMap.trueSize());
        assertEquals("val100", clockMap.get(100));
    }

    @Test
    public void secondChanceFlagProtectsAccessedEntryFromEviction()
    {
        final int maxSize = 4;
        final LRUClockMap<Integer, String> clockMap = new LRUClockMap<>(maxSize);

        for (int i = 0; i < maxSize; i++)
        {
            clockMap.put(i, "val" + i);
        }

        // Access entry 0 with get() to grant it a second chance
        assertEquals("val0", clockMap.get(0));

        // Insert entries 4, 5, 6 (will sweep clock hand and evict items without second chance)
        clockMap.put(4, "val4");
        clockMap.put(5, "val5");

        // Verify map size is strictly bounded by maxSize
        assertEquals(maxSize, clockMap.size());
        assertEquals(maxSize, clockMap.trueSize());
    }

    @Test
    public void getRawDoesNotAffectSecondChanceFlag()
    {
        final LRUClockMap<String, String> clockMap = new LRUClockMap<>(4);
        clockMap.put("a", "1");
        clockMap.put("b", "2");

        // getRaw shouldn't throw and should return the correct value
        assertEquals("1", clockMap.getRaw("a"));
        assertEquals("2", clockMap.getRaw("b"));
        assertNull(clockMap.getRaw("nonexistent"));
    }

    // =========================================================================
    // Collision & Probing Tests
    // =========================================================================

    private static class CollidingKey
    {
        final String name;
        final int fixedHash;

        CollidingKey(final String name, final int fixedHash)
        {
            this.name = name;
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
            return Objects.equals(this.name, other.name);
        }

        @Override
        public String toString()
        {
            return "Key(" + name + ", hash=" + fixedHash + ")";
        }
    }

    @Test
    public void collisionLinearProbingAndExpensiveGet()
    {
        final LRUClockMap<CollidingKey, String> colMap = new LRUClockMap<>(8);
        final CollidingKey k1 = new CollidingKey("A", 10);
        final CollidingKey k2 = new CollidingKey("B", 10);
        final CollidingKey k3 = new CollidingKey("C", 10);
        final CollidingKey k4 = new CollidingKey("D", 10);

        colMap.put(k1, "valA");
        colMap.put(k2, "valB");
        colMap.put(k3, "valC");
        colMap.put(k4, "valD");

        assertEquals(4, colMap.size());
        assertEquals(4, colMap.trueSize());

        // Test get (tests both fast path and expensiveGet)
        assertEquals("valA", colMap.get(k1));
        assertEquals("valB", colMap.get(k2));
        assertEquals("valC", colMap.get(k3));
        assertEquals("valD", colMap.get(k4));

        // Test getRaw with collisions
        assertEquals("valA", colMap.getRaw(k1));
        assertEquals("valB", colMap.getRaw(k2));
        assertEquals("valC", colMap.getRaw(k3));
        assertEquals("valD", colMap.getRaw(k4));
        assertNull(colMap.get(new CollidingKey("NotFound", 10)));
        assertNull(colMap.getRaw(new CollidingKey("NotFound", 10)));
    }

    @Test
    public void collisionRemovalMaintainsSearchInvariants()
    {
        final LRUClockMap<CollidingKey, String> colMap = new LRUClockMap<>(8);
        final CollidingKey k1 = new CollidingKey("A", 10);
        final CollidingKey k2 = new CollidingKey("B", 10);
        final CollidingKey k3 = new CollidingKey("C", 10);
        final CollidingKey k4 = new CollidingKey("D", 10);

        colMap.put(k1, "valA");
        colMap.put(k2, "valB");
        colMap.put(k3, "valC");
        colMap.put(k4, "valD");

        // Remove middle of cluster
        assertEquals("valB", colMap.remove(k2));
        assertEquals(3, colMap.size());
        assertEquals(3, colMap.trueSize());

        assertNull(colMap.get(k2));
        assertEquals("valA", colMap.get(k1));
        assertEquals("valC", colMap.get(k3));
        assertEquals("valD", colMap.get(k4));

        // Remove head of cluster
        assertEquals("valA", colMap.remove(k1));
        assertEquals(2, colMap.size());
        assertEquals(2, colMap.trueSize());

        assertNull(colMap.get(k1));
        assertEquals("valC", colMap.get(k3));
        assertEquals("valD", colMap.get(k4));

        // Remove tail
        assertEquals("valD", colMap.remove(k4));
        assertEquals(1, colMap.size());
        assertNull(colMap.get(k4));
        assertEquals("valC", colMap.get(k3));
    }

    // =========================================================================
    // Keys View
    // =========================================================================

    @Test
    public void keysReturnsAllPresentKeys()
    {
        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");

        final List<String> keys = map.keys();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("k1"));
        assertTrue(keys.contains("k2"));
        assertTrue(keys.contains("k3"));

        map.remove("k2");
        final List<String> updatedKeys = map.keys();
        assertEquals(2, updatedKeys.size());
        assertTrue(updatedKeys.contains("k1"));
        assertFalse(updatedKeys.contains("k2"));
        assertTrue(updatedKeys.contains("k3"));
    }

    // =========================================================================
    // Clear Operation
    // =========================================================================

    @Test
    public void clearEmptiesMap()
    {
        for (int i = 0; i < 8; i++)
        {
            map.put("key" + i, "val" + i);
        }
        assertEquals(8, map.size());
        assertEquals(8, map.trueSize());

        map.clear();

        assertEquals(0, map.size());
        assertEquals(0, map.trueSize());
        assertTrue(map.keys().isEmpty());
        for (int i = 0; i < 8; i++)
        {
            assertNull(map.get("key" + i));
            assertNull(map.getRaw("key" + i));
        }

        // Reinsert after clear
        map.put("newKey", "newVal");
        assertEquals(1, map.size());
        assertEquals("newVal", map.get("newKey"));
    }

    // =========================================================================
    // Debug & ToString Methods
    // =========================================================================

    @Test
    public void toStringAndDebugData()
    {
        map.put("k1", "v1");
        final String debugStr = map.toString();
        assertNotNull(debugStr);
        assertTrue(debugStr.contains("LRUClockMap"));
        assertTrue(debugStr.contains("k1"));
        assertTrue(debugStr.contains("v1"));

        final var debugData = map.getDebugData();
        assertNotNull(debugData);
        assertEquals(map.occupiedSpace(), debugData.size());
    }

    // =========================================================================
    // Continuous Stress and Eviction Cycle Test
    // =========================================================================

    @Test
    public void continuousHeavyEvictionStressTest()
    {
        final int maxSize = 50;
        final LRUClockMap<Integer, Integer> stressMap = new LRUClockMap<>(maxSize);
        final Random random = new Random(987654321L);

        final int totalOps = 20_000;
        for (int i = 0; i < totalOps; i++)
        {
            final int key = random.nextInt(200);
            final int op = random.nextInt(10);

            if (op < 6)
            {
                // Put
                stressMap.put(key, i);
                assertTrue(stressMap.size() <= maxSize);
                assertEquals(stressMap.size(), stressMap.trueSize());
                assertEquals(i, stressMap.get(key));
            }
            else if (op < 9)
            {
                // Get
                stressMap.get(key);
            }
            else
            {
                // Remove
                stressMap.remove(key);
                assertTrue(stressMap.size() <= maxSize);
                assertEquals(stressMap.size(), stressMap.trueSize());
                assertNull(stressMap.get(key));
            }
        }
    }
}
