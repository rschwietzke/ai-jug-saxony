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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IntTimeSeriesEntry Tests")
class IntTimeSeriesEntryTest
{
    @Nested
    @DisplayName("Initial State & Construction")
    class ConstructionTest
    {
        @Test
        @DisplayName("Default constructor initializes empty metrics")
        void defaultConstructor()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            assertThat(entry.getCount()).isEqualTo(0);
            assertThat(entry.getConcurrentCount()).isEqualTo(0);
            assertThat(entry.getErrorCount()).isEqualTo(0);
            assertThat(entry.getTotalValue()).isEqualTo(0L);
            assertThat(entry.getAverageValue()).isEqualTo(0);
            assertThat(entry.getMinimumValue()).isEqualTo(0);
            assertThat(entry.getMaximumValue()).isEqualTo(0);
            assertThat(entry.getValues()).isEmpty();
            assertThat(entry.toString()).contains("0 / 0 / 0 / 0 / 0 / 0 / 0");
        }

        @Test
        @DisplayName("Parameterized constructor sets initial value and failure state")
        void parameterizedConstructor()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry(150, true);
            assertThat(entry.getCount()).isEqualTo(1);
            assertThat(entry.getConcurrentCount()).isEqualTo(1);
            assertThat(entry.getErrorCount()).isEqualTo(1);
            assertThat(entry.getTotalValue()).isEqualTo(150L);
            assertThat(entry.getAverageValue()).isEqualTo(150);
            assertThat(entry.getMinimumValue()).isEqualTo(150);
            assertThat(entry.getMaximumValue()).isEqualTo(150);
            assertThat(entry.getValues()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Updating Values & Metrics Aggregation")
    class UpdateTest
    {
        @Test
        @DisplayName("Correctly aggregates multiple positive values")
        void multipleValues()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(10, false);
            entry.updateValue(20, true);
            entry.updateValue(30, false);

            assertThat(entry.getCount()).isEqualTo(3);
            assertThat(entry.getConcurrentCount()).isEqualTo(3);
            assertThat(entry.getErrorCount()).isEqualTo(1);
            assertThat(entry.getTotalValue()).isEqualTo(60L);
            assertThat(entry.getAverageValue()).isEqualTo(20);
            assertThat(entry.getMinimumValue()).isEqualTo(10);
            assertThat(entry.getMaximumValue()).isEqualTo(30);
        }

        @Test
        @DisplayName("Clamps negative values to 0")
        void negativeValuesClamped()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(-50, false);

            assertThat(entry.getCount()).isEqualTo(1);
            assertThat(entry.getTotalValue()).isEqualTo(0L);
            assertThat(entry.getMinimumValue()).isEqualTo(0);
            assertThat(entry.getMaximumValue()).isEqualTo(0);
            assertThat(entry.getAverageValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("updateConcurrency increments concurrentCount independently")
        void updateConcurrency()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(10, false);
            entry.updateConcurrency();
            entry.updateConcurrency();

            assertThat(entry.getCount()).isEqualTo(1);
            assertThat(entry.getConcurrentCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Approximate Distinct Values & Dynamic Scaling")
    class DistinctValuesTest
    {
        @Test
        @DisplayName("Values below 64 occupy distinctValuesLow bitset without scaling")
        void lowRangeValues()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(0, false);
            entry.updateValue(5, false);
            entry.updateValue(63, false);

            double[] values = entry.getValues();
            assertThat(values).containsExactly(0.0, 5.0, 63.0);
        }

        @Test
        @DisplayName("Values between 64 and 127 occupy distinctValuesHigh bitset without scaling")
        void highRangeValues()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(64, false);
            entry.updateValue(100, false);
            entry.updateValue(127, false);

            double[] values = entry.getValues();
            assertThat(values).containsExactly(64.0, 100.0, 127.0);
        }

        @Test
        @DisplayName("Values >= 128 trigger dynamic bitset scaling")
        void scalingTriggered()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(10, false);
            entry.updateValue(128, false);

            // Scale becomes 1 (factor 2^1 = 2)
            // 10 >> 1 = 5 -> scaled back = 5 * 2 = 10
            // 128 >> 1 = 64 -> scaled back = 64 * 2 = 128
            double[] values = entry.getValues();
            assertThat(values).contains(10.0, 128.0);
        }

        @Test
        @DisplayName("Large values scale multiple times")
        void multipleScales()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(5000, false);

            double[] values = entry.getValues();
            assertThat(values).isNotEmpty();
            // Value should be approximated close to 5000
            assertThat(values[0]).isCloseTo(5000.0, org.assertj.core.data.Offset.offset(100.0));
        }
    }

    @Nested
    @DisplayName("Merging Entries")
    class MergeTest
    {
        @Test
        @DisplayName("Merge with same scale combines all metrics correctly")
        void mergeSameScale()
        {
            IntTimeSeriesEntry entry1 = new IntTimeSeriesEntry();
            entry1.updateValue(10, false);
            entry1.updateValue(30, true);

            IntTimeSeriesEntry entry2 = new IntTimeSeriesEntry();
            entry2.updateValue(20, false);
            entry2.updateValue(40, false);

            entry1.merge(entry2);

            assertThat(entry1.getCount()).isEqualTo(4);
            assertThat(entry1.getConcurrentCount()).isEqualTo(2); // max of 2 and 2
            assertThat(entry1.getErrorCount()).isEqualTo(1);
            assertThat(entry1.getTotalValue()).isEqualTo(100L);
            assertThat(entry1.getMinimumValue()).isEqualTo(10);
            assertThat(entry1.getMaximumValue()).isEqualTo(40);
            assertThat(entry1.getAverageValue()).isEqualTo(25);
            assertThat(entry1.getValues()).contains(10.0, 20.0, 30.0, 40.0);
        }

        @Test
        @DisplayName("Merge when this has smaller scale than other")
        void mergeSmallerScale()
        {
            IntTimeSeriesEntry smallScale = new IntTimeSeriesEntry();
            smallScale.updateValue(10, false);

            IntTimeSeriesEntry largeScale = new IntTimeSeriesEntry();
            largeScale.updateValue(1000, false); // requires scaling

            smallScale.merge(largeScale);

            assertThat(smallScale.getCount()).isEqualTo(2);
            assertThat(smallScale.getTotalValue()).isEqualTo(1010L);
            assertThat(smallScale.getMinimumValue()).isEqualTo(10);
            assertThat(smallScale.getMaximumValue()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Merge when this has larger scale than other")
        void mergeLargerScale()
        {
            IntTimeSeriesEntry largeScale = new IntTimeSeriesEntry();
            largeScale.updateValue(1000, false);

            IntTimeSeriesEntry smallScale = new IntTimeSeriesEntry();
            smallScale.updateValue(10, false);

            largeScale.merge(smallScale);

            assertThat(largeScale.getCount()).isEqualTo(2);
            assertThat(largeScale.getTotalValue()).isEqualTo(1010L);
            assertThat(largeScale.getMinimumValue()).isEqualTo(10);
            assertThat(largeScale.getMaximumValue()).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("Equals and Contract")
    class EqualsTest
    {
        @Test
        @DisplayName("Verifies equals reflexivity, symmetry, and field checks")
        void equalsContract()
        {
            IntTimeSeriesEntry e1 = new IntTimeSeriesEntry(10, false);
            IntTimeSeriesEntry e2 = new IntTimeSeriesEntry(10, false);
            IntTimeSeriesEntry e3 = new IntTimeSeriesEntry(20, false);

            assertThat(e1).isEqualTo(e1);
            assertThat(e1).isEqualTo(e2);
            assertThat(e2).isEqualTo(e1);
            assertThat(e1).isNotEqualTo(null);
            assertThat(e1).isNotEqualTo("other type");
            assertThat(e1).isNotEqualTo(e3);

            // Modify count
            IntTimeSeriesEntry eDiffCount = new IntTimeSeriesEntry(10, false);
            eDiffCount.updateValue(0, false);
            assertThat(e1).isNotEqualTo(eDiffCount);

            // Modify error count
            IntTimeSeriesEntry eDiffError = new IntTimeSeriesEntry(10, true);
            assertThat(e1).isNotEqualTo(eDiffError);

            // Modify concurrency
            IntTimeSeriesEntry eDiffConc = new IntTimeSeriesEntry(10, false);
            eDiffConc.updateConcurrency();
            assertThat(e1).isNotEqualTo(eDiffConc);
        }
    }
}
