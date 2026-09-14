# Proposal: A Reworked `IntTimeSeries` Family

Status: **proposal — nothing in here is implemented.** The code in
`src/main/java/com/xceptance/xlt/report/util/` is unchanged; this document describes what a
rework should look like and why. Background and a description of the code as it stands today
are in [architecture.md](architecture.md).

Audience: whoever picks up the `rework` package next.

---

## 1. Why rework at all

The current implementation does the hard part right. It aggregates an unbounded stream of
measurements in a fixed 272 KB per metric, degrades resolution instead of allocating, and packs
one time slot into 64 flat bytes. That core idea is sound and this proposal keeps all of it.

What it does not do right is survive its own edge cases. Writing the test suite that accompanies
this document turned up three ways to reach a crash or a silent data loss with ordinary input:

| | Trigger | Result |
|---|---|---|
| **A** | a forward time gap of `size²` seconds — 68 minutes on a 64-slot series | every sample recorded so far disappears, silently |
| **B** | an out-of-order sample more than `2 × size + 1` seconds before the window | `ArrayIndexOutOfBoundsException` out of `addValue` |
| **C** | a request longer than the window, or any long request after a backward shift | `ArrayIndexOutOfBoundsException` out of `addValue` |

None of these is exotic. Load test data arrives from several agents and is *not* sorted, which
is exactly what triggers B and C. A load test with a warm-up gap, a paused agent or a clock
skew between machines triggers A. And when A hits, nothing is logged and no exception is
thrown — the report is simply wrong.

On top of that sit six correctness defects that make the numbers in a report disagree with each
other (§3), and the whole thing has no way to merge two series, which is what a distributed
report generator actually needs.

The proposal below is therefore not a rewrite. It is: **fix the three failure modes
structurally, make the numbers consistent, and expose the operation that is missing.**

---

## 2. What must stay true

Constraints the rework inherits. These are not negotiable.

| # | Constraint | Consequence |
|---|---|---|
| C1 | Memory per metric is bounded and independent of the sample count | no growing collections, no stored samples |
| C2 | One pass, no buffering, no sorting of the input | every operation is incremental |
| C3 | Samples arrive out of order, from several sources | the window must grow in both directions |
| C4 | A run can be arbitrarily long | the time resolution must degrade, not the memory |
| C5 | Single-threaded per series | no synchronization; concurrency is handled by having one series per worker and merging |
| C6 | The hot path is `addValue`, called 10⁷–10⁹ times | no allocation, no boxing, no `Math.pow`, few branches |
| C7 | Values are runtimes in milliseconds, mostly small, with a long tail | the value ladder must handle 0..2³¹ but optimize for 0..few thousand |

---

## 3. Defect catalogue

Severity: **S1** = wrong or lost data with no signal, **S2** = crash, **S3** = inconsistent
numbers, **S4** = API hazard or performance.

Every defect below has a test that pins today's behaviour. Those tests are the acceptance
criteria: a fix flips them from "documents the defect" to "asserts the fix".

### D1 — `condense` destroys all data on a large forward gap · S1

`IntTimeSeries.condense` merges neighbouring slots in a `do/while`, halving the live length `l`
each round. Once `l` reaches 1 the merge loop `for (i = 0; i < l - 1; i += 2)` has nothing to
merge, and the fill loop that follows overwrites slot 0 with a fresh entry. Every further round
does the same to an already empty array.

Threshold, measured: the first sample is lost at a gap of exactly **`size²` seconds** — 4 s for
a 2-slot series, 68 minutes for a 64-slot series, 194 days for the 4096-slot default.

```java
IntTimeSeries series = new IntTimeSeries(4);
series.addValue(0L, 7, false);
series.addValue(16_000L, 9, false);
series.getCount();       // 1  - the sample at second 0 is gone
series.getTotalValue();  // 9
```

Tests: `IntTimeSeriesTest` T36, T37, T38.

**Fix**: compute the number of halvings up front and merge with a stride in a single pass
(§4.3). A round that would halve a single slot cannot occur, because there are no rounds — only
one pass with the final width.

