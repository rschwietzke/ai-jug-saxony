# Architectural Documentation: com.xceptance.xlt.report.util

## Overview
This package provides high-performance utilities for collecting and analyzing time-series data and distribution statistics, specifically optimized for memory efficiency and speed.

## Core Components

### 1. Time Series Management (`IntTimeSeries`, `IntTimeSeriesEntry`)
- **Purpose**: Maintains statistics (min, max, count, sum) for values generated over time.
- **Key mechanism**: 
    - **Fixed-size circular-like buffer**: Uses a fixed array of `IntTimeSeriesEntry`.
    - **Self-Managing Scale**: If the time range exceeds the array size, the system "condenses" data by merging adjacent slots (doubling the time represented by each slot), ensuring it can handle arbitrary durations while maintaining a constant memory footprint.
    - **Precision**: Time is handled in seconds.

### 2. Distribution Analysis (`RuntimeHistogram`)
- **Purpose**: Calculates percentiles/quantiles without storing every individual value.
- **Key mechanism**: 
    - **Bucket-based counting**: Instead of storing a list of values, it counts occurrences in buckets.
    - **Configurable Precision**: Supports loss of precision (via power-of-2 shifting) to save memory.
    - **Dynamic Growth**: Grows the bucket array in both directions as values outside the current range are added.

### 3. Bit Manipulation (`BitUtil`, `BitCompression`)
- **Purpose**: Provides low-level "bit-twiddling" for fast operations.
- **Key routines**:
    - **Popcount**: Counting set bits (cardinality) using Hacker's Delight algorithms.
    - **NZT/NLZ**: Counting trailing and leading zeros.
    - **Bit Compression**: Techniques to combine and shift bits to produce approximate sets of distinct values in `IntTimeSeriesEntry`.

## Key Conceptual Knowledge
- **Power of Two**: Many components (precision, scale, array sizes) rely on power-of-two alignment to use bit-shifting (`>>`, `<<`) instead of division/multiplication for performance.
- **Approximation over Exactness**: For memory efficiency, the system uses histograms and bit-set approximations (in entries) rather than raw lists of data.
- **The "Year 2038" Problem**: The implementation acknowledges the use of 32-bit signed integers for timestamps (seconds since epoch), which will overflow in 2038.
