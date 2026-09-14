# XLT Data Architecture & Engineering Knowledge Base

## 1. Executive Overview & Domain Context

In high-throughput load and performance testing environments (such as Xceptance Load Testing - XLT), test engines execute hundreds of thousands of concurrent virtual users, generating millions of measurement records per second (response times, network metrics, transaction durations, error events).

Storing individual measurements in unbounded memory or disk during test runs is infeasible. The `com.xceptance` analytics subsystem provides specialized, high-performance, memory-bounded, streaming data structures designed for:
1. **$O(1)$ Streaming Ingestion**: Real-time aggregation of measurement values without object allocation on the hot path.
2. **Dynamic Temporal Condensation**: Fixed-capacity time series that dynamically double their time-slice resolution ($1\text{s} \to 2\text{s} \to 4\text{s} \dots$) as the test duration extends, keeping memory strictly bounded ($O(1)$ total memory regardless of test duration).
3. **Statistical Moment & Percentile Estimation**: Accurate estimation of minimum, maximum, mean, population standard deviation, and arbitrary quantiles ($P_{50}, P_{90}, P_{95}, P_{99}, P_{99.9}$).
4. **Distinct Value Approximation**: Scaled 128-bit bitmap representation capturing distinct response time distributions with minimal memory overhead.
5. **Bit-Twiddling & Vectorized Bit Operations**: Carry-Save Adder popcounting, bit compression, and bit-smearing algorithms for hardware-accelerated bit operations.

---

## 2. Component-by-Component Architectural Breakdown

```
com.xceptance.xlt.report.util
├── lucene
│   └── BitUtil.java             # Vectorized bit twiddling, CSA popcount, trailing/leading zeros
├── misc
│   └── BitCompression.java      # Parallel 64-bit to 32-bit bitmap folding and odd-bit extraction
├── RuntimeHistogram.java        # Memory-bounded dynamic frequency histogram for quantiles
└── rework
    ├── IntTimeSeriesEntry.java  # Per-slot metrics accumulator with 128-bit scaled distinct bitmap
    └── IntTimeSeries.java       # Fixed-size expanding/condensing time-series buffer
```

---

### 2.1 `BitUtil` (Vectorized Bit Manipulation & Math)

`BitUtil` is a low-level bit manipulation library adapted from Apache Lucene/Solr, containing optimized implementations of binary algorithms originally documented in *Hacker's Delight* (Henry S. Warren, Jr.).

#### Key Algorithms & Routines:
1. **Single-Word Popcount (`pop(long x)`)**:
   - Computes the 64-bit Hamming weight (number of set bits) using a parallel adder tree in 12 arithmetic/bitwise operations.
   - Masks: `0x5555...` (2-bit sum), `0x3333...` (4-bit sum), `0x0F0F...` (8-bit sum), followed by byte shifts.

2. **Harley-Seal Vector Popcount (`pop_array`)**:
   - Implements Robert Harley and David Seal's Carry-Save Adder (CSA) algorithm.
   - Processes `long[]` arrays in unrolled blocks of 8 words (512 bits) at a time.
   - Uses 3-bit CSA logic gates ($u = a \oplus b, v = c, h = (a \land b) \lor (u \land v), l = u \oplus v$) to sum bits into accumulator words (`ones`, `twos`, `fours`, `eights`), deferring expensive popcount operations until after full blocks are reduced.
   - Trailing words ($N < 8$) are handled via binary-search unrolling ($4 \to 2 \to 1$).

3. **Set Operations with Fused Popcount (`pop_intersect`, `pop_union`, `pop_andnot`, `pop_xor`)**:
   - Performs bitwise boolean set algebra ($A \cap B$, $A \cup B$, $A \setminus B$, $A \oplus B$) fused directly inside the CSA loop without allocating intermediate arrays or modifying operands.

4. **Trailing & Leading Zero Counts (`ntz`, `ntz2`, `ntz3`, `nlz`)**:
   - Uses precomputed 256-byte lookup tables (`ntzTable`, `nlzTable`) combined with binary search partitioning across 32-bit words and 8-bit bytes.

