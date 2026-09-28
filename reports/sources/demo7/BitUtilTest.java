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
 * Tests for {@link BitUtil}. The routines are validated against the JDK
 * built-ins ({@link Long#bitCount}, {@link Long#numberOfTrailingZeros},
 * {@link Long#numberOfLeadingZeros}) which act as the oracle.
 */
class BitUtilTest
{
    // ----------------------------------------------------------------
    // pop(long)
    // ----------------------------------------------------------------

    @Test
    void popMatchesLongBitCountForEdgeValues()
    {
        final long[] values = {
            0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
            0x5555555555555555L, 0xAAAAAAAAAAAAAAAAL,
            0x0123456789ABCDEFL, 0xFEDCBA9876543210L
        };

        for (final long v : values)
        {
            assertEquals(Long.bitCount(v), BitUtil.pop(v), "pop mismatch for " + v);
        }
    }

    @Test
    void popMatchesLongBitCountForSingleBits()
    {
        for (int i = 0; i < 64; i++)
        {
            final long v = 1L << i;
            assertEquals(1, BitUtil.pop(v), "single bit " + i);
            assertEquals(Long.bitCount(v), BitUtil.pop(v));
        }
    }

    @Test
    void popMatchesLongBitCountRandomized()
    {
        final Random random = new Random(1234);
        for (int i = 0; i < 100_000; i++)
        {
            final long v = random.nextLong();
            assertEquals(Long.bitCount(v), BitUtil.pop(v), "pop mismatch for " + v);
        }
    }

    // ----------------------------------------------------------------
    // pop_array / pop_intersect / pop_union / pop_andnot / pop_xor
    // ----------------------------------------------------------------

    @Test
    void popArrayMatchesSumOfBitCountsForAllWordCounts()
    {
        final Random random = new Random(7);
        final long[] a = randomArray(random, 40);

        // exercise every trailing-word path: 0..40 words covers the
        // 8-word loop, the 4-word, 2-word and 1-word tails
        for (int numWords = 0; numWords <= 40; numWords++)
        {
            final long expected = sumBitCount(a, 0, numWords);
            assertEquals(expected, BitUtil.pop_array(a, 0, numWords), "pop_array numWords=" + numWords);
        }
    }

    @Test
    void popArrayRespectsWordOffset()
    {
        final Random random = new Random(11);
        final long[] a = randomArray(random, 30);

        for (int offset = 0; offset < 20; offset++)
        {
            for (int numWords = 0; numWords <= 30 - offset; numWords++)
            {
                final long expected = sumBitCount(a, offset, numWords);
                assertEquals(expected, BitUtil.pop_array(a, offset, numWords),
                             "pop_array offset=" + offset + " numWords=" + numWords);
            }
        }
    }

    @Test
    void popArrayOnEmptyRangeIsZero()
    {
        final long[] a = {~0L, ~0L, ~0L};
        assertEquals(0L, BitUtil.pop_array(a, 0, 0));
        assertEquals(0L, BitUtil.pop_array(new long[0], 0, 0));
    }

    @Test
    void popSetOperationsMatchBitwiseOracles()
    {
        final Random random = new Random(99);
        final long[] a = randomArray(random, 40);
        final long[] b = randomArray(random, 40);

        for (int numWords = 0; numWords <= 40; numWords++)
        {
            assertEquals(sumBitCountOp(a, b, 0, numWords, Op.AND),
                         BitUtil.pop_intersect(a, b, 0, numWords), "intersect numWords=" + numWords);
            assertEquals(sumBitCountOp(a, b, 0, numWords, Op.OR),
                         BitUtil.pop_union(a, b, 0, numWords), "union numWords=" + numWords);
            assertEquals(sumBitCountOp(a, b, 0, numWords, Op.ANDNOT),
                         BitUtil.pop_andnot(a, b, 0, numWords), "andnot numWords=" + numWords);
            assertEquals(sumBitCountOp(a, b, 0, numWords, Op.XOR),
                         BitUtil.pop_xor(a, b, 0, numWords), "xor numWords=" + numWords);
        }
    }

    @Test
    void popSetOperationsRespectWordOffset()
    {
        final Random random = new Random(2024);
        final long[] a = randomArray(random, 25);
        final long[] b = randomArray(random, 25);

        for (int offset = 0; offset < 10; offset++)
        {
            for (int numWords = 0; numWords <= 25 - offset; numWords++)
            {
                assertEquals(sumBitCountOp(a, b, offset, numWords, Op.AND),
                             BitUtil.pop_intersect(a, b, offset, numWords));
                assertEquals(sumBitCountOp(a, b, offset, numWords, Op.OR),
                             BitUtil.pop_union(a, b, offset, numWords));
                assertEquals(sumBitCountOp(a, b, offset, numWords, Op.ANDNOT),
                             BitUtil.pop_andnot(a, b, offset, numWords));
                assertEquals(sumBitCountOp(a, b, offset, numWords, Op.XOR),
                             BitUtil.pop_xor(a, b, offset, numWords));
            }
        }
    }

    @Test
    void popSetOperationsDoNotModifyInputs()
    {
        final long[] a = {0xF0F0F0F0F0F0F0F0L, 0x0F0F0F0F0F0F0F0FL, ~0L};
        final long[] b = {0xFFFFFFFF00000000L, 0x00000000FFFFFFFFL, 0L};
        final long[] aCopy = a.clone();
        final long[] bCopy = b.clone();

        BitUtil.pop_intersect(a, b, 0, 3);
        BitUtil.pop_union(a, b, 0, 3);
        BitUtil.pop_andnot(a, b, 0, 3);
        BitUtil.pop_xor(a, b, 0, 3);

        assertEquals(java.util.Arrays.toString(aCopy), java.util.Arrays.toString(a));
        assertEquals(java.util.Arrays.toString(bCopy), java.util.Arrays.toString(b));
    }

    // ----------------------------------------------------------------
    // ntz(long) / ntz(int) / ntz2 / ntz3
    // ----------------------------------------------------------------

    @Test
    void ntzLongMatchesJdk()
    {
        assertEquals(64, BitUtil.ntz(0L), "ntz(0) must be 64");
        for (int i = 0; i < 64; i++)
        {
            assertEquals(i, BitUtil.ntz(1L << i), "single bit " + i);
        }

        final Random random = new Random(5);
        for (int i = 0; i < 100_000; i++)
        {
            final long v = random.nextLong();
            assertEquals(Long.numberOfTrailingZeros(v), BitUtil.ntz(v), "ntz mismatch for " + v);
        }
    }

    @Test
    void ntzIntMatchesJdk()
    {
        assertEquals(32, BitUtil.ntz(0), "ntz(0) must be 32");
        for (int i = 0; i < 32; i++)
        {
            assertEquals(i, BitUtil.ntz(1 << i), "single bit " + i);
        }

        final Random random = new Random(6);
        for (int i = 0; i < 100_000; i++)
        {
            final int v = random.nextInt();
            assertEquals(Integer.numberOfTrailingZeros(v), BitUtil.ntz(v), "ntz mismatch for " + v);
        }
    }

    @Test
    void ntz2MatchesJdkForNonZero()
    {
        assertEquals(64, BitUtil.ntz2(0L), "ntz2(0) must be 64");

        final Random random = new Random(7);
        for (int i = 0; i < 100_000; i++)
        {
            long v = random.nextLong();
            if (v == 0)
            {
                v = 1;
            }
            assertEquals(Long.numberOfTrailingZeros(v), BitUtil.ntz2(v), "ntz2 mismatch for " + v);
        }
    }

    @Test
    void ntz3MatchesJdkForNonZero()
    {
        // ntz3 is the Hacker's Delight variant; for a zero input it does not
        // short-circuit and therefore reports 63 instead of 64. Only non-zero
        // inputs are contractually equal to the JDK.
        assertEquals(63, BitUtil.ntz3(0L), "documented ntz3(0) quirk");

        final Random random = new Random(8);
        for (int i = 0; i < 100_000; i++)
        {
            long v = random.nextLong();
            if (v == 0)
            {
                v = 1;
            }
            assertEquals(Long.numberOfTrailingZeros(v), BitUtil.ntz3(v), "ntz3 mismatch for " + v);
        }
    }

    @Test
    void allNtzVariantsAgreeOnSingleBits()
    {
        for (int i = 0; i < 64; i++)
        {
            final long v = 1L << i;
            assertEquals(i, BitUtil.ntz(v));
            assertEquals(i, BitUtil.ntz2(v));
            assertEquals(i, BitUtil.ntz3(v));
        }
    }

    // ----------------------------------------------------------------
    // nlz(long)
    // ----------------------------------------------------------------

    @Test
    void nlzMatchesJdk()
    {
        assertEquals(64, BitUtil.nlz(0L), "nlz(0) must be 64");
        for (int i = 0; i < 64; i++)
        {
            assertEquals(63 - i, BitUtil.nlz(1L << i), "single bit " + i);
        }

        final Random random = new Random(9);
        for (int i = 0; i < 100_000; i++)
        {
            final long v = random.nextLong();
            assertEquals(Long.numberOfLeadingZeros(v), BitUtil.nlz(v), "nlz mismatch for " + v);
        }
    }

    // ----------------------------------------------------------------
    // ntzTable / nlzTable consistency
    // ----------------------------------------------------------------

    @Test
    void ntzTableIsCorrectForEveryByte()
    {
        assertEquals(256, BitUtil.ntzTable.length);
        assertEquals(8, BitUtil.ntzTable[0], "zero byte reports 8 trailing zeros");
        for (int b = 1; b < 256; b++)
        {
            assertEquals(Integer.numberOfTrailingZeros(b), BitUtil.ntzTable[b], "ntzTable[" + b + "]");
        }
    }

    @Test
    void nlzTableIsCorrectForEveryByte()
    {
        assertEquals(256, BitUtil.nlzTable.length);
        assertEquals(8, BitUtil.nlzTable[0], "zero byte reports 8 leading zeros");
        for (int b = 1; b < 256; b++)
        {
            // leading zeros within an 8-bit byte
            assertEquals(8 - (32 - Integer.numberOfLeadingZeros(b)), BitUtil.nlzTable[b], "nlzTable[" + b + "]");
        }
    }

    // ----------------------------------------------------------------
    // isPowerOfTwo
    // ----------------------------------------------------------------

    @Test
    void isPowerOfTwoInt()
    {
        assertTrue(BitUtil.isPowerOfTwo(0), "zero counts as power of two");
        for (int i = 0; i < 31; i++)
        {
            assertTrue(BitUtil.isPowerOfTwo(1 << i), "1<<" + i);
        }
        assertFalse(BitUtil.isPowerOfTwo(3));
        assertFalse(BitUtil.isPowerOfTwo(5));
        assertFalse(BitUtil.isPowerOfTwo(6));
        assertFalse(BitUtil.isPowerOfTwo(1000));
        assertFalse(BitUtil.isPowerOfTwo(Integer.MAX_VALUE));
    }

    @Test
    void isPowerOfTwoLong()
    {
        assertTrue(BitUtil.isPowerOfTwo(0L), "zero counts as power of two");
        for (int i = 0; i < 63; i++)
        {
            assertTrue(BitUtil.isPowerOfTwo(1L << i), "1L<<" + i);
        }
        assertFalse(BitUtil.isPowerOfTwo(3L));
        assertFalse(BitUtil.isPowerOfTwo(5L));
        assertFalse(BitUtil.isPowerOfTwo(1000L));
        assertFalse(BitUtil.isPowerOfTwo(Long.MAX_VALUE));
    }

    // ----------------------------------------------------------------
    // nextHighestPowerOfTwo
    // ----------------------------------------------------------------

    @Test
    void nextHighestPowerOfTwoInt()
    {
        assertEquals(0, BitUtil.nextHighestPowerOfTwo(0));
        assertEquals(1, BitUtil.nextHighestPowerOfTwo(1));
        assertEquals(2, BitUtil.nextHighestPowerOfTwo(2));
        assertEquals(4, BitUtil.nextHighestPowerOfTwo(3));
        assertEquals(4, BitUtil.nextHighestPowerOfTwo(4));
        assertEquals(8, BitUtil.nextHighestPowerOfTwo(5));
        assertEquals(1024, BitUtil.nextHighestPowerOfTwo(1000));
        assertEquals(4096, BitUtil.nextHighestPowerOfTwo(3600));

        // already a power of two stays unchanged
        for (int i = 0; i < 30; i++)
        {
            final int p = 1 << i;
            assertEquals(p, BitUtil.nextHighestPowerOfTwo(p));
            // the value just above must round up to the next power
            assertEquals(p << 1, BitUtil.nextHighestPowerOfTwo(p + 1));
        }
    }

    @Test
    void nextHighestPowerOfTwoLong()
    {
        assertEquals(0L, BitUtil.nextHighestPowerOfTwo(0L));
        assertEquals(1L, BitUtil.nextHighestPowerOfTwo(1L));
        assertEquals(2L, BitUtil.nextHighestPowerOfTwo(2L));
        assertEquals(4L, BitUtil.nextHighestPowerOfTwo(3L));
        assertEquals(4096L, BitUtil.nextHighestPowerOfTwo(3600L));

        for (int i = 0; i < 62; i++)
        {
            final long p = 1L << i;
            assertEquals(p, BitUtil.nextHighestPowerOfTwo(p));
            assertEquals(p << 1, BitUtil.nextHighestPowerOfTwo(p + 1));
        }
    }

    @Test
    void nextHighestPowerOfTwoResultIsAlwaysPowerOfTwo()
    {
        final Random random = new Random(13);
        for (int i = 0; i < 10_000; i++)
        {
            final int v = random.nextInt(1 << 30) + 1;
            final int r = BitUtil.nextHighestPowerOfTwo(v);
            assertTrue(BitUtil.isPowerOfTwo(r), "result must be a power of two for " + v);
            assertTrue(r >= v, "result must be >= input for " + v);
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private enum Op
    {
        AND, OR, ANDNOT, XOR
    }

    private static long[] randomArray(final Random random, final int length)
    {
        final long[] a = new long[length];
        for (int i = 0; i < length; i++)
        {
            a[i] = random.nextLong();
        }
        return a;
    }

    private static long sumBitCount(final long[] a, final int offset, final int numWords)
    {
        long total = 0;
        for (int i = offset; i < offset + numWords; i++)
        {
            total += Long.bitCount(a[i]);
        }
        return total;
    }

    private static long sumBitCountOp(final long[] a, final long[] b, final int offset, final int numWords, final Op op)
    {
        long total = 0;
        for (int i = offset; i < offset + numWords; i++)
        {
            final long combined = switch (op)
            {
                case AND -> a[i] & b[i];
                case OR -> a[i] | b[i];
                case ANDNOT -> a[i] & ~b[i];
                case XOR -> a[i] ^ b[i];
            };
            total += Long.bitCount(combined);
        }
        return total;
    }
}
