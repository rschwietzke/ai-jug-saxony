/*
 * Copyright (c) 2005-2026 Xceptance Software Technologies GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xceptance.xlt.report.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RuntimeHistogram}. With a precision of 1 the histogram is
 * lossless (one bucket per distinct value), which allows validating the
 * percentile/quantile routines against a sorted-array oracle.
 */
class RuntimeHistogramTest
{
    // ----------------------------------------------------------------
    // Empty histogram
    // ----------------------------------------------------------------

    @Test
    void emptyHistogramReportsZeros()
    {
        final RuntimeHistogram h = new RuntimeHistogram();

        assertTrue(h.isEmpty());
        assertEquals(0, h.getValueCount());
        assertEquals(0, h.getNumberOfBuckets());
        assertEquals(0.0, h.getPercentile(0.0));
        assertEquals(0.0, h.getPercentile(50.0));
        assertEquals(0.0, h.getPercentile(100.0));
        assertEquals(0.0, h.getMedianValue());
        assertEquals(0L, h.getCountForValue(0, 100));
    }

    // ----------------------------------------------------------------
    // Precision normalization
    // ----------------------------------------------------------------

    @Test
    void precisionIsRoundedUpToPowerOfTwo()
    {
        assertEquals(1, new RuntimeHistogram().getPrecision());
        assertEquals(1, new RuntimeHistogram(1).getPrecision());
        assertEquals(2, new RuntimeHistogram(2).getPrecision());
        assertEquals(8, new RuntimeHistogram(8).getPrecision());
        assertEquals(8, new RuntimeHistogram(5).getPrecision(), "5 rounds up to 8");
        assertEquals(8, new RuntimeHistogram(7).getPrecision(), "7 rounds up to 8");
        assertEquals(1024, new RuntimeHistogram(1000).getPrecision());
    }

    // ----------------------------------------------------------------
    // Single value
    // ----------------------------------------------------------------

    @Test
    void singleValueIsReturnedForEveryPercentile()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(42);

