# XLT Metrics Architecture & Technical Reference

This document provides a comprehensive architectural breakdown of the performance metrics and time series aggregation components located in `com.xceptance`. It covers design principles, data structures, low-level bit-twiddling algorithms, mathematical invariants, and discovered implementation edge cases.

---

## 1. System Overview & Purpose

The `com.xceptance` reporting engine is designed for **high-throughput, memory-bounded performance testing analysis** (originally developed for Xceptance LoadTest - XLT). During large-scale load tests, millions of transaction samples are generated per minute. Storing each transaction sample individually causes catastrophic GC overhead and out-of-memory errors.

To solve this, the engine employs:
1. **Bounded Memory Time Series (`IntTimeSeries`)**: Fixed-size circular/expanding temporal buffers that self-condense when time spans exceed capacity.
2. **Compact Metric Accumulators (`IntTimeSeriesEntry`)**: Fixed-size struct-like entries combining statistical aggregations with a 128-bit approximate distinct value bitmap.
3. **Register-Level Bit Manipulation (`BitUtil`, `BitCompression`)**: SWAR (SIMD-Within-A-Register) algorithms and Harley-Seal Carry-Save Adder (CSA) routines to downsample bitsets and count population with zero heap allocation.
4. **Elastic Power-of-Two Histograms (`RuntimeHistogram`)**: Dynamic range histogram with bit-shift bucket precision for quantile and percentile computation.

```
                              Raw Transaction Event
                    (startTime, endTime, runtimeValue, failed)
                                       │
                                       ▼
                             ┌───────────────────┐
                             │   IntTimeSeries   │
                             └─────────┬─────────┘
                                       │
            ┌──────────────────────────┴──────────────────────────┐
            ▼                                                     ▼
┌─────────────────────────┐                             ┌───────────────────┐
│   IntTimeSeriesEntry    │                             │ RuntimeHistogram  │
│  (Per-Second Accumulator)│                             │ (Global Quantiles)│
└───────────┬─────────────┘                             └───────────────────┘
            │
            ▼
┌─────────────────────────┐
│ BitCompression / BitUtil│
│(128-bit Distinct Bitmap)│
└─────────────────────────┘
```

---

## 2. Component Architecture & Deep Dives

### 2.1 Bit-Level Optimization Utilities (`BitUtil`)

`BitUtil` is a specialized bit-manipulation library adapted from Apache Lucene/Solr, engineered for high-performance set operations on `long[]` bit vectors.

#### 2.1.1 Carry-Save Adder (CSA) Vectorized Population Count
Naive bit counting loops over `Long.bitCount()` on every word. For large bit vectors, `BitUtil.pop_array` employs the **Harley-Seal 8-way Carry-Save Adder** algorithm:
- Operates on 8 64-bit words at a time ($W_0 \dots W_7$).
- Uses half-adders and full-adders constructed with bitwise logic (`XOR`, `AND`, `OR`):
  $$\text{Sum} = A \oplus B \oplus C$$
  $$\text{Carry} = (A \wedge B) \vee (B \wedge C) \vee (C \wedge A)$$
- Aggregates bit counts in parallel across registers into intermediate bit-weight accumulators ($ones, twos, fours, eights$), only executing hardware population count at step boundaries.
- **Complexity**: Amortized $\approx 1.5$ bitwise operations per 64-bit word, achieving 2x to 3x throughput compared to word-by-word `Long.bitCount()`.

#### 2.1.2 Set Operations with Inline Population Count
- `pop_intersect(A, B, offset, len)`: Counts bits in $A \cap B$ without allocating an intermediate array.
- `pop_union(A, B, offset, len)`: Counts bits in $A \cup B$.
- `pop_andnot(A, B, offset, len)`: Counts bits in $A \setminus B$.
- `pop_xor(A, B, offset, len)`: Counts bits in $A \oplus B$.

#### 2.1.3 De Bruijn & Lookup Table Bit Scanning
- `ntzTable` and `nlzTable`: Precomputed 256-byte lookup tables for fast byte-level trailing zero (`ntz`) and leading zero (`nlz`) resolution.
- Bit-branching trees for 32-bit and 64-bit integers isolate the non-zero byte, followed by a single array lookup.
- Power-of-two utilities:
  - `isPowerOfTwo(v)`: Uses `(v & (v - 1)) == 0`. *Note: Evaluates to `true` for `0` and `Integer.MIN_VALUE` (0x80000000).*
  - `nextHighestPowerOfTwo(v)`: Bitwise smearing (`x |= x >> 1; x |= x >> 2; ...`) to round up to the nearest $2^k$.

---

### 2.2 Register-Level Bit Compression (`BitCompression`)

`BitCompression` provides SWAR (SIMD-Within-A-Register) algorithms for Morton un-shuffling and bit reduction.

#### 2.2.1 `combineAdjacentBits(long value)`
```java
public static long combineAdjacentBits(final long value) {
    return value | (value << 1);
}
```
For every adjacent bit pair $(b_{2k}, b_{2k+1})$, this operation computes:
$$b'_{2k+1} = b_{2k} \vee b_{2k+1}$$
This performs pairwise OR reduction, storing the result in all odd positions.

