# XLT Data Aggregation: Current Architecture

## 1. Scope and status

This document describes the code currently present under
`src/main/java/com/xceptance`. It is a description of the implementation, not
an endorsement of every current behavior. Known limitations and defects are
listed explicitly in section 13.

The implementation is an in-memory aggregation pipeline for integer-valued
measurements. It stores exact summary data where practical and deliberately
loses precision in two dimensions:

- time resolution is reduced when the represented time span grows;
- value resolution is reduced for percentiles and per-slot distinct values.

The implementation is mutable, unsynchronized, and not safe for concurrent
access without external synchronization.

## 2. Source map

| Class | Responsibility |
|---|---|
| `com.xceptance.xlt.report.util.rework.IntTimeSeries` | Public aggregation facade, fixed-size time slots, global statistics, histogram export |
| `com.xceptance.xlt.report.util.rework.IntTimeSeriesEntry` | Aggregate for one time slot and adaptive approximation of distinct values |
| `com.xceptance.xlt.report.util.RuntimeHistogram` | Value-frequency histogram and percentile/range calculations |
| `com.xceptance.xlt.report.util.misc.BitCompression` | Pairwise folding of a 64-bit bitmap |
| `com.xceptance.xlt.report.util.lucene.BitUtil` | Population counts, zero counts, and power-of-two helpers |

The dependency direction is:

```text
IntTimeSeries
|-- IntTimeSeriesEntry[]
|   `-- BitCompression
|-- RuntimeHistogram
|   `-- BitUtil
`-- BitUtil
```

`BitUtil` is retained code originating from Apache Lucene/Solr. The other
classes carry Xceptance copyright headers.

## 3. Conceptual model

Each input measurement has four properties:

```text
start time in milliseconds
end time in milliseconds
integer value
failure flag
```

One measurement contributes to three representations:

| Representation | Data retained |
|---|---|
| Start-time slot | count, sum, error count, minimum, maximum, approximate distinct values |
| All touched time slots | concurrency count |
| Global state | sum of squares and an eight-unit value histogram |

The slot array answers time-oriented questions. The global histogram answers
distribution questions independently of temporal condensation. There is no
operation for removing a measurement.

## 4. Input path

`IntTimeSeries.addValue(startTime, endTime, value, failed)` performs these
steps:

1. Convert both millisecond timestamps to `int` seconds using
   `(int) (timestamp * 0.001)`.
2. If the start precedes `firstSecond`, shift existing slots to the right and
   make the new start the origin.
3. Otherwise, if the end is outside the represented range, repeatedly
   condense adjacent slots until the end appears to fit.
4. Map the start second to an array position and update that entry with the
   value and failure flag.
5. Walk later seconds through the end second, inclusive, and increment their
   concurrency counters.
6. Update the last used position.
7. Add `value^2` to the global sum of squares.
8. Add the raw value to the global `RuntimeHistogram`.

The three-argument overload treats a measurement as instantaneous by passing
the start timestamp as its end timestamp.

The operation is not transactional. An exception after an entry has been
updated can leave the slot array changed without updating all global state.

## 5. Temporal representation

### 5.1 Capacity

`IntTimeSeries.DEFAULT_SIZE` is 3,600. The constructor rounds every requested
size up with `BitUtil.nextHighestPowerOfTwo`, so the default physical array has
4,096 entries.

The power-of-two size supports repeated pairwise condensation. It does not
make the storage a ring buffer; shifts physically copy array references.

No constructor validation exists. Zero, negative, overflowing, or impractical
sizes therefore have accidental rather than defined behavior.

### 5.2 Empty state and origin

The array is eagerly filled with empty `IntTimeSeriesEntry` instances. Empty
state is represented by both:

```text
firstSecond = 2_147_385_000
lastPosUsed = -1
```

The `firstSecond` value is also a valid timestamp. `getFirstSecond()` and
`getLastSecond()` test the sentinel value rather than `lastPosUsed`, which
creates a collision described in section 13.

### 5.3 Time scale

The field named `scale` is a level, not a width. It starts at 1. The actual
number of seconds represented by one slot is:

```text
slotWidth = 2^(scale - 1)
```

| `getScale()` | `getSlotWidth()` | Nominal seconds per slot |
|---:|---:|---:|
| 1 | 1 | 1 |
| 2 | 2 | 2 |
| 3 | 4 | 4 |
| 4 | 8 | 8 |

For a consistent state, slot `i` nominally covers:

```text
[firstSecond + i * slotWidth,
 firstSecond + (i + 1) * slotWidth - 1]
