package org.jugsaxony.demo1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FastHashMapTest {

    @Nested
    @DisplayName("Basic CRUD & Map State")
    class BasicOperationsTest {

        @Test
        @DisplayName("Newly created map should be empty")
        void testEmptyMap() {
            FastHashMap<String, Integer> map = new FastHashMap<>();
            assertThat(map.size()).isZero();
            assertThat(map.get("key")).isNull();
            assertThat(map.keys()).isEmpty();
            assertThat(map.values()).isEmpty();
        }

        @Test
        @DisplayName("Single put and get")
        void testPutAndGetSingle() {
            FastHashMap<String, Integer> map = new FastHashMap<>();
            Integer prev = map.put("apple", 100);

            assertThat(prev).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("apple")).isEqualTo(100);
            assertThat(map.get("banana")).isNull();
        }

        @Test
        @DisplayName("Put overwrite should return old value and update mapping")
        void testPutOverwrite() {
            FastHashMap<String, String> map = new FastHashMap<>();
            assertThat(map.put("key1", "val1")).isNull();
            assertThat(map.put("key1", "val2")).isEqualTo("val1");

            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("key1")).isEqualTo("val2");
        }

        @Test
        @DisplayName("Remove existing key")
        void testRemoveExisting() {
            FastHashMap<String, String> map = new FastHashMap<>();
            map.put("k1", "v1");
            map.put("k2", "v2");

            String removed = map.remove("k1");
            assertThat(removed).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("k1")).isNull();
            assertThat(map.get("k2")).isEqualTo("v2");
        }

        @Test
        @DisplayName("Remove non-existing key returns null and keeps size unchanged")
        void testRemoveNonExisting() {
            FastHashMap<String, String> map = new FastHashMap<>();
            map.put("k1", "v1");

            assertThat(map.remove("unknown")).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("k1")).isEqualTo("v1");
        }

        @Test
        @DisplayName("Clear resets size and all backing slots")
        void testClear() {
            FastHashMap<Integer, String> map = new FastHashMap<>();
            for (int i = 0; i < 5; i++) {
                map.put(i, "val" + i);
            }
            assertThat(map.size()).isEqualTo(5);

            map.clear();
            assertThat(map.size()).isZero();
            assertThat(map.keys()).isEmpty();
            assertThat(map.values()).isEmpty();
            assertThat(map.get(0)).isNull();
            assertThat(map.keysArray()).containsOnlyNulls();
            assertThat(map.valuesArray()).containsOnlyNulls();

            // Clearing an already empty map is safe and idempotent
            map.clear();
            assertThat(map.size()).isZero();
            assertThat(map.keysArray()).containsOnlyNulls();
            assertThat(map.valuesArray()).containsOnlyNulls();
        }
    }

    @Nested
    @DisplayName("Null Key & Value Contracts")
    class NullHandlingTest {

        @Test
        @DisplayName("Null keys must throw NullPointerException")
        void testNullKeysRejected() {
            FastHashMap<String, String> map = new FastHashMap<>();

            assertThatNullPointerException()
                    .isThrownBy(() -> map.put(null, "val"))
                    .withMessage("Key cannot be null");

            assertThatNullPointerException()
                    .isThrownBy(() -> map.get(null))
                    .withMessage("Key cannot be null");

            assertThatNullPointerException()
                    .isThrownBy(() -> map.remove(null))
                    .withMessage("Key cannot be null");
        }

        @Test
        @DisplayName("Null values are fully supported")
        void testNullValuesSupported() {
            FastHashMap<String, String> map = new FastHashMap<>();

            // Insert null value
            assertThat(map.put("k1", null)).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("k1")).isNull();
            assertThat(map.keys()).containsExactly("k1");
            assertThat(map.values()).containsExactly((String) null);

            // Overwrite null value with non-null value
            assertThat(map.put("k1", "v1")).isNull();
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("k1")).isEqualTo("v1");

            // Overwrite non-null value with null value
            assertThat(map.put("k1", null)).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get("k1")).isNull();

            // Remove key with null value
            assertThat(map.remove("k1")).isNull();
            assertThat(map.size()).isZero();
            assertThat(map.keys()).isEmpty();
            assertThat(map.values()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Keys and Values Collection Snapshots")
    class CollectionsTest {

        @Test
        @DisplayName("Keys and values return independent snapshot lists")
        void testKeysAndValuesSnapshots() {
            FastHashMap<Integer, String> map = new FastHashMap<>();
            map.put(1, "one");
            map.put(2, "two");
            map.put(3, "three");

            List<Integer> keys = map.keys();
            List<String> values = map.values();

            assertThat(keys).containsExactlyInAnyOrder(1, 2, 3);
            assertThat(values).containsExactlyInAnyOrder("one", "two", "three");

            // Modifying returned lists should not affect the map
            keys.clear();
            values.clear();

            assertThat(map.size()).isEqualTo(3);
            assertThat(map.get(1)).isEqualTo("one");
        }
    }

    @Nested
    @DisplayName("Hash Collisions & Cluster Deletions")
    class CollisionAndProbingTest {

        static class CollisionKey {
            final int id;
            final int forcedHash;

            CollisionKey(int id, int forcedHash) {
                this.id = id;
                this.forcedHash = forcedHash;
            }

            @Override
            public int hashCode() {
                return forcedHash;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof CollisionKey that)) return false;
                return id == that.id;
            }

            @Override
            public String toString() {
                return "Key(id=" + id + ", hash=" + forcedHash + ")";
            }
        }

        @Test
        @DisplayName("Colliding keys probe and retrieve accurately")
        void testCollisionChainRetrieval() {
            FastHashMap<CollisionKey, String> map = new FastHashMap<>(16, 0.75f);
            int fixedHash = 42;

            List<CollisionKey> keys = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                CollisionKey k = new CollisionKey(i, fixedHash);
                keys.add(k);
                map.put(k, "val-" + i);
            }

            assertThat(map.size()).isEqualTo(10);
            for (int i = 0; i < 10; i++) {
                assertThat(map.get(keys.get(i))).isEqualTo("val-" + i);
            }
        }

        @Test
        @DisplayName("Cluster deletion: remove head of collision chain")
        void testRemoveHeadOfCollisionChain() {
            FastHashMap<CollisionKey, String> map = new FastHashMap<>(16, 0.75f);
            int fixedHash = 10;

            CollisionKey k0 = new CollisionKey(0, fixedHash);
            CollisionKey k1 = new CollisionKey(1, fixedHash);
            CollisionKey k2 = new CollisionKey(2, fixedHash);
            CollisionKey k3 = new CollisionKey(3, fixedHash);

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Remove head k0
            assertThat(map.remove(k0)).isEqualTo("v0");
            assertThat(map.size()).isEqualTo(3);
            assertThat(map.get(k0)).isNull();
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");
        }

        @Test
        @DisplayName("Cluster deletion: remove middle of collision chain")
        void testRemoveMiddleOfCollisionChain() {
            FastHashMap<CollisionKey, String> map = new FastHashMap<>(16, 0.75f);
            int fixedHash = 10;

            CollisionKey k0 = new CollisionKey(0, fixedHash);
            CollisionKey k1 = new CollisionKey(1, fixedHash);
            CollisionKey k2 = new CollisionKey(2, fixedHash);
            CollisionKey k3 = new CollisionKey(3, fixedHash);

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Remove middle k1
            assertThat(map.remove(k1)).isEqualTo("v1");
            assertThat(map.size()).isEqualTo(3);
            assertThat(map.get(k0)).isEqualTo("v0");
            assertThat(map.get(k1)).isNull();
            assertThat(map.get(k2)).isEqualTo("v2");
            assertThat(map.get(k3)).isEqualTo("v3");
        }

        @Test
        @DisplayName("Cluster deletion: remove tail of collision chain")
        void testRemoveTailOfCollisionChain() {
            FastHashMap<CollisionKey, String> map = new FastHashMap<>(16, 0.75f);
            int fixedHash = 10;

            CollisionKey k0 = new CollisionKey(0, fixedHash);
            CollisionKey k1 = new CollisionKey(1, fixedHash);
            CollisionKey k2 = new CollisionKey(2, fixedHash);

            map.put(k0, "v0");
            map.put(k1, "v1");
            map.put(k2, "v2");

            // Remove tail k2
            assertThat(map.remove(k2)).isEqualTo("v2");
            assertThat(map.size()).isEqualTo(2);
            assertThat(map.get(k0)).isEqualTo("v0");
            assertThat(map.get(k1)).isEqualTo("v1");
            assertThat(map.get(k2)).isNull();
        }

        @Test
        @DisplayName("Remove all items from collision cluster in random order")
        void testRemoveAllCollisionItems() {
            FastHashMap<CollisionKey, Integer> map = new FastHashMap<>(32, 0.8f);
            int fixedHash = 99;
            int count = 15;

            List<CollisionKey> keys = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                CollisionKey k = new CollisionKey(i, fixedHash);
                keys.add(k);
                map.put(k, i);
            }

            List<CollisionKey> removalOrder = new ArrayList<>(keys);
            Collections.shuffle(removalOrder, new Random(12345));

            for (CollisionKey k : removalOrder) {
                assertThat(map.remove(k)).isEqualTo(k.id);
                for (CollisionKey remaining : keys) {
                    if (removalOrder.indexOf(remaining) > removalOrder.indexOf(k)) {
                        assertThat(map.get(remaining)).isEqualTo(remaining.id);
                    }
                }
            }
            assertThat(map.size()).isZero();
        }
    }

    @Nested
    @DisplayName("MurmurHash3 MixHash & Deterministic Distribution")
    class HashMixingTest {

        static class SpecificHashKey {
            final String name;
            final int hash;

            SpecificHashKey(String name, int hash) {
                this.name = name;
                this.hash = hash;
            }

            @Override
            public int hashCode() {
                return hash;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof SpecificHashKey that)) return false;
                return Objects.equals(name, that.name);
            }

            @Override
            public String toString() {
                return name;
            }
        }

        @Test
        @DisplayName("MixHash validates MurmurHash3 finalizer constants and bit shifts")
        void testMixHashDistribution() {
            // Direct unit testing of MurmurHash3 finalizer operations
            assertThat(FastHashMap.mixHash(0)).isZero();
            
            int[] testInputs = {
                    0x00000000,
                    0x00000001,
                    0x00010000,
                    0x00002000,
                    0x12345678,
                    0x87654321,
                    0x55555555,
                    0xAAAAAAAA,
                    0x80000000,
                    0xFFFFFFFF
            };

            for (int input : testInputs) {
                int h = input;
                h ^= h >>> 16;
                h *= 0x85ebca6b;
                h ^= h >>> 13;
                h *= 0xc2b2ae35;
                h ^= h >>> 16;
                assertThat(FastHashMap.mixHash(input)).isEqualTo(h);
            }

            FastHashMap<SpecificHashKey, String> map = new FastHashMap<>(16, 0.95f);
            List<SpecificHashKey> keyList = new ArrayList<>();
            for (int i = 0; i < testInputs.length; i++) {
                SpecificHashKey key = new SpecificHashKey("k" + i, testInputs[i]);
                keyList.add(key);
                map.put(key, "val" + i);
            }

            assertThat(map.size()).isEqualTo(testInputs.length);

            for (int i = 0; i < testInputs.length; i++) {
                SpecificHashKey key = keyList.get(i);
                assertThat(map.get(key)).isEqualTo("val" + i);
            }

            List<SpecificHashKey> extractedKeys = map.keys();
            assertThat(extractedKeys).hasSize(testInputs.length);
        }
    }

    @Nested
    @DisplayName("Dynamic Resizing & Scale Tests")
    class ResizingAndScaleTest {

        @Test
        @DisplayName("Resize boundary triggers strictly when size >= threshold")
        void testExactThresholdResizeBoundary() {
            // initial capacity = 16, load factor = 0.5f -> capacity = 16, threshold = 8
            FastHashMap<Integer, String> map = new FastHashMap<>(16, 0.5f);
            assertThat(map.capacity()).isEqualTo(16);
            assertThat(map.threshold()).isEqualTo(8);

            // Put 7 items -> still at capacity 16
            for (int i = 0; i < 7; i++) {
                map.put(i, "v" + i);
            }
            assertThat(map.size()).isEqualTo(7);
            assertThat(map.capacity()).isEqualTo(16);

            // Put 8th item (size was 7 < threshold 8, becomes 8) -> still capacity 16
            map.put(7, "v7");
            assertThat(map.size()).isEqualTo(8);
            assertThat(map.capacity()).isEqualTo(16);

            // Inserting 9th item (size was 8 >= threshold 8) -> triggers resize to 32, threshold becomes 16
            map.put(8, "v8");
            assertThat(map.size()).isEqualTo(9);
            assertThat(map.capacity()).isEqualTo(32);
            assertThat(map.threshold()).isEqualTo(16);

            for (int i = 0; i <= 8; i++) {
                assertThat(map.get(i)).isEqualTo("v" + i);
            }
        }

        @ParameterizedTest
        @ValueSource(ints = {100, 1_000, 10_000})
        @DisplayName("Insert large sequence of items triggering multiple resizes")
        void testLargeInsertAndRetrieval(int count) {
            FastHashMap<Integer, String> map = new FastHashMap<>();

            for (int i = 0; i < count; i++) {
                map.put(i, "val-" + i);
            }

            assertThat(map.size()).isEqualTo(count);

            for (int i = 0; i < count; i++) {
                assertThat(map.get(i)).isEqualTo("val-" + i);
            }

            // Remove every other item
            for (int i = 0; i < count; i += 2) {
                assertThat(map.remove(i)).isEqualTo("val-" + i);
            }

            assertThat(map.size()).isEqualTo(count / 2);

            for (int i = 0; i < count; i++) {
                if (i % 2 == 0) {
                    assertThat(map.get(i)).isNull();
                } else {
                    assertThat(map.get(i)).isEqualTo("val-" + i);
                }
            }
        }
    }

    @Nested
    @DisplayName("Differential Fuzzing / Invariant Testing vs Reference HashMap")
    class FuzzTesting {

        @Test
        @DisplayName("5,000 random operations against standard java.util.HashMap")
        void testRandomOperationsAgainstReferenceMap() {
            FastHashMap<Integer, String> fastMap = new FastHashMap<>();
            Map<Integer, String> refMap = new HashMap<>();

            Random rng = new Random(42);
            int operations = 5_000;
            int keyDomain = 200;

            for (int op = 0; op < operations; op++) {
                int action = rng.nextInt(100);
                Integer key = rng.nextInt(keyDomain);

                if (action < 45) {
                    // PUT
                    String val = (rng.nextInt(20) == 0) ? null : ("v_" + rng.nextInt(1000));
                    String fastPrev = fastMap.put(key, val);
                    String refPrev = refMap.put(key, val);
                    assertThat(fastPrev).isEqualTo(refPrev);
                } else if (action < 75) {
                    // GET
                    String fastVal = fastMap.get(key);
                    String refVal = refMap.get(key);
                    assertThat(fastVal).isEqualTo(refVal);
                } else if (action < 98) {
                    // REMOVE
                    String fastRemoved = fastMap.remove(key);
                    String refRemoved = refMap.remove(key);
                    assertThat(fastRemoved).isEqualTo(refRemoved);
                } else {
                    // CLEAR
                    fastMap.clear();
                    refMap.clear();
                }

                assertThat(fastMap.size()).isEqualTo(refMap.size());
            }

            // Final state verification
            assertThat(fastMap.keys()).containsExactlyInAnyOrderElementsOf(refMap.keySet());
            assertThat(fastMap.values()).containsExactlyInAnyOrderElementsOf(refMap.values());
            for (Map.Entry<Integer, String> entry : refMap.entrySet()) {
                assertThat(fastMap.get(entry.getKey())).isEqualTo(entry.getValue());
            }
        }
    }

    @Nested
    @DisplayName("Constructors & Capacity Validation")
    class ConstructorAndValidationTest {

        @Test
        @DisplayName("Zero and small capacities should be handled cleanly")
        void testSmallCapacities() {
            FastHashMap<String, String> map0 = new FastHashMap<>(0);
            map0.put("k0", "v0");
            assertThat(map0.get("k0")).isEqualTo("v0");

            FastHashMap<String, String> map1 = new FastHashMap<>(1);
            map1.put("k1", "v1");
            assertThat(map1.get("k1")).isEqualTo("v1");

            FastHashMap<String, String> map2 = new FastHashMap<>(2);
            map2.put("k2", "v2");
            assertThat(map2.get("k2")).isEqualTo("v2");

            FastHashMap<String, String> map3 = new FastHashMap<>(3);
            map3.put("k3", "v3");
            assertThat(map3.get("k3")).isEqualTo("v3");

            FastHashMap<String, String> map1024 = new FastHashMap<>(1024);
            map1024.put("k", "v");
            assertThat(map1024.get("k")).isEqualTo("v");

            FastHashMap<String, String> mapMaxCap = new FastHashMap<>(FastHashMap.MAXIMUM_CAPACITY);
            assertThat(mapMaxCap.capacity()).isEqualTo(FastHashMap.MAXIMUM_CAPACITY);
        }

        @Test
        @DisplayName("Valid load factor boundaries")
        void testLoadFactorBoundaries() {
            FastHashMap<String, String> mapMinLF = new FastHashMap<>(16, 0.001f);
            mapMinLF.put("a", "b");
            assertThat(mapMinLF.get("a")).isEqualTo("b");

            FastHashMap<String, String> mapMaxLF = new FastHashMap<>(16, 0.999f);
            mapMaxLF.put("c", "d");
            assertThat(mapMaxLF.get("c")).isEqualTo("d");
        }

        @Test
        @DisplayName("Invalid constructor arguments throw IllegalArgumentException")
        void testInvalidConstructorArgs() {
            assertThatThrownBy(() -> new FastHashMap<>(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal initial capacity");

            assertThatThrownBy(() -> new FastHashMap<>(FastHashMap.MAXIMUM_CAPACITY + 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal initial capacity");

            assertThatThrownBy(() -> new FastHashMap<>(1 << 30))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal initial capacity");

            assertThatThrownBy(() -> new FastHashMap<>(16, 0.0f))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal load factor");

            assertThatThrownBy(() -> new FastHashMap<>(16, -0.1f))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal load factor");

            assertThatThrownBy(() -> new FastHashMap<>(16, 1.0f))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal load factor");

            assertThatThrownBy(() -> new FastHashMap<>(16, 1.5f))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal load factor");

            assertThatThrownBy(() -> new FastHashMap<>(16, Float.NaN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal load factor");
        }

        @Test
        @DisplayName("TableSizeFor calculations across power of two boundaries")
        void testTableSizeFor() {
            assertThat(FastHashMap.tableSizeFor(0)).isEqualTo(1);
            assertThat(FastHashMap.tableSizeFor(1)).isEqualTo(1);
            assertThat(FastHashMap.tableSizeFor(2)).isEqualTo(2);
            assertThat(FastHashMap.tableSizeFor(3)).isEqualTo(4);
            assertThat(FastHashMap.tableSizeFor(4)).isEqualTo(4);
            assertThat(FastHashMap.tableSizeFor(5)).isEqualTo(8);
            assertThat(FastHashMap.tableSizeFor(7)).isEqualTo(8);
            assertThat(FastHashMap.tableSizeFor(8)).isEqualTo(8);
            assertThat(FastHashMap.tableSizeFor(9)).isEqualTo(16);
            assertThat(FastHashMap.tableSizeFor(1024)).isEqualTo(1024);
            assertThat(FastHashMap.tableSizeFor(1025)).isEqualTo(2048);
            assertThat(FastHashMap.tableSizeFor(1 << 29)).isEqualTo(1 << 29);
            assertThat(FastHashMap.tableSizeFor(1 << 30)).isEqualTo(1 << 30);
        }
    }
}

