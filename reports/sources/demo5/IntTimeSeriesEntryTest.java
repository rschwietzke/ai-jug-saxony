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

import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link IntTimeSeriesEntry} min/max/sum/count slot that also
 * stores an approximate set of distinct values.
 */
public class IntTimeSeriesEntryTest
{
    @Test
    public void initialEntryIsEmpty()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        assertEquals(0, entry.getCount());
        assertEquals(0, entry.getConcurrentCount());
        assertEquals(0, entry.getErrorCount());
        assertEquals(0, entry.getTotalValue());
        assertEquals(0, entry.getAverageValue());
        assertEquals(0, entry.getMinimumValue());
        assertEquals(0, entry.getMaximumValue());
        assertEquals(0, entry.getValues().length);
    }

    @Test
    public void constructorWithFirstValue()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry(42, true);
        assertEquals(1, entry.getCount());
        assertEquals(1, entry.getErrorCount());
        assertEquals(1, entry.getConcurrentCount());
        assertEquals(42, entry.getTotalValue());
        assertEquals(42, entry.getAverageValue());
        assertEquals(42, entry.getMinimumValue());
        assertEquals(42, entry.getMaximumValue());
    }

    @Test
    public void updateValueAccumulatesStatistics()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(10, false);
        entry.updateValue(20, false);
        entry.updateValue(30, true);

        assertEquals(3, entry.getCount());
        assertEquals(1, entry.getErrorCount());
        assertEquals(3, entry.getConcurrentCount());
        assertEquals(60, entry.getTotalValue());
        assertEquals(20, entry.getAverageValue());
        assertEquals(10, entry.getMinimumValue());
        assertEquals(30, entry.getMaximumValue());
    }

    @Test
    public void averageUsesIntegerDivision()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(5, false);
        entry.updateValue(6, false);
        assertEquals(11, entry.getTotalValue());
        assertEquals(5, entry.getAverageValue());
    }

    @Test
    public void negativeValuesAreClampedToZero()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(-1, false);
        entry.updateValue(-5, true);

        assertEquals(2, entry.getCount());
        assertEquals(1, entry.getErrorCount());
        assertEquals(0, entry.getTotalValue());
        assertEquals(0, entry.getMinimumValue());
        assertEquals(0, entry.getMaximumValue());
        assertArrayEquals(new double[]{0.0}, entry.getValues());
    }

    @Test
    public void minAndMaxTrackExactValuesEvenAfterScaling()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(1, false);
        entry.updateValue(2_000_000_000, false);
        assertEquals(1, entry.getMinimumValue());
        assertEquals(2_000_000_000, entry.getMaximumValue());
        assertEquals(2_000_000_001L, entry.getTotalValue());
    }

    @Test
    public void updateConcurrencyIncreasesOnlyTheConcurrencyCount()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateConcurrency();
        entry.updateConcurrency();
        assertEquals(2, entry.getConcurrentCount());
        assertEquals(0, entry.getCount());
    }

    @Test
    public void distinctValuesExactForSmallValues()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(1, false);
        entry.updateValue(2, false);
        entry.updateValue(100, false);
        entry.updateValue(127, false);
        assertArrayEquals(new double[]{1.0, 2.0, 100.0, 127.0}, entry.getValues());
    }

    @Test
    public void distinctValuesCollapseDuplicatesAndSortAscending()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(7, false);
        entry.updateValue(3, false);
        entry.updateValue(7, false);
        entry.updateValue(3, false);
        assertArrayEquals(new double[]{3.0, 7.0}, entry.getValues());
    }

    @Test
    public void distinctValuesAreQuantizedToTheEntryScale()
    {
        // large values force the internal scale up; reported values become multiples of 2^scale
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(500, false);
        entry.updateValue(600, false);
        assertArrayEquals(new double[]{496.0, 600.0}, entry.getValues());

        final IntTimeSeriesEntry entry2 = new IntTimeSeriesEntry();
        entry2.updateValue(1000, false);
        entry2.updateValue(5, false);
        // scale 3, width 8: 1000 -> bucket 125, 5 -> bucket 0
        assertArrayEquals(new double[]{0.0, 1000.0}, entry2.getValues());
    }

    @Test
    public void distinctValuesMatchQuantizationModelForRandomData()
    {
        final Random random = new Random(77);
        for (int trial = 0; trial < 10; trial++)
        {
            final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            final int[] values = new int[300];
            int max = 0;
            for (int i = 0; i < values.length; i++)
            {
                final int value = random.nextInt(5_000_000);
                values[i] = value;
                max = Math.max(max, value);
                entry.updateValue(value, false);
            }

            // the entry ends up at the smallest scale whose bucket width fits the largest value
            int scale = 0;
            while ((max >> scale) >= 128)
            {
                scale++;
            }

            final Set<Double> expected = new TreeSet<>();
            for (final int value : values)
            {
                expected.add((double) (((long) value >> scale) << scale));
            }
            final Set<Double> actual = new TreeSet<>();
            for (final double value : entry.getValues())
            {
                actual.add(value);
            }
            assertEquals(expected, actual, "trial " + trial);
        }
    }

    @Test
    public void mergeSameScaleCombinesStatisticsAndDistinctValues()
    {
        final IntTimeSeriesEntry first = new IntTimeSeriesEntry();
        first.updateValue(10, false);
        first.updateConcurrency();

        final IntTimeSeriesEntry second = new IntTimeSeriesEntry();
        second.updateValue(20, true);
        second.updateValue(30, false);

        final IntTimeSeriesEntry result = first.merge(second);
        assertSame(first, result);
        assertEquals(3, result.getCount());
        assertEquals(1, result.getErrorCount());
        // concurrency is the maximum of both entries, not the sum
        assertEquals(2, result.getConcurrentCount());
        assertEquals(60, result.getTotalValue());
        assertEquals(10, result.getMinimumValue());
        assertEquals(30, result.getMaximumValue());
        assertArrayEquals(new double[]{10.0, 20.0, 30.0}, result.getValues());
    }

    @Test
    public void mergeScalesThisEntryUpAndKeepsAggregates()
    {
        final IntTimeSeriesEntry small = new IntTimeSeriesEntry();
        small.updateValue(5, false); // scale 0

        final IntTimeSeriesEntry large = new IntTimeSeriesEntry();
        large.updateValue(200, false); // scale 1

        small.merge(large);

        assertEquals(2, small.getCount());
        assertEquals(205, small.getTotalValue());
        assertEquals(5, small.getMinimumValue());
        assertEquals(200, small.getMaximumValue());
        // the small value is quantized to its new, coarser bucket
        assertArrayEquals(new double[]{4.0, 200.0}, small.getValues());
    }

    @Test
    public void mergeScalesTheItemUpAndMutatesIt()
    {
        final IntTimeSeriesEntry large = new IntTimeSeriesEntry();
        large.updateValue(200, false); // scale 1

        final IntTimeSeriesEntry small = new IntTimeSeriesEntry();
        small.updateValue(5, false); // scale 0
        assertArrayEquals(new double[]{5.0}, small.getValues());

        large.merge(small);

        assertEquals(2, large.getCount());
        assertEquals(205, large.getTotalValue());
        assertArrayEquals(new double[]{4.0, 200.0}, large.getValues());
        // the item was scaled up in place (documented side effect) and now stores 4.0
        assertArrayEquals(new double[]{4.0}, small.getValues());
    }

    @Test
    public void mergeAcrossScalesPreservesAggregates()
    {
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
        a.updateValue(3, false);
        a.updateValue(4, false);

        final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
        b.updateValue(1_000_000, true);
        b.updateValue(2_000_000, true);

        final long sumBefore = a.getTotalValue() + b.getTotalValue();
        final int countBefore = a.getCount() + b.getCount();
        final int errorsBefore = a.getErrorCount() + b.getErrorCount();
        final int minBefore = Math.min(a.getMinimumValue(), b.getMinimumValue());
        final int maxBefore = Math.max(a.getMaximumValue(), b.getMaximumValue());

        a.merge(b);

        assertEquals(countBefore, a.getCount());
        assertEquals(errorsBefore, a.getErrorCount());
        assertEquals(sumBefore, a.getTotalValue());
        assertEquals(minBefore, a.getMinimumValue());
        assertEquals(maxBefore, a.getMaximumValue());
    }

    @Test
    public void equalsComparesRelevantState()
    {
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
        a.updateValue(5, false);
        a.updateValue(7, true);

        final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
        b.updateValue(5, false);
        b.updateValue(7, true);

        assertEquals(a, b);
        assertEquals(b, a);

        final IntTimeSeriesEntry c = new IntTimeSeriesEntry();
        c.updateValue(5, false);
        c.updateValue(7, false);
        assertNotEquals(a, c);
        assertFalse(a.equals(null));
        assertNotEquals(a, new Object());
    }

    @Test
    @Disabled("Known defect: merging up an entry whose distinct values live in the high 64-bit word misplaces bits. "
        + "value 100 lives at scale 0 in the high word; after scaling it must end up in bucket 50 (=> 100.0) but the merge "
        + "compresses the high word without re-packing it into the low word, yielding bucket 82 (=> 164.0). "
        + "See doc/XLT-DATA.md.")
    public void mergeScalesHighWordDistinctValuesCorrectly()
    {
        final IntTimeSeriesEntry highWord = new IntTimeSeriesEntry();
        highWord.updateValue(100, false); // scale 0, distinct value in high 64-bit word

        final IntTimeSeriesEntry scaledUp = new IntTimeSeriesEntry();
        scaledUp.updateValue(200, false); // scale 1

        highWord.merge(scaledUp);

        assertArrayEquals(new double[]{100.0, 200.0}, highWord.getValues());
    }

    @Test
    public void toStringDoesNotFail()
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(1, false);
        assertTrue(entry.toString().length() > 0);
    }
}
