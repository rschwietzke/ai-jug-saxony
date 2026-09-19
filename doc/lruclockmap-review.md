# LRUClockMap Test Suite Comparative Review & Quality Analysis

## Executive Summary

This document presents an exhaustive technical review and comparative evaluation of all eleven `LRUClockMapTest.java` test suites across the project (`demo0` through `demo9`, plus `demo11`). 

Unlike `FastHashMap` (where each AI model implemented its own hash map from scratch), `LRUClockMap.java` was provided as a pre-existing component in each module. The task for each model was to create a proper and sufficient test suite for an open-addressing, linear-probing LRU cache based on the second-chance "clock" page-replacement algorithm.

### Master Scorecard & High-Level Comparison

| Module | AI Model / Provenance | Framework | Test Count | Lines | Black-Box vs. White-Box | Instruction Cov | PIT Mutation Score | Quality Tier | Primary Distinctions |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Baseline (Xceptance) | JUnit 4 | 29 | 861 | **Pure Black-Box** | 75.3% (527/700) | 84.6% (88/104) | **Tier 4 (Legacy)** | Heavy eviction stress scenarios; lacks `getRaw()`, null handling, and debug API verification; uses JUnit 4. |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | JUnit 5 + AssertJ | 19 (21 runs) | 499 | **Diagnostic Black-Box** | 97.9% (685/700) | 86.5% (90/104) | **Tier 2 (Strong)** | Clean `@Nested` BDD hierarchy; tests `expensiveGet` flag setting; continuous eviction fuzzer; AssertJ fluents. |
| **demo2** | Kimi K3 (Kilo Code) | JUnit 5 Jupiter | 38 | 761 | **Diagnostic Black-Box** | 96.9% (678/700) | 92.3% (96/104) | **Tier 1 (Elite Black-Box)** | Exhaustive black-box coverage; granular second-chance flag inspections; circular wrap-around; deterministic clock sweep checks. |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | JUnit 5 Jupiter | 25 | 582 | **Diagnostic Black-Box** | 98.3% (708/720) | 92.3% (96/104) | **Tier 2 (Strong)** | Compact, high-rigor tests; tests array boundary wrap-around deletion; tests maximum backing array overflow (`1 << 30`). |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | JUnit 5 Jupiter | 11 | 150 | **Pure Black-Box (Shallow)** | 58.5% (421/720) | 58.7% (61/104) | **Tier 4 (Deficient)** | Severely inadequate; misses branch conditions; pseudo-collision via String `"Aa"`/`"BB"`; lowest coverage and mutation score. |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | JUnit 5 Jupiter | 26 | 631 | **Diagnostic Black-Box** | 96.3% (693/720) | 81.7% (85/104) | **Tier 3 (Moderate)** | 26 solid tests; dual random fuzzers; verifies `lastInsertedEntryAlwaysSurvives`; flat class layout. |
| **demo6** | Claude Opus 5 Ultra (Claude) | JUnit 5 Jupiter | 39 (65 runs) | 1,156 | **Invariant White-Box** | 97.4% (701/720) | 94.2% (98/104) | **Tier 1 (Elite Algorithmic)** | Continuous probe-chain gap checks via `assertHealthy`; proves clock degradation for small N (< 24); 100k churn tests. |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | JUnit 5 Jupiter | 24 | 580 | **Diagnostic Black-Box** | 97.4% (701/720) | 91.3% (95/104) | **Tier 3 (Moderate)** | Clean test design; validates clock hand continuation across evictions; tests `occupiedSpace` doubling; flat class layout. |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | JUnit 5 Jupiter | 24 | 486 | **Diagnostic Black-Box** | 97.4% (701/720) | 84.6% (88/104) | **Tier 2 (Strong)** | Complete null contract validation (`getRawNullKeyThrows`); iterative middle-collision removal; misses snapshot isolation. |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | JUnit 5 + AssertJ | 47 (52 runs) | 1,078 | **Intrusive White-Box (Reflection)** | 100.0% (720/720) | **100.0% (104/104)** | **Tier 1 (Master / 100% Mutation)** | Perfect 100% mutation score; reflection tests for private bit-math; boundary wrap churn; realign second-chance preservation; 1024-slot toString cutoff. |
| **demo11** | Gemini 3.7 Flash High (100% PIT Rework) | JUnit 5 + AssertJ | 22 (24 runs) | 576 | **Intrusive White-Box (Reflection)** | 100.0% (683/683) | **100.0% (102/102)** | **Tier 1 (Master / 100% Mutation)** | Engineered specifically for 100% mutation score; validates capacity rounding math, mixer bitshifts, exact clock sequence, and `Wrapper.toString()`. |

---

## Detailed Per-Module Inventory & Analysis

### 1. demo0 (`org.jugsaxony.demo0.LRUClockMapTest`)
* **Framework**: JUnit 4 (`@Test`, `org.junit.Assert.*`)
* **Metrics**: 29 test methods, 861 lines, flat structure with test prefixes (`testConstructor_`, `testRemove_`, `testEviction_`).
* **Testing Paradigm**: **Pure Black-Box**. Only calls standard public CRUD operations. Completely ignores built-in testability hooks (`getRaw()`, `getDebugData()`, `trueSize()`, `occupiedSpace()`).

