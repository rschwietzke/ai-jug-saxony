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
        @DisplayName("Merge with same scale preserves odd numbers without scaling")
        void mergeSameScaleOddNumbers()
        {
            IntTimeSeriesEntry e1 = new IntTimeSeriesEntry();
            e1.updateValue(1, true);
            e1.updateValue(70, false);

            IntTimeSeriesEntry e2 = new IntTimeSeriesEntry();
            e2.updateValue(3, true);
            e2.updateValue(80, false);

            e1.merge(e2);

            // Error counts must sum up (kills line 301 + to -)
            assertThat(e1.getErrorCount()).isEqualTo(2);
            // High bits must be ORed together (kills line 304 | to &)
            // Odd numbers must not be degraded by accidental scale >= (kills line 262 and line 277)
            assertThat(e1.getValues()).contains(1.0, 3.0, 70.0, 80.0);
        }

        @Test
        @DisplayName("ScaleIfNeeded preserves high bits shifted into low")
        void scaleIfNeededPreservesHighBits() throws Exception
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(70, false); // in distinctValuesHigh
            entry.updateValue(130, false); // triggers scaleIfNeeded: h must shift left by 32 into low

            double[] values = entry.getValues();
            assertThat(java.util.Arrays.stream(values).anyMatch(v -> Math.abs(v - 70.0) <= 2.0)).isTrue();
            assertThat(java.util.Arrays.stream(values).anyMatch(v -> Math.abs(v - 130.0) <= 2.0)).isTrue();

            // Add another value that fits in current scale (scale = 1): 200 >> 1 = 100 < 128
            // In unmutated code (>>), distinctValuesScale remains 1
            // In mutated code (<<), 200 << 1 = 400 >= 128, triggering further scaling to 2
            entry.updateValue(200, false);
            assertThat(java.util.Arrays.stream(entry.getValues()).anyMatch(v -> Math.abs(v - 200.0) <= 2.0)).isTrue();

            java.lang.reflect.Field scaleField = IntTimeSeriesEntry.class.getDeclaredField("distinctValuesScale");
            scaleField.setAccessible(true);
            assertThat(scaleField.getInt(entry)).isEqualTo(1);
        }

        @Test
        @DisplayName("Merge when this has smaller scale than other")
        void mergeSmallerScale() throws Exception
        {
            IntTimeSeriesEntry smallScale = new IntTimeSeriesEntry();
            smallScale.updateValue(10, false);

            IntTimeSeriesEntry largeScale = new IntTimeSeriesEntry();
            largeScale.updateValue(1000, false); // requires scaling to 3

            smallScale.merge(largeScale);

            assertThat(smallScale.getCount()).isEqualTo(2);
            assertThat(smallScale.getTotalValue()).isEqualTo(1010L);
            assertThat(smallScale.getMinimumValue()).isEqualTo(10);
            assertThat(smallScale.getMaximumValue()).isEqualTo(1000);

            java.lang.reflect.Field scaleField = IntTimeSeriesEntry.class.getDeclaredField("distinctValuesScale");
            scaleField.setAccessible(true);
            // Kills line 265 while condition (>= would scale to 4) and line 262 negate conditional
            assertThat(scaleField.getInt(smallScale)).isEqualTo(3);
        }

        @Test
        @DisplayName("Merge when this has larger scale than other")
        void mergeLargerScale() throws Exception
        {
            IntTimeSeriesEntry largeScale = new IntTimeSeriesEntry();
            largeScale.updateValue(1000, false); // scale 3

            IntTimeSeriesEntry smallScale = new IntTimeSeriesEntry();
            smallScale.updateValue(10, false);

            largeScale.merge(smallScale);

            assertThat(largeScale.getCount()).isEqualTo(2);
            assertThat(largeScale.getTotalValue()).isEqualTo(1010L);
            assertThat(largeScale.getMinimumValue()).isEqualTo(10);
            assertThat(largeScale.getMaximumValue()).isEqualTo(1000);

            java.lang.reflect.Field scaleField = IntTimeSeriesEntry.class.getDeclaredField("distinctValuesScale");
            scaleField.setAccessible(true);
            assertThat(scaleField.getInt(largeScale)).isEqualTo(3);
            // item (smallScale) must have been scaled up to match this.scale (3)
            // Kills line 277 NegateConditionals and line 280 NegateConditionals
            assertThat(scaleField.getInt(smallScale)).isEqualTo(3);
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

        @Test
        @DisplayName("Verifies equals checks all individual fields")
        void equalsChecksAllFields() throws Exception
        {
            IntTimeSeriesEntry base = new IntTimeSeriesEntry(10, false);

            for (String fieldName : new String[]{"minimum", "maximum", "totalValue", "distinctValuesLow", "distinctValuesHigh", "distinctValuesScale"})
            {
                IntTimeSeriesEntry copy = new IntTimeSeriesEntry(10, false);
                java.lang.reflect.Field f = IntTimeSeriesEntry.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                if (f.getType() == int.class)
                {
                    f.setInt(copy, f.getInt(copy) + 1);
                }
                else if (f.getType() == long.class)
                {
                    f.setLong(copy, f.getLong(copy) + 1L);
                }
                assertThat(base).isNotEqualTo(copy);
            }
        }
    }
}
