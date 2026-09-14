# Proposal: Safer XLT Data Aggregation

> **Status: design proposal only. None of the implementation described here
> has been applied to the production classes.**

## 1. Objective

Replace the current aggregation internals with a design that keeps their main
advantages:

- bounded temporal storage;
- cheap integer measurement ingestion;
- mergeable time slots;
- percentile and histogram support;
- approximate per-slot value distribution.

The replacement must make precision, ownership, exceptional behavior, and
numeric limits explicit. It must not silently lose aggregates, hang on large
timestamps, expose mutable internal state, or let the three representations of
a sample disagree.

The current implementation and its constraints are documented in
`XLT-DATA-ARCHITECTURE.md`.

## 2. Goals

| Goal | Required outcome |
|---|---|
| Correctness | Every accepted sample updates slot, summary, and distribution state consistently |
| Bounded time memory | Temporal storage remains O(configured slot count) |
| Wide time range | Use `long` seconds and overflow-safe arithmetic |
| Explicit approximation | Time, percentile, concurrency, and distinct-value error are documented |
| Stable statistics | Population variance remains numerically stable for long runs |
| Robust out-of-order input | Earlier and later samples use the same epoch-aligned bucket model |
| Encapsulation | Public reads return immutable snapshots |
| Testability | Algorithms have simple independent reference models |
| Performance | Common instantaneous ingestion remains allocation-free after construction |

## 3. Non-goals

- Exact retention of every raw measurement is not proposed.
- Exact distinct-value recovery after compression is not proposed.
- Millisecond-accurate concurrency is not proposed unless it becomes a
  separately funded requirement.
- Thread-safe concurrent writers are not proposed. Parallel producers should
  aggregate locally and merge results.
- Compatibility adapters should not be added until real external call sites
  and serialized forms have been inventoried.

## 4. Proposed contract

### 4.1 Construction

```text
slotCount must be at least 2
slotCount is rounded up to a representable power of two
rounding overflow is rejected
allocation failure is allowed to propagate
```

The implementation should store the rounded capacity and expose it as
`capacity()`. A raw scale level should not be public. Expose the meaningful
`slotWidthSeconds()` instead.

### 4.2 Measurement input

The proposed input model is equivalent to:

```java
record Measurement(long startTimeMillis,
                   long endTimeMillis,
                   int value,
                   boolean failed) {}
```

Validation rules:

| Input | Rule |
|---|---|
| Start/end | Any `long` millisecond timestamp is representable after `floorDiv` |
| Interval | `endTimeMillis >= startTimeMillis` |
| Value | Nonnegative `int` |
| Failure | Independent of value |

Negative values should be rejected rather than clamped. The current domain is
runtime-like data, and clamping in only part of the pipeline creates
irreconcilable statistics. If signed measurements are later required, they
must be supported consistently by every representation instead.

Convert timestamps using:

```java
long startSecond = Math.floorDiv(startTimeMillis, 1_000L);
long endSecond = Math.floorDiv(endTimeMillis, 1_000L);
```

This has defined behavior before the epoch and does not use floating-point
conversion or the 2038-limited `int` domain.

### 4.3 Time semantics

Slots are aligned to Unix epoch boundaries. For a power-of-two width `w`, the
bucket number and start are:

```text
bucketNumber = floorDiv(second, w)
bucketStart = bucketNumber * w
```

Multiplication must use checked arithmetic or a representation that stores the
bucket number directly. Alignment must never change when an earlier sample is
added.

The public time range should distinguish:

- first measurement second;
- last measurement second;
- first represented bucket start;
- last represented bucket end.

Returning only a value named `lastSecond` is ambiguous after condensation.

### 4.4 Statistical semantics

Accepted values contribute exactly once to:

- `long count`;
- checked `long sum`;
- `long errorCount`;
- exact `int minimum` and `maximum`;
- mergeable floating-point mean and population variance state;
- the configured distribution sketch.

Percentiles retain the current empirical Type-2 rank convention to minimize a
behavioral change. Inputs must be finite and in `[0, 100]`; quantiles must be
finite and in `[0, 1]`. Empty distribution queries should return an explicit
empty result if API evolution permits it. If compatibility requires numeric
zero, that convention must be documented.

### 4.5 Concurrency semantics

Retain the current affordable approximation, but name it accurately:

