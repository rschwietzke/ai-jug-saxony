# Implementation Plan: Unit Tests for LRUClockMap

## 1. Overview
The goal is to provide a comprehensive, rigorous test suite for `LRUClockMap<K, V>` using **JUnit 5** and modern **Java 21** features (e.g., records, switch pattern matching / pattern matching for `instanceof`, sequencable collections / streams, nested test classes with `@Nested`, `@ParameterizedTest`, `@DisplayName`, assertAll, etc.).
The test suite will use strictly JUnit 5 (`org.junit.jupiter.api.*`, `org.junit.jupiter.params.*`) without third-party assertion/mocking libraries.

## 2. Target Class Contract & Analysis (`LRUClockMap<K, V>`)
From the class contract and implementation:
- **Constructor**:
  - `LRUClockMap(int maxSize)`: Requires `maxSize >= 4`. Throws `IllegalArgumentException` when `maxSize < 4`.
  - Backing array capacity is a power of 2 sized to keep load factor around 0.50 (minimum power of 2 >= maxSize / 0.50).
- **Basic Operations**:
  - `put(K key, V value)`: Null keys or null values throw `NullPointerException`. Inserts new key-value pair or updates existing key. Returns previous value or `null`. If `size == maxSize` and inserting a new key, triggers `evict()`. If updating existing key, does not evict and sets `secondChance = true`.
  - `get(K key)`: Null key throws `NullPointerException`. Returns mapped value or `null`. Sets `secondChance = true` if entry is found (supporting both fast-path direct slot match and linear probing / `expensiveGet`).
  - `getRaw(K key)`: Null key throws `NullPointerException`. Returns mapped value or `null`. Does NOT touch `secondChance` bit.
  - `remove(K key)`: Null key throws `NullPointerException`. Removes key and shifts/realigns cluster entries to preserve open addressing invariants. Returns previous value or `null`.
  - `size()`: Returns current count of entries.
  - `trueSize()`: Counts non-null slots in backing table (should always match `size()`).
  - `occupiedSpace()`: Returns backing array length (power of 2).
  - `keys()`: Returns `List<K>` of current keys in backing array order without `null`s.
  - `clear()`: Wipes backing array, resets `size` to 0.
  - `toString()`: Returns structured string containing map details and entries up to 1024 slots.
  - `getDebugData()`: Returns package-private debug inspection wrapper list.
- **Clock Eviction Algorithm**:
  - Circular hand pointer over table.
  - Entries start with `secondChance = true`.
  - On eviction need: Sweeps table. If `secondChance == true`, sets `secondChance = false` and advances. If `secondChance == false`, evicts that entry and realigns cluster.
  - When accessed via `get` or updated via `put`, entry gets `secondChance = true`.

## 3. Test Structure & Categories

We will structure the tests into modular `@Nested` classes with descriptive `@DisplayName` annotations:

1. **`ConstructorTests`**:
   - Rejection of invalid `maxSize` values (`-1`, `0`, `1`, `2`, `3`) with `IllegalArgumentException`.
   - Acceptance of boundary minimum `maxSize = 4`.
   - Verification of initial state: `size() == 0`, `trueSize() == 0`, `keys().isEmpty()`, `occupiedSpace() >= 8`, non-existent lookups return `null`.

2. **`NullContractTests`**:
   - `put(null, value)` -> NPE with message "Key must not be null".
   - `put(key, null)` -> NPE with message "Value must not be null".
   - `get(null)` -> NPE with message "Key must not be null".
   - `getRaw(null)` -> NPE with message "Key must not be null".
   - `remove(null)` -> NPE with message "Key must not be null".

3. **`BasicCrudOperationsTests`**:
   - Single and multiple `put` and `get`.
   - Replacing existing values (returns old value, updates value, does not change size).
   - `getRaw` behavior vs `get`.
   - `remove` existing keys, non-existing keys, empty map.
   - `size()` and `trueSize()` consistency throughout operations.

4. **`OpenAddressingAndCollisionTests`**:
   - Test colliding keys (same `hashCode()`) to verify linear probing in `put`, `get` (fast path and slow path `expensiveGet`), `getRaw`, and `remove`.
   - Ensure `remove` correctly realigns subsequent entries in collision chain without breaking lookups for remaining elements.
   - Full cluster fill and partial eviction / removal.

5. **`ClockEvictionTests`**:
   - Strict size bounds enforcement (`size <= maxSize`).
   - Eviction of entries without second chance.
   - Protection of accessed entries (`get` sets `secondChance = true`).
   - Protection of updated entries (`put` sets `secondChance = true`).
   - Contrast between `get` (gives second chance) and `getRaw` (does not give second chance).
   - Behavior when all entries have `secondChance = true` (clock sweeps once, clears second chance, then evicts on second pass).

6. **`KeysAndClearTests`**:
   - `keys()` contents match inserted keys across puts and removes.
   - `clear()` empties the map completely (`size == 0`, `trueSize == 0`, `keys().isEmpty()`).
   - Reuse of map after `clear()`.

7. **`DebugAndInspectionTests`**:
   - `toString()` output format and content.
   - `getDebugData()` inspection list correctness and element mapping.

8. **`PropertyAndStressTests`**:
   - Deterministic randomized stress testing with pseudo-random operations (Put, Get, GetRaw, Remove, Overwrite) maintaining internal consistency (`size == trueSize <= maxSize`).
   - Large key sets and high-churn eviction workloads.

## 4. Java 21 & JUnit 5 Features Used
- Records for test keys and fixtures (e.g. `record CollidingKey(String id, int hash)`).
- Local variable type inference (`var`).
- Pattern matching (`instanceof`, switch pattern matching where applicable).
- JUnit 5 Jupiter `@Nested`, `@ParameterizedTest`, `@ValueSource`, `@CsvSource`, `@RepeatedTest`, `@Tag`, `@DisplayName`.
- JUnit 5 assertions (`assertAll`, `assertThrows`, `assertNull`, `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull`).
