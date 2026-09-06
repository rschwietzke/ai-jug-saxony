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
package org.jugsaxony.demo9;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FastHashMapTest
{
    /**
     * Key helper with controllable hashCode for deliberate collision testing.
     */
    record FixedHashKey(int id, int hash)
    {
        @Override
        public int hashCode()
        {
            return hash;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof FixedHashKey that)) return false;
            return id == that.id;
        }

        @Override
        public String toString()
        {
            return "Key(" + id + ", h=" + hash + ")";
        }
    }

    @Nested
    @DisplayName("Constructor and Configuration Tests")
    class ConstructorTests
    {
        @Test
        void defaultConstructorInitializesValidMap()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            assertThat(map.size()).isZero();
            assertThat(map.capacity()).isEqualTo(16);
            assertThat(map.keys()).isEmpty();
            assertThat(map.values()).isEmpty();
        }

        @Test
        void customCapacityRoundsUpToPowerOfTwo()
        {
            assertThat(new FastHashMap<String, String>(0).capacity()).isEqualTo(4);
            assertThat(new FastHashMap<String, String>(3).capacity()).isEqualTo(4);
            assertThat(new FastHashMap<String, String>(5).capacity()).isEqualTo(8);
            assertThat(new FastHashMap<String, String>(16).capacity()).isEqualTo(16);
            assertThat(new FastHashMap<String, String>(17).capacity()).isEqualTo(32);
        }

        @Test
        void negativeCapacityThrowsIllegalArgumentException()
        {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> new FastHashMap<String, String>(-1));
        }

        @ParameterizedTest
        @ValueSource(floats = {-0.5f, 0.0f, 1.0f, 1.5f, Float.NaN})
        void invalidLoadFactorThrowsIllegalArgumentException(float invalidLf)
        {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> new FastHashMap<String, String>(16, invalidLf));
        }
    }

    @Nested
    @DisplayName("Basic Operations (CRUD)")
    class BasicOperationsTests
    {
        @Test
        void emptyMapBehavior()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            assertThat(map.size()).isZero();
            assertThat(map.get("missing")).isNull();
            assertThat(map.remove("missing")).isNull();
            assertThat(map.keys()).isEmpty();
            assertThat(map.values()).isEmpty();
        }

        @Test
        void putAndGetSingleEntry()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            final String old = map.put("key1", "val1");

            assertThat(old).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("key1")).isEqualTo("val1");
        }

        @Test
        void putOverwriteReturnsOldValueAndPreservesSize()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("k", "v1");

            final String prev = map.put("k", "v2");
            assertThat(prev).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("k")).isEqualTo("v2");
        }

        @Test
        void removeExistingEntry()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("k1", "v1");
            map.put("k2", "v2");

            final String removed = map.remove("k1");
            assertThat(removed).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("k1")).isNull();
            assertThat(map.get("k2")).isEqualTo("v2");
        }

        @Test
        void removeNonExistentEntryReturnsNull()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("k1", "v1");

            assertThat(map.remove("nonexistent")).isNull();
            assertThat(map.size()).isEqualTo(1);
        }

        @Test
        void clearEmptiesMapAndAllowsReuse()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");

            map.clear();
            assertThat(map.size()).isZero();
            assertThat(map.get("k1")).isNull();
            assertThat(map.keys()).isEmpty();
            assertThat(map.values()).isEmpty();

            // Reuse after clear
            map.put("k4", "v4");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("k4")).isEqualTo("v4");
        }
    }

    @Nested
    @DisplayName("Null Key and Value Handling")
    class NullHandlingTests
    {
        @Test
        void putNullKeyThrowsNullPointerException()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            assertThatNullPointerException()
                .isThrownBy(() -> map.put(null, "val"))
                .withMessageContaining("Key must not be null");
        }

        @Test
        void getNullKeyThrowsNullPointerException()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            assertThatNullPointerException()
                .isThrownBy(() -> map.get(null))
                .withMessageContaining("Key must not be null");
        }

        @Test
        void removeNullKeyThrowsNullPointerException()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();
            assertThatNullPointerException()
                .isThrownBy(() -> map.remove(null))
                .withMessageContaining("Key must not be null");
        }

        @Test
        void nullValuesAreSupported()
        {
            final FastHashMap<String, String> map = new FastHashMap<>();

            // Put null value
            final String prev1 = map.put("nullKey", null);
            assertThat(prev1).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("nullKey")).isNull();
            assertThat(map.keys()).containsExactly("nullKey");
            assertThat(map.values()).containsOnlyNulls().hasSize(1);

            // Replace null value with non-null
            final String prev2 = map.put("nullKey", "nonNull");
            assertThat(prev2).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("nullKey")).isEqualTo("nonNull");

            // Replace non-null with null
            final String prev3 = map.put("nullKey", null);
            assertThat(prev3).isEqualTo("nonNull");
            assertThat(map.get("nullKey")).isNull();

            // Remove entry containing null value
            final String removed = map.remove("nullKey");
            assertThat(removed).isNull();
            assertThat(map.size()).isZero();
            assertThat(map.get("nullKey")).isNull();
        }
    }

    @Nested
    @DisplayName("Collision Probing and Backward-Shift Deletion")
    class CollisionAndShiftDeletionTests
    {
        @Test
        void multipleCollidingKeysAreAllStoredAndRetrieved()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>(8);

            final FixedHashKey k1 = new FixedHashKey(1, 42);
            final FixedHashKey k2 = new FixedHashKey(2, 42);
            final FixedHashKey k3 = new FixedHashKey(3, 42);
            final FixedHashKey k4 = new FixedHashKey(4, 42);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");
            map.put(k4, "v4");

            assertThat(map.size()).isEqualTo(4);
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");
            assertThat(map.get(k4)).isEqualTo("v4");
        }

        @Test
        void removeHeadOfCollisionClusterShiftsRemainingElements()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>(16);

            final FixedHashKey k1 = new FixedHashKey(1, 10);
            final FixedHashKey k2 = new FixedHashKey(2, 10);
            final FixedHashKey k3 = new FixedHashKey(3, 10);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Remove head of cluster
            assertThat(map.remove(k1)).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(2);

            // Verify following elements are still accessible
            assertThat(map.get(k1)).isNull();
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");
        }

        @Test
        void removeMiddleOfCollisionClusterShiftsCorrectly()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>(16);

            final FixedHashKey k1 = new FixedHashKey(1, 10);
            final FixedHashKey k2 = new FixedHashKey(2, 10);
            final FixedHashKey k3 = new FixedHashKey(3, 10);
            final FixedHashKey k4 = new FixedHashKey(4, 10);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");
            map.put(k4, "v4");

            // Remove middle element k2
            assertThat(map.remove(k2)).isEqualTo("v2");
            assertThat(map.size()).isEqualTo(3);

            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isNull();
            assertThat(map.get(k3)).isEqualTo("v3");
            assertThat(map.get(k4)).isEqualTo("v4");

            // Remove k3 next
            assertThat(map.remove(k3)).isEqualTo("v3");
            assertThat(map.size()).isEqualTo(2);

            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k4)).isEqualTo("v4");
        }

        @Test
        void removeTailOfCollisionCluster()
        {
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>(16);

            final FixedHashKey k1 = new FixedHashKey(1, 10);
            final FixedHashKey k2 = new FixedHashKey(2, 10);
            final FixedHashKey k3 = new FixedHashKey(3, 10);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            assertThat(map.remove(k3)).isEqualTo("v3");
            assertThat(map.size()).isEqualTo(2);

            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isNull();
        }

        @Test
        void collisionInterleavedWithDifferentNaturalHomes()
        {
            // Initial capacity 16. Mask is 15.
            // mixHash(h) ^ (h >>> 16) for h=0 is 0.
            // For h=1 is 1.
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>(16);

            final FixedHashKey h0_a = new FixedHashKey(1, 0);
            final FixedHashKey h0_b = new FixedHashKey(2, 0);
            final FixedHashKey h1_a = new FixedHashKey(3, 1); // Natural home at slot 1
            final FixedHashKey h0_c = new FixedHashKey(4, 0);

            map.put(h0_a, "A"); // Slot 0
            map.put(h0_b, "B"); // Probes to slot 1
            map.put(h1_a, "C"); // Natural home 1, probes to slot 2
            map.put(h0_c, "D"); // Natural home 0, probes to slot 3

            // Now remove h0_a at slot 0
            assertThat(map.remove(h0_a)).isEqualTo("A");

            // h0_b should shift to slot 0.
            // h1_a has home 1, so it should not shift to 0, but stays at 1 or moves to 1 when h0_b moved!
            assertThat(map.get(h0_a)).isNull();
            assertThat(map.get(h0_b)).isEqualTo("B");
            assertThat(map.get(h1_a)).isEqualTo("C");
            assertThat(map.get(h0_c)).isEqualTo("D");
        }

        @Test
        void cyclicBoundaryWraparoundCollisionAndDeletion()
        {
            // Capacity 8: mask is 7.
            // mixHash for h=7: 7 ^ (7 >>> 16) = 7.
            // So hash 7 maps to index 7.
            final FastHashMap<FixedHashKey, String> map = new FastHashMap<>(8);

            final FixedHashKey k1 = new FixedHashKey(1, 7);
            final FixedHashKey k2 = new FixedHashKey(2, 7);
            final FixedHashKey k3 = new FixedHashKey(3, 7);

            map.put(k1, "v1"); // Index 7
            map.put(k2, "v2"); // Wraps to Index 0
            map.put(k3, "v3"); // Wraps to Index 1

            assertThat(map.size()).isEqualTo(3);
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");

            // Remove k1 at index 7 (across boundary)
            assertThat(map.remove(k1)).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(2);

            assertThat(map.get(k1)).isNull();
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");
        }
    }

    @Nested
    @DisplayName("Dynamic Resizing (Growth)")
    class DynamicResizingTests
    {
        @Test
        void mapExpandsWhenExceedingThreshold()
        {
            final FastHashMap<Integer, String> map = new FastHashMap<>(8, 0.50f);
            assertThat(map.capacity()).isEqualTo(8);

            // Threshold is 8 * 0.5 = 4; map can hold 4 elements before expanding
            map.put(1, "1");
            map.put(2, "2");
            map.put(3, "3");
            map.put(4, "4");
            assertThat(map.capacity()).isEqualTo(8);

            map.put(5, "5"); // 5th element exceeds threshold, triggering rehash
            assertThat(map.capacity()).isGreaterThanOrEqualTo(16);

            for (int i = 1; i <= 5; i++)
            {
                assertThat(map.get(i)).isEqualTo(String.valueOf(i));
            }
        }

        @Test
        void largeScaleInsertionAndLookup()
        {
            final FastHashMap<Integer, Integer> map = new FastHashMap<>();
            final int count = 20_000;

            for (int i = 0; i < count; i++)
            {
                map.put(i, i * 10);
            }

            assertThat(map.size()).isEqualTo(count);

            for (int i = 0; i < count; i++)
            {
                assertThat(map.get(i)).isEqualTo(i * 10);
            }

            // Remove half
            for (int i = 0; i < count; i += 2)
            {
                assertThat(map.remove(i)).isEqualTo(i * 10);
            }

            assertThat(map.size()).isEqualTo(count / 2);

            // Verify remaining
            for (int i = 0; i < count; i++)
            {
                if (i % 2 == 0)
                {
                    assertThat(map.get(i)).isNull();
                }
                else
                {
                    assertThat(map.get(i)).isEqualTo(i * 10);
                }
            }
        }
    }

    @Nested
    @DisplayName("Collection Views (keys() and values())")
    class CollectionViewsTests
    {
        @Test
        void keysAndValuesSnapshots()
        {
            final FastHashMap<String, Integer> map = new FastHashMap<>();
            map.put("a", 100);
            map.put("b", 200);
            map.put("c", null);

            final List<String> keys = map.keys();
            final List<Integer> values = map.values();

            assertThat(keys).containsExactlyInAnyOrder("a", "b", "c");
            assertThat(values).containsExactlyInAnyOrder(100, 200, null);
            assertThat(keys).hasSize(3);
            assertThat(values).hasSize(3);

            // Modifying returned list does not affect internal map state
            keys.clear();
            values.clear();
            assertThat(map.size()).isEqualTo(3);
            assertThat(map.get("a")).isEqualTo(100);
        }

        @Test
        void correspondingKeyAndValueOrder()
        {
            final FastHashMap<Integer, String> map = new FastHashMap<>();
            for (int i = 1; i <= 50; i++)
            {
                map.put(i, "val-" + i);
            }

            final List<Integer> keys = map.keys();
            final List<String> values = map.values();

            assertThat(keys).hasSize(50);
            assertThat(values).hasSize(50);

            for (int i = 0; i < 50; i++)
            {
                final Integer key = keys.get(i);
                final String value = values.get(i);
                assertThat(map.get(key)).isEqualTo(value);
                assertThat(value).isEqualTo("val-" + key);
            }
        }
    }

    @Nested
    @DisplayName("Differential Stress Testing against java.util.HashMap")
    class DifferentialStressTests
    {
        @Test
        void randomizedOperationsEquivalence()
        {
            final FastHashMap<String, String> fastMap = new FastHashMap<>();
            final Map<String, String> standardMap = new HashMap<>();

            final Random random = new Random(42);
            final int iterations = 50_000;
            final int keyUniverseSize = 500;

            for (int i = 0; i < iterations; i++)
            {
                final int op = random.nextInt(10);
                final String key = "key_" + random.nextInt(keyUniverseSize);
                final String value = random.nextBoolean() ? "val_" + random.nextInt(1000) : null;

                if (op < 5)
                {
                    // Put (50% probability)
                    final String fastPrev = fastMap.put(key, value);
                    final String stdPrev = standardMap.put(key, value);
                    assertThat(fastPrev).as("put mismatch for key %s at step %d", key, i).isEqualTo(stdPrev);
                }
                else if (op < 8)
                {
                    // Get (30% probability)
                    final String fastVal = fastMap.get(key);
                    final String stdVal = standardMap.get(key);
                    assertThat(fastVal).as("get mismatch for key %s at step %d", key, i).isEqualTo(stdVal);
                }
                else if (op == 8)
                {
                    // Remove (10% probability)
                    final String fastRem = fastMap.remove(key);
                    final String stdRem = standardMap.remove(key);
                    assertThat(fastRem).as("remove mismatch for key %s at step %d", key, i).isEqualTo(stdRem);
                }
                else
                {
                    // Occasional clear (rare)
                    if (random.nextInt(200) == 0)
                    {
                        fastMap.clear();
                        standardMap.clear();
                    }
                }

                assertThat(fastMap.size()).as("size mismatch at step %d", i).isEqualTo(standardMap.size());
            }

            // Final state verification
            assertThat(fastMap.size()).isEqualTo(standardMap.size());
            for (Map.Entry<String, String> entry : standardMap.entrySet())
            {
                assertThat(fastMap.get(entry.getKey())).isEqualTo(entry.getValue());
            }
        }
    }
}
