# FastHashMap Test Suite Comparative Review & Quality Analysis

## Executive Summary

This document presents a comprehensive technical review and comparative analysis of all eleven `FastHashMapTest.java` test suites across the project directories (`demo0` through `demo9`, plus `demo11`). Each test suite was generated or engineered in the context of implementing an open-addressing hash map (`FastHashMap`) with linear probing, backward-shift deletion, dynamic resizing, and specific contracts around null keys and values.

### Overview of Test Suites

| Module | Origin / Model | Framework | Test Count | Lines | Black-Box vs White-Box | Quality Tier | Primary Strengths & Distinctions |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Baseline (Xceptance / mikvor) | JUnit 4 | 8 | 357 | Pure Black-Box | **Tier 4 (Legacy)** | Real-world benchmark heritage; collision stress via `MockKey`. Lacks null contract tests; unseeded shuffle. |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | JUnit 5 + AssertJ | 17 (21 runs) | 449 | Pure Black-Box | **Tier 2 (Strong)** | Clean `@Nested` BDD hierarchy; cluster head/middle/tail deletion; 50k differential fuzzing; constructor validation. |
| **demo2** | Kimi K3 (Kilo Code) | JUnit 5 Jupiter | 43 | 909 | Pure Black-Box | **Tier 1 (Elite Black-Box)** | Largest black-box suite; exhaustive corner cases; circular wrap-around (`0xFFFFFFFF`); key/value positional alignment; 2 differential fuzzers. |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | JUnit 5 Jupiter | 19 | 507 | Pure Black-Box | **Tier 2 (Strong)** | Dense, high-rigor tests; extreme bit patterns (`MIN_VALUE`, `MAX_VALUE`); wrap-around cluster boundary; multiset value frequency assertions. |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | JUnit 5 Jupiter | 10 | 112 | Pure Black-Box | **Tier 4 (Deficient)** | Severely minimal; double-negative assertions; pseudocollision via String `"Aa"`/`"BB"`; no cluster deletion tests. |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | JUnit 5 Jupiter | 17 | 428 | Pure Black-Box | **Tier 3 (Moderate)** | 2,000-key collision stride-3 deletion; dual differential fuzz tests (general + collision); snapshot isolation verified. Flat structure. |
| **demo6** | Claude Opus 5 Ultra (Claude) | JUnit 5 Jupiter | 38 (44 runs) | 1,033 | Intrusive White-Box | **Tier 1 (Elite White-Box)** | Deepest algorithmic verification; continuous `assertHealthy` invariants; cluster length limit (`longestCluster < 100`); 1.2M fuzzer ops; 1M scale test. |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | JUnit 5 Jupiter | 19 | 492 | Near Black-Box (`capacity()`) | **Tier 3 (Moderate)** | 100k differential fuzzer; custom `NullSafeComparator`. Flaw: passed on chained map; missed cluster head/middle deletion. |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | JUnit 5 Jupiter | 28 | 577 | Pure Black-Box | **Tier 2 (Strong)** | Complete constructor boundary validation; step-by-step cluster deletion (head, middle, tail, sequential middle removal). Missing snapshot copy test. |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | JUnit 5 + AssertJ | 24 (29 runs) | 591 | Near Black-Box (`capacity()`) | **Tier 1 (Elite Algorithmic)** | Explicit Knuth 6.4R interleaved natural home displacement; circular boundary wrap-around deletion; full constructor suite; AssertJ fluents. |
| **demo11** | Gemini 3.7 Flash High (100% PIT Rework) | JUnit 5 + AssertJ | 22 (24 runs) | 633 | Intrusive White-Box | **Tier 2 (Engineered 100%)** | Engineered specifically for 100% PIT mutation and 100% JaCoCo coverage; tests Murmur3 mixer bitshifts, tableSizeFor power-of-2, slot nulling. |

---

## Detailed Per-Module Inventory and Analysis

### 1. demo0 (`org.jugsaxony.demo0.FastHashMapTest`)
* **Framework**: JUnit 4 (`@Test`, `org.junit.Assert.*`)
* **Metrics**: 8 test methods, 357 lines, flat structure.

#### Implemented Tests:
1. `happyPath()`: Inserts 5 keys (`"a"` to `"e"`), asserts sizes and values, overwrites `"b"` with 20 and re-verifies.
2. `keys()`: Tests `keys()` view on 5 entries, removes `"cc"`, attempts remove of `"c"`, inserts `"zz"`, and checks unknown key retrieval.
3. `values()`: Tests `values()` list size and membership before and after removal of `"cc"`.
4. `remove()`: Removes `"b"` and `"d"` from 5-element map; checks size becomes 3; asserts null on removed keys; removes again (verifying null return); reinserts with new values.
5. `clear()`: Populates, clears, verifies size=0, keys empty, values empty; re-inserts and clears again.
6. `collision()`: Uses `MockKey` with fixed hash 12. Inserts 15 items, verifies; inserts 20 items, verifies; removes first 10 items, verifies remaining 10.
7. `overflow()`: Inserts 152 items with fixed hash 1 into a map of initial capacity 5 (forcing multiple rehashes on pure collision cluster); verifies all 152 items.
8. `hitEachSlot()`: Inserts 300 items across 150 hashes; removes all; re-inserts in sorted order; re-inserts with `Collections.shuffle`; removes with shuffle; verifies size=0.

#### Strengths:
- Heavy collision stress tests (`collision`, `overflow`, `hitEachSlot`) with up to 300 entries colliding on specific slots.
- Verifies dynamic resizing under extreme collision density.

#### Weaknesses & Defects:
- **Zero Null Contract Testing**: Completely lacks tests for null key rejection (`NullPointerException`) or null value support.
- **Unseeded Non-Determinism**: Lines 298 and 308 call `Collections.shuffle(keys)` without a random seed, introducing non-deterministic execution order.
- **Flawed Helper**: `MockKey.compareTo` reverses parameter order (`o.key.compareTo(this.key)`), and `equals` performs an unchecked cast `(MockKey<T>) o`.
- **Legacy Framework**: Uses JUnit 4 assertions and annotations.
- **Missing Checks**: Does not assert return value of `put` when replacing an existing value.