#### 2.2.2 `compressAndShiftOddBits(long value)`
Extracts the 32 odd bits (positions $1, 3, 5, \dots, 63$) and packs them contiguously into the lower 32 bits (positions $0 \dots 31$):
```
Stage 0 (Mask 0x5555...): v = (value >>> 1) & 0x5555555555555555L  (shift odd bits to even)
Stage 1 (Mask 0x3333...): v = (v | (v >>> 1)) & 0x3333333333333333L
Stage 2 (Mask 0x0F0F...): v = (v | (v >>> 2)) & 0x0F0F0F0F0F0F0F0FL
Stage 3 (Mask 0x00FF...): v = (v | (v >>> 4)) & 0x00FF00FF00FF00FFL
Stage 4 (Mask 0x0000...): v = (v | (v >>> 8)) & 0x0000FFFF0000FFFFL
Stage 5 (Mask 0x0000...): v = (v | (v >>> 16)) & 0x00000000FFFFFFFFL
```
This is a log-step butterfly network running in $\mathcal{O}(\log_2(W))$ register operations, completely branchless and memory-barrier free.

---

### 2.3 Elastic Power-of-Two Histogram (`RuntimeHistogram`)

`RuntimeHistogram` tracks value distributions with a configurable bit-shift precision ($2^{\text{precision}}$).

#### 2.3.1 Dynamic Two-Way Growth
The histogram does not allocate an array covering the full integer range. Instead, it tracks `firstIndexValue` and `lastIndexValue`:
- Values map to buckets via `index = value >> precision`.
- **Right Growth (`index > lastIndexValue`)**: Reallocates using `Arrays.copyOf(countPerBucket, newSize)`.
- **Left Growth (`index < firstIndexValue`)**: Allocates a new array and shifts existing elements right by `delta = firstIndexValue - index` via `System.arraycopy`.
- **Negative Values**: Handled naturally through arithmetic right shift (`>>`), maintaining signed ordering.

#### 2.3.2 Percentiles and Quantiles
Percentile lookup calculates rank $np = N \times (p / 100)$:
- If $np$ is not an integer: Rank is $\lceil np \rceil$.
- If $np$ is an exact integer: Averages the value at rank $np$ and rank $np + 1$ (standard empirical quantile definition with linear interpolation).
- Bucket values are reconstructed via `index << precision`.

---

### 2.4 Time Series Entry & Distinct Bitmap (`IntTimeSeriesEntry`)

Each second (or aggregated interval) in `IntTimeSeries` is represented by an `IntTimeSeriesEntry`.

```
┌────────────────────────────────────────────────────────┐
│                  IntTimeSeriesEntry                    │
├────────────────────────────────────────────────────────┤
│ long totalValue         │ int count                    │
│ int concurrentCount     │ int errorCount               │
│ int minimum             │ int maximum                  │
├─────────────────────────┴──────────────────────────────┤
│ long distinctValuesLow  (bits 0..63)                   │
│ long distinctValuesHigh (bits 64..127)                 │
│ int  distinctValuesScale (power of two scale exponent) │
└────────────────────────────────────────────────────────┘
```

#### 2.4.1 Adaptive Distinct Bitmap Scaling
To track distinct response times without unbounded memory, each entry contains a **128-bit bitmap** (`distinctValuesLow` and `distinctValuesHigh`):
1. **Initial Resolution ($\text{scale} = 0$)**: Bits $0 \dots 127$ directly represent exact integer runtimes $0 \dots 127$ ms.
2. **Scaling Trigger ($v \ge 128$)**:
   When a value $v \ge 128$ arrives, the resolution is halved recursively:
   $$\text{bucket} = v \gg \text{distinctValuesScale}$$
   While $\text{bucket} \ge 128$:
   - Pairwise OR combine and compress `distinctValuesLow`: 64 bits $\to$ 32 bits.
   - Pairwise OR combine and compress `distinctValuesHigh`: 64 bits $\to$ 32 bits.
   - New `distinctValuesLow = compressedLow | (compressedHigh << 32)`.
   - New `distinctValuesHigh = 0`.
   - Increment `distinctValuesScale`.
3. **Information Recovery**:
   When reading distinct values via `getValues()`, each set bit $i \in [0, 127]$ is expanded back to $(1 \ll \text{distinctValuesScale}) \times i$.

---

### 2.5 Time Series Sliding Window (`IntTimeSeries`)

`IntTimeSeries` maintains second-by-second performance metrics over a dynamic time horizon.

#### 2.5.1 Temporal Indexing
- Millisecond timestamps are converted to seconds:
  $$\text{second} = \lfloor \text{timestamp} \times 0.001 \rfloor$$
- Sized to powers of two (default 3600 rounds up to 4096) to enable potential fast masking.
- Array position calculation:
  $$\text{pos} = (\text{startSecond} - \text{firstSecond}) \gg (\text{scale} - 1)$$

#### 2.5.2 Concurrency Tracking Over Intervals
Transactions have a start time and end time. `IntTimeSeries` tracks concurrency across the entire lifespan:
- Slot at `startSecond` receives `updateValue(value, failed)` (increments count, adds total runtime, increments concurrency).
- All subsequent seconds from `startSecond + 1` up to `endSecond` (stepped by `scale`) receive `updateConcurrency()`.

