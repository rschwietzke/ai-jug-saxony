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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntTimeSeriesEntryTest
{
    @Test
    void emptyEntryReturnsNeutralValues()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();

        assertEquals(0, entry.getCount());
        assertEquals(0, entry.getConcurrentCount());
        assertEquals(0, entry.getErrorCount());
        assertEquals(0, entry.getTotalValue());
        assertEquals(0, entry.getAverageValue());
        assertEquals(0, entry.getMinimumValue());
        assertEquals(0, entry.getMaximumValue());
        assertArrayEquals(new double[0], entry.getValues());
        assertEquals("0 / 0 / 0 / 0 / 0 / 0 / 0 / []\n", entry.toString());
    }

    @Test
    void updatesMaintainAggregatesErrorsConcurrencyAndDistinctValues()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry(-5, true);
        entry.updateValue(10, false);
        entry.updateValue(20, true);
        entry.updateConcurrency();
        entry.updateConcurrency();

        assertEquals(3, entry.getCount());
        assertEquals(5, entry.getConcurrentCount());
        assertEquals(2, entry.getErrorCount());
        assertEquals(30, entry.getTotalValue());
        assertEquals(10, entry.getAverageValue());
        assertEquals(0, entry.getMinimumValue());
        assertEquals(20, entry.getMaximumValue());
        assertArrayEquals(new double[] {0, 10, 20}, entry.getValues());
    }

    @Test
    void averageUsesIntegerDivision()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry(1, false);
        entry.updateValue(2, false);

        assertEquals(1, entry.getAverageValue());
    }

    @Test
    void distinctValuesAdaptivelyFoldAdjacentBuckets()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        for (final int value : new int[] {1, 2, 63, 64, 127, 128, 255, 512})
        {
            entry.updateValue(value, false);
        }

        assertArrayEquals(new double[] {0, 56, 64, 120, 128, 248, 512}, entry.getValues());
        assertEquals(8, entry.getCount());
        assertEquals(1_152, entry.getTotalValue());
        assertEquals(1, entry.getMinimumValue());
        assertEquals(512, entry.getMaximumValue());
    }

    @Test
    void mergeCombinesAggregatesAndUsesMaximumConcurrency()
    {
        final IntTimeSeriesEntry first = new IntTimeSeriesEntry(1, false);
        first.updateValue(3, true);
        first.updateConcurrency();
        first.updateConcurrency();

        final IntTimeSeriesEntry second = new IntTimeSeriesEntry(2, true);
        second.updateValue(4, false);
        second.updateConcurrency();

        assertSame(first, first.merge(second));
        assertEquals(4, first.getCount());
        assertEquals(4, first.getConcurrentCount());
        assertEquals(2, first.getErrorCount());
        assertEquals(10, first.getTotalValue());
        assertEquals(2, first.getAverageValue());
        assertEquals(1, first.getMinimumValue());
        assertEquals(4, first.getMaximumValue());
        assertArrayEquals(new double[] {1, 2, 3, 4}, first.getValues());
    }

    @Test
    void mergeAlignsDifferentDistinctValueScalesInEitherDirection()
    {
        final IntTimeSeriesEntry lowerScaleReceiver = new IntTimeSeriesEntry(20, false);
        lowerScaleReceiver.merge(new IntTimeSeriesEntry(128, false));
        assertArrayEquals(new double[] {20, 128}, lowerScaleReceiver.getValues());

        final IntTimeSeriesEntry higherScaleReceiver = new IntTimeSeriesEntry(128, false);
        final IntTimeSeriesEntry lowerScaleArgument = new IntTimeSeriesEntry(21, false);
        higherScaleReceiver.merge(lowerScaleArgument);

        assertArrayEquals(new double[] {20, 128}, higherScaleReceiver.getValues());
        assertArrayEquals(new double[] {20}, lowerScaleArgument.getValues());
        assertEquals(2, higherScaleReceiver.getCount());
        assertEquals(149, higherScaleReceiver.getTotalValue());
    }

    @Test
    void equalityComparesTheCompleteMutableState()
    {
        final IntTimeSeriesEntry first = new IntTimeSeriesEntry(7, true);
        first.updateValue(130, false);
        first.updateConcurrency();

        final IntTimeSeriesEntry equal = new IntTimeSeriesEntry(7, true);
        equal.updateValue(130, false);
        equal.updateConcurrency();

        assertTrue(first.equals(first));
        assertEquals(first, equal);
        assertFalse(first.equals(null));
        assertFalse(first.equals("not an entry"));
        assertNotEquals(first, new IntTimeSeriesEntry(7, true));

        equal.updateConcurrency();
        assertNotEquals(first, equal);
    }
}
