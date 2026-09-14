# XLT-DATA — Proposal for a re-designed report data-collection layer

> **Status: proposal only. Nothing in this document is implemented.**
> The code described here lives in
> `demo5/src/main/java/com/xceptance/xlt/report/util/` (packages `util`,
> `util.lucene`, `util.misc`, `util.rework`). The current behaviour, invariants,
> and defects are documented in [`xlt-util-architecture.md`](xlt-util-architecture.md).

---

## 1. Why change anything?

The classes work and are fast for the common case (chronological, dense,
non-negative data within a bounded time window), but the review and the test suites
(`src/test/java/com/xceptance/xlt/report/util/…`) exposed several structural issues:

| # | Issue | Evidence |
|---|---|---|
| 1 | **Seconds are `int`; timestamps after ~Jan 2038 break** (saturating cast, sentinel `DEFAULT = 2_147_385_000` near the edge, `condense` int overflow). | architecture doc §8.1–8.2 |
| 2 | **`condense()` drops the oldest data** when a single jump exceeds `size*size` seconds (`l` reaches 1 and `values[0]` is overwritten). | `IntTimeSeriesTest#condenseFarJumpsDoNotLoseEarlyData` (`@Disabled`) |
| 3 | **`shiftRight()` throws** `ArrayIndexOutOfBoundsException` when an out-of-order transaction is far earlier than the recorded window. | architecture doc §8.4 |
| 4 | **`merge()` corrupts distinct values** that live in the high 64-bit word (no `high<<32` re-pack on scale-up). | `IntTimeSeriesEntryTest#mergeScalesHighWordDistinctValuesCorrectly` (`@Disabled`) |
| 5 | **`toHistogram` throws for constant data** (`min == max` ⇒ bucket width 0). | `IntTimeSeriesTest#toHistogramHandlesConstantValues` (`@Disabled`) |
| 6 | **One class, many jobs.** `IntTimeSeriesEntry` mixes exact aggregates, a lossy distinct-value sketch, concurrency counting, and merge logic; `IntTimeSeries` adds window management, a second histogram, and `sumOfSquares`. Bookkeeping is triplicated (entry, `sumOfSquares`, histogram) and inconsistent for negative input. | code structure |
| 7 | **Percentiles are always approximate** (width-8 buckets, lower-edge only), while other statistics are exact — callers can’t tell which is which, and there is no way to choose a precision/error budget. | architecture doc §7 |
| 8 | **Every aggregate getter is O(size).** `getCount()`, `getTotalValue()`, `getMean()`, … each scan the whole slot array. | code |
| 9 | Concurrency counting is an approximation of “simultaneous transactions per second” and its semantics are not documented on the API. | architecture doc §2.3, §5.4 |

**Non-goals** for this proposal: thread safety, distributed use, floating-point
time, histograms for sub-millisecond data.

---

## 2. Design principles for the replacement

1. **Time as an axis, not a side effect.** One explicit “timeline” abstraction owns
   slot width, window, and re-basing. All arithmetic in `long` seconds.
2. **One statistic, one owner.** Exact statistics live only in the per-slot
   aggregator; approximate outputs (percentiles, histograms, distinct counts) are
   computed from explicitly configured, replaceable components — never silently
   hard-wired.
3. **Lossless by construction.** Collapsing a slot pair must be a pure monoid
   merge that can never lose a value. If memory requires sampling/approximation,
   that decision is explicit and has a documented error bound.
4. **Fixed memory, predictable CPU.** Insert is O(1) amortized; aggregates are
   O(1) by maintaining running totals (with a cheap undo/recompute path for
   compaction events).
5. **Small, well-named types.** Records for immutable statistics snapshots;
   value types instead of fat mutable beans.

---

## 3. Proposed target architecture

