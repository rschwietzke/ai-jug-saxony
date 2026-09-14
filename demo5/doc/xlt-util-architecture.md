# The `com.xceptance.xlt.report.util` package — Architecture & Knowledge Base

This document explains everything one must know to understand, test, or change the
classes in `demo5/src/main/java/com/xceptance/xlt/report/util/`. They were taken from
the Xceptance XLT performance-test report generator (that is why the package is named
`com.xceptance.xlt.report`) and were placed here *unmodified and untested*.

> All behavioral statements below were verified by running the code
> (`mvn test` in `demo5`). Quantities marked "currently" describe the implementation
> as shipped, including its quirks.

---

## 1. Files and responsibilities

| File | Purpose |
|---|---|
| `util/rework/IntTimeSeries.java` | Fixed-size, self-condensing time series. For each second (or coarser slot) it keeps an `IntTimeSeriesEntry`. |
| `util/rework/IntTimeSeriesEntry.java` | Per-slot aggregator: exact `count / sum / min / max / errorCount / concurrentCount` plus a lossy approximation of the *distinct* values seen. |
| `util/RuntimeHistogram.java` | Memory-lean histogram of `int` values; computes quantiles/percentiles and range counts. One instance is embedded in `IntTimeSeries` and receives **every** value. |
| `util/lucene/BitUtil.java` | Apache Lucene/Solr port: popcount, `ntz`/`nlz`, power-of-two helpers. `IntTimeSeries` uses `nextHighestPowerOfTwo`, `RuntimeHistogram` uses it too. |
| `util/misc/BitCompression.java` | The two primitives `combineAdjacentBits` + `compressAndShiftOddBits` used by `IntTimeSeriesEntry` to halve the resolution of its distinct-value bitmap. |

Dependency graph:

```
IntTimeSeries ──> RuntimeHistogram ──> BitUtil.nextHighestPowerOfTwo
IntTimeSeries ──> IntTimeSeriesEntry[] ──> BitCompression (merge / scaling)
```

There is **no threading model**: all classes are single-threaded and unsafe for
concurrent use.

---

## 2. Data model and vocabulary

### 2.1 Time is measured in *milliseconds*, aggregated in *seconds*

* Callers pass epoch milliseconds (`startTime`, `endTime`).
* A second is computed as `(int) (time * 0.001)` — a *truncating* cast. For the
  positive timestamps XLT deals with this is a floor.
  **All unit tests use whole multiples of 1000 ms** so that `time / 1000` is exact.
* `IntTimeSeries` keeps min/max/sum/… per **second** (1 slot = 1 s) as long as the
  data range fits into its window. When it does not fit, slot width is doubled
  repeatedly (see §5).

### 2.2 Slot terminology (IntTimeSeries)

* `size` – the number of slots (always a power of two; the constructor rounds up).
* `values[]` – an array of `IntTimeSeriesEntry`, index 0 = oldest recorded second.
* `firstSecond` – second represented by `values[0]`.
* `scale` – starts at 1; slot width in seconds is `2^(scale-1)` (exposed as
  `getSlotWidth()`). So:
  * `pos(v) = v >> (scale - 1)` (converts an offset from `firstSecond` to an index),
  * `seconds(pos) = pos << (scale - 1)` (converts an index to seconds).
* `lastPosUsed` – the largest slot index that ever received a value; used to compute
  `getLastSecond() = firstSecond + seconds(lastPosUsed)`.
* Everything is computed in **32-bit `int`** arithmetic. This causes the
  limitations documented in §8.

### 2.3 The entry stores *two* different aggregations

For each second-slot, `IntTimeSeriesEntry` keeps:

1. **Exact** values – `count`, `totalValue` (sum), `errorCount`, `minimum`,
   `maximum`, `concurrentCount`.
   * Negative inputs are **clamped to 0** for the sum/min/max bookkeeping
     (`v = value < 0 ? 0 : value`), i.e. this code only supports non-negative
     measurements. There is a subtle inconsistency: `if (value < minimum)
     minimum = v;` uses the raw `value` in the comparison but assigns the *clamped*
     value, so a negative input resets `minimum` to 0, never below.
   * `averageValue = (int)(sum / count)` — integer division, truncates.
   * `concurrentCount` counts how often the entry was "touched" by a transaction,
     but **merging takes the maximum**, not the sum (§5.4).

