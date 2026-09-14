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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RuntimeHistogram}.
 */
class RuntimeHistogramTest
{
    @Test
    void emptyHistogram()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        assertEquals(0, h.getValueCount());
        assertTrue(h.isEmpty());
        assertEquals(0, h.getNumberOfBuckets());
        assertEquals(0.0, h.getMedianValue());
        assertEquals(0.0, h.getPercentile(50.0));
    }

    @Test
    void defaultConstructor_usesPrecisionOne()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        assertEquals(1, h.getPrecision());
    }

    @Test
    void constructor_powerOfTwoPrecision()
    {
        assertEquals(1, new RuntimeHistogram(1).getPrecision());
        assertEquals(2, new RuntimeHistogram(2).getPrecision());
        assertEquals(4, new RuntimeHistogram(4).getPrecision());
        assertEquals(8, new RuntimeHistogram(8).getPrecision());
        assertEquals(16, new RuntimeHistogram(16).getPrecision());
    }

    @Test
    void constructor_nonPowerOfTwoRoundsUp()
    {
        // nextHighestPowerOfTwo(3)=4 -> getPrecision() returns 4
        assertEquals(4, new RuntimeHistogram(3).getPrecision());
        // nextHighestPowerOfTwo(5)=8 -> getPrecision() returns 8
        assertEquals(8, new RuntimeHistogram(5).getPrecision());
    }

    @Test
    void addValue_singleValue()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(5);
        assertEquals(1, h.getValueCount());
        assertFalse(h.isEmpty());
        assertEquals(1, h.getNumberOfBuckets());
    }

    @Test
    void addValue_sameValueMultipleTimes()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        for (int i = 0; i < 10; i++)
        {
            h.addValue(7);
        }
        assertEquals(10, h.getValueCount());
        assertEquals(1, h.getNumberOfBuckets());
        assertEquals(7.0, h.getMedianValue());
    }

    @Test
    void addValue_growingLeft()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(10);
        h.addValue(5); // smaller -> grows left; buckets span indices 5..10
        assertEquals(2, h.getValueCount());
        assertEquals(6, h.getNumberOfBuckets());
    }

    @Test
    void addValue_growingRight()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(5);
        h.addValue(10); // larger -> grows right; buckets span indices 5..10
        assertEquals(2, h.getValueCount());
        assertEquals(6, h.getNumberOfBuckets());
    }

    @Test
    void addValue_largeRange()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(0);
        h.addValue(100);
        assertEquals(2, h.getValueCount());
        assertEquals(101, h.getNumberOfBuckets());
    }

    @Test
    void getPercentile_boundaries()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(10);
        h.addValue(20);
        h.addValue(30);

        assertEquals(10.0, h.getPercentile(0.0));
        assertEquals(30.0, h.getPercentile(100.0));
    }

    @Test
    void getPercentile_oddCount()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(1);
        h.addValue(2);
        h.addValue(3);
        // median = 2
        assertEquals(2.0, h.getPercentile(50.0));
    }

    @Test
    void getPercentile_evenCount()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(1);
        h.addValue(2);
        h.addValue(3);
        h.addValue(4);
        // median = (2+3)/2 = 2.5
        assertEquals(2.5, h.getPercentile(50.0));
    }

    @Test
    void getMedianValue_matchesPercentile50()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(1);
        h.addValue(5);
        h.addValue(100);
        assertEquals(h.getPercentile(50.0), h.getMedianValue());
    }

    @Test
    void getQuantile_matchesPercentile()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(1);
        h.addValue(2);
        h.addValue(3);
        h.addValue(4);
        h.addValue(5);
        assertEquals(h.getPercentile(50.0), h.getQuantile(0.5));
        assertEquals(h.getPercentile(90.0), h.getQuantile(0.9));
    }

    @Test
    void getPercentile_invalidRangeThrows()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(1);
        assertThrows(IllegalArgumentException.class, () -> h.getPercentile(-1.0));
        assertThrows(IllegalArgumentException.class, () -> h.getPercentile(100.1));
    }

    @Test
    void getPercentile_precisionTwo()
    {
        // precision=2 means bucket width 2, values 0-1 share bucket 0, 2-3 bucket 1, etc.
        RuntimeHistogram h = new RuntimeHistogram(2);
        h.addValue(0);
        h.addValue(2);
        h.addValue(4);
        assertEquals(3, h.getValueCount());
        assertEquals(3, h.getNumberOfBuckets());
        // median is 2
        assertEquals(2.0, h.getMedianValue());
    }

    @Test
    void getCountForValue_fullRange()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(1);
        h.addValue(2);
        h.addValue(3);
        h.addValue(4);
        h.addValue(5);
        assertEquals(5, h.getCountForValue(1, 5));
    }

    @Test
    void getCountForValue_partialRange()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(1);
        h.addValue(2);
        h.addValue(3);
        h.addValue(4);
        h.addValue(5);
        assertEquals(3, h.getCountForValue(2, 4));
    }

    @Test
    void getCountForValue_noOverlap()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(10);
        h.addValue(20);
        assertEquals(0, h.getCountForValue(30, 40));
        assertEquals(0, h.getCountForValue(0, 5));
    }

    @Test
    void getCountForValue_emptyHistogram()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        assertEquals(0, h.getCountForValue(0, 100));
    }

    @Test
    void getCountForValue_startGreaterThanEndThrows()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(1);
        assertThrows(IllegalArgumentException.class, () -> h.getCountForValue(10, 5));
    }

    @Test
    void getCountForValue_clampsToExistingRange()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(5);
        h.addValue(6);
        // ask for range that extends beyond stored values
        assertEquals(2, h.getCountForValue(0, 100));
        assertEquals(2, h.getCountForValue(5, 100));
        assertEquals(2, h.getCountForValue(0, 6));
    }

    @Test
    void getCountForValue_withPrecision()
    {
        // precision=2: bucket width 2, values 0-1 in bucket 0, 2-3 in bucket 1, etc.
        RuntimeHistogram h = new RuntimeHistogram(2);
        h.addValue(0);
        h.addValue(1); // same bucket as 0
        h.addValue(2);
        h.addValue(3); // same bucket as 2
        h.addValue(4);
        h.addValue(5); // same bucket as 4
        assertEquals(2, h.getCountForValue(0, 1));
        assertEquals(2, h.getCountForValue(2, 3));
        assertEquals(2, h.getCountForValue(4, 5));
        assertEquals(6, h.getCountForValue(0, 5));
    }

    @Test
    void stressTest_medianOfUniformDistribution()
    {
        RuntimeHistogram h = new RuntimeHistogram();
        for (int i = 1; i <= 100; i++)
        {
            h.addValue(i);
        }
        // With 100 values, median should be between 50 and 51
        double median = h.getMedianValue();
        assertTrue(median >= 50 && median <= 51, "median was " + median);
        assertEquals(100, h.getValueCount());
    }

    @Test
    void addValue_negativeValues()
    {
        // RuntimeHistogram uses right-shift on value; negative values become large positive indices
        // The implementation does not explicitly support negative values but let's document behavior
        RuntimeHistogram h = new RuntimeHistogram();
        h.addValue(-1);
        assertEquals(1, h.getValueCount());
    }
}