```text
bucket occupancy = number of accepted measurements considered to touch a
                   bucket at the resolution active when they were added

merged occupancy = maximum child occupancy
```

When ingesting at a coarse width, increment each touched coarse bucket exactly
once. Iterate bucket numbers, not raw seconds and not a scale level.

This value approximates peak occupancy. It is not exact millisecond
concurrency and may overestimate after coarse ingestion. The public API should
use a name such as `peakOccupancyEstimate` rather than `concurrentCount`.

### 4.6 Ownership

No public method should return a mutable accumulator, backing array, or lookup
table. Public output types should be immutable records and collections created
from snapshots.

## 5. Proposed component model

```text
IntTimeSeries facade
|-- CircularTimeBuckets
|   `-- SlotAccumulator[]
|       `-- AdaptiveValueBitmap
|-- SummaryAccumulator
|-- DistributionSketch
|   `-- SparseFixedWidthHistogram
`-- immutable Snapshot records
```

| Component | Responsibility |
|---|---|
| `IntTimeSeries` | Validate and coordinate one logical ingestion transaction |
| `CircularTimeBuckets` | Map absolute bucket numbers to fixed storage and rebuild at wider resolution |
| `SlotAccumulator` | Count, sum, errors, extrema, occupancy estimate, distinct approximation |
| `SummaryAccumulator` | Global count/sum/errors/extrema and mergeable variance |
| `DistributionSketch` | Percentile and exported histogram source |
| `AdaptiveValueBitmap` | Encapsulated 128-bit approximate value set |
| `TimeSeriesSnapshot` | Immutable public view |

Names are illustrative. Responsibilities and boundaries are the important
part of the proposal.

## 6. Temporal storage design

### 6.1 State

Use a circular array with explicit absolute bucket identity:

```text
SlotAccumulator[] slots
int head
int usedSlots
long firstBucketNumber
long slotWidthSeconds
boolean empty
```

The logical slot at offset `i` resides at:

```text
physicalIndex = (head + i) & (capacity - 1)
absoluteBucket = firstBucketNumber + i
```

This removes O(capacity) copying for ordinary left or right extension within
the current representable span.

### 6.2 Selecting a width

Before mutation, calculate the union of:

- the existing represented interval, if any;
- the new sample's start and end seconds.

Choose the smallest power-of-two width for which the aligned first and last
bucket numbers differ by less than capacity. Use division and checked
subtraction rather than `first + capacity * width` comparisons that can
overflow.

If no supported `long` width can represent the union, reject the sample before
changing state. The API must never spin indefinitely.

### 6.3 Rebuilding at a wider width

If the required width exceeds the current width:

1. Allocate or clear a scratch slot array.
2. Compute the new epoch-aligned bucket number for every occupied old slot.
3. Merge each old accumulator into the matching scratch slot.
4. Verify that all new indices are within capacity.
5. Swap the arrays and publish the new width/origin together.

This is a single O(capacity) rebuild to the final width. It replaces repeated
in-place pair merging, cannot discard an unpaired last slot, and provides
strong exception safety.

Because widths are powers of two and both old and new boundaries are epoch
aligned, an old bucket belongs to exactly one wider bucket. Equal timestamps
cannot move into inconsistent slots after an out-of-order insertion.

### 6.4 Adding a sample

After selecting/rebuilding the time range:

1. Update count/value/error/distinct data only in the sample's start bucket.
2. Determine start and end bucket numbers at the active width.
3. Increment occupancy once for each bucket in that inclusive range.
4. Update first/last actual measurement seconds.

The loop visits at most `capacity` buckets because width selection happens
first. A multi-year sample is therefore bounded by configured temporal
capacity rather than duration in raw seconds.

## 7. Exact summary design

### 7.1 Mergeable moments

Replace `sumOfSquares / n - mean^2` with Welford's online state:

```text
count
mean
M2
```

For each value `x`:

```text
count += 1
delta = x - mean
mean += delta / count
delta2 = x - mean
M2 += delta * delta2
populationVariance = M2 / count
```

Welford state avoids catastrophic cancellation for large values with small
variance. Use the standard parallel merge formula so independently produced
summaries can be combined.

Keep a checked `long sum` separately if the public API requires an exact total.
Use `Math.addExact`; silent wrap is not acceptable. Perform overflow-prone
calculations before mutating other state.