5. **Power-of-Two Arithmetic**:
   - `isPowerOfTwo(v)`: Evaluates `(v & (v - 1)) == 0`. (Note: returns `true` for 0).
   - `nextHighestPowerOfTwo(v)`: Computes the least power of two $\ge v$ via bit-smearing shifts ($1, 2, 4, 8, 16, 32$).

---

### 2.2 `BitCompression` (Parallel Bitmap Folding & Gathering)

`BitCompression` provides parallel bit manipulation routines designed specifically for scaling 128-bit distinct value sets.

#### Algorithms:
1. **`combineAdjacentBits(long value)`**:
   - Computes `value | (value << 1)`.
   - For every adjacent pair of bits $(2k, 2k+1)$, the logical OR $(b_{2k} \lor b_{2k+1})$ is placed at the odd bit position $2k+1$.

2. **`compressAndShiftOddBits(long value)`**:
   - Extracts all odd-positioned bits ($1, 3, 5, \dots, 63$) and packs (compresses) them into contiguous positions ($0, 1, 2, \dots, 31$) in the lower 32 bits of a 64-bit word.
   - Implemented via a 5-stage parallel bit-gather (unshuffling) network:
     $$\text{Mask with } 0\text{x}5555555555555555\text{L}$$
     $$\text{Stage 1: } (v \mid (v \gg 1)) \ \& \ 0\text{x}3333333333333333\text{L} \quad (\text{2-bit pack})$$
     $$\text{Stage 2: } (v \mid (v \gg 2)) \ \& \ 0\text{x}0\text{F}0\text{F}0\text{F}0\text{F}0\text{F}0\text{F}0\text{F}0\text{FL} \quad (\text{4-bit pack})$$
     $$\text{Stage 3: } (v \mid (v \gg 4)) \ \& \ 0\text{x}00\text{FF}00\text{FF}00\text{FF}00\text{FFL} \quad (\text{8-bit pack})$$
     $$\text{Stage 4: } (v \mid (v \gg 8)) \ \& \ 0\text{x}0000\text{FFFF}0000\text{FFFFL} \quad (\text{16-bit pack})$$
     $$\text{Stage 5: } (v \mid (v \gg 16)) \ \& \ 0\text{x}00000000\text{FFFFFFFFL} \quad (\text{32-bit pack})$$

---

### 2.3 `RuntimeHistogram` (Dynamic Frequency Histogram)

`RuntimeHistogram` computes exact and approximate percentiles and quantiles without storing individual raw sample points.

```
                  countPerBucket Array
             [ 0 | 1 | 4 | 12 | 8 | 2 | 0 | 1 ]
               ^                              ^
        firstIndexValue                 lastIndexValue
  (e.g., 10 -> val: 10<<p)        (e.g., 17 -> val: 17<<p)
```

#### Key Architecture:
- **Binning & Precision**:
  - Values are projected into buckets via bit-shift: $\text{index} = \text{value} \gg \text{precision}$.
  - Precision is always a power of two ($2^k$). E.g., $\text{precision}=8 \implies \text{shift}=3$, so values in $[0, 7]$ map to bucket 0, $[8, 15]$ to bucket 1.
- **Bilateral Dynamic Range Growth**:
  - Left Growth (`index < firstIndexValue`): Reallocates array with delta, copies old array forward with `System.arraycopy`, and resets `firstIndexValue`.
  - Right Growth (`index > lastIndexValue`): Reallocates array using `Arrays.copyOf`, appending new buckets.
- **Quantile & Percentile Algorithm**:
  - Valid range: $p \in [0.0, 100.0]$.
  - $p = 0.0 \implies \text{firstIndexValue} \ll \text{precision}$ (approximate minimum).
  - $p = 100.0 \implies \text{lastIndexValue} \ll \text{precision}$ (approximate maximum).
  - For $0 < p < 100$, computes target sample rank $np = \text{valueCount} \times (p / 100.0)$:
    - If $np$ is an exact integer: computes the arithmetic mean of the two adjacent values at ranks $np$ and $np+1$: $(v_1 + v_2) / 2.0$.
    - If $np$ has a fractional part: retrieves the value at rank $\lceil np \rceil$.

