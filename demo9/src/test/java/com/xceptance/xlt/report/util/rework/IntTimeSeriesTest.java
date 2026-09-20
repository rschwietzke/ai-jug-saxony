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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.within;

class IntTimeSeriesTest
{
    @Nested
    @DisplayName("Constructor and initial empty state")
    class InitialStateTests
    {
        @Test
        @DisplayName("Default constructor creates power of two sized series")
        void testDefaultConstructor()
        {
            IntTimeSeries series = new IntTimeSeries();

            assertThat(series.getSize()).isEqualTo(4096); // nextHighestPowerOfTwo(3600)
            assertThat(series.getScale()).isEqualTo(1);
            assertThat(series.getSlotWidth()).isEqualTo(1);
            assertThat(series.getCount()).isEqualTo(0L);
            assertThat(series.getTotalValue()).isEqualTo(0L);
            assertThat(series.getErrorCount()).isEqualTo(0L);
            assertThat(series.getMean()).isEqualTo(0.0);
            assertThat(series.getStandardDeviation()).isEqualTo(0.0);
            assertThat(series.getValues()).hasSize(4096);
            assertThat(series.toHistogram(10)).isEmpty();

            assertThatIllegalStateException().isThrownBy(series::getFirstSecond);
            assertThatIllegalStateException().isThrownBy(series::getLastSecond);
        }

        @Test
        @DisplayName("Custom constructor rounds size to next highest power of two")
        void testCustomConstructor()
        {
            IntTimeSeries series = new IntTimeSeries(100);
            assertThat(series.getSize()).isEqualTo(128);
        }
    }

