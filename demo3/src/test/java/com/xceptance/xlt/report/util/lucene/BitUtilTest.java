/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package com.xceptance.xlt.report.util.lucene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.function.LongBinaryOperator;

import org.junit.jupiter.api.Test;

class BitUtilTest
{
    @Test
    void popMatchesJdkForCanonicalSingleBitAndRandomValues()
    {
        assertEquals(0, BitUtil.pop(0));
        assertEquals(Long.SIZE, BitUtil.pop(-1L));
        assertEquals(32, BitUtil.pop(0x5555555555555555L));
        assertEquals(32, BitUtil.pop(0xAAAAAAAAAAAAAAAAL));

        for (int bitIndex = 0; bitIndex < Long.SIZE; bitIndex++)
        {
            assertEquals(1, BitUtil.pop(1L << bitIndex), "single bit " + bitIndex);
            assertEquals(Long.SIZE - 1, BitUtil.pop(~(1L << bitIndex)), "complement " + bitIndex);
        }

        final Random random = new Random(0x50F_C0A17L);
        for (int iteration = 0; iteration < 10_000; iteration++)
        {
            final long value = random.nextLong();
            assertEquals(Long.bitCount(value), BitUtil.pop(value), "iteration " + iteration);
        }
    }

    @Test
    void arrayCardinalityOperationsMatchIndependentReferencesForAllTailLengths()
    {
        final long[] a = new long[80];
        final long[] b = new long[80];
        final Random random = new Random(0xCA771A11L);
        for (int i = 0; i < a.length; i++)
        {
            a[i] = random.nextLong();
            b[i] = random.nextLong();
        }
        final long[] originalA = a.clone();
        final long[] originalB = b.clone();

        for (final int offset : new int[] {0, 1, 2, 7, 8})
        {
            for (int length = 0; length <= 65; length++)
            {
                assertEquals(popReference(a, offset, length), BitUtil.pop_array(a, offset, length),
                             message("array", offset, length));
                assertEquals(popReference(a, b, offset, length, (x, y) -> x & y),
                             BitUtil.pop_intersect(a, b, offset, length), message("intersection", offset, length));
                assertEquals(popReference(a, b, offset, length, (x, y) -> x | y),
                             BitUtil.pop_union(a, b, offset, length), message("union", offset, length));
                assertEquals(popReference(a, b, offset, length, (x, y) -> x & ~y),
                             BitUtil.pop_andnot(a, b, offset, length), message("and-not", offset, length));
                assertEquals(popReference(a, b, offset, length, (x, y) -> x ^ y),
                             BitUtil.pop_xor(a, b, offset, length), message("xor", offset, length));
            }
        }

        assertArrayEquals(originalA, a);
        assertArrayEquals(originalB, b);
    }

    @Test
    void arrayCardinalityOperationsHandleMaximumCarries()
    {
        final long[] ones = new long[65];
        final long[] zeroes = new long[65];
        java.util.Arrays.fill(ones, -1L);

        for (int length = 0; length <= ones.length; length++)
        {
            final long expected = (long) Long.SIZE * length;
            assertEquals(expected, BitUtil.pop_array(ones, 0, length), "array length " + length);
            assertEquals(expected, BitUtil.pop_intersect(ones, ones, 0, length), "intersection length " + length);
            assertEquals(expected, BitUtil.pop_union(ones, zeroes, 0, length), "union length " + length);
            assertEquals(expected, BitUtil.pop_andnot(ones, zeroes, 0, length), "and-not length " + length);
            assertEquals(expected, BitUtil.pop_xor(ones, zeroes, 0, length), "xor length " + length);
        }
    }

    @Test
    void binaryCardinalityOperationsHaveTheExpectedSetSemantics()
    {
        final long[] values = {0, -1L, 0x5555555555555555L, 0x0123456789ABCDEFL};
        final long population = BitUtil.pop_array(values, 0, values.length);

        assertEquals(128, population);
        assertEquals(population, BitUtil.pop_intersect(values, values, 0, values.length));
        assertEquals(population, BitUtil.pop_union(values, values, 0, values.length));
        assertEquals(0, BitUtil.pop_andnot(values, values, 0, values.length));
        assertEquals(0, BitUtil.pop_xor(values, values, 0, values.length));
        assertEquals(63, BitUtil.pop_andnot(new long[] {-1L}, new long[] {1L}, 0, 1));
        assertEquals(0, BitUtil.pop_andnot(new long[] {1L}, new long[] {-1L}, 0, 1));
    }

    @Test
    void zeroLengthSlicesAtValidBoundariesAreEmpty()
    {
        final long[] values = {1, 2, 3};
        for (final int offset : new int[] {0, 1, values.length})
        {
            assertEquals(0, BitUtil.pop_array(values, offset, 0));
            assertEquals(0, BitUtil.pop_intersect(values, values, offset, 0));
            assertEquals(0, BitUtil.pop_union(values, values, offset, 0));
            assertEquals(0, BitUtil.pop_andnot(values, values, offset, 0));
            assertEquals(0, BitUtil.pop_xor(values, values, offset, 0));
        }
    }