---

### 2.4 `IntTimeSeriesEntry` (Per-Slot Metrics Aggregator)

`IntTimeSeriesEntry` aggregates all measurements falling into a single time slot (e.g. 1 second).

#### Internal State Layout:
- `long totalValue`: Sum of values (used to calculate average).
- `int count`: Number of measurements added.
- `int concurrentCount`: Number of concurrent operations active during this time slot.
- `int errorCount`: Number of failed operations.
- `int minimum`, `int maximum`: Extreme values observed.
- `long distinctValuesLow`, `long distinctValuesHigh`: 128-bit bitmap tracking presence of distinct values.
- `int distinctValuesScale`: Dynamic scale exponent $s$ ($2^s$ multiplier per bit).

#### Distinct Value Compression Mechanics:
1. Each bit $i \in [0, 127]$ represents value $i \times 2^s$.
2. When a value $v$ is added such that $v \gg s \ge 128$:
   - `distinctValuesLow` and `distinctValuesHigh` are folded via `BitCompression.combineAdjacentBits` and compressed via `BitCompression.compressAndShiftOddBits`.
   - The 32 compressed low bits and 32 compressed high bits are joined into `distinctValuesLow = l | (h << 32)`.
   - `distinctValuesHigh` is cleared to 0.
   - `distinctValuesScale` is incremented ($s \gets s + 1$).
   - This process repeats in a loop until $v \gg s < 128$.

---

### 2.5 `IntTimeSeries` (Streaming Condensing Time Series Buffer)

`IntTimeSeries` manages the entire timeline of measurements over time.

```
Timeline: |---- Slot 0 ----|---- Slot 1 ----|---- Slot 2 ----| ... |---- Slot N-1 ----|
Width:    |<- slotWidth  ->|<- slotWidth  ->|<- slotWidth  ->| ... |<- slotWidth   ->|
          ^                                                        ^
     firstSecond                                              lastSecond
```

#### Core Mechanisms:
1. **Fixed Array Capacity with Power-of-Two Slots**:
   - `size` is rounded up to the next power of two ($2^k$, default 4096 for 3600 initial request).
   - Pre-allocated `IntTimeSeriesEntry[]` array avoids null checks during streaming ingestion.

2. **Temporal Resolution & Scaling**:
   - Slot width: $\text{slotWidth} = 1 \ll (\text{scale} - 1)$ seconds.
   - Initial scale = 1 ($\text{slotWidth} = 1\text{s}$).

3. **Streaming Forward Condensation (`condense`)**:
   - When an incoming timestamp exceeds the current buffer range:
     $$\text{endSecond} \ge \text{firstSecond} + (\text{size} \ll (\text{scale} - 1))$$
   - The buffer condenses iteratively:
     - Merges adjacent pairs: $\text{values}[k] \gets \text{values}[2k].\text{merge}(\text{values}[2k+1])$.
     - Clears the upper half of the array.
     - Increments `scale` (doubling slot width from $1\text{s} \to 2\text{s} \to 4\text{s} \dots$).

4. **Retrospective Backward Insertion (`shiftRight`)**:
   - If an out-of-order event arrives with $\text{startSecond} < \text{firstSecond}$:
     - Calculates slot offset $\Delta = (\text{firstSecond} - \text{startSecond}) \gg (\text{scale} - 1)$.
     - Shifts existing entries to the right via `System.arraycopy`.
     - Initializes newly exposed lower slots with clean `IntTimeSeriesEntry` objects.

5. **Statistical Metrics Tracking**:
   - **Mean**: $\mu = \frac{\sum \text{totalValue}}{\sum \text{count}}$.
   - **Population Standard Deviation**: Calculated via cumulative sum of squares ($S_2 = \sum x^2$):
     $$\sigma = \sqrt{\frac{S_2}{N} - \mu^2}$$
   - **Quantiles**: Delegated to embedded `RuntimeHistogram(8)` (precision = 8).

---

## 3. Algorithmic Complexity & Performance Matrix

