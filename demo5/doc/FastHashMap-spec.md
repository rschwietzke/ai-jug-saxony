# FastHashMap — Specification & Implementation Plan

- **Project / folder:** `demo5`
- **Package:** `org.jugsaxony.demo5`
- **Java:** 21
- **Test framework:** JUnit 5.10.0 (junit-jupiter; confirmed — keep existing pom version)
- **Build:** Maven (subproject, own `pom.xml`, artifactId `demo5-fasthashmap`)
- **Status:** Spec draft — implementation **not** started yet

---

## 1. Goal

Implement a fast, unbounded hash map with **open addressing** (no separate
chaining), optimized for speed and cache friendliness, in the `demo5` Maven
module. It is a standalone implementation with no dependencies on
`java.util.HashMap` internals (but it may use standard JDK collection types for
its public `List` return types).

## 2. Requirements

### 2.1 Functional / API contract

The class is `FastHashMap<K, V>` with the following mandatory public methods:

| Method                          | Returns         | Semantics                                                                 |
|---------------------------------|-----------------|---------------------------------------------------------------------------|
| `public FastHashMap()`          | —               | No-arg constructor, unbounded (grows automatically)                       |
| `public V get(final K key)`     | `V`             | Value for `key`, or `null` if absent                                      |
| `public V put(final K key, final V value)` | `V` | Associates `key`→`value`; returns previous value or `null` if new          |
| `public V remove(final K key)`  | `V`             | Removes mapping; returns previous value or `null` if absent               |
| `public int size()`             | `int`           | Number of key→value mappings                                              |
| `public List<K> keys()`         | `List<K>`       | All keys currently in the map (order **not** guaranteed)                  |
| `public List<V> values()`       | `List<V>`       | All values currently in the map (order **not** guaranteed)                |
| `public void clear()`           | —               | Removes all mappings                                                      |

### 2.2 Constraints

- **Not thread-safe.** No synchronization; concurrent access from multiple
  threads is undefined behavior.
- **No `null` keys.** Passing `null` to `get`, `put`, or `remove` throws
  `NullPointerException` (`Objects.requireNonNull`).
- **`null` values are allowed.** `put(key, null)` is legal. Consequently a
  `null` result from `get`/`put`/`remove` is ambiguous (absent vs. present with
  `null` value); this is documented and accepted (same trade-off as
  `java.util.HashMap`).
- **Unbounded.** No maximum size, no eviction. The backing storage grows
  automatically via rehash when the load factor is exceeded.
- **Collision strategy:** free to choose → see §4.

## 3. Terminology

- **Open addressing** (a.k.a. closed hashing): all entries live directly in one
  backing array; collisions are resolved by probing to other slots.
- **Open hashing / separate chaining** (a.k.a. closed addressing): each bucket
  holds a linked list of entries. *Not used here.*
- **Load factor:** `size / capacity`. Threshold that triggers growth/rehash.

## 4. Design Decisions

### 4.1 Collision resolution: linear probing (confirmed)

**Confirmed by owner:** **linear probing** on a power-of-two sized array.

- Bucket index = `spread(key.hashCode()) & (capacity - 1)` (mask instead of
  modulo, requires power-of-two capacity).
- On collision, step to the next slot: `idx = (idx + 1) & (capacity - 1)`.
- **Why:** best cache locality (contiguous array, no pointer chasing), simplest
  code, and consistent with the sibling `LRUClockMap` in this module which
  already uses the same approach and hash mixing.
- Alternative considered: separate chaining with per-bucket linked lists. More
  robust against clustering and simpler `remove`, but worse cache behavior and
  more allocation. Rejected for a "fast" map.

### 4.2 Hash spread

Use the OpenJDK-style mixer `h ^ (h >>> 16)` to improve distribution of low
bits, mirroring `LRUClockMap.mixHash(...)` for consistency within this module.

### 4.3 Growth / rehash

- Initial capacity: a small power of two (e.g. `16`).
- Load factor threshold: e.g. `0.75` (tunable constant, not exposed).
- When `size` exceeds `capacity * loadFactor`, allocate a new array of
  `2 * capacity` and re-insert all entries. Old array becomes garbage.
- Power-of-two capacities keep the `& (capacity-1)` index fast path valid after
  every resize.

### 4.4 Removal (open addressing caveat)

Naive deletion (just null the slot) breaks probe chains and makes subsequent
lookups miss. Therefore `remove` must repair the chain — either by shifting
following entries back (as `LRUClockMap.freePositionAndAdjustArray` does) or by
using tombstone markers.

**Proposed:** repair by shifting/re-inserting subsequent entries in the same
cluster (backward/forward shift). This keeps lookups simple (no tombstone
skipping) and keeps memory tight. See plan §6.

### 4.5 Internal representation

- `Entry<K,V>`: minimal holder with `key` and `value` (`key` final).
- A single `Entry[]` array; empty slot = `null`.
- `int size`, `int capacity`, `int mask` (= `capacity - 1`), `float loadFactor`.
- No per-entry next pointers, no sentinel/tombstone objects when using shift
  removal.

