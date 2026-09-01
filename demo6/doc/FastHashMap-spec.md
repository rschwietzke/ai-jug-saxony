# FastHashMap — Specification & Implementation Plan

Status: **draft, not implemented yet**
Target package: `org.jugsaxony.demo6`
Build: Maven, Java 21, JUnit 6

---

## 1. Goal

A standalone, general-purpose hash map `FastHashMap<K, V>` with a small, fixed public API.
It is *not* a `java.util.Map` implementation and does not try to be one — it is a lean,
cache-friendly map optimized for speed, in the same spirit as the existing
[LRUClockMap](../src/main/java/org/jugsaxony/demo6/LRUClockMap.java) in this package.

### Required public API

```java
public FastHashMap()
public V get( final K key )
public V put( final K key, final V value )
public V remove( final K key )
public int size()
public List<K> keys()
public List<V> values()
public void clear()
```

These signatures are the contract and must not change. Additional *package-private*
members for testability are allowed (see §7.1).

### Requirements (given)

| # | Requirement | Consequence |
|---|-------------|-------------|
| R1 | Not thread-safe | No synchronization, no volatile, no atomics. Concurrent use is undefined behaviour, not detected. |
| R2 | Collision strategy free to choose | See §3 — open addressing with linear probing. |
| R3 | Unbounded | No max size, no eviction. Grows until memory / array-size limits (§4.6). |
| R4 | No `null` keys | `get`/`put`/`remove` throw `NullPointerException` on a `null` key. |
| R5 | `null` values allowed | `put(k, null)` is a legal mapping that counts towards `size()`. |
| R6 | Java 21, Maven, JUnit 6, package `org.jugsaxony.demo6` | Build setup per §8. |

### Non-goals

* No `Map` / `Iterable` interface implementation, no entrySet, no views.
* No thread-safety, no fail-fast modification detection.
* No serialization, no cloning, no `equals`/`hashCode` on the map itself.
* No ordering guarantee (insertion order is *not* preserved).

---

## 2. Terminology note

"Open hashing" in the classic textbook sense means *separate chaining*. Since R2
explicitly leaves the collision strategy open, this spec picks **open addressing
(closed hashing) with linear probing** instead, because it is measurably faster for
the typical workload: one flat array pair, no per-entry `Entry` object, no pointer
chasing, excellent CPU-cache locality. If separate chaining was actually intended,
this is a decision point — see the open questions in §10 (Q6).

---

## 3. Data structure

### 3.1 Layout

Two parallel `Object[]` arrays instead of one array of wrapper objects:

```
Object[] keys      // keys[i] == null  <=>  slot i is free
Object[] values    // values[i] is the value belonging to keys[i]; may be null
int      size      // number of live entries
int      mask      // keys.length - 1
```

Rationale for parallel arrays over a `Wrapper[]` (as used in `LRUClockMap`):

* Zero allocation per entry — `put` of an existing key allocates nothing, and inserting
  a new key allocates nothing either (only a resize allocates).
* Lookups touch only the `keys` array while probing; the `values` array is touched once
  on a hit. That halves the cache footprint of the hot probing loop.
* Handles R5 for free: **presence is decided by `keys[i] != null`, never by the value.**
  Because `null` keys are forbidden (R4), a `null` key slot is an unambiguous "free" marker,
  so `null` values need no sentinel, no boxing, no `Optional`.

Capacity is always a **power of two**, so wrapping a probe around the end of the table is
`(ptr + 1) & mask` (no modulo). `shift` (with `capacity == 1 << shift`) is kept alongside `mask`
for the hash, see §3.3.

### 3.2 Constants

| Constant | Value | Reason |
|----------|-------|--------|
| `DEFAULT_CAPACITY` | `16` | Small enough for tiny maps, ≥ 2 resizes cheaper than starting at 4. |
| `LOAD_FACTOR` | `0.5f` | Linear probing degrades sharply above ~0.7. 0.5 keeps average probe length ≈ 1.5 and matches `LRUClockMap`. Memory cost is accepted deliberately — this is a *Fast*HashMap. |
| `MAXIMUM_CAPACITY` | `1 << 30` | Largest power-of-two `int` array size. |

### 3.3 Hashing — Fibonacci, *not* a xor-fold

```java
private static final long FIBONACCI = 0x9E3779B97F4A7C15L;

int homeSlot( final Object key )
{
    return (int) ((key.hashCode() * FIBONACCI) >>> (64 - shift));   // capacity == 1 << shift
}
```