#### Implemented Tests:
1. `testConstructor_throwsExceptionForMaxSizeLessThan4`: Tests sizes 0, 1, 2, 3, -1 throw `IllegalArgumentException`.
2. `testConstructor_smallestSize`: Tests `maxSize = 4`.
3. `testSize_increasesAfterPut`: Puts 2 elements, verifies size increments.
4. `testSize_doesNotIncreaseOnUpdate`: Overwrites key, checks size.
5. `testSize_doesNotIncreaseOnUpdateWhenFull`: Overwrites when map is at `maxSize = 4`.
6. `testRemove_existingKey_OneElement`: Puts 1, removes, asserts size 0 and null lookup.
7. `testRemove_existingKey_MultipleElement`: Puts 3, removes middle, checks remaining.
8. `testRemove_Random`: Random insertions and removals.
9. `testRemove_EmptyMap`: Remove on empty map returns null.
10. `testRemove_nonExisting`: Remove absent key returns null.
11. `testPutAndGet_basicFunctionality`: Put/get 3 items.
12. `testGet_returnsNullForNonexistentKey`: Absent key returns null.
13. `testPut_updatesExistingValueAndReturnsOldValue`: Overwrite returns old value.
14. `testPutAndGet_handlesHashCollisions`: Collision key handling.
15. `testPutUpdate_handlesHashCollisions`: Overwrite colliding key.
16. `testGet_dontFindWithSameHash`: Miss on occupied collision bucket.
17. `testGet_findWithSameHashButTwoKeysAndFullMap`: Collision retrieval in full map.
18. `testEviction_Smallest`: Eviction in map of size 4.
19. `testEviction_NoneWhenOnlyUpdate`: Repeated updates at capacity do not evict.
20. `testEviction_withLargeMapAndOnlyPutMisses`: Fills 1,000 entries into map of size 100, verifies final size is 100.
21. `testEviction_whenMapIsFull`: Step-by-step eviction verification.
22. `testEviction_withHashCollisionsAndPushOut_StartAt_X`: Tests eviction in colliding clusters.
23. `testEviction_accessedElementSurvivesFirstEvictionPass`: Touches element via `get()`, pushes new entries, asserts accessed element survives initial eviction.
24. `testClear_emptiesMapAndResetsState`: Fills, clears, checks size=0.
25. `testClear_onEmptyMap_doesNothing`: Clear idempotency.
26. `testKeys_emptyMap`: Empty map keys view.
27. `testKeys_afterPuts`: Keys view contents.
28. `testKeys_afterUpdate`: Keys view after update.
29. `testKeys_afterClear`: Keys view after clear.

#### Strengths:
- Detailed eviction sequence verification in maps with hash collisions.
- Clear distinction between insert-triggered eviction and update stability.

#### Weaknesses & Gaps:
- **Missing `getRaw()`**: Does not test `getRaw()` or contrast it against `get()`.
- **Zero Null Contract Testing**: Completely ignores null keys and null values.
- **Missing Diagnostic APIs**: Does not test `occupiedSpace()`, `trueSize()`, `getDebugData()`, or `toString()`.
- **Legacy Framework**: JUnit 4 assertions and syntax.

---

### 2. demo1 (`org.jugsaxony.demo1.LRUClockMapTest`)
* **Framework**: JUnit 5 Jupiter + AssertJ
* **Metrics**: 19 test methods (21 executions), 499 lines, 6 `@Nested` classes with `@DisplayName`.
* **Testing Paradigm**: **Diagnostic Black-Box**. Interacts with public API and package-private diagnostic hooks (`getDebugData()`, `trueSize()`, `toString()`) without reflection or bytecode tampering.

#### Implemented Tests:
* **ConstructorTest**:
  1. `testInvalidMaxSize`: Parameterized test (-1, 0, 1, 2, 3) throwing IAE.
  2. `testValidInitialization`: Tests `maxSize = 4` initializes size 0, keys empty, and capacity 8.
* **BasicOperationsTest**:
  3. `testPutAndGetSingle`: Put/get lifecycle.
  4. `testPutOverwrite`: Overwrite returns old value, preserves size.
  5. `testRemoveExisting`: Remove existing returns value, decrements size.
  6. `testRemoveNonExisting`: Remove absent returns null.
  7. `testClear`: Clears map, verifies size 0, allows re-use.
  8. `testKeys`: Verifies snapshot copy is independent of subsequent map modifications.
* **FixedHashKey (Collision Tests)**:
  9. `testCollidingKeysGetAndPut`: 4 colliding keys retrieved accurately.
  10. `testCollidingKeyOverwrite`: Overwriting colliding keys along probe chain.
  11. `testExpensiveGetUpdatesSecondChance`: Explicitly tests that the slow linear-probing get path (`expensiveGet`) marks `secondChance = true`.
  12. `testCollidingKeyRemovalAndRealignment`: Removes head of collision cluster and verifies remaining keys are realigned.
* **EvictionTest**:
  13. `testEvictionOccursAtMaxSize`: Map of size 4 stays bounded at 4 when adding 5th element.
  14. `testSecondChanceProtection`: Compares touched vs untouched entries during eviction.
  15. `testGetRawDoesNotSetSecondChance`: Verifies `getRaw()` reads value without giving a second chance.
  16. `testOverwriteAtFullCapacity`: Updating full map does not trigger eviction.
* **DebugAndToStringTest**:
  17. `testToStringAndDebugData`: Verifies `toString()` format and `getDebugData()` slot integrity.
* **StressTest**:
  18. `testContinuousInserts`: Inserts 10,000 items into size 50 map; asserts size stays 50 and all slots match `trueSize()`.
  19. `testRandomOperations`: 10,000 random operations checking size invariants and `trueSize()`.

#### Strengths:
- Clean modern architecture with AssertJ assertions.
- Explicit test for `expensiveGet` second-chance flag setting.
- Verifies `getDebugData()` and `toString()`.

#### Weaknesses & Gaps:
- Does not test null key or null value rejection.
- Does not test circular wrap-around probing at the array boundary.

---

### 3. demo2 (`org.jugsaxony.demo2.LRUClockMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 38 test methods, 761 lines, 10 `@Nested` classes with `@DisplayName`.
* **Testing Paradigm**: **Diagnostic Black-Box (Exemplary)**. Uses package-level access to inspect `getDebugData()`, verifying `secondChance` bit state, clock hand movement, and internal array positions while keeping tests clean of reflection.

#### Implemented Tests:
* **ConstructorValidation**:
  1. `belowMinimumThrows`: Parameterized test (0, 1, 2, 3, -5).
  2. `minimumAccepted`: Tests `maxSize = 4`.
  3. `capacityIsPowerOfTwoAtHalfLoad`: Checks power-of-two capacity sizing across sizes (4, 7, 8, 9, 15, 16, 17).
* **BasicOperations**: `emptyOnCreate`, `putGetSingle`, `putOverwrite`, `getMissing`, `sizeTracking`, `keysContent`, `keysListIsACopy`.
* **contract**:
  4. `putNullKeyThrows`, `getNullKeyThrows`, `getRawNullKeyThrows`, `removeNullKeyThrows`: Validates NPE for null keys across all 4 entry points.