### D2 — `shiftRight` throws on a large backward gap · S2

`shiftRight` calls `condense` when the shift would push data out of the window, but `condense`
tests its loop condition against the **old** `firstSecond`, which has not been updated yet. The
condition is therefore false immediately and exactly one halving happens, however far back the
new time stamp is. The subsequent `System.arraycopy(values, 0, values, newOffset, size -
newOffset)` then gets a negative length.

Threshold, measured: **`2 × size + 2` seconds** before the window start.

```java
IntTimeSeries series = new IntTimeSeries(8);
series.addValue(100_000L, 1, false);
series.addValue(0L, 2, false);      // ArrayIndexOutOfBoundsException: arraycopy: length -1
```

Tests: T40, T41, T42.

**Fix**: one `ensureCovers(fromSecond, toSecond)` that derives the required width from the
*combined* span before touching anything (§4.2).

### D3 — a long request walks off the slot array · S2

The window overflow check sits in the `else` branch of the shift check:

```java
if (startSecond - this.firstSecond < 0)      { shiftRight(...); firstSecond = startSecond; }
else if (endSecond >= firstSecond + window)  { condense(endSecond); }
```

Whenever the shift branch is taken — which the **first** `addValue` on a fresh series always
does, because `firstSecond` is the sentinel — the end of the request is never checked. The
concurrency loop then indexes past the end of the array.

```java
new IntTimeSeries(4).addValue(0L, 5_000L, 10, false);   // ArrayIndexOutOfBoundsException: Index 4
```

Test: T52.

**Fix**: same as D2 — one entry point that covers `[startSecond, endSecond]` before any slot is
touched.

### D4 — `merge` misplaces sketch buckets above 63 · S1

The rescale in `IntTimeSeriesEntry.merge` compresses the low and the high word independently and
never folds the compressed high word into the upper half of the low word, which is what
`scaleIfNeeded` does. Buckets 64..127 therefore stay in the high word at half their index, and
are read back as `64 + (b - 64) / 2` instead of `b / 2` — a factor of about 1.6 on the reported
value.

```java
IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
entry.updateValue(100, false);
entry.merge(coarserEntry);          // any entry with a bigger scale
entry.getValues();                  // [..., 164.0, ...] - the 100 is reported as 164
```

Since `merge` is the primitive that `condense` is built from, this corrupts the value sketch of
every report that ran long enough to condense.

Tests: `IntTimeSeriesEntryTest` T33 (defect), T34 (the same rescale done correctly by
`updateValue`).

**Fix**: extract the sketch into its own type with exactly one `halve()` used by both paths
(§4.4).

### D5 — `toHistogram` throws for ordinary inputs · S2

`bucketWidth = (max - min) / bucketCount` is a `double` that becomes 0 when all values are equal
and < 1 when more buckets than distinct values are requested. The bucket end is then computed as
`start - 1`, and `RuntimeHistogram.getCountForValue` rejects the inverted range.

```java
series.addValue(1_000L, 100, false);
series.addValue(2_000L, 100, false);
series.toHistogram(4);        // IllegalArgumentException: Start value must be less than ...
```

A flat series is not an edge case — a stub, a cached endpoint or a hard-coded delay produces
one. Tests: T43, T44.

### D6 — the histogram double counts · S3

`toHistogram` asks the underlying `RuntimeHistogram` for value ranges that are finer than its
own bucket precision (8). Any bucket that straddles a boundary is reported in full on both
sides:

```java
// 100 samples, values 0..99
series.toHistogram(5);   // bucket counts sum to 132
```

Tests: T45. Related: T46 — the first bucket always starts at 0 regardless of the minimum, which
may be intentional for the chart but is undocumented.

### D7 — the concurrency loop uses the wrong step · S3

```java
for (int i = startSecond + 1; i <= endSecond; i = i + this.scale)
```

`scale` is the exponent plus one; the slot width is `1 << (scale - 1)`. They agree for widths 1
and 2 and diverge from width 4 on, so long requests bump some slots several times and the
reported concurrency is too high. Test: T51.

