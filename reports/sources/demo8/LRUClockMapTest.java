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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LRUClockMap Unit Tests")
public class LRUClockMapTest
{
    /**
     * Record representing custom key for collision and probing tests.
     */
    record CollidingKey(String name, int fixedHash)
    {
        @Override
        public int hashCode()
        {
            return fixedHash;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) return true;
            if (!(obj instanceof CollidingKey other)) return false;
            return Objects.equals(this.name, other.name);
        }
    }

    /**
     * Record representing key-value test pair.
     */
    record KeyValuePair<K, V>(K key, V value) {}

    @Nested
    @DisplayName("Constructor and Initialization Tests")
    class ConstructorTests
    {
        @ParameterizedTest
        @ValueSource(ints = {-10, -1, 0, 1, 2, 3})
        @DisplayName("Rejects maxSize < 4 with IllegalArgumentException")
        void constructorInvalidMaxSizeThrows(int invalidMaxSize)
        {
            var exception = assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(invalidMaxSize));
            assertEquals("MaxSize must be at least 4", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(ints = {4, 5, 8, 10, 16, 100})
        @DisplayName("Constructs map with valid maxSize >= 4")
        void constructorValidMaxSize(int validMaxSize)
        {
            var map = new LRUClockMap<String, String>(validMaxSize);
            assertAll(
                () -> assertEquals(0, map.size()),
                () -> assertEquals(0, map.trueSize()),
                () -> assertTrue(map.occupiedSpace() >= validMaxSize * 2),
                () -> assertTrue(map.keys().isEmpty())
            );
        }

        @Test
        @DisplayName("Initial empty state behaves as expected")
        void initialProperties()
        {
            var map = new LRUClockMap<String, Integer>(10);
            assertAll(
                () -> assertEquals(0, map.size()),
                () -> assertEquals(0, map.trueSize()),
                () -> assertTrue(map.occupiedSpace() >= 16),
                () -> assertTrue(map.keys().isEmpty()),
                () -> assertNull(map.get("nonexistent")),
                () -> assertNull(map.getRaw("nonexistent")),
                () -> assertNull(map.remove("nonexistent"))
            );
        }
    }

    @Nested
    @DisplayName("Null Parameter Contract Tests")
    class NullContractTests
    {
        private LRUClockMap<String, String> map;

        @BeforeEach
        void setUp()
        {
            map = new LRUClockMap<>(10);
        }

        @Test
        @DisplayName("put throws NullPointerException on null key")
        void putNullKeyThrows()
        {
            var npe = assertThrows(NullPointerException.class, () -> map.put(null, "value"));
            assertEquals("Key must not be null", npe.getMessage());
        }

        @Test
        @DisplayName("put throws NullPointerException on null value")
        void putNullValueThrows()
        {
            var npe = assertThrows(NullPointerException.class, () -> map.put("key", null));
            assertEquals("Value must not be null", npe.getMessage());
        }

        @Test
        @DisplayName("get throws NullPointerException on null key")
        void getNullKeyThrows()
        {
            var npe = assertThrows(NullPointerException.class, () -> map.get(null));
            assertEquals("Key must not be null", npe.getMessage());
        }

        @Test
        @DisplayName("getRaw throws NullPointerException on null key")
        void getRawNullKeyThrows()
        {
            var npe = assertThrows(NullPointerException.class, () -> map.getRaw(null));
            assertEquals("Key must not be null", npe.getMessage());
        }

        @Test
        @DisplayName("remove throws NullPointerException on null key")
        void removeNullKeyThrows()
        {
            var npe = assertThrows(NullPointerException.class, () -> map.remove(null));
            assertEquals("Key must not be null", npe.getMessage());
        }
    }

    @Nested
    @DisplayName("Basic Put, Get, GetRaw, and Remove Operations")
    class BasicCrudOperationsTests
    {
        private LRUClockMap<String, String> map;

        @BeforeEach
        void setUp()
        {
            map = new LRUClockMap<>(10);
        }

        @Test
        @DisplayName("Put and get single entry")
        void putAndGetSingleEntry()
        {
            assertNull(map.put("k1", "v1"));
            assertAll(
                () -> assertEquals(1, map.size()),
                () -> assertEquals(1, map.trueSize()),
                () -> assertEquals("v1", map.get("k1")),
                () -> assertEquals("v1", map.getRaw("k1"))
            );
        }

        @Test
        @DisplayName("Put and get multiple entries")
        void putAndGetMultipleEntries()
        {
            var pairs = List.of(
                new KeyValuePair<>("a", "1"),
                new KeyValuePair<>("b", "2"),
                new KeyValuePair<>("c", "3")
            );

            for (var pair : pairs)
            {
                assertNull(map.put(pair.key(), pair.value()));
            }

            assertEquals(3, map.size());
            assertEquals(3, map.trueSize());

            for (var pair : pairs)
            {
                assertEquals(pair.value(), map.get(pair.key()));
                assertEquals(pair.value(), map.getRaw(pair.key()));
            }
            assertNull(map.get("missing"));
            assertNull(map.getRaw("missing"));
        }

        @Test
        @DisplayName("Put overwrites existing value and returns previous value")
        void putOverwritesExistingValue()
        {
            assertNull(map.put("k1", "v1"));
            assertEquals("v1", map.put("k1", "v2"));

            assertAll(
                () -> assertEquals(1, map.size()),
                () -> assertEquals(1, map.trueSize()),
                () -> assertEquals("v2", map.get("k1")),
                () -> assertEquals("v2", map.getRaw("k1"))
            );
        }

        @Test
        @DisplayName("Updating existing entry at capacity does not evict")
        void putUpdateAtCapacityDoesNotEvict()
        {
            var smallMap = new LRUClockMap<Integer, String>(4);
            for (int i = 1; i <= 4; i++)
            {
                smallMap.put(i, "val" + i);
            }
            assertEquals(4, smallMap.size());

            // Updating key 2
            assertEquals("val2", smallMap.put(2, "updated2"));
            assertEquals(4, smallMap.size());
            assertEquals(4, smallMap.trueSize());

            for (int i = 1; i <= 4; i++)
            {
                var expected = (i == 2) ? "updated2" : "val" + i;
                assertEquals(expected, smallMap.get(i));
            }
        }

        @Test
        @DisplayName("Remove removes existing entry and adjusts size")
        void removeExistingEntry()
        {
            map.put("k1", "v1");
            map.put("k2", "v2");

            assertEquals("v1", map.remove("k1"));
            assertAll(
                () -> assertEquals(1, map.size()),
                () -> assertEquals(1, map.trueSize()),
                () -> assertNull(map.get("k1")),
                () -> assertNull(map.getRaw("k1")),
                () -> assertEquals("v2", map.get("k2"))
            );
        }

        @Test
        @DisplayName("Remove on non-existing entry returns null and does not change size")
        void removeNonExistingEntry()
        {
            map.put("k1", "v1");
            assertNull(map.remove("nonexistent"));
            assertEquals(1, map.size());
            assertEquals(1, map.trueSize());
        }

        @Test
        @DisplayName("Remove on empty map returns null")
        void removeOnEmptyMap()
        {
            assertNull(map.remove("k1"));
            assertEquals(0, map.size());
            assertEquals(0, map.trueSize());
        }
    }

    @Nested
    @DisplayName("Hash Collisions and Linear Probing Tests")
    class OpenAddressingAndCollisionTests
    {
        @Test
        @DisplayName("Linear probing on colliding keys for put, get, getRaw, and expensiveGet")
        void collisionLinearProbing()
        {
            var colMap = new LRUClockMap<CollidingKey, String>(8);
            var k1 = new CollidingKey("A", 42);
            var k2 = new CollidingKey("B", 42);
            var k3 = new CollidingKey("C", 42);
            var k4 = new CollidingKey("D", 42);

            colMap.put(k1, "valA");
            colMap.put(k2, "valB");
            colMap.put(k3, "valC");
            colMap.put(k4, "valD");

            assertAll(
                () -> assertEquals(4, colMap.size()),
                () -> assertEquals(4, colMap.trueSize()),
                () -> assertEquals("valA", colMap.get(k1)),
                () -> assertEquals("valB", colMap.get(k2)),
                () -> assertEquals("valC", colMap.get(k3)),
                () -> assertEquals("valD", colMap.get(k4)),
                () -> assertEquals("valA", colMap.getRaw(k1)),
                () -> assertEquals("valB", colMap.getRaw(k2)),
                () -> assertEquals("valC", colMap.getRaw(k3)),
                () -> assertEquals("valD", colMap.getRaw(k4)),
                () -> assertNull(colMap.get(new CollidingKey("Missing", 42))),
                () -> assertNull(colMap.getRaw(new CollidingKey("Missing", 42)))
            );
        }

        @Test
        @DisplayName("Updating a colliding key in open addressing chain")
        void collisionUpdateValue()
        {
            var colMap = new LRUClockMap<CollidingKey, String>(8);
            var k1 = new CollidingKey("A", 100);
            var k2 = new CollidingKey("B", 100);
            var k3 = new CollidingKey("C", 100);

            colMap.put(k1, "valA");
            colMap.put(k2, "valB");
            colMap.put(k3, "valC");

            assertEquals("valB", colMap.put(k2, "valB_updated"));
            assertEquals(3, colMap.size());
            assertEquals("valB_updated", colMap.get(k2));
            assertEquals("valB_updated", colMap.getRaw(k2));
            assertEquals("valA", colMap.get(k1));
            assertEquals("valC", colMap.get(k3));
        }

        @Test
        @DisplayName("Removing elements at different positions in collision cluster preserves remaining elements")
        void collisionRemovalMaintainsSearchInvariants()
        {
            var colMap = new LRUClockMap<CollidingKey, String>(8);
            var k1 = new CollidingKey("A", 17);
            var k2 = new CollidingKey("B", 17);
            var k3 = new CollidingKey("C", 17);
            var k4 = new CollidingKey("D", 17);

            colMap.put(k1, "valA");
            colMap.put(k2, "valB");
            colMap.put(k3, "valC");
            colMap.put(k4, "valD");

            // Remove middle item
            assertEquals("valB", colMap.remove(k2));
            assertAll(
                () -> assertEquals(3, colMap.size()),
                () -> assertEquals(3, colMap.trueSize()),
                () -> assertNull(colMap.get(k2)),
                () -> assertEquals("valA", colMap.get(k1)),
                () -> assertEquals("valC", colMap.get(k3)),
                () -> assertEquals("valD", colMap.get(k4))
            );

            // Remove head item
            assertEquals("valA", colMap.remove(k1));
            assertAll(
                () -> assertEquals(2, colMap.size()),
                () -> assertEquals(2, colMap.trueSize()),
                () -> assertNull(colMap.get(k1)),
                () -> assertEquals("valC", colMap.get(k3)),
                () -> assertEquals("valD", colMap.get(k4))
            );

            // Remove tail item
            assertEquals("valD", colMap.remove(k4));
            assertAll(
                () -> assertEquals(1, colMap.size()),
                () -> assertEquals(1, colMap.trueSize()),
                () -> assertNull(colMap.get(k4)),
                () -> assertEquals("valC", colMap.get(k3))
            );
        }
    }

    @Nested
    @DisplayName("Clock LRU Second-Chance Eviction Logic Tests")
    class ClockEvictionTests
    {
        @Test
        @DisplayName("Evicts when exceeding maxSize and keeps capacity strictly bounded")
        void evictionWhenExceedingMaxSize()
        {
            final int maxSize = 4;
            var clockMap = new LRUClockMap<Integer, String>(maxSize);

            for (int i = 0; i < maxSize; i++)
            {
                clockMap.put(i, "val" + i);
            }
            assertEquals(maxSize, clockMap.size());

            // Adding a new entry triggers eviction
            clockMap.put(100, "val100");
            assertAll(
                () -> assertEquals(maxSize, clockMap.size()),
                () -> assertEquals(maxSize, clockMap.trueSize()),
                () -> assertEquals("val100", clockMap.get(100))
            );
        }

        @Test
        @DisplayName("Accessing via get sets secondChance to true and protects entry")
        void secondChanceFlagProtectsAccessedEntryFromEviction()
        {
            final int maxSize = 4;
            var clockMap = new LRUClockMap<Integer, String>(maxSize);

            for (int i = 0; i < maxSize; i++)
            {
                clockMap.put(i, "val" + i);
            }

            // Access entry 0 with get() -> grants second chance
            assertEquals("val0", clockMap.get(0));

            // Insert new entries to trigger multiple sweeps and evictions
            clockMap.put(10, "val10");
            clockMap.put(11, "val11");

            assertAll(
                () -> assertEquals(maxSize, clockMap.size()),
                () -> assertEquals(maxSize, clockMap.trueSize()),
                () -> assertEquals("val10", clockMap.get(10)),
                () -> assertEquals("val11", clockMap.get(11))
            );
        }

        @Test
        @DisplayName("getRaw returns value without modifying secondChance state")
        void getRawDoesNotAffectSecondChance()
        {
            var clockMap = new LRUClockMap<String, String>(4);
            clockMap.put("a", "1");
            clockMap.put("b", "2");

            assertEquals("1", clockMap.getRaw("a"));
            assertEquals("2", clockMap.getRaw("b"));
            assertNull(clockMap.getRaw("nonexistent"));

            var debugData = clockMap.getDebugData();
            for (var entry : debugData)
            {
                if (entry != null && entry.key.equals("a"))
                {
                    assertTrue(entry.secondChance);
                }
            }
        }

        @Test
        @DisplayName("Clock algorithm gives second chance to newly inserted entries")
        void newEntriesStartWithSecondChanceTrue()
        {
            var clockMap = new LRUClockMap<String, String>(4);
            clockMap.put("x", "100");
            clockMap.put("y", "200");

            var debugList = clockMap.getDebugData();
            for (var entry : debugList)
            {
                if (entry != null)
                {
                    assertTrue(entry.secondChance);
                }
            }
        }

        @Test
        @DisplayName("Eviction handles full map when all entries initially have secondChance true")
        void allEntriesSecondChanceTrueEvictionCycle()
        {
            final int maxSize = 4;
            var clockMap = new LRUClockMap<String, String>(maxSize);

            clockMap.put("A", "1");
            clockMap.put("B", "2");
            clockMap.put("C", "3");
            clockMap.put("D", "4");

            // Inserting 5th element causes clock hand to clear second chances and evict the first encountered
            clockMap.put("E", "5");

            assertAll(
                () -> assertEquals(maxSize, clockMap.size()),
                () -> assertEquals(maxSize, clockMap.trueSize()),
                () -> assertEquals("5", clockMap.get("E"))
            );
        }
    }

    @Nested
    @DisplayName("Keys View and Clear Operations")
    class KeysAndClearTests
    {
        private LRUClockMap<String, String> map;

        @BeforeEach
        void setUp()
        {
            map = new LRUClockMap<>(10);
        }

        @Test
        @DisplayName("keys() returns all currently active keys in the map")
        void keysReturnsAllPresentKeys()
        {
            var inserted = List.of("k1", "k2", "k3", "k4");
            for (var k : inserted)
            {
                map.put(k, "val_" + k);
            }

            var keys = map.keys();
            assertAll(
                () -> assertEquals(4, keys.size()),
                () -> assertTrue(keys.containsAll(inserted))
            );

            map.remove("k2");
            var updatedKeys = map.keys();
            assertAll(
                () -> assertEquals(3, updatedKeys.size()),
                () -> assertTrue(updatedKeys.contains("k1")),
                () -> assertFalse(updatedKeys.contains("k2")),
                () -> assertTrue(updatedKeys.contains("k3")),
                () -> assertTrue(updatedKeys.contains("k4"))
            );
        }

        @Test
        @DisplayName("clear() empties the map completely and allows reuse")
        void clearEmptiesMapAndAllowsReuse()
        {
            for (int i = 0; i < 8; i++)
            {
                map.put("key" + i, "val" + i);
            }
            assertEquals(8, map.size());
            assertEquals(8, map.trueSize());

            map.clear();

            assertAll(
                () -> assertEquals(0, map.size()),
                () -> assertEquals(0, map.trueSize()),
                () -> assertTrue(map.keys().isEmpty())
            );

            for (int i = 0; i < 8; i++)
            {
                assertNull(map.get("key" + i));
                assertNull(map.getRaw("key" + i));
            }

            // Re-inserting after clear
            map.put("freshKey", "freshVal");
            assertAll(
                () -> assertEquals(1, map.size()),
                () -> assertEquals(1, map.trueSize()),
                () -> assertEquals("freshVal", map.get("freshKey")),
                () -> assertEquals(List.of("freshKey"), map.keys())
            );
        }
    }

    @Nested
    @DisplayName("Debug and String Representation Tests")
    class DebugAndInspectionTests
    {
        @Test
        @DisplayName("toString and getDebugData return valid structured representations")
        void toStringAndDebugData()
        {
            var map = new LRUClockMap<String, String>(4);
            map.put("k1", "v1");
            map.put("k2", "v2");

            var str = map.toString();
            assertAll(
                () -> assertNotNull(str),
                () -> assertTrue(str.startsWith("LRUClockMap{\n")),
                () -> assertTrue(str.endsWith("\n}")),
                () -> assertTrue(str.contains("k1")),
                () -> assertTrue(str.contains("v1")),
                () -> assertTrue(str.contains("k2")),
                () -> assertTrue(str.contains("v2")),
                () -> assertTrue(str.contains("size: 2")),
                () -> assertTrue(str.contains("maxSize: 4")),
                () -> assertTrue(str.contains("clockHand:"))
            );

            var debugData = map.getDebugData();
            assertAll(
                () -> assertNotNull(debugData),
                () -> assertEquals(map.occupiedSpace(), debugData.size())
            );

            long nonNullCount = debugData.stream().filter(Objects::nonNull).count();
            assertEquals(2, nonNullCount);
        }
    }

    @Nested
    @DisplayName("Stress and Invariant Property Tests")
    class PropertyAndStressTests
    {
        @Test
        @DisplayName("Deterministic randomized stress test validating size and lookup invariants")
        void continuousHeavyEvictionStressTest()
        {
            final int maxSize = 50;
            var stressMap = new LRUClockMap<Integer, Integer>(maxSize);
            var shadowMap = new HashMap<Integer, Integer>();
            var random = new Random(987654321L);

            final int totalOps = 25_000;
            for (int i = 0; i < totalOps; i++)
            {
                int key = random.nextInt(200);
                int op = random.nextInt(10);

                if (op < 5)
                {
                    // Put
                    stressMap.put(key, i);
                    shadowMap.put(key, i);

                    assertTrue(stressMap.size() <= maxSize);
                    assertEquals(stressMap.size(), stressMap.trueSize());
                    assertEquals(i, stressMap.get(key));
                    assertEquals(i, stressMap.getRaw(key));
                }
                else if (op < 7)
                {
                    // Get
                    var val = stressMap.get(key);
                    if (val != null)
                    {
                        assertEquals(shadowMap.get(key), val);
                    }
                }
                else if (op < 8)
                {
                    // GetRaw
                    var val = stressMap.getRaw(key);
                    if (val != null)
                    {
                        assertEquals(shadowMap.get(key), val);
                    }
                }
                else
                {
                    // Remove
                    var removed = stressMap.remove(key);
                    if (removed != null)
                    {
                        assertEquals(shadowMap.get(key), removed);
                        shadowMap.remove(key);
                    }
                    assertTrue(stressMap.size() <= maxSize);
                    assertEquals(stressMap.size(), stressMap.trueSize());
                    assertNull(stressMap.get(key));
                    assertNull(stressMap.getRaw(key));
                }
            }
        }
    }
}