### 7.2 Counter widths

Use `long` for sample count, error count, per-slot count, occupancy, and
distribution counts. Long-running load tests can exceed signed `int` counters.
Checked increments should fail explicitly at `Long.MAX_VALUE`.

### 7.3 One normalized value

Validation yields one accepted nonnegative `int value`. That exact value is
passed to slots, moments, totals, and the distribution. No downstream
component may independently clamp or reinterpret it.

## 8. Distribution design

### 8.1 Recommended representation

Use a `SparseFixedWidthHistogram` with the current default width of eight.
Retaining fixed-width lower-bound representatives preserves the existing
additive precision model while removing allocation proportional to the full
minimum/maximum span.

Store counters in fixed-size pages keyed by page number:

```text
bucket = floorDiv(value, width)
page = floorDiv(bucket, bucketsPerPage)
offset = floorMod(bucket, bucketsPerPage)
```

A `NavigableMap<Long, long[]>` is a simple initial implementation. Each page
contains primitive `long` counters. A later primitive page index can be
introduced only if profiling justifies it.

Properties:

| Property | Result |
|---|---|
| Add | O(log number of pages), no gap allocation |
| Memory | O(allocated pages), not O(value span) |
| Percentile scan | Ordered by page and bucket |
| Merge | Counter-wise page addition |
| Precision | Lower boundary with additive error below width |

If a hard bound on distribution memory is mandatory, substitute a documented
relative-error sketch such as DDSketch behind the same interface. That is a
contract change and should not happen implicitly.

### 8.2 Percentile calculation

Use `long` rank arithmetic wherever the requested percentile can be expressed
exactly and widen representatives before midpoint addition:

```text
midpoint = ((long) lowerRepresentative + upperRepresentative) / 2.0
```

Reject `NaN` and infinities explicitly with `Double.isFinite`.

### 8.3 Range semantics

Choose and document one of these APIs rather than conflating them:

| API | Meaning |
|---|---|
| `countRepresentedInRange` | Count representatives whose lower boundary lies in the range |
| `countPossiblyInRange` | Count every internal bucket intersecting the range |

The current method implements the second behavior. The first behavior is more
useful for building non-overlapping exported charts whose counts must sum to
the total.

### 8.4 Exported histogram

Require `bucketCount > 0`. For nonnegative values, partition integer
representatives over `[0, maximum]` with `long` integer arithmetic. If the
requested count exceeds the number of representable integer positions, return
fewer nonempty buckets rather than reversed ranges.

Assign each internal representative to exactly one exported bucket in one
ordered pass. Required invariants:

```text
every bucket has start <= end
buckets are ordered, gap-free, and non-overlapping
sum(exported counts) == total count
first start == 0
last end >= exact maximum
```

Equal-valued and narrow populations must be valid inputs.

## 9. Distinct-value approximation

Retain the compact 128-bit adaptive bitmap if the API needs approximate value
locations rather than only distinct cardinality. Encapsulate both words and
the scale in `AdaptiveValueBitmap`.

The only scale-up operation should fold the complete logical 128-bit value:

```text
compressedLow = pairFold(low)
compressedHigh = pairFold(high)
low = compressedLow | (compressedHigh << 32)
high = 0
scale += 1
```

Merge must not mutate its argument:

1. Determine the larger scale.
2. Fold local copies of each operand to that scale.
3. OR corresponding words.
4. Store the result only in the receiver or a new value.

Document that returned values are sorted lower boundaries, not raw values and
not frequencies. Return an immutable primitive snapshot or an immutable list.

If only cardinality is required by consumers, replace this structure with a
small cardinality sketch instead; do not retain value reconstruction merely
for historical reasons.

## 10. Bit operations

Prefer JDK intrinsics:

| Current helper | Proposed default |
|---|---|
| `BitUtil.pop` | `Long.bitCount` |
| `BitUtil.ntz` variants | `Long.numberOfTrailingZeros` / `Integer.numberOfTrailingZeros` |
| `BitUtil.nlz` | `Long.numberOfLeadingZeros` |
| Power-of-two test | A validated positive numeric helper |
| Next power | `highestOneBit` plus checked shift |

Modern JVMs intrinsify these operations and provide defined zero semantics.
Remove public mutable lookup tables.

