# FastHashMap Specification and Implementation Plan

## 1. Scope

`FastHashMap<K, V>` is a general-purpose, dynamically growing hash map for the
`demo3` Maven subproject. It uses open addressing and is independent of the
existing `LRUClockMap` implementation.

| Property | Decision |
|---|---|
| Package | `org.jugsaxony.demo3` |
| Java version | 21 |
| Test framework | JUnit 6 (JUnit Jupiter) |
| Thread safety | Not thread-safe |
| Null keys | Not supported |
| Null values | Supported |
| Collision handling | Linear probing with backward-shift deletion |
| Capacity | Grows automatically; no application-level maximum |
| Ordering | Unspecified |

"Unbounded" means that the map has no configured fixed capacity and resizes as
entries are added. It remains subject to Java's `int`-indexed array limits,
available heap memory, and `OutOfMemoryError`.

## 2. Public API

The implementation must provide this class and these exact public operations:

```java
package org.jugsaxony.demo3;

import java.util.List;

public final class FastHashMap<K, V> {
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

The class does not need to implement `java.util.Map`; only the API above is in
scope.

## 3. Behavioral Contract

### 3.1 Key equality and mutation

- Keys are compared using `equals` and located using `hashCode`, following the
  normal Java hash-map contract.
- Distinct key objects that are equal represent the same mapping.
- A key's `hashCode` and equality-relevant state must not change while the key
  is stored. Behavior after such a mutation is unspecified, as with standard
  hash-based collections.

### 3.2 Constructor

`public FastHashMap()` creates an empty map with `size() == 0`. The initial
backing capacity is 16 entries, and the resize load factor is 0.75.

### 3.3 `get`

`public V get(final K key)`:

- Returns the value associated with an equal key.
- Returns `null` if the key is absent.
- Also returns `null` if the key is present and mapped to `null`; the public API
  intentionally cannot distinguish these two cases.
- Throws `NullPointerException` when `key` is `null`.
- Does not modify the map.

### 3.4 `put`

`public V put(final K key, final V value)`:

- Adds a new mapping when no equal key is present.
- Replaces the value when an equal key is already present without changing the
  map's size.
- Returns the previous value for a replacement.
- Returns `null` for a new mapping, or when the previous value was `null`.
- Accepts a `null` value.
- Throws `NullPointerException` when `key` is `null`, without modifying the map.
- Grows and rehashes the backing arrays when another insertion would exceed
  the configured load threshold.

### 3.5 `remove`

`public V remove(final K key)`:

- Removes the mapping for an equal key when present.
- Returns the removed value.
- Returns `null` if the key was absent or its removed value was `null`.
- Decrements the size exactly once when a mapping is removed, including a
  mapping whose value is `null`.
- Throws `NullPointerException` when `key` is `null`, without modifying the map.

### 3.6 `size`

`public int size()` returns the number of mappings currently stored. A mapping
with a `null` value counts as one mapping. The operation is constant time.

### 3.7 `keys` and `values`

`public List<K> keys()` and `public List<V> values()`:

- Return newly allocated snapshots, not live views backed by the map.
- Return empty mutable lists when the map is empty.
- Include exactly one element for every current mapping.
- `keys()` never contains `null`; `values()` may contain `null`.
- Enumerate occupied slots in backing-array index order. This order is an
  implementation detail and is not a stable API guarantee.
- Preserve key/value correspondence for calls made against the same unchanged
  map state: the key at index `i` in `keys()` is mapped to the value at index
  `i` in `values()`.
- May be modified by the caller without changing the map.

### 3.8 `clear`

`public void clear()`:

- Removes every mapping and sets the size to zero.
- Releases references to all stored keys and values so they can be garbage
  collected.
- Restores fresh backing arrays at the default capacity of 16, avoiding
  retention of an unusually large table.
- Leaves the map ready for reuse.
- Is a no-op, apart from resetting storage, when the map is already empty.

### 3.9 Thread safety

No operation is thread-safe. Concurrent access requires external
synchronization if at least one thread can mutate the map. The implementation
does not provide fail-fast behavior or concurrent-modification detection.

## 4. Data Structure and Algorithms

### 4.1 Storage

Use two parallel `Object[]` arrays:

- `keys[index] == null` marks an unused slot. This is possible because null
  keys are forbidden.
- `values[index]` stores the value belonging to `keys[index]` and may itself be
  null.
- A separate `int size` tracks occupied slots.
- Array length is always a power of two, allowing index calculation with
  `hash & (capacity - 1)`.

Parallel arrays avoid allocating one wrapper object per mapping and permit key
probes without dereferencing entry objects.

### 4.2 Hash spreading and probing

- Spread a key hash with `hash ^ (hash >>> 16)` so high hash bits influence the
  low bits used by a power-of-two table.
- Compute the initial slot as `spreadHash & (capacity - 1)`.
- Resolve collisions with linear probing, advancing with
  `(index + 1) & (capacity - 1)`.
- Stop an unsuccessful search at the first unused key slot.

Expected `get`, `put`, and `remove` time is O(1). Their worst case is O(n).
Resizing is O(n), making insertion O(1) amortized under normal hash
distributions.

### 4.3 Insertion and replacement

Probe from the key's initial slot. If an equal key is found, replace only its
value and return the previous value. If an unused slot is found, ensure that
inserting a mapping will not exceed the 0.75 load factor, resize first if
necessary, then locate the slot in the current table and insert the mapping.

The implementation must check for an existing key before deciding that a
resize is needed so replacing a value does not cause unnecessary growth.

### 4.4 Resizing

- Double capacity when a new mapping would exceed the load threshold.
- Allocate new parallel arrays and reinsert every occupied slot according to
  the new mask; entries cannot be copied to the same raw indices.
- Do not shrink on individual removals, which avoids resize thrashing.
- Guard capacity arithmetic so it cannot silently overflow. If no larger Java
  array can be represented or allocated, fail rather than corrupting map
  state.

### 4.5 Deletion

Use backward-shift deletion rather than tombstones:

1. Find the key and retain its old value.
2. Treat its slot as a gap.
3. Scan forward through the remaining collision cluster.
4. Move an entry into the gap when the gap lies on that entry's probe path.
5. Continue with the moved entry's old slot as the new gap.
6. Clear the final gap and decrement `size` once.

This preserves reachability of collided entries, avoids a special tombstone
sentinel, and prevents deleted slots from degrading future probes.

## 5. Maven Subproject Plan

The intended source layout is:

```text
demo3/
|-- pom.xml
|-- doc/
|   `-- FastHashMap-spec.md
`-- src/
    |-- main/java/org/jugsaxony/demo3/
    |   |-- FastHashMap.java
    |   `-- LRUClockMap.java
    `-- test/java/org/jugsaxony/demo3/
        `-- FastHashMapTest.java
```