    @Nested
    @DisplayName("Adding values and time tracking")
    class AddingValuesTests
    {
        @Test
        @DisplayName("Adding single timestamp sets first and last second")
        void testAddSingleValue()
        {
            IntTimeSeries series = new IntTimeSeries(64);
            // 1,000,000 ms = 1000 seconds
            series.addValue(1_000_000L, 50, false);

            assertThat(series.getFirstSecond()).isEqualTo(1000L);
            assertThat(series.getLastSecond()).isEqualTo(1000L);
            assertThat(series.getCount()).isEqualTo(1L);
            assertThat(series.getTotalValue()).isEqualTo(50L);
            assertThat(series.getErrorCount()).isEqualTo(0L);
            assertThat(series.getMean()).isEqualTo(50.0);
            assertThat(series.getStandardDeviation()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Adding values across time updates lastSecond and statistics")
        void testMultipleValues()
        {
            IntTimeSeries series = new IntTimeSeries(64);
            series.addValue(1_000_000L, 10, false); // sec 1000
            series.addValue(1_002_000L, 30, true);  // sec 1002

            assertThat(series.getFirstSecond()).isEqualTo(1000L);
            assertThat(series.getLastSecond()).isEqualTo(1002L);
            assertThat(series.getCount()).isEqualTo(2L);
            assertThat(series.getTotalValue()).isEqualTo(40L);
            assertThat(series.getErrorCount()).isEqualTo(1L);
            assertThat(series.getMean()).isEqualTo(20.0);

            // StdDev for [10, 30]: mean=20, squares=100+900=1000, 1000/2 - 400 = 100, sqrt(100) = 10.0
            assertThat(series.getStandardDeviation()).isCloseTo(10.0, within(0.001));

            IntTimeSeries.Statistics stats = series.getStatistics();
            assertThat(stats.count).isEqualTo(2L);
            assertThat(stats.sum).isEqualTo(40L);
            assertThat(stats.errorCount).isEqualTo(1L);
            assertThat(stats.minValue).isEqualTo(10);
            assertThat(stats.maxValue).isEqualTo(30);
        }

        @Test
        @DisplayName("Concurrency is tracked across start and end time range")
        void testConcurrencyTracking()
        {
            IntTimeSeries series = new IntTimeSeries(64);
            // Transaction spanning from sec 1000 to sec 1003 (4 seconds: 1000, 1001, 1002, 1003)
            series.addValue(1_000_000L, 1_003_000L, 100, false);

            IntTimeSeriesEntry[] entries = series.getValues();
            // Slot 0 (sec 1000): value added, count=1, concurrency=1
            assertThat(entries[0].getCount()).isEqualTo(1);
            assertThat(entries[0].getConcurrentCount()).isEqualTo(1);

            // Slots 1, 2, 3 (sec 1001, 1002, 1003): concurrency only, count=0, concurrency=1
            assertThat(entries[1].getCount()).isEqualTo(0);
            assertThat(entries[1].getConcurrentCount()).isEqualTo(1);

            assertThat(entries[2].getCount()).isEqualTo(0);
            assertThat(entries[2].getConcurrentCount()).isEqualTo(1);

            assertThat(entries[3].getCount()).isEqualTo(0);
            assertThat(entries[3].getConcurrentCount()).isEqualTo(1);

            assertThat(series.getLastSecond()).isEqualTo(1003L);
        }
    }

    @Nested
    @DisplayName("Condensation and temporal shifting tests")
    class CondensationAndShiftingTests
    {
        @Test
        @DisplayName("Adding value beyond capacity triggers condense (doubles slot width)")
        void testCondensationForward()
        {
            // Small series with size 4: slots 0, 1, 2, 3 (span 4 seconds initially)
            IntTimeSeries series = new IntTimeSeries(4);
            series.addValue(1_000_000L, 10, false); // sec 1000 -> slot 0
            series.addValue(1_001_000L, 20, false); // sec 1001 -> slot 1

            assertThat(series.getScale()).isEqualTo(1);
            assertThat(series.getSlotWidth()).isEqualTo(1);

            // Now add sec 1005 (beyond sec 1000 + 4 = 1004)
            series.addValue(1_005_000L, 30, false);

            // Must have condensed: scale increased, slot width doubled
            assertThat(series.getScale()).isGreaterThan(1);
            assertThat(series.getCount()).isEqualTo(3L);
            assertThat(series.getTotalValue()).isEqualTo(60L);
        }

        @Test
        @DisplayName("Adding earlier timestamp triggers shiftRight")
        void testShiftRightEarlierTimestamp()
        {
            IntTimeSeries series = new IntTimeSeries(16);
            series.addValue(1_010_000L, 50, false); // sec 1010
            series.addValue(1_012_000L, 60, false); // sec 1012

            assertThat(series.getFirstSecond()).isEqualTo(1010L);

            // Add an earlier timestamp: sec 1005
            series.addValue(1_005_000L, 40, false);

            assertThat(series.getFirstSecond()).isEqualTo(1005L);
            assertThat(series.getCount()).isEqualTo(3L);
            assertThat(series.getTotalValue()).isEqualTo(150L);
        }

        @Test
        @DisplayName("Adding earlier timestamp triggers condense during shiftRight when near capacity")
        void testShiftRightWithCondense()
        {
            IntTimeSeries series = new IntTimeSeries(16); // capacity 16
            // Put an entry at 1020 and another at 1030 (lastPosUsed = 10)
            series.addValue(1_020_000L, 50, false);
            series.addValue(1_030_000L, 60, false);

            // Add sec 1010 (offset 10; 10 + 10 = 20 >= 16, triggers condensation)
            // After 1 condensation step (scale=2), newOffset = (1020-1010)>>1 = 5 < 16
            series.addValue(1_010_000L, 20, false);

            assertThat(series.getFirstSecond()).isEqualTo(1010L);
            assertThat(series.getCount()).isEqualTo(3L);
            assertThat(series.getTotalValue()).isEqualTo(130L);
            assertThat(series.getScale()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Histogram generation and percentiles")
    class HistogramAndPercentileTests
    {
        @Test
        @DisplayName("Percentile delegates to internal RuntimeHistogram")
        void testPercentile()
        {
            IntTimeSeries series = new IntTimeSeries(64);
            series.addValue(1_000_000L, 100, false);
            series.addValue(1_001_000L, 200, false);
            series.addValue(1_002_000L, 300, false);

            // Precision is 8, so values are binned into multiples of 8
            assertThat(series.getPercentile(50.0)).isGreaterThan(0);
        }

        @Test
        @DisplayName("toHistogram generates bucket ranges and counts")
        void testToHistogram()
        {
            IntTimeSeries series = new IntTimeSeries(64);
            series.addValue(1_000_000L, 100, false);
            series.addValue(1_001_000L, 200, false);
            series.addValue(1_002_000L, 300, false);

            List<IntTimeSeries.HistogramBucket> histogram = series.toHistogram(5);
            assertThat(histogram).hasSize(5);

            int totalCount = histogram.stream().mapToInt(IntTimeSeries.HistogramBucket::count).sum();
            assertThat(totalCount).isEqualTo(3);

            // Verify bucket formatting
            assertThat(histogram.get(0).toString()).contains(",");
        }
    }
}
