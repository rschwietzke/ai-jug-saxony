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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.xceptance.xlt.report.util.rework.IntTimeSeries.HistogramBucket;
import com.xceptance.xlt.report.util.rework.IntTimeSeries.Statistics;

/**
 * Tests for {@link IntTimeSeries}. Time-stamps are given in milliseconds and
 * converted to seconds internally; a slot width of one second means the test
 * uses {@code second * 1000L} to address a specific slot.
 */
class IntTimeSeriesTest
{
    // ----------------------------------------------------------------
    // Construction and sizing
    // ----------------------------------------------------------------

    @Test
    void defaultSizeIsRoundedToPowerOfTwo()
    {
        assertEquals(3600, IntTimeSeries.DEFAULT_SIZE);
        assertEquals(4096, new IntTimeSeries().getSize(), "default 3600 rounds up to 4096");
    }

    @Test
    void customSizeIsRoundedToPowerOfTwo()
    {
        assertEquals(1, new IntTimeSeries(1).getSize());
        assertEquals(2, new IntTimeSeries(2).getSize());
        assertEquals(4, new IntTimeSeries(3).getSize());
        assertEquals(8, new IntTimeSeries(8).getSize());
        assertEquals(1024, new IntTimeSeries(1000).getSize());
        assertEquals(4096, new IntTimeSeries(3600).getSize());
    }

