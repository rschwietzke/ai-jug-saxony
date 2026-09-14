# XLT-DATA — Proposal for an Improved Time-Series Data Pipeline

**Status:** Proposal — **DO NOT IMPLEMENT**
**Author:** AI-assisted analysis
**Scope:** `com.xceptance.xlt.report.util` in `demo2`
**Companion document:** [`architecture.md`](architecture.md) explains the current design.

---

## 1. Why a Proposal?

The current classes work, but they have grown organically and share a few recurring
weaknesses:

* silent loss of accuracy (three independent scale mechanisms)
* undocumented edge-case behaviour (`min == max`, negative inputs, year 2038)
* surprising side effects (`merge` mutates its argument, `getValues()` leaks internals)
* hard-coded trade-offs (precision=8 for the histogram, 128 bits for distinct values)
* no way to plug in a different storage / accuracy trade-off without forking code

This proposal sketches an **API-first redesign** that keeps the performance profile
but makes the trade-offs **explicit, configurable, and testable**.

---

## 2. Design Goals

| # | Goal |
|---|------|
| G1 | Bounded memory, same as today |
| G2 | Amortized O(1) `addValue`, same as today |
| G3 | **Explicit** accuracy contract per statistic |
| G4 | No hidden mutation of caller-visible state |
| G5 | Pluggable precision strategy (conservative / balanced / aggressive) |
| G6 | Java 21+, no external dependencies beyond the JDK |
| G7 | Long-safe time arithmetic (post-2038 safe) |

---

## 3. Proposed Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       TimeSeries (interface)                    │
│  add(Sample) · statistics() · percentile(p) · histogram(n)      │
└─────────────────────────────────────────────────────────────────┘
                              △
              ┌───────────────┴───────────────┐
              │                               │
┌─────────────────────────┐     ┌─────────────────────────┐
│ BucketedTimeSeries      │     │ SketchTimeSeries        │
│ (evolved IntTimeSeries) │     │ (t-digest / HDR based)  │
└─────────────────────────┘     └─────────────────────────┘
              │                               │
              └───────────────┬───────────────┘
                              ▼
                  ┌───────────────────────┐
                  │  ValueSketch (iface)  │
                  │  · DistinctBitSet     │
                  │  · RuntimeHistogram   │
                  └───────────────────────┘
```

### 3.1 `Sample` — value object for one measurement

```java
public record Sample(long startMs, long endMs, int value, boolean failed) {}
```

Replaces the current 4-arg `addValue`. Makes call sites self-documenting and
leaves room for additional dimensions later (e.g. agent id) without changing the
method signature again.

### 3.2 `TimeSeries` — public interface

```java
public interface TimeSeries
{
    void add(Sample sample);

    long firstSecond();          // throws NoSuchElementException when empty
    long lastSecond();           // throws NoSuchElementException when empty
    int  slotWidthSeconds();     // 1, 2, 4, 8, …

    Statistics statistics();     // immutable snapshot
    long       percentile(double p);          // 0..100
    List<HistogramBucket> histogram(int buckets);

    int size();                  // number of buckets
}
```

### 3.3 `Statistics` — immutable, total

```java
public record Statistics(
    long count,
    long errorCount,
    long sum,
    long sumOfSquares,
    int  minValue,
    int  maxValue)
{
    public double mean()               { ... }
    public double standardDeviation()  { ... }
}
```

No more mutable POJO. No more `Integer.MIN_VALUE` / `Integer.MAX_VALUE` sentinels —
`OptionalInt` or explicit `isEmpty()` makes "no data" unambiguous.

### 3.4 `BucketedTimeSeries` — the direct evolution

Roughly the current `IntTimeSeries`, but with three behavioural changes:

1. **Time stored as `long`** — fixes the 2038 overflow.
2. **Split shift and condense policies** into explicit strategy objects.
3. **No side-effecting merge** — merging returns a new entry.

```java
public final class BucketedTimeSeries implements TimeSeries
{
    public static Builder builder() { ... }