```
MeasurementProducer                (replaces: addValue plumbing in IntTimeSeries)
   │  (startMs, endMs, value, failed, flags)
   ▼
TimeAxis / RingWindow              (replaces: size/scale/shiftRight/condense)
   - long firstSecond (epoch seconds), long windowSeconds
   - slot width: 1 | 2 | 4 | … s, stored as a small int shift
   - ring buffer of Slot[]; rebase = adjust ring head + lossless merge of edge
   ▼
Slot / PerSecondStats              (replaces: IntTimeSeriesEntry)
   - exact: long count, long sum, long min/max (null when empty), long errorCount
   - immutable snapshot: record Stats(long count, long sum, long min, long max, …)
   - pluggable valueStats: exact min/max/sum always; everything else opt-in
        * QuantileSketch   (configured error, e.g. HdrHistogram-style or t-digest)
        * DistinctCounter  (optional; exact long-bitmap for small range, else Bloom/…)
   ▼
IntTimeSeries (facade)
   - add(measurement); getStats()/getQuantile(p)/toHistogram(...)/forEachSlot(...)
   - running totals so getCount() is O(1)
```

### 3.1 TimeAxis / window (`replaces IntTimeSeries window logic`)

* Keep **epoch seconds as `long`**; never saturate near 2038.
  API takes epoch milliseconds as `long` and converts with
  `Math.floorDiv`/`TimeUnit.MILLISECONDS.toSeconds`.
* Window model: a **ring buffer** of `size` slots plus an explicit `headSecond`.
  `size` need no longer be a power of two (the power-of-two requirement existed
  only for `& (mask)` indexing and the integer shift games).
* **Rebase left (earlier data):** move the ring head; if the distance exceeds the
  window, *first* expand the window to cover both edges, then move. Never copy more
  than `min(distance, size)` slots.