* **RemoveOperations**:
  5. `removeExisting`, `removeMissing`, `removeTwice`, `removeKeepsProbeChainIntact`, `removeThenReinsert`, `removeAll`.
* **ClearOperation**: `clearEmptiesMap`, `reusableAfterClear`, `clearOnEmpty`.
* **CollisionHandling**:
  6. `collisionCluster`: 6 keys with fixed hash 42.
  7. `wrapAroundProbing`: Tests keys placed at the end of the backing array wrapping to index 0.
  8. `equalsBasedLookup`: Equality vs identity lookup.
* **Eviction**:
  9. `neverExceedsMaxSize`, `evictsExactlyOne`, `deterministicClockOrder`.
  10. `secondChanceProtection`: Accesses key 1, adds key 5, asserts key 1 survives and key 2 is evicted.
  11. `updateWhenFullDoesNotEvict`: Overwriting full map preserves all keys.
  12. `evictionChurnStaysConsistent`: 500 inserts into size 10 map; verifies size=10, `trueSize()=10`, and `keys().size()=10`.
* **SecondChanceFlags**:
  13. `flagsTrueAfterInsert`: Uses `getDebugData()` to prove fresh entries have `secondChance == true`.
  14. `flagSemantics`: Proves `get()` flips flag to true, `getRaw()` does not touch flag.
  15. `overwriteRefreshesFlag`: Proves update resets flag to true.
* **DebugViews**: `toStringSmoke`, `debugDataConsistency`.
* **StressInvariants**: `randomizedInvariantCheck` (10,000 operations).

#### Strengths:
- **Best Black-Box Suite**: 38 granular tests covering every public contract.
- Direct inspection of second-chance flags via `getDebugData()` across insert, get, getRaw, and overwrite.
- Complete null key rejection checks across all methods.
- Array wrap-around probing test.

#### Weaknesses & Gaps:
- Does not test null value rejection (`put(key, null)`).
- Does not test backing array overflow at `1 << 30`.

---

### 4. demo3 (`org.jugsaxony.demo3.LRUClockMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 25 test methods, 582 lines, 5 `@Nested` classes.
* **Testing Paradigm**: **Diagnostic Black-Box**. Validates public contracts, diagnostic APIs (`trueSize()`, `getDebugData()`, `toString()`), and array overflow limits without using reflection.

#### Implemented Tests:
* **Construction**:
  1. `rejectsSizesBelowFour`: Validates IAE for sizes < 4.
  2. `rejectsSizesThatNeedAnUnsupportedBackingArray`: Tests capacity overflow threshold (`1 << 29` + 1) throwing IAE.
  3. `createsPowerOfTwoStorageAtNoMoreThanHalfLoad`: Validates power-of-two sizing.
* **BasicOperations**:
  4. `putGetAndGetRawReturnStoredValues`: CRUD lifecycle.
  5. `replacingAMappingReturnsTheOldValueWithoutChangingSize`.
  6. `replacingAtMaximumSizeDoesNotEvictAnotherMapping`.
  7. `removeReturnsOldValueAndUpdatesBothSizeMeasures`: Asserts `size()` and `trueSize()`.
  8. `equalButNonIdenticalKeysAddressTheSameMapping`.
* **NullHandling**:
  9. `rejectsNullKeysWithoutChangingExistingMappings`: NPE on get, getRaw, put, remove.
  10. `rejectsNullValuesWithoutChangingExistingMappings`: Tests putting null value.
* **Collisions**:
  11. `collidingKeysCanBeInsertedRetrievedAndReplaced`.
  12. `removingHeadMiddleAndTailRepairsCollisionCluster`.
  13. `removalRepairsAClusterThatWrapsAroundStorageBoundary`: Probing and deletion across circular buffer boundary.
  14. `arbitraryCollisionRemovalOrderPreservesRemainingMappings`: Shuffled removal order (`Random(73L)`).
* **Eviction**:
  15. `addingToAFullMapEvictsOneEntryAndNeverExceedsMaximumSize`.
  16. `getGivesAColdEntryASecondChance`.
  17. `getRawDoesNotGiveAColdEntryASecondChance`.
  18. `updatingAColdEntryGivesItASecondChance`.
  19. `repeatedInsertionsKeepTheConfiguredMaximum`.
* **ViewsAndClear**:
  20. `keysReturnsAMutableSnapshotIndependentOfTheMap`.
  21. `clearRemovesEverythingAndAllowsReuseWithoutChangingCapacity`.
  22. `clearOnAnEmptyMapIsSafe`.
  23. `debugDataMatchesOccupiedSlotsAndCalculatedHomePositions`.
  24. `toStringProvidesBoundedDiagnosticState`.
  25. `randomizedOperationsPreservePublicInvariants`.

#### Strengths:
- **Array Size Overflow Check**: One of the only suites to test `rejectsSizesThatNeedAnUnsupportedBackingArray` (max capacity bounds).
- Tests wrap-around cluster repair across storage boundary.
- Verifies null value rejection.

#### Weaknesses & Gaps:
- Lacks `@DisplayName` annotations.
- Randomized invariant test is relatively short (10k ops).

---

### 5. demo4 (`org.jugsaxony.demo4.LRUClockMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 11 test methods, 150 lines, flat structure.
* **Testing Paradigm**: **Pure Black-Box (Shallow)**. Only uses basic CRUD methods and `getRaw()`. Completely avoids all diagnostic APIs (`trueSize`, `occupiedSpace`, `getDebugData`).

#### Implemented Tests:
1. `testConstructor`: maxSize 3 throws, maxSize 4 succeeds.
2. `testBasicPutAndGet`: 2 entries put and read.
3. `testPutUpdate`: Overwrite check.
4. `testRemove`: Remove check.
5. `testNullsThrow`: NPE on put/get null.
6. `testEviction`: Inserts 5 items into size 4 map, checks size <= 4.
7. `testClockAlgorithmSecondChance`: Touches item 1, inserts item 5, checks item 1 survives.
8. `testGetRawDoesNotAffectLRU`: Reads via `getRaw`, checks evicted.
9. `testClear`: Clear check.
10. `testCollisionHandlingAndRemoval`: Uses String `"Aa"` and `"BB"`.
11. `testLargeVolume`: Puts 1,000 entries into size 100 map, checks size 100.