---

### 2. demo1 (`org.jugsaxony.demo1.FastHashMapTest`)
* **Framework**: JUnit 5 Jupiter + AssertJ
* **Metrics**: 17 test methods (21 executions), 449 lines, 7 `@Nested` classes with `@DisplayName`.

#### Implemented Tests:
* **BasicOperationsTest**:
  1. `testEmptyMap()`: Verifies size 0, get is null, keys/values empty.
  2. `testPutAndGetSingle()`: Verifies `put` returns null, size 1, get matches, absent key returns null.
  3. `testPutOverwrite()`: Overwrites key, verifies previous value returned, size unchanged.
  4. `testRemoveExisting()`: Removes existing key, verifies returned value, decrements size, subsequent get is null.
  5. `testRemoveNonExisting()`: Removes absent key, verifies null returned and size unchanged.
  6. `testClear()`: Fills 20 items, clears, asserts size 0 and empty views; verifies re-insertion.
* **NullHandlingTest**:
  7. `testNullKeysRejected()`: Asserts `NullPointerException` on `put(null, v)`, `get(null)`, and `remove(null)` with exact message `"Key cannot be null"`.
  8. `testNullValuesSupported()`: Stores null value, replaces with non-null, replaces with null, removes key with null value.
* **CollectionsTest**:
  9. `testKeysAndValuesSnapshots()`: Tests snapshot independence by clearing returned `keys()` and `values()` and verifying the map is unaffected.
* **CollisionAndProbingTest**:
  10. `testCollisionChainRetrieval()`: 10 items sharing fixed hash 42; verifies all retrieved.
  11. `testRemoveHeadOfCollisionChain()`: Removes head `k0` in 4-item collision cluster; verifies `k1`, `k2`, `k3` remain accessible.
  12. `testRemoveMiddleOfCollisionChain()`: Removes middle `k1` in 4-item cluster; verifies `k0`, `k2`, `k3` remain accessible.
  13. `testRemoveTailOfCollisionChain()`: Removes tail `k2` in 3-item cluster; verifies `k0`, `k1` remain accessible.
  14. `testRemoveAllCollisionItems()`: 15 items in cluster removed in shuffled order (`Random(12345)`); asserts remaining items stay accessible at every step.
* **ResizingAndScaleTest**:
  15. `testLargeInsertAndRetrieval(int count)`: Parameterized test (100, 1,000, 10,000, 50,000 items); removes every even item, verifies remaining odd items.
* **FuzzTesting**:
  16. `testRandomOperationsAgainstReferenceMap()`: 50,000 operations (45% put, 30% get, 23% remove, 2% clear) against `java.util.HashMap` using `Random(42)`.
* **ConstructorAndValidationTest**:
  17. `testSingleArgConstructor()`: Tests `FastHashMap(int)`.
  18. `testInvalidConstructorArgs()`: Verifies `IllegalArgumentException` on negative capacity, LF <= 0, LF >= 1, and `Float.NaN`.

#### Strengths:
- Clean structure, clear BDD intent via `@Nested` and `@DisplayName`.
- Systematic verification of backward-shift deletion across cluster positions (head, middle, tail, and random order).
- Solid differential fuzz testing with reproducible seed (`Random(42)`).

#### Weaknesses & Gaps:
- Does not test circular wrap-around probing at the end of the backing array.
- Does not test bit-pattern extremes or hash code collisions around negative numbers.

---

### 3. demo2 (`org.jugsaxony.demo2.FastHashMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 43 test methods, 908 lines, 9 `@Nested` classes with `@DisplayName`.

#### Implemented Tests:
* **InitialState**: `newInstanceIsEmpty`
* **NullKeys**: `putNullKeyThrows`, `getNullKeyThrows`, `removeNullKeyThrows`
* **NullValues**: `putNullValueIsStored`, `nullValueAppearsInValues`, `replaceValueWithNull`, `replaceNullWithValue`
* **PutGet**: `putReturnsNullForNewKey`, `putReturnsOldValueOnReplace`, `getAbsentKeyReturnsNull`, `getAfterPut`, `sizeTracksPuts`
* **Remove**: `removeExistingKeyReturnsOldValue`, `removeAbsentKeyReturnsNull`, `removeDoesNotBreakProbeChain`, `removeThenReinsert`, `removeAllEntries`, `removeEverySecondEntry`
* **Collisions**:
  - `collisionsAreHandled`: 12 keys with hash 42.
  - `overwriteWithinCollisionCluster`: Overwriting middle key `k3` with new value 333 inside collision cluster.
  - `wrapAroundProbing`: Explicitly tests hash `0xFFFFFFFF` forcing probes to wrap from the end of the array to index 0, followed by removals within the wrapped chain.
* **Resize**: `resizePreservesAllEntries` (100 items), `resizeWithCollisions` (60 collision items forcing multiple resizes), `resizeManyEntries` (1,000 items), `removeAfterResize` (100 items, removes every 3rd).
* **Clear**: `clearEmptiesMap`, `mapIsReusableAfterClear`, `clearOnEmptyMap` (idempotency).
* **KeysValues**: `keysContainsAllInsertedKeys`, `valuesContainsAllInsertedValues`, `keysAndValuesAreConsistent` (asserts `keys().get(i)` maps to `values().get(i)`), `mutatingReturnedListsDoesNotAffectMap`, `keysValuesOnEmptyMapReturnEmptyLists`, `keysContainNoNulls`.
* **ReferenceComparison**:
  - `randomizedOperationSequence`: 20,000 operations (60% put, 20% remove, 10% clear, 10% get) with `Random(42L)`.
  - `randomizedCollidingKeySequence`: 5,000 operations with keys restricted to 4 hash buckets (`id % 4`).
* **GeneralBehavior**: `distinctStringKeys`, `equalHashUnequalKeys`, `equalsBasedKeyLookup`, `keysWithoutToString`, `heavyMutation` (10 rounds of 100 puts, 50 removes, 50 re-puts), `keyContractSanity`.