2. **Approximate** distinct values – a 128-bit bitmap (two `long` words) where
   bit *i* means “a value quantized to bucket `i` was seen”. Buckets have a
   power-of-two width `2^distinctValuesScale`; `getValues()` reports the *lower
   edge* of every occupied bucket, i.e. `i * 2^distinctValuesScale`.
   This replaced the old `LowPrecisionIntValueSet` to save memory.

---

## 3. `RuntimeHistogram` — dense bucket histogram

### 3.1 Principle

Instead of storing every value, it counts *occurrences per bucket*:

* bucket index = `value >> precisionShift`, where `1 << precisionShift` is the
  requested bucket width (**precision**). The constructor accepts any positive
  precision and silently rounds it **up to the next power of two**
  (e.g. 3→4, 5→8, 10→16). Default width is 1 (exact values).
* Only the span `[minBucket, maxBucket]` is allocated:
  `countPerBucket[k]` = occurrences in bucket `firstIndexValue + k`.
  Memory is roughly `(max-min)/width + 1` ints.
* On a value outside the current span the array is grown and existing counts are
  shifted right (left growth) or simply copied (right growth).

Verified example (`getPrecision()` returns the *width*):
`new RuntimeHistogram(8)` → shift 3, width 8, so 0..7 share bucket 0, 8..15 bucket 1, …
Adding 0,7,8,15 gives 2 buckets and 4 values.

### 3.2 Percentile / quantile rule

For `n` added values and `p` in [0,100] (also 0 is accepted although the error
message says `(0, 100]`):

* `p == 0` → lower edge of the bucket holding the minimum,
* `p == 100` → lower edge of the bucket holding the maximum,
* otherwise `np = n * p/100`:
  * if `np` is an integer → average of the values at ranks `np` and `np+1`,
  * else → the value at rank `ceil(np)`.

Rank lookup walks the buckets from the lowest index upwards until the cumulative
count reaches the rank. The reported value is always the bucket’s **lower edge**
(`(firstIndexValue + bucketIndex) << precisionShift`), never an interpolation inside
the bucket. Example: single value `100` at width 8 → bucket 12 → every percentile
returns `96.0`. The exactness of this quantile rule was cross-checked in the tests
against an independent sorted-list re-implementation (for width 1) and a
bucket-quantized reference (for wider buckets), including negative values.

### 3.3 Range counting `getCountForValue(start, end)`

Returns the sum over all buckets whose index lies in
`[start >> shift, end >> shift]`, after clamping to the existing bucket span.
Consequences (verified):

* The query range is **inclusive** on both ends, but the bucket granularity means
  partial overlaps count the *whole* bucket. Example at width 8 with values 0 and 7:
  `getCountForValue(7,7)` returns **2** because both values share bucket 0.
* A range that does not intersect the stored bucket span at all returns 0.
* `start > end` throws `IllegalArgumentException`.
* Empty histogram → 0 for any valid range.

### 3.4 Known limits

* `valueCount` and the per-bucket counts are 32-bit `int` – ~2.1 billion values
  overflow silently.
* A value whose bucket index is far outside the current span causes a full-array
  copy on every such step; an adversarial sequence (huge alternating jumps) is
  O(n²) overall. Practical XLT data is dense in a small range.
* A single bucket array element can overflow when more than 2^31 values land in
  one bucket.

---

## 4. `BitUtil` and `BitCompression` — the bit primitive layer

### 4.1 BitUtil (Lucene port)

Pure static helpers. `RuntimeHistogram`/`IntTimeSeries` only call
`nextHighestPowerOfTwo(int)`. The rest (`pop*`, `ntz*`, `isPowerOfTwo`) is dead code
in this module but was tested anyway since it is public API. The popcount variants
(`pop_array`, `pop_intersect`, `pop_union`, `pop_andnot`, `pop_xor`) operate on a
range `[wordOffset, wordOffset+numWords)` of a `long[]` and use the 8-word CSA trick
from Hacker’s Delight. All tests compare them word-by-word against
`Long.bitCount`.

