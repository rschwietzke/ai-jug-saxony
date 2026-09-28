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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RuntimeHistogramTest
{
    @Test
    public void defaultConstructor()
    {
        RuntimeHistogram hist = new RuntimeHistogram();
        assertTrue(hist.isEmpty());
        assertEquals(0, hist.getValueCount());
        assertEquals(1, hist.getPrecision());
        assertEquals(0, hist.getNumberOfBuckets());
        assertEquals(0.0, hist.getPercentile(50.0));
    }

    @Test
    public void customPrecisionConstructor()
    {
        RuntimeHistogram hist = new RuntimeHistogram(8);
        assertEquals(8, hist.getPrecision());

        RuntimeHistogram hist16 = new RuntimeHistogram(15);
        assertEquals(16, hist16.getPrecision());
    }

    @Test
    public void addSingleValue()
    {
        RuntimeHistogram hist = new RuntimeHistogram();
        hist.addValue(100);

        assertFalse(hist.isEmpty());
        assertEquals(1, hist.getValueCount());
        assertEquals(1, hist.getNumberOfBuckets());
        assertEquals(100.0, hist.getPercentile(0.0));
        assertEquals(100.0, hist.getPercentile(50.0));
        assertEquals(100.0, hist.getPercentile(100.0));
        assertEquals(100.0, hist.getMedianValue());
        assertEquals(100.0, hist.getQuantile(0.5));
    }

    @Test
    public void growLeftAndRight()
    {
        RuntimeHistogram hist = new RuntimeHistogram(1);
        hist.addValue(50);
        assertEquals(1, hist.getNumberOfBuckets());

        // Grow right
        hist.addValue(55);
        assertEquals(6, hist.getNumberOfBuckets()); // 50 to 55 inclusive is 6 buckets

        // Grow left
        hist.addValue(45);
        assertEquals(11, hist.getNumberOfBuckets()); // 45 to 55 inclusive is 11 buckets

        // Add within existing range
        hist.addValue(48);
        assertEquals(11, hist.getNumberOfBuckets());
        assertEquals(4, hist.getValueCount());
    }

    @Test
    public void percentilesWithEvenAndOddCounts()
    {
        RuntimeHistogram hist = new RuntimeHistogram(1);
        hist.addValue(10);
        hist.addValue(20);
        hist.addValue(30);

        // Odd count = 3
        // Median is at 50%: np = 3 * 0.5 = 1.5 -> ceil(1.5) = 2 -> second value = 20
        assertEquals(20.0, hist.getMedianValue());
        assertEquals(10.0, hist.getPercentile(0.0));
        assertEquals(30.0, hist.getPercentile(100.0));

        // Add 4th value: 40 -> even count = 4
        // 50% -> np = 4 * 0.5 = 2.0 (integer) -> mean of value 2 (20) and value 3 (30) = 25.0
        hist.addValue(40);
        assertEquals(25.0, hist.getMedianValue());
    }

    @Test
    public void percentileBoundaryExceptions()
    {
        RuntimeHistogram hist = new RuntimeHistogram();
        hist.addValue(10);

        assertThrows(IllegalArgumentException.class, () -> hist.getPercentile(-0.1));
        assertThrows(IllegalArgumentException.class, () -> hist.getPercentile(100.1));
    }

    @Test
    public void getCountForValue()
    {
        RuntimeHistogram hist = new RuntimeHistogram(1);
        assertEquals(0, hist.getCountForValue(0, 100)); // Empty

        hist.addValue(10);
        hist.addValue(20);
        hist.addValue(20);
        hist.addValue(30);

        assertEquals(1, hist.getCountForValue(10, 10));
        assertEquals(2, hist.getCountForValue(20, 20));
        assertEquals(3, hist.getCountForValue(10, 20));
        assertEquals(4, hist.getCountForValue(0, 100));
        assertEquals(4, hist.getCountForValue(10, 30));

        // Outside ranges
        assertEquals(0, hist.getCountForValue(0, 5));
        assertEquals(0, hist.getCountForValue(35, 100));

        // Partially overlapping ranges
        assertEquals(1, hist.getCountForValue(5, 15));
        assertEquals(1, hist.getCountForValue(25, 35));

        // Invalid range
        assertThrows(IllegalArgumentException.class, () -> hist.getCountForValue(50, 40));
    }

    @Test
    public void withPrecisionScaling()
    {
        // Precision 8 -> values 0-7 bucket 0, 8-15 bucket 1, etc.
        RuntimeHistogram hist = new RuntimeHistogram(8);
        hist.addValue(5);  // index 0 -> reconstructed value = 0 << 3 = 0
        hist.addValue(12); // index 1 -> reconstructed value = 1 << 3 = 8
        hist.addValue(20); // index 2 -> reconstructed value = 2 << 3 = 16

        assertEquals(3, hist.getValueCount());
        assertEquals(3, hist.getNumberOfBuckets());
        assertEquals(8.0, hist.getMedianValue());
        assertEquals(0.0, hist.getPercentile(0.0));
        assertEquals(16.0, hist.getPercentile(100.0));
    }
}