| Operation | Component | Time Complexity | Space Complexity | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `pop(x)` | `BitUtil` | $O(1)$ (12 ops) | $O(1)$ | Pure arithmetic & masks |
| `pop_array` | `BitUtil` | $O(N)$ | $O(1)$ | Harley-Seal CSA vectorization |
| `combineAdjacentBits` | `BitCompression` | $O(1)$ (2 ops) | $O(1)$ | Parallel bitwise OR |
| `compressAndShiftOddBits` | `BitCompression` | $O(1)$ (11 ops) | $O(1)$ | 5-stage bit-gather network |
| `addValue` | `RuntimeHistogram` | $O(1)$ amortized | $O(\text{range} \gg p)$ | Reallocates only on range expansion |
| `getPercentile` | `RuntimeHistogram` | $O(\text{buckets})$ | $O(1)$ | Scans bucket array until cumulative rank |
| `addValue` (steady state) | `IntTimeSeries` | $O(1)$ | $O(1)$ | Direct array indexing |
| `condense` | `IntTimeSeries` | $O(N)$ amortized | $O(1)$ | $N/2$ entry merges per scale step |
| `shiftRight` | `IntTimeSeries` | $O(N)$ | $O(1)$ | `System.arraycopy` shift |
| `getStandardDeviation` | `IntTimeSeries` | $O(N)$ | $O(1)$ | Sums moments across active slots |

---

## 4. Memory Model & Resource Footprint

### Memory Consumption Breakdown (Per `IntTimeSeries` instance with 4096 slots):
1. **`IntTimeSeries` Object Header & Fields**: ~64 bytes.
2. **`values` Reference Array (`IntTimeSeriesEntry[4096]`)**:
   - Array header: 16 bytes.
   - 4096 object references (Compressed OOPs: 4 bytes each): $4096 \times 4 = 16,384$ bytes.
3. **4096 `IntTimeSeriesEntry` Objects**:
   - Each `IntTimeSeriesEntry` contains:
     - Mark/Class header: 12 bytes.
     - `long totalValue`: 8 bytes.
     - `int count, concurrentCount, errorCount, maximum, minimum, distinctValuesScale`: $6 \times 4 = 24$ bytes.
     - `long distinctValuesLow, distinctValuesHigh`: $2 \times 8 = 16$ bytes.
     - Alignment padding: 4 bytes.
     - Total per entry: **64 bytes**.
   - $4096 \times 64 = 262,144$ bytes (~256 KB).
4. **`RuntimeHistogram` & `countPerBucket` array**: ~4 KB to 64 KB depending on range.

**Total In-Memory Footprint**: ~**280 KB** for a complete 1-hour/dynamic multi-day time series.

---

## 5. Critical Quirks, Edge Cases, & Known Deficiencies

1. **Y2038 Epoch Second Limitation**:
   `IntTimeSeries` converts timestamps via `(int)(startTime * 0.001)`. In January 2038, standard 32-bit signed integer epoch seconds will overflow.
2. **Quantization in `IntTimeSeries.getPercentile`**:
   `IntTimeSeries` hardcodes `RuntimeHistogram(8)` (precision = 8). All returned percentile response times are rounded/quantized to multiples of 8.
3. **Multi-Scale Condensation Bug in Legacy Code**:
   In `IntTimeSeries.condense`, when condensing across multiple scale steps in a single call, the loop boundary `l = l >> 1` eventually reduces `l` to 1. In that round, the pair-merging loop does not execute, and the subsequent zero-fill loop overwrites `values[0]` with an empty entry, losing historical data.
4. **Shift-Right Condensation Boundary Defect**:
   In `IntTimeSeries.shiftRight`, calling `condense` changes `this.scale`, but subsequent arraycopy offsets can calculate negative lengths if the shift span exceeds the newly expanded window.
5. **Distinct Values Merge Discrepancy in `IntTimeSeriesEntry.merge`**:
   `scaleIfNeeded` combines high and low bits into `distinctValuesLow = l | (h << 32)`, while `merge` scales high and low separately without folding high bits into low bits.
6. **Thread Safety Contract**:
   None of these classes are thread-safe. Concurrent additions without external synchronization cause lost updates, race conditions during array expansion, and corrupted statistics.
