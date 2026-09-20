# XLT Report Architecture & Data Structures Documentation

## 1. Executive Summary & Purpose

The `com.xceptance` packages within `demo8` implement high-performance, memory-efficient data structures and algorithms originally created for load testing reporting engines (such as Xceptance LoadTest - XLT). During large-scale load tests, millions or billions of data points (response times, errors, throughput metrics) are generated across long execution durations.

Storing every measurement individually is prohibitively expensive in memory and processing time. The package addresses this through:
1. **Streaming Fixed-Memory Time Series Summarization (`IntTimeSeries`)**: Dynamically condensing time intervals and maintaining fixed-size memory while expanding across arbitrary durations.
2. **Compact Distinct Value Tracking with Lossy Bitset Compression (`IntTimeSeriesEntry` & `BitCompression`)**: Approximate distinct value sets using a 128-bit bitset with bit-level scale reductions.
3. **Dynamic Sparse Histogram Estimation (`RuntimeHistogram`)**: Dynamic range expansion with count-based bucketing to calculate percentiles and medians with bounded precision loss.
4. **Optimized Bit-Level Manipulation (`BitUtil`)**: Branchless and vector-like carry-save adder (CSA) bit population counting algorithms (Harley-Seal popcount).

---

## 2. Component Architecture & Relationships

