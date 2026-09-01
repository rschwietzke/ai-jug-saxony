# FastHashMap Specification & Implementation Plan

## 1. Overview
`FastHashMap<K, V>` is an open-addressing (open hashing) hash map implementation in Java 21 designed for high performance, low overhead, and unbounded capacity. It resides in the package `org.jugsaxony.demo8`.

### Key Characteristics:
- **Open Addressing**: Uses contiguous array storage without chaining/linked lists.
- **Unbounded**: Dynamically resizes (rehashes) when load threshold is reached.
- **Not Thread-Safe**: Designed for single-threaded usage.
- **Null Keys**: Prohibited (`NullPointerException` thrown on null keys).
- **Null Values**: Supported.
- **Collision Resolution**: Open addressing with linear probing (or Robin Hood / Quadratic hashing; Linear Probing is standard, CPU cache friendly, and aligns with `LRUClockMap`).

---

## 2. API Specification

```java
package org.jugsaxony.demo8;

import java.util.List;

public class FastHashMap<K, V> {
    /**
     * Constructs a new, empty FastHashMap with default initial capacity and load factor.
     */
    public FastHashMap();

    /**
     * Returns the value to which the specified key is mapped,
     * or null if this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned (must not be null)
     * @return the value to which the specified key is mapped, or null if no mapping exists
     * @throws NullPointerException if key is null
     */
    public V get(final K key);

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is replaced.
     *
     * @param key key with which the specified value is to be associated (must not be null)
     * @param value value to be associated with the specified key (null allowed)
     * @return the previous value associated with key, or null if there was no mapping for key
     * @throws NullPointerException if key is null
     */
    public V put(final K key, final V value);

    /**
     * Removes the mapping for a key from this map if it is present.
     *
     * @param key key whose mapping is to be removed from the map (must not be null)
     * @return the previous value associated with key, or null if there was no mapping for key
     * @throws NullPointerException if key is null
     */
    public V remove(final K key);

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings
     */
    public int size();

    /**
     * Returns a List view of the keys contained in this map.
     *
     * @return a List of all keys currently in the map
     */
    public List<K> keys();

    /**
     * Returns a List view of the values contained in this map.
     *
     * @return a List of all values currently in the map
     */
    public List<V> values();

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    public void clear();
}
```

---

## 3. Data Structure & Collision Strategy

### Internal Representation
- **Backing Storage**: An array of `Entry<K, V>` or parallel arrays `K[] keys` and `V[] values` with an `Entry<K, V>` object. Using an internal `Entry<K, V>` (or `Node<K, V>` wrapper) holding `K key` and `V value` cleanly supports `null` values while identifying empty slots by `entry == null`.
- **Capacity**: Always a power of two ($2^n$) to enable fast bitwise modulo operations (`hash & mask` where `mask = capacity - 1`).
- **Load Factor**: Default load factor of `0.50f` or `0.75f` (e.g. `0.70f` - `0.75f` is common for open addressing; `0.50f` offers minimal collisions and fast linear probes).
- **Hash Mixing Function**: OpenJDK-style hash mixing `(h ^ (h >>> 16))` to ensure higher bits contribute to lower index selection.

### Collision Resolution Strategy: Linear Probing with Shift Deletion (or Tombstone / Backward-Shift)
- **Insertion (`put`)**:
  1. Check if `size >= threshold`; if so, resize table to `capacity * 2` and rehash existing entries.
  2. Compute initial index: `idx = mixHash(key.hashCode()) & mask`.
  3. Probe consecutively (`idx = (idx + 1) & mask`) until:
     - An empty slot (`null`) is found: insert new entry, increment `size`, return `null`.
     - An entry with equal key (`key.equals(entry.key)`) is found: replace value, return old value.
- **Lookup (`get`)**:
  1. Compute initial index: `idx = mixHash(key.hashCode()) & mask`.
  2. Probe consecutively until:
     - An empty slot (`null`) is found: key is not present, return `null`.
     - Matching key is found: return `entry.value`.
- **Removal (`remove`)**:
  1. Compute initial index: `idx = mixHash(key.hashCode()) & mask`.
  2. Probe until matching key is found or empty slot reached.
  3. If found:
     - Store old value.
     - Decrement `size`.
     - Delete slot and perform **backward shift / cluster cleanup** (realign displaced entries in the cluster until a natural empty slot is found) to maintain probing invariants without accumulating tombstones.
     - Return old value.
  4. If not found, return `null`.
- **Null Value Distinguisher**:
  - Since `null` values are allowed, `containsKey` or internal slot check verifies presence; `put` returning `null` could mean either previously mapped to `null` or key was not present. (Standard Java Map semantics for `put`).

---

## 4. Implementation Plan

### Step 1: Implementation of `FastHashMap<K, V>`
- Located at: `demo8/src/main/java/org/jugsaxony/demo8/FastHashMap.java`
- Implement internal `Entry<K, V>` class.
- Implement capacity calculation, resizing (`resize()`), hash mixing, probing loop, deletion cleanup (`freePositionAndAdjustArray` / shift-rehash), `keys()`, `values()`, and `clear()`.

### Step 2: Comprehensive Unit Tests (JUnit 5)
- Located at: `demo8/src/test/java/org/jugsaxony/demo8/FastHashMapTest.java`
- Test categories:
  1. **Basic Operations**: `put`, `get`, `remove`, `size`, `clear` on simple types (String, Integer).
  2. **Null Handling**:
     - Verify `NullPointerException` thrown on `put(null, value)`, `get(null)`, `remove(null)`.
     - Verify `null` values are stored, retrieved, and replaced properly (`put("key", null)`, `get("key") == null`, `size() == 1`).
  3. **Collision & Probing**:
     - Custom keys with forced hash collisions to verify linear probing chain insertion, updates, lookups, and removals.
  4. **Removal Cluster Shift Invariants**:
     - Verify deleting elements in the middle/start/end of collision clusters does not break lookups for subsequent entries.
  5. **Dynamic Growth & Rehashing (Unbounded behavior)**:
     - Insert large numbers of elements (e.g. 10,000+ entries) to verify growth across multiple resizing thresholds.
     - Verify all keys and values remain accessible after multiple resizes.
  6. **Keys & Values Collection**:
     - Verify `keys()` and `values()` return exact elements, count matches `size()`, and reflect removals/updates.
  7. **Clear Operation**:
     - Verify map state after `clear()` (size == 0, `get()` returns null, `keys()` is empty).
  8. **Edge Cases**:
     - Replacing values for identical keys.
     - Removing non-existent keys.
     - Operations on empty map.
     - Re-inserting previously removed keys.

### Step 3: Verification
- Execute `mvn clean test` from `demo8` to confirm all tests pass.
