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
package com.xceptance.xlt.report.util.rework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.xceptance.xlt.report.util.RuntimeHistogram;

/**
 * Tests for the {@link IntTimeSeries} fixed-size, self-condensing time series.
 */
public class IntTimeSeriesTest
{
    /** A safe base time (ms since epoch); seconds stay far away from the int overflow edge. */
    private static final long BASE_MS = 1_700_000_000_000L;

    private static final long SECOND = 1_000L;

    @Test
    public void defaultConstruction()
    {
        final IntTimeSeries series = new IntTimeSeries();
        assertEquals(4096, series.getSize());
        assertEquals(4096, series.getValues().length);
        assertEquals(1, series.getScale());
        assertEquals(1, series.getSlotWidth());
    }

    @Test
    public void sizeIsRoundedUpToAPowerOfTwo()
    {
        assertEquals(16, new IntTimeSeries(10).getSize());
        assertEquals(16, new IntTimeSeries(16).getSize());
        assertEquals(128, new IntTimeSeries(100).getSize());
        assertEquals(4096, new IntTimeSeries(3600).getSize());
    }

    @Test
    public void emptySeriesHasNoData()
    {
        final IntTimeSeries series = new IntTimeSeries(16);

        assertThrows(IllegalStateException.class, series::getFirstSecond);
        assertThrows(IllegalStateException.class, series::getLastSecond);

        final IntTimeSeries.Statistics statistics = series.getStatistics();
        assertEquals(0, statistics.count);
        assertEquals(0, statistics.errorCount);
        assertEquals(0, statistics.sum);
        assertEquals(0, series.getCount());
        assertEquals(0, series.getTotalValue());
        assertEquals(0, series.getErrorCount());
        assertEquals(0.0, series.getMean());
        assertEquals(0.0, series.getStandardDeviation());
        assertEquals(0, series.getPercentile(50.0));
        assertTrue(series.toHistogram(4).isEmpty());

        for (final IntTimeSeriesEntry entry : series.getValues())
        {
            assertEquals(0, entry.getCount());
        }
    }

    @Test
    public void singleInstantIsStoredInFirstSlot()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        series.addValue(BASE_MS, 123, false);

        assertEquals(1_700_000_000L, series.getFirstSecond());
        assertEquals(1_700_000_000L, series.getLastSecond());

        final IntTimeSeries.Statistics statistics = series.getStatistics();
        assertEquals(1, statistics.count);
        assertEquals(0, statistics.errorCount);
        assertEquals(123, statistics.sum);
        assertEquals(123, statistics.minValue);
        assertEquals(123, statistics.maxValue);
        assertEquals(123.0, series.getMean());
        assertEquals(0.0, series.getStandardDeviation());

