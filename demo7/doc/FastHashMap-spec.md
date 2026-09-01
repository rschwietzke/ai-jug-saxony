# FastHashMap — Specification & Implementation Plan

## 1. Goal

A new, simple, generic hash map implementation based on **open hashing**
(separate chaining), optimized for readability and speed in single-threaded
scenarios.

- Class: `org.jugsaxony.demo7.FastHashMap<K, V>`
- Source: `demo7/src/main/java/org/jugsaxony/demo7/FastHashMap.java`
- Tests: `demo7/src/test/java/org/jugsaxony/demo7/FastHashMapTest.java`

## 2. Required Public API

```java
public FastHashMap()
public V get(final K key)
public V put(final K key, final V value)
public V remove(final K key)
public int size()
public List<K> keys()
public List<V> values()
public void clear()
```

No other public methods are planned. Package-private helpers (e.g. access to
current table capacity) may be added solely for testing resize behavior.

## 3. Functional Requirements

| # | Requirement |
|---|-------------|
| F1 | `put` returns the previous value associated with the key, or `null` if the key was new. |
| F2 | `put` on an existing key replaces the value; `size()` does not change. |
| F3 | `get` returns the value or `null` if the key is not present. |
| F4 | `remove` returns the removed value or `null` if the key was not present; `size()` decreases only when the key existed. |
| F5 | `size()` returns the number of key–value mappings. |
| F6 | `keys()` returns a new `ArrayList<K>` containing all keys; `values()` returns a new `ArrayList<V>` containing all values. Order is **unspecified**. Both return empty lists for an empty map. |
| F7 | `clear()` removes all mappings; afterwards `size() == 0` and `get` returns `null`. The map must be fully usable after `clear()`. |
| F8 | **Null keys are rejected**: `put(null, v)`, `get(null)`, `remove(null)` throw `NullPointerException`. |
| F9 | **Null values are allowed**: `put(k, null)` stores the mapping; `get(k)` then returns `null` while `size()` still counts the entry. |
| F10 | The map is **unbounded** — it grows automatically, no capacity limit. |
| F11 | **Not thread-safe.** Documented in the class Javadoc; no synchronization whatsoever. |

## 4. Design Decisions

### 4.1 Collision strategy: separate chaining with singly linked buckets

Open hashing means each table slot (bucket) holds a chain of entries.

- `Entry<K, V>` node: `final K key`, `V value`, `final int hash`, `Entry<K,V> next`.
- New entries are inserted at the **head** of the bucket chain (O(1), no tail scan).
- Lookup walks the chain comparing the cached `hash` first, then `key.equals(...)`
  only on hash match — cheap for long keys.
- No treeification of buckets (keep it simple; predictable for demo purposes).

### 4.2 Table sizing and growth

- Table length is always a **power of two**; bucket index = `hash & mask`.
- Initial capacity: **16**.
- Load factor: **0.75**; resize (double the table) when `size > capacity * 0.75`.
- Resize rehashes all entries into the new table.

### 4.3 Hashing

- Spread function to protect against weak `hashCode()` implementations:
  `h = key.hashCode(); h ^= (h >>> 16);`
- Negative and zero hash codes are handled correctly by the `& mask` indexing.

### 4.4 `clear()` behavior

- Nulls out all bucket references and resets `size` to 0.
- The table is **reset to the initial capacity** (releases memory of a once-large map).

### 4.5 Code style

Follow the conventions of the existing `LRUClockMap.java`:
Apache-2.0 license header, opening brace on its own line, full Javadoc on the
class and public methods, `final` parameters.

## 5. Complexity

| Operation | Average | Worst case |
|-----------|---------|------------|
| `get` / `put` / `remove` | O(1) | O(n) — all keys in one bucket |
| `size` | O(1) | O(1) |
| `keys` / `values` | O(n + capacity) | O(n + capacity) |
| `clear` | O(capacity) | O(capacity) |
| Resize (amortized per put) | O(1) | O(n) for the single resizing put |

## 6. Test Plan (JUnit 6)

Test class: `FastHashMapTest` in `org.jugsaxony.demo7`.

### 6.1 Basic behavior
- New map: `size() == 0`, `keys()`/`values()` empty, `get` returns `null`.
- `put` new key → returns `null`, `size` grows, `get` returns value.
- `put` existing key → returns old value, value replaced, `size` unchanged.
- `get` missing key → `null`.
- `remove` existing → returns value, `size` decreases, `get` returns `null` afterwards.
- `remove` missing key → `null`, `size` unchanged.

### 6.2 Null handling
- `put(null, v)`, `get(null)`, `remove(null)` each throw `NullPointerException`.
- `put(k, null)` succeeds; `get(k)` returns `null`; entry counted in `size()`,
  contained in `keys()` and `values()`; replaceable and removable.

### 6.3 Collection views
- `keys()`/`values()` contain exactly the stored keys/values (compared as
  sets/multisets against a reference `java.util.HashMap`), correct size.
- Returned lists are independent copies (mutating them does not affect the map).

### 6.4 Clear
- After `clear()`: `size() == 0`, `get` returns `null` for former keys.
- Map is reusable after `clear()`, including growing past the initial capacity again.

### 6.5 Growth / rehashing
- Insert enough entries to trigger multiple resizes (e.g. 10,000); all entries
  still retrievable, `size()` correct, `remove` of all entries works.
- Package-private capacity accessor verifies the table actually grew.

### 6.6 Collision robustness
- Custom key class with constant `hashCode()` (all keys collide): put/get/remove/
  replace behave correctly with one long chain.
- Custom key class with `hashCode() == 0` and negative hash codes.
- Keys that share the same hash but are `equals`-different are stored separately.

### 6.7 Randomized differential test
- Random put/get/remove sequence (e.g. 100k operations, ~1k key space) mirrored
  against `java.util.HashMap` as oracle; assert identical results and size after
  every batch.

### 6.8 Out of scope
- No concurrency tests (map is explicitly not thread-safe).
- No performance benchmarks (can be added later as separate JMH module).

## 7. Build Changes (`demo7/pom.xml`)

- Update `junit.version` from `5.10.0` → **`6.1.3`** (latest JUnit 6 GA).
  Keep `junit-jupiter-api` + `junit-jupiter-engine` test dependencies.
- Keep Java 21 compiler settings.
- Keep `maven-surefire-plugin` 3.2.2 (JUnit Platform compatible); bump to latest
  3.x only if test discovery issues arise.
- Cosmetic, optional: `artifactId` is currently `demo5-fasthashmap` in the demo7
  folder — left unchanged unless requested.

## 8. Implementation Steps

1. Update `pom.xml` (JUnit 6.1.3).
2. Implement `FastHashMap<K, V>` incl. private static `Entry<K, V>` node class.
3. Write `FastHashMapTest` covering sections 6.1–6.7.
4. Verify: `mvn -f demo7/pom.xml test` (all green).
5. Review pass: Javadoc complete, no comments beyond Javadoc, style consistent
   with `LRUClockMap`.

## 9. Open Questions

None blocking. Assumptions made (all documented above):
- JUnit 6 → latest GA 6.1.3.
- Order of `keys()`/`values()` is unspecified.
- `clear()` resets capacity to the initial value.
- No additional public API beyond the required signatures.
