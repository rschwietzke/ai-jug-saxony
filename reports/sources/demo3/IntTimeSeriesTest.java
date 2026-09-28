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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class IntTimeSeriesTest
{
    @Test
    void constructionRoundsStorageSizeUpToPowerOfTwo()
    {
        assertEquals(4_096, new IntTimeSeries().getSize());
        assertEquals(1, new IntTimeSeries(1).getSize());
        assertEquals(2, new IntTimeSeries(2).getSize());
        assertEquals(4, new IntTimeSeries(3).getSize());
        assertEquals(8, new IntTimeSeries(5).getSize());

        final IntTimeSeries series = new IntTimeSeries(3);
        assertEquals(1, series.getScale());
        assertEquals(1, series.getSlotWidth());
        assertEquals(4, series.getValues().length);
    }

    @Test
    void emptySeriesReturnsNeutralStatisticsAndHasNoTimeRange()
    {
        final IntTimeSeries series = new IntTimeSeries(4);
        final IntTimeSeries.Statistics statistics = series.getStatistics();

        assertEquals(0, series.getCount());
        assertEquals(0, series.getTotalValue());
        assertEquals(0, series.getErrorCount());
        assertEquals(0.0, series.getMean());
        assertEquals(0.0, series.getStandardDeviation());
        assertEquals(0, series.getPercentile(50));
        assertTrue(series.toHistogram(4).isEmpty());
        assertEquals(0, statistics.count);
        assertEquals(0, statistics.errorCount);
        assertEquals(0, statistics.sum);
        assertEquals(Integer.MAX_VALUE, statistics.minValue);
        assertEquals(Integer.MIN_VALUE, statistics.maxValue);
        assertThrows(IllegalStateException.class, series::getFirstSecond);
        assertThrows(IllegalStateException.class, series::getLastSecond);
        for (final IntTimeSeriesEntry entry : series.getValues())
        {
            assertEquals(0, entry.getCount());
        }
    }

    @Test
    void samplesInTheSameAndAdjacentSecondsProduceExactOverviewStatistics()
    {
        final IntTimeSeries series = new IntTimeSeries(8);
        series.addValue(10_100, 10_900, 2, false);
        series.addValue(10_200, 10_800, 4, true);
        series.addValue(11_000, 11_000, 6, false);

        assertEquals(10, series.getFirstSecond());
        assertEquals(11, series.getLastSecond());
        assertEquals(3, series.getCount());
        assertEquals(12, series.getTotalValue());
        assertEquals(1, series.getErrorCount());
        assertEquals(4.0, series.getMean());

        final IntTimeSeries.Statistics statistics = series.getStatistics();
        assertEquals(3, statistics.count);
        assertEquals(1, statistics.errorCount);
        assertEquals(12, statistics.sum);
        assertEquals(2, statistics.minValue);
        assertEquals(6, statistics.maxValue);

        final IntTimeSeriesEntry first = series.getValues()[0];
        assertEquals(2, first.getCount());
        assertEquals(2, first.getConcurrentCount());
        assertEquals(1, first.getErrorCount());
        assertEquals(6, first.getTotalValue());
        assertArrayEquals(new double[] {2, 4}, first.getValues());

        final IntTimeSeriesEntry second = series.getValues()[1];
        assertEquals(1, second.getCount());
        assertEquals(1, second.getConcurrentCount());
        assertEquals(6, second.getTotalValue());
    }

    @Test
    void aSpanningSampleCountsOnceButTracksEveryTouchedSecond()
    {
        final IntTimeSeries series = new IntTimeSeries(8);
        series.addValue(1_000, 3_500, 10, false);

        assertEquals(1, series.getFirstSecond());
        assertEquals(3, series.getLastSecond());
        assertEquals(1, series.getCount());
        assertEquals(10, series.getTotalValue());
        assertEquals(1, series.getValues()[0].getCount());
        assertEquals(1, series.getValues()[0].getConcurrentCount());
        assertEquals(0, series.getValues()[1].getCount());
        assertEquals(1, series.getValues()[1].getConcurrentCount());
        assertEquals(0, series.getValues()[2].getCount());
        assertEquals(1, series.getValues()[2].getConcurrentCount());
    }

    @Test
    void anEarlierSampleShiftsExistingSlotsWhenTheRangeStillFits()
    {
        final IntTimeSeries series = new IntTimeSeries(8);
        series.addValue(12_000, 12, false);
        series.addValue(10_000, 10, false);

        assertEquals(10, series.getFirstSecond());
        assertEquals(12, series.getLastSecond());
        assertEquals(2, series.getCount());
        assertEquals(10, series.getValues()[0].getTotalValue());
        assertEquals(0, series.getValues()[1].getCount());
        assertEquals(12, series.getValues()[2].getTotalValue());
    }

    @Test
    void forwardGrowthCondensesAdjacentSlotsWithoutLosingAggregates()
    {
        final IntTimeSeries series = new IntTimeSeries(4);
        for (int second = 0; second <= 4; second++)
        {
            series.addValue(second * 1_000L, second + 1, second == 2);
        }

        assertEquals(2, series.getScale());
        assertEquals(2, series.getSlotWidth());
        assertEquals(0, series.getFirstSecond());
        assertEquals(4, series.getLastSecond());
        assertEquals(5, series.getCount());
        assertEquals(15, series.getTotalValue());
        assertEquals(1, series.getErrorCount());

        final IntTimeSeriesEntry[] entries = series.getValues();
        assertEquals(2, entries[0].getCount());
        assertEquals(3, entries[0].getTotalValue());
        assertEquals(1, entries[0].getMinimumValue());
        assertEquals(2, entries[0].getMaximumValue());
        assertEquals(2, entries[1].getCount());
        assertEquals(7, entries[1].getTotalValue());
        assertEquals(1, entries[2].getCount());
        assertEquals(5, entries[2].getTotalValue());
    }

    @Test
    void aFarForwardSampleCanTriggerMultipleSafeCondensationLevels()
    {
        final IntTimeSeries series = new IntTimeSeries(8);
        series.addValue(0, 5, false);
        series.addValue(20_000, 7, false);

        assertEquals(3, series.getScale());
        assertEquals(4, series.getSlotWidth());
        assertEquals(0, series.getFirstSecond());
        assertEquals(20, series.getLastSecond());
        assertEquals(2, series.getCount());
        assertEquals(12, series.getTotalValue());
        assertEquals(5, series.getValues()[0].getTotalValue());
        assertEquals(7, series.getValues()[5].getTotalValue());
    }

    @Test
    void globalMomentsUsePopulationStandardDeviation()
    {
        final IntTimeSeries series = new IntTimeSeries(8);
        final int[] values = {2, 4, 4, 4, 5, 5, 7, 9};
        for (int second = 0; second < values.length; second++)
        {
            series.addValue(second * 1_000L, values[second], false);
        }

        assertEquals(8, series.getCount());
        assertEquals(40, series.getTotalValue());
        assertEquals(5.0, series.getMean());
        assertEquals(2.0, series.getStandardDeviation());
    }

    @Test
    void percentilesAndExportedHistogramUseEightValueInternalBuckets()
    {
        final IntTimeSeries series = new IntTimeSeries(8);
        final int[] values = {0, 8, 16, 24};
        for (int second = 0; second < values.length; second++)
        {
            series.addValue(second * 1_000L, values[second], false);
        }

        assertEquals(0, series.getPercentile(0));
        assertEquals(12, series.getPercentile(50));
        assertEquals(24, series.getPercentile(100));

        final List<IntTimeSeries.HistogramBucket> buckets = series.toHistogram(3);
        assertEquals(List.of(
            new IntTimeSeries.HistogramBucket(0, 7, 1),
            new IntTimeSeries.HistogramBucket(8, 15, 1),
            new IntTimeSeries.HistogramBucket(16, 24, 2)
        ), buckets);
        assertEquals("0, 7, 1", buckets.getFirst().toString());
    }

    @Test
    void valuesReturnsTheMutableBackingArray()
    {
        final IntTimeSeries series = new IntTimeSeries(4);

        assertSame(series.getValues(), series.getValues());
    }
}
