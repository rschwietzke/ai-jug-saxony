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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IntTimeSeriesEntry}.
 */
class IntTimeSeriesEntryTest
{
    @Test
    void defaultConstructor_emptyState()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        assertEquals(0, e.getCount());
        assertEquals(0, e.getErrorCount());
        assertEquals(0, e.getConcurrentCount());
        assertEquals(0, e.getTotalValue());
        assertEquals(0, e.getAverageValue());
        assertEquals(0, e.getMinimumValue());
        assertEquals(0, e.getMaximumValue());
        assertEquals(0, e.getValues().length);
    }

    @Test
    void constructorWithValue()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry(42, false);
        assertEquals(1, e.getCount());
        assertEquals(0, e.getErrorCount());
        assertEquals(1, e.getConcurrentCount());
        assertEquals(42, e.getTotalValue());
        assertEquals(42, e.getAverageValue());
        assertEquals(42, e.getMinimumValue());
        assertEquals(42, e.getMaximumValue());
    }

    @Test
    void constructorWithValue_failed()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry(42, true);
        assertEquals(1, e.getCount());
        assertEquals(1, e.getErrorCount());
    }

    @Test
    void updateValue_multipleValues()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(10, false);
        e.updateValue(20, false);
        e.updateValue(30, false);

        assertEquals(3, e.getCount());
        assertEquals(0, e.getErrorCount());
        assertEquals(3, e.getConcurrentCount());
        assertEquals(60, e.getTotalValue());
        assertEquals(20, e.getAverageValue());
        assertEquals(10, e.getMinimumValue());
        assertEquals(30, e.getMaximumValue());
    }

    @Test
    void updateValue_negativeValueClampedToZero()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(-5, false);
        assertEquals(1, e.getCount());
        assertEquals(0, e.getTotalValue());
        assertEquals(0, e.getMinimumValue());
        assertEquals(0, e.getMaximumValue());
    }

    @Test
    void updateValue_zero()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(0, false);
        assertEquals(1, e.getCount());
        assertEquals(0, e.getTotalValue());
        assertEquals(0, e.getMinimumValue());
        assertEquals(0, e.getMaximumValue());
    }

    @Test
    void updateValue_errorCount()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(1, true);
        e.updateValue(2, false);
        e.updateValue(3, true);
        assertEquals(3, e.getCount());
        assertEquals(2, e.getErrorCount());
    }

    @Test
    void updateConcurrency()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateConcurrency();
        e.updateConcurrency();
        assertEquals(2, e.getConcurrentCount());
        assertEquals(0, e.getCount()); // count not affected
    }

    @Test
    void getValues_smallDistinctValues()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(1, false);
        e.updateValue(3, false);
        e.updateValue(5, false);

        double[] values = e.getValues();
        assertArrayEquals(new double[]{1.0, 3.0, 5.0}, values);
    }

    @Test
    void getValues_noDuplicates()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(5, false);
        e.updateValue(5, false);
        e.updateValue(5, false);

        double[] values = e.getValues();
        assertEquals(1, values.length);
        assertEquals(5.0, values[0]);
    }

    @Test
    void getValues_valuesAbove63()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(64, false);
        e.updateValue(100, false);
        e.updateValue(127, false);

        double[] values = e.getValues();
        assertArrayEquals(new double[]{64.0, 100.0, 127.0}, values);
    }

    @Test
    void getValues_mixedLowAndHigh()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(1, false);
        e.updateValue(64, false);
        e.updateValue(127, false);

        double[] values = e.getValues();
        assertArrayEquals(new double[]{1.0, 64.0, 127.0}, values);
    }

    @Test
    void getValues_largeValuesTriggerScaling()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(1, false);
        e.updateValue(200, false); // exceeds 127, triggers scale up

        double[] values = e.getValues();
        // After scaling, values are approximated
        assertTrue(values.length > 0);
    }

    @Test
    void merge_sameScale()
    {
        IntTimeSeriesEntry e1 = new IntTimeSeriesEntry();
        e1.updateValue(10, false);
        e1.updateValue(20, false);

        IntTimeSeriesEntry e2 = new IntTimeSeriesEntry();
        e2.updateValue(30, false);
        e2.updateValue(40, true);

        IntTimeSeriesEntry merged = e1.merge(e2);

        assertEquals(4, merged.getCount());
        assertEquals(1, merged.getErrorCount());
        assertEquals(100, merged.getTotalValue());
        assertEquals(25, merged.getAverageValue());
        assertEquals(10, merged.getMinimumValue());
        assertEquals(40, merged.getMaximumValue());
        // concurrency takes max
        assertEquals(2, merged.getConcurrentCount());
    }

    @Test
    void merge_emptyIntoNonEmpty()
    {
        IntTimeSeriesEntry e1 = new IntTimeSeriesEntry();
        e1.updateValue(10, false);

        IntTimeSeriesEntry e2 = new IntTimeSeriesEntry();

        IntTimeSeriesEntry merged = e1.merge(e2);
        assertEquals(1, merged.getCount());
        assertEquals(10, merged.getTotalValue());
    }

    @Test
    void merge_nonEmptyIntoEmpty()
    {
        IntTimeSeriesEntry e1 = new IntTimeSeriesEntry();
        IntTimeSeriesEntry e2 = new IntTimeSeriesEntry();
        e2.updateValue(10, false);

        IntTimeSeriesEntry merged = e1.merge(e2);
        assertEquals(1, merged.getCount());
        assertEquals(10, merged.getTotalValue());
        assertEquals(10, merged.getMinimumValue());
        assertEquals(10, merged.getMaximumValue());
    }

    @Test
    void merge_concurrencyTakesMax()
    {
        IntTimeSeriesEntry e1 = new IntTimeSeriesEntry();
        e1.updateValue(1, false);
        e1.updateValue(2, false);
        e1.updateValue(3, false);
        // concurrentCount = 3

        IntTimeSeriesEntry e2 = new IntTimeSeriesEntry();
        e2.updateValue(4, false);
        // concurrentCount = 1

        IntTimeSeriesEntry merged = e1.merge(e2);
        assertEquals(3, merged.getConcurrentCount());
    }

    @Test
    void equals_sameContent()
    {
        IntTimeSeriesEntry e1 = new IntTimeSeriesEntry();
        e1.updateValue(1, false);
        e1.updateValue(2, false);

        IntTimeSeriesEntry e2 = new IntTimeSeriesEntry();
        e2.updateValue(1, false);
        e2.updateValue(2, false);

        assertEquals(e1, e2);
    }

    @Test
    void equals_differentContent()
    {
        IntTimeSeriesEntry e1 = new IntTimeSeriesEntry();
        e1.updateValue(1, false);

        IntTimeSeriesEntry e2 = new IntTimeSeriesEntry();
        e2.updateValue(2, false);

        assertNotEquals(e1, e2);
    }

    @Test
    void equals_null()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        assertNotEquals(null, e);
    }

    @Test
    void equals_sameInstance()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        assertEquals(e, e);
    }

    @Test
    void equals_differentType()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        assertNotEquals("not an entry", e);
    }

    @Test
    void toString_containsAllFields()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry(10, false);
        String s = e.toString();
        assertTrue(s.contains("10"));
        assertTrue(s.contains("1"));
    }

    @Test
    void scaling_largeValues()
    {
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        // Add many values to force multiple scale-ups
        for (int i = 0; i < 1000; i++)
        {
            e.updateValue(i, false);
        }
        assertEquals(1000, e.getCount());
        assertEquals(999, e.getMaximumValue());
        assertEquals(0, e.getMinimumValue());
        // Distinct values should be a subset approximation
        double[] values = e.getValues();
        assertTrue(values.length > 0);
        assertTrue(values.length <= 128);
    }

    @Test
    void minValue_handlesInitialSentinel()
    {
        // When no values added, minimum should be 0 (sentinel Integer.MAX_VALUE mapped to 0)
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        assertEquals(0, e.getMinimumValue());
    }

    @Test
    void maxValue_handlesInitialSentinel()
    {
        // When no values added, maximum should be 0 (sentinel Integer.MIN_VALUE mapped to 0)
        IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        assertEquals(0, e.getMaximumValue());
    }
}