```

`getLastSecond()` returns the start of the last used slot, not the inclusive
end of that slot.

### 5.4 Condensation

When a right-side timestamp does not fit, `condense` destructively joins
adjacent entries:

```text
old slots: [0] [1] [2] [3] [4] [5] ...
new slots: [0+1]   [2+3]   [4+5]   ...
```

It then fills the newly unused positions with empty entries and increments the
scale level. A merge applies these rules:

| Statistic | Merge operation |
|---|---|
| Count | Sum |
| Total value | Sum |
| Error count | Sum |
| Minimum | Minimum |
| Maximum | Maximum |
| Concurrency | Maximum |
| Distinct approximation | Scale alignment, then bitmap union |

Taking maximum concurrency preserves an approximation of the peak occupancy
of either child slot. It does not produce the number of measurements touching
the combined interval.

### 5.5 Earlier timestamps

An earlier timestamp invokes `shiftRight`. If the calculated offset appears
too large, the method first attempts condensation. It then uses
`System.arraycopy` to move entry references and creates new empty entries in
the freed prefix.

This operation changes `firstSecond`, so slot boundaries are anchored to the
earliest accepted timestamp rather than to epoch-aligned boundaries. The
current offset formulas use inconsistent alignment after condensation; see
section 13.

### 5.6 Concurrency meaning

`concurrentCount` starts with one increment in the measurement's start slot.
Every later second through the end second increments another slot. At one
second resolution this means "number of measurements touching this second",
not exact millisecond-level simultaneous execution.

After condensation it becomes a maximum of child values and is best
interpreted as an approximate peak within the wider interval.

## 6. Per-slot aggregation

`IntTimeSeriesEntry` holds:

| Field | Type | Empty sentinel or initial value |
|---|---|---|
| Total value | `long` | 0 |
| Count | `int` | 0 |
| Concurrency | `int` | 0 |
| Error count | `int` | 0 |
| Maximum | `int` | `Integer.MIN_VALUE` |
| Minimum | `int` | `Integer.MAX_VALUE` |
| Distinct lower bitmap | `long` | 0 |
| Distinct upper bitmap | `long` | 0 |
| Distinct-value scale level | `int` | 0 |

### 6.1 Value normalization

`IntTimeSeriesEntry.updateValue` clamps negative values to zero for its sum,
minimum, maximum, average, and distinct approximation. Count, error count, and
concurrency are incremented once.

The average uses integer division:

```text
average = totalValue / count
```

Empty minimum, maximum, and average accessors return zero rather than exposing
their internal sentinels.

### 6.2 Approximate distinct values

Two `long` fields form a logical 128-bit bitmap. At scale level 0, bit `i`
means that value `i` occurred. If a value is at least 128 after scaling, each
pair of adjacent bits is ORed and packed into one bit, and the scale level is
incremented. Folding repeats until the new value fits.

At distinct scale `s`, bit `i` represents lower boundary:

```text
i * 2^s
```

Examples after folding to width 2:

| Raw value | Approximate value returned by `getValues()` |
|---:|---:|
| 0 or 1 | 0 |
| 62 or 63 | 62 |
| 126 or 127 | 126 |
| 128 or 129 | 128 |

`getValues()` returns sorted represented lower boundaries as `double[]`. It
does not return frequencies and is not an exact set once scaling has occurred.

### 6.3 Bitmap primitives

The fold operation is composed from `BitCompression`:

1. `combineAdjacentBits(x)` computes `x | (x << 1)`. Only each odd output bit
   is meaningful as the OR of an input pair.
2. `compressAndShiftOddBits(x)` extracts bits 1, 3, ..., 63 and packs them into
   bits 0, 1, ..., 31 of a zero-extended `long`.

For a complete 128-bit fold, compressed low and high halves must be joined as:

```text
newLow = compress(combine(low)) | (compress(combine(high)) << 32)
newHigh = 0
```

The standalone scaling path does this. The scale-alignment path in `merge`
does not; this is a known defect.

### 6.4 Merge mutability

`merge(item)` mutates the receiver and can also mutate `item` while aligning
distinct-value scales. It returns the receiver. Callers must not assume either
object remains an independent value after merging.

The class overrides `equals` for all mutable fields but does not override
`hashCode`. It must not be used as a key in hash-based collections under the
normal Java equality contract.

## 7. Global statistics

`IntTimeSeries` derives count, sum, error count, minimum, and maximum by
scanning the entire entry array for every overview request. Empty entries are
ignored.

`Statistics` is a mutable public-field carrier:

```text
count, errorCount, sum, maxValue, minValue
```

For an empty series, count/error/sum are zero while minimum and maximum remain
`Integer.MAX_VALUE` and `Integer.MIN_VALUE`. The convenience methods return
zero for empty mean and standard deviation.

The standard deviation is the population standard deviation:

```text
mean = sum / count
standardDeviation = sqrt(sumOfSquares / count - mean^2)
```

`sumOfSquares` is a `double` updated once per input measurement with
`Math.pow(value, 2)`. It is independent of temporal condensation.

## 8. Runtime histogram

### 8.1 Storage model

`RuntimeHistogram` stores an `int` count for every represented value bucket.
It allocates one contiguous array spanning the smallest through largest bucket
index seen so far. Empty bucket positions inside that span still consume
memory.

The requested precision is rounded up to a power of two. Internally, the class
stores the number of right-shift bits. For width `w`, a raw value `v` maps to:

```text
bucketIndex = floor(v / w)
representative = bucketIndex * w
```

Because Java arithmetic right shift is used, negative values round toward
negative infinity. For width 8, `-1` is represented as `-8`.

The time series always creates its global histogram with requested precision
8. Percentiles therefore have additive quantization of up to 7 for
nonnegative integer values.

### 8.2 Growth

The first value allocates one counter. A lower value allocates a larger array
and shifts existing counts right. A higher value extends the array. Adding a
value inside the current range only increments its counter.

Memory complexity depends on numeric span, not sample count:

```text
O((maximum representative - minimum representative) / precision)
```

A single distant outlier can therefore force a very large allocation.

### 8.3 Percentiles

Percentile input is nominally in `[0, 100]`. Quantile input is multiplied by
100 and delegated to the same method.

The calculation uses the empirical Type-2 convention over bucket
representatives. For `n` observations and percentile `p`, let:

```text
r = n * p / 100
```

The result is:

| Condition | Result |
|---|---|
| Empty histogram | 0.0 |
| `p == 0` | Minimum representative |
| `p == 100` | Maximum representative |
| Integral `r` | Mean of observations at one-based ranks `r` and `r + 1` |
| Non-integral `r` | Observation at one-based rank `ceil(r)` |

This differs from both nearest-rank and linearly interpolated percentiles.
Integral rank, not an even sample count, triggers averaging.

### 8.4 Range counts

`getCountForValue(start, end)` treats both endpoints as inclusive. With unit
precision, this is an exact integer-value count. With coarser precision, it
counts every observation in any internal bucket touched by the requested
range. A query for value 1 at precision 8 therefore also counts observations
represented by bucket 0, including raw values 0 through 7.

### 8.5 Exported histogram

`IntTimeSeries.toHistogram(bucketCount)` builds a second histogram view over
the internal histogram:

```text
width = (exact maximum - exact minimum) / bucketCount
```

The first exported bucket starts at zero. Later buckets start at
`minimum + i * width`. Every non-final end has one subtracted before conversion
to `int`; the final end is the exact maximum. Counts come from
`RuntimeHistogram.getCountForValue`, so they retain its eight-unit internal
quantization.

The exported `HistogramBucket` record contains inclusive integer start/end
values and an `int` count.

## 9. Bit utilities

### 9.1 Population counts

`BitUtil.pop` implements a 64-bit population count using the Hacker's Delight
parallel-bit algorithm.

The array operations use a carry-save-adder strategy in blocks of eight words:

| Method | Per-word set operation |
|---|---|
| `pop_array` | `A` |
| `pop_intersect` | `A & B` |
| `pop_union` | `A | B` |
| `pop_andnot` | `A & ~B` |
| `pop_xor` | `A ^ B` |

Optional four-word, two-word, and one-word tails handle every length modulo
eight. Inputs are not modified. Slice arguments and nulls are not validated.

### 9.2 Zero-count operations

`ntz`, `ntz2`, and `ntz3` implement trailing-zero/first-set-bit searches.
`nlz` implements leading-zero count. Public 256-entry byte lookup tables
support these routines.

The arrays are `public static final`, which makes their references final but
their elements globally mutable. Changing a table entry corrupts future
results.

### 9.3 Power-of-two operations

`isPowerOfTwo` implements a bit-pattern definition: zero or any value with one
set bit is true. This includes `Integer.MIN_VALUE` and `Long.MIN_VALUE`.

`nextHighestPowerOfTwo` spreads the highest set bit and increments. It is used
for both time-series capacity and histogram precision. Positive values whose
next power remains representable behave conventionally; negative and
overflowing values have undocumented two's-complement results.

## 10. Exact and approximate guarantees

Under the supported practical domain of nonnegative values, positive sizes,
ordered start/end timestamps, representable time spans, and no external
mutation:

| Output | Nature |
|---|---|
| Count | Exact until `int` entry counters or histogram count overflow |
| Error count | Exact until `int` entry counters overflow |
| Total value | Exact until `long` overflow |
| Minimum/maximum | Exact |
| Mean | Derived from exact count and sum |
| Standard deviation | Population estimate using `double` sum of squares |
| Time-slot statistics | Exact aggregates at current lossy time resolution |
| Time-slot concurrency | Approximate after temporal condensation |
| Percentiles | Approximate to histogram bucket lower boundaries |
| Exported histogram | Approximate and may recount the same internal bucket |
| Per-slot distinct values | Approximate power-of-two lower boundaries |

Temporal condensation should preserve global count, total, errors, minimum,
and maximum because entry merges use associative aggregate operations. Current
edge defects can violate that intended invariant.

## 11. Complexity and memory

Let `S` be the configured slot count, `D` the measurement duration in seconds,
`C` the number of condensation levels, `B` the allocated histogram bucket
span, and `R` the number of exported buckets.

| Operation | Current cost |
|---|---|
| Add instantaneous value without resize | O(1) |
| Add spanning value | O(D / loop step) |
| Shift earlier data | O(S) |
| One condensation level | O(S) initially, then progressively smaller active prefixes inside one call |
| Overview getter | O(S) |
| Percentile | O(B) worst case |
| Histogram range count | O(B) over the selected internal range |
| Export histogram | O(R + internal buckets visited) |
| Entry distinct-value export | O(128) |

Base time-series memory is O(S), including one eagerly allocated entry object
per slot. Global histogram memory is O(B) and is not bounded by `S`.

## 12. Ownership and API boundaries

The current API exposes mutable implementation state:

- `IntTimeSeries.getValues()` returns the actual backing array;
- every returned `IntTimeSeriesEntry` is mutable;
- `Statistics` exposes mutable public fields;
- `BitUtil.ntzTable` and `BitUtil.nlzTable` are mutable arrays;
- `IntTimeSeriesEntry.merge` can mutate its argument.

Callers can invalidate counts and ranges by replacing an entry in the backing
array or mutating an entry without updating the global histogram and sum of
squares. Returned state must therefore be treated as read-only even though the
types do not enforce this.

No class declares a thread-safety contract or uses synchronization, volatile
state, or atomic operations. External synchronization must encompass all
reads and writes, including traversal of returned internal data.

## 13. Known limitations and defects

These are current implementation observations. They are not encoded as
passing compatibility requirements in the unit tests.

### 13.1 Time-series correctness

| Issue | Consequence |
|---|---|
| First long measurement checks the start branch but skips end-range condensation | It can index past the array and leave partial state |
| Duration loop increments by `scale` instead of `getSlotWidth()` | Concurrency is wrong from scale level 3 onward |
| Duration loop starts at `startSecond + 1` after scaling | The start slot can be incremented more than once |
| Far-left shift condenses toward `newSecond + oldSpan` | It can condense too little, lose entries, or pass invalid lengths to `arraycopy` |
| Shift and insertion use different scale-adjustment formulas | Re-anchoring can split equal timestamps across different slots |
| Repeated condensation can reduce the active prefix to one and then discard it | Sufficiently distant timestamps can lose historical entry aggregates |
| `int` time and masked Java shifts overflow | Large spans can loop indefinitely or alias slot widths |
| Timestamp conversion uses floating multiplication and narrowing | Negative fractional seconds truncate toward zero; large timestamps saturate or lose precision |
| Empty sentinel is a valid second | Data at exactly `2_147_385_000` seconds is reported as having no range |
| No `endTime >= startTime` validation | Reversed intervals have accidental semantics |

### 13.2 Value consistency

| Issue | Consequence |
|---|---|
| Entries clamp negatives, while histogram and sum of squares use raw negatives | Sum, mean, deviation, percentile, and distinct values describe different populations |
| Distinct scale alignment folds the two 64-bit halves independently | Upper-half values are misplaced or collide during unequal-scale merges |
| `getValues()` exposes internal entries | External mutation breaks global versus per-slot consistency |
| Count/error fields are `int` | Long-running aggregations can silently wrap |
| Total and percentile midpoint use unchecked integer arithmetic | Totals can wrap; averaging two extreme representatives can overflow before conversion to `double` |

A minimal unequal-scale distinct-value reproduction is:

```java
var lowScale = new IntTimeSeriesEntry(64, false);
var highScale = new IntTimeSeriesEntry(128, false);
lowScale.merge(highScale);
```

The represented set should retain lower boundaries 64 and 128. The current
merge can collapse them to one represented value.

### 13.3 Histogram behavior

| Issue | Consequence |
|---|---|
| Precision accepts zero, negatives, and values that overflow the next power | Width becomes accidental, zero-equivalent, or negative |
| Percentile validation does not reject `NaN` | Rank traversal produces an unintended value |
| Contiguous span allocation | One outlier can cause excessive allocation or `OutOfMemoryError` |
| `valueCount` and bucket counters are `int` | Counts silently wrap |
| Midpoint adds two `int` representatives before widening | Extreme values can produce an incorrect median/percentile |
| Export bucket count is not validated | Zero and negative counts have inconsistent failure behavior |
| Export width can be zero or below one | Equal/narrow value ranges can create reversed buckets and exceptions |
| Export boundaries can touch the same internal eight-unit bucket | Counts across exported buckets can overlap and sum above total count |

### 13.4 Bit utilities

| Issue | Consequence |
|---|---|
| Array slices are not validated and `offset + length` can overflow | Invalid input can return zero or fail inconsistently |
| Lookup tables are publicly mutable | Any caller can corrupt global results |
| `ntz3(0)` returns 63 while the other variants return 64 | Zero semantics are inconsistent |
| Signed and overflowing power-of-two inputs are undefined | Capacity/precision callers can receive zero or negative results |

## 14. Executable specification

The tests under `src/test/java/com/xceptance` provide independent oracles and
characterize the supported core behavior:

| Test class | Main coverage |
|---|---|
| `BitUtilTest` | Every array tail path and offset, JDK population/zero-count oracles, lookup tables, power-of-two behavior |
| `BitCompressionTest` | Every input bit, odd-bit packing, pair occupancy, deterministic random reference comparison |
| `RuntimeHistogramTest` | Precision, negative buckets, left/right growth, duplicates, Type-2 ranks, ranges, validation |
| `IntTimeSeriesEntryTest` | Empty state, normalization, aggregation, adaptive distinct scaling, merges, equality |
| `IntTimeSeriesTest` | Size rounding, time slots, concurrency, shifts, condensation, moments, percentiles, histogram export |

The tests intentionally do not assert accidental behavior for invalid slices,
overflow, non-finite percentiles, unsupported far spans, or known corruption
paths. Those cases require the explicit contract and redesign proposed in
`XLT-DATA.md`.

## 15. Glossary

| Term | Meaning in this implementation |
|---|---|
| Sample or measurement | One `addValue` call |
| Slot | One `IntTimeSeriesEntry` in the fixed array |
| Time scale | Level whose width is `2^(level - 1)` seconds |
| Distinct scale | Level whose represented value width is `2^level` |
| Representative | Lower boundary standing in for all raw values in a bucket |
| Condensation | Destructive pairwise merge of adjacent time slots |
| Concurrency | Count of measurements touching a base slot; approximate peak after merging |
| Type-2 percentile | Empirical quantile convention averaging adjacent ranks at integral `n * p` |
