# XLT Report Util — Architecture & Concepts

Scope: the untested code under `com.xceptance.xlt.report.util` in `demo2`.

This document explains **what** these classes do, **why** they exist, and **how** they interact.
For concrete improvement ideas, see [`XLT-DATA.md`](XLT-DATA.md).

---

## 1. Package Map

```
com.xceptance.xlt.report.util
├── RuntimeHistogram.java          (counting histogram for percentiles)
├── lucene/
│   └── BitUtil.java               (bit-twiddling helpers, Apache Solr heritage)
├── misc/
│   └── BitCompression.java        (bitset compression used during scale-up)
└── rework/
    ├── IntTimeSeries.java         (time-bucketed statistics container)
    └── IntTimeSeriesEntry.java    (per-bucket statistics + approximate distinct-value set)
```

**Layer rule:** `rework` depends on `util`, `util` depends on `lucene` and `misc`.
Nothing flows upward.

```
rework/IntTimeSeries
    ├── uses util/RuntimeHistogram           (for percentiles & toHistogram)
    └── uses util/lucene/BitUtil             (for nextHighestPowerOfTwo)

rework/IntTimeSeriesEntry
    └── uses util/misc/BitCompression        (for distinct-value scale-up)
```

---

## 2. The Problem These Classes Solve

Xceptance LoadTest (XLT) collects one measurement per sample (response time, bytes, etc.).
Report generation needs:

* min / max / mean / error count
* arbitrary percentiles (P50, P95, P99, ...)
* standard deviation
* an idea of the distinct values seen
* all of that **per second** (or coarser) over a potentially long test run

Two hard constraints shape the design:

1. **Memory** – a full day at 1 s resolution is 86 400 buckets; naive per-bucket value
   lists would explode heap.
2. **CPU** – updates happen on the hot aggregation path; anything that allocates or
   copies per sample is too slow.

The result is a set of **fixed-size, self-compressing data structures** that trade
exactness for bounded memory.

---

## 3. Class-by-Class

### 3.1 `BitUtil` (lucene)

A straight copy of Apache Solr's `org.apache.solr.util.BitUtil` (rev 555343).
Pure static helpers, no state, no surprises.

Used here only for:

* `nextHighestPowerOfTwo(int)` — normalizes bucket counts and precision values.

Everything else (`pop`, `ntz`, `nlz`, `pop_array`, …) is unused by the current code but
kept as a utility library.

> **Gotcha:** `nextHighestPowerOfTwo(0)` returns `0`, `nextHighestPowerOfTwo(1)` returns `1`.
> `nextHighestPowerOfTwo(Integer.MAX_VALUE)` overflows and returns a negative number.
> The current callers never pass those values.

---

### 3.2 `BitCompression` (misc)

Two tiny static helpers used by `IntTimeSeriesEntry` when it has to halve the resolution
of its distinct-value bitset.

* `combineAdjacentBits(long v)` → `v | (v << 1)`
  ORs every bit with its right neighbour. After this, bit `2k+1` is set iff either
  bit `2k` or `2k+1` was set in the input. This is the *lossy merge* of two adjacent
  value buckets.

* `compressAndShiftOddBits(long v)` → packed 32-bit word
  Extracts bits 1, 3, 5, …, 63 and packs them into positions 0..31. Uses the classic
  5-stage SWAR ladder (`0x5555…`, `0x3333…`, `0x0F0F…`, …).

Pipeline:

```
128-bit set (low+high)
  → combineAdjacentBits  (each 64-bit word, pairwise merge)
  → compressAndShiftOddBits  (extract odd bits, now 32 bits per word)
  → join the two 32-bit halves into a new 64-bit low word
  → high word := 0
  → scale++
```

This halves the memory footprint of the distinct-value tracker while preserving
*“was any value in this pair seen?”* information.

---

### 3.3 `RuntimeHistogram` (util)

A **counting histogram** for arbitrary int percentiles.

Concept:

* values are bucketed by right-shifting with a *precision* (`precision` is the log₂
  of the bucket width, e.g. precision=3 → buckets of width 8).
* only the **counts per bucket** are stored, not the values.
* the bucket array grows lazily left or right to accommodate the min/max seen.

Complexity:

* `addValue` – O(1) amortized, occasional array resize
* `getPercentile` – O(buckets) linear scan for the bucket containing the requested rank