#### Strengths:
- **Best Black-Box Architecture**: Pure black-box testing without a single package-private or internal method call.
- **Wrap-Around Precision**: Specifically forces and verifies circular wrap-around probing and deletion via `0xFFFFFFFF`.
- **Dual Fuzzing Strategy**: Combines uniform random fuzzing with high-collision 4-bucket fuzzing.
- **Contract Robustness**: Checks equals-vs-identity, absence of `toString()` dependencies, and positional correlation between keys and values views.

#### Weaknesses & Gaps:
- Capped at default constructor (`new FastHashMap<>()`), missing overloaded capacity/loadFactor validation.
- Max volume tested is 1,000 entries (no 10k+ stress test outside fuzzing).

---

### 4. demo3 (`org.jugsaxony.demo3.FastHashMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 19 test methods, 506 lines, 6 `@Nested` classes.

#### Implemented Tests:
* **BasicOperations**:
  1. `newMapIsEmpty`: Verifies empty state, null lookups, and null removals.
  2. `putGetReplaceAndRemoveFollowMapSemantics`: Comprehensive CRUD lifecycle on single map.
  3. `equalButNonIdenticalKeysAddressTheSameMapping`: Equality lookup and removal using distinct `new String("key")` instances.
  4. `allIntegerHashBitPatternsAreUsable`: Tests keys hashing to `0`, `-1`, `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, `0x12340000`, and `0x00001234`.
* **NullHandling**:
  5. `nullKeysAreRejectedWithoutChangingTheMap`: NPE checks for get, put, remove; asserts map size and contents remain unchanged.
  6. `nullValuesCanBeInsertedReplacedListedAndRemoved`: Complete null value lifecycle.
* **CollisionHandling**:
  7. `unequalKeysWithTheSameHashRemainDistinct`: 80 keys sharing hash 3.
  8. `deletingHeadMiddleAndTailPreservesCollisionCluster`: Tests removing keys 0, 5, 9 in a 10-key cluster using `removeAndCheck`.
  9. `probingAndDeletionWorkAcrossArrayBoundary`: Hash 15 targets the final slot of the initial 16-slot table, forcing probes across the array boundary; tests head, middle, and tail removal across the boundary.
  10. `collisionClusterCanBeRemovedInArbitraryOrder`: 60 items across 3 hash buckets removed in shuffled order (`Random(73L)`).
* **Growth**:
  11. `mappingsSurviveSeveralResizes`: 10,000 entries containing null values survive growth.
  12. `replacementAtInitialThresholdDoesNotAddAMapping`: Replaces value when map is at threshold capacity, verifying size does not increase.
  13. `removalsAfterSeveralResizesPreserveRemainingMappings`: 2,000 items grown, removes every 3rd, verifies 1,333 remain.
* **Snapshots**:
  14. `keyAndValueSnapshotsCorrespondByIndex`: Verifies index correspondence between `keys()` and `values()`, and verifies value frequencies via `frequencies()`.
  15. `snapshotsAreMutableAndIndependentFromTheMap`: Verifies adding to `keys()` or clearing `values()` does not affect map, and map modifications do not affect previously captured snapshots.
  16. `duplicateAndNullValuesKeepTheirMultiplicity`: Verifies duplicate values and multiple null values in `values()`.
* **Clear**:
  17. `clearEmptiesAGrownMapAndAllowsReuse`: 1,000 items cleared, verifies key snapshot preserved, re-populates map.
  18. `clearingAnEmptyMapIsSafe`: Idempotent clear on empty map.
* **Top-Level**:
  19. `seededOperationsMatchHashMap`: 30,000 operations (`Random(0x5EEDC0DEL)`), asserts map contents via `assertMapContents` every 257 steps.

#### Strengths:
- Very high testing density and algorithmic awareness.
- Tests hash bit-pattern extremes (`MIN_VALUE`, `-1`, `MAX_VALUE`).
- Tests circular array boundary probing and deletion explicitly at index 15.
- Verifies value frequency multiplicity (counting duplicate and null occurrences).
- Periodic deep verification (`assertMapContents`) during differential fuzzing.

#### Weaknesses & Gaps:
- Lacks `@DisplayName` annotations.
- Only tests default constructor.

---

### 5. demo4 (`org.jugsaxony.demo4.FastHashMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 10 test methods, 111 lines, flat class using `@BeforeEach`.

#### Implemented Tests:
1. `testPutAndGet`: 2 elements put and retrieved.
2. `testPutUpdate`: 1 element updated, asserts old value returned.
3. `testRemove`: 2 elements, removes 1, asserts removed value and remaining entry.
4. `testRemoveNonExistent`: Asserts null return.
5. `testNullKeyThrows`: NPE on put, get, remove.
6. `testNullValueAllowed`: Stores null value. Uses double negative: `assertFalse(map.keys().contains("nullVal") == false)`.
7. `testResize`: Inserts 100 items, verifies size and retrieval.
8. `testClear`: Clears 2 items, verifies size 0.
9. `testKeysAndValues`: Asserts keys and values list sizes and membership for 2 items.
10. `testCollisions`: Relies on Java String hash collision of `"Aa"` and `"BB"`.

#### Strengths:
- Minimal basic smoke test.

#### Weaknesses & Flaws:
- **Critically Deficient**: Lowest coverage and quality among all suites.
- **Pseudo-Collision Flaw**: Line 105 assumes `"Aa"` and `"BB"` collide. In open addressing maps with hash mixing (e.g. Murmur3 or high-bit shifting), these strings may not land in the same bucket.
- **No Cluster Deletion**: Never tests removing colliding items; completely misses backward-shift deletion bugs.
- **No Fuzzing / Stress Testing**: Zero comparison against `java.util.HashMap`.
- **No Boundary / Wrap-around / Snapshot Testing**: Completely absent.
- **Code Smell**: Double-negative assertion `assertFalse(... == false)`.

---

### 6. demo5 (`org.jugsaxony.demo5.FastHashMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 17 test methods, 427 lines, flat structure.

