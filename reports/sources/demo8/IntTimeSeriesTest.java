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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IntTimeSeriesTest
{
    @Test
    public void defaultConstructor()
    {
        IntTimeSeries ts = new IntTimeSeries();
        assertEquals(4096, ts.getSize()); // 3600 rounded up to next power of two
        assertEquals(1, ts.getScale());
        assertEquals(1, ts.getSlotWidth());
        assertEquals(0, ts.getCount());
        assertEquals(0, ts.getTotalValue());
        assertEquals(0, ts.getErrorCount());
        assertEquals(0.0, ts.getMean());
        assertEquals(0.0, ts.getStandardDeviation());
        assertThrows(IllegalStateException.class, ts::getFirstSecond);
        assertThrows(IllegalStateException.class, ts::getLastSecond);
    }

    @Test
    public void customSizeConstructorPowerOfTwo()
    {
        IntTimeSeries ts = new IntTimeSeries(100);
        assertEquals(128, ts.getSize()); // nextHighestPowerOfTwo(100) = 128
        assertEquals(128, ts.getValues().length);
    }

    @Test
    public void addSingleValue()
    {
        IntTimeSeries ts = new IntTimeSeries(64);
        long timeMs = 1_000_000L; // 1000s
        ts.addValue(timeMs, 50, false);

        assertEquals(1000, ts.getFirstSecond());
        assertEquals(1000, ts.getLastSecond());
        assertEquals(1, ts.getCount());
        assertEquals(50L, ts.getTotalValue());
        assertEquals(0, ts.getErrorCount());
        assertEquals(50.0, ts.getMean());
        assertEquals(0.0, ts.getStandardDeviation());
        // RuntimeHistogram precision is 8, so value 50 is in bucket 50>>3 = 6 -> 6<<3 = 48
        assertEquals(48, ts.getPercentile(50.0));
    }

    @Test
    public void addValueWithStartAndEndTime()
    {
        IntTimeSeries ts = new IntTimeSeries(64);
        long startTime = 1000_000L; // 1000s
        long endTime = 1003_000L;   // 1003s
        ts.addValue(startTime, endTime, 100, true);

        assertEquals(1000, ts.getFirstSecond());
        assertEquals(1003, ts.getLastSecond());
        assertEquals(1, ts.getCount());
        assertEquals(1, ts.getErrorCount());
        assertEquals(100L, ts.getTotalValue());

        // Check concurrency across slots 0, 1, 2, 3
        IntTimeSeriesEntry[] values = ts.getValues();
        assertEquals(1, values[0].getConcurrentCount()); // startSecond
        assertEquals(1, values[1].getConcurrentCount()); // 1001s
        assertEquals(1, values[2].getConcurrentCount()); // 1002s
        assertEquals(1, values[3].getConcurrentCount()); // 1003s
    }

    @Test
    public void shiftRightWhenEarlierTimestampArrives()
    {
        IntTimeSeries ts = new IntTimeSeries(64);
        ts.addValue(1010_000L, 50, false); // firstSecond = 1010, pos = 0
        assertEquals(1010, ts.getFirstSecond());

        // Add earlier timestamp without overflowing capacity
        ts.addValue(1005_000L, 30, false); // firstSecond should become 1005
        assertEquals(1005, ts.getFirstSecond());
        assertEquals(1010, ts.getLastSecond());
        assertEquals(2, ts.getCount());
        assertEquals(80L, ts.getTotalValue());

        IntTimeSeriesEntry[] entries = ts.getValues();
        // At index 0 (1005s): value 30
        assertEquals(30, entries[0].getMaximumValue());
        // At index 5 (1010s): value 50
        assertEquals(50, entries[5].getMaximumValue());
    }

    @Test
    public void condenseWhenFutureTimestampExceedsCapacity()
    {
        IntTimeSeries ts = new IntTimeSeries(8); // size = 8 seconds capacity at scale 1
        ts.addValue(1000_000L, 10, false); // 1000s, pos 0
        ts.addValue(1001_000L, 20, false); // 1001s, pos 1
        assertEquals(1, ts.getScale());

        // Add timestamp beyond 8 slots: 1010s (exceeds 1000 + 8 = 1008)
        // This triggers condense: pairs of slots merge, scale increases
        ts.addValue(1010_000L, 30, false);

        assertTrue(ts.getScale() > 1);
        assertEquals(3, ts.getCount());
        assertEquals(60L, ts.getTotalValue());
        assertEquals(20.0, ts.getMean());
    }

    @Test
    public void shiftRightWithCondense()
    {
        IntTimeSeries ts = new IntTimeSeries(8); // size = 8
        ts.addValue(1005_000L, 10, false); // firstSecond = 1005
        ts.addValue(1010_000L, 20, false); // lastSecond = 1010 (offset 5)

        // Add 990s -> offset from 1005 is 15, which with pos 5 is 20 >= 8, triggering condense during shiftRight
        ts.addValue(990_000L, 30, false);

        assertTrue(ts.getScale() > 1);
        assertEquals(990, ts.getFirstSecond());
    }

    @Test
    public void toHistogram()
    {
        IntTimeSeries ts = new IntTimeSeries(64);
        // Empty time series toHistogram returns empty list
        List<IntTimeSeries.HistogramBucket> emptyBuckets = ts.toHistogram(5);
        assertTrue(emptyBuckets.isEmpty());

        ts.addValue(1000_000L, 10, false);
        ts.addValue(1001_000L, 50, false);
        ts.addValue(1002_000L, 100, false);

        List<IntTimeSeries.HistogramBucket> buckets = ts.toHistogram(10);
        assertEquals(10, buckets.size());

        for (IntTimeSeries.HistogramBucket bucket : buckets)
        {
            assertNotNull(bucket.toString());
        }
    }

    @Test
    public void statisticsAggregations()
    {
        IntTimeSeries ts = new IntTimeSeries(64);
        ts.addValue(1000_000L, 10, false);
        ts.addValue(1001_000L, 20, true);
        ts.addValue(1002_000L, 30, false);

        IntTimeSeries.Statistics stats = ts.getStatistics();
        assertEquals(3, stats.count);
        assertEquals(1, stats.errorCount);
        assertEquals(60L, stats.sum);
        assertEquals(10, stats.minValue);
        assertEquals(30, stats.maxValue);
    }
}
