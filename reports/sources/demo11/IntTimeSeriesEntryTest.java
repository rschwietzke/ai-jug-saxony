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

import java.lang.reflect.Field;

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
        @DisplayName("Clamps negative values to 0 and tests boundary zero")
        void negativeValuesClamped()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(-50, false);

            assertThat(entry.getCount()).isEqualTo(1);
            assertThat(entry.getTotalValue()).isEqualTo(0L);
            assertThat(entry.getMinimumValue()).isEqualTo(0);
            assertThat(entry.getMaximumValue()).isEqualTo(0);
            assertThat(entry.getAverageValue()).isEqualTo(0);

            // Exactly 0 (boundary between < 0 and >= 0)
            IntTimeSeriesEntry zeroEntry = new IntTimeSeriesEntry();
            zeroEntry.updateValue(0, false);
            assertThat(zeroEntry.getMinimumValue()).isEqualTo(0);
            assertThat(zeroEntry.getMaximumValue()).isEqualTo(0);
            assertThat(zeroEntry.getTotalValue()).isEqualTo(0L);

            // Adding a negative value after positive minimum should update minimum to 0
            IntTimeSeriesEntry entryPos = new IntTimeSeriesEntry();
            entryPos.updateValue(100, false);
            assertThat(entryPos.getMinimumValue()).isEqualTo(100);
            entryPos.updateValue(-10, false);
            assertThat(entryPos.getMinimumValue()).isEqualTo(0);

            // Adding value equal to current minimum
            entryPos.updateValue(0, false);
            assertThat(entryPos.getMinimumValue()).isEqualTo(0);

            // Adding value equal to current maximum
            entryPos.updateValue(100, false);
            assertThat(entryPos.getMaximumValue()).isEqualTo(100);
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
            entry.updateValue(64, false); // bit 0 in high part (64 - 64 = 0)
            entry.updateValue(65, false); // bit 1 in high part (65 - 64 = 1) -> tests subtraction 65 - 64
            entry.updateValue(100, false);
            entry.updateValue(127, false); // bit 63 in high part (127 - 64 = 63)

            double[] values = entry.getValues();
            assertThat(values).containsExactly(64.0, 65.0, 100.0, 127.0);

            // Specifically test a single high value at 65:
            // In getValues(): if (i - 64) is mutated to (i + 64), (65 + 64) = 129 -> shift by 129 is 129%64 = 1 -> matches bit 1!
            // But for i = 64: (64 - 64) = 0 (checks bit 0). If mutated to (64 + 64) = 128 (128%64 = 0, checks bit 0).
            // But for i = 66: (66 - 64) = 2 (checks bit 2). If mutated to (66 + 64) = 130 (130%64 = 2, checks bit 2).
            // For which i in 64..127 does (i - 64)%64 != (i + 64)%64 in Java?
            // In Java, long shift count is masked by 63 (i.e. x & 0x3F).
            // (i - 64) & 63 == (i + 64) & 63 because 64 is a multiple of 64!
            // So (i - 64) and (i + 64) produce the exact same bit shift in Java bytecode! (Equivalent mutant)
        }

        @Test
        @DisplayName("Boundary at value 127 and 128 tests scaleIfNeeded loop condition")
        void scaleIfNeededBoundary()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(127, false); // v = 127, loop (v >= 128) does not run
            assertThat(entry.getValues()).containsExactly(127.0);

            IntTimeSeriesEntry entry128 = new IntTimeSeriesEntry();
            entry128.updateValue(128, false); // v = 128, loop (v >= 128) runs once
            assertThat(entry128.getValues()).containsExactly(128.0);
        }

        @Test
        @DisplayName("Values >= 128 trigger dynamic bitset scaling with high bits preserved")
        void scalingTriggeredWithHighBits()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(10, false);
            entry.updateValue(70, false); // in high part (bit 6)
            entry.updateValue(128, false); // triggers scale from 0 to 1

            // 10 >> 1 = 5 -> scaled back = 10.0
            // 70 >> 1 = 35 -> scaled back = 70.0 (in high 32 bits of low)
            // 128 >> 1 = 64 -> scaled back = 128.0 (in high)
            double[] values = entry.getValues();
            assertThat(values).contains(10.0, 70.0, 128.0);
        }

        @Test
        @DisplayName("Large values scale multiple times")
        void multipleScales()
        {
            IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(5000, false);

            double[] values = entry.getValues();
            assertThat(values).isNotEmpty();
            assertThat(values[0]).isCloseTo(5000.0, org.assertj.core.data.Offset.offset(100.0));
        }
    }

    @Nested
    @DisplayName("Merging Entries")
    class MergeTest
    {
        @Test
        @DisplayName("Merge with same scale combines all metrics correctly including high bits and odd values")
        void mergeSameScale()
        {
            IntTimeSeriesEntry entry1 = new IntTimeSeriesEntry();
            entry1.updateValue(1, false);
            entry1.updateValue(30, true);
            entry1.updateValue(70, false); // high bit 6

            IntTimeSeriesEntry entry2 = new IntTimeSeriesEntry();
            entry2.updateValue(3, true);
            entry2.updateValue(40, true);
            entry2.updateValue(80, false); // high bit 16

            IntTimeSeriesEntry result = entry1.merge(entry2);

            assertThat(result).isSameAs(entry1);
            assertThat(entry1.getCount()).isEqualTo(6);
            assertThat(entry1.getConcurrentCount()).isEqualTo(3);
            assertThat(entry1.getErrorCount()).isEqualTo(3); // 1 + 2 = 3
            assertThat(entry1.getTotalValue()).isEqualTo(1 + 30 + 70 + 3 + 40 + 80);
            assertThat(entry1.getMinimumValue()).isEqualTo(1);
            assertThat(entry1.getMaximumValue()).isEqualTo(80);
            assertThat(entry1.getValues()).contains(1.0, 3.0, 30.0, 40.0, 70.0, 80.0);
        }

        @Test
        @DisplayName("Merge when this has smaller scale than other by exactly 1")
        void mergeSmallerScale()
        {
            IntTimeSeriesEntry smallScale = new IntTimeSeriesEntry();
            smallScale.updateValue(10, true);
            smallScale.updateValue(70, false); // scale = 0

            IntTimeSeriesEntry largeScale = new IntTimeSeriesEntry();
            largeScale.updateValue(130, true); // scale = 1 (130 >> 1 = 65 < 128)
            largeScale.updateValue(140, false);

            IntTimeSeriesEntry result = smallScale.merge(largeScale);

            assertThat(result).isSameAs(smallScale);
            assertThat(smallScale.getCount()).isEqualTo(4);
            assertThat(smallScale.getErrorCount()).isEqualTo(2);
            assertThat(smallScale.getTotalValue()).isEqualTo(10 + 70 + 130 + 140);
            assertThat(smallScale.getMinimumValue()).isEqualTo(10);
            assertThat(smallScale.getMaximumValue()).isEqualTo(140);
            assertThat(smallScale.getValues()).contains(10.0, 134.0, 130.0, 140.0);
        }

        @Test
        @DisplayName("Merge when this has larger scale than other by exactly 1")
        void mergeLargerScale()
        {
            IntTimeSeriesEntry largeScale = new IntTimeSeriesEntry();
            largeScale.updateValue(130, true); // scale = 1
            largeScale.updateValue(140, false);

            IntTimeSeriesEntry smallScale = new IntTimeSeriesEntry();
            smallScale.updateValue(10, true); // scale = 0
            smallScale.updateValue(70, false);

            IntTimeSeriesEntry result = largeScale.merge(smallScale);

            assertThat(result).isSameAs(largeScale);
            assertThat(largeScale.getCount()).isEqualTo(4);
            assertThat(largeScale.getErrorCount()).isEqualTo(2);
            assertThat(largeScale.getTotalValue()).isEqualTo(10 + 70 + 130 + 140);
            assertThat(largeScale.getMinimumValue()).isEqualTo(10);
            assertThat(largeScale.getMaximumValue()).isEqualTo(140);
            assertThat(largeScale.getValues()).contains(10.0, 134.0, 130.0, 140.0);
        }

        @Test
        @DisplayName("Merge with unequal scales and distinctValuesHigh bits in both")
        void mergeWithHighBitsAndScaleDifferences() throws Exception
        {
            // Set up scale = 0 with distinctValuesHigh non-zero
            IntTimeSeriesEntry entryA = new IntTimeSeriesEntry();
            entryA.updateValue(66, false); // in high part

            // Set up scale = 2 with distinctValuesHigh non-zero
            IntTimeSeriesEntry entryB = new IntTimeSeriesEntry();
            entryB.updateValue(500, false); // triggers scale 2 (500 >> 2 = 125, in high part)

            entryA.merge(entryB);
            assertThat(entryA.getValues()).isNotEmpty();
            assertThat(entryA.getCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Equals and Contract")
    class EqualsTest
    {
        @Test
        @DisplayName("Verifies equals reflexivity, symmetry, and field checks")
        void equalsContract() throws Exception
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
            setField(eDiffCount, "count", 999);
            assertThat(e1).isNotEqualTo(eDiffCount);

            // Modify maximum
            IntTimeSeriesEntry eDiffMax = new IntTimeSeriesEntry(10, false);
            setField(eDiffMax, "maximum", 999);
            assertThat(e1).isNotEqualTo(eDiffMax);

            // Modify minimum
            IntTimeSeriesEntry eDiffMin = new IntTimeSeriesEntry(10, false);
            setField(eDiffMin, "minimum", 999);
            assertThat(e1).isNotEqualTo(eDiffMin);

            // Modify totalValue
            IntTimeSeriesEntry eDiffTotal = new IntTimeSeriesEntry(10, false);
            setField(eDiffTotal, "totalValue", 999L);
            assertThat(e1).isNotEqualTo(eDiffTotal);

            // Modify error count
            IntTimeSeriesEntry eDiffError = new IntTimeSeriesEntry(10, true);
            assertThat(e1).isNotEqualTo(eDiffError);

            // Modify distinctValuesLow
            IntTimeSeriesEntry eDiffLow = new IntTimeSeriesEntry(10, false);
            setField(eDiffLow, "distinctValuesLow", 999L);
            assertThat(e1).isNotEqualTo(eDiffLow);

            // Modify distinctValuesHigh
            IntTimeSeriesEntry eDiffHigh = new IntTimeSeriesEntry(10, false);
            setField(eDiffHigh, "distinctValuesHigh", 999L);
            assertThat(e1).isNotEqualTo(eDiffHigh);

            // Modify concurrency
            IntTimeSeriesEntry eDiffConc = new IntTimeSeriesEntry(10, false);
            setField(eDiffConc, "concurrentCount", 999);
            assertThat(e1).isNotEqualTo(eDiffConc);

            // Modify distinctValuesScale
            IntTimeSeriesEntry eDiffScale = new IntTimeSeriesEntry(10, false);
            setField(eDiffScale, "distinctValuesScale", 999);
            assertThat(e1).isNotEqualTo(eDiffScale);
        }

        private void setField(Object target, String fieldName, Object value) throws Exception
        {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }
    }
}