#### Implemented Tests:
1. `emptyMap`: Size 0, null get/remove, empty views.
2. `putAndGet`: 3 items inserted and verified.
3. `putReturnsPreviousValue`: Overwrite returns old value.
4. `nullKeysAreRejected`: NPE on get, put, remove.
5. `nullValuesAreAllowed`: Null value stored, overwritten with non-null, overwritten with null.
6. `removeExisting`: Removes key, verifies return and remaining mapping.
7. `removeAbsent`: Asserts null return.
8. `removeWithNullValue`: Removes key mapped to null.
9. `clearEmptiesMap`: Fills 100 items, clears, verifies re-usability.
10. `growsBeyondInitialCapacity`: Inserts 20,000 items, overwrites all 20,000 items.
11. `collidingKeys`: 2,000 `CollisionKey` instances (hash 42); removes every 3rd key; verifies all 2,000 items (absent vs present).
12. `keysReturnsSnapshot`: Mutates returned `keys()` list (clears it), verifies map unaffected.
13. `valuesReturnSnapshot`: Mutates returned `values()` list, verifies map unaffected.
14. `keysAndValuesMatchSize`: 500 items, asserts views have size 500.
15. `randomOperationsMatchHashMap`: 50,000 operations (`Random(42)`) comparing put, get, remove against JDK `HashMap`.
16. `randomCollisionOperationsMatchHashMap`: 20,000 operations (`Random(7)`) using `CollisionKey` instances against `HashMap`.
17. `customKeyUsesEquals`: Verifies value-based `MapKey` lookup.

#### Strengths:
- Heavy collision stress test (`collidingKeys`) inserting 2,000 colliding items and removing every 3rd.
- Dual differential fuzz testing (general keys + collision keys).
- Pure black-box testing.

#### Weaknesses & Gaps:
- Flat structure without `@Nested` grouping.
- Lacks constructor argument validation.
- Does not test circular wrap-around at array boundary.
- Fuzz testing omits `clear()`.

---

### 7. demo6 (`org.jugsaxony.demo6.FastHashMapTest`)
* **Framework**: JUnit 5 Jupiter (`@Test`, `@ParameterizedTest`, `@Nested`, `@DisplayName`, `@Tag`)
* **Metrics**: 38 test methods (44 executions), 1,032 lines, 10 `@Nested` classes (A through J).

#### Implemented Tests:
* **A. Construction and empty state**:
  - `T01 freshMapIsEmpty`: Checks empty state, null get/remove, clear on empty map, and asserts invariants.
* **B. Basic put and get**:
  - `T02 singleRoundTrip`: Put and get, checks health.
  - `T03 putReturnsPreviousValue`: Overwrite returns old value, size unchanged.
  - `T04 manyDistinctKeys`: 1,000 keys inserted and retrieved.
  - `T05 equalButNotIdenticalKey`: Equality lookup; asserts original key instance is preserved (`assertSame(inserted, map.keys().get(0))`).
  - `T06 unknownKey`: Absent key returns null.
* **C. Null handling**:
  - `T07 nullValueIsAMapping`: Storing null value creates entry, verified in `keys()` and `values()`.
  - `T08 overwriteNullValues`: Overwrite null with value and value with null.
  - `T09 removeNullValuedEntry`: Removes null-valued entry, verifies return is null and size decrements.
  - `T10 nullKeysAreRejected`: NPE with exact message `"Key must not be null"`.
  - `T11 nullValueDoesNotFreeTheSlot`: Places `a -> null` and `b -> "val"` on identical home slots; verifies looking up `b` probes past `a` without stopping.
* **D. Removal and chain repair**:
  - `T12 removeExisting`, `T13 removeUnknown`, `T14 removeTwice`.
  - `T15 removeInTheMiddleOfAChain`: 4 colliding keys, removes `b`, asserts `c` and `d` shifted back.
  - `T16 removeFirstAndLastOfAChain`: Removes head `a`, then tail `c`.
  - `T17 removeInsideAWrappedCluster`: Uses `hashForSlot` to target slot `capacity - 1`; cluster wraps to slots 0, 1, 2; removes slot 0 entry, verifying repair across circular array boundary.
  - `T18 reinsertAfterRemoval`.
  - `T19 churnDoesNotLeakSlots`: 100,000 put/remove cycles on a 50-item working set; asserts `trueSize() == 50` and capacity never expands (proving zero tombstone leaks).
* **E. Growth**:
  - `T20 crossTheThreshold`: Verifies capacity expansion at threshold.
  - `T21 severalResizes`: 100,000 items, asserts power-of-two capacity and load factor invariant.
  - `T22 growthPreservesNullValues`: 100 items containing null values survive resize.
  - `T23 growthKeepsChainsIntact`: 200 keys on 8 home slots rehashed across resizes.
* **F. Hash behaviour**:
  - `T24 allKeysCollide`: 100 keys hashing to 0; removes even keys; verifies odd keys.
  - `T25 highBitsOnlyKeys`: 64 keys differing only in high bits (`i << 16`).
  - `T40 sequentialKeysDoNotFormOneCluster`: 100,000 sequential keys; asserts `map.longestCluster() < 100` to prevent O(N^2) degradation during bulk removal.
  - `T26 negativeHashCodes`: Tests `Integer.MIN_VALUE`, `-1`, `-123456789`.
* **G. keys() and values()**:
  - `T27 contentMatchesReference`, `T28 listSizes`, `T29 keysAndValuesCorrespond`, `T30 valuesKeepsDuplicatesAndNulls`, `T31 listsAreSnapshots` (both map-to-list and list-to-map isolation), `T32 keysAreUniqueAndNotNull`.
* **H. clear()**:
  - `T33 clearEmptiesEverything`, `T34 usableAfterClear`, `T35 clearKeepsCapacity`.
* **I. Randomized comparison against java.util.HashMap**:
  - `T36 randomOperations`: Parameterized across seeds `1L`, `42L`, `4711L`, `987654321L` (200,000 ops each).
  - `T37 randomOperationsWithNullValues`: Parameterized across seeds `7L`, `2026L` with null values (200,000 ops each).
  - `T38 ordinaryKeyTypes`: Tests Java record `Point(int x, int y)` as map key.
