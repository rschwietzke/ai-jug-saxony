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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("IntTimeSeries Tests")
class IntTimeSeriesTest
{
    @Nested
    @DisplayName("Initial State & Construction")
    class ConstructionTest
    {
        @Test
        @DisplayName("Default constructor sets power of two capacity (4096 for 3600)")
        void defaultConstructor()
        {
            IntTimeSeries ts = new IntTimeSeries();
            assertThat(ts.getSize()).isEqualTo(4096);
            assertThat(ts.getScale()).isEqualTo(1);
            assertThat(ts.getSlotWidth()).isEqualTo(1);
            assertThat(ts.getCount()).isEqualTo(0L);
            assertThat(ts.getTotalValue()).isEqualTo(0L);
            assertThat(ts.getErrorCount()).isEqualTo(0L);
            assertThat(ts.getMean()).isEqualTo(0.0);
            assertThat(ts.getStandardDeviation()).isEqualTo(0.0);
            assertThat(ts.getValues()).hasSize(4096);
            List<IntTimeSeries.HistogramBucket> emptyHist = ts.toHistogram(10);
            assertThat(emptyHist).isNotNull();
            assertThat(emptyHist).isInstanceOf(java.util.ArrayList.class);
            assertThat(emptyHist).isEmpty();
            // Verify mutability of returned ArrayList (kills Collections.emptyList mutator)
            emptyHist.add(new IntTimeSeries.HistogramBucket(0, 0, 0));
            assertThat(emptyHist).hasSize(1);

            assertThatThrownBy(ts::getFirstSecond)
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(ts::getLastSecond)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Custom size rounds up to power of two")
        void customSize()
        {
            IntTimeSeries ts10 = new IntTimeSeries(10);
            assertThat(ts10.getSize()).isEqualTo(16);

            IntTimeSeries ts64 = new IntTimeSeries(64);
            assertThat(ts64.getSize()).isEqualTo(64);

            IntTimeSeries ts65 = new IntTimeSeries(65);
            assertThat(ts65.getSize()).isEqualTo(128);
        }
    }

    @Nested
    @DisplayName("Adding Values & Metric Aggregations")
    class AddValueTest
    {
        @Test
        @DisplayName("Single value added initializes first and last second")
        void singleValue()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long timeMs = 1_000_000L; // second 1000
            ts.addValue(timeMs, 50, false);

            assertThat(ts.getFirstSecond()).isEqualTo(1000L);
            assertThat(ts.getLastSecond()).isEqualTo(1000L);
            assertThat(ts.getCount()).isEqualTo(1L);
            assertThat(ts.getTotalValue()).isEqualTo(50L);
            assertThat(ts.getErrorCount()).isEqualTo(0L);
            assertThat(ts.getMean()).isEqualTo(50.0);
            assertThat(ts.getStandardDeviation()).isEqualTo(0.0);

            // Note: RuntimeHistogram uses precision=8 (power of two), quantizing 50 to (50 >> 3) << 3 = 48
            assertThat(ts.getPercentile(50.0)).isEqualTo(48);

            IntTimeSeries.Statistics stats = ts.getStatistics();
            assertThat(stats.count).isEqualTo(1L);
            assertThat(stats.sum).isEqualTo(50L);
            assertThat(stats.minValue).isEqualTo(50);
            assertThat(stats.maxValue).isEqualTo(50);
            assertThat(stats.errorCount).isEqualTo(0L);
        }

        @Test
        @DisplayName("Adding values at exact boundary positions")
        void exactBoundaryPositions()
        {
            // size = 16 (indices 0..15)
            IntTimeSeries ts = new IntTimeSeries(16);
            long baseMs = 1000_000L; // second 1000
            ts.addValue(baseMs, 10, false); // startSecond = 1000, firstSecond = 1000

            // Adding same second (startSecond - firstSecond = 0)
            ts.addValue(baseMs, 20, false);
            assertThat(ts.getCount()).isEqualTo(2L);

            // Adding exact max position within capacity: second 1015 (endSecond = 1015 < 1000 + 16 = 1016)
            ts.addValue(baseMs + 15_000L, 30, false);
            assertThat(ts.getScale()).isEqualTo(1); // did not condense!
            assertThat(ts.getLastSecond()).isEqualTo(1015L);

            // Adding exact boundary that triggers condensation: second 1016 (endSecond = 1016 >= 1000 + 16)
            ts.addValue(baseMs + 16_000L, 40, false);
            assertThat(ts.getScale()).isEqualTo(2); // condensed!
        }

