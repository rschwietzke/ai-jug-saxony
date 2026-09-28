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
package com.xceptance.xlt.report.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class RuntimeHistogramTest
{
    @Nested
    @DisplayName("Constructor and basic state tests")
    class ConstructorTests
    {
        @Test
        @DisplayName("Default constructor initializes with precision 1 and empty state")
        void testDefaultConstructor()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            assertThat(rh.isEmpty()).isTrue();
            assertThat(rh.getValueCount()).isEqualTo(0);
            assertThat(rh.getNumberOfBuckets()).isEqualTo(0);
            assertThat(rh.getPrecision()).isEqualTo(1);
            assertThat(rh.getMedianValue()).isEqualTo(0.0);
            assertThat(rh.getPercentile(50.0)).isEqualTo(0.0);
            assertThat(rh.getQuantile(0.5)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Precision is normalized to next highest power of two")
        void testPrecisionRounding()
        {
            RuntimeHistogram rh1 = new RuntimeHistogram(1);
            assertThat(rh1.getPrecision()).isEqualTo(1);

            RuntimeHistogram rh2 = new RuntimeHistogram(2);
            assertThat(rh2.getPrecision()).isEqualTo(2);

            RuntimeHistogram rh3 = new RuntimeHistogram(3);
            assertThat(rh3.getPrecision()).isEqualTo(4);

            RuntimeHistogram rh8 = new RuntimeHistogram(8);
            assertThat(rh8.getPrecision()).isEqualTo(8);

            RuntimeHistogram rh10 = new RuntimeHistogram(10);
            assertThat(rh10.getPrecision()).isEqualTo(16);
        }
    }

    @Nested
    @DisplayName("Dynamic array growth and bucket management")
    class GrowthTests
    {
        @Test
        @DisplayName("First value initializes single bucket")
        void testFirstValueInitialization()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            rh.addValue(50);

            assertThat(rh.isEmpty()).isFalse();
            assertThat(rh.getValueCount()).isEqualTo(1);
            assertThat(rh.getNumberOfBuckets()).isEqualTo(1);
            assertThat(rh.getCountForValue(50, 50)).isEqualTo(1);
        }

        @Test
        @DisplayName("Adding larger value grows array to the right")
        void testGrowRight()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            rh.addValue(10);
            rh.addValue(15);

            assertThat(rh.getValueCount()).isEqualTo(2);
            assertThat(rh.getNumberOfBuckets()).isEqualTo(6); // 10..15 inclusive is 6 buckets
            assertThat(rh.getCountForValue(10, 10)).isEqualTo(1);
            assertThat(rh.getCountForValue(15, 15)).isEqualTo(1);
            assertThat(rh.getCountForValue(11, 14)).isEqualTo(0);
        }

        @Test
        @DisplayName("Adding smaller value grows array to the left")
        void testGrowLeft()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            rh.addValue(20);
            rh.addValue(10);

            assertThat(rh.getValueCount()).isEqualTo(2);
            assertThat(rh.getNumberOfBuckets()).isEqualTo(11); // 10..20 inclusive is 11 buckets
            assertThat(rh.getCountForValue(10, 10)).isEqualTo(1);
            assertThat(rh.getCountForValue(20, 20)).isEqualTo(1);
            assertThat(rh.getCountForValue(11, 19)).isEqualTo(0);
        }

        @Test
        @DisplayName("Adding values into existing range increments middle buckets")
        void testIncrementExistingBuckets()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            rh.addValue(10);
            rh.addValue(20);
            rh.addValue(15);
            rh.addValue(15);
            rh.addValue(10);

            assertThat(rh.getValueCount()).isEqualTo(5);
            assertThat(rh.getCountForValue(10, 10)).isEqualTo(2);
            assertThat(rh.getCountForValue(15, 15)).isEqualTo(2);
            assertThat(rh.getCountForValue(20, 20)).isEqualTo(1);
            assertThat(rh.getCountForValue(10, 20)).isEqualTo(5);
        }

        @Test
        @DisplayName("Supports negative values via signed shifting")
        void testNegativeValues()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            rh.addValue(-10);
            rh.addValue(10);
            rh.addValue(-5);

            assertThat(rh.getValueCount()).isEqualTo(3);
            assertThat(rh.getCountForValue(-10, -10)).isEqualTo(1);
            assertThat(rh.getCountForValue(-5, -5)).isEqualTo(1);
            assertThat(rh.getCountForValue(10, 10)).isEqualTo(1);
            assertThat(rh.getCountForValue(-10, 10)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Percentile and Quantile tests")
    class PercentileTests
    {
        @Test
        @DisplayName("Percentile parameter validation")
        void testInvalidPercentiles()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            rh.addValue(10);

            assertThatIllegalArgumentException().isThrownBy(() -> rh.getPercentile(-0.01));
            assertThatIllegalArgumentException().isThrownBy(() -> rh.getPercentile(100.01));
        }

        @Test
        @DisplayName("Percentiles 0 and 100 return minimum and maximum")
        void testBoundaryPercentiles()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            rh.addValue(10);
            rh.addValue(50);
            rh.addValue(30);

            assertThat(rh.getPercentile(0.0)).isEqualTo(10.0);
            assertThat(rh.getPercentile(100.0)).isEqualTo(50.0);
        }

        @Test
        @DisplayName("Odd count percentiles")
        void testOddCountPercentiles()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            // Values: 10, 20, 30, 40, 50 (5 values)
            rh.addValue(10);
            rh.addValue(20);
            rh.addValue(30);
            rh.addValue(40);
            rh.addValue(50);

            // np = 5 * 0.5 = 2.5 -> ceil is 3 -> 3rd value is 30
            assertThat(rh.getMedianValue()).isEqualTo(30.0);
            assertThat(rh.getQuantile(0.5)).isEqualTo(30.0);

            // np = 5 * 0.2 = 1.0 (even integer -> mean of 1st and 2nd -> (10+20)/2 = 15.0)
            assertThat(rh.getPercentile(20.0)).isEqualTo(15.0);

            // np = 5 * 0.8 = 4.0 (even integer -> mean of 4th and 5th -> (40+50)/2 = 45.0)
            assertThat(rh.getPercentile(80.0)).isEqualTo(45.0);
        }

        @Test
        @DisplayName("Even count percentiles with interpolation")
        void testEvenCountPercentiles()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            // Values: 10, 20, 30, 40 (4 values)
            rh.addValue(10);
            rh.addValue(20);
            rh.addValue(30);
            rh.addValue(40);

            // np = 4 * 0.5 = 2.0 (integer -> mean of 2nd and 3rd -> (20+30)/2 = 25.0)
            assertThat(rh.getMedianValue()).isEqualTo(25.0);

            // np = 4 * 0.25 = 1.0 (mean of 1st and 2nd -> (10+20)/2 = 15.0)
            assertThat(rh.getPercentile(25.0)).isEqualTo(15.0);

            // np = 4 * 0.75 = 3.0 (mean of 3rd and 4th -> (30+40)/2 = 35.0)
            assertThat(rh.getPercentile(75.0)).isEqualTo(35.0);
        }

        @Test
        @DisplayName("Percentile with coarse precision (e.g., precision = 8)")
        void testCoarsePrecision()
        {
            RuntimeHistogram rh = new RuntimeHistogram(8); // bucket width is 8
            rh.addValue(10); // bucket 1 (8..15) -> value reconstructed as 1 << 3 = 8
            rh.addValue(12); // bucket 1
            rh.addValue(35); // bucket 4 (32..39) -> value reconstructed as 4 << 3 = 32

            assertThat(rh.getValueCount()).isEqualTo(3);
            assertThat(rh.getPercentile(0.0)).isEqualTo(8.0);
            assertThat(rh.getPercentile(100.0)).isEqualTo(32.0);
        }
    }

    @Nested
    @DisplayName("getCountForValue range query tests")
    class CountForValueTests
    {
        @Test
        @DisplayName("Throws exception if start > end")
        void testInvalidRange()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            assertThatIllegalArgumentException().isThrownBy(() -> rh.getCountForValue(50, 40));
        }

        @Test
        @DisplayName("Returns 0 when empty")
        void testEmptyCount()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            assertThat(rh.getCountForValue(10, 20)).isEqualTo(0);
        }

        @Test
        @DisplayName("Returns 0 when range is completely outside data")
        void testOutsideRange()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            rh.addValue(100);
            rh.addValue(200);

            assertThat(rh.getCountForValue(0, 50)).isEqualTo(0);
            assertThat(rh.getCountForValue(300, 400)).isEqualTo(0);
        }

        @Test
        @DisplayName("Returns correct count for partial overlaps and clamped ranges")
        void testPartialAndClampedRanges()
        {
            RuntimeHistogram rh = new RuntimeHistogram();
            rh.addValue(10);
            rh.addValue(20);
            rh.addValue(30);

            // Range starting before firstIndex and ending in the middle
            assertThat(rh.getCountForValue(0, 25)).isEqualTo(2); // 10 and 20

            // Range starting in the middle and ending after lastIndex
            assertThat(rh.getCountForValue(15, 50)).isEqualTo(2); // 20 and 30

            // Range encompassing all data
            assertThat(rh.getCountForValue(0, 100)).isEqualTo(3);

            // Point query
            assertThat(rh.getCountForValue(20, 20)).isEqualTo(1);
            assertThat(rh.getCountForValue(15, 15)).isEqualTo(0);
        }
    }
}