* **J. Volume**:
  - `T39 oneMillionEntries`: 1,000,000 entries put, read, and removed (`@Tag("slow")`).

#### Strengths:
- **Most Sophisticated Suite**: Invariant testing via `assertHealthy`, probing continuity checks via `checkChainInvariant`, and tombstone tracking via `trueSize()`.
- **Cluster Length Guard**: Explicitly verifies cluster clustering limit (`longestCluster < 100`) on 100k items.
- **Enormous Fuzzing Scale**: 1.2 million total randomized operations across 6 seeds.
- **Highest Volume**: 1,000,000 entries tested.

#### Weaknesses & Gaps:
- **Intrusive White-Box Coupling**: Relies on 5 package-private methods (`capacity()`, `trueSize()`, `homeSlot()`, `longestCluster()`, `checkChainInvariant()`). If executed against any other demo's FastHashMap, it will not compile.
- Does not test capacity or load factor constructors.

---

### 8. demo7 (`org.jugsaxony.demo7.FastHashMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 19 test methods, 491 lines, flat structure with commented sections, uses `@BeforeEach`.

#### Implemented Tests:
* Basic: `newMapIsEmpty`, `putNewKeyReturnsNullAndStoresValue`, `putExistingKeyReturnsOldValueAndReplaces`, `getMissingKeyReturnsNull`, `removeExistingKeyReturnsValueAndShrinksSize`, `removeMissingKeyReturnsNullAndKeepsSize`, `putAndRemoveManyKeysInSameBucketOrderIndependently`.
* Nulls: `nullKeyIsRejected`, `nullValuesAreAllowed`.
* Views: `keysAndValuesMatchReferenceMap` (uses custom `NullSafeComparator`), `returnedListsAreIndependentCopies`.
* Clear: `clearEmptiesTheMap`, `mapIsFullyUsableAfterClear`.
* Growth: `manyEntriesSurviveMultipleResizes` (10,000 items, asserts power-of-two capacity), `sizeStaysCorrectAcrossGrowthAndReplacement`.
* Collisions: `allKeysCollidingStillWork` (200 keys with `ConstantHashKey(42)`), `sameHashButDifferentKeysAreStoredSeparately`, `zeroAndNegativeHashCodesWork`.
* Fuzzer: `randomOperationsMatchJavaHashMap` (100,000 ops against JDK `HashMap` with `Random(42)`).

#### Strengths:
- High operation count (100k) in differential fuzzer.
- Clean value sorting helper (`NullSafeComparator`).

#### Weaknesses & Defects:
- **False Positive on Non-Open-Addressing**: In `demo7`, Qwen implemented a separate-chaining map instead of an open-addressing map. Because `FastHashMapTest` in `demo7` only tested high-level map semantics and never tested probe chain deletions (head/middle/tail backward shift repair), the tests passed 100% on a completely wrong data structure!
- Lacks circular buffer wrap-around tests.
- Uses mutable shared `map` field from `@BeforeEach`.

---

### 9. demo8 (`org.jugsaxony.demo8.FastHashMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 28 test methods, 576 lines, flat structure with banner comments, uses `@BeforeEach`.

#### Implemented Tests:
* **Construction**: `newMapIsEmpty`, `constructorWithInitialCapacity`, `constructorWithInitialCapacityAndLoadFactor`, `constructorInvalidArgumentsThrow` (negative capacity, 0.0, -0.5, 1.0, 1.5, `Float.NaN`).
* **Basic CRUD**: `putAndGetSingleEntry`, `putAndGetMultipleEntries`, `putOverwritesExistingValueAndReturnsOldValue`, `removeExistingEntryReturnsOldValue`, `removeNonExistingEntryReturnsNull`, `removeOnEmptyMapReturnsNull`, `reinsertRemovedKey`.
* **Nulls**: `putNullKeyThrowsNullPointerException`, `getNullKeyThrowsNullPointerException`, `removeNullKeyThrowsNullPointerException`, `putNullValueAllowed`.
* **Collisions & Probing**:
  - `collisionLinearProbingLookup`: 4 colliding keys.
  - `collisionRemovalFromBeginningOfCluster`: Removes head `k1`.
  - `collisionRemovalFromMiddleOfCluster`: Removes middle `k2`.
  - `collisionRemovalFromEndOfCluster`: Removes tail `k3`.
  - `collisionRemovalAllSequentially`: Iteratively removes middle element until cluster is empty, checking remaining accessibility.
* **Growth**: `mapGrowsDynamicallyAcrossMultipleResizes` (10,000 items, removes even items).
* **Views**: `keysAndValuesOnEmptyMap`, `keysAndValuesContainAllEntries`, `keysAndValuesWithNullValues`, `keysAndValuesReflectRemovals`.
* **Clear**: `clearEmptiesTheMap`, `clearOnEmptyMap`.
* **Stress**: `randomizedStressTestAgainstReferenceMap` (50,000 ops with `Random(123456789L)`).

#### Strengths:
- Pure black-box testing.
- Thorough constructor argument validation (capacities and invalid load factors).
- Detailed collision cluster deletion tests, including iterative middle removal (`collisionRemovalAllSequentially`).

#### Weaknesses & Gaps:
- Does not test snapshot immutability (does not check if clearing `keys()` or `values()` affects map).
- Does not test circular array wrap-around.
- Stress test omits `clear()`.

---

### 10. demo9 (`org.jugsaxony.demo9.FastHashMapTest`)
* **Framework**: JUnit 5 Jupiter + AssertJ
* **Metrics**: 24 test methods (29 executions), 590 lines, 7 `@Nested` classes with `@DisplayName`.

#### Implemented Tests:
* **ConstructorTests**:
  1. `defaultConstructorInitializesValidMap`: Verifies initial capacity 16 and empty state.
  2. `customCapacityRoundsUpToPowerOfTwo`: Verifies capacities 0->4, 3->4, 5->8, 16->16, 17->32.
  3. `negativeCapacityThrowsIllegalArgumentException`: Asserts IAE on -1.
  4. `invalidLoadFactorThrowsIllegalArgumentException`: Parameterized test (-0.5f, 0.0f, 1.0f, 1.5f, Float.NaN).
