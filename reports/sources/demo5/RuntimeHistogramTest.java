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

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link RuntimeHistogram} memory efficient percentile calculator.
 */
public class RuntimeHistogramTest
{
    @Test
    public void emptyHistogram()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        assertTrue(histogram.isEmpty());
        assertEquals(0, histogram.getValueCount());
        assertEquals(0, histogram.getNumberOfBuckets());
        assertEquals(1, histogram.getPrecision());
        assertEquals(0.0, histogram.getMedianValue());
        assertEquals(0.0, histogram.getPercentile(0.0));
        assertEquals(0.0, histogram.getPercentile(50.0));
        assertEquals(0.0, histogram.getPercentile(100.0));
        assertEquals(0.0, histogram.getQuantile(0.5));
        assertEquals(0, histogram.getCountForValue(1, 100));
        assertEquals(0, histogram.getCountForValue(-1000, -500));
    }

    @Test
    public void percentileRangeIsValidated()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        histogram.addValue(1);
        histogram.addValue(2);

        // allowed
        assertEquals(1.0, histogram.getPercentile(0.0));
        assertEquals(2.0, histogram.getPercentile(100.0));

        // forbidden
        assertThrows(IllegalArgumentException.class, () -> histogram.getPercentile(-0.0001));
        assertThrows(IllegalArgumentException.class, () -> histogram.getPercentile(100.0001));
        assertThrows(IllegalArgumentException.class, () -> histogram.getQuantile(1.5));
        assertThrows(IllegalArgumentException.class, () -> histogram.getQuantile(-0.5));
    }

    @Test
    public void countRangeIsValidated()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        histogram.addValue(5);
        assertThrows(IllegalArgumentException.class, () -> histogram.getCountForValue(10, 5));
    }

    @Test
    public void addValueIncreasesCountAndKeepsExtremes()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        histogram.addValue(42);
        assertFalse(histogram.isEmpty());
        assertEquals(1, histogram.getValueCount());

        histogram.addValue(-7);
        histogram.addValue(1_000_000);
        assertEquals(3, histogram.getValueCount());
        // percentiles 0 and 100 are exact bucket boundaries (here: the exact values)
        assertEquals(-7.0, histogram.getPercentile(0.0));
        assertEquals(1_000_000.0, histogram.getPercentile(100.0));
    }

    @Test
    public void medianForOddAndEvenCounts()
    {
        final RuntimeHistogram odd = new RuntimeHistogram();
        odd.addValue(10);
        odd.addValue(20);
        odd.addValue(30);
        assertEquals(20.0, odd.getMedianValue());

        final RuntimeHistogram even = new RuntimeHistogram();
        even.addValue(10);
        even.addValue(20);
        assertEquals(15.0, even.getMedianValue());

        // single value: every percentile is that value
        final RuntimeHistogram single = new RuntimeHistogram();
        single.addValue(7);
        assertEquals(7.0, single.getPercentile(0.0));
        assertEquals(7.0, single.getPercentile(50.0));
        assertEquals(7.0, single.getPercentile(100.0));
    }

    @Test
    public void duplicatesAreCounted()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        for (int i = 0; i < 7; i++)
        {
            histogram.addValue(5);
        }
        assertEquals(7, histogram.getValueCount());
        assertEquals(5.0, histogram.getMedianValue());
        assertEquals(7, histogram.getCountForValue(5, 5));
        assertEquals(7, histogram.getCountForValue(4, 6));
    }

    @Test
    public void defaultPrecisionKeepsValuesExact()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        histogram.addValue(5);
        histogram.addValue(6);
        histogram.addValue(7);
        assertEquals(6.0, histogram.getMedianValue());
        assertEquals(3, histogram.getCountForValue(5, 7));
        assertEquals(1, histogram.getCountForValue(6, 6));
        assertEquals(0, histogram.getCountForValue(1, 4));
        assertEquals(0, histogram.getCountForValue(100, 200));
    }

    @Test
    public void coarserPrecisionGroupsAdjacentValues()
    {
        // bucket width 8: values 0-7 share bucket 0, values 8-15 share bucket 1, ...
        final RuntimeHistogram histogram = new RuntimeHistogram(8);
        assertEquals(8, histogram.getPrecision());

        histogram.addValue(0);
        histogram.addValue(7);
        histogram.addValue(8);
        histogram.addValue(15);

        assertEquals(4, histogram.getValueCount());
        assertEquals(2, histogram.getNumberOfBuckets());
        assertEquals(2, histogram.getCountForValue(0, 7));
        assertEquals(2, histogram.getCountForValue(8, 15));

        // buckets are approximated: the range [7, 8] spans two buckets -> all 4 values
        assertEquals(4, histogram.getCountForValue(7, 8));
        assertEquals(0, histogram.getCountForValue(16, 100));

        // the median of the quantized values 0, 0, 8, 8 is the mean of ranks 2 and 3
        assertEquals(4.0, histogram.getMedianValue());
        assertEquals(0.0, histogram.getPercentile(0.0));
        assertEquals(8.0, histogram.getPercentile(100.0));
    }

    @Test
    public void wholeBucketsAreCountedEvenIfTheyStraddleTheRange()
    {
        // value 0 and 7 share one bucket of width 8 -> both queries below count both
        final RuntimeHistogram histogram = new RuntimeHistogram(8);
        histogram.addValue(0);
        histogram.addValue(7);
        assertEquals(2, histogram.getCountForValue(7, 7));
        assertEquals(2, histogram.getCountForValue(0, 0));
    }

    @Test
    public void nonPowerOfTwoPrecisionRoundsUpToPowerOfTwo()
    {
        assertPrecision(1, 1);
        assertPrecision(2, 2);
        assertPrecision(3, 4);
        assertPrecision(4, 4);
        assertPrecision(5, 8);
        assertPrecision(8, 8);
        assertPrecision(10, 16);
        assertPrecision(16, 16);
    }

    private static void assertPrecision(final int requested, final int expected)
    {
        assertEquals(expected, new RuntimeHistogram(requested).getPrecision());
    }

    @Test
    public void negativeValuesAreSupported()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        histogram.addValue(-1000);
        histogram.addValue(10);
        histogram.addValue(1000);
        assertEquals(3, histogram.getValueCount());
        assertEquals(-1000.0, histogram.getPercentile(0.0));
        assertEquals(1000.0, histogram.getPercentile(100.0));
        assertEquals(10.0, histogram.getMedianValue());
        assertEquals(1, histogram.getCountForValue(-999, 999));
        assertEquals(3, histogram.getCountForValue(-1000, 1000));
        assertEquals(0, histogram.getCountForValue(-5000, -2000));
        assertEquals(0, histogram.getCountForValue(2000, 5000));
    }

    @Test
    public void sparseValuesAllocateOnlyTheSpanBetweenMinAndMaxBucket()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        histogram.addValue(-1000);
        histogram.addValue(1000);
        // buckets span exactly the closed index interval [-1000, 1000]
        assertEquals(2001, histogram.getNumberOfBuckets());
        assertEquals(1, histogram.getCountForValue(-1000, -1000));
        assertEquals(1, histogram.getCountForValue(1000, 1000));
        assertEquals(0, histogram.getCountForValue(-999, 999));
        // repeated growth into both directions
        histogram.addValue(5000);
        histogram.addValue(-5000);
        assertEquals(10001, histogram.getNumberOfBuckets());
        assertEquals(4, histogram.getValueCount());
    }

    @Test
    public void randomValuesMatchExactSortedListForDefaultPrecision()
    {
        final Random random = new Random(11);
        for (int trial = 0; trial < 20; trial++)
        {
            final int count = 1 + random.nextInt(400);
            final RuntimeHistogram histogram = new RuntimeHistogram();
            final long[] values = new long[count];
            for (int i = 0; i < count; i++)
            {
                final int value = random.nextInt(2000) - 1000;
                values[i] = value;
                histogram.addValue(value);
            }
            java.util.Arrays.sort(values);
            checkPercentiles(histogram, values, 1);
        }
    }

    @Test
    public void randomValuesMatchBucketizedReferenceForVariousPrecisions()
    {
        final Random random = new Random(12);
        final int[] shifts = {0, 1, 2, 3, 4};
        for (int trial = 0; trial < 60; trial++)
        {
            final int shift = shifts[random.nextInt(shifts.length)];
            final int width = 1 << shift;
            final int count = 1 + random.nextInt(300);
            final RuntimeHistogram histogram = new RuntimeHistogram(width);
            final long[] edges = new long[count];
            for (int i = 0; i < count; i++)
            {
                final int value = random.nextInt(1200) - 200;
                edges[i] = quantize(value, shift);
                histogram.addValue(value);
            }
            java.util.Arrays.sort(edges);
            checkPercentiles(histogram, edges, width);
        }
    }

    private static void checkPercentiles(final RuntimeHistogram histogram, final long[] sortedEdges, final int width)
    {
        final double[] percentiles = {0.0, 1.0, 5.0, 10.0, 25.0, 33.3, 50.0, 66.6, 75.0, 90.0, 95.0, 99.0, 100.0};
        for (final double p : percentiles)
        {
            assertEquals(referencePercentile(sortedEdges, p), histogram.getPercentile(p), "p=" + p + " width=" + width);
        }
    }

    /**
     * Re-implements the quantile rule used by {@link RuntimeHistogram#getPercentile(double)}
     * over a sorted list of already bucket-quantized values.
     */
    private static double referencePercentile(final long[] sortedEdges, final double p)
    {
        final int n = sortedEdges.length;
        if (n == 0)
        {
            return 0.0;
        }
        if (p == 0.0)
        {
            return sortedEdges[0];
        }
        if (p == 100.0)
        {
            return sortedEdges[n - 1];
        }
        final double np = n * (p / 100.0);
        if (np % 1.0 == 0.0)
        {
            return (sortedEdges[(int) np - 1] + sortedEdges[(int) np]) / 2.0;
        }
        return sortedEdges[(int) Math.ceil(np) - 1];
    }

    private static long quantize(final int value, final int shift)
    {
        return (long) (value >> shift) << shift;
    }

    @Test
    public void quantileDelegatesToPercentile()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        histogram.addValue(100);
        histogram.addValue(200);
        histogram.addValue(300);
        assertEquals(histogram.getPercentile(25.0), histogram.getQuantile(0.25));
        assertEquals(histogram.getPercentile(50.0), histogram.getQuantile(0.5));
        assertEquals(histogram.getPercentile(99.0), histogram.getQuantile(0.99));
    }

    @Test
    public void valuesAddedInAnyOrderProduceTheSameResult()
    {
        final int[] values = {5, 5000, -100, 100, 42, 42, 5000, -100};
        final RuntimeHistogram forward = new RuntimeHistogram();
        final RuntimeHistogram backward = new RuntimeHistogram();
        for (final int v : values)
        {
            forward.addValue(v);
        }
        for (int i = values.length - 1; i >= 0; i--)
        {
            backward.addValue(values[i]);
        }
        assertEquals(forward.getValueCount(), backward.getValueCount());
        assertEquals(forward.getMedianValue(), backward.getMedianValue());
        assertEquals(forward.getPercentile(0.0), backward.getPercentile(0.0));
        assertEquals(forward.getPercentile(100.0), backward.getPercentile(100.0));
        assertEquals(forward.getNumberOfBuckets(), backward.getNumberOfBuckets());
    }
}
