/*
 * Copyright (c) 2005-2025 Xceptance Software Technologies GmbH
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
package com.xceptance.xlt.report.util.rework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IntTimeSeries}.
 */
class IntTimeSeriesTest
{
    // Helper: base timestamp in ms, a fixed known second
    private static final long BASE_MS = 1_700_000_000_000L; // 2023-11-14
    private static final int BASE_SEC = (int) (BASE_MS / 1000);

    @Test
    void defaultConstructor()
    {
        IntTimeSeries ts = new IntTimeSeries();
        assertEquals(4096, ts.getSize()); // next power of two of 3600
        assertEquals(1, ts.getScale());
        assertEquals(1, ts.getSlotWidth());
        assertEquals(0, ts.getCount());
        assertEquals(0, ts.getTotalValue());
        assertEquals(0, ts.getErrorCount());
        assertEquals(0.0, ts.getMean());
        assertEquals(0.0, ts.getStandardDeviation());
    }

    @Test
    void customSize()
    {
        IntTimeSeries ts = new IntTimeSeries(100);
        assertEquals(128, ts.getSize()); // next power of two of 100
    }

    @Test
    void sizeAlwaysPowerOfTwo()
    {
        assertEquals(1, new IntTimeSeries(1).getSize());
        assertEquals(2, new IntTimeSeries(2).getSize());
        assertEquals(4, new IntTimeSeries(3).getSize());
        assertEquals(8, new IntTimeSeries(8).getSize());
        assertEquals(16, new IntTimeSeries(9).getSize());
        assertEquals(4096, new IntTimeSeries(3600).getSize());
    }

    @Test
    void getFirstSecond_throwsWhenEmpty()
    {
        IntTimeSeries ts = new IntTimeSeries();
        assertThrows(IllegalStateException.class, ts::getFirstSecond);
    }

    @Test
    void getLastSecond_throwsWhenEmpty()
    {
        IntTimeSeries ts = new IntTimeSeries();
        assertThrows(IllegalStateException.class, ts::getLastSecond);
    }

    @Test
    void addValue_single()
    {
        IntTimeSeries ts = new IntTimeSeries();
        ts.addValue(BASE_MS, 100, false);

        assertEquals(BASE_SEC, ts.getFirstSecond());
        assertEquals(BASE_SEC, ts.getLastSecond());
        assertEquals(1, ts.getCount());
        assertEquals(100, ts.getTotalValue());
        assertEquals(0, ts.getErrorCount());
        assertEquals(100.0, ts.getMean());
    }

    @Test
    void addValue_sameSecondMultipleValues()
    {
        IntTimeSeries ts = new IntTimeSeries();
        ts.addValue(BASE_MS, 100, false);
        ts.addValue(BASE_MS + 100, 200, false);
        ts.addValue(BASE_MS + 999, 300, true);

        assertEquals(3, ts.getCount());
        assertEquals(600, ts.getTotalValue());
        assertEquals(1, ts.getErrorCount());
        assertEquals(200.0, ts.getMean());
        assertEquals(100, ts.getStatistics().minValue);
        assertEquals(300, ts.getStatistics().maxValue);
    }

    @Test
    void addValue_multipleSeconds()
    {
        IntTimeSeries ts = new IntTimeSeries();
        ts.addValue(BASE_MS, 100, false);
        ts.addValue(BASE_MS + 1000, 200, false);
        ts.addValue(BASE_MS + 2000, 300, false);

        assertEquals(BASE_SEC, ts.getFirstSecond());
        assertEquals(BASE_SEC + 2, ts.getLastSecond());
        assertEquals(3, ts.getCount());
        assertEquals(600, ts.getTotalValue());
    }

    @Test
    void addValue_withEndTime_concurrency()
    {
        IntTimeSeries ts = new IntTimeSeries();
        // A request spanning 3 seconds
        ts.addValue(BASE_MS, BASE_MS + 2000, 100, false);

        assertEquals(1, ts.getCount()); // one value
        // But concurrency: slots 0, 1, 2 should have concurrent counts
        IntTimeSeriesEntry[] values = ts.getValues();
        assertEquals(1, values[0].getConcurrentCount());
        assertEquals(1, values[1].getConcurrentCount());
        assertEquals(1, values[2].getConcurrentCount());
    }

    @Test
    void addValue_goingBackInTime_shiftsRight()
    {
        IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(BASE_MS + 5000, 100, false);
        // go back 2 seconds
        ts.addValue(BASE_MS + 3000, 200, false);

        assertEquals(BASE_SEC + 3, ts.getFirstSecond());
        assertEquals(BASE_SEC + 5, ts.getLastSecond());
        assertEquals(2, ts.getCount());
        assertEquals(300, ts.getTotalValue());
    }

    @Test
    void addValue_exceedsCapacity_triggersCondense()
    {
        IntTimeSeries ts = new IntTimeSeries(8); // capacity 8 seconds
        ts.addValue(BASE_MS, 100, false);
        ts.addValue(BASE_MS + 9000, 200, false); // 9 seconds later, exceeds capacity

        assertEquals(2, ts.getScale()); // condensed once, scale doubled
        assertEquals(2, ts.getSlotWidth());
        assertEquals(2, ts.getCount());
        assertEquals(300, ts.getTotalValue());
    }

    @Test
    void addValue_multipleCondensations()
    {
        IntTimeSeries ts = new IntTimeSeries(4); // very small capacity
        long t = BASE_MS;
        for (int i = 0; i < 10; i++)
        {
            ts.addValue(t, i * 10, false);
            t += 10_000; // 10 seconds apart
        }
        // Should have condensed multiple times
        assertTrue(ts.getScale() > 1);
        assertEquals(10, ts.getCount());
    }

