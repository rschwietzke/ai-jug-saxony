# FastHashMap — Specification & Implementation Plan

## 1. Overview

| Item | Value |
|---|---|
| Class name | `FastHashMap<K, V>` |
| Package | `org.jugsaxony.demo2` |
| Maven module | `demo2` (subproject of `ai-jug-saxony-parent`) |
| Java version | 21 |
| Test framework | JUnit 6 (JUnit Jupiter), plain JUnit assertions (no AssertJ) |
| Thread safety | **Not thread-safe** |
| Null keys | **Not allowed** — `NullPointerException` |
| Null values | **Allowed** |
| Collision strategy | Open addressing with linear probing, power-of-two capacity, tombstone-free deletion via backward-shift (cluster reinsertion) |
| Sizing | **Resizable, unbounded** — grows when load factor is reached |
| Relation to LRUClockMap | None — `FastHashMap` is an independent, general-purpose open-addressing map |

## 2. API Contract

```java
public FastHashMap()
```
Creates an empty map with default initial capacity (16) and load factor (0.75).

```java
public V get(final K key)
```
Returns the value mapped to `key`, or `null` if absent. A return value of `null` is ambiguous because `null` values are permitted (same semantics as `java.util.HashMap#get`). Average time O(1). Throws `NullPointerException` if `key` is null.

```java
public V put(final K key, final V value)
```
Associates `value` with `key`. Returns the previous value, or `null` if there was no mapping (or the previous mapping was `null`). `value` may be `null`. Triggers a resize when the insertion would exceed the load factor. Average time O(1) amortized. Throws `NullPointerException` if `key` is null.

```java
public V remove(final K key)
```
Removes the mapping for `key` and returns the previous value, or `null` if absent. Deletion uses backward-shift cluster repair (no tombstones), keeping subsequent lookups fast. Throws `NullPointerException` if `key` is null.

```java
public int size()
```
Returns the number of mappings.

```java
public List<K> keys()
```
Returns a new `List<K>` containing all keys in internal slot order (unspecified; not sorted, not insertion order). Mutating the returned list does not affect the map. Empty map → empty list.

```java
public List<V> values()
```
Returns a new `List<V>` containing all values in the same internal order as `keys()`; `keys().get(i)` corresponds to `values().get(i)`. May contain `null` elements. Mutating the returned list does not affect the map.

```java
public void clear()
```
Removes all mappings, resets size to 0, and resets capacity to the default (16). After `clear()`, `size() == 0` and `keys()`/`values()` are empty. The map remains fully usable afterwards.

## 3. Semantics Summary

- **No null keys:** `get`, `put`, `remove` throw `NullPointerException` for null keys.
- **Null values allowed:** `put(k, null)` is valid; `get(k)` returning `null` means either absent or mapped to `null`.
- **Duplicate keys:** `put` on an existing key replaces the value and returns the old one; `size` unchanged.
- **Missing keys:** `get`/`remove` return `null`; `size` unchanged.
- **Unbounded growth:** capacity doubles when `size + 1 > capacity × LOAD_FACTOR`; capacity is always a power of two.
- **Iteration order:** `keys()`/`values()` order is unspecified and may change after any mutation.
- **Concurrent modification:** not detected; views are copies, not live views.
- **Thread safety:** none. External synchronization is required for multi-threaded use.

## 4. Implementation Plan

### 4.1 Internal structure

```java
public class FastHashMap<K, V> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private K[] keys;      // parallel array: keys[i] null means empty slot
    private V[] values;    // parallel array: values[i]
    private int size;
    private int mask;      // keys.length - 1
}
```

Rationale for parallel `keys`/`values` arrays over an `Entry[]`: better cache locality on key scans (only keys are touched while probing), no per-entry object allocation.

### 4.2 Hashing

- Spread function (same as `java.util.HashMap`): `h = key.hashCode(); h ^ (h >>> 16)`
- Index: `mask & h` where `mask = capacity - 1` (capacity always a power of two).

### 4.3 Core operations

| Method | Algorithm |
|---|---|
| `get` | Probe from `index(hash)` while `keys[i] != null`; return `values[i]` on match, `null` on empty slot. |
| `put` | Probe; if key found, replace value and return old. If empty slot found, insert; `size++`; if `size > capacity × LOAD_FACTOR`, `resize(capacity × 2)` rehashing all entries. Return `null`. |
| `remove` | Probe to find key; capture old value; then backward-shift: walk forward through the cluster; for each entry whose home index lies outside the gap-to-entry probe range, move it into the gap and advance the gap. `size--`. |
| `resize` | Allocate arrays of double capacity; reinsert every non-null key at its new probe position. |
| `clear` | Replace arrays with fresh default-capacity arrays; `size = 0`. |
| `keys`/`values` | Single pass over the backing arrays, collecting non-null slots into a pre-sized `ArrayList`. |

