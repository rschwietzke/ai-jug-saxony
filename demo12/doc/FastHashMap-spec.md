# FastHashMap Technical Specification & Implementation Plan

## 1. Overview & Purpose
`FastHashMap<K, V>` is a high-performance, open-addressing (open hashing) hash map implementation in Java. Unlike standard chaining hash maps (such as `java.util.HashMap` which uses linked nodes and red-black trees), `FastHashMap` stores keys and values in flat, parallel arrays to maximize CPU cache locality, eliminate per-entry object allocation overhead, and deliver fast lookups, insertions, and deletions.

---

## 2. API Contract & Method Signatures

The class belongs to package `org.jugsaxony.demo1` and is generic over key type `K` and value type `V`:
```java
package org.jugsaxony.demo1;

import java.util.List;

public class FastHashMap<K, V> {
    public FastHashMap();
    public V get(final K key);
    public V put(final K key, final V value);
    public V remove(final K key);
    public int size();
    public List<K> keys();
    public List<V> values();
    public void clear();
}
```

### Method Behaviors & Edge Cases

| Method | Description | Return Value | Null Handling | Complexity |
| :--- | :--- | :--- | :--- | :--- |
| `FastHashMap()` | Constructs an empty map with default initial capacity (16) and default load factor (0.65). | N/A | N/A | $O(1)$ |
| `V get(final K key)` | Retrieves the value associated with `key`. | Value mapped to `key`, or `null` if not found or mapped to `null`. | Throws `NullPointerException` if `key == null`. | $O(1)$ avg, $O(n)$ worst |
| `V put(final K key, final V value)` | Associates `value` with `key`. If `key` exists, replaces and returns old value. | Previous value associated with `key`, or `null` if key was absent or previously mapped to `null`. | Throws `NullPointerException` if `key == null`. `value` may be `null`. | $O(1)$ amortized |
| `V remove(final K key)` | Removes the mapping for `key` if present. | Previous value associated with `key`, or `null` if key was absent or mapped to `null`. | Throws `NullPointerException` if `key == null`. | $O(1)$ avg |
| `int size()` | Returns the count of key-value pairs stored in the map. | Current number of entries (`>= 0`). | N/A | $O(1)$ |
| `List<K> keys()` | Returns a snapshot `List<K>` containing all keys currently in the map. | New `ArrayList<K>` containing all present keys. | No `null` keys. | $O(\text{capacity})$ |
| `List<V> values()` | Returns a snapshot `List<V>` containing all values currently in the map. | New `ArrayList<V>` containing all present values (including `null` values). | May contain `null` elements if present. | $O(\text{capacity})$ |
| `void clear()` | Removes all mappings, resetting size to 0 and nulling array elements to allow garbage collection. | `void` | N/A | $O(\text{capacity})$ |

---

## 3. Data Structure & Algorithmic Design

### 3.1 Memory Layout (Parallel Arrays)
To achieve maximum performance and zero object wrapper overhead:
- `K[] keys`: Flat array of keys. An empty slot is identified by `keys[i] == null`.
- `V[] values`: Flat array of values matching indices in `keys`. If a slot is occupied with a `null` value, `keys[i] != null` and `values[i] == null`.
- `int size`: Number of active entries.
- `int capacity`: Power-of-two table size ($2^n$).
- `int mask`: `capacity - 1`, for fast bitwise modulo (`hash & mask`).
- `int threshold`: Resize threshold (`(int)(capacity * loadFactor)`).

```
Index:    0       1       2       3       4      ...   Capacity - 1
keys:   [KeyA]  [null]  [KeyB]  [KeyC]  [null]   ...   [KeyZ]
values: [ValA]  [null]  [null]  [ValC]  [null]   ...   [ValZ]
                         ^ KeyB has null value!
```

### 3.2 Probing Strategy & Collision Resolution
* **Linear Probing with Power-of-Two Bitmask**:
  - Initial bucket index: `index = mixHash(key.hashCode()) & mask`
  - Probe step: `index = (index + 1) & mask`
  - Optimal CPU cache line utilization.

### 3.3 Deletion Strategy: Backward-Shift Deletion (Knuth's Algorithm R)
Tombstone-free removal:
- When removing an entry at index $i$:
  1. Clear slot $i$: `keys[i] = null; values[i] = null;`
  2. Scan subsequent contiguous slots $j = (i + 1) \ \& \ \text{mask}$ until an empty slot (`keys[j] == null`) is encountered.
  3. For each slot $j$, check its natural hash index $r = \text{mixHash}(keys[j].hashCode()) \ \& \ \text{mask}$.
  4. If candidate at $j$ belongs at or before hole $i$ cyclically (i.e. `((i - r) & mask) < ((j - r) & mask)`), shift element from $j$ into $i$, set $i = j$, clear slot $i$, and continue scanning forward.
  5. Decrement `size`.

### 3.4 Hash Mixing Function
32-bit MurmurHash3 finalizer:
```java
private static int mixHash(int h) {
    h ^= h >>> 16;
    h *= 0x85ebca6b;
    h ^= h >>> 13;
    h *= 0xc2b2ae35;
    h ^= h >>> 16;
    return h;
}
```

### 3.5 Dynamic Resizing
- When inserting a new key and `size >= threshold`, allocate `newCapacity = capacity << 1`.
- Re-index all non-null entries into the new arrays.
- Default load factor: **0.65**.

---

## 4. Test Suite (JUnit 6 + AssertJ)

1. **Basic Operations**: Single put/get, overwriting, removing existing/non-existing keys, clearing and re-inserting.
2. **Null Contract**:
   - `put(null, val)`, `get(null)`, `remove(null)` throw `NullPointerException`.
   - `put(key, null)` allowed, retrieved via `get(key) == null`, correctly listed in `values()`.
3. **Collision & Backward-Shift Verification**:
   - High-collision chains using synthetic `CollisionKey` instances.
   - Removals from head, middle, and tail of collision clusters.
   - Wrap-around probing across array boundaries.
4. **Dynamic Resizing**: Inserting 100 to 50,000 elements across multiple table doublings.
5. **Differential Fuzzing**: 50,000 randomized operations cross-verified against `java.util.HashMap`.

---

## 5. Multi-Module Project Layout

```
.
├── pom.xml (Root parent POM, packaging: pom, modules: [demo1])
└── demo1/
    ├── pom.xml (Subproject POM, packaging: jar, Java 21, JUnit 6, AssertJ)
    ├── doc/
    │   └── FastHashMap-spec.md
    └── src/
        ├── main/
        │   └── java/
        │       └── org/
        │           └── jugsaxony/
        │               └── demo1/
        │                   └── FastHashMap.java
        └── test/
            └── java/
                └── org/
                    └── jugsaxony/
                        └── demo1/
                            └── FastHashMapTest.java
```