Keep optimized array carry-save cardinality only if a representative JMH
benchmark demonstrates a meaningful advantage over a straightforward loop on
supported JVMs. If retained, validate slices with overflow-safe conditions:

```text
array != null
offset >= 0
length >= 0
offset <= array.length
length <= array.length - offset
```

Binary methods validate the slice against both arrays.

`BitCompression` can remain a package-private implementation detail of
`AdaptiveValueBitmap`, with its composition tested exhaustively for all bit
lanes.

## 11. Public read model

One snapshot operation should calculate internally consistent output once:

```java
record Statistics(long count,
                  long errorCount,
                  long sum,
                  int minimum,
                  int maximum,
                  double mean,
                  double populationStandardDeviation) {}

record TimeSlot(long startSecond,
                long endSecond,
                long count,
                long errorCount,
                long sum,
                int minimum,
                int maximum,
                long peakOccupancyEstimate,
                List<Integer> approximateDistinctValues) {}

record TimeSeriesSnapshot(Statistics statistics,
                          long firstMeasurementSecond,
                          long lastMeasurementSecond,
                          long slotWidthSeconds,
                          List<TimeSlot> slots) {}
```

Empty extrema should use `OptionalInt`, an explicit `empty` flag, or an empty
statistics variant. Public consumers should never receive internal sentinel
extrema.

Lists must be immutable and detached from accumulators. Snapshot creation is
O(capacity); individual simple global getters can remain O(1) by reading the
global summary.

## 12. Exception safety

An invalid or unrepresentable sample must leave state unchanged. The ingestion
sequence should be:

1. Validate all arguments.
2. Convert timestamps and determine target width with overflow-safe math.
3. Precompute checked global counter and sum updates.
4. Build any required replacement time array in scratch storage.
5. Apply slot, summary, and distribution updates.
6. Publish replacement temporal state only after successful construction.

Distribution allocation can still fail with `OutOfMemoryError`; full recovery
from VM resource exhaustion is not a design goal. Ordinary argument,
arithmetic, and range failures must be detected before mutation.

## 13. Parallel aggregation

Keep each accumulator single-writer and provide an explicit merge operation.
This is cheaper and easier to reason about than locks on every sample.

Merge rules:

| State | Merge |
|---|---|
| Summary | Parallel Welford merge plus checked totals |
| Distribution | Counter-wise page merge |
| Time slots | Select common width/origin, rebuild both, merge matching slots |
| Distinct bitmap | Align copied scales and OR |
| Occupancy estimate | Maximum for merged intervals with the same time range |

Document whether merging represents concatenated data sets or overlapping
producer streams. Exact cross-producer concurrency cannot be reconstructed
from maxima alone.

## 14. Defect-to-design mapping

| Current problem | Proposed prevention |
|---|---|
| First long sample overruns array | Fit complete start/end range before mutation |
| Wrong concurrency step after scale 2 | Iterate absolute active bucket numbers |
| Far-left shift fails or loses data | Circular indexing plus one aligned rebuild |
| Equal timestamps split after re-anchoring | Epoch-aligned bucket boundaries |
| Condensation discards final entry | Scratch rebuild maps every occupied old slot |
| Large spans overflow or loop | `long` domain, checked arithmetic, finite width selection |
| Sentinel timestamp collision | Explicit `empty` state |
| Negative values disagree across state | Reject once at facade boundary |
| Variance cancellation | Welford/parallel moments |
| Unequal distinct scales corrupt upper half | One encapsulated full-128-bit fold |
| Histogram allocates the entire numeric gap | Sparse fixed-size pages |
| Exported histogram overlaps/reverses | Integer partition plus single representative assignment |
| Mutable state escapes | Immutable snapshots |
| Public lookup tables can be changed | JDK intrinsics or private constants |
| `ntz3(0)` inconsistency | JDK-defined zero behavior |
| Silent counter overflow | `long` counters and checked arithmetic |

## 15. Verification strategy

### 15.1 Unit tests

Retain the newly added current-behavior tests as a baseline. Add focused tests
for the new contract:

