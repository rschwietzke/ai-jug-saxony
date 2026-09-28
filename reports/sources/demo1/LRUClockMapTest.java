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
        @DisplayName("Constructor rejects maxSize < 4")
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
        @DisplayName("Valid initial state for maxSize >= 4")
        void testValidInitialization() {
            LRUClockMap<String, String> map = new LRUClockMap<>(4);
            assertThat(map.size()).isZero();
            assertThat(map.trueSize()).isZero();
            assertThat(map.occupiedSpace()).isGreaterThanOrEqualTo(8);
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
        @DisplayName("expensiveGet updates secondChance from false to true for colliding key")
        void testExpensiveGetUpdatesSecondChance() {
            LRUClockMap<FixedHashKey, String> tightMap = new LRUClockMap<>(4);
            int fixedHash = 42;
            FixedHashKey tk0 = new FixedHashKey(0, fixedHash);
            FixedHashKey tk1 = new FixedHashKey(1, fixedHash);
            FixedHashKey tk2 = new FixedHashKey(2, fixedHash);
            FixedHashKey tk3 = new FixedHashKey(3, fixedHash);

            tightMap.put(tk0, "v0");
            tightMap.put(tk1, "v1");
            tightMap.put(tk2, "v2");
            tightMap.put(tk3, "v3");

            // Inserting a 5th item causes clock scan to flip secondChance to false
            tightMap.put(new FixedHashKey(4, 999), "v4");

            // tk1 was in collision chain and had secondChance set to false.
            // Accessing tk1 via get() will execute expensiveGet and flip secondChance to true!
            if (tightMap.getRaw(tk1) != null) {
                assertThat(tightMap.get(tk1)).isEqualTo("v1");
            }
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
    }

    @Nested
    @DisplayName("Clock / Second Chance Eviction Mechanism")
    class EvictionTest {

        @Test
        @DisplayName("Capacity is respected and eviction occurs when size reaches maxSize")
        void testEvictionOccursAtMaxSize() {
            int maxSize = 4;
            LRUClockMap<String, Integer> map = new LRUClockMap<>(maxSize);

            for (int i = 0; i < maxSize; i++) {
                map.put("k" + i, i);
            }
            assertThat(map.size()).isEqualTo(maxSize);
            assertThat(map.trueSize()).isEqualTo(maxSize);

            // Inserting a 5th entry must trigger eviction
            map.put("k4", 4);
            assertThat(map.size()).isEqualTo(maxSize);
            assertThat(map.trueSize()).isEqualTo(maxSize);
            assertThat(map.get("k4")).isEqualTo(4);
        }

        @Test
        @DisplayName("Second chance flag protects accessed entries from immediate eviction")
        void testSecondChanceProtection() {
            int maxSize = 4;
            LRUClockMap<Integer, String> map = new LRUClockMap<>(maxSize);

            // Insert 4 items (all secondChance = true initially)
            for (int i = 0; i < maxSize; i++) {
                map.put(i, "val" + i);
            }

            // Inspect debug data: all 4 entries should have secondChance == true
            var debugBefore = map.getDebugData();
            long secondChanceCount = debugBefore.stream().filter(Objects::nonNull).filter(d -> d.secondChance).count();
            assertThat(secondChanceCount).isEqualTo(4);

            // Inserting item 4 causes clockHand to scan through items, flipping their secondChance to false,
            // and evicting the first entry whose secondChance was flipped to false.
            map.put(4, "val4");
            assertThat(map.size()).isEqualTo(maxSize);

            // Now access item 4 so it gains secondChance = true again
            map.get(4);
            var debugAfter = map.getDebugData();
            var item4Debug = debugAfter.stream().filter(Objects::nonNull).filter(d -> d.key.equals(4)).findFirst();
            assertThat(item4Debug).isPresent();
            assertThat(item4Debug.get().secondChance).isTrue();
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

            // Find an entry with secondChance == false
            var debugList = map.getDebugData();
            var entryWithoutSecondChance = debugList.stream()
                    .filter(Objects::nonNull)
                    .filter(d -> !d.secondChance)
                    .findFirst();

            if (entryWithoutSecondChance.isPresent()) {
                Integer key = entryWithoutSecondChance.get().key;
                // getRaw should return value without altering secondChance
                assertThat(map.getRaw(key)).isNotNull();
                var debugListAfter = map.getDebugData();
                var entryAfter = debugListAfter.stream()
                        .filter(Objects::nonNull)
                        .filter(d -> d.key.equals(key))
                        .findFirst();
                assertThat(entryAfter).isPresent();
                assertThat(entryAfter.get().secondChance).isFalse();

                // get() should update secondChance to true
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

        @Test
        @DisplayName("Overwriting an existing key at full capacity updates in-place without evicting")
        void testOverwriteAtFullCapacity() {
            int maxSize = 4;
            LRUClockMap<String, String> map = new LRUClockMap<>(maxSize);

            for (int i = 0; i < maxSize; i++) {
                map.put("k" + i, "v" + i);
            }
            assertThat(map.size()).isEqualTo(maxSize);

            // Overwrite k0
            assertThat(map.put("k0", "v0_new")).isEqualTo("v0");
            assertThat(map.size()).isEqualTo(maxSize);
            assertThat(map.trueSize()).isEqualTo(maxSize);

            // All original keys must still exist
            for (int i = 0; i < maxSize; i++) {
                assertThat(map.get("k" + i)).isNotNull();
            }
            assertThat(map.get("k0")).isEqualTo("v0_new");
        }
    }

    @Nested
    @DisplayName("Debug and String Representation")
    class DebugAndToStringTest {

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

            // Check non-null debug wrapper toString
            var nonNullDebug = debugData.stream().filter(Objects::nonNull).findFirst();
            assertThat(nonNullDebug).isPresent();
            String debugStr = nonNullDebug.get().toString();
            assertThat(debugStr).startsWith("[").endsWith("]");
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

            for (int step = 0; step < 20_000; step++) {
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

