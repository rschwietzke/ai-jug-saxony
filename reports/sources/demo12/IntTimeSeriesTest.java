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
            assertThat(ts.toHistogram(10)).isEmpty();

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

            // Add value at second 1020 (span is 21 seconds > 16 slots capacity)
            ts.addValue(startMs + 20_000L, 50, false);

            // Capacity at scale 2 is 16 * 2 = 32 seconds
            assertThat(ts.getScale()).isEqualTo(2);
            assertThat(ts.getSlotWidth()).isEqualTo(2);
            assertThat(ts.getCount()).isEqualTo(3L);
            assertThat(ts.getTotalValue()).isEqualTo(80L);

            // First slot should now combine seconds 1000 and 1001
            IntTimeSeriesEntry[] entries = ts.getValues();
            assertThat(entries[0].getCount()).isEqualTo(2);
            assertThat(entries[0].getTotalValue()).isEqualTo(30L);
        }

        @Test
        @DisplayName("Condensation on larger time series preserves all metrics")
        void largerCondensation()
        {
            IntTimeSeries ts = new IntTimeSeries(64);
            long startMs = 1000_000L; // second 1000
            ts.addValue(startMs, 100, false);
            ts.addValue(startMs + 10_000L, 200, false); // second 1010

            // Exceed 64 seconds capacity
            ts.addValue(startMs + 80_000L, 300, false); // second 1080

            assertThat(ts.getScale()).isEqualTo(2);
            assertThat(ts.getCount()).isEqualTo(3L);
            assertThat(ts.getTotalValue()).isEqualTo(600L);
        }

        @Test
        @DisplayName("Multi-step condensation when adding timestamp far in the future")
        void multiStepCondensation()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long baseMs = 1000_000L;
            ts.addValue(baseMs, 10, false);
            // 35 seconds later: initial scale 1 covers 16s. 35s requires scale 4 (covers 64s), looping twice in condense
            ts.addValue(baseMs + 35_000L, 20, false);

            assertThat(ts.getCount()).isEqualTo(2L);
            assertThat(ts.getTotalValue()).isEqualTo(30L);
            assertThat(ts.getSlotWidth()).isEqualTo(4);
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

        @Test
        @DisplayName("Adding earlier timestamp exceeding capacity triggers condense")
        void shiftRightExceedingCapacityTriggersCondense()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long baseMs = 1000_000L;
            ts.addValue(baseMs, 10, false);
            ts.addValue(baseMs + 10_000L, 20, false); // lastPosUsed = 10
            // add earlier value at baseMs - 10_000L => offset = 10, lastPosUsed + offset = 20 >= 16
            ts.addValue(baseMs - 10_000L, 5, false);

            assertThat(ts.getFirstSecond()).isLessThanOrEqualTo(990L);
            assertThat(ts.getCount()).isEqualTo(3L);
            assertThat(ts.getTotalValue()).isEqualTo(35L);
        }
    }

    @Nested
    @DisplayName("Histogram Generation (toHistogram)")
    class ToHistogramTest
    {
        @Test
        @DisplayName("Returns empty mutable list when empty")
        void emptyTimeSeries()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            List<IntTimeSeries.HistogramBucket> buckets = ts.toHistogram(5);
            assertThat(buckets).isEmpty();
            // Verify mutable list (kills EmptyObjectReturnValsMutator)
            buckets.add(new IntTimeSeries.HistogramBucket(0, 0, 0));
            assertThat(buckets).hasSize(1);
        }

        @Test
        @DisplayName("Generates bucketed histogram across min and max value range")
        void toHistogramGeneration()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            long baseMs = 1000_000L;

            ts.addValue(baseMs, 32, false);
            ts.addValue(baseMs, 96, false);

            List<IntTimeSeries.HistogramBucket> buckets = ts.toHistogram(2);
            assertThat(buckets).hasSize(2);

            // min=32, max=96, bucketWidth = (96-32)/2 = 32
            // Bucket 0: start = 0 (since i==0 ? 0 : ...), end = (32 + 32) - 1 = 63. count = 1
            // Bucket 1: start = 32 + 1 * 32 = 64, end = 32 + 2 * 32 = 96 (since i == bucketCount - 1). count = 1
            // If line 465 (max - min) is mutated to (max + min):
            // bucketWidth = (96 + 32)/2 = 64, end would be 32 + 64 - 1 = 95 != 63
            assertThat(buckets.get(0).startValue()).isEqualTo(0);
            assertThat(buckets.get(0).endValue()).isEqualTo(63);
            assertThat(buckets.get(0).count()).isEqualTo(1);

            assertThat(buckets.get(1).startValue()).isEqualTo(64);
            assertThat(buckets.get(1).endValue()).isEqualTo(96);
            assertThat(buckets.get(1).count()).isEqualTo(1);

            // Check toString format of HistogramBucket
            IntTimeSeries.HistogramBucket first = buckets.get(0);
            assertThat(first.toString()).contains(first.startValue() + ", " + first.endValue() + ", " + first.count());
        }

        @Test
        @DisplayName("addValue at startSecond equal to firstSecond does not shift")
        void addValueAtSameFirstSecond()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            ts.addValue(1000_000L, 10, false);
            ts.addValue(1000_500L, 20, false);
            assertThat(ts.getFirstSecond()).isEqualTo(1000L);
            assertThat(ts.getCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("addValue at exact capacity limit triggers condense")
        void addValueAtExactCapacityLimit()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            ts.addValue(1000_000L, 10, false);
            ts.addValue(1016_000L, 20, false);
            assertThat(ts.getSlotWidth()).isEqualTo(2);
        }

        @Test
        @DisplayName("shiftRight when lastPosUsed + offset equals size triggers condense")
        void shiftRightExactBoundaryCondense()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            ts.addValue(1000_000L, 10, false);
            ts.addValue(1010_000L, 20, false);
            // Shift right by 6 seconds: new second = 994
            // offset = 1000 - 994 = 6. lastPosUsed + offset = 10 + 6 = 16 == size
            ts.addValue(994_000L, 30, false);
            assertThat(ts.getSlotWidth()).isEqualTo(2);
            assertThat(ts.getCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("condense updates lastPosUsed by shifting right")
        void condenseUpdatesLastPosUsed()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            ts.addValue(1000_000L, 10, false);
            ts.addValue(1010_000L, 20, false);
            ts.addValue(1016_000L, 30, false);
            assertThat(ts.getSlotWidth()).isEqualTo(2);
            // In unmutated code: lastPosUsed = Math.max(10 >> 1, 8) = 8 -> getLastSecond() = 1000 + 8 * 2 = 1016
            // In mutated code (>> 1 mutated to << 1): lastPosUsed = Math.max(10 << 1, 8) = 20 -> getLastSecond() = 1000 + 20 * 2 = 1040
            assertThat(ts.getLastSecond()).isEqualTo(1016L);
        }

        @Test
        @DisplayName("condense while condition boundary repeats if still at boundary")
        void condenseRepeatsAtBoundary()
        {
            IntTimeSeries ts = new IntTimeSeries(16);
            ts.addValue(1000_000L, 10, false);
            ts.addValue(1032_000L, 20, false);
            assertThat(ts.getSlotWidth()).isEqualTo(4);
        }
    }
}
