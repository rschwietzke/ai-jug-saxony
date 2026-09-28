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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class LRUClockMapTest
{
    /**
     * Key helper with controllable hashCode for deliberate collision and clustering testing.
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
        @ParameterizedTest
        @ValueSource(ints = {-100, -1, 0, 1, 2, 3})
        void maxSizeBelowFourThrowsIllegalArgumentException(int invalidSize)
        {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> new LRUClockMap<String, String>(invalidSize))
                .withMessageContaining("MaxSize must be at least 4");
        }

        @Test
        void validMaxSizeInitializesCorrectCapacityAndState()
        {
            // capacity = nextPowerOfTwo(ceil(maxSize / 0.50f))
            // maxSize = 4  -> 4/0.5 = 8   -> cap 8
            // maxSize = 5  -> 5/0.5 = 10  -> cap 16
            // maxSize = 8  -> 8/0.5 = 16  -> cap 16
            // maxSize = 9  -> 9/0.5 = 18  -> cap 32
            // maxSize = 16 -> 16/0.5 = 32 -> cap 32
            // maxSize = 17 -> 17/0.5 = 34 -> cap 64
            assertThat(new LRUClockMap<String, String>(4).occupiedSpace()).isEqualTo(8);
            assertThat(new LRUClockMap<String, String>(5).occupiedSpace()).isEqualTo(16);
            assertThat(new LRUClockMap<String, String>(8).occupiedSpace()).isEqualTo(16);
            assertThat(new LRUClockMap<String, String>(9).occupiedSpace()).isEqualTo(32);
            assertThat(new LRUClockMap<String, String>(16).occupiedSpace()).isEqualTo(32);
            assertThat(new LRUClockMap<String, String>(17).occupiedSpace()).isEqualTo(64);

            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThat(map.size()).isZero();
            assertThat(map.trueSize()).isZero();
            assertThat(map.keys()).isEmpty();
        }

        @Test
        void excessivelyLargeMaxSizeThrowsIllegalArgumentException()
        {
            // 600_000_000 with load factor 0.5 results in capacity (1 << 31) > (1 << 30)
            final int tooLarge = 600_000_000;
            assertThatIllegalArgumentException()
                .isThrownBy(() -> new LRUClockMap<String, String>(tooLarge))
                .withMessageContaining("Too large");
        }

        @Test
        void arraySizeBoundaryExactLimit() throws Exception
        {
            final Method m = LRUClockMap.class.getDeclaredMethod("arraySize", int.class, float.class);
            m.setAccessible(true);

            // 1 << 29 with f = 0.5f results in s = 1 << 30 exactly, which is <= (1 << 30)
            int result = (int) m.invoke(null, 1 << 29, 0.5f);
            assertThat(result).isEqualTo(1 << 30);
        }

        @Test
        void nextPowerOfTwoDirectTestViaReflection() throws Exception
        {
            final Method m = LRUClockMap.class.getDeclaredMethod("nextPowerOfTwo", long.class);
            m.setAccessible(true);

            assertThat((long) m.invoke(null, 0L)).isEqualTo(1L);
            assertThat((long) m.invoke(null, 1L)).isEqualTo(1L);
            assertThat((long) m.invoke(null, 2L)).isEqualTo(2L);
            assertThat((long) m.invoke(null, 3L)).isEqualTo(4L);
            assertThat((long) m.invoke(null, 4L)).isEqualTo(4L);
            assertThat((long) m.invoke(null, 5L)).isEqualTo(8L);
            assertThat((long) m.invoke(null, 1023L)).isEqualTo(1024L);
            assertThat((long) m.invoke(null, 1024L)).isEqualTo(1024L);
        }
    }

    @Nested
    @DisplayName("Null Contract Tests")
    class NullContractTests
    {
        @Test
        void putNullKeyThrowsNullPointerException()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThatNullPointerException()
                .isThrownBy(() -> map.put(null, "value"))
                .withMessageContaining("Key must not be null");
        }

        @Test
        void putNullValueThrowsNullPointerException()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThatNullPointerException()
                .isThrownBy(() -> map.put("key", null))
                .withMessageContaining("Value must not be null");
        }

        @Test
        void getNullKeyThrowsNullPointerException()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThatNullPointerException()
                .isThrownBy(() -> map.get(null))
                .withMessageContaining("Key must not be null");
        }

        @Test
        void getRawNullKeyThrowsNullPointerException()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThatNullPointerException()
                .isThrownBy(() -> map.getRaw(null))
                .withMessageContaining("Key must not be null");
        }

        @Test
        void removeNullKeyThrowsNullPointerException()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThatNullPointerException()
                .isThrownBy(() -> map.remove(null))
                .withMessageContaining("Key must not be null");
        }
    }

    @Nested
    @DisplayName("Basic Operations (CRUD)")
    class BasicOperationsTests
    {
        @Test
        void emptyMapBehavior()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
            assertThat(map.size()).isZero();
            assertThat(map.trueSize()).isZero();
            assertThat(map.get("missing")).isNull();
            assertThat(map.getRaw("missing")).isNull();
            assertThat(map.remove("missing")).isNull();
            assertThat(map.keys()).isEmpty();
        }

        @Test
        void putAndGetSingleEntry()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            final String old = map.put("key1", "val1");

            assertThat(old).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.trueSize()).isEqualTo(1);
            assertThat(map.get("key1")).isEqualTo("val1");
            assertThat(map.getRaw("key1")).isEqualTo("val1");
            assertThat(map.keys()).containsExactly("key1");
        }

        @Test
        void putOverwriteReturnsOldValueAndPreservesSize()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            map.put("k", "v1");

            final String prev = map.put("k", "v2");
            assertThat(prev).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.trueSize()).isEqualTo(1);
            assertThat(map.get("k")).isEqualTo("v2");
            assertThat(map.getRaw("k")).isEqualTo("v2");
        }

        @Test
        void putMultipleDistinctEntriesWithinCapacity()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
            assertThat(map.put("a", 1)).isNull();
            assertThat(map.put("b", 2)).isNull();
            assertThat(map.put("c", 3)).isNull();

            assertThat(map.size()).isEqualTo(3);
            assertThat(map.trueSize()).isEqualTo(3);
            assertThat(map.get("a")).isEqualTo(1);
            assertThat(map.get("b")).isEqualTo(2);
            assertThat(map.get("c")).isEqualTo(3);
            assertThat(map.keys()).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        void removeExistingEntry()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            map.put("k1", "v1");
            map.put("k2", "v2");

            final String removed = map.remove("k1");
            assertThat(removed).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.trueSize()).isEqualTo(1);
            assertThat(map.get("k1")).isNull();
            assertThat(map.getRaw("k1")).isNull();
            assertThat(map.get("k2")).isEqualTo("v2");
            assertThat(map.keys()).containsExactly("k2");
        }

        @Test
        void removeNonExistentEntryReturnsNull()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            map.put("k1", "v1");

            assertThat(map.remove("nonexistent")).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.trueSize()).isEqualTo(1);
        }

        @Test
        void clearEmptiesMapAndAllowsReuse()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");

            map.clear();
            assertThat(map.size()).isZero();
            assertThat(map.trueSize()).isZero();
            assertThat(map.get("k1")).isNull();
            assertThat(map.getRaw("k1")).isNull();
            assertThat(map.keys()).isEmpty();

            // Reuse after clear
            map.put("k4", "v4");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.trueSize()).isEqualTo(1);
            assertThat(map.get("k4")).isEqualTo("v4");
            assertThat(map.keys()).containsExactly("k4");
        }

        @Test
        void keysReturnsDefensiveCopy()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            map.put("k1", "v1");
            final List<String> keys = map.keys();
            keys.clear();

            // Map keys should remain intact
            assertThat(map.keys()).containsExactly("k1");
            assertThat(map.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Hash Distribution and Collision Probing Tests")
    class CollisionAndProbingTests
    {
        @Test
        void mixHashCalculationDirectTestViaReflection() throws Exception
        {
            final Method m = LRUClockMap.class.getDeclaredMethod("mixHash", int.class);
            m.setAccessible(true);

            // h ^ (h >>> 16)
            assertThat((int) m.invoke(new LRUClockMap<String, String>(4), 0)).isEqualTo(0);
            assertThat((int) m.invoke(new LRUClockMap<String, String>(4), 1)).isEqualTo(1);
            assertThat((int) m.invoke(new LRUClockMap<String, String>(4), 0x10000)).isEqualTo(0x10001);
            assertThat((int) m.invoke(new LRUClockMap<String, String>(4), -1)).isEqualTo(-1 ^ (-1 >>> 16));
        }

        @Test
        void negativeHashCodeHandledWithoutException()
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey negKey1 = new FixedHashKey(1, -1);
            final FixedHashKey negKey2 = new FixedHashKey(2, Integer.MIN_VALUE);

            map.put(negKey1, "val1");
            map.put(negKey2, "val2");

            assertThat(map.get(negKey1)).isEqualTo("val1");
            assertThat(map.getRaw(negKey1)).isEqualTo("val1");
            assertThat(map.get(negKey2)).isEqualTo("val2");
            assertThat(map.getRaw(negKey2)).isEqualTo("val2");

            assertThat(map.remove(negKey1)).isEqualTo("val1");
            assertThat(map.get(negKey1)).isNull();
            assertThat(map.getRaw(negKey1)).isNull();
            assertThat(map.get(negKey2)).isEqualTo("val2");
        }

        @Test
        void collisionLinearProbingOnInsertAndGet()
        {
            // Capacity 8 for maxSize 4.
            // Keys with identical hash will probe linearly:
            // k1 -> slot 2, k2 -> slot 3, k3 -> slot 4
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 2);
            final FixedHashKey k2 = new FixedHashKey(2, 2);
            final FixedHashKey k3 = new FixedHashKey(3, 2);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            assertThat(map.size()).isEqualTo(3);
            assertThat(map.trueSize()).isEqualTo(3);

            // k1 is direct hit, k2 and k3 require expensiveGet
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");

            // getRaw also probes linearly
            assertThat(map.getRaw(k1)).isEqualTo("v1");
            assertThat(map.getRaw(k2)).isEqualTo("v2");
            assertThat(map.getRaw(k3)).isEqualTo("v3");

            // Miss on key with colliding hash stops at first empty slot
            final FixedHashKey nonExistent = new FixedHashKey(99, 2);
            assertThat(map.get(nonExistent)).isNull();
            assertThat(map.getRaw(nonExistent)).isNull();
        }

        @Test
        void collisionOverwriteKeyAlongProbeChain()
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 2);
            final FixedHashKey k2 = new FixedHashKey(2, 2);

            map.put(k1, "v1");
            map.put(k2, "v2");

            // Overwrite k2 which is probed
            final String old = map.put(k2, "v2-updated");
            assertThat(old).isEqualTo("v2");
            assertThat(map.size()).isEqualTo(2);
            assertThat(map.get(k2)).isEqualTo("v2-updated");
            assertThat(map.getRaw(k2)).isEqualTo("v2-updated");
        }

        @Test
        void wrapAroundAtArrayBoundaryOnInsertAndGet()
        {
            // Capacity 8: mask = 7.
            // Hash 7 lands on index 7.
            // Next collision wraps to 0, then 1.
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 7);
            final FixedHashKey k2 = new FixedHashKey(2, 7);
            final FixedHashKey k3 = new FixedHashKey(3, 7);

            map.put(k1, "v1"); // slot 7
            map.put(k2, "v2"); // slot 0 (wrapped)
            map.put(k3, "v3"); // slot 1 (wrapped)

            assertThat(map.size()).isEqualTo(3);
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");

            assertThat(map.getRaw(k1)).isEqualTo("v1");
            assertThat(map.getRaw(k2)).isEqualTo("v2");
            assertThat(map.getRaw(k3)).isEqualTo("v3");

            // Missing key with hash 7 wraps and stops at null
            final FixedHashKey missing = new FixedHashKey(99, 7);
            assertThat(map.get(missing)).isNull();
            assertThat(map.getRaw(missing)).isNull();
        }
    }

    @Nested
    @DisplayName("Removal and Backward-Shift Realign Tests")
    class RemovalAndRealignTests
    {
        @Test
        void removeHeadOfCollisionClusterRealignsSubsequentElements()
        {
            // k1, k2, k3 all hash to bucket 2
            // k1 at 2, k2 at 3, k3 at 4
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 2);
            final FixedHashKey k2 = new FixedHashKey(2, 2);
            final FixedHashKey k3 = new FixedHashKey(3, 2);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Remove head k1
            assertThat(map.remove(k1)).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(2);
            assertThat(map.trueSize()).isEqualTo(2);

            // k2 and k3 must still be reachable
            assertThat(map.get(k1)).isNull();
            assertThat(map.getRaw(k1)).isNull();
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.getRaw(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");
            assertThat(map.getRaw(k3)).isEqualTo("v3");
        }

        @Test
        void removeMiddleOfCollisionClusterRealignsSubsequentElements()
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 2);
            final FixedHashKey k2 = new FixedHashKey(2, 2);
            final FixedHashKey k3 = new FixedHashKey(3, 2);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Remove middle k2
            assertThat(map.remove(k2)).isEqualTo("v2");
            assertThat(map.size()).isEqualTo(2);
            assertThat(map.trueSize()).isEqualTo(2);

            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isNull();
            assertThat(map.get(k3)).isEqualTo("v3");
            assertThat(map.getRaw(k3)).isEqualTo("v3");
        }

        @Test
        void removeTailOfCollisionClusterLeavesPrecedingIntact()
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 2);
            final FixedHashKey k2 = new FixedHashKey(2, 2);
            final FixedHashKey k3 = new FixedHashKey(3, 2);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Remove tail k3
            assertThat(map.remove(k3)).isEqualTo("v3");
            assertThat(map.size()).isEqualTo(2);
            assertThat(map.trueSize()).isEqualTo(2);

            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isNull();
        }

        @Test
        void removeWithClusterWrappingAroundArrayBoundary()
        {
            // Capacity 8, mask 7.
            // k1 at 7, k2 at 0, k3 at 1
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 7);
            final FixedHashKey k2 = new FixedHashKey(2, 7);
            final FixedHashKey k3 = new FixedHashKey(3, 7);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Remove k1 at index 7 -> causes k2 and k3 across boundary to realign
            assertThat(map.remove(k1)).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(2);
            assertThat(map.trueSize()).isEqualTo(2);

            assertThat(map.get(k1)).isNull();
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");
            assertThat(map.getRaw(k2)).isEqualTo("v2");
            assertThat(map.getRaw(k3)).isEqualTo("v3");
        }

        @Test
        void removeNonExistentKeyInOccupiedClusterReturnsNull()
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 2);
            final FixedHashKey k2 = new FixedHashKey(2, 2);
            map.put(k1, "v1");
            map.put(k2, "v2");

            final FixedHashKey missing = new FixedHashKey(99, 2);
            assertThat(map.remove(missing)).isNull();
            assertThat(map.size()).isEqualTo(2);
            assertThat(map.trueSize()).isEqualTo(2);
        }

        @Test
        void realignPreservesSecondChanceFlag()
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 2);
            final FixedHashKey k2 = new FixedHashKey(2, 2);

            map.put(k1, "v1");
            map.put(k2, "v2");

            // Initially both secondChance == true
            // Manually inspect or toggle secondChance on k2 to false by running partial eviction,
            // or verify via getDebugData that secondChance remains true after realigning k2
            map.remove(k1); // k2 is realigned into k1's slot

            final var debugList = map.getDebugData();
            final var debugEntry = debugList.stream()
                .filter(d -> d != null && d.key.equals(k2))
                .findFirst()
                .orElseThrow();

            // secondChance must still be preserved as true
            assertThat(debugEntry.secondChance).isTrue();
        }
    }

    @Nested
    @DisplayName("LRU Clock Replacement Policy and Eviction Tests")
    class LRUClockEvictionTests
    {
        @Test
        void insertBeyondMaxSizeTriggersEviction()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            map.put("a", "1");
            map.put("b", "2");
            map.put("c", "3");
            map.put("d", "4");

            assertThat(map.size()).isEqualTo(4);

            // Inserting 5th element triggers eviction
            map.put("e", "5");

            assertThat(map.size()).isEqualTo(4);
            assertThat(map.trueSize()).isEqualTo(4);
            assertThat(map.get("e")).isEqualTo("5");

            // Exactly 4 keys present in total
            assertThat(map.keys()).hasSize(4);
        }

        @Test
        void updateExistingKeyAtMaxSizeDoesNotTriggerEviction()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            map.put("a", "1");
            map.put("b", "2");
            map.put("c", "3");
            map.put("d", "4");

            // Update "b"
            final String old = map.put("b", "updated-2");
            assertThat(old).isEqualTo("2");
            assertThat(map.size()).isEqualTo(4);
            assertThat(map.trueSize()).isEqualTo(4);

            // All 4 keys still exist
            assertThat(map.keys()).containsExactlyInAnyOrder("a", "b", "c", "d");
            assertThat(map.get("b")).isEqualTo("updated-2");
        }

        @Test
        void updateProbedKeyAtMaxSizeDoesNotTriggerEviction()
        {
            // Colliding keys
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 2);
            final FixedHashKey k2 = new FixedHashKey(2, 2);
            final FixedHashKey k3 = new FixedHashKey(3, 2);
            final FixedHashKey k4 = new FixedHashKey(4, 2);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");
            map.put(k4, "v4");

            // Update probed key k3 at maxSize
            final String old = map.put(k3, "v3-updated");
            assertThat(old).isEqualTo("v3");
            assertThat(map.size()).isEqualTo(4);
            assertThat(map.trueSize()).isEqualTo(4);
            assertThat(map.get(k3)).isEqualTo("v3-updated");
            assertThat(map.keys()).containsExactlyInAnyOrder(k1, k2, k3, k4);
        }

        @Test
        void getProtectsEntryFromEvictionViaSecondChance()
        {
            // MaxSize = 4, capacity = 8.
            // Insert 4 keys at deliberate slot positions: 0, 1, 2, 3
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k0 = new FixedHashKey(0, 0); // slot 0
            final FixedHashKey k1 = new FixedHashKey(1, 1); // slot 1
            final FixedHashKey k2 = new FixedHashKey(2, 2); // slot 2
            final FixedHashKey k3 = new FixedHashKey(3, 3); // slot 3

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // All 4 entries have secondChance = true initially.
            // Clock hand starts at 0.
            // Now insert k4 at slot 4 (capacity 8).
            // Eviction loop runs:
            // - sweeps 0, 1, 2, 3: sets all secondChance = false.
            // - skips empty slots 4, 5, 6, 7.
            // - wraps to 0: finds k0 with secondChance = false -> evicts k0!
            // - clockHand is now at 0.
            final FixedHashKey k4 = new FixedHashKey(4, 4);
            map.put(k4, "v4");

            assertThat(map.get(k0)).isNull(); // k0 was evicted
            assertThat(map.get(k4)).isEqualTo("v4");

            // Current state:
            // k1, k2, k3 have secondChance = false.
            // k4 has secondChance = true.
            // Now, access k1 using get() to give it a second chance!
            assertThat(map.get(k1)).isEqualTo("v1");

            // Now k1 has secondChance = true, whereas k2 and k3 have secondChance = false!
            // Insert k5 at slot 5.
            // Eviction will sweep from clockHand (starts around 0/1):
            // - visits k1: sees secondChance = true -> sets secondChance = false, advances.
            // - visits k2: sees secondChance = false -> evicts k2!
            final FixedHashKey k5 = new FixedHashKey(5, 5);
            map.put(k5, "v5");

            // k2 must be evicted, but k1 was spared!
            assertThat(map.get(k2)).isNull();
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k3)).isEqualTo("v3");
            assertThat(map.get(k4)).isEqualTo("v4");
            assertThat(map.get(k5)).isEqualTo("v5");
        }

        @Test
        void expensiveGetProtectsEntryFromEviction() throws Exception
        {
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            // k0 and k1 collide at slot 0: k0 at 0, k1 probed to 1
            final FixedHashKey k0 = new FixedHashKey(0, 0);
            final FixedHashKey k1 = new FixedHashKey(1, 0);

            map.put(k0, "v0");
            map.put(k1, "v1");

            // Access k1's wrapper via reflection while k0 is still in slot 0,
            // so that get(k1) is guaranteed to take expensiveGet!
            final Field dataField = LRUClockMap.class.getDeclaredField("data");
            dataField.setAccessible(true);
            final Object[] data = (Object[]) dataField.get(map);

            final Field secondChanceField = data[1].getClass().getDeclaredField("secondChance");
            secondChanceField.setAccessible(true);
            secondChanceField.setBoolean(data[1], false);

            assertThat(secondChanceField.getBoolean(data[1])).isFalse();

            // Calling get(k1) invokes expensiveGet, which finds !w.secondChance == true and sets secondChance = true
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(secondChanceField.getBoolean(data[1])).isTrue();

            // Calling get(k1) again when secondChance is already true
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(secondChanceField.getBoolean(data[1])).isTrue();

            // Also test direct hit in get() when secondChance is false
            secondChanceField.setBoolean(data[0], false);
            assertThat(map.get(k0)).isEqualTo("v0");
            assertThat(secondChanceField.getBoolean(data[0])).isTrue();
        }

        @Test
        void getRawDoesNotProtectEntryFromEviction()
        {
            // getRaw reads value without touching secondChance flag
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k0 = new FixedHashKey(0, 0);
            final FixedHashKey k1 = new FixedHashKey(1, 1);
            final FixedHashKey k2 = new FixedHashKey(2, 2);
            final FixedHashKey k3 = new FixedHashKey(3, 3);

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Evict k0 to set secondChance = false on k1, k2, k3
            final FixedHashKey k4 = new FixedHashKey(4, 4);
            map.put(k4, "v4");

            // Verify k1 has secondChance = false
            var debugK1 = map.getDebugData().stream()
                .filter(d -> d != null && d.key.equals(k1))
                .findFirst()
                .orElseThrow();
            assertThat(debugK1.secondChance).isFalse();

            // Access k1 using getRaw
            assertThat(map.getRaw(k1)).isEqualTo("v1");

            // k1 must STILL have secondChance = false!
            debugK1 = map.getDebugData().stream()
                .filter(d -> d != null && d.key.equals(k1))
                .findFirst()
                .orElseThrow();
            assertThat(debugK1.secondChance).isFalse();

            // Insert k5 -> k1 will be evicted because its secondChance was NOT set to true!
            final FixedHashKey k5 = new FixedHashKey(5, 5);
            map.put(k5, "v5");

            assertThat(map.get(k1)).isNull();
        }

        @Test
        void clockHandContinuityAcrossSuccessiveEvictions()
        {
            final LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
            for (int i = 0; i < 20; i++)
            {
                map.put("key" + i, i);
                assertThat(map.size()).isLessThanOrEqualTo(4);
                assertThat(map.trueSize()).isEqualTo(map.size());
            }

            assertThat(map.size()).isEqualTo(4);
            assertThat(map.trueSize()).isEqualTo(4);
        }

        @Test
        void evictionRealignsCollisionCluster()
        {
            // Create a collision cluster: k1, k2, k3 at hash 2
            // k4 at hash 5.
            final LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            final FixedHashKey k1 = new FixedHashKey(1, 2);
            final FixedHashKey k2 = new FixedHashKey(2, 2);
            final FixedHashKey k3 = new FixedHashKey(3, 2);
            final FixedHashKey k4 = new FixedHashKey(4, 5);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");
            map.put(k4, "v4");

            // Insert k5 to trigger eviction
            final FixedHashKey k5 = new FixedHashKey(5, 0);
            map.put(k5, "v5");

            assertThat(map.size()).isEqualTo(4);
            assertThat(map.trueSize()).isEqualTo(4);

            // All remaining keys in the collision cluster must still be reachable
            for (FixedHashKey k : map.keys())
            {
                assertThat(map.get(k)).isNotNull();
                assertThat(map.getRaw(k)).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Diagnostics, Debug, and String Formatting Tests")
    class DiagnosticsAndDebugTests
    {
        @Test
        void occupiedSpaceMatchesBackingArrayLength()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThat(map.occupiedSpace()).isEqualTo(8);

            final LRUClockMap<String, String> map16 = new LRUClockMap<>(16);
            assertThat(map16.occupiedSpace()).isEqualTo(32);
        }

        @Test
        void trueSizeAlwaysMatchesActualOccupiedSlots()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThat(map.trueSize()).isZero();

            map.put("k1", "v1");
            assertThat(map.trueSize()).isEqualTo(1);
            assertThat(map.size()).isEqualTo(1);

            map.put("k2", "v2");
            assertThat(map.trueSize()).isEqualTo(2);

            map.remove("k1");
            assertThat(map.trueSize()).isEqualTo(1);
            assertThat(map.size()).isEqualTo(1);

            map.clear();
            assertThat(map.trueSize()).isZero();
            assertThat(map.size()).isZero();
        }

        @Test
        void getDebugDataReturnsAccurateWrappersAndNulls()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            map.put("alpha", "beta");

            final var debugList = map.getDebugData();
            assertThat(debugList).hasSize(map.occupiedSpace());

            long occupiedCount = debugList.stream().filter(java.util.Objects::nonNull).count();
            assertThat(occupiedCount).isEqualTo(1);

            final var debugWrapper = debugList.stream()
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();

            assertThat(debugWrapper.key).isEqualTo("alpha");
            assertThat(debugWrapper.value).isEqualTo("beta");
            assertThat(debugWrapper.secondChance).isTrue();
            assertThat(debugWrapper.currentPosition).isBetween(0, 7);
            assertThat(debugWrapper.truePosition).isBetween(0, 7);
            assertThat(debugWrapper.toString()).contains("alpha", "beta", "true");
        }

        @Test
        void toStringFormatsCorrectlyForSmallMap()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            map.put("a", "1");

            final String str = map.toString();
            assertThat(str).startsWith("LRUClockMap{\n");
            assertThat(str).contains("FREE");
            assertThat(str).contains("[a, 1,");
            assertThat(str).contains("clockHand: ");
            assertThat(str).contains("size: 1");
            assertThat(str).contains("maxSize: 4");
            assertThat(str).endsWith("\n}");
        }

        @Test
        void toStringTruncatesAt1024SlotsForVeryLargeMap()
        {
            // maxSize = 1024 -> capacity = 2048 > 1024
            final LRUClockMap<Integer, Integer> map = new LRUClockMap<>(1024);
            assertThat(map.occupiedSpace()).isEqualTo(2048);

            final String str = map.toString();
            // Should contain slot indices up to 1023, but not 1024
            assertThat(str).contains("1023 FREE");
            assertThat(str).doesNotContain("1024 FREE");
            assertThat(str).contains("maxSize: 1024");
        }

        @Test
        void wrapperToStringDirectTestViaReflection() throws Exception
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            map.put("foo", "bar");

            final Field dataField = LRUClockMap.class.getDeclaredField("data");
            dataField.setAccessible(true);
            final Object[] data = (Object[]) dataField.get(map);

            Object wrapper = null;
            for (Object o : data)
            {
                if (o != null)
                {
                    wrapper = o;
                    break;
                }
            }

            assertThat(wrapper).isNotNull();
            assertThat(wrapper.toString()).isEqualTo("[foo, bar, true]");
        }
    }

    @Nested
    @DisplayName("Stress and Differential Invariant Tests")
    class StressAndDifferentialTests
    {
        @Test
        void differentialStressTestAgainstInvariants()
        {
            final int maxSize = 16;
            final LRUClockMap<Integer, String> map = new LRUClockMap<>(maxSize);
            final Random rnd = new Random(42);

            for (int step = 0; step < 5000; step++)
            {
                final int op = rnd.nextInt(10);
                final int key = rnd.nextInt(50);

                if (op < 6)
                {
                    // 60% put
                    final String val = "val_" + step;
                    map.put(key, val);
                    assertThat(map.get(key)).isEqualTo(val);
                    assertThat(map.getRaw(key)).isEqualTo(val);
                }
                else if (op < 8)
                {
                    // 20% get / getRaw
                    final String valGet = map.get(key);
                    final String valRaw = map.getRaw(key);
                    assertThat(valGet).isEqualTo(valRaw);
                }
                else
                {
                    // 20% remove
                    map.remove(key);
                    assertThat(map.get(key)).isNull();
                    assertThat(map.getRaw(key)).isNull();
                }

                // Invariant checks
                assertThat(map.size()).isLessThanOrEqualTo(maxSize);
                assertThat(map.trueSize()).isEqualTo(map.size());
                assertThat(map.keys()).hasSize(map.size());

                for (Integer k : map.keys())
                {
                    assertThat(map.getRaw(k)).isNotNull();
                }
            }
        }

        @Test
        void highCollisionContinuousChurn()
        {
            final LRUClockMap<FixedHashKey, Integer> map = new LRUClockMap<>(4);

            // Insert 50 keys that all have hash = 3
            for (int i = 0; i < 50; i++)
            {
                final FixedHashKey key = new FixedHashKey(i, 3);
                map.put(key, i);

                assertThat(map.size()).isLessThanOrEqualTo(4);
                assertThat(map.trueSize()).isEqualTo(map.size());
                assertThat(map.get(key)).isEqualTo(i);
                assertThat(map.getRaw(key)).isEqualTo(i);
            }

            // Verify all remaining keys are accessible and consistent
            assertThat(map.size()).isEqualTo(4);
            assertThat(map.trueSize()).isEqualTo(4);
            for (FixedHashKey k : map.keys())
            {
                assertThat(map.get(k)).isEqualTo(k.id());
                assertThat(map.getRaw(k)).isEqualTo(k.id());
            }
        }

        @Test
        void boundaryWrapAroundContinuousChurn()
        {
            final LRUClockMap<FixedHashKey, Integer> map = new LRUClockMap<>(4);
            final int capacity = map.occupiedSpace(); // 8
            final int mask = capacity - 1; // 7

            // Insert keys hashing to the boundary (mask)
            for (int i = 0; i < 50; i++)
            {
                final FixedHashKey key = new FixedHashKey(i, mask);
                map.put(key, i);

                assertThat(map.size()).isLessThanOrEqualTo(4);
                assertThat(map.trueSize()).isEqualTo(map.size());
            }

            assertThat(map.size()).isEqualTo(4);
            for (FixedHashKey k : map.keys())
            {
                assertThat(map.get(k)).isEqualTo(k.id());
                assertThat(map.getRaw(k)).isEqualTo(k.id());
            }
        }

        @Test
        void minimumMaxSizeOperations()
        {
            final LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThat(map.size()).isZero();

            map.put("a", "1");
            map.put("b", "2");
            map.put("c", "3");
            map.put("d", "4");
            assertThat(map.size()).isEqualTo(4);

            // Overwrite all
            map.put("a", "10");
            map.put("b", "20");
            map.put("c", "30");
            map.put("d", "40");
            assertThat(map.size()).isEqualTo(4);
            assertThat(map.get("a")).isEqualTo("10");
            assertThat(map.get("b")).isEqualTo("20");
            assertThat(map.get("c")).isEqualTo("30");
            assertThat(map.get("d")).isEqualTo("40");

            // Remove all
            assertThat(map.remove("a")).isEqualTo("10");
            assertThat(map.remove("b")).isEqualTo("20");
            assertThat(map.remove("c")).isEqualTo("30");
            assertThat(map.remove("d")).isEqualTo("40");
            assertThat(map.size()).isZero();
            assertThat(map.trueSize()).isZero();
        }
    }
}