Caveats worth knowing:

* `ntz(0L)` = 64 and `ntz(0)` = 32 by construction of the table lookup
  (JLS/`Long.numberOfTrailingZeros` also returns 64/32 for zero, so they agree).
* `ntz2`/`ntz3` are documented to only work for `x != 0`.
* `nextHighestPowerOfTwo` returns garbage (not a power of two) for inputs whose
  result would not fit the type (`int` > 2^30, `long` > 2^62) – the surrounding
  code must keep sizes below that.
* `isPowerOfTwo(0)` is `true` (matches `(v & (v-1)) == 0`).

### 4.2 BitCompression – the folding primitive

`IntTimeSeriesEntry` stores its distinct-value bitmap as two words
(`distinctValuesLow` = buckets 0..63, `distinctValuesHigh` = buckets 64..127).
When the scale must increase (a value arrives whose quantized bucket is ≥ 128),
the bitmap resolution is halved. The two-step primitive is:

```java
combined  = combineAdjacentBits(word);   // word | (word << 1)
compressed= compressAndShiftOddBits(word);// extract odd bits, pack to lower 32
```

Semantics (verified property): **new bit `k` is set iff at least one of old bits
`2k` or `2k+1` was set** — exactly the “join adjacent buckets” behaviour needed for
halving the bucket width. `combineAdjacentBits` alone sets both members of a pair;
`compressAndShiftOddBits` then drops the even member and compacts the odd members.

Important detail of the *halving step in `IntTimeSeriesEntry`* (see §6):
the high word is compressed and then **shifted 32 bits to the left and ORed into the
low word**, i.e. the two 64-bit halves fold into one 64-bit word.

---

## 5. `IntTimeSeries` — the time-condensing series

### 5.1 Construction

* `new IntTimeSeries()` → `DEFAULT_SIZE = 3600`, rounded to `size = 4096`.
* `new IntTimeSeries(n)` → `size = nextHighestPowerOfTwo(n)`, so the series can only
  represent a time window whose length is a power of two in slots.
* All slots are pre-allocated and pre-initialized (`new IntTimeSeriesEntry()`)
  so no null checks are needed on the hot path.
* An embedded `new RuntimeHistogram(8)` (bucket width 8) is used for percentiles.

### 5.2 `addValue(startTime, endTime, value, failed)`

1. Convert to seconds; remember `startSecond`, `endSecond`.
2. Window management:
   * if `startSecond < firstSecond` → `shiftRight(startSecond)` and move the window
     start; or
   * if `endSecond` would exceed `firstSecond + seconds(size)` → `condense(endSecond)`
     (double the slot width until the end fits).
3. `values[pos(startSecond - firstSecond)].updateValue(value, failed)` — the slot’s
   `concurrentCount` is incremented once for the start second.
4. For every further second the measurement spans
   (`i = startSecond+1 … endSecond`, stepping by the current slot width) the slot’s
   `updateConcurrency()` is called. So a transaction that merely touches a second is
   counted once in that second (the code comment spells this out).
5. `sumOfSquares += Math.pow(value, 2)` and `histogram.addValue(value)`.

A 3-argument convenience overload delegates with `endTime == startTime`.

### 5.3 `shiftRight` (earlier data arrives)

Moves all slot content to the right by
`adjust(firstSecond) - adjust(second)` slots and fills the freed slots with fresh
entries. This preserves all aggregates (verified: re-adding an earlier transaction
keeps sum/count and simply re-indexes the slots).

### 5.4 `condense` (window too small) — *the interesting part*

`condense(targetSecond)` doubles the slot width until
`targetSecond < firstSecond + seconds(size)`:

```
round:  merge slots (0,1)->0, (2,3)->1, …          // v1.merge(v2)
        fill the remaining second half with fresh entries
        scale++, lastPosUsed >>= 1
```

