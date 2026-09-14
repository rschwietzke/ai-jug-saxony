# Proposal for Improved Implementation: XLT-DATA

## Current Limitations

1. **32-bit Time Stamps**: The use of `int` for seconds leads to the Year 2038 overflow.
2. **Manual Array Management**: `RuntimeHistogram` and `IntTimeSeries` use manual `System.arraycopy` and growth logic which is error-prone and complex.
3. **Precision Loss**: `IntTimeSeriesEntry` uses a custom bit-compression scheme to approximate distinct values, which is difficult to maintain and reason about.
4. **Linear Summation**: `calculateOverviewData` in `IntTimeSeries` performs a linear scan over the array, which could be optimized to incremental updates.

## Proposed Improvements

### 1. Modernize Time Handling
- Replace `int` timestamp management with `java.time.Instant` or `long` (representing milliseconds/seconds) to eliminate the 2038 problem.

### 2. Leverage Standard Libraries
- **Histograms**: Replace `RuntimeHistogram` with a proven library like `HdrHistogram`. `HdrHistogram` provides constant-time updates and configurable precision with much better theoretical guarantees and a cleaner API.
- **Bit Manipulation**: Use `Long.bitCount()`, `Long.numberOfTrailingZeros()`, and `Long.numberOfLeadingZeros()` instead of `BitUtil`. These are intrinsic in modern JVMs and map directly to CPU instructions (POPCNT, TZCNT, LZCNT), making the custom `BitUtil` obsolete.

### 3. Refactor Data Storage
- Replace the `IntTimeSeriesEntry` manual bit-set approximation with a **T-Digest** or **KLL Sketch**. These provide mathematically sound approximations for quantiles and distinct values with a fixed memory bound.

### 4. Incremental Statistics
- Maintain a running `Statistics` object within `IntTimeSeries`. Update the global sum, count, and min/max incrementally during `addValue` calls rather than recalculating them via array stream in `getStatistics()`.

## Predicted Impact
- **Maintainability**: Significant reduction in "magic" bit-twiddling code.
- **Correctness**: Removal of the 2038 clock bug.
- **Performance**: Better CPU utilization via JVM intrinsics.
- **Accuracy**: Better statistical guarantees for percentiles and distinct value approximations.
