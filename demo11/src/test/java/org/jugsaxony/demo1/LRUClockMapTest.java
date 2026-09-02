package org.jugsaxony.demo1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LRUClockMapTest {

    @Nested
    @DisplayName("Constructor & Initialization Tests")
    class ConstructorTest {

        @Test
        @DisplayName("Constructor rejects maxSize < 4 and maxSize > (1 << 29)")
        void testInvalidMaxSize() {
            assertThatThrownBy(() -> new LRUClockMap<String, String>(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MaxSize must be at least 4");

            assertThatThrownBy(() -> new LRUClockMap<String, String>(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MaxSize must be at least 4");

            assertThatThrownBy(() -> new LRUClockMap<String, String>(3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MaxSize must be at least 4");

            assertThatThrownBy(() -> new LRUClockMap<String, String>(1 << 30))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Too large");
        }

        @Test
        @DisplayName("Capacity calculation across various maxSize boundaries")
        void testCapacityCalculations() {
            assertThat(new LRUClockMap<String, String>(4).occupiedSpace()).isEqualTo(8);
            assertThat(new LRUClockMap<String, String>(5).occupiedSpace()).isEqualTo(16);
            assertThat(new LRUClockMap<String, String>(8).occupiedSpace()).isEqualTo(16);
            assertThat(new LRUClockMap<String, String>(9).occupiedSpace()).isEqualTo(32);
            assertThat(new LRUClockMap<String, String>(16).occupiedSpace()).isEqualTo(32);
            assertThat(new LRUClockMap<String, String>(17).occupiedSpace()).isEqualTo(64);
            assertThat(new LRUClockMap<String, String>(32).occupiedSpace()).isEqualTo(64);
            assertThat(new LRUClockMap<String, String>(33).occupiedSpace()).isEqualTo(128);
            assertThat(new LRUClockMap<String, String>(64).occupiedSpace()).isEqualTo(128);
            assertThat(new LRUClockMap<String, String>(65).occupiedSpace()).isEqualTo(256);
        }

        @Test
        @DisplayName("Valid initial state for maxSize >= 4")
        void testValidInitialization() {
            LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThat(map.size()).isZero();
            assertThat(map.trueSize()).isZero();
            assertThat(map.occupiedSpace()).isEqualTo(8);
            assertThat(map.keys()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Basic Operations (Get, Put, Remove, Clear)")
    class BasicOperationsTest {

        @Test
        @DisplayName("Put and get single entry")
        void testPutAndGetSingle() {
            LRUClockMap<String, Integer> map = new LRUClockMap<>(10);
            assertThat(map.put("k1", 100)).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.trueSize()).isEqualTo(1);
            assertThat(map.get("k1")).isEqualTo(100);
            assertThat(map.getRaw("k1")).isEqualTo(100);
            assertThat(map.get("absent")).isNull();
            assertThat(map.getRaw("absent")).isNull();
        }

        @Test
        @DisplayName("Put overwrite updates value and returns old value without increasing size")
        void testPutOverwrite() {
            LRUClockMap<String, String> map = new LRUClockMap<>(10);
            assertThat(map.put("key", "val1")).isNull();
            assertThat(map.put("key", "val2")).isEqualTo("val1");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.trueSize()).isEqualTo(1);
            assertThat(map.get("key")).isEqualTo("val2");
        }

        @Test
        @DisplayName("Remove existing entry")
        void testRemoveExisting() {
            LRUClockMap<String, String> map = new LRUClockMap<>(10);
            map.put("k1", "v1");
            map.put("k2", "v2");

            assertThat(map.remove("k1")).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.trueSize()).isEqualTo(1);
            assertThat(map.get("k1")).isNull();
            assertThat(map.get("k2")).isEqualTo("v2");
        }

        @Test
        @DisplayName("Remove non-existing entry returns null and does not affect map")
        void testRemoveNonExisting() {
            LRUClockMap<String, String> map = new LRUClockMap<>(10);
            map.put("k1", "v1");

            assertThat(map.remove("absent")).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.trueSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("Clear resets size and all backing slots")
        void testClear() {
            LRUClockMap<Integer, String> map = new LRUClockMap<>(10);
            for (int i = 0; i < 5; i++) {
                map.put(i, "val" + i);
            }
            assertThat(map.size()).isEqualTo(5);

            map.clear();
            assertThat(map.size()).isZero();
            assertThat(map.trueSize()).isZero();
            assertThat(map.keys()).isEmpty();
            assertThat(map.get(0)).isNull();
        }

        @Test
        @DisplayName("Keys() returns snapshot in internal array order")
        void testKeys() {
            LRUClockMap<String, String> map = new LRUClockMap<>(10);
            map.put("a", "1");
            map.put("b", "2");
            map.put("c", "3");

            List<String> keys = map.keys();
            assertThat(keys).containsExactlyInAnyOrder("a", "b", "c");
            assertThat(keys).hasSize(3);

            // Modifying returned list does not mutate map
            keys.clear();
            assertThat(map.size()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Hash Collisions & Probing Branches")
    class CollisionTest {

        static class FixedHashKey {
            final int id;
            final int hash;

            FixedHashKey(int id, int hash) {
                this.id = id;
                this.hash = hash;
            }

            @Override
            public int hashCode() {
                return hash;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof FixedHashKey other)) return false;
                return id == other.id;
            }

            @Override
            public String toString() {
                return "Key#" + id;
            }
        }

        @Test
        @DisplayName("Colliding keys exercise expensiveGet and linear probing")
        void testCollidingKeysGetAndPut() {
            LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(20);
            int fixedHash = 42;

            FixedHashKey k0 = new FixedHashKey(0, fixedHash);
            FixedHashKey k1 = new FixedHashKey(1, fixedHash);
            FixedHashKey k2 = new FixedHashKey(2, fixedHash);
            FixedHashKey k3 = new FixedHashKey(3, fixedHash);

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            assertThat(map.size()).isEqualTo(4);
            assertThat(map.trueSize()).isEqualTo(4);

            // get() fast path (first slot k0) and expensiveGet (k1, k2, k3)
            assertThat(map.get(k0)).isEqualTo("v0");
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");

            // getRaw()
            assertThat(map.getRaw(k0)).isEqualTo("v0");
            assertThat(map.getRaw(k1)).isEqualTo("v1");
            assertThat(map.getRaw(k2)).isEqualTo("v2");
            assertThat(map.getRaw(k3)).isEqualTo("v3");

            // Miss for non-existing key with colliding hash
            FixedHashKey kAbsent = new FixedHashKey(999, fixedHash);
            assertThat(map.get(kAbsent)).isNull();
            assertThat(map.getRaw(kAbsent)).isNull();
        }

        @Test
        @DisplayName("Colliding keys overwrite existing entry in probing chain")
        void testCollidingKeyOverwrite() {
            LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(20);
            int fixedHash = 42;

            FixedHashKey k0 = new FixedHashKey(0, fixedHash);
            FixedHashKey k1 = new FixedHashKey(1, fixedHash);
            FixedHashKey k2 = new FixedHashKey(2, fixedHash);

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");

            // Update middle entry
            assertThat(map.put(k1, "v1_updated")).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(3);
            assertThat(map.get(k1)).isEqualTo("v1_updated");
        }

        @Test
        @DisplayName("expensiveGet flips secondChance and protects entry")
        void testExpensiveGetUpdatesSecondChance() {
            LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            int fixedHash = 0;
            FixedHashKey k0 = new FixedHashKey(0, fixedHash);
            FixedHashKey k1 = new FixedHashKey(1, fixedHash);
            FixedHashKey k2 = new FixedHashKey(2, fixedHash);
            FixedHashKey k3 = new FixedHashKey(3, fixedHash);

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Insert 5th item to clear secondChance flags and evict k0
            FixedHashKey k4 = new FixedHashKey(4, 4);
            map.put(k4, "v4");
            assertThat(map.get(k0)).isNull(); // k0 evicted

            // k2 had secondChance set to false; accessing k2 updates secondChance to true
            assertThat(map.get(k2)).isEqualTo("v2");
            // Call again when secondChance is already true
            assertThat(map.get(k2)).isEqualTo("v2");

            // Next eviction should skip k2 because secondChance is true
            FixedHashKey k5 = new FixedHashKey(5, 5);
            map.put(k5, "v5");
            assertThat(map.get(k1)).isNull(); // k1 evicted

            FixedHashKey k6 = new FixedHashKey(6, 6);
            map.put(k6, "v6");
            assertThat(map.get(k3)).isNull(); // k3 evicted

            // k2 is still preserved!
            assertThat(map.get(k2)).isEqualTo("v2");
        }

        @Test
        @DisplayName("Removal within collision chain correctly realigns subsequent entries")
        void testCollidingKeyRemovalAndRealignment() {
            LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(20);
            int fixedHash = 10;

            FixedHashKey k0 = new FixedHashKey(0, fixedHash);
            FixedHashKey k1 = new FixedHashKey(1, fixedHash);
            FixedHashKey k2 = new FixedHashKey(2, fixedHash);
            FixedHashKey k3 = new FixedHashKey(3, fixedHash);

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Remove head k0
            assertThat(map.remove(k0)).isEqualTo("v0");
            assertThat(map.size()).isEqualTo(3);
            assertThat(map.trueSize()).isEqualTo(3);
            assertThat(map.get(k0)).isNull();
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");

            // Remove middle k2
            assertThat(map.remove(k2)).isEqualTo("v2");
            assertThat(map.size()).isEqualTo(2);
            assertThat(map.trueSize()).isEqualTo(2);
            assertThat(map.get(k2)).isNull();
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k3)).isEqualTo("v3");

            // Remove tail k3
            assertThat(map.remove(k3)).isEqualTo("v3");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.trueSize()).isEqualTo(1);
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k3)).isNull();
        }

        @Test
        @DisplayName("MixHash validates upper 16-bit shift XOR and bit operations")
        void testMixHashHighBits() {
            // Direct mixHash unit testing
            assertThat(LRUClockMap.mixHash(0)).isZero();
            assertThat(LRUClockMap.mixHash(0x12345678)).isEqualTo(0x12345678 ^ 0x00001234);
            assertThat(LRUClockMap.mixHash(0x80000000)).isEqualTo(0x80000000 ^ 0x00008000);
            assertThat(LRUClockMap.mixHash(-1)).isEqualTo(-1 ^ 0x0000FFFF);

            LRUClockMap<FixedHashKey, String> map = new LRUClockMap<>(4);
            // 0x10001: 0x10001 ^ (0x10001 >>> 16) = 0x10001 ^ 1 = 0x10000 -> slot 0 in size 8
            FixedHashKey keyHigh = new FixedHashKey(1, 0x10001);
            map.put(keyHigh, "valHigh");
            assertThat(map.get(keyHigh)).isEqualTo("valHigh");
            assertThat(map.getDebugData().get(0)).isNotNull();
            assertThat(map.getDebugData().get(0).key).isEqualTo(keyHigh);
        }

        @Test
        @DisplayName("arraySize calculation and boundary checks")
        void testArraySizeCalculations() {
            assertThat(LRUClockMap.arraySize(4, 0.5f)).isEqualTo(8);
            assertThat(LRUClockMap.arraySize(1 << 29, 0.5f)).isEqualTo(1 << 30);
            assertThatThrownBy(() -> LRUClockMap.arraySize((1 << 29) + 256, 0.5f))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Too large");
        }
    }

    @Nested
    @DisplayName("Clock / Second Chance Eviction Mechanism")
    class EvictionTest {

        @Test
        @DisplayName("Clock evicts in forward direction (clockHand + 1)")
        void testClockEvictionExactSequence() {
            LRUClockMap<CollisionTest.FixedHashKey, String> map = new LRUClockMap<>(4);
            // Occupy slots 0, 1, 2, 3
            CollisionTest.FixedHashKey k0 = new CollisionTest.FixedHashKey(0, 0);
            CollisionTest.FixedHashKey k1 = new CollisionTest.FixedHashKey(1, 1);
            CollisionTest.FixedHashKey k2 = new CollisionTest.FixedHashKey(2, 2);
            CollisionTest.FixedHashKey k3 = new CollisionTest.FixedHashKey(3, 3);

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");
            assertThat(map.size()).isEqualTo(4);

            // Put k4 (hash 4) -> sweeps 0..3 clearing secondChance, wraps to 0, evicts k0
            CollisionTest.FixedHashKey k4 = new CollisionTest.FixedHashKey(4, 4);
            map.put(k4, "v4");
            assertThat(map.getRaw(k0)).isNull();
            assertThat(map.getRaw(k1)).isEqualTo("v1");
            assertThat(map.getRaw(k2)).isEqualTo("v2");
            assertThat(map.getRaw(k3)).isEqualTo("v3");
            assertThat(map.getRaw(k4)).isEqualTo("v4");

            // Put k5 (hash 5) -> clockHand advances to 1, evicts k1
            CollisionTest.FixedHashKey k5 = new CollisionTest.FixedHashKey(5, 5);
            map.put(k5, "v5");
            assertThat(map.getRaw(k1)).isNull();
            assertThat(map.getRaw(k2)).isEqualTo("v2");

            // Put k6 (hash 6) -> clockHand advances to 2, evicts k2
            CollisionTest.FixedHashKey k6 = new CollisionTest.FixedHashKey(6, 6);
            map.put(k6, "v6");
            assertThat(map.getRaw(k2)).isNull();
            assertThat(map.getRaw(k3)).isEqualTo("v3");

            // Put k7 (hash 7) -> clockHand advances to 3, evicts k3
            CollisionTest.FixedHashKey k7 = new CollisionTest.FixedHashKey(7, 7);
            map.put(k7, "v7");
            assertThat(map.getRaw(k3)).isNull();
            assertThat(map.getRaw(k4)).isEqualTo("v4");
        }

        @Test
        @DisplayName("Overwriting colliding entry at full capacity uses linear probe in update")
        void testUpdateWithCollisionAtFullCapacity() {
            LRUClockMap<CollisionTest.FixedHashKey, String> map = new LRUClockMap<>(4);
            int fixedHash = 0;
            CollisionTest.FixedHashKey k0 = new CollisionTest.FixedHashKey(0, fixedHash);
            CollisionTest.FixedHashKey k1 = new CollisionTest.FixedHashKey(1, fixedHash);
            CollisionTest.FixedHashKey k2 = new CollisionTest.FixedHashKey(2, fixedHash);
            CollisionTest.FixedHashKey k3 = new CollisionTest.FixedHashKey(3, fixedHash);

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            assertThat(map.size()).isEqualTo(4);

            // Overwrite k2 at full capacity (update() must advance ptr = (ptr + 1) & mask)
            assertThat(map.put(k2, "v2_updated")).isEqualTo("v2");
            assertThat(map.size()).isEqualTo(4);
            assertThat(map.get(k0)).isEqualTo("v0");
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isEqualTo("v2_updated");
            assertThat(map.get(k3)).isEqualTo("v3");
        }

        @Test
        @DisplayName("getRaw does not refresh secondChance flag")
        void testGetRawDoesNotSetSecondChance() {
            int maxSize = 4;
            LRUClockMap<Integer, String> map = new LRUClockMap<>(maxSize);

            for (int i = 0; i < maxSize; i++) {
                map.put(i, "val" + i);
            }

            // Force one round of clock eviction
            map.put(100, "val100");

            var debugList = map.getDebugData();
            var entryWithoutSecondChance = debugList.stream()
                    .filter(Objects::nonNull)
                    .filter(d -> !d.secondChance)
                    .findFirst();

            if (entryWithoutSecondChance.isPresent()) {
                Integer key = entryWithoutSecondChance.get().key;
                assertThat(map.getRaw(key)).isNotNull();
                var debugListAfter = map.getDebugData();
                var entryAfter = debugListAfter.stream()
                        .filter(Objects::nonNull)
                        .filter(d -> d.key.equals(key))
                        .findFirst();
                assertThat(entryAfter).isPresent();
                assertThat(entryAfter.get().secondChance).isFalse();

                assertThat(map.get(key)).isNotNull();
                var debugListAfterGet = map.getDebugData();
                var entryAfterGet = debugListAfterGet.stream()
                        .filter(Objects::nonNull)
                        .filter(d -> d.key.equals(key))
                        .findFirst();
                assertThat(entryAfterGet).isPresent();
                assertThat(entryAfterGet.get().secondChance).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Debug, Wrapper and String Representation")
    class DebugAndToStringTest {

        @Test
        @DisplayName("Wrapper toString representation")
        void testWrapperToString() {
            LRUClockMap.Wrapper<String, String> w = new LRUClockMap.Wrapper<>("k", "v");
            assertThat(w.toString()).isEqualTo("[k, v, true]");
            w.secondChance = false;
            assertThat(w.toString()).isEqualTo("[k, v, false]");
        }

        @Test
        @DisplayName("toString and DebugWrapper produce expected debug representations")
        void testToStringAndDebugData() {
            LRUClockMap<String, Integer> map = new LRUClockMap<>(4);
            map.put("alpha", 1);
            map.put("beta", 2);

            String str = map.toString();
            assertThat(str).contains("LRUClockMap{");
            assertThat(str).contains("FREE");
            assertThat(str).contains("clockHand:");
            assertThat(str).contains("size: 2");
            assertThat(str).contains("maxSize: 4");

            var debugData = map.getDebugData();
            assertThat(debugData).hasSize(map.occupiedSpace());

            var nonNullDebug = debugData.stream().filter(Objects::nonNull).findFirst();
            assertThat(nonNullDebug).isPresent();
            var dw = nonNullDebug.get();
            assertThat(dw.key).isNotNull();
            assertThat(dw.value).isNotNull();
            assertThat(dw.currentPosition).isGreaterThanOrEqualTo(0);
            assertThat(dw.truePosition).isGreaterThanOrEqualTo(0);
            String debugStr = dw.toString();
            assertThat(debugStr).startsWith("[").endsWith("]");

            // Verify DebugWrapper calculates truePosition using bitwise AND and mask
            CollisionTest.FixedHashKey highKey = new CollisionTest.FixedHashKey(99, 0x10005);
            LRUClockMap<CollisionTest.FixedHashKey, Integer> fixedMap = new LRUClockMap<>(4);
            fixedMap.put(highKey, 99);
            var fixedDebug = fixedMap.getDebugData();
            var dwHigh = fixedDebug.stream().filter(Objects::nonNull).filter(d -> d.key.equals(highKey)).findFirst().get();
            // 0x10005 ^ (0x10005 >>> 16) = 0x10005 ^ 1 = 0x10004
            // capacity is 8 (mask = 7). 0x10004 & 7 = 4.
            // If bitwise AND was replaced with OR, 0x10004 | 7 = 0x10007 (which is 65543 != 4)
            assertThat(dwHigh.truePosition).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Scale and Randomized Stress Tests")
    class StressTest {

        @ParameterizedTest
        @ValueSource(ints = {10, 50, 200})
        @DisplayName("Continuous inserts maintain size <= maxSize and size == trueSize")
        void testContinuousInserts(int maxSize) {
            LRUClockMap<Integer, String> map = new LRUClockMap<>(maxSize);

            for (int i = 0; i < maxSize * 10; i++) {
                map.put(i, "val" + i);
                assertThat(map.size()).isLessThanOrEqualTo(maxSize);
                assertThat(map.size()).isEqualTo(map.trueSize());
            }

            assertThat(map.size()).isEqualTo(maxSize);
            assertThat(map.trueSize()).isEqualTo(maxSize);
            assertThat(map.keys()).hasSize(maxSize);
        }

        @Test
        @DisplayName("Randomized operation sequence preserves internal invariants")
        void testRandomOperations() {
            int maxSize = 25;
            LRUClockMap<Integer, String> map = new LRUClockMap<>(maxSize);
            Random rng = new Random(42);

            for (int step = 0; step < 5_000; step++) {
                int op = rng.nextInt(100);
                int key = rng.nextInt(100);

                if (op < 50) {
                    // PUT
                    map.put(key, "val" + rng.nextInt(1000));
                } else if (op < 80) {
                    // GET or GET_RAW
                    if (rng.nextBoolean()) {
                        map.get(key);
                    } else {
                        map.getRaw(key);
                    }
                } else if (op < 95) {
                    // REMOVE
                    map.remove(key);
                } else {
                    // CLEAR
                    map.clear();
                }

                assertThat(map.size()).isLessThanOrEqualTo(maxSize);
                assertThat(map.size()).isEqualTo(map.trueSize());
                assertThat(map.keys()).hasSize(map.size());
            }
        }
    }
}