        @Test
        @DisplayName("Adding values spanning across a time duration tracks concurrency")
        void durationConcurrency()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long startMs = 1000_000L; // second 1000
            long endMs = 1003_000L;   // second 1003
            ts.addValue(startMs, endMs, 100, false);

            assertThat(ts.getFirstSecond()).isEqualTo(1000L);
            assertThat(ts.getLastSecond()).isEqualTo(1003L);

            IntTimeSeriesEntry[] entries = ts.getValues();
            // Slot 0 (second 1000) has count=1, concurrentCount=1
            assertThat(entries[0].getCount()).isEqualTo(1);
            assertThat(entries[0].getConcurrentCount()).isEqualTo(1);
            // Slots 1, 2, 3 (seconds 1001, 1002, 1003) have concurrentCount=1, count=0
            assertThat(entries[1].getCount()).isEqualTo(0);
            assertThat(entries[1].getConcurrentCount()).isEqualTo(1);
            assertThat(entries[2].getCount()).isEqualTo(0);
            assertThat(entries[2].getConcurrentCount()).isEqualTo(1);
            assertThat(entries[3].getCount()).isEqualTo(0);
            assertThat(entries[3].getConcurrentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Aggregates mean, standard deviation, and error count accurately")
        void statisticsAndStdDev()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long base = 1000_000L;
            // Add 10, 20, 30 at second 1000, 1001, 1002
            ts.addValue(base, 10, false);
            ts.addValue(base + 1000, 20, true);
            ts.addValue(base + 2000, 30, false);

            assertThat(ts.getCount()).isEqualTo(3L);
            assertThat(ts.getTotalValue()).isEqualTo(60L);
            assertThat(ts.getErrorCount()).isEqualTo(1L);
            assertThat(ts.getMean()).isEqualTo(20.0);

            // Mean = 20. Values = 10, 20, 30.
            // Pop std dev: sqrt(((10-20)^2 + (20-20)^2 + (30-20)^2) / 3) = sqrt(200/3) = ~8.1649658
            assertThat(ts.getStandardDeviation()).isCloseTo(Math.sqrt(200.0 / 3.0), within(0.001));

            IntTimeSeries.Statistics stats = ts.getStatistics();
            assertThat(stats.minValue).isEqualTo(10);
            assertThat(stats.maxValue).isEqualTo(30);
            assertThat(stats.errorCount).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Streaming Condensation (Scale-Up on Forward Span)")
    class CondensationTest
    {
        @Test
        @DisplayName("Overrunning capacity to the right triggers condense and doubles scale")
        void condenseOnForwardSpan()
        {
            // Capacity = 16 slots (seconds 0 to 15 at scale 1)
            IntTimeSeries ts = new IntTimeSeries(16);
            long startMs = 1000_000L; // second 1000
            ts.addValue(startMs, 10, false);
            ts.addValue(startMs + 1000, 20, false); // second 1001

            assertThat(ts.getScale()).isEqualTo(1);
            assertThat(ts.getSlotWidth()).isEqualTo(1);

            // Add value at second 1016 (span is 17 seconds > 16 slots capacity)
            ts.addValue(startMs + 16_000L, 50, false);

            // Capacity at scale 2 is 16 * 2 = 32 seconds
            assertThat(ts.getScale()).isEqualTo(2);
            assertThat(ts.getSlotWidth()).isEqualTo(2);
            assertThat(ts.getCount()).isEqualTo(3L);
            assertThat(ts.getTotalValue()).isEqualTo(80L);

            // First slot should now combine seconds 1000 and 1001
            IntTimeSeriesEntry[] entries = ts.getValues();
            assertThat(entries[0].getCount()).isEqualTo(2);
            assertThat(entries[0].getTotalValue()).isEqualTo(30L);
            // lastSecond is firstSecond (1000) + expandToSeconds(lastPosUsed = 8) = 1000 + 16 = 1016
            assertThat(ts.getLastSecond()).isEqualTo(1016L);
        }

        @Test
        @DisplayName("Condensation across multiple rounds verifies scale increments and boundary condition")
        void multiRoundCondensation()
        {
            // size = 16
            IntTimeSeries ts = new IntTimeSeries(16);
            long startMs = 1000_000L; // second 1000
            ts.addValue(startMs, 10, false);
            ts.addValue(startMs + 1000L, 20, false); // second 1001

            // Exceed by exactly 2 rounds: second 1032
            // Round 1: scale 2, expandToSeconds(16) = 32. 1032 >= 1000 + 32 -> continues to round 2!
            // Round 2: scale 3, expandToSeconds(16) = 64. 1032 < 1000 + 64 -> stops!
            ts.addValue(startMs + 32_000L, 50, false);
            assertThat(ts.getScale()).isEqualTo(3);
            assertThat(ts.getSlotWidth()).isEqualTo(4);
            assertThat(ts.getCount()).isEqualTo(3L);
            assertThat(ts.getTotalValue()).isEqualTo(80L);
            // lastPosUsed after round 1 was 16>>1 = 8; after round 2: pos of 1032 is (1032-1000)>>2 = 32>>2 = 8
            // expandToSeconds(8) at scale 3 = 8 << 2 = 32. lastSecond = 1000 + 32 = 1032.
            assertThat(ts.getLastSecond()).isEqualTo(1032L);
        }

        @Test
        @DisplayName("Condense when lastPosUsed was at end of array tests lastPosUsed >> 1")
        void condenseLastPosUsedReduction()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long startMs = 1000_000L;
            // Set firstSecond = 1000
            ts.addValue(startMs, 10, false);
            // Place value at last position 15 (second 1015)
            ts.addValue(startMs + 15_000L, 10, false);
            assertThat(ts.getLastSecond()).isEqualTo(1015L);

            // Adding a value with startSecond = 1002 and endSecond = 1016:
            // Triggers condense to scale 2: lastPosUsed should become 15 >> 1 = 7.
            // In addValue: pos of 1002 is (1002-1000)>>1 = 1.
            // Concurrency loop runs for i=1003, 1005, 1007, 1009, 1011, 1013, 1015.
            // For i=1015: pos = (1015-1000)>>1 = 7.
            // Step is scale=2, so next is 1017 > 1016.
            // Concurrency loop ends with pos = 7.
            // lastPosUsed becomes Math.max(7, 7) = 7.
            // With lastPosUsed = 7: getLastSecond() = 1000 + (7 << 1) = 1014.
            // If lastPosUsed was NOT shifted in condense (remained 15), lastPosUsed would be max(7, 15) = 15,
            // resulting in getLastSecond() = 1000 + (15 << 1) = 1030!
            ts.addValue(startMs + 2_000L, startMs + 16_000L, 20, false);
            assertThat(ts.getLastSecond()).isEqualTo(1014L);
        }
    }