#### Strengths:
- Minimal basic smoke test.

#### Weaknesses & Severe Deficiencies:
- **Lowest Quality in Project**: 58.5% instruction coverage and 58.7% mutation score.
- **Pseudo-Collision Flaw**: Uses String `"Aa"` and `"BB"` which may not collide under hash mixing.
- **No Cluster Position Deletion**: Never tests head/middle/tail removal.
- **No Invariant Verification**: Ignores `trueSize()`, `occupiedSpace()`, and `getDebugData()`.

---

### 6. demo5 (`org.jugsaxony.demo5.LRUClockMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 26 test methods, 631 lines, flat structure.
* **Testing Paradigm**: **Diagnostic Black-Box**. Validates public methods, `occupiedSpace()`, and `toString()`. Does not inspect `getDebugData()`.

#### Implemented Tests:
1. `constructorRejectsMaxSizeBelowFour`, `constructorAcceptsMaxSizeFour`.
2. `emptyMap`, `putAndGet`, `putReturnsPreviousValue`.
3. `getRawReadsWithoutLruEffect`.
4. `nullKeyRejectedByAllMethods`, `nullValueRejectedByPut`.
5. `removeExistingEntry`, `removeAbsentEntry`, `removeManyEntriesKeepsMapIntact`.
6. `clearEmptiesMapAndReusesStorage`, `keysReflectCurrentContents`.
7. `newEntriesStartWithSecondChance`, `getSetsSecondChanceButGetRawDoesNot`.
8. `evictionKeepsSizeBounded`, `lastInsertedEntryAlwaysSurvives`, `accessedEntrySurvivesEviction`, `evictionRemovesExactlyOneEntryPerInsertAtCapacity`.
9. `collidingKeysAreStoredAndRemovedCorrectly`: 12 keys with hash 42, removes every 2nd key.
10. `largeCapacityMap`: maxSize 10,000, inserts 15,000 entries.
11. `occupiedSpaceIsPowerOfTwoAndHasHeadroom`.
12. `randomOperationsMatchHashMapWhenNotFull`, `randomCollisionOperationsMatchHashMapWhenNotFull`, `randomEvictionStaysBounded`.
13. `toStringDoesNotThrow`.

#### Strengths:
- Clean second-chance flag verification.
- Proves `lastInsertedEntryAlwaysSurvives` and `evictionRemovesExactlyOneEntryPerInsertAtCapacity`.
- Dual random fuzzers (general and collision-heavy).

#### Weaknesses & Gaps:
- Flat structure without `@Nested` grouping.
- Does not test circular buffer wrap-around.

---

### 7. demo6 (`org.jugsaxony.demo6.LRUClockMapTest`)
* **Framework**: JUnit 5 Jupiter (`@Test`, `@ParameterizedTest`, `@Nested`, `@DisplayName`, `@Tag`)
* **Metrics**: 39 test methods (65 executions), 1,156 lines, 8 `@Nested` classes.
* **Testing Paradigm**: **Invariant White-Box (Algorithmic Engine)**. Uses `getDebugData()` to build an invariant assertion engine (`assertHealthy`), actively tracing linear probe chains from each slot to its natural home position on every operation.

#### Implemented Tests:
* **Construction**: `tooSmallMaxSize`, `smallestLegalMaxSize`, `tableSize`, `freshMapIsEmpty`.
* **PutAndGet**: `singleRoundTrip`, `putReturnsPreviousValue`, `manyEntries` (1,000 items), `equalButNotIdenticalKey`, `unknownKey`.
* **NullHandling**: `nullKeysAreRejected`, `nullValuesAreRejected`.
* **BoundsAndEviction**:
  - `oneTooMany`: Adding 5th entry to size 4 map.
  - `sizeStaysBounded`: Parameterized test across sizes 4, 7, 8, 15, 16, 31, 32, 64, 100.
  - `survivorsAreConsistent`: Verifies all surviving entries match expected data.
  - `updateOnFullMapDoesNotEvict`.
  - `untouchedEntriesDie`: Inserts working set, flushes with fresh keys, verifies untouched die.
  - `aHotWorkingSetSurvives`: Repeatedly touches half the entries while churning the other half.
  - `smallMapsGiveWeakerProtection`: **Groundbreaking Empirical Test**: Documents and tests why the clock algorithm cannot reliably protect entries for `maxSize < 24` due to rapid clock sweep wrap-around!
* **SecondChance**: `freshEntryHasSecondChance`, `getMarksAndGetRawDoesNot`, `putMarks`, `theClockClearsFlags`.
* **Removal**: `removeExisting`, `removeUnknown`, `removeTwice`, `removeInTheMiddleOfAChain`, `removeHeadAndTailOfAChain`, `removeInsideAWrappedCluster`, `reinsertAfterRemoval`, `churnDoesNotLeakSlots` (100,000 put/remove cycles).
* **KeysAndClear**: `keysReportsTheContent`, `keysIsASnapshot`, `clearEmptiesTheMap`, `usableAfterClear`, `toStringWorks`.
* **MixedWorkloads**: `collisionsAndEviction`, `randomWorkload` (50,000 ops), `aFittingWorkingSetSurvives`, `largeMap` (10,000 maxSize).

#### Strengths:
- **Continuous Invariant Checking**: `assertHealthy` inspects `getDebugData()` after every step, tracing every occupied slot back to its `truePosition` to guarantee no probe chain has a gap.
- **Empirical Algorithmic Proof**: Identifies and proves the boundary condition where the second-chance algorithm breaks down for small capacities (`maxSize < 24`).
- **Heavy Churn**: 100,000-cycle churn test proving zero slot leaks and constant memory.

#### Weaknesses & Gaps:
- High execution overhead due to continuous `assertHealthy` validation.
- Does not test backing array overflow at `1 << 30`.

---

### 8. demo7 (`org.jugsaxony.demo7.LRUClockMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 24 test methods, 580 lines, flat structure.
* **Testing Paradigm**: **Diagnostic Black-Box**. Uses public API, `occupiedSpace()`, and `toString()`.