        assertFalse(h.isEmpty());
        assertEquals(1, h.getValueCount());
        assertEquals(1, h.getNumberOfBuckets());
        assertEquals(42.0, h.getPercentile(0.0));
        assertEquals(42.0, h.getPercentile(50.0));
        assertEquals(42.0, h.getPercentile(100.0));
        assertEquals(42.0, h.getMedianValue());
        assertEquals(1L, h.getCountForValue(42, 42));
        assertEquals(1L, h.getCountForValue(0, 100));
        assertEquals(0L, h.getCountForValue(43, 100));
    }

    // ----------------------------------------------------------------
    // Growing left and right
    // ----------------------------------------------------------------

    @Test
    void growingLeftKeepsCountsAndBuckets()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(100);
        h.addValue(50);

        assertEquals(2, h.getValueCount());
        assertEquals(51, h.getNumberOfBuckets(), "buckets span 50..100 inclusive");
        assertEquals(1L, h.getCountForValue(50, 50));
        assertEquals(1L, h.getCountForValue(100, 100));
        assertEquals(0L, h.getCountForValue(60, 90));
        assertEquals(2L, h.getCountForValue(50, 100));
    }

    @Test
    void growingRightKeepsCountsAndBuckets()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(50);
        h.addValue(100);

        assertEquals(2, h.getValueCount());
        assertEquals(51, h.getNumberOfBuckets());
        assertEquals(1L, h.getCountForValue(50, 50));
        assertEquals(1L, h.getCountForValue(100, 100));
        assertEquals(2L, h.getCountForValue(0, 200));
    }

    @Test
    void alternatingGrowthAccumulatesAllValues()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        for (int i = 0; i <= 100; i++)
        {
            // force repeated growth in both directions
            h.addValue(i % 2 == 0 ? i : 100 - i);
        }

        assertEquals(101, h.getValueCount());
        assertEquals(101L, h.getCountForValue(0, 100));
    }

    // ----------------------------------------------------------------
    // Percentiles with distinct values (precision 1, lossless)
    // ----------------------------------------------------------------

    @Test
    void percentilesOfDistinctValues()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(10);
        h.addValue(20);
        h.addValue(30);
        h.addValue(40);

        assertEquals(10.0, h.getPercentile(0.0));
        assertEquals(40.0, h.getPercentile(100.0));
        // np = 4 * 0.5 = 2 (integer) -> mean of 2nd and 3rd order statistic
        assertEquals(25.0, h.getPercentile(50.0));
        // np = 4 * 0.25 = 1 (integer) -> mean of 1st and 2nd
        assertEquals(15.0, h.getPercentile(25.0));
        // np = 4 * 0.75 = 3 (integer) -> mean of 3rd and 4th
        assertEquals(35.0, h.getPercentile(75.0));
        assertEquals(25.0, h.getMedianValue());
        assertEquals(25.0, h.getQuantile(0.5));
    }

    @Test
    void percentileWithOddCountUsesSingleOrderStatistic()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(10);
        h.addValue(20);
        h.addValue(30);

        // np = 3 * 0.5 = 1.5 (not integer) -> ceil = 2nd order statistic = 20
        assertEquals(20.0, h.getPercentile(50.0));
        assertEquals(10.0, h.getPercentile(0.0));
        assertEquals(30.0, h.getPercentile(100.0));
    }

    // ----------------------------------------------------------------
    // Precision loss (bucket size > 1)
    // ----------------------------------------------------------------

    @Test
    void precisionEightBucketsValuesIntoGroupsOfEight()
    {
        final RuntimeHistogram h = new RuntimeHistogram(8);
        for (int v = 0; v < 8; v++)
        {
            h.addValue(v); // all land in bucket index 0
        }
        for (int v = 8; v < 16; v++)
        {
            h.addValue(v); // all land in bucket index 1
        }

        assertEquals(16, h.getValueCount());
        assertEquals(2, h.getNumberOfBuckets());
        // reconstruction is bucketIndex << 3
        assertEquals(0.0, h.getPercentile(0.0));
        assertEquals(8.0, h.getPercentile(100.0));
        // np = 16 * 0.5 = 8 -> mean of 8th (bucket 0 -> 0) and 9th (bucket 1 -> 8)
        assertEquals(4.0, h.getMedianValue());
        assertEquals(8L, h.getCountForValue(0, 7));
        assertEquals(8L, h.getCountForValue(8, 15));
        assertEquals(16L, h.getCountForValue(0, 15));
    }

    @Test
    void precisionLossTruncatesReconstructedExtremes()
    {
        final RuntimeHistogram h = new RuntimeHistogram(8);
        h.addValue(5); // bucket 0
        h.addValue(20); // bucket 2

        assertEquals(0.0, h.getPercentile(0.0), "min reconstructs to 0<<3");
        assertEquals(16.0, h.getPercentile(100.0), "max reconstructs to 2<<3");
    }

    // ----------------------------------------------------------------
    // Negative values
    // ----------------------------------------------------------------

    @Test
    void negativeValuesAreSupported()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(-10);
        h.addValue(-5);

        assertEquals(2, h.getValueCount());
        assertEquals(-10.0, h.getPercentile(0.0));
        assertEquals(-5.0, h.getPercentile(100.0));
        // np = 1 (integer) -> mean of -10 and -5
        assertEquals(-7.5, h.getMedianValue());
        assertEquals(2L, h.getCountForValue(-10, -5));
        assertEquals(1L, h.getCountForValue(-10, -10));
    }

    // ----------------------------------------------------------------
    // getCountForValue edge cases
    // ----------------------------------------------------------------

    @Test
    void getCountForValueRejectsInvertedRange()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(10);
        assertThrows(IllegalArgumentException.class, () -> h.getCountForValue(10, 5));
    }

    @Test
    void getCountForValueOutsideDataIsZero()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(10);
        h.addValue(20);

        assertEquals(0L, h.getCountForValue(0, 5), "fully below");
        assertEquals(0L, h.getCountForValue(25, 30), "fully above");
        assertEquals(2L, h.getCountForValue(-100, 100), "fully covering");
    }

    @Test
    void getCountForValueClampsPartialOverlap()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        for (int v = 10; v <= 20; v++)
        {
            h.addValue(v);
        }

        assertEquals(11L, h.getCountForValue(10, 20));
        assertEquals(11L, h.getCountForValue(0, 100), "clamped to actual range");
        assertEquals(6L, h.getCountForValue(15, 20));
        assertEquals(6L, h.getCountForValue(10, 15));
    }

    // ----------------------------------------------------------------
    // getPercentile argument validation
    // ----------------------------------------------------------------

    @Test
    void getPercentileRejectsOutOfRange()
    {
        final RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(1);

        assertThrows(IllegalArgumentException.class, () -> h.getPercentile(-1.0));
        assertThrows(IllegalArgumentException.class, () -> h.getPercentile(101.0));
    }

    // ----------------------------------------------------------------
    // Randomized differential test against a sorted-array oracle
    // ----------------------------------------------------------------

    @Test
    void percentilesMatchSortedArrayOracle()
    {
        final Random random = new Random(42);
        final int n = 5_000;
        final int[] data = new int[n];
        final RuntimeHistogram h = new RuntimeHistogram(); // precision 1 -> lossless

        for (int i = 0; i < n; i++)
        {
            data[i] = random.nextInt(1_000); // non-negative, bounded range
            h.addValue(data[i]);
        }

        final int[] sorted = data.clone();
        Arrays.sort(sorted);

        assertEquals(n, h.getValueCount());
        assertEquals((long) n, h.getCountForValue(0, 999));

        final double[] percentiles = {0.0, 0.5, 1.0, 10.0, 25.0, 50.0, 75.0, 90.0, 99.0, 99.5, 100.0};
        for (final double p : percentiles)
        {
            assertEquals(expectedPercentile(sorted, p), h.getPercentile(p), 1e-9, "percentile " + p);
        }
    }

    @Test
    void percentilesMatchOracleWithDuplicates()
    {
        final Random random = new Random(7);
        final int n = 2_000;
        final int[] data = new int[n];
        final RuntimeHistogram h = new RuntimeHistogram();

        for (int i = 0; i < n; i++)
        {
            data[i] = random.nextInt(10); // heavy duplicates
            h.addValue(data[i]);
        }

        final int[] sorted = data.clone();
        Arrays.sort(sorted);

        for (final double p : new double[] {0.0, 25.0, 50.0, 75.0, 100.0})
        {
            assertEquals(expectedPercentile(sorted, p), h.getPercentile(p), 1e-9, "percentile " + p);
        }
    }

    @Test
    void bucketCountsAlwaysSumToValueCount()
    {
        final Random random = new Random(2026);
        final RuntimeHistogram h = new RuntimeHistogram();
        int added = 0;
        for (int i = 0; i < 3_000; i++)
        {
            final int v = random.nextInt(2_000) - 500; // includes negatives
            h.addValue(v);
            added++;
            assertEquals(added, h.getValueCount());
            assertEquals((long) added, h.getCountForValue(-1_000, 2_000), "full range must equal valueCount");
        }
    }

    // ----------------------------------------------------------------
    // Oracle
    // ----------------------------------------------------------------

    /**
     * Mirrors the empirical-quantile algorithm implemented by
     * {@link RuntimeHistogram#getPercentile(double)} for a lossless
     * (precision 1) histogram over the given sorted data.
     */
    private static double expectedPercentile(final int[] sorted, final double p)
    {
        final int n = sorted.length;
        if (p == 0.0)
        {
            return sorted[0];
        }
        if (p == 100.0)
        {
            return sorted[n - 1];
        }

        final double np = n * (p / 100.0);
        if ((np % 1.0) == 0.0)
        {
            final int k = (int) np; // 1-based
            return (sorted[k - 1] + sorted[k]) / 2.0;
        }
        return sorted[(int) Math.ceil(np) - 1];
    }
}