During implementation:

1. Correct `demo3/pom.xml`, which currently declares `artifactId`, name, and
   description for `demo2`; it must identify `demo3`.
2. Keep Java 21 and JUnit 6 inherited from the parent POM.
3. Add `demo3` to the parent reactor's `<modules>` list.
4. Add `org.jugsaxony:demo3` to parent dependency management if the parent
   continues to list each subproject there.
5. Leave the existing `demo1`, `demo2`, and `LRUClockMap` implementations
   unchanged except where reactor configuration necessarily includes `demo3`.

## 6. Test Plan

Tests will use JUnit 6 and exercise only the public API unless a focused
structural test is necessary. Assertions must not rely on unspecified
enumeration order.

### 6.1 Initial state

- A new map has size zero.
- `keys()` and `values()` return empty lists.
- `get` and `remove` for an absent non-null key return null.

### 6.2 Null contracts

- `get(null)`, `put(null, value)`, and `remove(null)` each throw
  `NullPointerException`.
- Rejected null-key operations leave existing contents and size unchanged.
- A null value can be inserted, retrieved, replaced, listed, and removed.
- A mapping to null contributes to size and its key appears in `keys()`.

### 6.3 Basic insertion and lookup

- Inserting a new key returns null and increments size.
- Multiple distinct keys retain their respective values.
- Looking up an equal but non-identical key finds the mapping.
- Replacing an existing value returns the old value and leaves size unchanged.
- Replacing a null value and replacing a non-null value with null return the
  specified results.