#### Implemented Tests:
1. `constructorRejectsMaxSizeBelowFour`, `constructorAcceptsMinimumMaxSize`, `occupiedSpaceIsPowerOfTwoOfAtLeastDoubleMaxSize`.
2. `newMapIsEmpty`, `putStoresValueAndGetReturnsIt`, `putReplacesExistingValueAndReturnsOldOne`, `getMissingKeyReturnsNull`.
3. `nullKeysAreRejected`, `nullValuesAreRejected`.
4. `collidingKeysAreFoundViaProbing`.
5. `removeRepairsProbeChainAfterHeadRemoval`, `removeRepairsProbeChainAfterMiddleAndTailRemoval`, `removeReturnsValueAndShrinksMap`.
6. `evictionKeepsSizeAtMaxAndDropsUntouchedEntry`.
7. `evictionContinuesWhereClockLeftOff`: Verifies that the clock hand does not reset to 0 between evictions.
8. `secondChanceProtectsRecentlyUsedEntry`, `updateOnFullMapDoesNotEvict`.
9. `newEntriesStartWithSecondChance`, `getSetsSecondChanceWhileGetRawDoesNot`.
10. `keysListsAllEntriesWithoutDuplicates`, `clearEmptiesMapButKeepsCapacity`, `mapIsFullyUsableAfterClearIncludingEviction`.
11. `randomOperationsKeepMapInvariants` (50,000 ops), `toStringContainsMapState`.

#### Strengths:
- Explicit verification of clock hand continuity (`evictionContinuesWhereClockLeftOff`).
- Clean separation of head vs middle/tail probe chain repairs.

#### Weaknesses & Gaps:
- Flat structure without `@Nested` classes.
- Does not test circular wrap-around at the array boundary.

---

### 9. demo8 (`org.jugsaxony.demo8.LRUClockMapTest`)
* **Framework**: JUnit 5 Jupiter
* **Metrics**: 24 test methods, 486 lines, flat structure with banner comments.
* **Testing Paradigm**: **Diagnostic Black-Box**. Exercises public APIs, `getDebugData()`, and `toString()`.

#### Implemented Tests:
1. `constructorInvalidMaxSizeThrows`, `constructorValidMinSize`, `initialProperties`.
2. `putAndGetSingleEntry`, `putAndGetMultipleEntries`, `putOverwritesExistingValue`, `putUpdateAtCapacityDoesNotEvict`.
3. `putNullKeyThrows`, `putNullValueThrows`, `getNullKeyThrows`, `getRawNullKeyThrows`, `removeNullKeyThrows`.
4. `removeExistingEntry`, `removeNonExistingEntry`, `removeOnEmptyMap`.
5. `evictionWhenExceedingMaxSize`, `secondChanceFlagProtectsAccessedEntryFromEviction`, `getRawDoesNotAffectSecondChanceFlag`.
6. `collisionLinearProbingAndExpensiveGet`: Tests linear probing and `expensiveGet`.
7. `collisionRemovalMaintainsSearchInvariants`: Iteratively removes colliding elements from middle.
8. `keysReturnsAllPresentKeys`, `clearEmptiesMap`, `toStringAndDebugData`.
9. `continuousHeavyEvictionStressTest`: Inserts 10,000 elements into size 10 map.

#### Strengths:
- Complete null contract testing across all entry points (`getRawNullKeyThrows`, `putNullValueThrows`).
- Iterative middle-collision removal test.

#### Weaknesses & Gaps:
- Does not test snapshot immutability for `keys()`.
- Missing circular wrap-around test.

---

### 10. demo9 (`org.jugsaxony.demo9.LRUClockMapTest`)
* **Framework**: JUnit 5 Jupiter + AssertJ
* **Metrics**: 47 test methods (52 executions), 1,078 lines, 7 `@Nested` classes with `@DisplayName`.
* **Testing Paradigm**: **Intrusive White-Box (Reflection-Assisted 100% Mutation)**. Combines extensive diagnostic inspection (`getDebugData()`, `trueSize()`, `occupiedSpace()`) with direct Java reflection on private static utility methods (`arraySize`, `nextPowerOfTwo`, `mixHash`, `Wrapper.toString()`) to achieve a perfect 100% mutation kill score.

#### Implemented Tests:
* **ConstructorTests**:
  1. `maxSizeBelowFourThrowsIllegalArgumentException` (-100, -1, 0, 1, 2, 3).
  2. `validMaxSizeInitializesCorrectCapacityAndState`: Checks exact capacity calculations (4->8, 5->16, 8->16, 9->32, 16->32, 17->64).
  3. `excessivelyLargeMaxSizeThrowsIllegalArgumentException`: Tests `600_000_000` throwing IAE.
  4. `arraySizeBoundaryExactLimit`: Reflection test verifying `arraySize(1 << 29, 0.5f)` equals `1 << 30`.
  5. `nextPowerOfTwoDirectTestViaReflection`: Validates power-of-two bit twiddling on 0, 1, 2, 3, 5, 7, 8, 9, 1023, 1024, 1025, and powers of 2 up to `1 << 30`.
* **NullContractTests**:
  6. `putNullKeyThrowsNullPointerException`, `putNullValueThrowsNullPointerException`, `getNullKeyThrowsNullPointerException`, `getRawNullKeyThrowsNullPointerException`, `removeNullKeyThrowsNullPointerException`.
* **BasicOperationsTests**:
  7. `emptyMapBehavior`, `putAndGetSingleEntry`, `putOverwriteReturnsOldValueAndPreservesSize`, `putMultipleDistinctEntriesWithinCapacity`, `removeExistingEntry`, `removeNonExistentEntryReturnsNull`, `clearEmptiesMapAndAllowsReuse`, `keysReturnsDefensiveCopy`.
* **CollisionAndProbingTests**:
  8. `mixHashCalculationDirectTestViaReflection`: Direct unit test of hash mixing.
  9. `negativeHashCodeHandledWithoutException`: Keys with negative hash codes.
  10. `collisionLinearProbingOnInsertAndGet`, `collisionOverwriteKeyAlongProbeChain`.
  11. `wrapAroundAtArrayBoundaryOnInsertAndGet`: Tests wrap-around at table end.
