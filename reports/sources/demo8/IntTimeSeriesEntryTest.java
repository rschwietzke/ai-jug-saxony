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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IntTimeSeriesEntryTest
{
    @Test
    public void defaultConstructor()
    {
        IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        assertEquals(0, entry.getCount());
        assertEquals(0, entry.getConcurrentCount());
        assertEquals(0, entry.getErrorCount());
        assertEquals(0L, entry.getTotalValue());
        assertEquals(0, entry.getAverageValue());
        assertEquals(0, entry.getMinimumValue());
        assertEquals(0, entry.getMaximumValue());
        assertEquals(0, entry.getValues().length);
    }

    @Test
    public void constructorWithInitialValue()
    {
        IntTimeSeriesEntry entrySuccess = new IntTimeSeriesEntry(50, false);
        assertEquals(1, entrySuccess.getCount());
        assertEquals(1, entrySuccess.getConcurrentCount());
        assertEquals(0, entrySuccess.getErrorCount());
        assertEquals(50L, entrySuccess.getTotalValue());
        assertEquals(50, entrySuccess.getAverageValue());
        assertEquals(50, entrySuccess.getMinimumValue());
        assertEquals(50, entrySuccess.getMaximumValue());
        assertArrayEquals(new double[]{50.0}, entrySuccess.getValues());

        IntTimeSeriesEntry entryFailed = new IntTimeSeriesEntry(100, true);
        assertEquals(1, entryFailed.getCount());
        assertEquals(1, entryFailed.getErrorCount());
    }

    @Test
    public void negativeValueTreatedAsZero()
    {
        IntTimeSeriesEntry entry = new IntTimeSeriesEntry(-10, false);
        assertEquals(0, entry.getMinimumValue());
        assertEquals(0, entry.getMaximumValue());
        assertEquals(0L, entry.getTotalValue());
    }

    @Test
    public void updateConcurrency()
    {
        IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateConcurrency();
        entry.updateConcurrency();
        assertEquals(2, entry.getConcurrentCount());
        assertEquals(0, entry.getCount());
    }

    @Test
    public void multipleValuesAveragingAndMinMax()
    {
        IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(10, false);
        entry.updateValue(20, false);
        entry.updateValue(30, true);

        assertEquals(3, entry.getCount());
        assertEquals(3, entry.getConcurrentCount());
        assertEquals(1, entry.getErrorCount());
        assertEquals(60L, entry.getTotalValue());
        assertEquals(20, entry.getAverageValue());
        assertEquals(10, entry.getMinimumValue());
        assertEquals(30, entry.getMaximumValue());
    }

    @Test
    public void distinctValuesWithoutScaling()
    {
        IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(0, false);
        entry.updateValue(5, false);
        entry.updateValue(63, false); // in low
        entry.updateValue(64, false); // in high
        entry.updateValue(127, false); // highest in high before scale

        double[] values = entry.getValues();
        assertEquals(5, values.length);
        assertArrayEquals(new double[]{0.0, 5.0, 63.0, 64.0, 127.0}, values);
    }

    @Test
    public void distinctValuesScalingWhenValueExceeds127()
    {
        IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(10, false);
        entry.updateValue(20, false);

        // Add 256 -> requires scaling (256 >> 1 = 128 -> 256 >> 2 = 64 < 128, scale becomes 2)
        entry.updateValue(256, false);

        double[] values = entry.getValues();
        // With scale = 2: multiplier is 1L << 2 = 4
        // 10 >> 2 = 2 -> 2 * 4 = 8.0
        // 20 >> 2 = 5 -> 5 * 4 = 20.0
        // 256 >> 2 = 64 -> 64 * 4 = 256.0
        assertTrue(values.length >= 3);
        assertEquals(256, entry.getMaximumValue());
        assertEquals(10, entry.getMinimumValue());
    }

    @Test
    public void mergeSameScale()
    {
        IntTimeSeriesEntry e1 = new IntTimeSeriesEntry();
        e1.updateValue(10, false);
        e1.updateValue(20, true);

        IntTimeSeriesEntry e2 = new IntTimeSeriesEntry();
        e2.updateValue(30, false);
        e2.updateValue(40, false);

        e1.merge(e2);

        assertEquals(4, e1.getCount());
        assertEquals(1, e1.getErrorCount());
        assertEquals(100L, e1.getTotalValue());
        assertEquals(25, e1.getAverageValue());
        assertEquals(10, e1.getMinimumValue());
        assertEquals(40, e1.getMaximumValue());
        assertEquals(2, e1.getConcurrentCount()); // max(2, 2)
    }

    @Test
    public void mergeDifferentScales()
    {
        // e1 with small values (scale 0)
        IntTimeSeriesEntry e1 = new IntTimeSeriesEntry();
        e1.updateValue(10, false);

        // e2 with large values (scale > 0)
        IntTimeSeriesEntry e2 = new IntTimeSeriesEntry();
        e2.updateValue(500, false); // requires scaling

        // Merge e2 into e1 (item has higher scale)
        e1.merge(e2);
        assertEquals(2, e1.getCount());
        assertEquals(510L, e1.getTotalValue());
        assertEquals(10, e1.getMinimumValue());
        assertEquals(500, e1.getMaximumValue());

        // Merge e1 into e2 (this has higher scale, item has lower scale)
        IntTimeSeriesEntry e3 = new IntTimeSeriesEntry();
        e3.updateValue(500, false);

        IntTimeSeriesEntry e4 = new IntTimeSeriesEntry();
        e4.updateValue(10, false);

        e3.merge(e4);
        assertEquals(2, e3.getCount());
        assertEquals(510L, e3.getTotalValue());
        assertEquals(10, e3.getMinimumValue());
        assertEquals(500, e3.getMaximumValue());
    }

    @Test
    public void equalsAndHashCodeContract()
    {
        IntTimeSeriesEntry e1 = new IntTimeSeriesEntry();
        e1.updateValue(10, false);

        IntTimeSeriesEntry e2 = new IntTimeSeriesEntry();
        e2.updateValue(10, false);

        IntTimeSeriesEntry e3 = new IntTimeSeriesEntry();
        e3.updateValue(20, false);

        assertEquals(e1, e1);
        assertEquals(e1, e2);
        assertNotEquals(e1, e3);
        assertNotEquals(e1, null);
        assertNotEquals(e1, "someString");

        // Test field inequalities in equals
        IntTimeSeriesEntry diffCount = new IntTimeSeriesEntry();
        diffCount.updateValue(10, false);
        diffCount.updateValue(0, false);
        assertNotEquals(e1, diffCount);

        IntTimeSeriesEntry diffMax = new IntTimeSeriesEntry();
        diffMax.updateValue(15, false);
        assertNotEquals(e1, diffMax);

        IntTimeSeriesEntry diffError = new IntTimeSeriesEntry();
        diffError.updateValue(10, true);
        assertNotEquals(e1, diffError);

        IntTimeSeriesEntry diffConcurrency = new IntTimeSeriesEntry();
        diffConcurrency.updateValue(10, false);
        diffConcurrency.updateConcurrency();
        assertNotEquals(e1, diffConcurrency);
    }

    @Test
    public void toStringOutput()
    {
        IntTimeSeriesEntry entry = new IntTimeSeriesEntry(10, false);
        String s = entry.toString();
        assertNotNull(s);
        assertTrue(s.contains("1 / 1 / 0 / 10 / 10 / 10 / 10"));
    }
}