Multiply by the golden ratio, keep the **top** bits of the product. One multiplication and one
shift. Taking the top bits is the point — the low bits of a product are barely mixed, so a
`& mask` on the product would be worthless.

**This replaces the originally specified `h ^ (h >>> 16)` fold, and the reason is worth recording.**
The fold (OpenJDK `HashMap`, `LRUClockMap`) maps consecutive keys to consecutive slots. For a
chaining map that is ideal. For linear probing it is poison: insert `0..999_999` as `Integer` keys
and every one of them lands in the low part of the table, so the occupied slots form **one single
contiguous cluster about a million slots long**, while the upper half of the table stays empty.
Lookups are still fine (every key sits on its home slot), but chain repair on removal has to walk
the run behind the freed slot — so removing those keys in order becomes O(n²). The volume test
(T39) turned that into an effectively hanging build: >5 minutes and still running, with the thread
dump parked in `freePositionAndAdjustArray`.

Fibonacci hashing spreads consecutive keys evenly across the whole table, so clusters stay short
no matter how regular the keys are. With it, T39 runs in ~0.5s.

Guarded by T40, which asserts that 100k sequential keys produce a longest cluster below 100 rather
than one run of 100k. That is a deterministic assertion about the structure, not a timing check.

`hashCode()` is called exactly **once** per public operation.

### 3.4 Key comparison

```java
if (k == key || k.equals(key))   // k is the stored key, key the probe key
```

Identity check first as a cheap fast path (interned strings, enums, cached boxes), then
`equals`. Note the stored key is the receiver, so a caller `equals` implementation that
misbehaves on foreign types is still called correctly.

**Precondition on keys:** `hashCode()` and `equals()` must be stable while the key is in the
map. Mutating a key after insertion corrupts the map (entry becomes unreachable). Documented,
not enforced — same contract as `java.util.HashMap`.

---

## 4. Algorithms

### 4.1 `get( K key )`

```
requireNonNull(key)
i = mixHash(key.hashCode()) & mask
loop:
    k = keys[i]
    if k == null            -> return null            // free slot: key cannot exist beyond
    if k == key || k.equals(key) -> return values[i]  // hit
    i = (i + 1) & mask                                // linear probe, wraps around
```

The loop terminates because the table is never full (§5, I5): there is always at least one
free slot, so the "free slot" exit is always reachable.

Optional micro-optimization (mirroring `LRUClockMap.get` / `expensiveGet`): keep the
first probe inline in `get` and move the probing loop into a private `expensiveGet` so the
hot method stays small enough for JIT inlining. Applied only if it does not hurt readability.

### 4.2 `put( K key, V value )`

```
requireNonNull(key)
if (size + 1) > threshold -> resize(capacity * 2)     // grow *before* inserting
i = mixHash(key.hashCode()) & mask
loop:
    k = keys[i]
    if k == null:                                     // insert
        keys[i] = key; values[i] = value; size++
        return null
    if k == key || k.equals(key):                     // update
        old = values[i]; values[i] = value
        return old
    i = (i + 1) & mask
```

Returns the previous value, or `null` if there was no mapping. **Ambiguity by design:**
`null` also comes back when the previous mapping was `key -> null`. See §6.

Growing before the insert (rather than after) means the insert never happens into a table
that is about to be thrown away, and the returned slot index stays valid.

### 4.3 `remove( K key )` — backward-shift deletion

Plain slot clearing would break probe chains, and tombstones would slowly poison an
unbounded, long-lived map (R3): with heavy churn the table fills with markers and probe
lengths grow without bound until a resize cleans up. So removal uses **backward-shift
deletion**, exactly like `LRUClockMap.freePositionAndAdjustArray`:

```
find slot i of key (probe as in get); if not found -> return null
old = values[i]
keys[i] = null; values[i] = null; size--

// repair the chain: re-place every entry in the contiguous run following i
j = i
loop:
    j = (j + 1) & mask
    k = keys[j]
    if k == null -> break                 // end of the cluster, chain is intact again
    v = values[j]
    keys[j] = null; values[j] = null      // lift it out ...
    reinsert(k, v)                        // ... and let it fall into its earliest free slot
return old
```

`reinsert` probes from the entry's home slot and drops it into the first free slot — no
key comparison needed, because duplicate keys cannot exist and the entry is guaranteed to
find a slot at or before its old position.

Properties: no tombstones, table occupancy always equals `size()`, and steady-state
add/remove churn costs stay flat forever. Cost is bounded by the length of the cluster
following the removed slot, which stays short at load factor 0.5.