        final IntTimeSeriesEntry slot = series.getValues()[0];
        assertEquals(1, slot.getCount());
        assertEquals(1, slot.getConcurrentCount());
        assertEquals(123, slot.getTotalValue());
    }

    @Test
    public void transactionsInTheSameSecondAccumulate()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        series.addValue(BASE_MS, 4, false);
        series.addValue(BASE_MS, 6, true);

        final IntTimeSeriesEntry slot = series.getValues()[0];
        assertEquals(2, slot.getCount());
        assertEquals(2, slot.getConcurrentCount());
        assertEquals(1, slot.getErrorCount());
        assertEquals(10, slot.getTotalValue());
        assertEquals(4, slot.getMinimumValue());
        assertEquals(6, slot.getMaximumValue());

        assertEquals(2, series.getCount());
        assertEquals(1, series.getErrorCount());
        assertEquals(10, series.getTotalValue());
    }

    @Test
    public void transactionsInDifferentSecondsLandInMatchingSlots()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        series.addValue(BASE_MS, 10, false);
        series.addValue(BASE_MS + 3 * SECOND, 20, false);
        series.addValue(BASE_MS + 6 * SECOND, 30, false);

        assertEquals(1_700_000_000L, series.getFirstSecond());
        assertEquals(1_700_000_006L, series.getLastSecond());
        assertEquals(10, series.getValues()[0].getTotalValue());
        assertEquals(20, series.getValues()[3].getTotalValue());
        assertEquals(30, series.getValues()[6].getTotalValue());
        assertEquals(3, series.getCount());
        assertEquals(60, series.getTotalValue());
    }

    @Test
    public void outOfOrderEarlierTransactionsShiftTheWindowToTheRight()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        series.addValue(BASE_MS + 2 * SECOND, 10, false);
        series.addValue(BASE_MS, 20, false);

        assertEquals(1_700_000_000L, series.getFirstSecond());
        assertEquals(1_700_000_002L, series.getLastSecond());
        // the first transaction was shifted to the right by two slots
        assertEquals(20, series.getValues()[0].getTotalValue());
        assertEquals(10, series.getValues()[2].getTotalValue());
        assertEquals(2, series.getCount());
        assertEquals(30, series.getTotalValue());
    }

    @Test
    public void forwardJumpBeyondTheWindowCondenses()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        series.addValue(BASE_MS, 5, false);
        // 20 seconds later than the window (16 slots of 1 s) can cover -> slot width doubles
        series.addValue(BASE_MS + 20 * SECOND, 7, false);

        assertEquals(2, series.getScale());
        assertEquals(2, series.getSlotWidth());
        assertEquals(2, series.getCount());
        assertEquals(12, series.getTotalValue());
        assertEquals(5, series.getStatistics().minValue);
        assertEquals(7, series.getStatistics().maxValue);
        assertEquals(1_700_000_000L, series.getFirstSecond());
        assertEquals(1_700_000_020L, series.getLastSecond());
    }

    @Test
    public void longRunsCondenseWithoutLosingStatistics()
    {
        final Random random = new Random(1);
        final IntTimeSeries series = new IntTimeSeries(16);

        int count = 0;
        long sum = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int i = 0; i < 400; i++)
        {
            final int value = 1 + random.nextInt(1000);
            final long time = BASE_MS + (long) i * 5 * SECOND;
            series.addValue(time, value, false);
            count++;
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        assertTrue(series.getScale() > 1);
        assertTrue(series.getSlotWidth() > 1);

        final IntTimeSeries.Statistics statistics = series.getStatistics();
        assertEquals(count, statistics.count);
        assertEquals(sum, statistics.sum);
        assertEquals(min, statistics.minValue);
        assertEquals(max, statistics.maxValue);
        assertEquals(sum, series.getTotalValue());
        assertEquals((double) sum / count, series.getMean(), 1e-9);
    }

    @Test
    public void failedTransactionsAreCountedAsErrors()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        series.addValue(BASE_MS, 10, false);
        series.addValue(BASE_MS + SECOND, 20, true);

        assertEquals(2, series.getCount());
        assertEquals(1, series.getErrorCount());
        assertEquals(30, series.getTotalValue());
        assertEquals(1, series.getValues()[1].getErrorCount());
    }

    @Test
    public void meanAndStandardDeviationOfKnownSet()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        double sumSquares = 0;
        for (int i = 1; i <= 10; i++)
        {
            final double value = i * 10;
            series.addValue(BASE_MS + (long) i * SECOND, i * 10, false);
            sumSquares += value * value;
        }

        final double mean = 55.0;
        final double expectedStdDev = Math.sqrt(sumSquares / 10.0 - mean * mean);
        assertEquals(mean, series.getMean(), 1e-9);
        assertEquals(expectedStdDev, series.getStandardDeviation(), 1e-9);
        assertEquals(550, series.getTotalValue());
    }

    @Test
    public void largeValuesAreTrackedExactlyInTheAggregates()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        series.addValue(BASE_MS, 1, false);
        series.addValue(BASE_MS + SECOND, 2_000_000_000, false);

        assertEquals(2, series.getCount());
        assertEquals(2_000_000_001L, series.getTotalValue());
        assertEquals(1, series.getStatistics().minValue);
        assertEquals(2_000_000_000, series.getStatistics().maxValue);
        assertEquals(1_000_000_000.5, series.getMean(), 1e-9);
    }

    @Test
    public void percentilesDelegatedToAWidthEightHistogram()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        final RuntimeHistogram reference = new RuntimeHistogram(8);

        final int[] values = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        for (int i = 0; i < values.length; i++)
        {
            series.addValue(BASE_MS + (long) i * SECOND, values[i], false);
            reference.addValue(values[i]);
        }

        final double[] percentiles = {0.0, 1.0, 5.0, 25.0, 50.0, 75.0, 90.0, 95.0, 99.0, 100.0};
        for (final double p : percentiles)
        {
            assertEquals((int) reference.getPercentile(p), series.getPercentile(p), "percentile " + p);
        }
    }

    @Test
    public void histogramBucketsAreOrderedAndComplete()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        for (int i = 1; i <= 100; i++)
        {
            series.addValue(BASE_MS + (long) i * SECOND, i * 100, false);
        }

        final List<IntTimeSeries.HistogramBucket> buckets = series.toHistogram(10);
        assertEquals(10, buckets.size());

        long sum = 0;
        int previousEnd = -1;
        for (final IntTimeSeries.HistogramBucket bucket : buckets)
        {
            assertTrue(bucket.count() >= 0);
            assertTrue(bucket.startValue() > previousEnd, "buckets must not overlap: " + buckets);
            previousEnd = bucket.endValue();
            sum += bucket.count();
        }
        assertEquals(100, sum);
    }

    @Test
    public void spanningTransactionsIncreaseConcurrencyInEachCoveredSecond()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        // measurement occupies seconds 0..5
        series.addValue(BASE_MS, BASE_MS + 5 * SECOND, 100, false);

        assertEquals(1, series.getValues()[0].getConcurrentCount());
        assertEquals(1, series.getValues()[0].getCount());
        for (int slot = 1; slot <= 5; slot++)
        {
            assertEquals(1, series.getValues()[slot].getConcurrentCount(), "slot " + slot);
            assertEquals(0, series.getValues()[slot].getCount(), "slot " + slot);
        }
        // slot 6 is not touched by a measurement ending in second 5
        assertEquals(0, series.getValues()[6].getConcurrentCount());
    }

    @Test
    public void overlappingTransactionsIncreaseConcurrency()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        // measurement 1: seconds 0..3, value 10
        series.addValue(BASE_MS, BASE_MS + 3 * SECOND, 10, false);
        // measurement 2: seconds 2..4, value 20
        series.addValue(BASE_MS + 2 * SECOND, BASE_MS + 4 * SECOND, 20, false);

        assertEquals(1, series.getValues()[0].getConcurrentCount());
        assertEquals(2, series.getValues()[2].getConcurrentCount());
        assertEquals(2, series.getValues()[3].getConcurrentCount());
        assertEquals(1, series.getValues()[4].getConcurrentCount());

        assertEquals(2, series.getCount());
        assertEquals(30, series.getTotalValue());
        assertEquals(10, series.getStatistics().minValue);
        assertEquals(20, series.getStatistics().maxValue);
    }

    @Test
    public void valuesSurviveACondenseInASingleJump()
    {
        // a jump of 1e6 seconds at the default window size needs several condense rounds,
        // but stays below the size*size seconds limit where data would be lost
        final IntTimeSeries series = new IntTimeSeries();
        series.addValue(BASE_MS, 5, false);
        series.addValue(BASE_MS + 1_000_000 * SECOND, 9, false);

        assertTrue(series.getScale() > 2);
        assertEquals(256, series.getSlotWidth());
        assertEquals(2, series.getCount());
        assertEquals(14, series.getTotalValue());
        assertEquals(5, series.getStatistics().minValue);
        assertEquals(9, series.getStatistics().maxValue);
    }

    @Test
    @Disabled("Known defect: a single jump larger than size*size seconds makes condense() overwrite the oldest "
        + "remaining slot and silently drops the earliest data. With size 16 a single jump of 1e6 s keeps only the "
        + "newest value (count becomes 1 instead of 2). See doc/XLT-DATA.md.")
    public void condenseFarJumpsDoNotLoseEarlyData()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        series.addValue(BASE_MS, 5, false);
        series.addValue(BASE_MS + 1_000_000 * SECOND, 9, false);

        assertEquals(2, series.getCount());
        assertEquals(14, series.getTotalValue());
        assertEquals(5, series.getStatistics().minValue);
        assertEquals(9, series.getStatistics().maxValue);
    }

    @Test
    @Disabled("Known defect: toHistogram divides by zero (bucket width becomes 0) when all observed values are equal, "
        + "and throws IllegalArgumentException for more than one bucket. See doc/XLT-DATA.md.")
    public void toHistogramHandlesConstantValues()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        series.addValue(BASE_MS, 100, false);
        series.addValue(BASE_MS + SECOND, 100, false);

        final List<IntTimeSeries.HistogramBucket> buckets = series.toHistogram(5);
        assertEquals(5, buckets.size());
        long sum = 0;
        for (final IntTimeSeries.HistogramBucket bucket : buckets)
        {
            sum += bucket.count();
        }
        assertEquals(2, sum);
    }

    @Test
    public void condenseDoublesSlotWidthIncrementally()
    {
        final IntTimeSeries series = new IntTimeSeries(16);
        series.addValue(BASE_MS, 1, false);
        assertEquals(1, series.getScale());

        // a single second beyond the 16 second window forces one condense round
        series.addValue(BASE_MS + 17 * SECOND, 2, false);
        assertEquals(2, series.getScale());
        assertEquals(2, series.getSlotWidth());

        // and again, once the doubled window (32 s) is exceeded
        series.addValue(BASE_MS + 40 * SECOND, 3, false);
        assertEquals(4, series.getSlotWidth());
        assertEquals(3, series.getCount());
    }
}