Important details:

* `precision` is stored as the **shift amount**, `getPrecision()` returns `1 << shift`.
* Negative values are not handled gracefully: `-1 >> shift` is still a huge positive
  index, which will blow up the bucket array. Callers must clamp to ≥ 0.
* `getCountForValue(start, end)` works in bucket space; both ends are shifted by the
  same precision, so the range may silently extend past the requested `end` when the
  end falls inside a partially-covered bucket.
* `firstIndexValue` / `lastIndexValue` track the *bucket index* of the smallest /
  largest value seen, not the raw value.

Used by `IntTimeSeries` for percentile computation and by `toHistogram(bucketCount)`.

---

### 3.4 `IntTimeSeriesEntry` (rework)

Statistics for **one time slot** (one second at scale=1, two seconds at scale=2, …).

Fields:

| field                | meaning                                                  |
|----------------------|----------------------------------------------------------|
| `totalValue`         | Σ of all values (for mean)                               |
| `count`              | number of samples                                        |
| `concurrentCount`    | max concurrent measurements overlapping this slot        |
| `errorCount`         | number of failed samples                                 |
| `maximum`, `minimum` | extremes; sentinel-based                                 |
| `distinctValuesLow`  | bits 0..63 of a 128-bit set of approximated distinct values |
| `distinctValuesHigh` | bits 64..127                                             |
| `distinctValuesScale`| log₂ of the value-quantization factor                    |

Distinct-value approximation:

* A value `v` is right-shifted by `distinctValuesScale`, then bit `v >> scale` is set.
* If the shifted value is ≥ 128, the whole bitset is **compressed** (via `BitCompression`)
  and `distinctValuesScale` is incremented. This halves precision but keeps the set bounded.
* Negative input values are clamped to `0` before being tracked.

Merging:

* `merge(other)` aligns scales (compressing the coarser one further), then ORs the
  bitsets, sums the counters, takes `max(concurrentCount)`.
* Merging **mutates** the passed-in `other` when its scale has to be raised!
  The method returns `this` for chaining.

> **Subtle bug/quirk:** `updateValue` uses `v` (clamped) for `maximum` and `totalValue`,
> but compares `value < this.minimum` with the **raw** value. Since `v = max(0, value)`,
> negative values push `minimum` negative while `maximum` stays ≥ 0. All current
> production inputs are non-negative, so this is dormant.

---

### 3.5 `IntTimeSeries` (rework)

A **fixed-size ring of `IntTimeSeriesEntry` buckets** covering a sliding window in time.

Concept:

* `size` = number of buckets, always a power of two (rounded up from the requested size;
  default request 3600 → actual 4096).
* `scale` = log₂ of bucket width in seconds (`scale = 1` → 1 s/bucket, `scale = 2` → 2 s/bucket, …).
* `firstSecond` = the epoch-second that maps to bucket 0 (raw seconds, never scaled).
* `lastPosUsed` = highest bucket index touched so far.

Time maths:

* ms → seconds: `(int)(ms * 0.001)` — comment says *"mul is faster than div on x86"*.
  (True historically, questionable on modern JIT; kept as-is.)
* `adjustToScale(v) = v >> (scale - 1)` — convert a second-offset to a bucket index.
* `expandToSeconds(p) = p << (scale - 1)` — inverse: bucket index → second-offset.

Behaviour on insert:

1. **Too far left** (`startSecond < firstSecond`) → `shiftRight`: physically moves all
   entries up by the required offset, inserts fresh empty entries at the beginning.
2. **Too far right** (`endSecond ≥ firstSecond + size * slotWidth`) → `condense`: merge
   pairs of adjacent entries until the new value fits. `scale++` per round.
3. Then the sample is written to `values[adjustToScale(startSecond - firstSecond)]`.

Concurrency tracking: every *additional* second the request overlaps increments
`updateConcurrency()` on the matching bucket (with the current scale as step size).

Aggregate state kept outside the buckets:

* `sumOfSquares` — for standard deviation (avoids re-iterating buckets).
* `histogram` — a `RuntimeHistogram(8)` with **every** value ever added, used for
  percentiles and `toHistogram(bucketCount)`. This histogram is **not** affected by
  condensation: percentiles stay exact-to-bucket even after the time axis has been
  coarsened.

