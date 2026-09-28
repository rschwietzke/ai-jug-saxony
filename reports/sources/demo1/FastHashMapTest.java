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
        @DisplayName("Clear removes all entries and resets size")
        void testClear() {
            FastHashMap<Integer, String> map = new FastHashMap<>();
            for (int i = 0; i < 20; i++) {
                map.put(i, "val" + i);
            }
            assertThat(map.size()).isEqualTo(20);

            map.clear();
            assertThat(map.size()).isZero();
            assertThat(map.keys()).isEmpty();
            assertThat(map.values()).isEmpty();
            assertThat(map.get(0)).isNull();
            assertThat(map.get(19)).isNull();

            // Re-inserting after clear
            map.put(42, "answer");
            assertThat(map.size()).isEqualTo(1);
            assertThat(map.get(42)).isEqualTo("answer");
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
    @DisplayName("Hash Collisions & Backward-Shift Deletions")
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
        @DisplayName("Backward-shift deletion: remove head of collision chain")
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
        @DisplayName("Backward-shift deletion: remove middle of collision chain")
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
        @DisplayName("Backward-shift deletion: remove tail of collision chain")
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
    @DisplayName("Dynamic Resizing & Scale Tests")
    class ResizingAndScaleTest {

        @ParameterizedTest
        @ValueSource(ints = {100, 1_000, 10_000, 50_000})
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
        @DisplayName("50,000 random operations against standard java.util.HashMap")
        void testRandomOperationsAgainstReferenceMap() {
            FastHashMap<Integer, String> fastMap = new FastHashMap<>();
            Map<Integer, String> refMap = new HashMap<>();

            Random rng = new Random(42);
            int operations = 50_000;
            int keyDomain = 500;

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
    @DisplayName("Constructors & Argument Validation")
    class ConstructorAndValidationTest {

        @Test
        @DisplayName("Single-argument constructor sets initial capacity")
        void testSingleArgConstructor() {
            FastHashMap<String, String> map = new FastHashMap<>(64);
            assertThat(map.size()).isZero();
            map.put("key", "val");
            assertThat(map.get("key")).isEqualTo("val");
        }

        @Test
        @DisplayName("Invalid constructor arguments throw IllegalArgumentException")
        void testInvalidConstructorArgs() {
            assertThatThrownBy(() -> new FastHashMap<>(-1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FastHashMap<>(16, 0.0f))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FastHashMap<>(16, 1.0f))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FastHashMap<>(16, Float.NaN))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