### 4.4 Generic-array creation

```java
@SuppressWarnings("unchecked")
private static <T> T[] newArray(int length) { return (T[]) new Object[length]; }
```

### 4.5 Project layout

```
demo2/
├── pom.xml                                    (fixed: artifactId demo2)
├── doc/
│   └── FastHashMap-spec.md                    (this document)
└── src/
    ├── main/java/org/jugsaxony/demo2/
    │   ├── LRUClockMap.java                   (existing)
    │   └── FastHashMap.java                   (new)
    └── test/java/org/jugsaxony/demo2/
        └── FastHashMapTest.java               (new)
```

Parent `pom.xml` gains `<module>demo2</module>` and a `dependencyManagement` entry for the `demo2` artifact.

## 5. Test Plan (`FastHashMapTest`, JUnit 6, plain assertions)

**Constructor / initial state**
- `newInstanceIsEmpty`: `size() == 0`, `keys()` empty, `values()` empty.

**Null-key handling**
- `putNullKeyThrows`, `getNullKeyThrows`, `removeNullKeyThrows` → `assertThrows(NullPointerException.class, …)`.

**Null-value handling**
- `putNullValueIsStored`: `put("a", null)` returns `null`, `get("a")` returns `null`, `size() == 1`.
- `nullValueAppearsInValues`: `values()` contains `null`.
- `replaceValueWithNull`: overwriting a real value with `null` returns the old value.

**put / get basics**
- `putReturnsNullForNewKey`
- `putReturnsOldValueOnReplace`; size unchanged.
- `getAbsentKeyReturnsNull`
- `getAfterPut` for several entries.
- `sizeTracksPuts`.

**remove**
- `removeExistingKeyReturnsOldValue`; subsequent `get` returns `null`; `size` decremented.
- `removeAbsentKeyReturnsNull`; size unchanged.
- `removeDoesNotBreakProbeChain` — critical: insert colliding keys (`FixedHashKey`), remove the middle entry, verify the remaining entries are still reachable via `get`.
- `removeThenReinsert`.

**Collision handling**
- `collisionsAreHandled`: 10+ keys with identical forced hash codes; all retrievable; `keys()` contains all; `size` correct.
- `wrapAroundProbing`: fill map near capacity so probing wraps past the end of the array.

**Resize**
- `resizePreservesAllEntries`: insert 100 entries, verify all retrievable and `size == 100` (forces multiple resizes from capacity 16).
- `resizeWithCollisions`: same with forced-collision keys.
- `resizeManyEntries`: 1,000 distinct keys, all present afterwards.

**clear**
- `clearEmptiesMap`: size 0, `keys`/`values` empty, `get` returns `null` for previously present keys.
- `mapIsReusableAfterClear`: put/get works normally after `clear()`.

**keys / values**
- `keysContainsAllInsertedKeys`: sizes match; every inserted key contained.
- `valuesContainsAllInsertedValues`.
- `keysAndValuesAreConsistent`: for each index `i`, `get(keys().get(i))` equals `values().get(i)` (non-null-value case).
- `mutatingReturnedListsDoesNotAffectMap`.
- `keysValuesOnEmptyMapReturnEmptyLists`.
- Order is unspecified: assertions use set-equality/containment, never ordering.

**Stress / reference comparison**
- `randomizedOperationSequence`: deterministic (seeded) pseudo-random mix of put/get/remove/clear against a `java.util.HashMap` reference; identical observable results (`get`, `size`) for every operation.

**Test helpers**
- `FixedHashKey`: class with forced constant `hashCode()` and proper `equals` — forces collisions deterministically.
- Deterministic `java.util.Random(42)` for the randomized test.

## 6. Decisions

1. **Storage layout:** parallel `K[]`/`V[]` arrays — simpler with generics, good cache locality during probing.
2. **Initial capacity:** 16 (matches `HashMap` convention).
3. **Load factor:** 0.75 — keeps probe chains short while avoiding excessive memory; matches JDK conventions.
4. **Deletion:** backward-shift (no tombstones) — lookup stays O(1)-ish even after many removals; no periodic rehash needed.
5. **Shrink on remove:** not implemented (avoids thrashing); `clear()` resets capacity.
6. **No relation to LRUClockMap:** independent implementation; no shared code.
7. **POM fix required:** `demo2/pom.xml` previously declared `artifactId`/`name` as `demo1` — corrected to `demo2`, and `demo2` added to the parent `<modules>`.