* **RemovalAndRealignTests**:
  12. `removeHeadOfCollisionClusterRealignsSubsequentElements`.
  13. `removeMiddleOfCollisionClusterRealignsSubsequentElements`.
  14. `removeTailOfCollisionClusterLeavesPrecedingIntact`.
  15. `removeWithClusterWrappingAroundArrayBoundary`: Cluster spanning index `capacity - 1` to `0`.
  16. `removeNonExistentKeyInOccupiedClusterReturnsNull`.
  17. `realignPreservesSecondChanceFlag`: Proves that shifting an entry during removal retains its `secondChance` bit!
* **LRUClockEvictionTests**:
  18. `insertBeyondMaxSizeTriggersEviction`.
  19. `updateExistingKeyAtMaxSizeDoesNotTriggerEviction`.
  20. `updateProbedKeyAtMaxSizeDoesNotTriggerEviction`: Updating a linearly probed key at capacity does not evict.
  21. `getProtectsEntryFromEvictionViaSecondChance`.
  22. `expensiveGetProtectsEntryFromEviction`: Proves `expensiveGet` grants second chance.
  23. `getRawDoesNotProtectEntryFromEviction`.
  24. `clockHandContinuityAcrossSuccessiveEvictions`.
  25. `evictionRealignsCollisionCluster`: Eviction triggers Knuth 6.4R realignment of subsequent items.
* **DiagnosticsAndDebugTests**:
  26. `occupiedSpaceMatchesBackingArrayLength`, `trueSizeAlwaysMatchesActualOccupiedSlots`.
  27. `getDebugDataReturnsAccurateWrappersAndNulls`.
  28. `toStringFormatsCorrectlyForSmallMap`.
  29. `toStringTruncatesAt1024SlotsForVeryLargeMap`: Tests 1024-slot truncation logic.
  30. `wrapperToStringDirectTestViaReflection`.
* **StressAndDifferentialTests**:
  31. `differentialStressTestAgainstInvariants` (20,000 ops).
  32. `highCollisionContinuousChurn`.
  33. `boundaryWrapAroundContinuousChurn`.
  34. `minimumMaxSizeOperations`: Churn on minimum capacity map (`maxSize = 4`).

#### Strengths:
- **100% Mutation Kill Rate & 100% Coverage**: Kills all 104 mutants in PIT.
- **Unmatched Algorithmic Depth**: Tests `realignPreservesSecondChanceFlag`, `expensiveGetProtectsEntryFromEviction`, and `evictionRealignsCollisionCluster`.
- **Reflection on Private Limits**: Validates `arraySize` boundary at `1 << 30`, `nextPowerOfTwo`, and `mixHash`.
- Verifies exact 1024-slot truncation in `toString()`.

#### Weaknesses & Gaps:
- Relies on reflection to achieve 100% mutation kill on private static utility methods.

---

### 11. demo11 (`org.jugsaxony.demo1.LRUClockMapTest`)
* **Framework**: JUnit 5 Jupiter + AssertJ
* **Metrics**: 22 test methods (24 executions), 576 lines, 6 `@Nested` classes.
* **Testing Paradigm**: **Intrusive White-Box (Reflection-Assisted)**. Extends demo1 with reflection on private static `arraySize` and deep clock step-by-step state verification to achieve 100% PIT mutation kill rate.

#### Implemented Tests:
* Derived from `demo1`, enhanced for 100% PIT mutation kill rate (102/102 killed):
  1. `testCapacityCalculations`: Parameterized test verifying exact `occupiedSpace()` for sizes 4, 5, 8, 9, 10, 16, 17, 32.
  2. `testMixHashHighBits`: Tests hash mixing on high bits.
  3. `testArraySizeCalculations`: Tests capacity calculation throwing on excessive size via reflection.
  4. `testClockEvictionExactSequence`: Traces clock hand position and second-chance flag clearance step-by-step.
  5. `testUpdateWithCollisionAtFullCapacity`: Overwriting colliding entry when full.
  6. `testWrapperToString`: Tests `DebugWrapper.toString()` and `Wrapper.toString()`.

#### Strengths:
- 100% PIT mutation kill rate and 100% JaCoCo coverage.
- Step-by-step clock sequence test tracing exact clock hand pointer moves.

#### Weaknesses & Gaps:
- Missing circular wrap-around deletion tests across array boundaries.
- Does not test null value rejection.

---

## Cross-Comparison Matrix

| Category / Feature | demo0 | demo1 | demo2 | demo3 | demo4 | demo5 | demo6 | demo7 | demo8 | demo9 | demo11 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Testing Paradigm: Pure Black-Box** | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Testing Paradigm: Diagnostic API Hooks** | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Testing Paradigm: Invariant White-Box Engine** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Testing Paradigm: Intrusive Private Reflection**| ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Constructor: maxSize < 4 Rejection** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Constructor: maxSize == 4 Minimum** | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Constructor: Excessive Size (> 1<<30)**| ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Capacity Math / Power-of-Two Size** | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Basic Put, Get & Overwrite** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Overwrite At Capacity Does Not Evict**| ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Remove Existing & Decrement Size** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Remove on Empty Map** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| **Clear Idempotency on Empty Map** | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Null Key Rejected on Put/Get/Del** | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Null Key Rejected on getRaw()** | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
| **Null Value Rejected on Put** | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| **getRaw() Reads Without LRU Effect** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Fresh Entry Has Second Chance == true**| ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| **get() Flips Second Chance to true** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **expensiveGet() Flips Second Chance** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Overwrite Resets Second Chance to true**| ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Eviction Keeps Size Bounded** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Untouched / Cold Entries Evicted** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Touched / Hot Entries Survive** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Clock Hand Continuity Across Evictions**| ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ |
| **Collision Probing On Insert/Get** | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Cluster Head Removal & Realignment** | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Cluster Middle Removal & Realignment** | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Cluster Tail Removal** | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ |
| **Realign Preserves Second Chance Bit** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| **Circular Boundary Wrap-Around Probing**| ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| **Circular Boundary Wrap-Around Deletion**| ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ |
| **keys() Returns Defensive Copy** | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ |
| **Diagnostics: occupiedSpace()** | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| **Diagnostics: trueSize() Matches size()**| ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ |
| **Diagnostics: getDebugData() Validated** | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ |
| **Diagnostics: toString() Output Tested** | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Diagnostics: toString() 1024 Truncation**| ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| **Continuous Heavy Eviction Stress Test** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **PIT Mutation Score** | 84.6% | 86.5% | 92.3% | 92.3% | 58.7% | 81.7% | 94.2% | 91.3% | 84.6% | **100.0%**| **100.0%**|