### 4.4 `resize( int newCapacity )`

Allocate new `keys`/`values` arrays of `newCapacity`, set `mask = newCapacity - 1`, then
scan the old `keys` array and re-place every non-null entry with the same free-slot probe
as `reinsert`. `size` is unchanged. Threshold becomes `newCapacity * LOAD_FACTOR`.

Doubling only — capacity is always a power of two. There is **no shrink**: `remove` never
reduces capacity (an unbounded map that shrank on removal would thrash under churn).

### 4.5 `size()`, `keys()`, `values()`, `clear()`

* `size()` — returns the `size` field, O(1).
* `keys()` — new `ArrayList<K>` presized to `size`, filled by scanning slot 0..capacity-1 and
  collecting non-null keys. Snapshot, unique elements, never contains `null`.
* `values()` — same scan, collecting `values[i]` for every slot with a non-null key. Snapshot,
  **may contain `null` and duplicates**, `values().size() == size()`.
* Both are **independent snapshots**: mutating the returned list does not touch the map, and
  mutating the map afterwards does not touch the returned list.
* **Correspondence guarantee:** if the map is not modified between the two calls, `keys().get(i)`
  and `values().get(i)` belong to the same entry for all `i` (both scans walk the table in slot
  order). This is worth specifying because it makes the pair usable, and it is testable.
* Order is table order, i.e. deterministic for a given map state but **unspecified** to the
  caller — it depends on hashes, capacity and insertion/removal history. Tests must never
  assume insertion order.
* `clear()` — `Arrays.fill(keys, null)`, `Arrays.fill(values, null)`, `size = 0`. **Capacity is
  retained** (like `LRUClockMap.clear`): a cleared map is usually refilled, so keeping the
  table avoids a re-grow. Filling both arrays also drops the references, so no memory leak of
  keys/values. See Q3 in §10 if releasing the table is preferred instead.

### 4.6 Limits

At `MAXIMUM_CAPACITY` (2^30 slots) the map holds ~536M entries at load factor 0.5. A `put`
that would need to grow beyond that throws `IllegalStateException("Map is too large")`.
"Unbounded" (R3) means "no user-configured bound", not "beyond `int` array limits".

---

## 5. Invariants

These are stated so tests can assert them directly (via the test hooks in §7.1):

| # | Invariant |
|---|-----------|
| I1 | `keys[i] == null` ⟺ slot `i` is free. `values[i]` is `null` for every free slot. |
| I2 | `size` == number of slots with `keys[i] != null` (no tombstones, ever). |
| I3 | No two occupied slots hold equal keys. |
| I4 | For every occupied slot `i` with home slot `h`, all slots on the wrap-around path `h -> i` are occupied (probe chains have no gaps). This is what makes `get` correct and what backward-shift deletion preserves. |
| I5 | `size <= capacity * LOAD_FACTOR < capacity` — the table is never full, so every probe loop terminates. |
| I6 | `capacity` is a power of two and `mask == capacity - 1`. |

---

## 6. The `null`-value ambiguity (explicit)

With `null` values permitted (R5) and no `containsKey` in the required API, these cases are
indistinguishable to a caller:

| Call | `null` means either … | … or |
|------|----------------------|------|
| `get(k)` | no mapping for `k` | `k -> null` |
| `put(k, v)` | no previous mapping | previous mapping was `null` |
| `remove(k)` | nothing was removed | removed a `null` value |

`size()` before/after is the only way to tell removal apart. This is inherent to the given
signatures. **Decided (Q1): no `containsKey`, no `getOrDefault`** — the API stays at exactly the
eight required methods, and this table goes verbatim into the class Javadoc so callers are warned.
Callers that need to distinguish the cases have to track `size()` deltas around the call.

---

## 7. Test plan

### 7.1 Test hooks (package-private, not part of the public API)

Mirroring the debug accessors of `LRUClockMap`, needed to assert the invariants of §5:

```java
int capacity()                  // keys.length
int trueSize()                  // counted non-null keys — must always equal size()
int longestCluster()            // longest run of occupied slots, wrap-around aware
boolean checkChainInvariant()   // verifies I1 and I4 for every occupied slot
int homeSlot( Object key )      // also used by the map itself; lets a test place a key on an exact slot
```

`assertHealthy(map)` bundles the first four and is called after every interesting step in the
suite, not only in dedicated tests.

Tests live in the same package (`org.jugsaxony.demo6`) so they can see these.