```
+-----------------------------------------------------------------------------------+
|                                com.xceptance                                      |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                             IntTimeSeries                                   |  |
|  |  - Circular/Slot-based Array: IntTimeSeriesEntry[] (size: Power-of-Two)     |  |
|  |  - Auto-scaling window (scale: power-of-two seconds per slot)                |  |
|  |  - Global reservoir histogram: RuntimeHistogram (precision 8)              |  |
|  |  - Incremental metrics: count, totalValue, errorCount, sumOfSquares         |  |
|  +-----------------------------------------------------------------------------+  |
|              |                                              |                     |
|              v (per-slot aggregation)                       v (percentiles)       |
|  +-------------------------------------+      +--------------------------------+  |
|  |        IntTimeSeriesEntry           |      |        RuntimeHistogram        |  |
|  | - count, totalValue, errorCount     |      | - Precision bucket shifting    |  |
|  | - min, max, concurrentCount         |      | - Dynamic left/right array grow|  |
|  | - distinctValuesLow (64 bits)       |      | - Empirical percentile & median|  |
|  | - distinctValuesHigh (64 bits)      |      +--------------------------------+  |
|  | - distinctValuesScale (2^scale)     |                     |                    |
|  +-------------------------------------+                     |                    |
|              |                                               |                    |
|              v (bit twiddling)                               v (power of 2)       |
|  +-------------------------------------+      +--------------------------------+  |
|  |           BitCompression            |      |            BitUtil             |  |
|  | - combineAdjacentBits (OR pairs)    |      | - pop / pop_array (Harley-Seal)|  |
|  | - compressAndShiftOddBits (pack)    |      | - pop_intersect, pop_union     |  |
|  |                                     |      | - ntz, nlz, nextHighestPowerOf2|  |
|  +-------------------------------------+      +--------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 3. Deep Dive into Core Data Structures

### 3.1 `IntTimeSeries`
- **Goal**: Maintain second-level and multi-second aggregated load test time series across variable and unknown durations without dynamic memory allocations per sample.
- **Backing Array**: Backed by `IntTimeSeriesEntry[] values` where array length is forced to the next highest power of two (default `3600` rounded up to `4096`).
- **Time Window Mechanics**:
  - `firstSecond`: The epoch second corresponding to index `0`.
  - `scale`: Resolution factor ($2^{\text{scale}-1}$ seconds per slot). Starts at scale 1 (1 second per slot).
  - **Right Expansion & Condensing (`condense`)**: When a timestamp exceeds `firstSecond + size * slotWidth`, the entire array is condensed by half: adjacent slots $i$ and $i+1$ are merged into slot $i/2$, the second half of the array is reset, and `scale` increments by 1 (slot width doubles).
  - **Left Expansion (`shiftRight`)**: When a timestamp arrives before `firstSecond`, existing entries are shifted right by the required slot delta. If the shift would overflow the right boundary, a `condense` is triggered prior to shifting.
- **Concurrency Tracking**: Spans between `startTime` and `endTime` increment `concurrentCount` across all covered second slots.
- **Statistics Derived**:
  - Exact count, sum, min, max, error count, mean.
  - Standard deviation calculated via the sum of squares formula: $\sigma = \sqrt{\frac{\sum x^2}{N} - \mu^2}$.
  - Percentiles computed via the internal `RuntimeHistogram`.

### 3.2 `IntTimeSeriesEntry`
- **Goal**: Maintain slot statistics (count, sum, min, max, errorCount, concurrency) plus an approximate representation of distinct values in only **128 bits** of memory without allocating `Set<Integer>` objects.
- **Distinct Value Bitset**:
  - `distinctValuesLow` (long, 64 bits) and `distinctValuesHigh` (long, 64 bits) represent a 128-bit bitset.
  - `distinctValuesScale`: Current scale factor ($2^{\text{distinctValuesScale}}$).
  - When a value $v \ge 128 \times 2^{\text{scale}}$ is added:
    1. Adjacent bit pairs $(2k, 2k+1)$ are combined using `BitCompression.combineAdjacentBits` followed by `BitCompression.compressAndShiftOddBits`.
    2. Low and High 32-bit compressed representations are merged into `distinctValuesLow`, and `distinctValuesHigh` is cleared.
    3. `distinctValuesScale` is incremented.
    4. Repeats until the new scaled value fits within 128 bits.
- **Merging**: `merge(IntTimeSeriesEntry other)` aligns the lower-scale entry to the higher scale before bitwise ORing distinct value sets and aggregating counters.

### 3.3 `BitCompression`
- **Algorithm**:
  - `combineAdjacentBits(x)`: Computes `x | (x << 1)`. If either bit $2k$ or $2k+1$ was set, bit $2k+1$ is guaranteed to be set.
  - `compressAndShiftOddBits(x)`: Gathers the odd bits $(1, 3, 5, \dots, 63)$ and packs them contiguously into bits $(0, 1, 2, \dots, 31)$ using parallel bit-masking and shift stages (SIMD within a register / SWAR):
    - Mask with `0x5555555555555555L`
    - Shift and mask with `0x3333333333333333L`
    - Shift and mask with `0x0F0F0F0F0F0F0F0FL`
    - Shift and mask with `0x00FF00FF00FF00FFL`
    - Shift and mask with `0x0000FFFF0000FFFFL`
    - Shift and mask with `0x00000000FFFFFFFFL`

### 3.4 `RuntimeHistogram`
- **Goal**: Efficient approximate percentile calculation for runtime values without retaining all samples.
- **Storage**: An array `int[] countPerBucket` where index represents `value >> precision`.
- **Dynamic Growing**:
  - Dynamically grows left (`firstIndexValue` shifts down) or right (`lastIndexValue` shifts up) using `System.arraycopy` / `Arrays.copyOf`.
  - Zero memory allocation for values falling within known bounds.
- **Percentile Calculation**:
  - Computes exact rank index $np = N \times (p / 100.0)$.
  - Iterates accumulated bucket counts to retrieve the values matching $np$ and $np+1$, computing the linear midpoint for even counts.

### 3.5 `BitUtil`
- **Origin**: Ported and adapted from Apache Solr / Lucene bit twiddling routines.
- **Harley-Seal Vector Popcount (`pop_array`, `pop_intersect`, `pop_union`, `pop_andnot`, `pop_xor`)**:
  - Employs Carry-Save Adders (CSA) to process blocks of 8 long words (512 bits) simultaneously using bitwise operations (`ones`, `twos`, `fours`, `eights`), minimizing total `pop` instructions.
- **Trailing & Leading Zero Counting**: Table-lookup optimized `ntz` and `nlz` implementations.
- **Power of Two Operations**: `isPowerOfTwo` and bit-smearing `nextHighestPowerOfTwo`.

---

## 4. Key Performance Characteristics & Trade-offs

| Feature | Design Decision | Benefit | Trade-off / Limitation |
|---|---|---|---|
| **Fixed memory time series** | Fixed power-of-two array with periodic condensing | Bounded memory usage regardless of test run duration | Loss of temporal resolution as time scale doubles |
| **Bitset distinct values** | 128-bit bitset with bit compression | Zero object allocation, compact footprint (16 bytes) | Precision loss on large value ranges ($2^{\text{scale}}$ quantization) |
| **Histogram bucketing** | Dynamic `int[]` array shifted by `precision` | Fast rank lookups, no tree balancing overhead | Sparse or outlier values cause array growth / gap padding |
| **Bit count parallelism** | Harley-Seal CSA popcount | High throughput popcounting on arrays | Modern JVMs with intrinsic `Long.bitCount` / AVX-512 often match or surpass this without JNI |
| **Concurrency tracking** | Linear second step loop | Accurately accounts for long running requests | Loops across span: large durations with small scales execute multiple iterations |

---

## 5. What One Must Know When Working with this Codebase

1. **Epoch Milliseconds vs Seconds**: `addValue` expects epoch milliseconds and converts using `startTime * 0.001` (multiplying is used instead of division for float optimization).
2. **Year 2038 Constraint**: Timestamps are stored as 32-bit signed ints (`startSecond`), which will overflow in 2038 if not upgraded to 64-bit longs.
3. **Power-of-Two Masking & Alignment**: Array sizes and scaling factors rely heavily on power-of-two arithmetic (shifts and masks).
4. **Non-Thread-Safe**: All structures are designed for single-threaded processing per transaction stream or map partition.
5. **No Negative Values in TimeSeries**: Negative sample values are clamped to `0` in `IntTimeSeriesEntry`.
