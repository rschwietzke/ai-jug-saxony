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
package org.jugsaxony.demo7;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SimpleMath}.
 *
 * The tests encode the following contract assumptions:
 *
 * - {@code max(int, int)} and {@code max(Integer, Integer)} return the
 *   mathematically larger of both arguments. Equal arguments yield that value.
 * - The {@code Integer} overload compares by value, not by object identity,
 *   and rejects {@code null} arguments with a {@link NullPointerException}
 *   (fail-fast, consistent with JDK helpers such as {@code Math.max}).
 * - {@code sum(int[])} returns the mathematical sum of all elements. The
 *   {@code long} return type signals that results outside the {@code int}
 *   value range are part of the contract, hence the accumulation must not
 *   overflow {@code int}.
 * - {@code sum(int[])} rejects a {@code null} array with a
 *   {@link NullPointerException} and never modifies its input.
 */
class SimpleMathTest
{
    /**
     * Boundary, sign-change, and Integer-cache-edge values used to build
     * pairwise combination matrices.
     */
    private static final int[] BOUNDARY_VALUES =
        {
            Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1000, -129, -128, -127, -2, -1,
            0, 1, 2, 127, 128, 129, 1000, Integer.MAX_VALUE - 1, Integer.MAX_VALUE
        };

    /**
     * Computes the mathematically exact sum using {@code long} arithmetic.
     * This is the contract {@code sum(int[])} has to fulfill.
     */
    private static long expectedSum(final int[] values)
    {
        long sum = 0;
        for (final int value : values)
        {
            sum += value;
        }
        return sum;
    }

    // ----------------------------------------------------------------
    // max(int, int)
    // ----------------------------------------------------------------