* **BasicOperationsTests**:
  5. `emptyMapBehavior`, 6. `putAndGetSingleEntry`, 7. `putOverwriteReturnsOldValueAndPreservesSize`, 8. `removeExistingEntry`, 9. `removeNonExistentEntryReturnsNull`, 10. `clearEmptiesMapAndAllowsReuse`.
* **NullHandlingTests**:
  11. `putNullKeyThrowsNullPointerException`, 12. `getNullKeyThrowsNullPointerException`, 13. `removeNullKeyThrowsNullPointerException`, 14. `nullValuesAreSupported`.
* **CollisionAndShiftDeletionTests**:
  15. `multipleCollidingKeysAreAllStoredAndRetrieved`: 4 keys with hash 42.
  16. `removeHeadOfCollisionClusterShiftsRemainingElements`: Removes cluster head.
  17. `removeMiddleOfCollisionClusterShiftsCorrectly`: Removes middle keys `k2` then `k3`.
  18. `removeTailOfCollisionCluster`: Removes cluster tail.
  19. `collisionInterleavedWithDifferentNaturalHomes`: **Unique & Critical Test**: Puts keys with natural homes 0, 0, 1, 0 into slots 0, 1, 2, 3. Removes slot 0. Verifies Knuth 6.4R invariant that key with natural home 1 does not shift illegally into slot 0!
  20. `cyclicBoundaryWraparoundCollisionAndDeletion`: Capacity 8. Puts keys with hash 7 into slot 7, wrapping to slots 0 and 1. Removes key at slot 7 across the boundary.
* **DynamicResizingTests**:
  21. `mapExpandsWhenExceedingThreshold`: Verifies capacity stays 8 up to 4 items and expands to >= 16 on 5th item.
  22. `largeScaleInsertionAndLookup`: 20,000 items, removes even items, verifies odd.
* **CollectionViewsTests**:
  23. `keysAndValuesSnapshots`: Snapshot independence (clears returned lists).
  24. `correspondingKeyAndValueOrder`: Verifies index alignment of keys and values.
* **DifferentialStressTests**:
  25. `randomizedOperationsEquivalence`: 50,000 ops (`Random(42)`) against `java.util.HashMap` with put, get, remove, and occasional clear.

#### Strengths:
- **Best Algorithmic Shift Test**: `collisionInterleavedWithDifferentNaturalHomes` directly tests the subtle invariant of Knuth's Algorithm 6.4R (preventing shifting an entry prior to its natural home).
- **Circular Wrap Boundary Test**: Dedicated test for cyclic boundary insertion and deletion.
- **Power-of-Two Rounding Test**: Validates constructor capacity rounding.
- Modern AssertJ fluent assertions and `@Nested` structure.

#### Weaknesses & Gaps:
- Volume capped at 20,000 entries.
- Differential testing limited to a single seed.

---

### 11. demo11 (`org.jugsaxony.demo1.FastHashMapTest`)
* **Framework**: JUnit 5 Jupiter + AssertJ
* **Metrics**: 22 test methods (24 executions), 632 lines, 7 `@Nested` classes with `@DisplayName`.

#### Implemented Tests:
* Inherits all baseline tests from `demo1`, plus mutation-killing additions:
  1. `testClear`: Enhanced to assert backing arrays are nulled out (`map.keysArray().containsOnlyNulls()`, `valuesArray().containsOnlyNulls()`) and verifies clear idempotency on an already empty map.
  2. `HashMixingTest.testMixHashDistribution`: Directly tests MurmurHash3 finalizer bit shifts and constants with exact bit patterns (`0x00000000`, `0x80000000`, `0xFFFFFFFF`, etc.).
  3. `testExactThresholdResizeBoundary`: Asserts exact capacity (16 -> 32) and threshold (8 -> 16) before and after threshold crossing.
  4. `testSmallCapacities`: Verifies initial capacities 0, 1, 2, 3, 1024, and `MAXIMUM_CAPACITY`.
  5. `testLoadFactorBoundaries`: Tests extreme valid load factors (`0.001f` and `0.999f`).
  6. `testTableSizeFor`: Validates power-of-two table size math across 13 powers of two up to `1 << 30`.

#### Strengths:
- **100% Mutation Killed (PIT) & 100% JaCoCo Coverage**: Specifically engineered to kill all mutants.
- Deep verification of hash mixing function and capacity calculations.
- Verifies backing array slot cleanup on clear (preventing memory leaks).

#### Weaknesses & Flaws:
- **Intrusive White-Box Coupling**: Exposes internal array accessors (`keysArray()`, `valuesArray()`), static internals (`mixHash()`, `tableSizeFor()`), and package-private state (`capacity()`, `threshold()`). Not portable.
- Still lacks an explicit circular wrap-around deletion test across the table boundary.

---

## Cross-Comparison Matrix

The following table provides a functional comparison across all 11 test suites:

| Category / Feature | demo0 | demo1 | demo2 | demo3 | demo4 | demo5 | demo6 | demo7 | demo8 | demo9 | demo11 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Basic CRUD Lifecycle** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Put Overwrite Returns Old Value** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Remove Return Value Checked** | Partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Remove on Empty Map** | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ |
| **Clear Idempotency on Empty Map** | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ | ✅ |
| **Null Key Rejection (NPE on Get/Put/Del)**| ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Null Key Exception Message Asserted** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ✅ |
| **Null Value Insertion & Retrieval** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Overwrite Value with Null & Vice Versa**| ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Null Value Does Not Break Probe Chain**| ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Collision: Fixed Hash Code Key Class** | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Collision: Cluster Head Deletion** | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ |
| **Collision: Cluster Middle Deletion** | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ |
| **Collision: Cluster Tail Deletion** | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ |
| **Collision: Arbitrary Shuffled Deletion**| ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| **Collision: Interleaved Natural Homes** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| **Circular Wrap-Around Probing** | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ |
| **Circular Wrap-Around Deletion** | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ |
| **Slot Leaks / Churn Invariant** | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Max Cluster Length Check** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Extreme Hash Bit Patterns (MIN_VALUE)** | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ |
| **Resizing: Null Values Preserved** | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Exact Threshold Resize Boundary** | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ✅ |
| **Volume Scale: 10,000+ items** | ❌ | ✅ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Volume Scale: 100,000+ items** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Volume Scale: 1,000,000 items** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Snapshot Independence (List Mutation)** | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Positional Match: keys() vs values()** | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ |
| **Constructor: Overloaded Capacity/LF** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Constructor: Invalid Argument Checks** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Constructor: Power-of-2 Rounding Math** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Differential Fuzzing vs JDK HashMap** | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Fuzzing: Clear Operation Included** | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Fuzzing Operations Count** | 0 | 50k | 25k | 30k | 0 | 70k | 1.2M | 100k | 50k | 50k | 5k |
| **Pure Black-Box (Portable)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ⚠️ | ✅ | ⚠️ | ❌ |

---

## In-Depth Comparative Analysis

### 1. Tier 1: The Titans — demo6 vs demo2
- **demo6 (Claude Opus 5 Ultra)** is the most algorithmically rigorous test suite. Its use of continuous invariant verification (`assertHealthy`), probe continuity checks (`checkChainInvariant`), tombstone absence verification (`size == trueSize()`), and dynamic slot targeting (`hashForSlot`) makes it an exceptional test bench for debugging an open-addressing map. However, it is an **intrusive white-box** suite: it requires 5 internal methods on `FastHashMap` and cannot run against any other implementation.
- **demo2 (Kimi K3)** is the **gold standard for pure black-box testing**. With 43 tests and 908 lines, it achieves comprehensive coverage without calling a single private or package-private method. It covers wrap-around probing (`0xFFFFFFFF`), object contracts (identity vs equality, missing `toString()`), snapshot mutability, and dual differential fuzzers.
- **Comparison**: While `demo6` is superior for validating internal hash map mechanics, `demo2` is superior as a specification-compliance suite that can test any third-party open-addressing implementation.

