# Technical Proposal: Modernized & High-Performance Implementation of XLT Data Structures

**Document Status:** Proposal / Architectural Recommendation  
**Notice:** DO NOT IMPLEMENT THIS PROPOSAL. This document serves strictly as an architectural and algorithmic proposal for future iterations.

---

## 1. Motivation & Problem Statement

The current implementation of `IntTimeSeries`, `IntTimeSeriesEntry`, `RuntimeHistogram`, and `BitUtil` demonstrates robust foundational concepts (fixed memory ceilings, bit compression, online percentile approximation). However, several areas can be significantly modernized and improved for modern Java (Java 21+), contemporary CPU microarchitectures, and high-throughput telemetry pipelines:

1. **Object Overhead & Cache Locality**:
   - `IntTimeSeries` allocates an array of `IntTimeSeriesEntry` objects (`IntTimeSeriesEntry[] values`). For a default size of 4096, this results in 4096 separate objects on the heap, introducing pointer indirection, header overhead (16-24 bytes per object), and cache misses during iteration/merging.
2. **Year 2038 Vulnerability & Timestamp Precision**:
   - Timestamps are truncated into 32-bit signed integer seconds (`(int)(startTime * 0.001)`), which overflows in January 2038.
   - Millisecond and microsecond timing resolutions cannot be preserved directly inside slot representations.
3. **Sparse Value Memory Waste in `RuntimeHistogram`**:
   - A contiguous array `int[] countPerBucket` is used. When outlier values arrive (e.g., a single request taking 60,000ms alongside standard 50ms requests), the histogram allocates a huge array spanning from 50ms to 60,000ms filled mostly with zeros.
4. **Historical Bit Twiddling vs. Modern JVM Intrinsics**:
   - `BitUtil` includes manual bit twiddling routines (e.g., `ntzTable`, manual `nlz`, manual 64-bit `pop` routines) that were necessary in early Java versions. Modern JVMs (HotSpot / GraalVM) compile methods like `Long.bitCount`, `Long.numberOfTrailingZeros`, and `Long.numberOfLeadingZeros` directly into native x86/ARM CPU instructions (`POPCNT`, `TZCNT`, `LZCNT`, `CLZ`).
5. **Lossy Scaling in Concurrency & Statistics**:
   - When condensing slots, `concurrentCount` takes `Math.max(c1, c2)`, which is an approximation that can distort true overlapping concurrency across condensed intervals.
6. **Concurrency & Mutability**:
   - The data structures are mutable and not thread-safe, requiring external synchronization or single-threaded confinement.

---

## 2. Proposed Architecture & Enhancements

```
+-------------------------------------------------------------------------------+
|                       Modernized Telemetry Architecture                       |
|                                                                               |
|  +-------------------------------------------------------------------------+  |
|  |                   Columnar / Off-Heap TimeSeries                        |  |
|  |  - Struct-of-Arrays (SoA) layout / MemorySegment (Java 21 FFM API)      |  |
|  |  - Flat primitive arrays: long[] sums, int[] counts, int[] mins/maxs    |  |
|  |  - Long-based 64-bit Epoch Second Indexing (Beyond Year 2038)           |  |
|  +-------------------------------------------------------------------------+  |
|                                     |                                         |
|                 +-------------------+-------------------+                     |
|                 v                                       v                     |
|  +-----------------------------+         +---------------------------------+  |
|  |  HdrHistogram / DDSketch    |         |  HyperLogLog / RoaringBitmap    |  |
|  |  - Fixed relative error %   |         |  - True cardinality estimation  |  |
|  |  - High dynamic range       |         |  - Compact sparse-to-dense      |  |
|  |  - Sparse chunk allocation  |         |  - Zero pointer overhead        |  |
|  +-----------------------------+         +---------------------------------+  |
+-------------------------------------------------------------------------------+
```

---

## 3. Detailed Proposed Design

### 3.1 Struct-of-Arrays (SoA) / Columnar Storage for `IntTimeSeries`

#### Current Design (Array of References - AoS):
```java
// 4096 reference objects pointing to scattered heap allocations
IntTimeSeriesEntry[] values = new IntTimeSeriesEntry[4096];
```

#### Proposed Design (Columnar Primitive Storage - SoA):
Instead of allocating 4096 objects, manage all fields in flat parallel primitive arrays or a single contiguous `MemorySegment` / direct `ByteBuffer`:
```java
public class ColumnarTimeSeries {
    private final int capacity; // e.g. 4096
    private final long[] totalValues;
    private final int[] counts;
    private final int[] errorCounts;
    private final int[] concurrentCounts;
    private final int[] minimums;
    private final int[] maximums;
    private final long[] distinctBitsLow;
    private final long[] distinctBitsHigh;
    private final byte[] distinctScales;
    
    private long firstSecond; // 64-bit epoch second
    private int scale;
}
```
**Benefits:**
- Eliminates 4096 object headers (saving ~96 KB per instance).
- Sequential memory layout enables CPU hardware prefetching and vectorization (SIMD) during aggregations (`sum`, `mean`, `max`).
- Condensing and shifting can be performed using highly optimized vectorized memory operations (`System.arraycopy`).

---

### 3.2 Replacement of `RuntimeHistogram` with Exponential / Log-Linear Histogram (DDSketch or HdrHistogram Algorithm)

#### Current Limitation:
Linear bucketing (`value >> precision`) is vulnerable to memory explosion on extreme values:
- Range `[0 .. 100_000]` with precision 8 requires ~12,500 ints.
- Relative error is non-uniform (coarse for small numbers, over-precise for huge numbers).