- constructor boundaries and next-power overflow;
- negative values rejected without mutation;
- reversed intervals rejected without mutation;
- timestamps before 1970 and beyond 2038;
- a first sample spanning more than capacity seconds;
- far-left and far-right out-of-order samples;
- multiple condensation levels, including more levels than `log2(capacity)`;
- equal timestamps before and after re-anchoring attempts;
- occupancy at width 4 and above;
- unequal distinct scales with values in both 64-bit halves;
- count/sum/error/extrema invariants after every rebuild;
- stable variance for large, tightly clustered values;
- equal-valued histogram input and bucket counts greater than value range;
- distant distribution outliers without gap-sized allocation;
- percentile `NaN`, infinity, zero, and endpoint behavior;
- immutable snapshot and collection behavior;
- checked arithmetic failures leave snapshots unchanged.

### 15.2 Reference and property tests

Use a simple test-only model that stores raw measurements. For deterministic
random streams, compare:

- exact global totals and extrema;
- Type-2 percentile results after applying configured quantization;
- slot assignment at the current width;
- snapshot ordering and range boundaries;
- exported histogram count conservation.

Generate permutations of the same measurements. Exact global results and
epoch-aligned slot aggregates at a fixed final width should be independent of
input order.

### 15.3 Mutation tests

Prioritize mutation coverage for:

- inclusive end boundaries;
- width selection comparisons;
- floor division for negative times;
- pair-fold cross-word movement;
- max versus sum occupancy merge;
- Type-2 integral-rank averaging;
- checked preconditions before mutation.

### 15.4 Performance tests

Benchmark independently:

- instantaneous add without rebuild;
- full-capacity interval add;
- one temporal rebuild;
- local and distant histogram values;
- percentile query by populated page count;
- snapshot creation;
- accumulator merge.

Compare JDK bit intrinsics with retained custom routines on every supported JDK
before carrying custom bit code forward.

## 16. Implementation sequence

This is a future implementation sequence, not work performed by this change.

1. Freeze required externally observable behavior and identify actual callers.
2. Introduce immutable snapshot records and independent summary tests.
3. Implement `SummaryAccumulator` with checked totals and Welford moments.
4. Implement and property-test `AdaptiveValueBitmap`.
5. Implement `SparseFixedWidthHistogram` and Type-2 percentile queries.
6. Implement epoch-aligned circular temporal storage and scratch rebuilds.
7. Integrate validated, exception-safe ingestion.
8. Add differential, permutation, overflow, and mutation tests.
9. Benchmark against the current implementation on representative XLT data.
10. Add a compatibility adapter only for confirmed external consumers.
11. Switch consumers, compare generated reports, then remove obsolete code.

## 17. Acceptance criteria

An implementation of this proposal is acceptable only when:

- all input domains and approximation semantics are documented and tested;
- no accepted timestamp is narrowed to `int` or converted through `double`;
- no timestamp span can cause an infinite loop;
- adding earlier data cannot change absolute bucket identity;
- all accepted samples preserve exact global count, error count, sum, minimum,
  and maximum through any number of temporal rebuilds;
- slot, summary, and distribution state use the same validated value;
- occupancy visits each active bucket at most once per sample;
- unequal distinct-value scales merge without mutating the source;
- distribution memory depends on populated pages rather than numeric span;
- exported histogram ranges never overlap or reverse, and counts sum to the
  total;
- percentile midpoint arithmetic cannot overflow;
- public APIs expose no mutable internal arrays or accumulators;
- invalid and arithmetic-failing operations leave state unchanged;
- performance benchmarks demonstrate acceptable ingestion and report costs.

## 18. Decision summary

| Decision | Recommendation |
|---|---|
| Time type | `long` epoch seconds derived with `Math.floorDiv` |
| Temporal alignment | Epoch-aligned power-of-two widths |
| Temporal storage | Circular fixed array with scratch rebuild on widening |
| Value domain | Reject negative values |
| Counts | Checked `long` |
| Variance | Mergeable Welford state |
| Percentile convention | Preserve Type-2 |
| Distribution | Sparse paged fixed-width histogram, default width 8 |
| Distinct approximation | Encapsulated 128-bit adaptive bitmap |
| Concurrency | Explicitly named bucket occupancy/peak estimate |
| Public output | Immutable snapshots |
| Bit helpers | Prefer JDK intrinsics; retain custom loops only with evidence |
| Threading | Single-writer accumulators with explicit merge |

This proposal deliberately favors explicit contracts and simple reference
models over incidental micro-optimizations. Optimization should follow
correctness tests and representative benchmarks, not precede them.