* **`count`, `sum`, `errorCount`, `min`, `max` survive merging exactly**; only the
  distinct-value approximation and percentiles degrade. Verified over 400 values /
  2000 s in a 16-slot series.
* **Concurrency is *not* summed across merged slots** — the merge takes the maximum
  (`concurrentCount = max(...)`), because the field means “maximum simultaneous
  load”, not “number of measurements”. This approximation is only correct while
  slot width ≤ transaction length; the coarse second-loop counting in `addValue`
  already blurs it (see §8).
* `merge` may mutate its argument to align the distinct-value scale (documented in
  the javadoc: *“Attention: this might modify the given item”*).

### 5.5 Aggregated views

`getStatistics()`/`getCount()`/`getTotalValue()`/`getErrorCount()`/`getMean()`/
`getStandardDeviation()` scan **all slots** (they are O(size) per call) and skip
empty ones. `getMean` and `getStandardDeviation` guard division by zero. The
standard deviation formula is `sqrt(sumOfSquares/n - mean²)`; `sumOfSquares` uses
the *unclamped* raw value.

`getPercentile(p)` simply forwards to the embedded width-8 histogram and casts to
`int`. Verified: feeding the same values into a standalone
`RuntimeHistogram(8)` reproduces every percentile.

`toHistogram(bucketCount)` builds `bucketCount` buckets between `0` and the observed
maximum and answers each with `histogram.getCountForValue(...)`. Two quirks:

* bucket 0 starts at **0**, not at `min`;
* consecutive bucket ranges may share a bucket-index (8-wide histogram buckets
  straddle the boundaries), so values near a boundary can be double counted.

---

## 6. `IntTimeSeriesEntry` — exact stats + approximate distinct values

### 6.1 `updateValue(value, failed)`

* Clamp negative values to 0.
* `count++`, `errorCount += failed?1:0`, `totalValue += v`, `concurrentCount++`,
  update `minimum`/`maximum`.
* For the distinct bitmap: `v = scaleIfNeeded(v)` then set bit `v` (low word if
  `v<64`, high word if `64 ≤ v < 128`).

### 6.2 `scaleIfNeeded` — adaptive bucket width

Keeps the invariant “all buckets fit into 128 bits”:

```
while (v >= 128):
    low  = compress(combine(low))
    high = compress(combine(high))
    low  = low | (high << 32)   // pack both halves into one word
    high = 0
    scale++
    v = value >> scale
```

Consequence (and a very useful test property, verified for random data):
independent of insertion order, after the dust settles every added value `x` is
represented by the bit `x >> finalScale`, and `getValues()` returns exactly the set
`{ (x >> scale) << scale : x in values }` — i.e. all reported values are multiples
of `2^scale`. Only *lower edges* are reported; the real `min`/`max` are kept exactly
in separate fields, so `getMinimumValue`/`getMaximumValue` never lie.

`merge` aligns the two scales first (scaling up either `this` or `item` using the
same fold primitive, **mutating** the smaller one), then ORs both bitmaps and the
numeric aggregates.

### 6.3 Verified defect — merging entries with high-word distinct values

Scaling **up** in `merge` compresses the low and high word but — unlike
`scaleIfNeeded` — does **not** re-pack `high` into `low`. If the entry being scaled
has occupied high-word bits, those bits land at the wrong position and the reported
distinct values are wrong (e.g. value 100 becomes 164 after a merge with a scale-1
entry). The disabled test
`IntTimeSeriesEntryTest#mergeScalesHighWordDistinctValuesCorrectly` pins the
expected, correct outcome.

---

## 7. Percentiles/statistics vs. reality — what each number really means

| Metric | Where | Exact? |
|---|---|---|
| count, sum, min, max, errorCount per slot and overall | entries | exact (except negative clamping) |
| mean / standard deviation | entries + `sumOfSquares` | exact for clamped values |
| percentiles | embedded width-8 histogram | approximate; always a bucket *lower edge*, values within ±7 of the true quantile |
| distinct values | 128-bit scaled bitmap | approximate; multiples of `2^scale` |
| concurrency | per-slot `concurrentCount` | approximate (second coverage + max-merge) |
| `firstSecond`/`lastSecond` | `lastPosUsed` | `lastSecond` is the *lower edge* of the last touched slot, which can be a few seconds before the actual newest value once slot width > 1 |