    public static final class Builder
    {
        Builder bucketCount(int n);          // default 4096
        Builder precision(Precision p);      // CONSERVATIVE / BALANCED / AGGRESSIVE
        Builder clock(LongSupplier nanoTime);// for tests
        BucketedTimeSeries build();
    }
}
```

### 3.5 `ValueSketch` — abstract the "how do we approximate values?" question

Today, three different approximations live side by side:

* 128-bit scaled bitset (per entry)
* `RuntimeHistogram(8)` (whole series)
* the min/max/sum/count scalars (per entry)

Make each one a `ValueSketch` implementation with a shared interface:

```java
public interface ValueSketch
{
    void   add(int value);
    double percentile(double p);
    long   countIn(int from, int to);
    ValueSketch merge(ValueSketch other);   // returns new, no mutation
    int    estimatedBytes();
}
```

Existing implementations become:

| class                       | role                                             |
|-----------------------------|--------------------------------------------------|
| `ScaledBitSetSketch`        | today's `IntTimeSeriesEntry` distinct-values     |
| `BucketedHistogramSketch`   | today's `RuntimeHistogram`                       |
| `TDigestSketch` *(new)*     | optionally better tail accuracy                  |

The `BucketedTimeSeries` is then constructed with **two** sketches:

* one per-bucket (memory-cheap, e.g. `ScaledBitSetSketch`)
* one global (accuracy-focused, e.g. `BucketedHistogramSketch` or `TDigestSketch`)

### 3.6 `Precision` enum

```java
public enum Precision
{
    CONSERVATIVE (/* histogram bucket = 1, bitset scale capped at 4 */),
    BALANCED     (/* histogram bucket = 8, bitset scale capped at 8 */),
    AGGRESSIVE   (/* histogram bucket = 32, bitset scale capped at 16 */);
}
```

Replaces the magic numbers `8` and `128` sprinkled through the code.

---

## 4. Bug Fixes Bundled Into the Redesign

These are the bugs and sharp edges the new design **must** fix (all documented in
`architecture.md` §5):

1. **`histogram(n)` with `min == max`** → return one bucket with the full count.
2. **`histogram(n)` bucket counts exceeding total** → make the histogram
   compute exact per-bucket ranges in *value* space, not in bucket space.
3. **Negative input values** → clamp consistently across min, max, total,
   distinct-values. Decide at API level: either reject (`IllegalArgumentException`)
   or accept and clamp. Document the choice.
4. **Year-2038** → all time arithmetic in `long`.
5. **`merge` mutating its argument** → return a new instance.
6. **Leaking internal array via `getValues()`** → return an unmodifiable view or
   an `Iterable<TimeSlot>` snapshot.
7. **`concurrentCount` underreported after condense** → track it in a separate
   bucketed counter that is *not* scaled by `this.scale` when iterating the
   "extra seconds" of a request.
8. **`Statistics` sentinels** → no more `Integer.MIN_VALUE` / `MAX_VALUE` leaks.

---

## 5. Migration Path

The current classes stay untouched (this proposal is not an implementation plan
for a big-bang rewrite). A suggested order:

1. **Phase 1** — Introduce `TimeSeries` interface and adapt `IntTimeSeries` to
   implement it (`class IntTimeSeries implements TimeSeries`). No behavioural
   change. Existing callers keep working.
2. **Phase 2** — Add `BucketedTimeSeries` builder-based API next to it. New code
   uses the new API.
3. **Phase 3** — Fix the eight bugs listed in §4 in `BucketedTimeSeries` only.
   Add regression tests (the failing-then-passing kind).
4. **Phase 4** — Deprecate `IntTimeSeries`, `IntTimeSeriesEntry`,
   `RuntimeHistogram` for new code. Keep them as legacy adapters.
5. **Phase 5** — Once nothing references the legacy types, remove them.

Each phase is independently shippable.

---

## 6. Test Strategy for the Redesign

The existing 95 tests from `architecture.md` §6 must keep passing unchanged
(Phase 1 + 2). On top:

* **Property-based tests** (jqwik) for the invariants:
  * `statistics().count()` equals number of `add` calls
  * `min ≤ percentile(p) ≤ max` for all `p ∈ [0,100]`
  * `histogram(n)` buckets sum to `count`
* **Golden-master tests** against the legacy implementation for a fixed seed of
  random samples to ensure Phase 1 doesn't change behaviour.
* **Bug-regression tests** — one test per item in §4.
* **Microbenchmarks** (JMH, mirroring `FastHashMapBenchmark`) to ensure the new
  API stays within 10 % of the old one's `addValue` cost.

---

## 7. Out of Scope

* Distributed / multi-agent aggregation
* Persistence (the current in-memory design is kept)
* A UI for histograms
* Changes to the `org.jugsaxony.demo2` package (`FastHashMap`, `LRUClockMap`)

---

## 8. Open Questions for the Team

1. **Negative values:** reject, clamp, or track separately? — current code is
   inconsistent (see §4, item 3).
2. **Concurrency definition:** should `concurrentCount` reflect overlapping
   *requests* (current behaviour) or *active users* (more useful for capacity
   planning)?
3. **Percentile accuracy target:** is `RuntimeHistogram(8)` good enough, or do
   we need tail-focussed sketches (t-digest) for P99+?
4. **Time resolution floor:** is sub-second resolution ever needed? Today the
   floor is 1 s.
5. **Removal timeline:** when is the team comfortable deleting the legacy
   classes (Phase 5)?

---

## 9. Summary

| What                | Today                                | Proposed                                |
|---------------------|--------------------------------------|-----------------------------------------|
| Time storage        | `int` seconds (2038 overflow)        | `long` seconds                          |
| Entry merge         | mutates argument                     | returns new instance                    |
| Internal state leak | `getValues()` returns live array     | unmodifiable snapshot                   |
| Accuracy            | three independent implicit scales    | explicit `Precision` enum               |
| Histogram edge case | `min == max` → IAE                   | well-defined single-bucket result       |
| Bucket counting     | can overcount edge buckets           | exact value-space ranges                |
| Extensibility       | copy-paste to add a new statistic    | `ValueSketch` plugin interface          |
| API                 | 4-arg `addValue`                     | `add(Sample)`                           |

This proposal is intentionally **not** accompanied by code. It is meant to be
discussed, refined, and — once the team agrees on the open questions in §8 —
turned into an implementation plan.