    @Nested
    @DisplayName("Retrospective Insertion & Backward Shifting (shiftRight)")
    class ShiftRightTest
    {
        @Test
        @DisplayName("Adding earlier timestamp shifts array right without scaling if within capacity")
        void shiftRightWithinCapacity()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long baseMs = 1010_000L; // second 1010
            ts.addValue(baseMs, 30, false);

            // Add value at second 1005 (5 seconds earlier)
            ts.addValue(baseMs - 5000L, 10, false);

            assertThat(ts.getFirstSecond()).isEqualTo(1005L);
            assertThat(ts.getLastSecond()).isEqualTo(1010L);
            assertThat(ts.getCount()).isEqualTo(2L);
            assertThat(ts.getTotalValue()).isEqualTo(40L);

            IntTimeSeriesEntry[] entries = ts.getValues();
            assertThat(entries[0].getCount()).isEqualTo(1); // second 1005
            assertThat(entries[0].getTotalValue()).isEqualTo(10L);
            assertThat(entries[5].getCount()).isEqualTo(1); // second 1010 (offset = 5)
            assertThat(entries[5].getTotalValue()).isEqualTo(30L);
        }

        @Test
        @DisplayName("Shift right with exact capacity boundary (lastPosUsed + offset == size - 1 vs size)")
        void shiftRightBoundary()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long baseMs = 1015_000L; // second 1015 (pos 0)
            ts.addValue(baseMs, 30, false);