---

## 8. Pitfalls / invariants one must know before touching this code

1. **Seconds are `int`.** `(int)(time * 0.001)` saturates at `Integer.MAX_VALUE`
   (~2038-01-19) for larger millisecond values; everything beyond that is broken.
   `firstSecond`’s sentinel `DEFAULT = 2_147_385_000` (2037-12-31) sits very close to
   that edge, so `condense`/`shiftRight` can run into int overflow.
2. **Sentinel collision.** If the first added transaction happens to be at second
   `DEFAULT` exactly, `firstSecond == DEFAULT` is still true afterwards and
   `getFirstSecond()`/`getLastSecond()` throw `IllegalStateException` although data
   exists. Tests use a base time of `1_700_000_000_000` ms.
3. **Condense data loss on far jumps.** A *single* jump larger than
   `size * size` seconds (default: ~16.7M s ≈ 6.4 months) runs the condense loop
   down to `l == 1`, where the code overwrites `values[0]` with a fresh empty entry
   instead of merging — silently dropping the **oldest** data. Disabled regression:
   `IntTimeSeriesTest#condenseFarJumpsDoNotLoseEarlyData`. Jumps smaller than
   `size*size` seconds are lossless (verified with a 1e6 s jump at size 4096).
4. **`shiftRight` crashes for far-*earlier* jumps.** Inserting a transaction far
   before `firstSecond` when the offset exceeds the slot count computes a negative
   `System.arraycopy` length → `ArrayIndexOutOfBoundsException`.
5. **Constant data breaks `toHistogram`.** When `min == max`, the bucket width is 0
   and internal bucket ranges become `[x, x-1]`, so `toHistogram(n>1)` throws
   `IllegalArgumentException`. `toHistogram(1)` happens to work. Disabled regression:
   `IntTimeSeriesTest#toHistogramHandlesConstantValues`.
6. **Negative input handling is inconsistent.** Entries clamp values to 0, but
   `IntTimeSeries.sumOfSquares` and the embedded histogram receive the raw value.
7. **`merge` has side effects and a high-word bug** (see §6.3).
8. **O(n) scans.** Every aggregate getter walks all `size` slots; repeated calls are
   expensive for large series.
9. **Floating quantile rule.** The “is `np` an integer?” test is a plain
   `% 1.0 == 0.0` comparison. Tests replicate the exact same arithmetic.
10. **Non-atomic time arithmetic.** The number of seconds a transaction covers and
    the concurrency counting loop use truncation, so 5000 ms spans seconds
    `start .. start+5` (six touched seconds) — be careful when asserting.

---

## 9. Testing notes (how the new suites work)

| Test class | Exercises |
|---|---|
| `…lucene.BitUtilTest` | popcount vs `Long.bitCount`, array ops vs word-by-word bitwise ops for all remainder branches (0…33 words), `ntz*` vs JDK, power-of-two helpers vs independent references. |
| `…misc.BitCompressionTest` | fold semantics: single bit j → bit j>>1; adjacent pairs collapse; random masks vs the “new bit k = old bit 2k or 2k+1” model. |
| `…RuntimeHistogramTest` | empty/validation, exact & bucketed percentiles vs sorted references, whole-bucket range counting, growth in both directions, order independence. |
| `…rework.IntTimeSeriesEntryTest` | exact aggregates, clamping, error/concurrency counting, distinct-value quantization model on random data, merge aggregation & mutation, one `@Disabled` regression. |
| `…rework.IntTimeSeriesTest` | empty state, slot placement, shift-right, condensing (single and gradual), statistics preservation, percentile parity with `RuntimeHistogram(8)`, histogram buckets, two `@Disabled` regressions. |

Two general rules that keep the tests deterministic:
use whole-millisecond timestamps far below the 2038 edge, and stay inside the
documented lossless ranges (§8.3).