### 2. Tier 2: The Modern Algorithmic Contenders — demo9, demo1, demo11, demo3
- **demo9 (Gemini 3.8 Flash High Antigravity)** features the most precise algorithmic test in the repository: `collisionInterleavedWithDifferentNaturalHomes`. This test creates a cluster where an interleaved entry has a natural home other than the cluster start and verifies that backward-shift deletion does not shift an element before its natural home (the defining correctness invariant of Knuth's Algorithm 6.4R). It also includes cyclic boundary wrap-around deletion and constructor capacity rounding.
- **demo1 (Gemini 3.7 Flash High Antigravity)** established the clean BDD `@Nested` architecture with AssertJ assertions, isolated cluster position deletion tests (head, middle, tail), and 50,000-operation differential fuzzing.
- **demo11 (Gemini 3.7 Flash High Rework)** demonstrates how mutation-driven testing (PIT) transforms a suite: it adds direct validation of mixer constants, table size bit-twiddling, exact threshold boundaries, and nulling of backing arrays.
- **demo3 (OpenAI 5.6 Sol Max)** excels in compact high-rigor tests: it is one of the few black-box suites to test circular buffer wrap-around at array boundary (slot 15 of 16), extreme bit patterns (`MIN_VALUE`, `MAX_VALUE`, `-1`), and multiset value frequency tracking.

### 3. Tier 3: The Solid Workhorses — demo8, demo7, demo5
- **demo8 (Gemini 3.7 Flash High Kilo Code)** provides the most comprehensive constructor validation among black-box suites and includes an iterative middle-cluster removal test. However, it completely forgot to test snapshot immutability for `keys()` and `values()`.
- **demo7 (Qwen 38 max XHigh)** contains solid fuzzing (100k ops), but illustrates a critical testing pitfall: **it failed to catch that its own implementation was a chained hash map rather than an open-addressing map**. Because it never tested probe chain deletion or cluster repair, a completely non-compliant data structure passed all tests.
- **demo5 (Deepseek V4 Flash Max)** features a strong 2,000-key collision stride-deletion test and dual fuzzers, but suffers from a flat layout, no constructor tests, and no wrap-around tests.

### 4. Tier 4: The Deficient & Legacy Suites — demo4 and demo0
- **demo4 (Gemma 4 31B Thinking)** is severely deficient (10 tests, 111 lines). It uses String hash collisions that may not collide under hash mixing, has no cluster deletion tests, no fuzzing, and awkward double-negative assertions.
- **demo0 (Baseline)** reflects legacy JUnit 4 practices with unseeded random shuffles and missing null contract tests, though its collision overflow tests remain valuable.

---

## Gap Analysis: Missing Tests Across Suites

### 1. Universal Gaps (Missing Across All or Nearly All Suites)
1. **Maximum Capacity & Table Doubling Overflow**:
   - What happens when a map of capacity `1 << 30` attempts to resize? Does it throw an `IllegalStateException` or overflow into negative capacity? None of the suites test growth overflow behavior at the maximum capacity boundary.
2. **Concurrent Modification / Fail-Fast Behavior**:
   - The prompt specifies that `FastHashMap` is not thread-safe. However, none of the suites test or document behavior when the map is modified while iterating over `keys()` or `values()` streams (or whether snapshots prevent CME).
3. **Repeated Insertions and Deletions at Wrap-Around Boundary**:
   - While `demo2`, `demo3`, `demo6`, and `demo9` test simple wrap-around deletion, none perform extensive random churn strictly across the index `capacity - 1` to `0` boundary.
4. **Extreme Load Factors under High Density**:
   - Testing performance and termination when load factor is set very high (e.g. `0.95f`) where clusters merge into massive runs.
5. **Key Mutation**:
   - Verifying behavior when a key's mutable field is changed after insertion (confirming that the old hash slot is preserved or standard hash map lookup failure occurs).

### 2. Specific Gaps in Individual Suites
- **Missing Constructor Tests**: `demo0`, `demo2`, `demo3`, `demo4`, `demo5`, `demo6`, `demo7`.
- **Missing Wrap-Around Boundary Tests**: `demo0`, `demo1`, `demo4`, `demo5`, `demo7`, `demo8`, `demo11`.
- **Missing Snapshot Copy Mutation Tests**: `demo0`, `demo4`, `demo8`.
- **Missing Differential Fuzz Testing**: `demo0`, `demo4`.
- **Missing Clear Idempotency**: `demo0`, `demo1`, `demo4`, `demo5`, `demo7`, `demo8`, `demo9`.
- **Missing Return Value Verification on Overwrite**: `demo0`.

---

## Test Quality Evaluation

### 1. Assertion Rigor & Mutation Killing Power
- **High Rigor (`demo11`, `demo6`, `demo9`, `demo3`)**: Assertions verify return values on every mutation, check size invariants, assert that unrelated keys remain untouched, and verify snapshot independence.
- **Moderate Rigor (`demo1`, `demo2`, `demo5`, `demo7`, `demo8`)**: Strong checks on common paths, but occasional reliance on high-level set equality rather than step-by-step state checks.
- **Weak Rigor (`demo4`, `demo0`)**: Tests check basic retrieval without verifying return values of `put`/`remove` or testing cluster repair.

### 2. Algorithmic Collision Verification
- **Knuth 6.4R Correctness**: `demo9` is the only suite that tests the subtle displacement rule for interleaved natural homes.
- **Cluster Positions**: `demo1`, `demo6`, `demo8`, `demo9`, and `demo11` explicitly test removing the head, middle, and tail of a collision cluster.
- **Circular Probing**: `demo2`, `demo3`, `demo6`, and `demo9` correctly test circular buffer wrap-around.

### 3. Architecture & Cleanliness
- **Best in Class**: `demo1`, `demo9`, `demo11`, `demo2`, and `demo6` utilize JUnit 5 `@Nested` classes with clear `@DisplayName` descriptions representing BDD specifications.
- **AssertJ vs JUnit Jupiter**: `demo1`, `demo9`, and `demo11` benefit significantly from AssertJ fluent assertions (`assertThat(map.get(k)).isEqualTo(...)`, `containsExactlyInAnyOrder`), which produce much clearer failure messages than standard JUnit assertions.

### 4. Determinism and Reproducibility
- Suites `demo1`, `demo2`, `demo3`, `demo5`, `demo6`, `demo7`, `demo8`, `demo9`, and `demo11` all use fixed random seeds (`Random(42)`, etc.), guaranteeing 100% reproducible test execution.
- Only `demo0` introduced unseeded `Collections.shuffle()`.

---

## Architectural Blueprint for the "Ultimate" FastHashMap Test Suite

To build the definitive test suite for open-addressing hash maps, one should synthesize the strengths of the top suites:

```
FastHashMapTest (Ultimate Synthesis)
├── 1. ConstructorAndConfigurationTests (from demo9 & demo11)
│   ├── Default initial capacity and load factor
│   ├── Custom capacity rounded to power of two
│   ├── Boundary load factors (0.001f, 0.999f)
│   └── Invalid arguments (negative capacity, LF <= 0, LF >= 1, NaN)
├── 2. BasicCrudLifecycleTests (from demo2 & demo1)
│   ├── Empty map behavior (size 0, get null, remove null, views empty)
│   ├── Put single entry, get, and absent key check
│   ├── Put overwrite returns old value and maintains size
│   ├── Remove existing returns old value and decrements size
│   ├── Remove absent returns null and preserves size
│   ├── Remove on empty map returns null
│   └── Reinsert after removal
├── 3. NullContractTests (from demo1 & demo6)
│   ├── NPE on put(null, v), get(null), remove(null) with message check
│   ├── Null values supported (insert, retrieve, overwrite, remove)
│   └── Null value does NOT terminate linear probe chain (from demo6 T11)
├── 4. KeyEqualityAndContractTests (from demo2 & demo3)
│   ├── Equal but non-identical key instances hit same mapping
│   ├── Equal hash code with unequal keys remain distinct
│   ├── Keys without toString()
│   └── Extreme hash bit patterns (0, -1, MIN_VALUE, MAX_VALUE)
├── 5. CollisionAndProbingTests (from demo1, demo8 & demo9)
│   ├── Collision retrieval with forced hash key
│   ├── Overwrite within collision cluster
│   ├── Backward-shift: remove cluster head
│   ├── Backward-shift: remove cluster middle
│   ├── Backward-shift: remove cluster tail
│   ├── Backward-shift: interleaved natural homes Knuth 6.4R invariant (from demo9)
│   └── Shuffled removal of entire cluster (from demo1 & demo8)
├── 6. CircularBoundaryWrapAroundTests (from demo2, demo3 & demo9)
│   ├── Probing wraps from array end (capacity - 1) to index 0
│   └── Backward-shift deletion across circular array boundary
├── 7. ResizingAndDynamicGrowthTests (from demo11, demo2 & demo6)
│   ├── Exact threshold boundary triggering
│   ├── Rehash preserves collision clusters and null values
│   └── High volume insertion and removal (20k to 100k items)
├── 8. CollectionViewsTests (from demo2, demo3 & demo6)
│   ├── Empty map views are empty
│   ├── View sizes match map size
│   ├── Snapshot independence: mutating returned list does not affect map
│   ├── Snapshot independence: mutating map does not affect prior snapshot
│   ├── Positional alignment: keys().get(i) corresponds to values().get(i)
│   └── values() preserves duplicate and null multiplicity
├── 9. ClearOperationTests (from demo6 & demo11)
│   ├── Clear empties populated map and allows immediate reuse
│   ├── Clear is idempotent on empty map
│   └── Clear nulls backing array slots (preventing memory retention)
└── 10. DifferentialStressTests (from demo6 & demo2)
    ├── Seeded random operations against java.util.HashMap (100k ops)
    └── High-collision restricted-bucket differential fuzzer
```