### D8 — the standard deviation contradicts the mean · S3

`sumOfSquares` is accumulated from the **raw** value while the slots clamp negatives to zero,
and it is never adjusted when D1 throws data away. After a data loss the series reports a
standard deviation for samples it no longer counts:

```java
// after the D1 example above
series.getCount();               // 1
series.getMean();                // 9.0
series.getStandardDeviation();   // 7.0  - a single value cannot have a spread
```

Test: T39. `sqrt(E[x²] - E[x]²)` is also the numerically unstable form.

### D9 — `merge` mutates its argument · S4

`coarse.merge(fine)` rescales `fine` in place. Two entries built from the same input stop being
`equals` after one of them has been used as a merge argument. Tests: T27, T30.

### D10 — `getValues()` hands out the live array · S4

Callers can add samples through the returned entries; those samples reach the slot statistics
but never the histogram, so percentiles and counts drift apart. Test: T47.

### D11 — `equals` without `hashCode` · S4

Entries in a `HashSet` or as `HashMap` keys behave by identity. Test: T31.

### D12 — no constructor validation · S4

`nextHighestPowerOfTwo` returns 0 for 0 and for negative input and overflows to
`Integer.MIN_VALUE` above 2³⁰; both results go straight into `new IntTimeSeriesEntry[size]`. A
size of 0 yields a series that throws on the first value; `Integer.MAX_VALUE` yields
`NegativeArraySizeException`. Tests: T48, T49, T50.

### D13 — the histogram's memory follows the value spread · S4

`RuntimeHistogram` allocates one `int` per bucket between the smallest and the largest value
seen, so the memory is `(max - min) / precision * 4` bytes and does not depend on the number of
samples at all. Two values, 0 and 1 000 000, cost 488 KB at the precision 8 that `IntTimeSeries`
uses, and 3.8 MB at the default precision of 1. One request that hits a 20-minute timeout
therefore costs 586 KB in that metric's histogram - times the number of metrics in the report.
Test: `RuntimeHistogramTest` T29.

### D14–D18 — smaller items · S4

| # | Item | Test |
|---|---|---|
| D14 | `getPercentile(NaN)` passes the range check and returns one bucket below the minimum | `RuntimeHistogramTest` T28 |
| D15 | `getPercentile(100)` returns a bucket floor, not the maximum | T25 |
| D16 | `new RuntimeHistogram(0)` reports precision 1 but computes with a shift of 32 (masked back to 0 by the JVM) | T24 |
| D17 | seconds are `int`; the sentinel `2_147_385_000` is 2038-01-17, the comment says 2037-12-31 | — |
| D18 | negatives are clamped for the slot but not for the histogram or the sum of squares, so percentiles and min/max come from different populations | `IntTimeSeriesTest` T12 |

Also worth knowing, though not defects: `getCount()`, `getTotalValue()`, `getErrorCount()`,
`getMean()` and `getStandardDeviation()` each rescan all 4096 slots; `IntTimeSeriesEntry
.getValues()` builds an `ArrayList<Double>` and boxes every bucket; `sumOfSquares` uses
`Math.pow(value, 2)`.

---

## 4. Proposed design

### 4.1 Shape

Five types instead of the current three, splitting the two halving ladders apart so that each
one has exactly one implementation:

```
IntTimeSeries          the window, the running aggregates, the API
  └── SlotArray        addressing: window <-> slot index, growing, condensing
        └── Slot       today's IntTimeSeriesEntry, minus the sketch mechanics
              └── ValueSketch   the 128 bucket sketch and its halving  (new)
  └── ValueHistogram   today's RuntimeHistogram, with a bounded bucket window
```

`ValueSketch` and the fixed `ValueHistogram` are the two pieces that carry all the tricky bit
arithmetic; isolating them is what removes D4 by construction rather than by inspection.

### 4.2 One entry point for window management

Every path that can change the window goes through a single method, so the shift case and the
overflow case cannot diverge again (D2, D3):

