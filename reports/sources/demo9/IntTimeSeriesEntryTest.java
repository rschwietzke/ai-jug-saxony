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

import static org.assertj.core.api.Assertions.assertThat;

class IntTimeSeriesEntryTest
{
    @Nested
    @DisplayName("Constructor and initial empty state")
    class InitialStateTests
    {
        @Test
        @DisplayName("Default constructor initializes zeros and empty metrics")
        void testDefaultConstructor()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();

            assertThat(entry.getCount()).isEqualTo(0);
            assertThat(entry.getTotalValue()).isEqualTo(0L);
            assertThat(entry.getAverageValue()).isEqualTo(0);
            assertThat(entry.getMinimumValue()).isEqualTo(0);
            assertThat(entry.getMaximumValue()).isEqualTo(0);
            assertThat(entry.getErrorCount()).isEqualTo(0);
            assertThat(entry.getConcurrentCount()).isEqualTo(0);
            assertThat(entry.getValues()).isEmpty();
        }

        @Test
        @DisplayName("Parameterized constructor applies first value")
        void testParameterizedConstructor()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry(150, true);

            assertThat(entry.getCount()).isEqualTo(1);
            assertThat(entry.getTotalValue()).isEqualTo(150L);
            assertThat(entry.getAverageValue()).isEqualTo(150);
            assertThat(entry.getMinimumValue()).isEqualTo(150);
            assertThat(entry.getMaximumValue()).isEqualTo(150);
            assertThat(entry.getErrorCount()).isEqualTo(1);
            assertThat(entry.getConcurrentCount()).isEqualTo(1);
            assertThat(entry.getValues()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("updateValue and updateConcurrency tests")
    class UpdateValueTests
    {
        @Test
        @DisplayName("Updating positive values updates min, max, total, and counts")
        void testPositiveValues()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(10, false);
            entry.updateValue(30, true);
            entry.updateValue(20, false);

            assertThat(entry.getCount()).isEqualTo(3);
            assertThat(entry.getConcurrentCount()).isEqualTo(3);
            assertThat(entry.getErrorCount()).isEqualTo(1);
            assertThat(entry.getTotalValue()).isEqualTo(60L);
            assertThat(entry.getAverageValue()).isEqualTo(20);
            assertThat(entry.getMinimumValue()).isEqualTo(10);
            assertThat(entry.getMaximumValue()).isEqualTo(30);
        }

        @Test
        @DisplayName("Negative values are clamped to 0")
        void testNegativeValueClamping()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(-50, false);

            assertThat(entry.getCount()).isEqualTo(1);
            assertThat(entry.getTotalValue()).isEqualTo(0L);
            assertThat(entry.getMinimumValue()).isEqualTo(0);
            assertThat(entry.getMaximumValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("updateConcurrency increments concurrency independently")
        void testUpdateConcurrency()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateConcurrency();
            entry.updateConcurrency();

            assertThat(entry.getConcurrentCount()).isEqualTo(2);
            assertThat(entry.getCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Distinct values bitmap and dynamic scaling")
    class DistinctValuesScalingTests
    {
        @Test
        @DisplayName("Values < 64 set bits in distinctValuesLow without scaling")
        void testValuesUnder64()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(0, false);
            entry.updateValue(5, false);
            entry.updateValue(63, false);

            assertThat(entry.getValues()).containsExactly(0.0, 5.0, 63.0);
        }

        @Test
        @DisplayName("Values between 64 and 127 set bits in distinctValuesHigh without scaling")
        void testValuesBetween64And127()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(10, false);
            entry.updateValue(70, false);
            entry.updateValue(127, false);

            assertThat(entry.getValues()).containsExactly(10.0, 70.0, 127.0);
        }

        @Test
        @DisplayName("Value >= 128 triggers scaleIfNeeded, compressing existing bits")
        void testScalingTriggered()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(10, false);
            entry.updateValue(20, false);

            // Adding 128 causes scale to increase from 0 to 1 (slots represent multiples of 2)
            entry.updateValue(128, false);

            double[] values = entry.getValues();
            assertThat(values).isNotEmpty();
            // Value 128 scaled by 2^1 is bucket 64 -> (1<<1)*64 = 128.0
            assertThat(values).contains(128.0);
        }

        @Test
        @DisplayName("Large values trigger multiple scale increments")
        void testMultiScaleIncrement()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(1000, false); // requires scaling (1000 >> 3 = 125 < 128) -> scale 3 (slots of 8)

            double[] values = entry.getValues();
            assertThat(values).isNotEmpty();
            // 1000 >> 3 = 125. 125 * 8 = 1000.0
            assertThat(values).contains(1000.0);
        }
    }

    @Nested
    @DisplayName("merge tests")
    class MergeTests
    {
        @Test
        @DisplayName("Merge entries with the same scale")
        void testMergeSameScale()
        {
            IntTimeSeriesEntry entry1 = new IntTimeSeriesEntry();
            entry1.updateValue(10, false);
            entry1.updateValue(20, false);

            IntTimeSeriesEntry entry2 = new IntTimeSeriesEntry();
            entry2.updateValue(30, true);
            entry2.updateConcurrency(); // concurrency becomes 2 in entry2

            entry1.merge(entry2);

            assertThat(entry1.getCount()).isEqualTo(3);
            assertThat(entry1.getTotalValue()).isEqualTo(60L);
            assertThat(entry1.getErrorCount()).isEqualTo(1);
            assertThat(entry1.getMinimumValue()).isEqualTo(10);
            assertThat(entry1.getMaximumValue()).isEqualTo(30);
            // Concurrency merges with Math.max (entry1 had 2, entry2 had 2)
            assertThat(entry1.getConcurrentCount()).isEqualTo(2);
            assertThat(entry1.getValues()).contains(10.0, 20.0, 30.0);
        }

        @Test
        @DisplayName("Merge entries with different scales")
        void testMergeDifferentScales()
        {
            IntTimeSeriesEntry entrySmall = new IntTimeSeriesEntry();
            entrySmall.updateValue(10, false);

            IntTimeSeriesEntry entryLarge = new IntTimeSeriesEntry();
            entryLarge.updateValue(500, true); // larger scale

            // Merge large into small (small scales up)
            entrySmall.merge(entryLarge);

            assertThat(entrySmall.getCount()).isEqualTo(2);
            assertThat(entrySmall.getTotalValue()).isEqualTo(510L);
            assertThat(entrySmall.getErrorCount()).isEqualTo(1);
            assertThat(entrySmall.getMinimumValue()).isEqualTo(10);
            assertThat(entrySmall.getMaximumValue()).isEqualTo(500);

            // Merge small into large (item scales up)
            IntTimeSeriesEntry entryLarge2 = new IntTimeSeriesEntry();
            entryLarge2.updateValue(500, false);

            IntTimeSeriesEntry entrySmall2 = new IntTimeSeriesEntry();
            entrySmall2.updateValue(10, false);

            entryLarge2.merge(entrySmall2);
            assertThat(entryLarge2.getCount()).isEqualTo(2);
            assertThat(entryLarge2.getTotalValue()).isEqualTo(510L);
        }
    }

    @Nested
    @DisplayName("equals and toString tests")
    class ObjectContractTests
    {
        @Test
        @DisplayName("equals and reflexivity")
        void testEquals()
        {
            IntTimeSeriesEntry e1 = new IntTimeSeriesEntry(25, false);
            IntTimeSeriesEntry e2 = new IntTimeSeriesEntry(25, false);
            IntTimeSeriesEntry e3 = new IntTimeSeriesEntry(30, false);

            assertThat(e1.equals(e1)).isTrue();
            assertThat(e1.equals(e2)).isTrue();
            assertThat(e2.equals(e1)).isTrue();
            assertThat(e1.equals(e3)).isFalse();
            assertThat(e1.equals(null)).isFalse();
            assertThat(e1.equals("different type")).isFalse();
        }

        @Test
        @DisplayName("toString contains metrics summary")
        void testToString()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry(42, true);
            String str = entry.toString();

            assertThat(str).contains("42");
        }
    }
}
