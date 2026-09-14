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

import org.junit.jupiter.api.Test;

class RuntimeHistogramTest
{
    @Test
    void defaultAndEmptyStateUseNeutralResults()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();

        assertEquals(1, histogram.getPrecision());
        assertEquals(0, histogram.getValueCount());
        assertEquals(0, histogram.getNumberOfBuckets());
        assertTrue(histogram.isEmpty());
        assertEquals(0.0, histogram.getPercentile(0));
        assertEquals(0.0, histogram.getPercentile(50));
        assertEquals(0.0, histogram.getPercentile(100));
        assertEquals(0.0, histogram.getMedianValue());
        assertEquals(0.0, histogram.getQuantile(0.5));
        assertEquals(0, histogram.getCountForValue(-100, 100));
    }

    @Test
    void requestedPrecisionRoundsUpAndValuesUseLowerBucketBoundary()
    {
        final int[][] precisions = {
            {1, 1}, {2, 2}, {3, 4}, {4, 4}, {5, 8}, {7, 8}, {8, 8}, {9, 16}, {16, 16}, {17, 32}
        };
        for (final int[] precision : precisions)
        {
            assertEquals(precision[1], new RuntimeHistogram(precision[0]).getPrecision(),
                         "requested precision " + precision[0]);
        }

        final int[][] valuesAndRepresentatives = {
            {-9, -12}, {-8, -8}, {-7, -8}, {-5, -8}, {-4, -4}, {-3, -4}, {-1, -4},
            {0, 0}, {3, 0}, {4, 4}, {7, 4}, {8, 8}
        };
        for (final int[] sample : valuesAndRepresentatives)
        {
            final RuntimeHistogram histogram = new RuntimeHistogram(3);
            histogram.addValue(sample[0]);

            assertEquals(sample[1], histogram.getPercentile(0), "minimum for " + sample[0]);
            assertEquals(sample[1], histogram.getMedianValue(), "median for " + sample[0]);
            assertEquals(sample[1], histogram.getPercentile(100), "maximum for " + sample[0]);
        }
    }

    @Test
    void growsInBothDirectionsAndPreservesCountsAndHoles()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram(3);
        for (final int value : new int[] {9, 18, 13, -1, 7, 9})
        {
            histogram.addValue(value);
        }

        assertFalse(histogram.isEmpty());
        assertEquals(6, histogram.getValueCount());
        assertEquals(6, histogram.getNumberOfBuckets());
        assertEquals(-4.0, histogram.getPercentile(0));
        assertEquals(4.0, histogram.getPercentile(25));
        assertEquals(8.0, histogram.getMedianValue());
        assertEquals(12.0, histogram.getPercentile(75));
        assertEquals(16.0, histogram.getPercentile(100));
        assertEquals(1, histogram.getCountForValue(-4, -1));
        assertEquals(0, histogram.getCountForValue(0, 3));
        assertEquals(1, histogram.getCountForValue(4, 7));
        assertEquals(2, histogram.getCountForValue(8, 11));
        assertEquals(1, histogram.getCountForValue(12, 15));
        assertEquals(1, histogram.getCountForValue(16, 19));
    }

    @Test
    void duplicateValuesIncreaseCountsWithoutAllocatingMoreBuckets()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        for (int i = 0; i < 5; i++)
        {
            histogram.addValue(7);
        }

        assertEquals(5, histogram.getValueCount());
        assertEquals(1, histogram.getNumberOfBuckets());
        assertEquals(7.0, histogram.getPercentile(0));
        assertEquals(7.0, histogram.getPercentile(20));
        assertEquals(7.0, histogram.getMedianValue());
        assertEquals(7.0, histogram.getPercentile(100));
        assertEquals(5, histogram.getCountForValue(7, 7));
        assertEquals(0, histogram.getCountForValue(6, 6));
        assertEquals(0, histogram.getCountForValue(8, 8));
    }

    @Test
    void percentilesUseTypeTwoEmpiricalRanks()
    {
        final RuntimeHistogram histogram = histogramOf(40, 10, 30, 20);
        final double[][] percentiles = {
            {0, 10}, {1, 10}, {24.999, 10}, {25, 15}, {25.001, 20},
            {49.999, 20}, {50, 25}, {50.001, 30}, {74.999, 30},
            {75, 35}, {75.001, 40}, {99, 40}, {100, 40}
        };

        for (final double[] percentile : percentiles)
        {
            assertEquals(percentile[1], histogram.getPercentile(percentile[0]),
                         "percentile " + percentile[0]);
        }
        assertEquals(histogram.getPercentile(50), histogram.getMedianValue());
    }

    @Test
    void integralRanksAreAveragedForOddSizedSamplesToo()
    {
        final RuntimeHistogram histogram = histogramOf(5, 1, 4, 2, 3);

        assertEquals(1.0, histogram.getPercentile(0));
        assertEquals(1.5, histogram.getPercentile(20));
        assertEquals(2.5, histogram.getPercentile(40));
        assertEquals(3.0, histogram.getPercentile(50));
        assertEquals(3.5, histogram.getPercentile(60));
        assertEquals(4.5, histogram.getPercentile(80));
        assertEquals(5.0, histogram.getPercentile(100));
    }

    @Test
    void quantilesMapToPercentilesAndValidateTheirRange()
    {
        final RuntimeHistogram histogram = histogramOf(40, 10, 30, 20);

        assertEquals(10.0, histogram.getQuantile(0));
        assertEquals(15.0, histogram.getQuantile(0.25));
        assertEquals(25.0, histogram.getQuantile(0.5));
        assertEquals(35.0, histogram.getQuantile(0.75));
        assertEquals(40.0, histogram.getQuantile(1));

        assertThrows(IllegalArgumentException.class, () -> histogram.getPercentile(-0.01));
        assertThrows(IllegalArgumentException.class, () -> histogram.getPercentile(100.01));
        assertThrows(IllegalArgumentException.class, () -> histogram.getPercentile(Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> histogram.getPercentile(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> histogram.getQuantile(-0.01));
        assertThrows(IllegalArgumentException.class, () -> histogram.getQuantile(1.01));
    }

    @Test
    void exactPrecisionRangeQueriesAreInclusiveAndClampToStoredData()
    {
        final RuntimeHistogram histogram = histogramOf(2, -1, 5, -3, 2, 0, -1);

        assertEquals(7, histogram.getCountForValue(-100, 100));
        assertEquals(7, histogram.getCountForValue(-3, 5));
        assertEquals(5, histogram.getCountForValue(-1, 2));
        assertEquals(3, histogram.getCountForValue(-2, 1));
        assertEquals(1, histogram.getCountForValue(-3, -3));
        assertEquals(2, histogram.getCountForValue(-1, -1));
        assertEquals(1, histogram.getCountForValue(0, 0));
        assertEquals(0, histogram.getCountForValue(1, 1));
        assertEquals(2, histogram.getCountForValue(2, 2));
        assertEquals(0, histogram.getCountForValue(-100, -4));
        assertEquals(0, histogram.getCountForValue(6, 100));
        assertThrows(IllegalArgumentException.class, () -> histogram.getCountForValue(1, 0));
    }

    @Test
    void coarseRangeQueriesCountWholeIntersectingInternalBuckets()
    {
        final RuntimeHistogram histogram = new RuntimeHistogram(8);
        for (final int value : new int[] {-8, -1, 0, 7, 8, 15})
        {
            histogram.addValue(value);
        }

        assertEquals(2, histogram.getCountForValue(-1, -1));
        assertEquals(2, histogram.getCountForValue(1, 1));
        assertEquals(2, histogram.getCountForValue(9, 9));
        assertEquals(6, histogram.getCountForValue(-8, 15));
    }

    private static RuntimeHistogram histogramOf(final int... values)
    {
        final RuntimeHistogram histogram = new RuntimeHistogram();
        for (final int value : values)
        {
            histogram.addValue(value);
        }
        return histogram;
    }
}