```java
// sketch, not implemented
private void ensureCovers(final long fromSecond, final long toSecond)
{
    if (isEmpty()) { open(fromSecond, toSecond); return; }

    final long lo   = Math.min(fromSecond, windowStart);
    final long hi   = Math.max(toSecond,   lastUsedSecond);
    final long span = hi - lo + 1;

    // 1. how wide does a slot have to be so that the whole span fits into 'size' slots?
    final int neededExp = ceilLog2(ceilDiv(span, size));
    if (neededExp > scaleExp) { condenseTo(neededExp); }   // one pass, §4.3

    // 2. now the span fits; grow the window to the left and/or to the right
    if (lo < windowStart) { growLeft(lo); }
    // growing right needs nothing but a wider lastUsedSecond, the ring already covers it
}
```

`addValue` becomes: clamp the value once, convert both time stamps once, call `ensureCovers`,
book the sample, walk the concurrency range with `slotWidth` as the step (D7), update the
running aggregates and the histogram.

### 4.3 Condensing in one pass

Instead of repeating a halving round until the target fits, compute the number of halvings `r`
first and do a single strided merge. This is the fix for D1: there is no round in which the
live length shrinks to one, because there is only one pass.

```
r = 3, size = 8, slot width w -> 8w

old   [ 0 ][ 1 ][ 2 ][ 3 ][ 4 ][ 5 ][ 6 ][ 7 ]
       \___________________/  \______________/
new   [        0        ][        1        ][ empty ] …
```

Two details that must be got right:

* **Slot boundaries are aligned to absolute epoch seconds**, not to the first sample:
  `windowStart` is always a multiple of `slotWidth`. That makes a merge deterministic and
  independent of the order in which data arrived, which is what makes §4.6 (merging two series)
  possible at all.
* Condensing already touches every slot, so it also **normalizes the ring** (`head = 0`). That
  keeps the in-place strided merge trivially correct.

Cost: O(size) per condense, and a series condenses at most `log2(runLength / size)` times ever —
17 times for a default-sized series that grows from one second to a decade.

### 4.4 `ValueSketch` — one halving, two callers

```java
// sketch, not implemented
final class ValueSketch
{
    private long low;      // buckets 0..63
    private long high;     // buckets 64..127
    private int  scaleExp; // bucket width is 1 << scaleExp

    void add(int value);                       // halves until the value fits
    void mergeFrom(ValueSketch other);         // never modifies 'other' (D9)
    int  copyValuesTo(int[] target);           // no boxing, returns the number written
    int  scaleExp();

    private void halve();                      // the ONLY place that folds high into low (D4)
}
```

`mergeFrom` rescales a **local copy** of the other side's two words — copying two `long`s is
free, and it makes merging a read-only operation on the argument.

### 4.5 Running aggregates instead of rescans

`count`, `sum`, `errorCount`, `min`, `max` and Welford's `mean`/`M2` live in `IntTimeSeries` and
are updated in `addValue`. Getters become O(1), the standard deviation becomes numerically
stable, and — because D1 is gone — they can no longer contradict the slots (D8).

```java
// Welford, population variance; sketch, not implemented
count++;
final double delta = value - mean;
mean += delta / count;
m2   += delta * (value - mean);
```

Keep a debug-only cross-check that the scanned totals equal the running totals; that is a cheap
invariant test and it is what the current code silently violates.

**Clamping is decided once**, at the top of `addValue`, and the clamped value is what the slot,
the sketch, the histogram and the aggregates all see (D18). Count the clamped samples in a
`negativeValueCount` so a caller can see that its input was bad instead of guessing.

### 4.6 The operation that is missing: merging two series

A distributed report generator aggregates per-agent results. Today there is no way to combine
two `IntTimeSeries`, which forces every producer through one shared instance.

With absolute slot alignment (§4.3) this becomes well defined:

```java
// sketch, not implemented
public IntTimeSeries merge(IntTimeSeries other);   // brings both to the coarser width, then adds slot by slot
```