---

## In-Depth Comparative Analysis

### 1. Tier 1: The Champions — demo9 vs demo6 vs demo2
- **demo9 (Gemini 3.8 Flash High Antigravity)** represents the pinnacle of mutation testing rigor in this project, achieving a perfect **100.0% PIT score (104/104 killed)** and **100.0% JaCoCo coverage**. It is the only test suite that:
  1. Proves that realigning an entry during cluster deletion preserves its `secondChance` bit (`realignPreservesSecondChanceFlag`).
  2. Tests `expensiveGet` setting the second chance flag on the slow probe path.
  3. Tests the 1024-slot cutoff in `toString()` for very large maps.
  4. Tests the `1 << 30` capacity overflow limit in `arraySize`.
- **demo6 (Claude Opus 5 Ultra)** is the most conceptually profound test suite. Its `assertHealthy` validation algorithm traces every single occupied entry back to its home slot on every mutation, verifying zero broken chains. Furthermore, demo6 empirically discovers and tests that second-chance protection degrades and fails for `maxSize < 24` due to rapid clock hand sweep wrap-around.
- **demo2 (Kimi K3)** is the cleanest and most comprehensive pure black-box suite (38 tests). It provides granular second-chance flag inspections, circular wrap-around probing, and deterministic clock order tests without relying on private method reflection.

### 2. Tier 2: The Strong Contenders — demo11, demo3, demo1, demo8
- **demo11 (Gemini 3.7 Flash High Rework)** achieved 100% mutation score by adding exact clock eviction sequences, capacity calculations, and mixer bit-shift tests to demo1.
- **demo3 (OpenAI 5.6 Sol Max)** delivers exceptional testing density: array boundary cluster repair at slot 15 of 16, power-of-two capacity math, and capacity overflow rejection.
- **demo1 (Gemini 3.7 Flash High Antigravity)** established the clean BDD `@Nested` AssertJ layout, but missed null contracts and circular wrap-around tests.
- **demo8 (Gemini 3.7 Flash High Kilo Code)** features exhaustive null handling tests (including `getRawNullKeyThrows` and `putNullValueThrows`), but misses snapshot isolation.

### 3. Tier 3: The Solid Mid-Tier — demo7 and demo5
- **demo7 (Qwen 38 max XHigh)** contains 24 clean tests with clock hand continuity verification, but lacks `@Nested` organization and misses circular buffer wrap-around.
- **demo5 (Deepseek V4 Flash Max)** has 26 tests with dual random fuzzers, proving that the last inserted entry always survives eviction, but lacks structural grouping.

### 4. Tier 4: The Deficient Suites — demo4 and demo0
- **demo4 (Gemma 4 31B Thinking)** is severely deficient (11 tests, 58.5% coverage, 58.7% mutation score). It uses non-guaranteed String `"Aa"`/`"BB"` collisions and lacks cluster repair testing.
- **demo0 (Xceptance Baseline)** represents the original legacy test bench: strong on eviction scenarios, but completely blind to null contracts, `getRaw()`, and diagnostic APIs.

---

## Gap Analysis: Missing Tests Across Suites

### 1. Universal Gaps
1. **Clock Hand Behavior Across `clear()`**:
   - In `LRUClockMap.java`, `clear()` nulls `data` and resets `size = 0`, but does **NOT** reset `clockHand = 0`! Only `demo9` touches upon clock continuity, but none of the suites explicitly test whether `clockHand` retains its pointer offset after `clear()`.
2. **Key Mutation During Caching**:
   - Mutating a key's identity while cached breaks linear probing search chains. No suite tests or warns about key mutability.
3. **Eviction Loop Termination Under Infinite Loops**:
   - What if all entries have `secondChance == true`? The clock algorithm sweeps through, flips all to `false`, and evicts on the second pass. While several tests assert that eviction finishes, none test worst-case sweep latency across massive tables.

### 2. Specific Gaps in Individual Suites
- **Missing `getRaw()` Verification**: `demo0`.
- **Missing Null Key Rejection**: `demo0`, `demo1`, `demo11`.
- **Missing Null Value Rejection**: `demo0`, `demo1`, `demo2`, `demo4`, `demo11`.
- **Missing Circular Wrap-Around Deletion**: `demo0`, `demo1`, `demo4`, `demo5`, `demo7`, `demo8`, `demo11`.
- **Missing Diagnostic API Tests (`trueSize`, `getDebugData`)**: `demo0`, `demo4`, `demo7`.
- **Missing Snapshot Copy Mutation Protection**: `demo0`, `demo4`, `demo5`, `demo7`, `demo8`.

---

## Test Quality Evaluation

### 1. Clock Algorithm Eviction Rigor
- **Elite (`demo9`, `demo6`, `demo2`)**: Explicitly verifies second-chance bit toggling, contrasts `get()` vs `getRaw()`, asserts clock hand pointer advancement, and tests eviction cluster realignment.
- **Moderate (`demo1`, `demo3`, `demo5`, `demo7`, `demo8`, `demo11`)**: Checks that touched entries survive and size remains bounded, but lacks granular pointer tracking.
- **Weak (`demo4`, `demo0`)**: Checks basic size bounding without proving second-chance mechanics.

### 2. Probing and Realignment Integrity
- The defining challenge in an open-addressing LRU map is that **both eviction and removal create holes** that require backward shifting/realignment.
- `demo9`, `demo6`, and `demo3` excel by testing that deletion and eviction across the array boundary properly realign subsequent entries without dropping second-chance flags.

### 3. Architecture & Style
- `demo9`, `demo1`, `demo11`, `demo2`, and `demo6` utilize JUnit 5 `@Nested` classes with descriptive `@DisplayName` annotations.
- `demo9`, `demo1`, and `demo11` leverage AssertJ fluent assertions (`assertThat`), providing actionable diffs upon assertion failures.

### 4. Black-Box vs. White-Box Testing Strategies: Four Distinct Paradigms