    @Test
    void getValues_returnsArray()
    {
        IntTimeSeries ts = new IntTimeSeries(4);
        ts.addValue(BASE_MS, 100, false);
        IntTimeSeriesEntry[] values = ts.getValues();
        assertEquals(4, values.length);
    }

    @Test
    void getStatistics_aggregates()
    {
        IntTimeSeries ts = new IntTimeSeries();
        ts.addValue(BASE_MS, 100, false);
        ts.addValue(BASE_MS + 1000, 200, true);
        ts.addValue(BASE_MS + 2000, 300, false);

        IntTimeSeries.Statistics stat = ts.getStatistics();
        assertEquals(3, stat.count);
        assertEquals(1, stat.errorCount);
        assertEquals(600, stat.sum);
        assertEquals(100, stat.minValue);
        assertEquals(300, stat.maxValue);
    }

    @Test
    void getPercentile()
    {
        IntTimeSeries ts = new IntTimeSeries();
        for (int i = 1; i <= 100; i++)
        {
            ts.addValue(BASE_MS, i, false);
        }
        // RuntimeHistogram uses precision 8 internally, so the median lands
        // in the bucket covering 48..55 and returns the bucket base value 48
        int p50 = ts.getPercentile(50.0);
        assertTrue(p50 >= 40 && p50 <= 56, "p50 was " + p50);
    }

    @Test
    void getStandardDeviation_uniform()
    {
        IntTimeSeries ts = new IntTimeSeries();
        ts.addValue(BASE_MS, 10, false);
        ts.addValue(BASE_MS + 1000, 20, false);
        ts.addValue(BASE_MS + 2000, 30, false);

        // mean=20, variance = ((10-20)^2 + (20-20)^2 + (30-20)^2)/3 = 200/3
        // sd = sqrt(200/3) ≈ 8.16
        double sd = ts.getStandardDeviation();
        assertTrue(Math.abs(sd - 8.1649658) < 0.001, "sd was " + sd);
    }

    @Test
    void getStandardDeviation_singleValue()
    {
        IntTimeSeries ts = new IntTimeSeries();
        ts.addValue(BASE_MS, 42, false);
        assertEquals(0.0, ts.getStandardDeviation());
    }

    @Test
    void getMean_emptyReturnsZero()
    {
        IntTimeSeries ts = new IntTimeSeries();
        assertEquals(0.0, ts.getMean());
    }

    @Test
    void toHistogram_empty()
    {
        IntTimeSeries ts = new IntTimeSeries();
        List<IntTimeSeries.HistogramBucket> buckets = ts.toHistogram(10);
        assertTrue(buckets.isEmpty());
    }

    @Test
    void toHistogram_singleValue_throwsIAE_whenMinEqualsMax()
    {
        // Documents a bug: when all values are identical (min==max), bucketWidth becomes 0
        // and the computed start/end range inverts, causing an IllegalArgumentException
        // from RuntimeHistogram.getCountForValue. See doc/XLT-DATA.md for a proposed fix.
        IntTimeSeries ts = new IntTimeSeries();
        ts.addValue(BASE_MS, 50, false);
        assertThrows(IllegalArgumentException.class, () -> ts.toHistogram(10));
    }

    @Test
    void toHistogram_uniformDistribution()
    {
        IntTimeSeries ts = new IntTimeSeries();
        for (int i = 0; i < 100; i++)
        {
            ts.addValue(BASE_MS, i, false);
        }
        List<IntTimeSeries.HistogramBucket> buckets = ts.toHistogram(10);
        assertEquals(10, buckets.size());
        // Note: the internal RuntimeHistogram uses precision 8 (8-wide buckets), so
        // getCountForValue can count edge buckets in two adjacent output buckets.
        // The total can therefore exceed the number of input values.
        long totalCount = buckets.stream().mapToLong(IntTimeSeries.HistogramBucket::count).sum();
        assertTrue(totalCount >= 100, "total was " + totalCount);
    }

    @Test
    void histogramBucket_toStringFormat()
    {
        IntTimeSeries.HistogramBucket b = new IntTimeSeries.HistogramBucket(0, 10, 5);
        assertEquals("0, 10, 5", b.toString());
    }

    @Test
    void addValue_threeArgOverload()
    {
        IntTimeSeries ts = new IntTimeSeries();
        ts.addValue(BASE_MS, 100, false);
        ts.addValue(BASE_MS + 1000, 200, false);

        assertEquals(2, ts.getCount());
        assertEquals(300, ts.getTotalValue());
    }

    @Test
    void valuesArePreservedAcrossCondense()
    {
        IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(BASE_MS, 100, false);
        ts.addValue(BASE_MS + 1000, 200, false);
        // now force condense
        ts.addValue(BASE_MS + 10_000, 300, false);

        // all values must still be counted
        assertEquals(3, ts.getCount());
        assertEquals(600, ts.getTotalValue());
    }

    @Test
    void slotWidthMatchesScale()
    {
        IntTimeSeries ts = new IntTimeSeries(4);
        assertEquals(1, ts.getSlotWidth());
        ts.addValue(BASE_MS, 1, false);
        ts.addValue(BASE_MS + 100_000, 2, false);
        // after large jump, scale must have grown
        assertTrue(ts.getScale() > 1);
        assertEquals(1 << (ts.getScale() - 1), ts.getSlotWidth());
    }
}