#### Proposed Solution:
Adopt an **exponentially bucketed histogram** (log-linear / DDSketch model):
- Bucket index computed via logarithmic mapping: $\text{index} = \lfloor \log_{\gamma}(\text{value}) \rfloor$ where $\gamma = \frac{1 + \alpha}{1 - \alpha}$ guarantees a fixed relative error $\alpha$ (e.g., 1% error across the entire range from $1\text{ms}$ to $1\text{ hour}$).
- **Sparse Chunk Allocation**: Buckets are allocated in small chunks on demand, using an open-addressing primitive map for sparse distributions.
- **Constant Memory Footprint**: Bounded to $< 2\text{ KB}$ while covering 6+ orders of magnitude.

---

### 3.3 Modernizing Bit Manipulation with JVM Intrinsics & Vector API

#### Current State:
`BitUtil` and `BitCompression` use custom software loops and lookup tables:
- `BitUtil.ntz` uses a 256-byte table.
- `BitUtil.pop` uses a 64-bit SWAR formula.

#### Proposed Improvement:
1. Replace all scalar bit twiddling with direct `java.lang.Long` and `java.lang.Integer` intrinsic calls:
   - `Long.bitCount(v)` $\rightarrow$ compiles to native `POPCNT`
   - `Long.numberOfTrailingZeros(v)` $\rightarrow$ compiles to native `TZCNT`
   - `Long.numberOfLeadingZeros(v)` $\rightarrow$ compiles to native `LZCNT`
2. **Java Vector API (JEP 448)** for Array Popcounts & Bit Operations:
   - For bulk array operations (`pop_intersect`, `pop_union`), utilize `LongVector.fromArray(...)` to compute SIMD bitwise logic and vector popcounts (`v.lanewise(VectorOperators.BIT_COUNT)`) across 256-bit (AVX2) or 512-bit (AVX-512 / ARM Neon/SVE) registers.

---

### 3.4 Upgrading Distinct Value Tracking to HyperLogLog / 64-Bit Quantized Sets

#### Current Design:
A 128-bit bitset with dynamic right-shifting bit compression. While fast, it loses precision quickly for values exceeding $128 \times 2^{\text{scale}}$ and cannot estimate cardinalities beyond 128 distinct scaled values accurately.

#### Proposed Design Options:
- **Option A: 64-bit / 128-bit HyperLogLog Register**:
  - Uses standard HLL with 16 or 32 4-bit registers packed into 64/128 bits.
  - Allows cardinality estimation from 1 to $10^6$ distinct values with a standard error of $\approx 10-15\%$.
- **Option B: Compact Dynamic Roaring Bitmap**:
  - Automatically alternates between 16-bit packed arrays (for sparse sets) and bitsets (for dense sets).

---

### 3.5 64-Bit Timestamps and Millisecond/Microsecond Resolution

#### Current Design:
`firstSecond` is stored as an integer, and `startTime` is multiplied by `0.001`.

#### Proposed Design:
- Use `long firstUnit` representing either seconds or milliseconds.
- Define a configurable `TemporalResolution` enum (`SECONDS`, `MILLISECONDS`, `MICROSECONDS`).
- Eliminate float multiplication `startTime * 0.001` in favor of integer division `startTime / 1000` (modern JIT compiles division by constants into reciprocal multiplication and shifts).

---

### 3.6 Lock-Free / Concurrent Accumulator Support

For multi-threaded ingestion pipelines:
- Implement `ConcurrentIntTimeSeries` using atomic stripes or thread-local buffers combined with a background compaction worker.
- Alternatively, design `IntTimeSeries` to support immutable snapshotting and thread-safe merging (`ts1.merge(ts2)`) across worker threads.

---

## 4. Comparison Matrix: Current vs. Proposed

| Dimension | Current Implementation | Proposed Modern Implementation |
|---|---|---|
| **Memory Layout** | Array of Objects (`IntTimeSeriesEntry[]`) | Columnar Struct-of-Arrays / Direct `MemorySegment` |
| **Object Allocations** | 4,096+ objects per time series | 1 single object / buffer |
| **Histogram Strategy** | Linear dense array (`RuntimeHistogram`) | Log-linear DDSketch / HdrHistogram (Bounded Relative Error) |
| **Outlier Handling** | High memory allocation for large ranges | Sparse chunk allocation, zero overhead for outliers |
| **Bit Twiddling** | Software routines & lookup tables | JVM Intrinsics (`POPCNT`) & Java Vector API |
| **Time Horizon** | Year 2038 limit (`int` epoch second) | 64-bit `long` (Sub-millisecond ready, indefinite horizon) |
| **Distinct Estimator** | 128-bit shifting bitset | Packed HyperLogLog (128-bit) or Sparse-Dense Roaring |
| **Cache Friendliness** | Low (Pointer chasing across heap) | High (Contiguous primitive arrays, SIMD-friendly) |

---

## 5. Migration & Non-Functional Guidelines

If this proposal is adopted in future revisions:
1. **Zero-Implementation Rule for Current Turn**: Retain existing code as-is; this document serves exclusively as the design specification.
2. **Backward Compatibility**: Any new API should implement a common interface (e.g. `TimeSeriesReporter`) to maintain interoperability with existing report generators.
3. **Microbenchmarking (JMH)**: Benchmark throughput, cache-miss rates (via `perf-asm`), and GC allocation pressure under synthetic load test streams before finalizing the buffer layouts.
