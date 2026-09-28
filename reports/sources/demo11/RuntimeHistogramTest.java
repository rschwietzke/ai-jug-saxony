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
package com.xceptance.xlt.report.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RuntimeHistogram Tests")
class RuntimeHistogramTest
{
    @Nested
    @DisplayName("Constructor & Initial State")
    class InitialStateTest
    {
        @Test
        @DisplayName("Default constructor initializes correctly")
        void defaultConstructor()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            assertThat(hist.isEmpty()).isTrue();
            assertThat(hist.getValueCount()).isEqualTo(0);
            assertThat(hist.getNumberOfBuckets()).isEqualTo(0);
            assertThat(hist.getPrecision()).isEqualTo(1);
            assertThat(hist.getMedianValue()).isEqualTo(0.0);
            assertThat(hist.getPercentile(0.0)).isEqualTo(0.0);
            assertThat(hist.getPercentile(50.0)).isEqualTo(0.0);
            assertThat(hist.getPercentile(100.0)).isEqualTo(0.0);
            assertThat(hist.getCountForValue(0, 100)).isEqualTo(0L);
        }

        @Test
        @DisplayName("Custom precision is rounded to power of two")
        void customPrecision()
        {
            RuntimeHistogram hist1 = new RuntimeHistogram(1);
            assertThat(hist1.getPrecision()).isEqualTo(1);

            RuntimeHistogram hist2 = new RuntimeHistogram(2);
            assertThat(hist2.getPrecision()).isEqualTo(2);

            RuntimeHistogram hist7 = new RuntimeHistogram(7);
            assertThat(hist7.getPrecision()).isEqualTo(8);

            RuntimeHistogram hist8 = new RuntimeHistogram(8);
            assertThat(hist8.getPrecision()).isEqualTo(8);

            RuntimeHistogram hist9 = new RuntimeHistogram(9);
            assertThat(hist9.getPrecision()).isEqualTo(16);
        }
    }

    @Nested
    @DisplayName("Adding Values & Dynamic Array Growth")
    class AddValueTest
    {
        @Test
        @DisplayName("Single value creates 1 bucket")
        void singleValue()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            hist.addValue(42);

            assertThat(hist.isEmpty()).isFalse();
            assertThat(hist.getValueCount()).isEqualTo(1);
            assertThat(hist.getNumberOfBuckets()).isEqualTo(1);
            assertThat(hist.getMedianValue()).isEqualTo(42.0);
            assertThat(hist.getPercentile(0.0)).isEqualTo(42.0);
            assertThat(hist.getPercentile(100.0)).isEqualTo(42.0);
            assertThat(hist.getCountForValue(42, 42)).isEqualTo(1L);
            assertThat(hist.getCountForValue(0, 41)).isEqualTo(0L);
            assertThat(hist.getCountForValue(43, 100)).isEqualTo(0L);
        }

        @Test
        @DisplayName("Adding ascending values grows to the right")
        void ascendingGrowth()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            hist.addValue(10);
            hist.addValue(20);
            hist.addValue(30);

            assertThat(hist.getValueCount()).isEqualTo(3);
            assertThat(hist.getNumberOfBuckets()).isEqualTo(21); // indices 10 to 30 = 21 buckets
            assertThat(hist.getCountForValue(10, 10)).isEqualTo(1L);
            assertThat(hist.getCountForValue(20, 20)).isEqualTo(1L);
            assertThat(hist.getCountForValue(30, 30)).isEqualTo(1L);
            assertThat(hist.getCountForValue(10, 30)).isEqualTo(3L);
        }

        @Test
        @DisplayName("Adding descending values grows to the left (array shift)")
        void descendingGrowth()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            hist.addValue(30);
            hist.addValue(20);
            hist.addValue(10);

            assertThat(hist.getValueCount()).isEqualTo(3);
            assertThat(hist.getNumberOfBuckets()).isEqualTo(21);
            assertThat(hist.getCountForValue(10, 10)).isEqualTo(1L);
            assertThat(hist.getCountForValue(20, 20)).isEqualTo(1L);
            assertThat(hist.getCountForValue(30, 30)).isEqualTo(1L);
            assertThat(hist.getCountForValue(10, 30)).isEqualTo(3L);
        }

        @Test
        @DisplayName("Adding values within existing range increments middle buckets")
        void middleIncrement()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            hist.addValue(10);
            hist.addValue(30);
            hist.addValue(20);
            hist.addValue(20);
            hist.addValue(20);

            assertThat(hist.getValueCount()).isEqualTo(5);
            assertThat(hist.getCountForValue(20, 20)).isEqualTo(3L);
            assertThat(hist.getCountForValue(10, 30)).isEqualTo(5L);
        }

        @Test
        @DisplayName("Supports negative values and left growth into negative indices")
        void negativeValues()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            hist.addValue(-10);
            hist.addValue(10);
            hist.addValue(0);

            assertThat(hist.getValueCount()).isEqualTo(3);
            assertThat(hist.getNumberOfBuckets()).isEqualTo(21); // -10 to +10
            assertThat(hist.getCountForValue(-10, -10)).isEqualTo(1L);
            assertThat(hist.getCountForValue(0, 0)).isEqualTo(1L);
            assertThat(hist.getCountForValue(10, 10)).isEqualTo(1L);
            assertThat(hist.getPercentile(0.0)).isEqualTo(-10.0);
            assertThat(hist.getPercentile(100.0)).isEqualTo(10.0);
        }

        @Test
        @DisplayName("Direct invocation of grow via reflection validates boundary behavior")
        void directGrowBoundaryTest() throws Exception
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            hist.addValue(10);
            hist.addValue(20);

            Method growMethod = RuntimeHistogram.class.getDeclaredMethod("grow", int.class);
            growMethod.setAccessible(true);

            // Invoking grow with firstIndexValue (10) when firstIndexValue < lastIndexValue (20)
            // Under original code (newIndexPositionToSupport < firstIndexValue is false), it goes to else branch
            // delta = 10 - 20 = -10, copyOf shrinks array to 1 bucket
            growMethod.invoke(hist, 10);
            assertThat(hist.getNumberOfBuckets()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Percentile & Quantile Calculations")
    class PercentileTest
    {
        @Test
        @DisplayName("Invalid percentile parameter throws IllegalArgumentException")
        void invalidPercentile()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            hist.addValue(10);

            assertThatThrownBy(() -> hist.getPercentile(-0.1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> hist.getPercentile(100.1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Calculates percentiles for odd number of elements")
        void oddNumberOfElements()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            // 5 elements: 10, 20, 30, 40, 50
            for (int v = 10; v <= 50; v += 10)
            {
                hist.addValue(v);
            }

            assertThat(hist.getPercentile(0.0)).isEqualTo(10.0);
            assertThat(hist.getPercentile(100.0)).isEqualTo(50.0);
            // np = 5 * 0.5 = 2.5 -> ceil(2.5) = 3 -> 3rd value is 30
            assertThat(hist.getMedianValue()).isEqualTo(30.0);
            assertThat(hist.getQuantile(0.5)).isEqualTo(30.0);
            // np = 5 * 0.2 = 1.0 (even np) -> mean of value 1 (10) and value 2 (20) -> 15.0
            assertThat(hist.getPercentile(20.0)).isEqualTo(15.0);
            // np = 5 * 0.8 = 4.0 (even np) -> mean of value 4 (40) and value 5 (50) -> 45.0
            assertThat(hist.getPercentile(80.0)).isEqualTo(45.0);
        }

        @Test
        @DisplayName("Calculates percentiles for even number of elements")
        void evenNumberOfElements()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            // 4 elements: 10, 20, 30, 40
            for (int v = 10; v <= 40; v += 10)
            {
                hist.addValue(v);
            }

            // np = 4 * 0.5 = 2.0 (integer) -> mean of value 2 (20) and value 3 (30) -> 25.0
            assertThat(hist.getMedianValue()).isEqualTo(25.0);
            // np = 4 * 0.25 = 1.0 -> mean of value 1 (10) and value 2 (20) -> 15.0
            assertThat(hist.getPercentile(25.0)).isEqualTo(15.0);
            // np = 4 * 0.75 = 3.0 -> mean of value 3 (30) and value 4 (40) -> 35.0
            assertThat(hist.getPercentile(75.0)).isEqualTo(35.0);
        }

        @Test
        @DisplayName("High percentiles (90, 95, 99) on uniform distribution")
        void highPercentiles()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            for (int i = 1; i <= 100; i++)
            {
                hist.addValue(i);
            }

            assertThat(hist.getPercentile(0.0)).isEqualTo(1.0);
            assertThat(hist.getPercentile(50.0)).isEqualTo(50.5);
            assertThat(hist.getPercentile(90.0)).isEqualTo(90.5);
            assertThat(hist.getPercentile(95.0)).isEqualTo(95.5);
            assertThat(hist.getPercentile(99.0)).isEqualTo(99.5);
            assertThat(hist.getPercentile(100.0)).isEqualTo(100.0);
        }

        @Test
        @DisplayName("Percentiles with custom precision scale correctly")
        void percentilesWithPrecision()
        {
            RuntimeHistogram hist = new RuntimeHistogram(8); // precision shift = 3 (1<<3 = 8)
            hist.addValue(16); // bucket index 2 (16 >> 3 = 2)
            hist.addValue(32); // bucket index 4 (32 >> 3 = 4)
            hist.addValue(48); // bucket index 6 (48 >> 3 = 6)

            assertThat(hist.getPercentile(0.0)).isEqualTo(16.0);
            assertThat(hist.getPercentile(100.0)).isEqualTo(48.0);
            assertThat(hist.getMedianValue()).isEqualTo(32.0);
            assertThat(hist.getPercentile(50.0)).isEqualTo(32.0);
        }
    }

    @Nested
    @DisplayName("Range Count Queries (getCountForValue)")
    class RangeCountTest
    {
        @Test
        @DisplayName("Start greater than end throws IllegalArgumentException")
        void invalidRange()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            hist.addValue(10);

            assertThatThrownBy(() -> hist.getCountForValue(20, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Queries outside existing range return 0")
        void outsideRange()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            hist.addValue(50);
            hist.addValue(60);

            assertThat(hist.getCountForValue(0, 49)).isEqualTo(0L);
            assertThat(hist.getCountForValue(61, 100)).isEqualTo(0L);
        }

        @Test
        @DisplayName("Queries partially overlapping range clamp correctly")
        void partialOverlap()
        {
            RuntimeHistogram hist = new RuntimeHistogram();
            hist.addValue(10);
            hist.addValue(20);
            hist.addValue(30);

            // Left partial overlap (0 to 15 covers 10)
            assertThat(hist.getCountForValue(0, 15)).isEqualTo(1L);
            // Right partial overlap (25 to 50 covers 30)
            assertThat(hist.getCountForValue(25, 50)).isEqualTo(1L);
            // Enclosing range (0 to 100 covers all 3)
            assertThat(hist.getCountForValue(0, 100)).isEqualTo(3L);
        }

        @Test
        @DisplayName("Range count with custom precision shifts end and start correctly")
        void countWithPrecision()
        {
            RuntimeHistogram hist = new RuntimeHistogram(8); // shift = 3
            hist.addValue(16); // bucket index 2
            hist.addValue(24); // bucket index 3
            hist.addValue(32); // bucket index 4

            // Query range [16, 24] -> indices [2, 3] -> count = 2
            assertThat(hist.getCountForValue(16, 24)).isEqualTo(2L);
            // Query range [0, 8] -> outside (indices 0..1 < 2) -> 0
            assertThat(hist.getCountForValue(0, 8)).isEqualTo(0L);
            // Query range [40, 50] -> outside (indices 5..6 > 4) -> 0
            assertThat(hist.getCountForValue(40, 50)).isEqualTo(0L);
            // Query range [0, 100] -> covers all -> 3
            assertThat(hist.getCountForValue(0, 100)).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("Bucket Precision & Quantization")
    class PrecisionTest
    {
        @Test
        @DisplayName("Precision of 8 groups values into 8-unit buckets")
        void precision8()
        {
            RuntimeHistogram hist = new RuntimeHistogram(8);
            // Values 0..7 all shift by 3 (0>>3 = 0, 7>>3 = 0), so all fall in bucket 0
            for (int i = 0; i < 8; i++)
            {
                hist.addValue(i);
            }

            assertThat(hist.getNumberOfBuckets()).isEqualTo(1);
            assertThat(hist.getValueCount()).isEqualTo(8);
            assertThat(hist.getCountForValue(0, 7)).isEqualTo(8L);

            // Value 8 goes into bucket index 1 (8>>3 = 1)
            hist.addValue(8);
            assertThat(hist.getNumberOfBuckets()).isEqualTo(2);
            assertThat(hist.getValueCount()).isEqualTo(9);
            assertThat(hist.getCountForValue(8, 15)).isEqualTo(1L);
        }
    }
}
