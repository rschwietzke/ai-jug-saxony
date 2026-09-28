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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The test suite for {@link IntTimeSeries}.
 *
 * <p>The class is a fixed size ring of per second buckets that never rejects data. It reacts to time
 * stamps it cannot hold in two ways, and everything interesting in this suite is about those two
 * reactions.
 *
 * <p><b>Condensing</b> happens when a time stamp arrives that is too far in the future for the
 * current window. Every two neighbouring slots are merged into one, the slot width doubles, and the
 * window covers twice as much wall clock time. The scale is an exponent plus one, so
 * {@code getScale() == 1} means one second per slot, {@code 2} means two seconds, {@code 3} means
 * four seconds - {@link IntTimeSeries#getSlotWidth()} is the readable version of the same thing.
 *
 * <p><b>Shifting</b> happens when a time stamp arrives before the current start of the window. The
 * whole array is moved to the right to make room, which is the price for accepting out of order
 * data.
 *
 * <p>Section G collects the cases where those two mechanisms break. They are real, reproducible and
 * reachable with ordinary input - a large forward gap silently drops everything that was recorded
 * before it, and a large backward gap throws an {@link ArrayIndexOutOfBoundsException} out of
 * {@code addValue}. The tests pin the current behaviour on purpose, including the thresholds where
 * it flips, so that a fix can be verified against them. {@code doc/XLT-DATA.md} describes what the
 * replacement should do instead.
 */
class IntTimeSeriesTest
{
    /**
     * A fixed seed keeps the randomized tests reproducible.
     */
    private static final long SEED = 20260906L;

    /**
     * Adds one sample at the given second.
     */
    private static void addAtSecond(final IntTimeSeries series, final long second, final int value, final boolean failed)
    {
        series.addValue(second * 1000L, value, failed);
    }

    /**
     * Adds a sample that spans from the start second to the end second.
     */
    private static void addSpan(final IntTimeSeries series, final long startSecond, final long endSecond, final int value)
    {
        series.addValue(startSecond * 1000L, endSecond * 1000L, value, false);
    }

    /**
     * The number of slots that actually carry a sample.
     */
    private static int usedSlots(final IntTimeSeries series)
    {
        int used = 0;
        for (final IntTimeSeriesEntry entry : series.getValues())
        {
            if (entry.getCount() > 0)
            {
                used++;
            }
        }

        return used;
    }

    /**
     * The sum over all slots, computed directly from the entries rather than through the statistics.
     */
    private static long sumOverSlots(final IntTimeSeries series)
    {
        long sum = 0;
        for (final IntTimeSeriesEntry entry : series.getValues())
        {
            sum += entry.getTotalValue();
        }

        return sum;
    }

    @Nested
    @DisplayName("A. Construction and the empty series")
    class ConstructionAndEmptyState
    {
        @ParameterizedTest
        @CsvSource(
        {
            "1, 1", "2, 2", "3, 4", "4, 4", "10, 16", "1000, 1024", "3600, 4096", "4096, 4096"
        })
        @DisplayName("T01 the requested size is rounded up to a power of two")
        void sizeIsRoundedUp(final int requested, final int effective)
        {
            final IntTimeSeries series = new IntTimeSeries(requested);

            assertEquals(effective, series.getSize());
            assertEquals(effective, series.getValues().length, "the slot array has exactly that many entries");
        }

        @Test
        @DisplayName("T02 the default size is one hour rounded up to 4096 slots")
        void defaultSize()
        {
            assertEquals(3600, IntTimeSeries.DEFAULT_SIZE, "the documented default is one hour of seconds");
            assertEquals(4096, new IntTimeSeries().getSize(), "but the array is the next power of two");
        }

        @Test
        @DisplayName("T03 all slots are pre-allocated, none of them is null")
        void slotsArePreAllocated()
        {
            final IntTimeSeries series = new IntTimeSeries(16);

            for (final IntTimeSeriesEntry entry : series.getValues())
            {
                assertNotNull(entry, "the code never checks for null, so the array must be filled");
                assertEquals(0, entry.getCount());
            }
        }

        @Test
        @DisplayName("T04 an empty series answers with zero and refuses to name a time range")
        void emptySeries()
        {
            final IntTimeSeries series = new IntTimeSeries(8);

            assertEquals(0L, series.getCount());
            assertEquals(0L, series.getTotalValue());
            assertEquals(0L, series.getErrorCount());
            assertEquals(0.0, series.getMean());
            assertEquals(0.0, series.getStandardDeviation());
            assertEquals(0, series.getPercentile(50.0));
            assertEquals(1, series.getScale(), "one second per slot");
            assertEquals(1, series.getSlotWidth());
            assertTrue(series.toHistogram(4).isEmpty(), "no values, no histogram");

            assertThrows(IllegalStateException.class, series::getFirstSecond);
            assertThrows(IllegalStateException.class, series::getLastSecond);
        }

        @Test
        @DisplayName("T05 an empty series reports the sentinel statistics unchanged")
        void emptyStatistics()
        {
            final IntTimeSeries.Statistics statistics = new IntTimeSeries(8).getStatistics();

            assertEquals(0L, statistics.count);
            assertEquals(0L, statistics.sum);
            assertEquals(0L, statistics.errorCount);
            assertEquals(Integer.MAX_VALUE, statistics.minValue, "nothing pulled the minimum down yet");
            assertEquals(Integer.MIN_VALUE, statistics.maxValue, "nothing pushed the maximum up yet");
        }
    }

    @Nested
    @DisplayName("B. Adding values in order")
    class AddInOrder
    {
        @Test
        @DisplayName("T06 the first value defines the start of the window")
        void firstValueDefinesTheWindow()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addAtSecond(series, 1_000, 100, false);

            assertEquals(1_000L, series.getFirstSecond());
            assertEquals(1_000L, series.getLastSecond());
            assertEquals(1L, series.getCount());
            assertEquals(100L, series.getTotalValue());
            assertEquals(100, series.getValues()[0].getMaximumValue(), "it landed in slot 0");
        }

        @Test
        @DisplayName("T07 consecutive seconds land in consecutive slots")
        void consecutiveSeconds()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addAtSecond(series, 1, 100, false);
            addAtSecond(series, 2, 200, true);
            addAtSecond(series, 3, 300, false);

            assertEquals(1L, series.getFirstSecond());
            assertEquals(3L, series.getLastSecond());
            assertEquals(3L, series.getCount());
            assertEquals(600L, series.getTotalValue());
            assertEquals(1L, series.getErrorCount());
            assertEquals(1, series.getScale(), "three seconds fit into eight slots, no condensing");

            final IntTimeSeriesEntry[] slots = series.getValues();
            assertEquals(100, slots[0].getMaximumValue());
            assertEquals(200, slots[1].getMaximumValue());
            assertEquals(300, slots[2].getMaximumValue());
            assertEquals(0, slots[3].getCount(), "the rest is still empty");
        }

        @Test
        @DisplayName("T08 several values in the same second share a slot")
        void sameSecond()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addAtSecond(series, 5, 10, false);
            addAtSecond(series, 5, 20, true);
            addAtSecond(series, 5, 30, false);

            assertEquals(1, usedSlots(series));
            assertEquals(3L, series.getCount());
            assertEquals(60L, series.getTotalValue());
            assertEquals(1L, series.getErrorCount());
            assertEquals(5L, series.getFirstSecond());
            assertEquals(5L, series.getLastSecond());
        }

        @Test
        @DisplayName("T09 a gap inside the window stays an empty slot")
        void gapsStayEmpty()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addAtSecond(series, 0, 10, false);
            addAtSecond(series, 3, 20, false);

            assertEquals(2, usedSlots(series));
            assertEquals(0, series.getValues()[1].getCount());
            assertEquals(0, series.getValues()[2].getCount());
            assertEquals(3L, series.getLastSecond());
        }

        @Test
        @DisplayName("T10 milliseconds inside a second are cut off")
        void millisecondsAreTruncated()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            series.addValue(1_000L, 10, false);
            series.addValue(1_999L, 20, false);
            series.addValue(2_000L, 30, false);

            assertEquals(2, usedSlots(series), "1000 and 1999 are both second 1");
            assertEquals(30L, series.getValues()[0].getTotalValue());
            assertEquals(30L, series.getValues()[1].getTotalValue());
        }

        @Test
        @DisplayName("T11 the two argument overload is the same as a span of length zero")
        void shortOverloadIsASpanOfOne()
        {
            final IntTimeSeries viaShort = new IntTimeSeries(8);
            final IntTimeSeries viaSpan = new IntTimeSeries(8);

            viaShort.addValue(5_000L, 42, true);
            viaSpan.addValue(5_000L, 5_000L, 42, true);

            assertEquals(viaShort.getCount(), viaSpan.getCount());
            assertEquals(viaShort.getFirstSecond(), viaSpan.getFirstSecond());
            assertEquals(viaShort.getLastSecond(), viaSpan.getLastSecond());
            assertEquals(viaShort.getValues()[0], viaSpan.getValues()[0]);
        }

        @Test
        @DisplayName("T12 a negative value is counted but contributes nothing to the sum")
        void negativeValue()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addAtSecond(series, 1, -50, false);

            assertEquals(1L, series.getCount());
            assertEquals(0L, series.getTotalValue(), "the entry clamps it to zero");
            assertEquals(0, series.getStatistics().minValue);
            assertEquals(0, series.getStatistics().maxValue);
        }
    }

    @Nested
    @DisplayName("C. Concurrency across a time span")
    class Concurrency
    {
        @Test
        @DisplayName("T13 a request that spans several seconds marks every second it touches")
        void spanMarksEverySecond()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addSpan(series, 1, 4, 50);

            final IntTimeSeriesEntry[] slots = series.getValues();

            assertEquals(1, slots[0].getCount(), "the sample itself is booked on the start second only");
            assertEquals(1, slots[0].getConcurrentCount());
            for (int slot = 1; slot <= 3; slot++)
            {
                assertEquals(0, slots[slot].getCount(), "slot " + slot + " has no sample");
                assertEquals(1, slots[slot].getConcurrentCount(), "but slot " + slot + " was busy");
            }
            assertEquals(0, slots[4].getConcurrentCount(), "the second after the end is free again");

            assertEquals(1L, series.getCount(), "one request, however long it took");
            assertEquals(50L, series.getTotalValue());
            assertEquals(4L, series.getLastSecond(), "the window reaches to the end of the request");
        }

        @Test
        @DisplayName("T14 overlapping requests add up their concurrency")
        void overlappingRequests()
        {
            final IntTimeSeries series = new IntTimeSeries(16);
            addSpan(series, 0, 4, 10);
            addSpan(series, 2, 6, 20);

            final IntTimeSeriesEntry[] slots = series.getValues();

            assertEquals(1, slots[0].getConcurrentCount());
            assertEquals(1, slots[1].getConcurrentCount());
            assertEquals(2, slots[2].getConcurrentCount(), "both requests are in flight here");
            assertEquals(2, slots[3].getConcurrentCount());
            assertEquals(2, slots[4].getConcurrentCount());
            assertEquals(1, slots[5].getConcurrentCount(), "the first one is done");
            assertEquals(1, slots[6].getConcurrentCount());
            assertEquals(0, slots[7].getConcurrentCount());
        }

        @Test
        @DisplayName("T15 a request that ends before it starts only books its start second")
        void endBeforeStart()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addSpan(series, 5, 1, 30);

            assertEquals(1L, series.getCount());
            assertEquals(30L, series.getTotalValue());
            assertEquals(5L, series.getFirstSecond());
            assertEquals(5L, series.getLastSecond(), "the concurrency loop never runs");
            assertEquals(1, usedSlots(series));
        }

        @Test
        @DisplayName("T16 a span that leaves the window condenses first and is then booked on the coarser slots")
        void spanBeyondTheWindow()
        {
            final IntTimeSeries series = new IntTimeSeries(4);
            addAtSecond(series, 0, 10, false);
            addSpan(series, 0, 5, 10);

            assertEquals(2, series.getScale(), "six seconds do not fit into four one second slots");
            assertEquals(2, series.getSlotWidth());
            assertEquals(2L, series.getCount());
            assertEquals(0L, series.getFirstSecond());
            assertEquals(4L, series.getLastSecond());
        }
    }

    @Nested
    @DisplayName("D. Condensing when time runs past the window")
    class Condensing
    {
        @Test
        @DisplayName("T17 filling the window exactly does not condense")
        void exactFitDoesNotCondense()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            for (int second = 0; second < 8; second++)
            {
                addAtSecond(series, second, 10, false);
            }

            assertEquals(1, series.getScale());
            assertEquals(8, usedSlots(series));
            assertEquals(8L, series.getCount());
            assertEquals(7L, series.getLastSecond());
        }

        @Test
        @DisplayName("T18 one second past the window doubles the slot width and pairs up the slots")
        void oneSecondTooFar()
        {
            final IntTimeSeries series = new IntTimeSeries(4);
            for (int second = 0; second < 4; second++)
            {
                addAtSecond(series, second, 10, false);
            }
            assertEquals(1, series.getScale());

            addAtSecond(series, 4, 10, false);

            assertEquals(2, series.getScale());
            assertEquals(2, series.getSlotWidth());
            assertEquals(5L, series.getCount(), "nothing was dropped");
            assertEquals(50L, series.getTotalValue());

            final IntTimeSeriesEntry[] slots = series.getValues();
            assertEquals(2, slots[0].getCount(), "seconds 0 and 1");
            assertEquals(2, slots[1].getCount(), "seconds 2 and 3");
            assertEquals(1, slots[2].getCount(), "second 4, alone in its slot so far");
            assertEquals(0, slots[3].getCount());
        }

        @Test
        @DisplayName("T19 a long run keeps condensing and keeps every sample")
        void longRunKeepsEverything()
        {
            final IntTimeSeries series = new IntTimeSeries(16);
            for (int second = 0; second < 1_000; second++)
            {
                addAtSecond(series, second, 7, second % 10 == 0);
            }

            assertEquals(1_000L, series.getCount(), "no sample was lost");
            assertEquals(7_000L, series.getTotalValue());
            assertEquals(100L, series.getErrorCount());
            assertEquals(64, series.getSlotWidth(), "1000 seconds into 16 slots means 64 seconds per slot");
            assertEquals(0L, series.getFirstSecond());
            assertEquals(sumOverSlots(series), series.getTotalValue(), "the statistics agree with the raw slots");
        }

        @Test
        @DisplayName("T20 the reported window always contains the samples that were added")
        void windowContainsTheData()
        {
            final IntTimeSeries series = new IntTimeSeries(64);
            for (int second = 100; second < 400; second++)
            {
                addAtSecond(series, second, 1, false);
            }

            assertEquals(100L, series.getFirstSecond());
            assertTrue(series.getLastSecond() <= 399L, "the last slot starts at or before the last second");
            assertTrue(series.getLastSecond() + series.getSlotWidth() > 399L, "but its slot still covers it");
            assertEquals(300L, series.getCount());
        }

        @Test
        @DisplayName("T21 the slot width is always a power of two and the scale is its exponent plus one")
        void scaleAndSlotWidthAgree()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            for (int second = 0; second < 500; second += 7)
            {
                addAtSecond(series, second, 1, false);

                final int width = series.getSlotWidth();
                assertEquals(1, Integer.bitCount(width), "the slot width must stay a power of two");
                assertEquals(1 << (series.getScale() - 1), width);
            }
        }

        @Test
        @DisplayName("T22 samples always end up in the slot the window arithmetic points at")
        void samplesLandInTheRightSlot()
        {
            final IntTimeSeries series = new IntTimeSeries(32);
            final Random random = new Random(SEED);

            final int[] seconds = new int[200];
            for (int i = 0; i < seconds.length; i++)
            {
                seconds[i] = random.nextInt(1_000);
            }
            java.util.Arrays.sort(seconds);

            for (final int second : seconds)
            {
                addAtSecond(series, second, 5, false);
            }

            final long first = series.getFirstSecond();
            final int width = series.getSlotWidth();
            final IntTimeSeriesEntry[] slots = series.getValues();

            for (int slot = 0; slot < slots.length; slot++)
            {
                if (slots[slot].getCount() > 0)
                {
                    final long slotStart = first + (long) slot * width;
                    assertTrue(slotStart >= first, "slot " + slot + " starts before the window");
                    assertTrue(slotStart <= series.getLastSecond(), "slot " + slot + " starts after the last used slot");
                }
            }
        }
    }

    @Nested
    @DisplayName("E. Shifting when data arrives out of order")
    class Shifting
    {
        @Test
        @DisplayName("T23 an earlier time stamp moves the window to the left and keeps the older data")
        void shiftsLeft()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addAtSecond(series, 10, 1, false);
            addAtSecond(series, 8, 2, false);

            assertEquals(8L, series.getFirstSecond());
            assertEquals(10L, series.getLastSecond());
            assertEquals(2L, series.getCount());
            assertEquals(3L, series.getTotalValue());

            final IntTimeSeriesEntry[] slots = series.getValues();
            assertEquals(2L, slots[0].getTotalValue(), "second 8 is the new slot 0");
            assertEquals(1L, slots[2].getTotalValue(), "second 10 moved two slots to the right");
        }

        @Test
        @DisplayName("T24 several shifts in a row keep everything in the right order")
        void repeatedShifts()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addAtSecond(series, 10, 1, false);
            addAtSecond(series, 8, 2, false);
            addAtSecond(series, 3, 3, false);

            assertEquals(3L, series.getFirstSecond());
            assertEquals(10L, series.getLastSecond());
            assertEquals(3L, series.getCount());
            assertEquals(6L, series.getTotalValue());

            final IntTimeSeriesEntry[] slots = series.getValues();
            assertEquals(3L, slots[0].getTotalValue(), "second 3");
            assertEquals(2L, slots[5].getTotalValue(), "second 8");
            assertEquals(1L, slots[7].getTotalValue(), "second 10");
        }

        @Test
        @DisplayName("T25 a shift that would push data out of the window condenses instead")
        void shiftCondensesWhenNeeded()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            for (int second = 0; second < 8; second++)
            {
                addAtSecond(series, second, 1, false);
            }
            assertEquals(1, series.getScale());

            addAtSecond(series, -5, 100, false);

            assertEquals(2, series.getScale(), "the window had to be widened to fit the new start");
            assertEquals(-5L, series.getFirstSecond());
            assertEquals(9L, series.getCount(), "all eight old samples plus the new one");
            assertEquals(108L, series.getTotalValue());
        }

        @Test
        @DisplayName("T26 out of order input reaches the same totals as sorted input")
        void orderDoesNotChangeTheTotals()
        {
            final Random random = new Random(SEED);
            final int[] seconds = new int[300];
            final int[] values = new int[seconds.length];

            for (int i = 0; i < seconds.length; i++)
            {
                seconds[i] = 500 + random.nextInt(200);
                values[i] = 1 + random.nextInt(100);
            }

            final IntTimeSeries shuffled = new IntTimeSeries(1024);
            for (int i = 0; i < seconds.length; i++)
            {
                addAtSecond(shuffled, seconds[i], values[i], false);
            }

            final int[] sortedSeconds = seconds.clone();
            java.util.Arrays.sort(sortedSeconds);
            final IntTimeSeries sorted = new IntTimeSeries(1024);
            long expectedSum = 0;
            for (int i = 0; i < seconds.length; i++)
            {
                expectedSum += values[i];
            }
            for (int i = 0; i < sortedSeconds.length; i++)
            {
                addAtSecond(sorted, sortedSeconds[i], 1, false);
            }

            assertEquals(seconds.length, shuffled.getCount());
            assertEquals(expectedSum, shuffled.getTotalValue());
            assertEquals(sorted.getFirstSecond(), shuffled.getFirstSecond());
            assertEquals(sorted.getScale(), shuffled.getScale());
        }
    }

    @Nested
    @DisplayName("F. The derived statistics")
    class Statistics
    {
        @Test
        @DisplayName("T27 count, sum, error count, minimum and maximum are collected over all slots")
        void overviewData()
        {
            final IntTimeSeries series = new IntTimeSeries(16);
            addAtSecond(series, 0, 100, false);
            addAtSecond(series, 1, 300, true);
            addAtSecond(series, 2, 200, false);

            final IntTimeSeries.Statistics statistics = series.getStatistics();

            assertEquals(3L, statistics.count);
            assertEquals(600L, statistics.sum);
            assertEquals(1L, statistics.errorCount);
            assertEquals(100, statistics.minValue);
            assertEquals(300, statistics.maxValue);

            assertEquals(statistics.count, series.getCount());
            assertEquals(statistics.sum, series.getTotalValue());
            assertEquals(statistics.errorCount, series.getErrorCount());
        }

        @Test
        @DisplayName("T28 the mean is the sum over the count")
        void mean()
        {
            final IntTimeSeries series = new IntTimeSeries(16);
            addAtSecond(series, 0, 100, false);
            addAtSecond(series, 1, 200, false);
            addAtSecond(series, 2, 300, false);

            assertEquals(200.0, series.getMean());
        }

        @Test
        @DisplayName("T29 the standard deviation is the population sigma of the samples")
        void standardDeviation()
        {
            final IntTimeSeries series = new IntTimeSeries(16);
            addAtSecond(series, 0, 100, false);
            addAtSecond(series, 1, 200, false);
            addAtSecond(series, 2, 300, false);

            // sqrt(140000/3 - 200^2)
            assertEquals(Math.sqrt(140_000.0 / 3.0 - 40_000.0), series.getStandardDeviation(), 1e-9);

            assertEquals(0.0, new IntTimeSeries(4).getStandardDeviation(), "no values, no deviation");
            final IntTimeSeries single = new IntTimeSeries(4);
            addAtSecond(single, 0, 42, false);
            assertEquals(0.0, single.getStandardDeviation(), 1e-9, "a single value has no spread");
        }

        @Test
        @DisplayName("T30 the percentile comes from the histogram and is rounded down to its bucket")
        void percentile()
        {
            final IntTimeSeries series = new IntTimeSeries(16);
            addAtSecond(series, 0, 100, false);
            addAtSecond(series, 1, 200, false);
            addAtSecond(series, 2, 300, false);

            assertEquals(96, series.getPercentile(0.0), "the internal histogram groups eight values per bucket");
            assertEquals(200, series.getPercentile(50.0));
            assertEquals(296, series.getPercentile(100.0), "p100 is the floor of the bucket that holds 300");
        }

        @Test
        @DisplayName("T31 the percentiles of a large sample are close to the real ones")
        void percentileAccuracy()
        {
            final IntTimeSeries series = new IntTimeSeries(4096);
            for (int i = 0; i < 1_000; i++)
            {
                addAtSecond(series, i, i, false);
            }

            // eight values share a bucket, so the answer is at most 7 below the true percentile
            assertTrue(Math.abs(series.getPercentile(50.0) - 500) <= 8, "p50 was " + series.getPercentile(50.0));
            assertTrue(Math.abs(series.getPercentile(90.0) - 900) <= 8, "p90 was " + series.getPercentile(90.0));
            assertTrue(Math.abs(series.getPercentile(99.0) - 990) <= 8, "p99 was " + series.getPercentile(99.0));
        }

        @Test
        @DisplayName("T32 the statistics survive condensing")
        void statisticsSurviveCondensing()
        {
            final IntTimeSeries series = new IntTimeSeries(16);
            long expectedSum = 0;
            for (int second = 0; second < 500; second++)
            {
                final int value = 10 + (second % 40);
                addAtSecond(series, second, value, second % 25 == 0);
                expectedSum += value;
            }

            assertTrue(series.getSlotWidth() > 1, "this run must have condensed");
            assertEquals(500L, series.getCount());
            assertEquals(expectedSum, series.getTotalValue());
            assertEquals(20L, series.getErrorCount());
            assertEquals(10, series.getStatistics().minValue);
            assertEquals(49, series.getStatistics().maxValue);
            assertEquals((double) expectedSum / 500.0, series.getMean(), 1e-9);
        }

        @Test
        @DisplayName("T33 the histogram splits the value range into buckets")
        void histogram()
        {
            final IntTimeSeries series = new IntTimeSeries(16);
            addAtSecond(series, 0, 100, false);
            addAtSecond(series, 1, 200, false);
            addAtSecond(series, 2, 300, false);

            final List<IntTimeSeries.HistogramBucket> buckets = series.toHistogram(4);

            assertEquals(4, buckets.size());
            assertEquals(new IntTimeSeries.HistogramBucket(0, 149, 1), buckets.get(0), "the first bucket always starts at zero");
            assertEquals(new IntTimeSeries.HistogramBucket(150, 199, 0), buckets.get(1));
            assertEquals(new IntTimeSeries.HistogramBucket(200, 249, 1), buckets.get(2));
            assertEquals(new IntTimeSeries.HistogramBucket(250, 300, 1), buckets.get(3), "the last bucket ends at the maximum");
        }

        @Test
        @DisplayName("T34 the histogram bucket is a record with a compact toString")
        void histogramBucketRecord()
        {
            final IntTimeSeries.HistogramBucket bucket = new IntTimeSeries.HistogramBucket(1, 2, 3);

            assertEquals(1, bucket.startValue());
            assertEquals(2, bucket.endValue());
            assertEquals(3, bucket.count());
            assertEquals("1, 2, 3", bucket.toString());
            assertEquals(new IntTimeSeries.HistogramBucket(1, 2, 3), bucket);
        }

        @Test
        @DisplayName("T35 asking for no buckets returns nothing instead of dividing by zero")
        void histogramWithoutBuckets()
        {
            final IntTimeSeries series = new IntTimeSeries(16);
            addAtSecond(series, 0, 100, false);
            addAtSecond(series, 1, 200, false);

            assertTrue(series.toHistogram(0).isEmpty());
        }
    }

    @Nested
    @DisplayName("G. Documented defects - reachable with ordinary input")
    class Defects
    {
        @Test
        @DisplayName("T36 DEFECT a forward gap of more than twice the window silently drops everything before it")
        void largeForwardGapDropsData()
        {
            // condensing runs in a loop that halves the number of live slots every round. Once that
            // number reaches one, the round has no pair left to merge and overwrites the slot with a
            // fresh entry instead - everything that was recorded so far is gone.
            final IntTimeSeries series = new IntTimeSeries(4);
            addAtSecond(series, 0, 7, false);
            addAtSecond(series, 16, 9, false);

            assertEquals(1L, series.getCount(), "the sample from second 0 disappeared");
            assertEquals(9L, series.getTotalValue());

            // and it happens with any size, just later - the threshold is size * size seconds,
            // which is where the required slot width grows past the number of slots
            for (final int size : new int[]
            {
                8, 16, 32
            })
            {
                final IntTimeSeries bigger = new IntTimeSeries(size);
                addAtSecond(bigger, 0, 7, false);
                addAtSecond(bigger, size * size - 1, 9, false);
                assertEquals(2L, bigger.getCount(), "one second below the threshold, size " + size);

                final IntTimeSeries lost = new IntTimeSeries(size);
                addAtSecond(lost, 0, 7, false);
                addAtSecond(lost, size * size, 9, false);
                assertEquals(1L, lost.getCount(), "at the threshold of size * size, size " + size);
            }
        }

        @Test
        @DisplayName("T37 DEFECT the threshold where data starts to disappear is a gap of size * size seconds")
        void forwardGapThreshold()
        {
            for (final int gap : new int[]
            {
                4, 8, 15
            })
            {
                final IntTimeSeries series = new IntTimeSeries(4);
                addAtSecond(series, 0, 7, false);
                addAtSecond(series, gap, 9, false);
                assertEquals(2L, series.getCount(), "a gap of " + gap + " is still handled correctly");
            }

            for (final int gap : new int[]
            {
                16, 32, 1_000
            })
            {
                final IntTimeSeries series = new IntTimeSeries(4);
                addAtSecond(series, 0, 7, false);
                addAtSecond(series, gap, 9, false);
                assertEquals(1L, series.getCount(), "a gap of " + gap + " loses the older sample");
            }
        }

        @Test
        @DisplayName("T38 DEFECT a series of size one loses its data on the second value")
        void sizeOneLosesData()
        {
            final IntTimeSeries series = new IntTimeSeries(1);
            addAtSecond(series, 0, 5, false);
            assertEquals(1L, series.getCount());

            addAtSecond(series, 1, 6, false);
            assertEquals(1L, series.getCount(), "the first sample was overwritten, not merged");
            assertEquals(6L, series.getTotalValue());
        }

        @Test
        @DisplayName("T39 DEFECT the sum of squares is not condensed with the rest, so sigma drifts after data loss")
        void standardDeviationDriftsAfterDataLoss()
        {
            final IntTimeSeries series = new IntTimeSeries(4);
            addAtSecond(series, 0, 7, false);
            addAtSecond(series, 16, 9, false);

            // count and sum only know about the surviving sample, the sum of squares knows about both
            assertEquals(1L, series.getCount());
            assertEquals(9.0, series.getMean());
            assertEquals(7.0, series.getStandardDeviation(), 1e-9, "a single remaining value cannot have a spread of 7");
        }

        @Test
        @DisplayName("T40 DEFECT a backward gap of more than twice the size throws out of addValue")
        void largeBackwardGapThrows()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addAtSecond(series, 100, 1, false);

            final ArrayIndexOutOfBoundsException exception =
                assertThrows(ArrayIndexOutOfBoundsException.class, () -> addAtSecond(series, 0, 2, false));
            assertTrue(exception.getMessage().contains("negative"), "it is a negative arraycopy length: " + exception.getMessage());
        }

        @Test
        @DisplayName("T41 DEFECT the backward gap threshold is 2 * size + 2 seconds")
        void backwardGapThreshold()
        {
            for (final int start : new int[]
            {
                8, 16, 17
            })
            {
                final IntTimeSeries series = new IntTimeSeries(8);
                addAtSecond(series, start, 1, false);
                addAtSecond(series, 0, 2, false);

                assertEquals(0L, series.getFirstSecond(), "a start at " + start + " is still survivable");
                // up to twice the size the shift condenses first and keeps both samples, beyond that
                // it only stops throwing because the older sample is dropped on the way
                assertEquals(start <= 8 ? 2L : 1L, series.getCount(), "a start at " + start);
            }

            for (final int start : new int[]
            {
                18, 20, 100
            })
            {
                final IntTimeSeries series = new IntTimeSeries(8);
                addAtSecond(series, start, 1, false);
                assertThrows(ArrayIndexOutOfBoundsException.class, () -> addAtSecond(series, 0, 2, false),
                             "a start at " + start + " must throw");
            }
        }

        @Test
        @DisplayName("T42 DEFECT a backward shift just below the threshold drops the older sample")
        void backwardShiftDropsData()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addAtSecond(series, 16, 1, false);
            addAtSecond(series, 0, 2, false);

            assertEquals(1L, series.getCount(), "the sample at second 16 was overwritten during the shift");
            assertEquals(2L, series.getTotalValue());
        }

        @Test
        @DisplayName("T43 DEFECT toHistogram throws when all values are the same and more than two buckets are asked for")
        void histogramThrowsForAFlatSeries()
        {
            final IntTimeSeries series = new IntTimeSeries(8);
            addAtSecond(series, 1, 100, false);
            addAtSecond(series, 2, 100, false);

            // min == max makes the bucket width zero, so every bucket but the last gets an end that
            // is one below its start and the range check inside the histogram rejects it
            assertThrows(IllegalArgumentException.class, () -> series.toHistogram(4));
            assertThrows(IllegalArgumentException.class, () -> series.toHistogram(3));

            assertEquals(List.of(new IntTimeSeries.HistogramBucket(0, 100, 2)), series.toHistogram(1), "one bucket still works");
        }

        @Test
        @DisplayName("T44 DEFECT toHistogram throws when the buckets get narrower than one value")
        void histogramThrowsForTooManyBuckets()
        {
            final IntTimeSeries series = new IntTimeSeries(128);
            for (int i = 0; i < 100; i++)
            {
                addAtSecond(series, i, i, false);
            }

            assertThrows(IllegalArgumentException.class, () -> series.toHistogram(200));
            assertThrows(IllegalArgumentException.class, () -> series.toHistogram(-1),
                         "and a negative bucket count reaches the ArrayList constructor unchecked");
        }

        @Test
        @DisplayName("T45 DEFECT the histogram bucket counts add up to more than the number of samples")
        void histogramDoubleCountsValues()
        {
            final IntTimeSeries series = new IntTimeSeries(128);
            for (int i = 0; i < 100; i++)
            {
                addAtSecond(series, i, i, false);
            }

            long total = 0;
            for (final IntTimeSeries.HistogramBucket bucket : series.toHistogram(5))
            {
                total += bucket.count();
            }

            // the internal histogram counts in buckets of eight, so a boundary that falls inside one
            // of those buckets makes both neighbours report all of its values
            assertEquals(132L, total, "100 samples are reported as 132 across five buckets");
            assertEquals(100L, series.getCount(), "while the series itself knows the real number");
        }

        @Test
        @DisplayName("T46 DEFECT the first histogram bucket always starts at zero, whatever the minimum is")
        void histogramFirstBucketIgnoresTheMinimum()
        {
            final IntTimeSeries series = new IntTimeSeries(16);
            addAtSecond(series, 0, 1_000, false);
            addAtSecond(series, 1, 2_000, false);

            final List<IntTimeSeries.HistogramBucket> buckets = series.toHistogram(2);

            assertEquals(0, buckets.get(0).startValue(), "the range 0..999 is empty but gets its own bucket anyway");
            assertEquals(1_499, buckets.get(0).endValue());
            assertEquals(1_500, buckets.get(1).startValue());
        }

        @Test
        @DisplayName("T47 DEFECT getValues hands out the live slot array")
        void getValuesExposesInternals()
        {
            final IntTimeSeries series = new IntTimeSeries(4);
            addAtSecond(series, 1, 5, false);

            series.getValues()[0].updateValue(1_000, true);

            assertEquals(2L, series.getCount(), "a caller just added a sample through the back door");
            assertEquals(1_005L, series.getTotalValue());
            assertEquals(1L, series.getErrorCount());
            assertEquals(0, series.getPercentile(100.0), "but the histogram behind the percentiles never saw it");
        }

        @Test
        @DisplayName("T48 DEFECT a size of zero produces a series that cannot take a value")
        void sizeZero()
        {
            final IntTimeSeries series = new IntTimeSeries(0);

            assertEquals(0, series.getSize());
            assertEquals(0, series.getValues().length);
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> addAtSecond(series, 0, 1, false));
        }

        @ParameterizedTest
        @ValueSource(ints =
        {
            -1, -1_000, -1 << 20
        })
        @DisplayName("T49 DEFECT a negative size is rounded to zero instead of being rejected")
        void negativeSize(final int size)
        {
            final IntTimeSeries series = new IntTimeSeries(size);

            assertEquals(0, series.getSize());
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> addAtSecond(series, 0, 1, false));
        }

        @Test
        @DisplayName("T50 DEFECT a size above 2^30 overflows into a negative array size")
        void hugeSize()
        {
            assertThrows(NegativeArraySizeException.class, () -> new IntTimeSeries(Integer.MAX_VALUE));
            assertThrows(NegativeArraySizeException.class, () -> new IntTimeSeries((1 << 30) + 1));
            assertThrows(NegativeArraySizeException.class, () -> new IntTimeSeries(Integer.MIN_VALUE),
                         "the smallest int wraps back to itself instead of to zero");
        }

        @Test
        @DisplayName("T52 DEFECT a request longer than the window throws while the window is being opened")
        void longSpanOnAFreshSeriesThrows()
        {
            // the overflow check sits in the else branch of the shift check. The very first value
            // always takes the shift branch, so nothing condenses and the concurrency loop runs
            // straight off the end of the slot array.
            final IntTimeSeries series = new IntTimeSeries(4);

            assertThrows(ArrayIndexOutOfBoundsException.class, () -> addSpan(series, 0, 5, 10));

            // the same happens for any request that is at least as long as the window
            final IntTimeSeries exact = new IntTimeSeries(4);
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> addSpan(exact, 0, 4, 10));

            final IntTimeSeries stillFine = new IntTimeSeries(4);
            addSpan(stillFine, 0, 3, 10);
            assertEquals(1L, stillFine.getCount(), "a request that exactly fills the window is still fine");

            // and it is not limited to the first value either, any backward shift skips the check
            final IntTimeSeries shifted = new IntTimeSeries(8);
            addAtSecond(shifted, 10, 1, false);
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> addSpan(shifted, 9, 30, 1),
                         "the shift branch was taken, so the span was never checked against the window");
        }

        @Test
        @DisplayName("T51 DEFECT the concurrency loop steps by the scale, not by the slot width")
        void concurrencyLoopUsesTheWrongStep()
        {
            // at scale 3 a slot is four seconds wide but the loop walks in steps of three, so a long
            // request bumps some slots twice and the concurrency of those slots is too high
            final IntTimeSeries series = new IntTimeSeries(4);
            for (int second = 0; second < 8; second++)
            {
                addAtSecond(series, second, 1, false);
            }
            assertEquals(2, series.getScale());

            addSpan(series, 8, 20, 5);

            assertEquals(4, series.getScale());
            assertEquals(3, series.getValues()[1].getConcurrentCount(),
                         "one request bumped this slot more than once because the step and the slot width disagree");
        }
    }
}
