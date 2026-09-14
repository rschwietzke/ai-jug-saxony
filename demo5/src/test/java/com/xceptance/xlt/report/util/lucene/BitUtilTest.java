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
package com.xceptance.xlt.report.util.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link BitUtil} port of the Apache Lucene/Solr bit twiddling
 * routines.
 */
public class BitUtilTest
{
    @Test
    public void popMatchesJdkForRandomLongs()
    {
        final Random random = new Random(1);
        for (int i = 0; i < 10_000; i++)
        {
            final long value = random.nextLong();
            assertEquals(Long.bitCount(value), BitUtil.pop(value), Long.toHexString(value));
        }
    }

    @Test
    public void popKnownValues()
    {
        assertEquals(0, BitUtil.pop(0L));
        assertEquals(1, BitUtil.pop(1L));
        assertEquals(1, BitUtil.pop(1L << 63));
        assertEquals(64, BitUtil.pop(-1L));
        assertEquals(32, BitUtil.pop(0xFFFF_FFFFL));
        assertEquals(32, BitUtil.pop(0xFFFF_FFFF_0000_0000L));
    }

    @Test
    public void popArrayMatchesJdk()
    {
        final Random random = new Random(2);
        final long[] data = new long[40];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = random.nextLong();
        }

        final int[] offsets = {0, 1, 5, 17};
        final int[] lengths = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 16, 17, 31, 32, 33};
        for (final int offset : offsets)
        {
            for (final int length : lengths)
            {
                if (offset + length > data.length)
                {
                    continue;
                }
                long expected = 0;
                for (int i = offset; i < offset + length; i++)
                {
                    expected += Long.bitCount(data[i]);
                }
                assertEquals(expected, BitUtil.pop_array(data, offset, length), "offset " + offset + " length " + length);
            }
        }
    }

    @Test
    public void popIntersectUnionAndNotXorMatchJdk()
    {
        final Random random = new Random(3);
        final long[] a = new long[40];
        final long[] b = new long[40];
        for (int i = 0; i < a.length; i++)
        {
            a[i] = random.nextLong();
            b[i] = random.nextLong();
        }

        final int[] offsets = {0, 3};
        final int[] lengths = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 16, 17, 31, 32, 33};
        for (final int offset : offsets)
        {
            for (final int length : lengths)
            {
                if (offset + length > a.length)
                {
                    continue;
                }
                final String where = "offset " + offset + " length " + length;
                assertEquals(sumBitCount(a, b, offset, length, (x, y) -> x & y), BitUtil.pop_intersect(a, b, offset, length), "intersect " + where);
                assertEquals(sumBitCount(a, b, offset, length, (x, y) -> x | y), BitUtil.pop_union(a, b, offset, length), "union " + where);
                assertEquals(sumBitCount(a, b, offset, length, (x, y) -> x & ~y), BitUtil.pop_andnot(a, b, offset, length), "andnot " + where);
                assertEquals(sumBitCount(a, b, offset, length, (x, y) -> x ^ y), BitUtil.pop_xor(a, b, offset, length), "xor " + where);
            }
        }
    }

    private static long sumBitCount(final long[] a, final long[] b, final int offset, final int length, final java.util.function.LongBinaryOperator op)
    {
        long total = 0;
        for (int i = offset; i < offset + length; i++)
        {
            total += Long.bitCount(op.applyAsLong(a[i], b[i]));
        }
        return total;
    }

    @Test
    public void ntzLongMatchesJdk()
    {
        final Random random = new Random(4);
        for (int i = 0; i < 10_000; i++)
        {
            final long value = random.nextLong();
            assertEquals(Long.numberOfTrailingZeros(value), BitUtil.ntz(value), Long.toHexString(value));
        }
        // ntz of zero is 64 by definition (all 64 bits are zero)
        assertEquals(64, BitUtil.ntz(0L));
    }

    @Test
    public void ntzIntMatchesJdk()
    {
        final Random random = new Random(5);
        for (int i = 0; i < 10_000; i++)
        {
            final int value = random.nextInt();
            assertEquals(Integer.numberOfTrailingZeros(value), BitUtil.ntz(value), Integer.toHexString(value));
        }
        assertEquals(32, BitUtil.ntz(0));
    }

    @Test
    public void ntzVariantsAgree()
    {
        final Random random = new Random(6);
        for (int i = 0; i < 10_000; i++)
        {
            final long value = random.nextLong();
            if (value == 0)
            {
                continue;
            }
            final int expected = BitUtil.ntz(value);
            assertEquals(expected, BitUtil.ntz2(value), Long.toHexString(value));
            assertEquals(expected, BitUtil.ntz3(value), Long.toHexString(value));
        }
    }

    @Test
    public void isPowerOfTwoMatchesJdkForInts()
    {
        final int[] values = {0, 1, 2, 3, 4, 5, 7, 8, 9, 16, 255, 256, 257, 1 << 20, (1 << 20) + 1, 1 << 30, -1, 0x8000_0000};
        for (final int v : values)
        {
            assertEquals(isPowerOfTwoRef(v), BitUtil.isPowerOfTwo(v), "int " + v);
        }

        final Random random = new Random(8);
        for (int i = 0; i < 10_000; i++)
        {
            final int v = random.nextInt();
            assertEquals(isPowerOfTwoRef(v), BitUtil.isPowerOfTwo(v), "int " + v);
        }
    }

    @Test
    public void isPowerOfTwoMatchesJdkForLongs()
    {
        final long[] values = {0L, 1L, 2L, 3L, 4L, 1L << 40, (1L << 40) + 1, 1L << 62, (1L << 62) + 1, -1L, 0x8000_0000_0000_0000L};
        for (final long v : values)
        {
            assertEquals(isPowerOfTwoRef(v), BitUtil.isPowerOfTwo(v), "long " + v);
        }

        final Random random = new Random(9);
        for (int i = 0; i < 10_000; i++)
        {
            final long v = random.nextLong();
            assertEquals(isPowerOfTwoRef(v), BitUtil.isPowerOfTwo(v), "long " + v);
        }
    }

    @Test
    public void nextHighestPowerOfTwoMatchesReferenceForInts()
    {
        final int[] values = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 33, 100, 255, 256, 257, 1_000, 1 << 20, (1 << 20) + 1, 1 << 30};
        for (final int v : values)
        {
            assertEquals(nextPowerOfTwoRef(v), BitUtil.nextHighestPowerOfTwo(v), "int " + v);
        }
    }

    @Test
    public void nextHighestPowerOfTwoMatchesReferenceForLongs()
    {
        final long[] values = {0L, 1L, 2L, 3L, 4L, 5L, 100L, 1L << 20, (1L << 20) + 1, 1L << 40, (1L << 40) + 1, 1L << 61, (1L << 61) + 1, 1L << 62};
        for (final long v : values)
        {
            assertEquals(nextPowerOfTwoRef(v), BitUtil.nextHighestPowerOfTwo(v), "long " + v);
        }
    }

    private static boolean isPowerOfTwoRef(final int v)
    {
        return (v & (v - 1)) == 0;
    }

    private static boolean isPowerOfTwoRef(final long v)
    {
        return (v & (v - 1)) == 0;
    }

    private static int nextPowerOfTwoRef(final int v)
    {
        if (v <= 1)
        {
            return v == 0 ? 0 : 1;
        }
        return 1 << (32 - Integer.numberOfLeadingZeros(v - 1));
    }

    private static long nextPowerOfTwoRef(final long v)
    {
        if (v <= 1)
        {
            return v == 0 ? 0 : 1;
        }
        return 1L << (64 - Long.numberOfLeadingZeros(v - 1));
    }
}