    @Test
    void maxIntReturnsFirstArgumentWhenItIsLarger()
    {
        assertEquals(5, SimpleMath.max(5, 3));
        assertEquals(1, SimpleMath.max(1, 0));
        assertEquals(1000, SimpleMath.max(1000, 999));
        assertEquals(-3, SimpleMath.max(-3, -5));
        assertEquals(0, SimpleMath.max(0, -1));
        assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE, 0));
    }

    @Test
    void maxIntReturnsSecondArgumentWhenItIsLarger()
    {
        assertEquals(5, SimpleMath.max(3, 5));
        assertEquals(1, SimpleMath.max(0, 1));
        assertEquals(1000, SimpleMath.max(999, 1000));
        assertEquals(-3, SimpleMath.max(-5, -3));
        assertEquals(0, SimpleMath.max(-1, 0));
        assertEquals(Integer.MAX_VALUE, SimpleMath.max(0, Integer.MAX_VALUE));
    }

    @Test
    void maxIntOfEqualArgumentsIsThatValue()
    {
        assertEquals(0, SimpleMath.max(0, 0));
        assertEquals(7, SimpleMath.max(7, 7));
        assertEquals(-7, SimpleMath.max(-7, -7));
        assertEquals(Integer.MIN_VALUE, SimpleMath.max(Integer.MIN_VALUE, Integer.MIN_VALUE));
        assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void maxIntHandlesAdjacentValues()
    {
        assertEquals(1, SimpleMath.max(0, 1));
        assertEquals(1, SimpleMath.max(1, 0));
        assertEquals(-1, SimpleMath.max(-1, -2));
        assertEquals(-1, SimpleMath.max(-2, -1));
        assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE, Integer.MAX_VALUE - 1));
        assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
        assertEquals(Integer.MIN_VALUE + 1, SimpleMath.max(Integer.MIN_VALUE + 1, Integer.MIN_VALUE));
        assertEquals(Integer.MIN_VALUE + 1, SimpleMath.max(Integer.MIN_VALUE, Integer.MIN_VALUE + 1));
    }

    @Test
    void maxIntHandlesExtremeValues()
    {
        assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertEquals(0, SimpleMath.max(Integer.MIN_VALUE, 0));
        assertEquals(0, SimpleMath.max(0, Integer.MIN_VALUE));
        assertEquals(-1, SimpleMath.max(Integer.MIN_VALUE, -1));
        assertEquals(-1, SimpleMath.max(-1, Integer.MIN_VALUE));
    }

    @Test
    void maxIntIsSymmetric()
    {
        for (final int a : BOUNDARY_VALUES)
        {
            for (final int b : BOUNDARY_VALUES)
            {
                assertEquals(SimpleMath.max(a, b), SimpleMath.max(b, a),
                             "max must be symmetric for a=" + a + ", b=" + b);
            }
        }
    }

    @Test
    void maxIntIsNeverSmallerThanEitherArgumentAndReturnsOneOfThem()
    {
        for (final int a : BOUNDARY_VALUES)
        {
            for (final int b : BOUNDARY_VALUES)
            {
                final int max = SimpleMath.max(a, b);

                assertTrue(max >= a, "max(" + a + ", " + b + ") must be >= a");
                assertTrue(max >= b, "max(" + a + ", " + b + ") must be >= b");
                assertTrue(max == a || max == b,
                           "max(" + a + ", " + b + ") must return one of its arguments");
            }
        }
    }

    @Test
    void maxIntMatchesMathMaxOnBoundaryMatrix()
    {
        for (final int a : BOUNDARY_VALUES)
        {
            for (final int b : BOUNDARY_VALUES)
            {
                assertEquals(Math.max(a, b), SimpleMath.max(a, b), "max(" + a + ", " + b + ")");
            }
        }
    }

    @Test
    void maxIntMatchesMathMaxOnRandomValues()
    {
        final Random random = new Random(42);
        for (int i = 0; i < 10_000; i++)
        {
            final int a = random.nextInt();
            final int b = random.nextInt();

            assertEquals(Math.max(a, b), SimpleMath.max(a, b), "max(" + a + ", " + b + ")");
        }
    }

    // ----------------------------------------------------------------
    // max(Integer, Integer)
    // ----------------------------------------------------------------

    @Test
    void maxIntegerReturnsLargerValue()
    {
        assertEquals(5, SimpleMath.max(Integer.valueOf(5), Integer.valueOf(3)));
        assertEquals(5, SimpleMath.max(Integer.valueOf(3), Integer.valueOf(5)));
        assertEquals(0, SimpleMath.max(Integer.valueOf(0), Integer.valueOf(-1)));
        assertEquals(-3, SimpleMath.max(Integer.valueOf(-3), Integer.valueOf(-5)));
        assertEquals(-3, SimpleMath.max(Integer.valueOf(-5), Integer.valueOf(-3)));
    }

    @Test
    void maxIntegerOfEqualValuesIsThatValue()
    {
        assertEquals(0, SimpleMath.max(Integer.valueOf(0), Integer.valueOf(0)));
        assertEquals(7, SimpleMath.max(Integer.valueOf(7), Integer.valueOf(7)));
        assertEquals(-7, SimpleMath.max(Integer.valueOf(-7), Integer.valueOf(-7)));
        assertEquals(Integer.MIN_VALUE,
                     SimpleMath.max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MIN_VALUE)));
        assertEquals(Integer.MAX_VALUE,
                     SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE)));
    }

    @Test
    void maxIntegerComparesByValueNotByObjectIdentity()
    {
        // values outside the Integer cache (-128..127) are distinct objects
        final Integer small = Integer.valueOf(1000);
        final Integer large = Integer.valueOf(2000);

        assertEquals(2000, SimpleMath.max(small, large));
        assertEquals(2000, SimpleMath.max(large, small));

        // two independently boxed equal values above the cache range
        assertEquals(1000, SimpleMath.max(Integer.valueOf(1000), Integer.valueOf(1000)));

        // cache boundaries: 127 is cached, 128 is not; -128 is cached, -129 is not
        assertEquals(128, SimpleMath.max(Integer.valueOf(127), Integer.valueOf(128)));
        assertEquals(128, SimpleMath.max(Integer.valueOf(128), Integer.valueOf(127)));
        assertEquals(-128, SimpleMath.max(Integer.valueOf(-128), Integer.valueOf(-129)));
        assertEquals(-128, SimpleMath.max(Integer.valueOf(-129), Integer.valueOf(-128)));
    }

    @Test
    void maxIntegerHandlesExtremeValues()
    {
        final Integer min = Integer.valueOf(Integer.MIN_VALUE);
        final Integer max = Integer.valueOf(Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, SimpleMath.max(max, min));
        assertEquals(Integer.MAX_VALUE, SimpleMath.max(min, max));
        assertEquals(0, SimpleMath.max(min, Integer.valueOf(0)));
        assertEquals(0, SimpleMath.max(Integer.valueOf(0), min));
        assertEquals(-1, SimpleMath.max(min, Integer.valueOf(-1)));
        assertEquals(-1, SimpleMath.max(Integer.valueOf(-1), min));
    }

    @Test
    void maxIntegerMatchesMathMaxOnBoundaryMatrix()
    {
        for (final int a : BOUNDARY_VALUES)
        {
            for (final int b : BOUNDARY_VALUES)
            {
                assertEquals(Math.max(a, b),
                             SimpleMath.max(Integer.valueOf(a), Integer.valueOf(b)),
                             "max(" + a + ", " + b + ")");
            }
        }
    }

    @Test
    void maxIntegerMatchesMathMaxOnRandomValues()
    {
        final Random random = new Random(43);
        for (int i = 0; i < 10_000; i++)
        {
            final int a = random.nextInt();
            final int b = random.nextInt();

            assertEquals(Math.max(a, b),
                         SimpleMath.max(Integer.valueOf(a), Integer.valueOf(b)),
                         "max(" + a + ", " + b + ")");
        }
    }

    @Test
    void maxIntegerAgreesWithPrimitiveOverload()
    {
        for (final int a : BOUNDARY_VALUES)
        {
            for (final int b : BOUNDARY_VALUES)
            {
                assertEquals(SimpleMath.max(a, b),
                             SimpleMath.max(Integer.valueOf(a), Integer.valueOf(b)),
                             "both overloads must agree for a=" + a + ", b=" + b);
            }
        }
    }

    @Test
    void maxIntegerRejectsNullFirstArgument()
    {
        assertThrows(NullPointerException.class, () -> SimpleMath.max(null, Integer.valueOf(1)));
    }

    @Test
    void maxIntegerRejectsNullSecondArgument()
    {
        assertThrows(NullPointerException.class, () -> SimpleMath.max(Integer.valueOf(1), null));
    }

    @Test
    void maxIntegerRejectsBothArgumentsNull()
    {
        assertThrows(NullPointerException.class, () -> SimpleMath.max(null, null));
    }

    // ----------------------------------------------------------------
    // sum(int[])
    // ----------------------------------------------------------------

    @Test
    void sumRejectsNullArray()
    {
        assertThrows(NullPointerException.class, () -> SimpleMath.sum(null));
    }

    @Test
    void sumOfEmptyArrayIsZero()
    {
        assertEquals(0L, SimpleMath.sum(new int[0]));
    }

    @Test
    void sumOfSingleElementIsThatElement()
    {
        assertEquals(0L, SimpleMath.sum(new int[] { 0 }));
        assertEquals(1L, SimpleMath.sum(new int[] { 1 }));
        assertEquals(-1L, SimpleMath.sum(new int[] { -1 }));
        assertEquals(42L, SimpleMath.sum(new int[] { 42 }));
        assertEquals(-42L, SimpleMath.sum(new int[] { -42 }));
        assertEquals(Integer.MAX_VALUE, SimpleMath.sum(new int[] { Integer.MAX_VALUE }));
        assertEquals(Integer.MIN_VALUE, SimpleMath.sum(new int[] { Integer.MIN_VALUE }));
    }

    @Test
    void sumOfPositiveValues()
    {
        assertEquals(10L, SimpleMath.sum(new int[] { 1, 2, 3, 4 }));
        assertEquals(15L, SimpleMath.sum(new int[] { 5, 5, 5 }));
        assertEquals(300L, SimpleMath.sum(new int[] { 100, 200 }));
    }

    @Test
    void sumOfNegativeValues()
    {
        assertEquals(-10L, SimpleMath.sum(new int[] { -1, -2, -3, -4 }));
        assertEquals(-15L, SimpleMath.sum(new int[] { -5, -5, -5 }));
        assertEquals(-300L, SimpleMath.sum(new int[] { -100, -200 }));
    }

    @Test
    void sumOfMixedSignValues()
    {
        assertEquals(3L, SimpleMath.sum(new int[] { 5, -2 }));
        assertEquals(-3L, SimpleMath.sum(new int[] { -5, 2 }));
        assertEquals(-1L, SimpleMath.sum(new int[] { 3, -1, 4, -1, -5, -9, 2, 6 }));
    }

    @Test
    void sumOfValuesCancelingOutIsZero()
    {
        assertEquals(0L, SimpleMath.sum(new int[] { 1, -1 }));
        assertEquals(0L, SimpleMath.sum(new int[] { 10, -5, 3, -8 }));
        assertEquals(0L, SimpleMath.sum(new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE, 1 }));
    }

    @Test
    void sumOfAllZerosIsZero()
    {
        assertEquals(0L, SimpleMath.sum(new int[] { 0, 0, 0 }));
        assertEquals(0L, SimpleMath.sum(new int[100]));
    }

    @Test
    void sumOfIntExtremesStaysInRange()
    {
        // MIN + MAX is mathematically -1, well within the int range
        assertEquals(-1L, SimpleMath.sum(new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE }));

        // intermediate int overflow that returns into the int range must
        // still yield the exact mathematical result (2*MAX + 2*MIN = -2)
        assertEquals(-2L, SimpleMath.sum(new int[]
            {
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
            }));
    }

    @Test
    void sumExceedingIntRangeUsesFullLongRange()
    {
        // the long return type implies sums beyond the int range are supported
        assertEquals(2147483648L, SimpleMath.sum(new int[] { Integer.MAX_VALUE, 1 }));
        assertEquals(4294967294L, SimpleMath.sum(new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE }));
        assertEquals(4294967294L, SimpleMath.sum(new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE - 2, 2 }));
    }

    @Test
    void sumBelowIntRangeUsesFullLongRange()
    {
        assertEquals(-2147483649L, SimpleMath.sum(new int[] { Integer.MIN_VALUE, -1 }));
        assertEquals(-4294967296L, SimpleMath.sum(new int[] { Integer.MIN_VALUE, Integer.MIN_VALUE }));
    }

    @Test
    void sumOfManyElementsExceedingIntRange()
    {
        final int[] values = new int[1000];

        Arrays.fill(values, 3_000_000);
        assertEquals(3_000_000_000L, SimpleMath.sum(values));

        Arrays.fill(values, -3_000_000);
        assertEquals(-3_000_000_000L, SimpleMath.sum(values));
    }

    @Test
    void sumOfLargeArrayWithinIntRange()
    {
        final int[] values = new int[1_000_000];

        Arrays.fill(values, 1);
        assertEquals(1_000_000L, SimpleMath.sum(values));

        Arrays.fill(values, -2);
        assertEquals(-2_000_000L, SimpleMath.sum(values));
    }

    @Test
    void sumDoesNotModifyInputArray()
    {
        final int[] values = { 3, 1, -4, 1, 5, -9, 2, 6 };
        final int[] copy = values.clone();

        SimpleMath.sum(values);

        assertArrayEquals(copy, values);
    }

    @Test
    void sumIsIndependentOfElementOrder()
    {
        final int[] values = { 5, -3, 17, 0, -100, 42, 7, -8 };
        final int[] shuffled = values.clone();

        final Random random = new Random(7);
        for (int i = shuffled.length - 1; i > 0; i--)
        {
            final int j = random.nextInt(i + 1);
            final int t = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = t;
        }

        assertEquals(expectedSum(values), SimpleMath.sum(values));
        assertEquals(SimpleMath.sum(values), SimpleMath.sum(shuffled));
    }

    @Test
    void sumIsAdditiveOverConcatenation()
    {
        final int[] a = { 1, 2, 3, -4 };
        final int[] b = { 10, -20, 30 };

        final int[] concat = new int[a.length + b.length];
        System.arraycopy(a, 0, concat, 0, a.length);
        System.arraycopy(b, 0, concat, a.length, b.length);

        assertEquals(expectedSum(a) + expectedSum(b), SimpleMath.sum(concat));
        assertEquals(SimpleMath.sum(a) + SimpleMath.sum(b), SimpleMath.sum(concat));
    }

    @Test
    void sumMatchesLongAccumulationOnRandomArraysWithinIntRange()
    {
        final Random random = new Random(11);
        for (int run = 0; run < 100; run++)
        {
            final int[] values = new int[random.nextInt(200)];
            for (int i = 0; i < values.length; i++)
            {
                values[i] = random.nextInt(2001) - 1000;
            }

            assertEquals(expectedSum(values), SimpleMath.sum(values),
                         "run " + run + ": " + Arrays.toString(values));
        }
    }

    @Test
    void sumMatchesLongAccumulationOnRandomFullRangeArrays()
    {
        final Random random = new Random(12);
        for (int run = 0; run < 100; run++)
        {
            final int[] values = new int[random.nextInt(50)];
            for (int i = 0; i < values.length; i++)
            {
                values[i] = random.nextInt();
            }

            assertEquals(expectedSum(values), SimpleMath.sum(values),
                         "run " + run + ": " + Arrays.toString(values));
        }
    }
}