#### 2.5.3 Condensation (Forward Expansion)
When a measurement arrives at $\text{endSecond} \ge \text{firstSecond} + \text{capacityInSeconds}$:
- Adjacent entries are merged pairwise: $\text{values}[i] = \text{values}[2i].\text{merge}(\text{values}[2i + 1])$.
- Remaining slots are reinitialized with empty entries.
- `scale` increments by 1, doubling the time span represented by each slot (1s $\to$ 2s $\to$ 4s $\to \dots$).

#### 2.5.4 Right Shift (Out-of-Order Past Samples)
When a measurement arrives with $\text{startSecond} < \text{firstSecond}$:
- Existing data is shifted to the right by $\text{offset} = \text{adjustToScale}(\text{firstSecond}) - \text{adjustToScale}(\text{startSecond})$.
- If $\text{lastPosUsed} + \text{offset} \ge \text{size}$, a condensation step is triggered first.
- Preceding slots are populated with empty entries, and `firstSecond` is reset to `startSecond`.

---

## 3. Architectural Deficiencies & Discovered Edge Cases

During code inspection and unit test implementation, several critical limitations and defects were uncovered in the existing implementation:

### 3.1 Defect in `IntTimeSeriesEntry.merge` Bitmap Upscaling
In `IntTimeSeriesEntry.scaleIfNeeded`:
```java
var l = BitCompression.compressAndShiftOddBits(BitCompression.combineAdjacentBits(distinctValuesLow));
var h = BitCompression.compressAndShiftOddBits(BitCompression.combineAdjacentBits(distinctValuesHigh));
this.distinctValuesLow = l | (h << 32);
this.distinctValuesHigh = 0;
```
However, in `IntTimeSeriesEntry.merge(IntTimeSeriesEntry)`:
```java
while (this.distinctValuesScale < item.distinctValuesScale) {
    this.distinctValuesLow = BitCompression.compressAndShiftOddBits(BitCompression.combineAdjacentBits(this.distinctValuesLow));
    this.distinctValuesHigh = BitCompression.compressAndShiftOddBits(BitCompression.combineAdjacentBits(this.distinctValuesHigh));
    this.distinctValuesScale++;
}
```
**Impact**: The high bits are compressed in-place in `distinctValuesHigh` without being shifted by 32 into `distinctValuesLow`, and `distinctValuesHigh` is never cleared. This leads to distinct value corruption when merging entries with different scales.

### 3.2 Defect in `IntTimeSeries.shiftRight` Condense Loop
In `IntTimeSeries.shiftRight`:
```java
if (this.lastPosUsed + offset >= this.size) {
    condense(second + expandToSeconds(this.lastPosUsed));
}
```
In `condense`:
```java
do { ... } while (second >= firstSecond + expandToSeconds(this.size));
```
**Impact**: Because `second < firstSecond`, the loop condition `second >= firstSecond + ...` is immediately false! The condensation loop only executes once regardless of how far in the past `second` is. If the shift offset still exceeds array bounds after one condensation, `size - newOffset` becomes negative, triggering `java.lang.ArrayIndexOutOfBoundsException: arraycopy: length -1 is negative`.

### 3.3 Year 2038 Timestamp Overflow & Float Multiplication Inaccuracy
`IntTimeSeries.java` converts timestamps via:
```java
final int startSecond = (int) (startTime * 0.001);
```
- Floating-point multiplication by `0.001` introduces floating-point rounding errors compared to integer division (`startTime / 1000L`).
- Casting `startTime * 0.001` to a 32-bit signed `int` overflows when unix epoch milliseconds exceed $2,147,483,647 \times 1000$ (Year 2038 problem).

### 3.4 Linear Bucket Scan in `RuntimeHistogram.getValueByCount`
`getValueByCount` scans sequentially through `countPerBucket` from index 0:
- For sparse distributions with wide ranges, this performs an $\mathcal{O}(B)$ linear search on every quantile computation.
- Prefix sums or Fenwick trees (Binary Indexed Trees) would reduce quantile queries to $\mathcal{O}(\log B)$.

---

## 4. Test Strategy & Invariants

The test suite in `src/test/java/com/xceptance/` enforces the following invariants:

| Class | Invariants & Test Coverage |
|---|---|
| `BitUtilTest` | CSA Harley-Seal reduction equivalence to `Long.bitCount`, bitwise set popcounts, trailing/leading zero tables, power-of-two edge cases. |
| `BitCompressionTest` | Pairwise OR invariance, 64-to-32 bit compression accuracy across all bit positions, random vector property testing. |
| `RuntimeHistogramTest` | Two-way dynamic array growth, signed negative indexing, quantile interpolation, range query bounds clamping. |
| `IntTimeSeriesEntryTest` | Statistical moments, 128-bit scaling transitions, concurrency increment, same/cross-scale merging. |
| `IntTimeSeriesTest` | Time window progression, multi-second concurrency tracking, forward condensation, backward shifting. |