Public aggregates (`getCount`, `getTotalValue`, `getErrorCount`, `getMean`,
`getStandardDeviation`, `getStatistics`) all rebuild a `Statistics` object by walking
the buckets; there is no caching.

`HistogramBucket` is a Java `record (startValue, endValue, count)`.

---

## 4. Key Concepts to Understand Before Touching the Code

### 4.1 Two different scales

* **Time scale** (`IntTimeSeries.scale`) — how many seconds one bucket covers.
* **Value scale** (`IntTimeSeriesEntry.distinctValuesScale`) — how many raw units one
  distinct-value bit covers.

They are **independent**. A condense on the time axis merges entries; each entry keeps
its own value scale and only aligns with its partner during `merge`.

### 4.2 Power-of-two everywhere

* bucket count (`size`) is a power of two
* bucket width (`1 << (scale-1)`) is a power of two
* value quantization is a power of two

This lets the implementation use shifts and masks instead of division and modulo.

### 4.3 Lossy by design

| What            | Where it gets lossy                                              |
|-----------------|------------------------------------------------------------------|
| Time resolution | `condense` merges two seconds into one — fine-grained per-second info is gone |
| Distinct values | `scaleIfNeeded` halves value precision once > 127                |
| Percentiles     | `RuntimeHistogram(8)` rounds values into buckets of 8            |
| Minimum         | sentinel `Integer.MAX_VALUE` → `0` hides "no data" vs "really 0" |

### 4.4 Year-2038 limitation

Seconds are stored in a signed `int` (`firstSecond`, arithmetic in `addValue`).
`(int)(System.currentTimeMillis() * 0.001)` overflows in **January 2038**.
The code comments acknowledge this ("*we don't care yet*"). All new code should
consider moving to `long` if the product is expected to be running then.

### 4.5 Singleton histogram for all values

`IntTimeSeries.histogram` sees **every** value once, regardless of which bucket it
lands in, and regardless of any later condense/shift. This is the only place where
percentile information survives long-running tests intact — but it also means the
histogram grows with the *value range*, not the time range.

---

## 5. Known Weaknesses (documented, not fixed)

1. `IntTimeSeries.toHistogram(n)` **breaks when `min == max`**
   (`bucketWidth = 0` → range inversion → IAE). Reproduced in
   `IntTimeSeriesTest.toHistogram_singleValue_throwsIAE_whenMinEqualsMax`.

2. `IntTimeSeries.toHistogram` bucket counts **can exceed the total value count**,
   because `RuntimeHistogram.getCountForValue` works in bucket space and an edge
   bucket may be counted in two adjacent output buckets. Reproduced in
   `IntTimeSeriesTest.toHistogram_uniformDistribution`.

3. `IntTimeSeriesEntry.updateValue` uses the **raw** value for the minimum comparison
   but the **clamped** value for maximum/total/distinct set — inconsistent for
   negative inputs.

4. `IntTimeSeries` exposes its internal bucket array via `getValues()` —
   callers can mutate entries in place.

5. `Statistics` is a mutable POJO with `public` fields — no encapsulation.

6. `IntTimeSeriesEntry.merge` **mutates the argument** when scales differ.
   Surprising side effect for callers who reuse the merged-from entry.

7. `RuntimeHistogram` with negative values: `-1 >> p` becomes a huge index and grows
   the bucket array to ~2³¹ entries → OOM. Input validation is absent.

8. `IntTimeSeries.addValue` walks the "extra seconds" with step `this.scale`, but at
   scale > 1 a request spanning 2 s may be tracked in only one bucket — concurrency
   is underreported after condensation.

---

## 6. Test Suite

New tests added (all JUnit 5 / Jupiter, plain assertions):

| Test class                     | Tests | Focus                                            |
|--------------------------------|-------|--------------------------------------------------|
| `BitCompressionTest`           | 19    | bit patterns, edge cases, idempotence, pair semantics |
| `RuntimeHistogramTest`         | 25    | constructor rounding, growth, percentiles, count ranges |
| `IntTimeSeriesEntryTest`       | 26    | counters, min/max, distinct values, scaling, merge, equals |
| `IntTimeSeriesTest`            | 25    | addValue, shift/condense, statistics, percentiles, known bugs |

Total: **95 new tests** — module builds green.