Semantics follow the slot merge: counts, sums and error counts add up, min/max combine,
concurrency takes the maximum, sketches OR together, histograms add their bucket counts.

### 4.7 A bounded histogram

`ValueHistogram` keeps the counting design — it is the right one — but stops letting the value
spread decide the memory (D13). The mechanism is the one the package already uses twice:
**halve on demand**.

```
maxBuckets = 65_536                     -> 256 KB worst case, per metric
on add:  index = (value - base) >> precisionExp
         while (index out of the bucket window)  { precisionExp++; foldNeighbouringBuckets(); }
```

Folding is the same neighbour-merge as everywhere else, so the resolution degrades gracefully
instead of the allocator exploding. The precision then becomes a *result*, not a parameter, and
`getPrecision()` reports what the histogram actually achieved.

Alternative considered: HdrHistogram-style log-linear bucketing, which gives a constant
*relative* error instead of a constant absolute one. It is the better statistical answer for
runtimes and it is a bigger change; §8 keeps it as an option.

Further fixes in this type:

* Track exact `min` and `max` alongside the buckets, so `getPercentile(0)` and
  `getPercentile(100)` return real values (D15).
* Reject `NaN` explicitly (D14) and require `precision >= 1` (D16).
* Document `getCountForValue` as bucket-granular, and add a `long[] countsFor(int[] boundaries)`
  that snaps the boundaries to bucket edges so the counts **partition** the samples. That is
  what `toHistogram` needs.

### 4.8 `toHistogram` with a real contract

```java
// sketch, not implemented
public List<HistogramBucket> toHistogram(int bucketCount);   // bucketCount >= 1, else IllegalArgumentException
```

Guarantees to state and to test:

1. Exactly `bucketCount` buckets, or one bucket when `min == max` (D5).
2. Half-open intervals `[start, end)`, with the last one closed, so nothing overlaps (D6).
3. The bucket counts sum to `getCount()` — exactly (D6).
4. Boundaries are snapped to the histogram's bucket edges, so the counts are exact rather than
   approximately assigned.
5. Whether the first bucket starts at `min` or at 0 is an explicit parameter, not an accident.
   The current code starts at 0; that is a chart decision and the report team owns it.

### 4.9 Time base and API hygiene

| Change | Fixes |
|---|---|
| `long` seconds everywhere, `Math.floorDiv(millis, 1000)` for the conversion | D17, and the truncation-towards-zero error for negative time stamps: `(int)(-1500 * 0.001)` is -1 where -2 is correct |
| replace the `2_147_385_000` sentinel with an explicit empty state | removes a magic number and a wrong comment |
| `getSlotWidth()` becomes the only width accessor; `getScale()` is deprecated | removes the exponent-plus-one trap behind D7 |
| `slots()` returns an unmodifiable view, plus a `forEachSlot(SlotVisitor)` for the hot path | D10 |
| `hashCode` next to `equals`, or drop `equals` and give the tests a comparator | D11 |
| constructor validates `1 <= size <= 2^30` and `precision >= 1` | D12 |
| `Statistics` becomes a record | immutability |
| class javadoc states "not thread-safe, one instance per producer, merge at the end" | C5 |
| `value * value` instead of `Math.pow`, `int[]` instead of `ArrayList<Double>` | C6 |

---

## 5. What the caller sees change

A rework that fixes D1–D8 **changes numbers in existing reports**. That is the point, but it
has to be communicated, and each item needs a decision before implementation:

| Change | Effect on a report |
|---|---|
| D1, D2, D3 fixed | runs that previously lost data now show more samples; some runs that failed now succeed |
| D4 fixed | the value sketch of condensed series moves down to where it belongs (between a third and a half lower on the affected buckets) |
| D6 fixed | histogram bars get smaller and now sum to the sample count |
| D7 fixed | concurrency for long requests goes down |
| D8 fixed | standard deviation changes wherever data had been lost |
| D15 fixed | `p100` becomes the true maximum instead of a bucket floor |
| §4.7 | the histogram precision becomes adaptive; percentiles of wide-spread metrics get coarser, but the process stops running out of memory |