### 4.6 Return types `keys()` / `values()`

Return a freshly created `java.util.ArrayList` (or `Arrays.asList` on a snapshot
array) of the current keys/values. Order is unspecified — do **not** rely on
insertion order. Required because the map does not maintain order and callers
must not get a live view into internal storage.

## 5. Behavioral Contract / Guarantees

1. `put(key, v)` returns the previous value (possibly `null`) and never returns
   `null` to signal success — matching `HashMap` semantics.
2. `get(key)` returns `null` for an absent key and for a key mapped to `null`.
3. `remove(key)` returns the previous value (possibly `null`) or `null` if
   absent.
4. `size()` returns the mapping count, `0` right after construction or `clear()`.
5. After `clear()`, the map behaves like a freshly constructed one (storage may
   be reused or dropped; plan: drop/replace array, reset `size`).
6. `keys()` and `values()` reflect the current state; the returned lists are
   independent snapshots (mutating them does not affect the map).
7. Equality of keys is determined by `key.equals(other)` (with the additional
   requirement `Objects.hashCode`/`hashCode()` consistency — i.e. keys must
   obey the `equals`/`hashCode` contract).
8. Iteration order of `keys()`/`values()` is undefined and may differ between
   calls.

## 6. Implementation Plan

### Step 1 — Project scaffolding
- `demo5/pom.xml`: set Java 21 compiler, keep JUnit **5.10.0** junit-jupiter test
  dependency, surefire config. **Rename `artifactId` from `demo4-fasthashmap` to
  `demo5-fasthashmap`** (confirmed).
- Ensure package dir `org/jugsaxony/demo5/` exists under
  `src/main/java` and `src/test/java`.

### Step 2 — Core class `FastHashMap<K,V>`
File: `demo5/src/main/java/org/jugsaxony/demo5/FastHashMap.java`

- Fields: `Entry<K,V>[] data`, `int size`, `int capacity`, `int mask`,
  `float loadFactor`, constants `DEFAULT_CAPACITY`, `LOAD_FACTOR`.
- Nested `static final class Entry<K,V>` with `final K key; V value;`.
- Helpers:
  - `spread(int h)` → `h ^ (h >>> 16)`
  - `index(K key)` → `spread(key.hashCode()) & mask`
  - `nextPowerOfTwo` / capacity computation (borrow pattern from
    `LRUClockMap.arraySize`), with overflow guard.
  - `resize()` → double capacity, rehash all entries.
  - `shiftBack(int hole)` → repair probe chain after removal.
- Public API implementing §2.1 exactly (signatures must match byte-for-byte).

### Step 3 — Tests
File: `demo5/src/test/java/org/jugsaxony/demo5/FastHashMapTest.java`

Comprehensive JUnit 6 test suite (see §7).

### Step 4 — Build & verify
- `mvn -q test` (within `demo5`). All tests green.
- Optional: compare behavior against `java.util.HashMap` as a reference
  oracle for randomized property tests.

## 7. Test Plan

| Area                    | Cases                                                                                     |
|-------------------------|-------------------------------------------------------------------------------------------|
| Basic put/get           | single entry, overwrite returns old value, get returns updated value                      |
| Null keys               | `get/put/remove(null)` throw `NullPointerException`                                        |
| Null values             | `put(k, null)`, `get(k)` → `null`, `remove(k)` → `null`, `containsMapping` via keys        |
| Remove                  | remove existing (value returned), remove absent (null), remove-then-get → null, chains intact after many removes |
| Size                    | 0 initially, increments/decrements correctly, unaffected by overwrite                      |
| Clear                   | `clear()` → size 0, get → null, keys/values empty; map usable again                       |
| Growth / rehash         | insert well beyond initial capacity (e.g. 10_000 entries), all keys still retrievable, size correct |
| Collisions              | keys with identical `hashCode()` (same bucket) still stored/retrieved/removed correctly   |
| keys()/values()         | contents match entries, sizes match `size()`, independent snapshot (mutation doesn't affect map) |
| Equals/hashCode         | custom key class with proper equals/hashCode; verify lookup uses equals                   |
| Randomized (oracle)     | random op sequences; result compared to `java.util.HashMap` (if acceptable to use as test oracle) |

## 8. Resolved Decisions

| # | Question | Resolution |
|---|----------|-----------|
| 1 | Test framework | **JUnit 5.10.0** (keep existing `junit-jupiter` pom version) |
| 2 | Maven artifactId | **`demo5-fasthashmap`** (rename from `demo4-fasthashmap`) |
| 3 | Collision strategy | **Linear probing**, power-of-two array |
| 4 | Remove repair strategy | **Shift-based repair** (default, as proposed §4.4) |
| 5 | Initial capacity / load factor | **16 / 0.75** (default, as proposed §4.3) |