- Negative, zero, and extreme hash codes are handled correctly.

### 6.4 Collision handling

- A test key type with a controlled constant hash creates a long collision
  cluster; every mapping remains retrievable.
- Equal hash codes with unequal keys remain separate mappings.
- Enough colliding keys are inserted to force both probing and resizing.
- A controlled hash near the final table slot verifies probing across the
  array boundary.

### 6.5 Removal and cluster repair

- Removing an existing key returns its old value, decrements size, and makes
  the key unreachable.
- Removing absent keys leaves contents and size unchanged.
- Removing a null-valued mapping updates size correctly.
- Removing the first, middle, and last entries of collision clusters preserves
  all remaining entries.
- Removing from a cluster that wraps around the array boundary preserves all
  remaining entries.
- Repeated removal of the same key returns null after the first removal.
- A removed key can be inserted again.

### 6.6 Growth and resizing

- Inserting enough distinct mappings to cross several resize thresholds
  preserves every mapping and the exact size.
- Resizing preserves null values.
- Resizing preserves heavily colliding mappings.
- Replacing an existing mapping near a threshold does not change size or lose
  entries.

### 6.7 Snapshot lists

- `keys()` contains every key exactly once.
- `values()` contains every value with the correct multiplicity, including
  duplicate and null values.
- Key and value snapshots have the same size and corresponding indices for an
  unchanged map.
- Modifying or clearing a returned list does not affect map contents.
- Previously returned snapshots do not change after later map mutations.
- Fresh calls after insertions, removals, and clear reflect current state.

### 6.8 Clear and reuse

- Clearing a populated map removes all mappings and returns empty snapshots.
- Clearing a map containing null values behaves identically.
- Clearing an already empty map is safe.
- The map accepts and retrieves new mappings after clear.
- Reuse after clearing a map that previously grew through several resizes is
  verified.

### 6.9 Deterministic differential test

Run a seeded randomized sequence of `put`, `get`, `remove`, and `clear`
operations against both `FastHashMap` and `java.util.HashMap`. Use non-null keys
and a mixture of null and non-null values. After operations, compare return
values, size, key membership, and each key's current value. The fixed seed
makes failures reproducible.

## 7. Implementation Sequence

1. Correct and register the `demo3` Maven module.
2. Add `FastHashMap<K, V>` with constructor, storage, hash spreading, and probe
   helpers.
3. Implement `get`, `put`, growth, and rehashing.
4. Implement tombstone-free `remove` with backward-shift cluster repair.
5. Implement `size`, snapshot-producing `keys` and `values`, and `clear`.
6. Add focused JUnit 6 tests for contracts, collisions, wrap-around, removal,
   resizing, snapshots, and reuse.
7. Add the deterministic differential test against `java.util.HashMap`.
8. Run the module test suite and the full Maven reactor test suite, then fix
   any regressions without changing the specified API.

## 8. Acceptance Criteria

- The exact public API in section 2 is available in
  `org.jugsaxony.demo3.FastHashMap`.
- All behavioral contracts in section 3 are covered by tests and pass on Java
  21 with JUnit 6.
- Null keys are rejected and null values work throughout the API.
- Collisions, wrap-around probing, deletion, and multiple resizes do not lose
  or corrupt mappings.
- The map grows automatically and has no configured fixed entry limit.
- Returned key and value lists are independent snapshots.
- Existing subprojects and `LRUClockMap` continue to build and test.