Recommendation: land the rework behind a switch and run both implementations over a corpus of
real timer files, comparing report output. Differences that are not explained by the table above
are bugs in the rework.

---

## 6. Phasing

| Phase | Content | Risk | Note |
|---|---|---|---|
| **P0** | D1, D2, D3 via §4.2 + §4.3 | low, contained in `IntTimeSeries` | stops crashes and data loss; do this first even if nothing else follows |
| **P1** | D4 via §4.4, D7, D8, D18 | medium | changes report numbers, needs the corpus comparison from §5 |
| **P2** | D5, D6 via §4.8, D14–D16 | medium | histogram API and its contract |
| **P3** | §4.5 running aggregates, §4.6 series merge, D9–D12 | low | performance and API hygiene |
| **P4** | §4.7 bounded histogram | medium | the memory guarantee; can be deferred but not forgotten |

P0 alone is worth doing on its own: three defects, one new method, and the tests for them
already exist.

---

## 7. Test strategy

The suite in `src/test/java/com/xceptance/…` is the safety net and the specification. Rules for
the rework:

1. **Do not delete the `DEFECT` tests — invert them.** Each one names the input that breaks
   today; after the fix the same input must produce the right answer. That turns the current
   test suite into the regression suite for the rework.
2. **Add the invariants as properties**, checked over randomized workloads:
   * no sample is ever lost: `count` equals the number of `addValue` calls, always;
   * `sum`, `min`, `max`, `errorCount` scanned over the slots equal the running aggregates;
   * the window `[firstSecond, lastSecond + slotWidth)` contains every time stamp that was
     added;
   * the histogram bucket counts sum to the sample count;
   * `addValue` never throws for any `(long, long, int, boolean)` input.
3. **Differential test against a naive reference**: a second implementation that just keeps
   every sample in a list and computes everything exactly. For sample counts small enough to fit
   in memory, every number the reworked series reports must be within its documented error
   bound of the reference. This is the test that would have caught D1, D4 and D6 on day one.

---

## 8. Alternatives considered

| Alternative | Why not (now) |
|---|---|
| **Use HdrHistogram** instead of `RuntimeHistogram` | It solves D13, D15 and the precision contract in one step and is battle-tested. It also adds a dependency, changes every percentile in every report, and does not address the time series at all — which is where the S1/S2 defects are. Worth revisiting after P0–P3. |
| **t-digest / KLL sketches** for percentiles | Better accuracy per byte in the tail, but a much bigger conceptual jump and harder to explain in a report footnote. |
| **Keep the array shifting, just fix the arithmetic** | Possible, but the shift is O(size) per out-of-order sample and out-of-order samples are the normal case. The ring costs one mask and removes the whole class of arraycopy bugs. |
| **Grow the slot array instead of condensing** | Violates C1. The fixed footprint is the feature. |
| **Store the sketch as a `byte[]` of bucket counts** | 128 bytes per slot instead of 16, i.e. ~1.1 MB per metric instead of 272 KB, in exchange for frequency information the histogram already provides. |

---

## 9. Open questions

1. **Does any report consume `getScale()` directly?** If yes, the deprecation in §4.9 needs a
   migration path.
2. **Is "the first histogram bucket starts at 0" intentional?** It looks like a chart
   requirement, not a bug, but it is nowhere documented (D6/T46).
3. **What is the real distribution of `size`?** Everything in §3 scales with it; if production
   only ever uses `DEFAULT_SIZE`, D1 needs a 194-day gap and P0 could be scheduled behind P1.
4. **Should a clamped negative value count as a sample at all?** Today it does, with value 0. An
   explicit `negativeValueCount` (§4.5) makes the decision visible either way.
5. **Do we keep `BitUtil`?** Only `nextHighestPowerOfTwo` has a caller, and
   `Integer.highestOneBit`/`numberOfLeadingZeros` are JVM intrinsics that do the same job
   without the overflow surprise. Dropping the file removes 839 lines of untouched imported
   code from the module.