* **Rebase right (later data):** if `endSecond >= headSecond + windowSeconds`,
  enlarge slot width in powers of two until it fits (as today), but implement
  collapse as a **pairwise fold with explicit survivor handling**:
  `slot[i] = fold(slot[2i], slot[2i+1])` where `fold` is the monoid merge and the
  “free” half is cleared **without ever overwriting the survivor**
  (fix for defect #2). Because collapse is a monoid fold, a whole history can also
  be collapsed lazily in O(size) once, rather than every add.
* Add regression tests (#2, #3) and enable them once fixed.

### 3.2 Slot statistics — exact core

A slot is conceptually the record:

```java
record SlotStats(long count, long sum, long min, long max, long errorCount)
```

with the monoid `merge(SlotStats a, SlotStats b)`:
`count = a+b; sum = a+b; errorCount=a+b; min=min(a,b); max=max(a,b)`.

* `count`/`sum`/`errorCount` as **`long`** — today `int` count overflows at
  2^31 and `sum` is `long` anyway.
* Empty slots are `null`/absent (ring of `SlotStats[]`), no sentinel values like
  `Integer.MIN_VALUE` masquerading as “no max”.
* The running overall totals are updated incrementally, so `getCount()`,
  `getSum()`, `getMean()`, `getStdDev()` are **O(1)** (defect #8). Std-dev uses an
  incremental sum-of-squares (`Welford`-style for stability) with the same clamp
  policy as sum (see §3.4).

### 3.3 Quantiles and histograms — pluggable, bounded, honest

Today every percentile silently uses a width-8 bucket histogram and every value is
added to it, whether or not percentiles are requested (wasted work + wrong
precision for some use cases).

* Introduce `QuantileEstimator` (interface) with a **relative-error or
  rank-error bound**:
  - option A: keep the dense-power-of-two histogram but fix the API (requested
    width validated `≥1`, counts in `long`, no silent sentinel second),
  - option B: a two-level histogram (fine buckets near the median, coarse tails),
  - option C: an existing library sketch (e.g. HdrHistogram) if dependencies are
    acceptable.
* `getQuantile(p)` returns `double` and documents that the answer is an estimate
  with a bounded error; `getExactMin/Max/Mean/Count` stays exact.
* `toHistogram(bucketCount)` is implemented over the *same* estimator, so constant
  data degrades gracefully (a single bucket / one bar per value) instead of
  throwing (defect #5).
* Default: do not maintain a quantile estimator at all unless requested
  (per-slot or global), to keep the common “no percentiles” path minimal.

### 3.4 Distinct values and negatives — decide and simplify

* **Negative inputs:** the old code clamps to 0 but then computes
  `sumOfSquares`/histogram from the raw value (inconsistent, defect #6). Decide
  once: either reject/ignore negative samples at the API boundary, or document that
  negatives are clamped *everywhere*. Recommendation: reject with a clear
  `IllegalArgumentException` for the performance-data use case, or add a
  `normalize` policy parameter.
* **Distinct values:** the current 128-bit folded bitmap is a neat trick but adds
  the hard-to-reason merge bug (#4). Options:
  - drop it from the slot (it is not used by the current report math) — preferred;
  - keep it as an explicitly optional, immutable `ValueSetSketch` that stores exact
    values while they fit and only then switches to the folding scheme, with the
    high-word packing done in **one** shared helper used by both `scaleIfNeeded`
    and `merge` (fixes #4), so there is exactly one place that understands the
    bitmap layout.

### 3.5 Concurrency — define the semantics

Decide what `concurrentCount` means and make the API say so:
“maximum number of transactions simultaneously active during this slot”.
A correct and still cheap implementation: for each measurement
`[startSecond, endSecond]` add +1/−1 *events* at the slot edges and sweep once per
window when reporting — or keep the per-second increment but merge as a *sum per
overlap*, not a plain max, and document the ±slot-width error. Add property tests
that compare against an event-sweep oracle.

### 3.6 Error values

Decide at the API level whether failed transactions contribute to
`sum/count/quantiles` or only to `errorCount`. Today they are silently included in
everything (architecture doc §7). Recommendation: make it explicit
(`add(measurement)` vs `addFailed(measurement)`), because for load testing error
response times are typically *not* part of “response time” percentiles.

---

## 4. Migration & verification plan

1. Keep `RuntimeHistogram`, `BitUtil`, `BitCompression` as-is; they are small and
   correct in their domain. Only `IntTimeSeries`/`IntTimeSeriesEntry` are replaced.
2. New tests first, then implementation, per component (test-last-first of the
   existing suites remain the executable spec):
   * enable the three `@Disabled` regressions in
     `IntTimeSeriesTest` / `IntTimeSeriesEntryTest`,
   * add the far-earlier-`shiftRight` regression,
   * property-test `merge` against the “value set quantized by final scale” model
     including high-word values,
   * property-test `condense`/window against a brute-force per-second model
     (identical `count/sum/min/max` before and after every compaction),
   * property-test concurrency against an event-sweep oracle.
3. Performance targets (micro-benchmarks, JMH as already used in this module):
   `add` stays allocation-free in the steady state; `getStats()` is O(1);
   percentiles over 10^7 values ≤ some ms; collapse cost is amortized O(1) per add.
4. The old classes are deleted only after the new facade passes the whole existing
   test corpus unchanged (except the three regressions, which now run green).

## 5. Open questions

1. Does the report still need *per-slot* min/max/avg rendering, or only aggregates
   and a chart-ready histogram? (Determines how much slot state we keep.)
2. Is a dependency on an external sketch library acceptable?
3. Do we need percentiles for the *whole report* only, or also per time-window?
4. Is sub-second or cross-midnight behaviour (UTC/local) relevant for time slots?
5. Should the concurrency counter count a transaction that merely *touches* a
   second as 1 (today) or only fully overlapping intervals?

## 6. Summary

The proposed redesign separates time-window management from slot statistics,
makes all arithmetic `long`-based and lossless, removes the three crash/data-loss
defects, makes aggregates O(1), and turns percentile accuracy into an explicit,
configurable decision with a documented error bound — while keeping the memory
profile (bounded slot array) that made the current design attractive.