    @Test
    void newSeriesStartsAtScaleOneWithFilledSlots()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);

        assertEquals(1, ts.getScale());
        assertEquals(1, ts.getSlotWidth());
        assertEquals(8, ts.getValues().length);
        for (final IntTimeSeriesEntry entry : ts.getValues())
        {
            assertNotNull(entry, "slots are pre-filled to avoid null checks");
            assertEquals(0, entry.getCount());
        }
    }

    // ----------------------------------------------------------------
    // Empty series
    // ----------------------------------------------------------------

    @Test
    void emptySeriesReportsZeroAggregates()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);

        assertEquals(0L, ts.getCount());
        assertEquals(0L, ts.getTotalValue());
        assertEquals(0L, ts.getErrorCount());
        assertEquals(0.0, ts.getMean());
        assertEquals(0.0, ts.getStandardDeviation());
        assertEquals(0, ts.getPercentile(50.0));
        assertTrue(ts.toHistogram(5).isEmpty());
    }

    @Test
    void emptySeriesRejectsFirstAndLastSecond()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);

        assertThrows(IllegalStateException.class, ts::getFirstSecond);
        assertThrows(IllegalStateException.class, ts::getLastSecond);
    }

    // ----------------------------------------------------------------
    // Single value
    // ----------------------------------------------------------------

    @Test
    void singleValueSetsFirstSecondAndAggregates()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(1000L, 1000L, 42, false); // second 1

        assertEquals(1L, ts.getFirstSecond());
        assertEquals(1L, ts.getLastSecond());
        assertEquals(1L, ts.getCount());
        assertEquals(42L, ts.getTotalValue());
        assertEquals(42.0, ts.getMean());
        assertEquals(0.0, ts.getStandardDeviation(), "single value has no deviation");
        assertEquals(1, ts.getValues()[0].getCount());
    }

    @Test
    void millisecondTimestampsAreTruncatedToSeconds()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(1999L, 1999L, 10, false); // 1.999 s -> second 1

        assertEquals(1L, ts.getFirstSecond());
        assertEquals(1, ts.getValues()[0].getCount());
    }

    @Test
    void threeArgAddValueDelegatesToSingleSecond()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(5000L, 7, false); // second 5

        assertEquals(5L, ts.getFirstSecond());
        assertEquals(5L, ts.getLastSecond());
        assertEquals(1L, ts.getCount());
        assertEquals(7L, ts.getTotalValue());
    }

    // ----------------------------------------------------------------
    // Sequential values within range (no condense)
    // ----------------------------------------------------------------

    @Test
    void sequentialValuesFillConsecutiveSlots()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        for (int s = 0; s < 8; s++)
        {
            ts.addValue(s * 1000L, s * 1000L, s, false);
        }

        assertEquals(1, ts.getScale());
        assertEquals(0L, ts.getFirstSecond());
        assertEquals(7L, ts.getLastSecond());
        assertEquals(8L, ts.getCount());
        assertEquals(28L, ts.getTotalValue()); // 0+1+...+7

        for (int s = 0; s < 8; s++)
        {
            assertEquals(1, ts.getValues()[s].getCount());
            assertEquals(s, ts.getValues()[s].getMaximumValue());
        }
    }

    // ----------------------------------------------------------------
    // Condense (slot width doubling)
    // ----------------------------------------------------------------

    @Test
    void overrunTriggersCondenseAndPreservesAggregates()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        // fill the whole window: seconds 0..7 with value == second
        for (int s = 0; s < 8; s++)
        {
            ts.addValue(s * 1000L, s * 1000L, s, false);
        }
        assertEquals(1, ts.getScale());

        // second 8 is outside [0..7], forcing one condense step
        ts.addValue(8000L, 8000L, 8, false);

        assertEquals(2, ts.getScale(), "scale doubled once");
        assertEquals(2, ts.getSlotWidth());
        assertEquals(0L, ts.getFirstSecond());
        assertEquals(8L, ts.getLastSecond());
        assertEquals(9L, ts.getCount(), "no measurement lost");
        assertEquals(36L, ts.getTotalValue(), "sum 0..8 preserved");
    }

    @Test
    void repeatedOverrunCondensesMultipleTimes()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(0L, 0L, 1, false); // second 0

        // a far-out second forces several condense rounds, but stays within
        // the range where the active window does not collapse to a single slot
        ts.addValue(20_000L, 20_000L, 1, false); // second 20

        assertEquals(2L, ts.getCount());
        assertEquals(2L, ts.getTotalValue());
        assertTrue(ts.getScale() > 1, "scale must have grown");
        assertEquals(0L, ts.getFirstSecond());
        assertEquals(20L, ts.getLastSecond());
    }

    // ----------------------------------------------------------------
    // shiftRight (earlier value than current first second)
    // ----------------------------------------------------------------

    @Test
    void earlierValueShiftsWindowRight()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(10_000L, 10_000L, 100, false); // second 10
        assertEquals(10L, ts.getFirstSecond());

        // a value 5 seconds earlier shifts the window
        ts.addValue(5_000L, 5_000L, 50, false); // second 5

        assertEquals(5L, ts.getFirstSecond());
        assertEquals(10L, ts.getLastSecond());
        assertEquals(2L, ts.getCount());
        assertEquals(150L, ts.getTotalValue());
        assertEquals(1, ts.getValues()[0].getCount(), "second 5 now at slot 0");
        assertEquals(50, ts.getValues()[0].getMaximumValue());
        assertEquals(1, ts.getValues()[5].getCount(), "second 10 moved to slot 5");
        assertEquals(100, ts.getValues()[5].getMaximumValue());
    }

    @Test
    void shiftRightCondensesFirstWhenOldDataWouldFallOff()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(50_000L, 50_000L, 10, false); // second 50 -> slot 0
        ts.addValue(57_000L, 57_000L, 20, false); // second 57 -> slot 7 (window full)

        // second 49 is earlier than firstSecond and the occupied window is
        // full, so shiftRight must condense before shifting
        ts.addValue(49_000L, 49_000L, 30, false);

        assertEquals(2, ts.getScale(), "condense doubled the slot width");
        assertEquals(2, ts.getSlotWidth());
        assertEquals(49L, ts.getFirstSecond());
        assertEquals(57L, ts.getLastSecond());
        assertEquals(3L, ts.getCount(), "no measurement lost");
        assertEquals(60L, ts.getTotalValue());
        assertEquals(30, ts.getValues()[0].getMaximumValue(), "second 49 at slot 0");
        assertEquals(10, ts.getValues()[1].getMaximumValue(), "second 50 shifted to slot 1");
        assertEquals(20, ts.getValues()[4].getMaximumValue(), "second 57 condensed to slot 4");
    }

    // ----------------------------------------------------------------
    // Concurrency tracking across a multi-second measurement
    // ----------------------------------------------------------------

    @Test
    void multiSecondMeasurementCountsConcurrencyPerSlot()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        // measurement spans seconds 1..4
        ts.addValue(1000L, 4000L, 50, false);

        // only the start second records a real measurement
        assertEquals(1L, ts.getCount());
        assertEquals(50L, ts.getTotalValue());
        assertEquals(1L, ts.getFirstSecond());
        assertEquals(4L, ts.getLastSecond());

        assertEquals(1, ts.getValues()[0].getCount());
        assertEquals(1, ts.getValues()[0].getConcurrentCount());
        // the following seconds only see concurrency, no measurement
        for (int slot = 1; slot <= 3; slot++)
        {
            assertEquals(0, ts.getValues()[slot].getCount());
            assertEquals(1, ts.getValues()[slot].getConcurrentCount());
        }
    }

    // ----------------------------------------------------------------
    // Error counting
    // ----------------------------------------------------------------

    @Test
    void failedMeasurementsAreCounted()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(0L, 0L, 10, false);
        ts.addValue(1000L, 1000L, 20, true);
        ts.addValue(2000L, 2000L, 30, true);
        ts.addValue(3000L, 3000L, 40, false);

        assertEquals(4L, ts.getCount());
        assertEquals(2L, ts.getErrorCount());
        assertEquals(100L, ts.getTotalValue());
    }

    // ----------------------------------------------------------------
    // Statistics
    // ----------------------------------------------------------------

    @Test
    void statisticsReflectAggregatedValues()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(0L, 0L, 10, false);
        ts.addValue(1000L, 1000L, 20, true);
        ts.addValue(2000L, 2000L, 30, false);

        final Statistics stat = ts.getStatistics();
        assertEquals(3L, stat.count);
        assertEquals(1L, stat.errorCount);
        assertEquals(60L, stat.sum);
        assertEquals(30, stat.maxValue);
        assertEquals(10, stat.minValue);
    }

    @Test
    void meanAndStandardDeviation()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(0L, 0L, 10, false);
        ts.addValue(1000L, 1000L, 20, false);
        ts.addValue(2000L, 2000L, 30, false);

        assertEquals(20.0, ts.getMean(), 1e-9);
        // sumOfSquares = 100 + 400 + 900 = 1400; var = 1400/3 - 400
        final double expectedStdDev = Math.sqrt(1400.0 / 3.0 - 20.0 * 20.0);
        assertEquals(expectedStdDev, ts.getStandardDeviation(), 1e-9);
    }

    // ----------------------------------------------------------------
    // Percentiles (delegated to the precision-8 runtime histogram)
    // ----------------------------------------------------------------

    @Test
    void percentileUsesHistogramWithPrecisionLoss()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(0L, 0L, 42, false);

        // histogram bucket size is 8 -> 42 lands in bucket 5 and reconstructs to 40
        assertEquals(40, ts.getPercentile(50.0));
        assertEquals(40, ts.getPercentile(0.0));
        assertEquals(40, ts.getPercentile(100.0));
    }

    @Test
    void percentileRejectsOutOfRange()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(0L, 0L, 10, false);

        assertThrows(IllegalArgumentException.class, () -> ts.getPercentile(-1.0));
        assertThrows(IllegalArgumentException.class, () -> ts.getPercentile(101.0));
    }

    // ----------------------------------------------------------------
    // toHistogram
    // ----------------------------------------------------------------

    @Test
    void toHistogramReturnsRequestedBucketCount()
    {
        final IntTimeSeries ts = new IntTimeSeries(16);
        for (int s = 0; s <= 10; s++)
        {
            ts.addValue(s * 1000L, s * 1000L, s * 10, false); // values 0..100
        }

        final List<HistogramBucket> buckets = ts.toHistogram(10);
        assertEquals(10, buckets.size());
        assertEquals(0, buckets.get(0).startValue(), "first bucket starts at 0");
        assertEquals(100, buckets.get(9).endValue(), "last bucket ends at max");
        for (final HistogramBucket b : buckets)
        {
            assertTrue(b.count() >= 0);
        }
    }

    @Test
    void histogramBucketToStringIsReadable()
    {
        final HistogramBucket bucket = new HistogramBucket(1, 2, 3);
        assertEquals("1, 2, 3", bucket.toString());
        assertEquals(1, bucket.startValue());
        assertEquals(2, bucket.endValue());
        assertEquals(3, bucket.count());
    }

    // ----------------------------------------------------------------
    // Randomized: sliding window keeps exact count/sum
    // ----------------------------------------------------------------

    @Test
    void slidingWindowPreservesCountAndSum()
    {
        final IntTimeSeries ts = new IntTimeSeries(64);
        final Random random = new Random(17);

        long expectedSum = 0;
        int expectedCount = 0;

        // advance second by second so the window slides and condenses,
        // but never collapses to a single active slot
        for (int second = 0; second < 500; second++)
        {
            final int value = random.nextInt(1000);
            ts.addValue(second * 1000L, second * 1000L, value, false);
            expectedSum += value;
            expectedCount++;
        }

        assertEquals(expectedCount, ts.getCount(), "every measurement is counted");
        assertEquals(expectedSum, ts.getTotalValue(), "sum is preserved across condense");
        assertEquals(0L, ts.getFirstSecond());
        // the last second is reported as the start of its (scaled) slot
        final int slotWidth = ts.getSlotWidth();
        assertEquals(499L - (499L % slotWidth), ts.getLastSecond());
    }

    @Test
    void valuesArrayIsExposedDirectly()
    {
        final IntTimeSeries ts = new IntTimeSeries(8);
        ts.addValue(0L, 0L, 5, false);

        final IntTimeSeriesEntry[] values = ts.getValues();
        assertEquals(8, values.length);
        assertEquals(1, values[0].getCount());
    }
}
