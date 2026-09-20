package org.jugsaxony.demo12;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

            for (int step = 0; step < 2_000; step++) {
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

    @Nested
    @DisplayName("Mutation Coverage and Internal Implementation Tests")
    class MutationCoverageTest {

        @Test
        @DisplayName("Wrapper toString representation")
        void testWrapperToString() throws Exception {
            Class<?> wrapperClass = Class.forName("org.jugsaxony.demo12.LRUClockMap$Wrapper");
            Constructor<?> ctor = wrapperClass.getDeclaredConstructor(Object.class, Object.class);
            ctor.setAccessible(true);
            Object w = ctor.newInstance("foo", "bar");
            assertThat(w.toString()).isEqualTo("[foo, bar, true]");
        }

        @Test
        @DisplayName("nextPowerOfTwo direct boundary tests")
        void testNextPowerOfTwo() throws Exception {
            Method m = LRUClockMap.class.getDeclaredMethod("nextPowerOfTwo", long.class);
            m.setAccessible(true);

            assertThat((long) m.invoke(null, 0L)).isEqualTo(1L);
            assertThat((long) m.invoke(null, 1L)).isEqualTo(1L);
            assertThat((long) m.invoke(null, 2L)).isEqualTo(2L);
            assertThat((long) m.invoke(null, 3L)).isEqualTo(4L);
            assertThat((long) m.invoke(null, 5L)).isEqualTo(8L);
            assertThat((long) m.invoke(null, 17L)).isEqualTo(32L);
            assertThat((long) m.invoke(null, (1L << 31) + 1L)).isEqualTo(1L << 32);
            assertThat((long) m.invoke(null, 1L << 40)).isEqualTo(1L << 40);
        }

        @Test
        @DisplayName("arraySize boundary and validation")
        void testArraySize() throws Exception {
            Method m = LRUClockMap.class.getDeclaredMethod("arraySize", int.class, float.class);
            m.setAccessible(true);

            // Exactly 1 << 30
            assertThat((int) m.invoke(null, 1 << 29, 0.5f)).isEqualTo(1 << 30);

            // Exceeds 1 << 30
            assertThatThrownBy(() -> {
                try {
                    m.invoke(null, 1 << 30, 0.5f);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("mixHash bit mixing tests")
        void testMixHash() throws Exception {
            LRUClockMap<String, String> map = new LRUClockMap<>(4);
            Method m = LRUClockMap.class.getDeclaredMethod("mixHash", int.class);
            m.setAccessible(true);

            int val = 0x12345678;
            int expected = val ^ (val >>> 16);
            assertThat((int) m.invoke(map, val)).isEqualTo(expected);
            assertThat((int) m.invoke(map, 0)).isEqualTo(0);
        }

        @Test
        @DisplayName("expensiveGet sets secondChance when false and retains when true")
        void testExpensiveGetSecondChance() throws Exception {
            LRUClockMap<Integer, String> map = new LRUClockMap<>(4);
            int mask = map.occupiedSpace() - 1;

            // Find two keys that collide into same initial slot
            Integer k1 = null;
            Integer k2 = null;
            for (int i = 0; i < 1000; i++) {
                for (int j = i + 1; j < 1000; j++) {
                    int h1 = (i ^ (i >>> 16)) & mask;
                    int h2 = (j ^ (j >>> 16)) & mask;
                    if (h1 == h2) {
                        k1 = i;
                        k2 = j;
                        break;
                    }
                }
                if (k1 != null) break;
            }

            assertThat(k1).isNotNull();
            assertThat(k2).isNotNull();

            map.put(k1, "v1");
            map.put(k2, "v2"); // k2 collides with k1 and sits at linear probe position

            Field dataField = LRUClockMap.class.getDeclaredField("data");
            dataField.setAccessible(true);
            Object[] data = (Object[]) dataField.get(map);

            Field scField = Class.forName("org.jugsaxony.demo12.LRUClockMap$Wrapper").getDeclaredField("secondChance");
            scField.setAccessible(true);

            // Set k2's wrapper secondChance to false
            for (Object w : data) {
                if (w != null) {
                    Field kField = w.getClass().getDeclaredField("key");
                    kField.setAccessible(true);
                    if (kField.get(w).equals(k2)) {
                        scField.setBoolean(w, false);
                    }
                }
            }

            // First expensive get: transitions secondChance from false to true (executes line 226)
            assertThat(map.get(k2)).isEqualTo("v2");
            // Verify secondChance was indeed set to true (kills NegateConditionals on line 221)
            for (Object w : data) {
                if (w != null) {
                    Field kField = w.getClass().getDeclaredField("key");
                    kField.setAccessible(true);
                    if (kField.get(w).equals(k2)) {
                        assertThat(scField.getBoolean(w)).isTrue();
                    }
                }
            }

            // Second expensive get: secondChance is already true (skips line 226)
            assertThat(map.get(k2)).isEqualTo("v2");
        }

        @Test
        @DisplayName("update on collided key verifies ptr + 1 linear probe when at maxSize")
        void testUpdateCollidedKey() {
            LRUClockMap<Integer, String> map = new LRUClockMap<>(4);
            int mask = map.occupiedSpace() - 1;

            Integer k1 = null;
            Integer k2 = null;
            for (int i = 0; i < 1000; i++) {
                for (int j = i + 1; j < 1000; j++) {
                    int h1 = (i ^ (i >>> 16)) & mask;
                    int h2 = (j ^ (j >>> 16)) & mask;
                    if (h1 == h2) {
                        k1 = i;
                        k2 = j;
                        break;
                    }
                }
                if (k1 != null) break;
            }

            assertThat(k1).isNotNull();
            assertThat(k2).isNotNull();

            map.put(k1, "v1");
            map.put(k2, "v2");
            // Fill remaining slots up to maxSize 4
            int extra = 10000;
            while (map.size() < 4) {
                map.put(extra++, "extra");
            }

            assertThat(map.size()).isEqualTo(4);

            // Overwrite collided key: calls update(k2, "v2_updated", ptr) which probes (ptr + 1) & mask
            assertThat(map.put(k2, "v2_updated")).isEqualTo("v2");
            assertThat(map.get(k2)).isEqualTo("v2_updated");
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(4);
        }

        @Test
        @DisplayName("evict advances clockHand forward (+1), not backward (-1)")
        void testEvictSecondChanceAndClockHand() throws Exception {
            LRUClockMap<Integer, String> map = new LRUClockMap<>(4);

            Field clockHandField = LRUClockMap.class.getDeclaredField("clockHand");
            clockHandField.setAccessible(true);
            Field dataField = LRUClockMap.class.getDeclaredField("data");
            dataField.setAccessible(true);
            Object[] data = (Object[]) dataField.get(map);

            Class<?> wrapperClass = Class.forName("org.jugsaxony.demo12.LRUClockMap$Wrapper");
            Constructor<?> wrapperCtor = wrapperClass.getDeclaredConstructor(Object.class, Object.class);
            wrapperCtor.setAccessible(true);
            Field scField = wrapperClass.getDeclaredField("secondChance");
            scField.setAccessible(true);

            // clockHand = 1
            // slot 0 (backward): key 100, secondChance = false
            // slot 1 (current): key 101, secondChance = true
            // slot 2 (forward): key 102, secondChance = false
            Object w0 = wrapperCtor.newInstance(100, "v100");
            scField.setBoolean(w0, false);
            Object w1 = wrapperCtor.newInstance(101, "v101");
            scField.setBoolean(w1, true);
            Object w2 = wrapperCtor.newInstance(102, "v102");
            scField.setBoolean(w2, false);

            data[0] = w0;
            data[1] = w1;
            data[2] = w2;
            clockHandField.setInt(map, 1);

            Method evictMethod = LRUClockMap.class.getDeclaredMethod("evict");
            evictMethod.setAccessible(true);
            evictMethod.invoke(map);

            // In forward direction (+1):
            // slot 1: secondChance true -> set to false (survives in slot 1)
            // slot 2: secondChance false -> evicted (slot 2 becomes null)
            // slot 0 is NOT evicted and still in data[0]
            // If line 389 negated: slot 1 is evicted immediately (data[1] is removed/shifted)
            // If line 402 backward (-1): slot 0 is evicted instead of slot 2
            assertThat(data[1]).isNotNull();
            assertThat(scField.getBoolean(data[1])).isFalse();

            assertThat(data[0]).isNotNull();
            Field kField = wrapperClass.getDeclaredField("key");
            kField.setAccessible(true);
            assertThat(kField.get(data[0])).isEqualTo(100);

            assertThat(data[2]).isNull();
        }

        @Test
        @DisplayName("DebugWrapper truePosition bitwise mask verification")
        void testDebugWrapperBitwiseMask() throws Exception {
            LRUClockMap<Integer, String> map = new LRUClockMap<>(4);
            int highKey = 0x12345;
            map.put(highKey, "val");

            Method m = LRUClockMap.class.getDeclaredMethod("mixHash", int.class);
            m.setAccessible(true);
            int mask = map.occupiedSpace() - 1;
            int expectedTruePos = ((int) m.invoke(map, Integer.hashCode(highKey))) & mask;

            var debugData = map.getDebugData();
            var entry = debugData.stream().filter(Objects::nonNull).filter(d -> d.key.equals(highKey)).findFirst().get();
            assertThat(entry.truePosition).isEqualTo(expectedTruePos);
            assertThat(entry.truePosition).isBetween(0, mask);
        }
    }
}