    @Test
    void trailingZeroImplementationsMatchJdkForTheirDefinedInputs()
    {
        assertEquals(Integer.SIZE, BitUtil.ntz(0));
        assertEquals(Long.SIZE, BitUtil.ntz(0L));
        assertEquals(Long.SIZE, BitUtil.ntz2(0L));

        for (int bitIndex = 0; bitIndex < Integer.SIZE; bitIndex++)
        {
            assertEquals(bitIndex, BitUtil.ntz(1 << bitIndex), "int bit " + bitIndex);
        }
        for (int bitIndex = 0; bitIndex < Long.SIZE; bitIndex++)
        {
            final long value = 1L << bitIndex;
            assertEquals(bitIndex, BitUtil.ntz(value), "ntz bit " + bitIndex);
            assertEquals(bitIndex, BitUtil.ntz2(value), "ntz2 bit " + bitIndex);
            assertEquals(bitIndex, BitUtil.ntz3(value), "ntz3 bit " + bitIndex);
        }

        final Random random = new Random(0x7A11_20L);
        for (int iteration = 0; iteration < 10_000; iteration++)
        {
            long value = random.nextLong();
            if (value == 0)
            {
                value = 1;
            }
            final int expected = Long.numberOfTrailingZeros(value);
            assertEquals(expected, BitUtil.ntz(value), "ntz iteration " + iteration);
            assertEquals(expected, BitUtil.ntz2(value), "ntz2 iteration " + iteration);
            assertEquals(expected, BitUtil.ntz3(value), "ntz3 iteration " + iteration);
        }
    }

    @Test
    void leadingZeroImplementationMatchesJdk()
    {
        assertEquals(Long.SIZE, BitUtil.nlz(0));
        for (int bitIndex = 0; bitIndex < Long.SIZE; bitIndex++)
        {
            final long value = 1L << bitIndex;
            assertEquals(Long.numberOfLeadingZeros(value), BitUtil.nlz(value), "bit " + bitIndex);
        }

        final Random random = new Random(0x1EAD_20L);
        for (int iteration = 0; iteration < 10_000; iteration++)
        {
            final long value = random.nextLong();
            assertEquals(Long.numberOfLeadingZeros(value), BitUtil.nlz(value), "iteration " + iteration);
        }
    }

    @Test
    void lookupTablesContainCanonicalByteResults()
    {
        assertEquals(256, BitUtil.ntzTable.length);
        assertEquals(256, BitUtil.nlzTable.length);

        for (int value = 0; value < 256; value++)
        {
            final int expectedTrailing = value == 0 ? Byte.SIZE : Integer.numberOfTrailingZeros(value);
            final int expectedLeading = Integer.numberOfLeadingZeros(value) - (Integer.SIZE - Byte.SIZE);
            assertEquals(expectedTrailing, BitUtil.ntzTable[value], "ntz byte " + value);
            assertEquals(expectedLeading, BitUtil.nlzTable[value], "nlz byte " + value);
        }
    }

    @Test
    void powerOfTwoChecksUseZeroOrSingleSetBitSemantics()
    {
        for (int bitIndex = 0; bitIndex < Integer.SIZE; bitIndex++)
        {
            assertTrue(BitUtil.isPowerOfTwo(1 << bitIndex), "int bit " + bitIndex);
        }
        for (int bitIndex = 0; bitIndex < Long.SIZE; bitIndex++)
        {
            assertTrue(BitUtil.isPowerOfTwo(1L << bitIndex), "long bit " + bitIndex);
        }

        assertTrue(BitUtil.isPowerOfTwo(0));
        assertTrue(BitUtil.isPowerOfTwo(0L));
        assertFalse(BitUtil.isPowerOfTwo(3));
        assertFalse(BitUtil.isPowerOfTwo(-1));
        assertFalse(BitUtil.isPowerOfTwo(3L));
        assertFalse(BitUtil.isPowerOfTwo(-1L));
    }

    @Test
    void nextHighestPowerOfTwoRoundsPositiveRepresentableValues()
    {
        assertEquals(0, BitUtil.nextHighestPowerOfTwo(0));
        assertEquals(0L, BitUtil.nextHighestPowerOfTwo(0L));

        for (int bitIndex = 0; bitIndex <= 30; bitIndex++)
        {
            final int power = 1 << bitIndex;
            assertEquals(power, BitUtil.nextHighestPowerOfTwo(power), "int power " + bitIndex);
            if (bitIndex > 1)
            {
                assertEquals(power, BitUtil.nextHighestPowerOfTwo(power - 1), "int below " + bitIndex);
            }
            if (bitIndex < 30)
            {
                assertEquals(power << 1, BitUtil.nextHighestPowerOfTwo(power + 1), "int above " + bitIndex);
            }
        }

        for (int bitIndex = 0; bitIndex <= 62; bitIndex++)
        {
            final long power = 1L << bitIndex;
            assertEquals(power, BitUtil.nextHighestPowerOfTwo(power), "long power " + bitIndex);
            if (bitIndex > 1)
            {
                assertEquals(power, BitUtil.nextHighestPowerOfTwo(power - 1), "long below " + bitIndex);
            }
            if (bitIndex < 62)
            {
                assertEquals(power << 1, BitUtil.nextHighestPowerOfTwo(power + 1), "long above " + bitIndex);
            }
        }
    }

    private static long popReference(final long[] values, final int offset, final int length)
    {
        long result = 0;
        for (int i = 0; i < length; i++)
        {
            result += Long.bitCount(values[offset + i]);
        }
        return result;
    }

    private static long popReference(final long[] a, final long[] b, final int offset, final int length,
                                     final LongBinaryOperator operation)
    {
        long result = 0;
        for (int i = 0; i < length; i++)
        {
            result += Long.bitCount(operation.applyAsLong(a[offset + i], b[offset + i]));
        }
        return result;
    }

    private static String message(final String operation, final int offset, final int length)
    {
        return operation + " at offset " + offset + " with length " + length;
    }
}