            // Adding second 1000 -> offset = 1015 - 1000 = 15. lastPosUsed = 0. 0 + 15 = 15 < 16.
            // Exactly fits without condensing!
            ts.addValue(baseMs - 15_000L, 10, false);
            assertThat(ts.getScale()).isEqualTo(1);
            assertThat(ts.getFirstSecond()).isEqualTo(1000L);
            assertThat(ts.getLastSecond()).isEqualTo(1015L);

            // Now firstSecond = 1000, lastPosUsed = 15.
            // Adding second 999 -> offset = 1000 - 999 = 1. lastPosUsed + offset = 15 + 1 = 16 == size (16).
            // Condition (lastPosUsed + offset >= size): with >= (16 >= 16) it condenses. With > (16 > 16), it wouldn't!
            // Condense called with: second (999) + expandToSeconds(15) = 999 + 15 = 1014.
            // Condense loop: 1014 < 1000 + 32 -> 1 round -> scale becomes 2.
            ts.addValue(999_000L, 5, false);
            assertThat(ts.getScale()).isEqualTo(2);
            assertThat(ts.getFirstSecond()).isEqualTo(999L);
            assertThat(ts.getCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("Shift right with multiple earlier timestamps within capacity")
        void shiftRightMultipleTimes()
        {
            IntTimeSeries ts = new IntTimeSeries(32);
            long baseMs = 1020_000L;
            ts.addValue(baseMs, 100, false);
            ts.addValue(baseMs - 5000L, 50, false);
            ts.addValue(baseMs - 10000L, 25, false);

            assertThat(ts.getFirstSecond()).isEqualTo(1010L);
            assertThat(ts.getLastSecond()).isEqualTo(1020L);
            assertThat(ts.getCount()).isEqualTo(3L);
            assertThat(ts.getTotalValue()).isEqualTo(175L);
        }
    }

    @Nested
    @DisplayName("Histogram Generation (toHistogram)")
    class ToHistogramTest
    {
        @Test
        @DisplayName("Generates bucketed histogram across min and max value range")
        void toHistogramGeneration()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long baseMs = 1000_000L;

            // Add values from 0 to 80 (multiples of 16 to avoid precision overlap)
            for (int v = 0; v <= 80; v += 16)
            {
                ts.addValue(baseMs, v, false);
            }

            List<IntTimeSeries.HistogramBucket> buckets = ts.toHistogram(5);
            assertThat(buckets).hasSize(5);

            int totalCount = buckets.stream().mapToInt(IntTimeSeries.HistogramBucket::count).sum();
            assertThat(totalCount).isGreaterThanOrEqualTo(6);

            // Check toString format of HistogramBucket
            IntTimeSeries.HistogramBucket first = buckets.get(0);
            assertThat(first.toString()).contains(first.startValue() + ", " + first.endValue() + ", " + first.count());
        }

        @Test
        @DisplayName("Histogram with non-zero minimum checks start boundary for bucket 0 vs subsequent buckets")
        void toHistogramNonZeroMin()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long baseMs = 1000_000L;

            // Min value is 100, max value is 200
            ts.addValue(baseMs, 100, false);
            ts.addValue(baseMs, 150, false);
            ts.addValue(baseMs, 200, false);

            List<IntTimeSeries.HistogramBucket> buckets = ts.toHistogram(4);
            assertThat(buckets).hasSize(4);

            // Bucket 0 start should be 0 (special case i == 0 ? 0 : ...)
            assertThat(buckets.get(0).startValue()).isEqualTo(0);
            // Bucket 1 start should be min + 1 * bucketWidth = 100 + 25 = 125
            assertThat(buckets.get(1).startValue()).isEqualTo(125);
            // Bucket 3 (last bucket) end should be inclusive max (not minus 1)
            assertThat(buckets.get(3).endValue()).isEqualTo(200);
            // Intermediate bucket end should be (min + (i+1)*bucketWidth) - 1
            assertThat(buckets.get(0).endValue()).isEqualTo(124);
        }
    }
}