### 7.2 Test cases

`FastHashMapTest` — JUnit 6, `@Nested` classes per area.

**A. Construction & empty state**
* T01 fresh map: `size() == 0`, `keys()`/`values()` empty, `get(anything) == null`, `remove(anything) == null`, `clear()` on empty is a no-op.

**B. Basic put/get**
* T02 single put/get round trip.
* T03 put returns `null` for a new key, previous value on overwrite; `size()` does not grow on overwrite.
* T04 many distinct keys, all retrievable, `size()` correct.
* T05 lookup with an *equal but not identical* key instance (`new String("a")`) hits.
* T06 lookup of a key that was never inserted returns `null`.

**C. `null` handling**
* T07 `put(k, null)` then `get(k) == null` **and** `size() == 1`, and the entry shows up in `keys()`/`values()`.
* T08 overwriting a `null` value with a non-null one and back; return values correct each time.
* T09 `remove` of a `null`-valued entry returns `null` and decrements `size()`.
* T10 `get(null)`, `put(null, v)`, `remove(null)` each throw `NullPointerException` (message asserted).
* T11 `put(k, null)` does **not** create a free slot — `trueSize() == size()` afterwards, and a
  subsequent probing key that hashes to a later slot is still found (guards against the classic
  "null value treated as empty slot" bug).

**D. Removal & chain repair** — the highest-risk area
* T12 remove existing → returns value, `size()` decremented, `get` now `null`.
* T13 remove non-existing → `null`, size unchanged.
* T14 remove twice → second call returns `null`.
* T15 **collision chain, middle removal:** with a `FixedHashKey` (constant `hashCode`), insert A,B,C,D, remove B, assert A, C, D are all still reachable and `checkChainInvariant()` holds.
* T16 remove first element of a chain; remove last element of a chain.
* T17 **wrap-around removal:** keys engineered to hash to the last slots of the table, so the cluster wraps past index 0; remove inside the wrapped run and assert all survivors reachable.
* T18 re-insert a removed key → lands correctly, `size()` right.
* T19 **churn:** 100k rounds of `put` + `remove` on a small key space; assert `trueSize() == size()` and `capacity()` never grows beyond the expected bound — proves the absence of tombstone accumulation.

**E. Growth**
* T20 insert past the resize threshold: all entries still present, values correct, `size()` correct.
* T21 growth across several resizes (e.g. 100k entries); `capacity()` is a power of two and `size() <= capacity() * 0.5`.
* T22 growth preserves `null` values.
* T23 growth preserves chain integrity (`checkChainInvariant()` after each resize).

**F. Hash behaviour**
* T24 all keys with `hashCode() == 0`: still correct (worst case, degenerate to linear scan).
* T25 keys with hashes differing only in high bits (`i << 16`): correct, and average probe length stays sane — guards the mix function.
* T26 negative `hashCode()` values do not produce a negative index.
* T40 **anti-clustering regression:** 100k sequential `Integer` keys must not collapse into one
  contiguous run (`longestCluster() < 100`), and removing them in ascending order — the worst case
  for chain repair — must stay cheap. This is the test that pins down the §3.3 fix.

**G. keys() / values()**
* T27 content equality against a reference `HashMap` (order-insensitive, via `Set`/multiset compare).
* T28 sizes: `keys().size() == values().size() == size()`.
* T29 positional correspondence keys↔values on an unmodified map.
* T30 duplicate values appear as many times as inserted; `null` values appear.
* T31 returned lists are snapshots: modifying the returned list does not affect the map, and later map mutation does not affect the returned list.
* T32 `keys()` contains no `null` and no duplicates.

**H. clear()**
* T33 after `clear()`: `size() == 0`, `trueSize() == 0`, all previous keys `get` to `null`, lists empty.
* T34 map is fully usable after `clear()` (re-put, re-get, re-remove).
* T35 `clear()` retains capacity (documented behaviour) and drops all key/value references.

**I. Differential / randomized**
* T36 **Reference test:** seeded `Random`, 200k random ops (`put`/`get`/`remove`) over a bounded key
  space, mirrored into a `java.util.HashMap`; after every op compare return value and `size()`, and
  at the end compare the full key and value multisets. Runs with several fixed seeds. This is the
  test that actually catches chain-repair bugs.
* T37 same, with `null` values mixed in.
* T38 record keys and `Integer`/`String` keys to cover normal generic usage.