The eleven suites demonstrate four fundamentally different philosophical approaches to testing visibility, encapsulation, and internal mechanics:

```
                      Black-Box vs. White-Box Spectrum
[Pure Black-Box] ──> [Diagnostic Black-Box] ──> [Invariant White-Box] ──> [Intrusive Reflection]
  demo0, demo4         demo1, demo2, demo3        demo6                     demo9, demo11
                       demo5, demo7, demo8
```

#### Paradigm A: Pure Black-Box Testing (`demo0`, `demo4`)
* **Philosophy**: Restricts tests strictly to the standard map operations (`put`, `get`, `remove`, `size`, `clear`, `keys`) without observing internal states.
* **Critique & Limitations**: For complex algorithmic structures like an open-addressing LRU clock map, pure black-box testing exhibits severe blind spots:
  - In `demo4`, black-box tests passed while leaving 41.5% of instructions and 41.3% of mutants untouched. Mutants that corrupted clock hand pointer progression or failed to clear `secondChance` bits survived unnoticed.
  - In `demo0`, despite 861 lines of code, the suite never noticed the existence of `getRaw()`, `occupiedSpace()`, or `getDebugData()`.

#### Paradigm B: Diagnostic Black-Box Testing (`demo1`, `demo2`, `demo3`, `demo5`, `demo7`, `demo8`)
* **Philosophy**: Leverages non-intrusive diagnostic hooks intentionally built into the class by the author (`getDebugData()`, `trueSize()`, `occupiedSpace()`, `getRaw()`).
* **Why it Excels**:
  - `LRUClockMap` provides `getDebugData()` specifically to allow observers to inspect slot positions and `secondChance` flags without exposing or modifying internal arrays.
  - `demo2` and `demo3` master this approach: they assert that `secondChance` was set to `true` on `get()` and unchanged on `getRaw()`, verify that `trueSize()` equals `size()`, and confirm that eviction drops the correct slot—all while maintaining package-level encapsulation and requiring zero reflection.

#### Paradigm C: Invariant White-Box Verification (`demo6`)
* **Philosophy**: Rather than checking outcomes after the fact, `demo6` introduces an automated verification engine (`assertHealthy`) that runs after *every* mutation.
* **Why it Excels**:
  - It uses `getDebugData()` to programmatically trace linear probe chains from each occupied slot back to its natural home position, asserting that no tombstone or gap breaks the chain.
  - It empirically discovers and asserts the boundary condition where the second-chance algorithm breaks down for small capacities (`maxSize < 24`).
  - It bridges black-box API interactions with continuous white-box structural guarantees.

#### Paradigm D: Intrusive Reflection Testing (`demo9`, `demo11`)
* **Philosophy**: Uses Java reflection (`setAccessible(true)`) to directly invoke private static methods (`arraySize`, `nextPowerOfTwo`, `mixHash`, `Wrapper.toString()`).
* **Trade-Off Analysis**:
  - **Pros**: It enables 100.0% branch coverage and kills 100.0% of PIT mutants (104/104 in `demo9`), reaching boundary conditions (such as the `1 << 30` capacity ceiling in `arraySize`) that are difficult to trigger through public constructors.
  - **Cons**: It tightly couples the test suite to private implementation details. If a maintainer renames or refactors `nextPowerOfTwo` to use `Integer.numberOfLeadingZeros`, the reflection tests will break at runtime despite the public contract remaining unchanged.

---

## Architectural Blueprint for the "Ultimate" LRUClockMap Test Suite

```
LRUClockMapTest (Ultimate Synthesis)
├── 1. ConstructorAndCapacityTests (from demo9 & demo3)
│   ├── maxSize < 4 throws IllegalArgumentException
│   ├── maxSize == 4 initializes capacity 8
│   ├── Power-of-two capacity math (arraySize at 0.50f load factor)
│   └── Excessively large maxSize (> 1 << 29) throws IllegalArgumentException
├── 2. NullContractTests (from demo8 & demo2)
│   ├── put(null, v), get(null), getRaw(null), remove(null) throw NPE
│   └── put(k, null) throws NPE or IllegalArgumentException
├── 3. BasicCrudLifecycleTests (from demo2 & demo1)
│   ├── Empty map behavior (size 0, trueSize 0, get null, getRaw null)
│   ├── Put single entry, get, getRaw, absent key check
│   ├── Put overwrite returns old value and maintains size
│   ├── Remove existing returns old value and decrements size
│   ├── Remove absent returns null and preserves size
│   └── Clear resets size to 0 and re-allows insertion
├── 4. SecondChanceAndClockEvictionTests (from demo9, demo6 & demo2)
│   ├── Fresh entry initializes with secondChance == true
│   ├── get() refreshes secondChance to true
│   ├── expensiveGet() (slow probe path) refreshes secondChance to true
│   ├── getRaw() reads value WITHOUT setting secondChance
│   ├── Overwrite resets secondChance to true
│   ├── Update at full capacity does NOT trigger eviction
│   ├── Eviction keeps size bounded at maxSize
│   ├── Untouched entries are evicted while touched entries survive
│   ├── Clock hand sweeps continuously without resetting to 0
│   └── Degradation documented for small N (< 24)
├── 5. CollisionAndProbingTests (from demo9, demo3 & demo2)
│   ├── Multiple colliding keys stored and retrieved
│   ├── Overwrite key along probe chain
│   ├── Remove cluster head and realign subsequent entries
│   ├── Remove cluster middle and realign subsequent entries
│   ├── Remove cluster tail leaves preceding entries intact
│   ├── Realignment preserves secondChance bit of shifted entries
│   └── Circular array boundary wrap-around probing and deletion
├── 6. DiagnosticViewsTests (from demo9, demo6 & demo2)
│   ├── occupiedSpace() matches backing array length
│   ├── trueSize() matches size() at all times
│   ├── keys() returns independent defensive snapshot
│   ├── getDebugData() matches internal slot layout and home positions
│   └── toString() outputs valid diagnostic state with 1024-slot truncation
└── 7. StressAndFuzzingTests (from demo6 & demo9)
    ├── Continuous heavy eviction stress (10,000+ insertions)
    ├── High-collision continuous churn
    └── Boundary wrap-around continuous churn
```
