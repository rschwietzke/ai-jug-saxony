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

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IntTimeSeriesEntry}. The entry tracks count/sum/min/max/
 * errors/concurrency exactly, plus a lossy 128-bucket approximation of the
 * distinct values that rescales (joins adjacent buckets) once values exceed
 * the 0..127 range.
 */
class IntTimeSeriesEntryTest
{
    // ----------------------------------------------------------------
    // Empty entry
    // ----------------------------------------------------------------

    @Test
    void emptyEntryReportsZeros()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry();

        assertEquals(0, e.getCount());
        assertEquals(0, e.getConcurrentCount());
        assertEquals(0, e.getErrorCount());
        assertEquals(0L, e.getTotalValue());
        assertEquals(0, e.getAverageValue());
        assertEquals(0, e.getMaximumValue(), "unset maximum reports 0");
        assertEquals(0, e.getMinimumValue(), "unset minimum reports 0");
        assertEquals(0, e.getValues().length);
    }

    // ----------------------------------------------------------------
    // Single value
    // ----------------------------------------------------------------

    @Test
    void singleValueViaDefaultConstructor()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(5, false);

        assertEquals(1, e.getCount());
        assertEquals(1, e.getConcurrentCount());
        assertEquals(0, e.getErrorCount());
        assertEquals(5L, e.getTotalValue());
        assertEquals(5, e.getAverageValue());
        assertEquals(5, e.getMaximumValue());
        assertEquals(5, e.getMinimumValue());
        assertArrayEquals(new double[] {5.0}, e.getValues());
    }

    @Test
    void singleValueViaConstructor()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry(7, true);

        assertEquals(1, e.getCount());
        assertEquals(1, e.getErrorCount());
        assertEquals(7L, e.getTotalValue());
        assertEquals(7, e.getMaximumValue());
        assertEquals(7, e.getMinimumValue());
        assertArrayEquals(new double[] {7.0}, e.getValues());
    }

    @Test
    void zeroValueIsRecorded()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry(0, false);

        assertEquals(1, e.getCount());
        assertEquals(0L, e.getTotalValue());
        assertEquals(0, e.getMaximumValue());
        assertEquals(0, e.getMinimumValue());
        assertArrayEquals(new double[] {0.0}, e.getValues());
    }

    // ----------------------------------------------------------------
    // Negative values are clamped to zero
    // ----------------------------------------------------------------

    @Test
    void negativeValueIsClampedToZero()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry(-3, false);

        assertEquals(1, e.getCount());
        assertEquals(0L, e.getTotalValue(), "negative value contributes 0 to the sum");
        assertEquals(0, e.getMaximumValue());
        assertEquals(0, e.getMinimumValue());
        assertArrayEquals(new double[] {0.0}, e.getValues());
    }

    // ----------------------------------------------------------------
    // Aggregation over multiple values
    // ----------------------------------------------------------------

    @Test
    void multipleValuesAggregateExactly()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(3, false);
        e.updateValue(7, false);
        e.updateValue(5, true);

        assertEquals(3, e.getCount());
        assertEquals(3, e.getConcurrentCount());
        assertEquals(1, e.getErrorCount());
        assertEquals(15L, e.getTotalValue());
        assertEquals(5, e.getAverageValue());
        assertEquals(7, e.getMaximumValue());
        assertEquals(3, e.getMinimumValue());
        // distinct values are reported in ascending order
        assertArrayEquals(new double[] {3.0, 5.0, 7.0}, e.getValues());
    }

    @Test
    void duplicateValuesCollapseInDistinctSet()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(4, false);
        e.updateValue(4, false);
        e.updateValue(4, false);

        assertEquals(3, e.getCount());
        assertEquals(12L, e.getTotalValue());
        assertEquals(4, e.getMaximumValue());
        assertEquals(4, e.getMinimumValue());
        assertArrayEquals(new double[] {4.0}, e.getValues(), "duplicates occupy one bucket");
    }

    @Test
    void averageUsesIntegerDivision()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(5, false);
        e.updateValue(6, false);
        e.updateValue(7, false);

        assertEquals(18L, e.getTotalValue());
        assertEquals(6, e.getAverageValue()); // 18 / 3
    }

    @Test
    void updateConcurrencyIncrementsOnlyConcurrencyCount()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(1, false);
        assertEquals(1, e.getCount());
        assertEquals(1, e.getConcurrentCount());

        e.updateConcurrency();
        e.updateConcurrency();

        assertEquals(1, e.getCount(), "count unchanged");
        assertEquals(3, e.getConcurrentCount());
        assertEquals(1L, e.getTotalValue());
    }

    // ----------------------------------------------------------------
    // Distinct value boundaries (no scaling yet)
    // ----------------------------------------------------------------

    @Test
    void valueAtLowBoundaryUsesLowWord()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry(63, false);
        assertArrayEquals(new double[] {63.0}, e.getValues());
    }

    @Test
    void valueAtHighBoundaryUsesHighWord()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry(64, false);
        assertArrayEquals(new double[] {64.0}, e.getValues());

        final IntTimeSeriesEntry e2 = new IntTimeSeriesEntry(127, false);
        assertArrayEquals(new double[] {127.0}, e2.getValues());
    }

    @Test
    void allValuesUpTo127AreTrackedWithoutScaling()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        for (int v = 0; v < 128; v++)
        {
            e.updateValue(v, false);
        }

        assertEquals(128, e.getCount());
        assertEquals(0, e.getMinimumValue());
        assertEquals(127, e.getMaximumValue());

        final double[] values = e.getValues();
        assertEquals(128, values.length);
        for (int v = 0; v < 128; v++)
        {
            assertEquals((double) v, values[v]);
        }
    }

    // ----------------------------------------------------------------
    // Scaling (value >= 128 joins buckets)
    // ----------------------------------------------------------------

    @Test
    void singleLargeValueIsReconstructedAfterScaling()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry(200, false);

        assertEquals(1, e.getCount());
        assertEquals(200L, e.getTotalValue());
        assertEquals(200, e.getMaximumValue());
        assertEquals(200, e.getMinimumValue());
        // scale becomes 1, value 200 maps to bucket 100 -> 100 * 2 = 200
        assertArrayEquals(new double[] {200.0}, e.getValues());
    }

    @Test
    void valueOf128TriggersFirstScalingStep()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry(128, false);
        // scale 1, bucket 64 -> 64 * 2 = 128
        assertArrayEquals(new double[] {128.0}, e.getValues());
    }

    @Test
    void veryLargeValueScalesMultipleTimes()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry(100_000, false);

        assertEquals(1, e.getCount());
        assertEquals(100_000L, e.getTotalValue());
        assertEquals(100_000, e.getMaximumValue());

        final double[] values = e.getValues();
        assertEquals(1, values.length, "a single value occupies a single bucket");
        // the reconstructed value is the scaled bucket and may lose precision,
        // but must be within one bucket width of the original
        assertTrue(values[0] <= 100_000.0, "reconstruction must not overshoot");
        assertTrue(values[0] >= 100_000.0 / 2.0, "reconstruction stays in range");
    }

    @Test
    void scalingJoinsAdjacentBucketsLossily()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        // fill the full unscaled range first
        for (int v = 0; v < 128; v++)
        {
            e.updateValue(v, false);
        }
        // now force a scaling step
        e.updateValue(200, false);

        assertEquals(129, e.getCount());
        assertEquals(8128L + 200L, e.getTotalValue());
        assertEquals(0, e.getMinimumValue());
        assertEquals(200, e.getMaximumValue());

        final double[] values = e.getValues();
        // after joining, at most 128 buckets remain and 200 must be present
        assertTrue(values.length <= 128);
        assertTrue(java.util.Arrays.stream(values).anyMatch(v -> v == 200.0), "200 must survive scaling");
        // all reconstructed values are even multiples at scale 1 except none odd
        for (final double v : values)
        {
            assertEquals(0.0, v % 2.0, "scale-1 buckets reconstruct to even values");
        }
    }

    // ----------------------------------------------------------------
    // merge (same scale) - exact
    // ----------------------------------------------------------------

    @Test
    void mergeSameScaleCombinesAllFields()
    {
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
        a.updateValue(3, false);
        a.updateValue(7, true);

        final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
        b.updateValue(5, false);
        b.updateValue(9, true);

        final IntTimeSeriesEntry result = a.merge(b);
        assertSame(a, result, "merge returns this");

        assertEquals(4, result.getCount());
        assertEquals(2, result.getErrorCount());
        assertEquals(24L, result.getTotalValue());
        assertEquals(9, result.getMaximumValue());
        assertEquals(3, result.getMinimumValue());
        // each entry saw 2 updates -> concurrency is max(2, 2), not the sum
        assertEquals(2, result.getConcurrentCount(), "max of both concurrency counts");
        assertArrayEquals(new double[] {3.0, 5.0, 7.0, 9.0}, result.getValues());
    }

    @Test
    void mergeTakesMaximumOfConcurrencyNotSum()
    {
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
        a.updateValue(1, false);
        a.updateConcurrency();
        a.updateConcurrency(); // concurrentCount = 3

        final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
        b.updateValue(2, false); // concurrentCount = 1

        a.merge(b);
        assertEquals(3, a.getConcurrentCount(), "concurrency is maxed, not summed");
        assertEquals(2, a.getCount());
    }

    // ----------------------------------------------------------------
    // merge (different scales)
    // ----------------------------------------------------------------

    @Test
    void mergeScalesUpThisWhenOtherIsLarger()
    {
        final IntTimeSeriesEntry small = new IntTimeSeriesEntry(5, false); // scale 0
        final IntTimeSeriesEntry large = new IntTimeSeriesEntry(200, false); // scale 1

        small.merge(large);

        assertEquals(2, small.getCount());
        assertEquals(205L, small.getTotalValue());
        assertEquals(200, small.getMaximumValue());
        assertEquals(5, small.getMinimumValue());
        // both original values must be represented in the joined approximation
        assertTrue(small.getValues().length >= 1);
    }

    @Test
    void mergeScalesUpOtherWhenThisIsLarger()
    {
        final IntTimeSeriesEntry large = new IntTimeSeriesEntry(200, false); // scale 1
        final IntTimeSeriesEntry small = new IntTimeSeriesEntry(5, false); // scale 0

        large.merge(small);

        assertEquals(2, large.getCount());
        assertEquals(205L, large.getTotalValue());
        assertEquals(200, large.getMaximumValue());
        assertEquals(5, large.getMinimumValue());
    }

    @Test
    void mergeIsCommutativeForScalarFields()
    {
        final Random random = new Random(3);
        for (int round = 0; round < 200; round++)
        {
            final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
            final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
            for (int i = 0; i < 20; i++)
            {
                a.updateValue(random.nextInt(500), random.nextBoolean());
                b.updateValue(random.nextInt(500), random.nextBoolean());
            }

            final long totalA = a.getTotalValue();
            final long totalB = b.getTotalValue();
            final int countA = a.getCount();
            final int countB = b.getCount();
            final int maxA = a.getMaximumValue();
            final int maxB = b.getMaximumValue();
            final int minA = a.getMinimumValue();
            final int minB = b.getMinimumValue();

            a.merge(b);

            assertEquals(countA + countB, a.getCount());
            assertEquals(totalA + totalB, a.getTotalValue());
            assertEquals(Math.max(maxA, maxB), a.getMaximumValue());
            assertEquals(Math.min(minA, minB), a.getMinimumValue());
        }
    }

    // ----------------------------------------------------------------
    // equals / hashCode-less value equality
    // ----------------------------------------------------------------

    @Test
    void equalsComparesFullState()
    {
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
        final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
        a.updateValue(10, false);
        b.updateValue(10, false);
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, null);
        assertNotEquals(a, "not an entry");

        b.updateValue(11, false);
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsErrorCountDifference()
    {
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry(10, false);
        final IntTimeSeriesEntry b = new IntTimeSeriesEntry(10, true);
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsMaximumDifference()
    {
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry(5, false);
        final IntTimeSeriesEntry b = new IntTimeSeriesEntry(7, false);
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsMinimumDifference()
    {
        // same count and maximum, different minimum
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
        a.updateValue(5, false);
        a.updateValue(10, false);

        final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
        b.updateValue(7, false);
        b.updateValue(10, false);

        assertEquals(a.getCount(), b.getCount());
        assertEquals(a.getMaximumValue(), b.getMaximumValue());
        assertNotEquals(a.getMinimumValue(), b.getMinimumValue());
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsTotalValueDifference()
    {
        // same count, min and max but different sum
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
        a.updateValue(0, false);
        a.updateValue(5, false);
        a.updateValue(10, false);

        final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
        b.updateValue(0, false);
        b.updateValue(10, false);
        b.updateValue(10, false);

        assertEquals(a.getCount(), b.getCount());
        assertEquals(a.getMinimumValue(), b.getMinimumValue());
        assertEquals(a.getMaximumValue(), b.getMaximumValue());
        assertNotEquals(a.getTotalValue(), b.getTotalValue());
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDistinctLowDifference()
    {
        // identical scalars, different distinct-value bitset in the low word
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
        a.updateValue(1, false);
        a.updateValue(2, false);
        a.updateValue(3, false);
        a.updateValue(4, false);

        final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
        b.updateValue(1, false);
        b.updateValue(1, false);
        b.updateValue(4, false);
        b.updateValue(4, false);

        assertEquals(a.getCount(), b.getCount());
        assertEquals(a.getTotalValue(), b.getTotalValue());
        assertEquals(a.getMinimumValue(), b.getMinimumValue());
        assertEquals(a.getMaximumValue(), b.getMaximumValue());
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDistinctHighDifference()
    {
        // identical scalars and empty low word, different high-word bitset
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
        a.updateValue(64, false);
        a.updateValue(65, false);
        a.updateValue(66, false);
        a.updateValue(67, false);

        final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
        b.updateValue(64, false);
        b.updateValue(64, false);
        b.updateValue(67, false);
        b.updateValue(67, false);

        assertEquals(a.getCount(), b.getCount());
        assertEquals(a.getTotalValue(), b.getTotalValue());
        assertEquals(a.getMinimumValue(), b.getMinimumValue());
        assertEquals(a.getMaximumValue(), b.getMaximumValue());
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsConcurrencyDifference()
    {
        final IntTimeSeriesEntry a = new IntTimeSeriesEntry(5, false);

        final IntTimeSeriesEntry b = new IntTimeSeriesEntry(5, false);
        b.updateConcurrency();

        assertEquals(a.getCount(), b.getCount());
        assertNotEquals(a.getConcurrentCount(), b.getConcurrentCount());
        assertNotEquals(a, b);
    }

    // ----------------------------------------------------------------
    // toString
    // ----------------------------------------------------------------

    @Test
    void toStringContainsAggregateState()
    {
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry();
        e.updateValue(10, false);
        e.updateValue(20, true);

        final String s = e.toString();
        assertTrue(s.contains("2"), "count");
        assertTrue(s.contains("30"), "total value");
        assertTrue(s.contains("20"), "maximum");
    }

    // ----------------------------------------------------------------
    // Randomized invariant: sum/count/min/max always exact
    // ----------------------------------------------------------------

    @Test
    void randomValuesKeepExactAggregates()
    {
        final Random random = new Random(11);
        final IntTimeSeriesEntry e = new IntTimeSeriesEntry();

        long expectedSum = 0;
        int expectedCount = 0;
        int expectedErrors = 0;
        int expectedMax = 0;
        int expectedMin = Integer.MAX_VALUE;

        for (int i = 0; i < 5_000; i++)
        {
            final int value = random.nextInt(0, 100_000);
            final boolean failed = random.nextInt(10) == 0;
            e.updateValue(value, failed);

            expectedSum += value;
            expectedCount++;
            expectedErrors += failed ? 1 : 0;
            expectedMax = Math.max(expectedMax, value);
            expectedMin = Math.min(expectedMin, value);
        }

        assertEquals(expectedCount, e.getCount());
        assertEquals(expectedErrors, e.getErrorCount());
        assertEquals(expectedSum, e.getTotalValue());
        assertEquals(expectedMax, e.getMaximumValue());
        assertEquals(expectedMin, e.getMinimumValue());
        assertEquals(expectedSum / expectedCount, e.getAverageValue());
        assertFalse(e.getValues().length == 0);
        assertTrue(e.getValues().length <= 128, "distinct value approximation is capped at 128");
    }
}