**J. Volume / performance smoke** (`@Tag("slow")`, not in the default run)
* T39 1M inserts, then 1M lookups, then 1M removals; assert correctness and that it completes.

Explicitly **not** tested: thread-safety (R1 excludes it) and behaviour after mutating a key's
`hashCode` (documented as undefined).

---

## 8. Build changes

The current `pom.xml` uses JUnit 5.10.0 and `maven.compiler.source/target`. Required changes:

* `junit.version` → **6.x** (`org.junit.jupiter:junit-jupiter-api` + `junit-jupiter-engine`; JUnit 6
  keeps the same coordinates and requires Java 17+, so Java 21 is fine). The exact current 6.x
  version gets pinned when implementing — verified against the local repo / Maven Central rather
  than guessed.
* `maven-surefire-plugin` → 3.5.x (3.2.2 predates the JUnit Platform 6 series).
* Replace `maven.compiler.source`/`target` with `<maven.compiler.release>21</maven.compiler.release>`.
* Add `-Dgroups`/`excludedGroups` config so `@Tag("slow")` is skipped by default.
* Note: `artifactId` still reads `demo5-fasthashmap` — left alone unless you want it renamed (Q5).

Files to create:

```
doc/FastHashMap-spec.md                                   (this file)
src/main/java/org/jugsaxony/demo6/FastHashMap.java
src/test/java/org/jugsaxony/demo6/FastHashMapTest.java
src/test/java/org/jugsaxony/demo6/FixedHashKey.java        (test helper: controllable hashCode/equals)
```

---

## 9. Implementation steps

| Step | Content | Done when |
|------|---------|-----------|
| S0 | `pom.xml`: JUnit 6, surefire 3.5.x, `release` 21. | `mvn -q test` runs (no tests yet). |
| S1 | Class skeleton: fields, constants, constructor, `mixHash`, `size()`, test hooks. | Compiles. |
| S2 | `get` + `put` + `resize`. | T01–T06, T20–T23 green. |
| S3 | `remove` + backward-shift deletion + `reinsert`. | T12–T19 green. |
| S4 | `keys`, `values`, `clear`, `toString` (debug, capped output like `LRUClockMap`). | T27–T35 green. |
| S5 | Full test suite incl. randomized differential test. | T36–T38 green. |
| S6 | Javadoc pass: class doc states not thread-safe, no `null` keys, `null` values allowed, the §6 ambiguity, undefined order, key immutability precondition. | Review. |
| S7 | `mvn test` clean; optional `@Tag("slow")` volume run. | Green build. |

**Outcome:** all steps done. 43 tests in the default run (~2s), 44 with `-DexcludedGroups=`
(the 1M volume test adds ~0.6s). JUnit pinned to 6.0.3, surefire 3.5.6, both fully cached locally
so the build works offline. The only deviation from this spec is the hash function, §3.3.

Style follows `LRUClockMap`: Allman braces, `final` on parameters and locals, `this.` on field
access, Javadoc on every method, inline comments explaining *why* on the tricky paths
(chain repair, resize).

### Complexity

| Operation | Average | Worst case | Allocation |
|-----------|---------|------------|------------|
| `get` | O(1) | O(n) (all keys collide) | none |
| `put` | O(1) amortized | O(n) | only on resize |
| `remove` | O(1) amortized | O(n) | none |
| `size` | O(1) | O(1) | none |
| `keys`/`values` | O(capacity) | O(capacity) | one list |
| `clear` | O(capacity) | O(capacity) | none |

---

## 10. Decisions & open points

Answered on 2026-09-01:

| # | Question | Decision |
|---|----------|----------|
| Q1 | Add `containsKey` / `getOrDefault` to resolve the `null`-value ambiguity of §6? | **No.** Exactly the eight required methods. The ambiguity is documented prominently in the class Javadoc (§6 table goes into the doc comment). |
| Q4 | Load factor. | **0.5**, matching `LRUClockMap`. |
| Q6 | Collision strategy. | **Open addressing with linear probing** and backward-shift deletion, as specified in §3/§4. |

Still on defaults — say so if you want them changed, otherwise they are implemented as written:

| # | Point | Default applied |
|---|-------|-----------------|
| Q2 | Order of `keys()` / `values()`. | Unspecified table order (deterministic per map state, not insertion order). Positional correspondence between the two lists is guaranteed on an unmodified map. |
| Q3 | `clear()` capacity handling. | Keeps the current capacity, fills both arrays with `null`. |
| Q5 | `pom.xml` `artifactId` is `demo5-fasthashmap`. | Left as is. |
