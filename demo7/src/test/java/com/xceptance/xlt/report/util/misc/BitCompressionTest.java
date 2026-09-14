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
package com.xceptance.xlt.report.util.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BitCompression}. The two routines implement the
 * "join adjacent buckets" primitive used by the time series scaling:
 * {@code combineAdjacentBits} ORs each bit with its left neighbour and
 * {@code compressAndShiftOddBits} gathers the odd bits into the low 32 bits.
 */
class BitCompressionTest
{
    // ----------------------------------------------------------------
    // combineAdjacentBits
    // ----------------------------------------------------------------

    @Test
    void combineAdjacentBitsIsValueOrValueShiftedLeft()
    {
        final Random random = new Random(1);
        for (int i = 0; i < 10_000; i++)
        {
            final long v = random.nextLong();
            assertEquals(v | (v << 1), BitCompression.combineAdjacentBits(v));
        }
    }

    @Test
    void combineAdjacentBitsSpreadsSingleBitToNeighbour()
    {
        for (int i = 0; i < 63; i++)
        {
            final long v = 1L << i;
            // bit i and its left neighbour i+1 are both set
            assertEquals((1L << i) | (1L << (i + 1)), BitCompression.combineAdjacentBits(v));
        }
    }

    @Test
    void combineAdjacentBitsTopBitHasNoNeighbour()
    {
        // bit 63 shifted left overflows, so only bit 63 remains
        assertEquals(1L << 63, BitCompression.combineAdjacentBits(1L << 63));
    }

    @Test
    void combineAdjacentBitsOfZeroIsZero()
    {
        assertEquals(0L, BitCompression.combineAdjacentBits(0L));
    }

    // ----------------------------------------------------------------
    // compressAndShiftOddBits
    // ----------------------------------------------------------------

    @Test
    void compressMapsSingleOddBitToPackedPosition()
    {
        // odd bit (2k+1) is gathered into position k
        for (int k = 0; k < 32; k++)
        {
            final long v = 1L << (2 * k + 1);
            assertEquals(1L << k, BitCompression.compressAndShiftOddBits(v), "odd bit " + (2 * k + 1));
        }
    }

    @Test
    void compressDiscardsEvenBits()
    {
        // an isolated even bit produces no odd bit and is dropped
        for (int k = 0; k < 32; k++)
        {
            final long v = 1L << (2 * k);
            assertEquals(0L, BitCompression.compressAndShiftOddBits(v), "even bit " + (2 * k));
        }
    }

    @Test
    void compressAllOddBitsSetFillsLow32Bits()
    {
        assertEquals(0xFFFFFFFFL, BitCompression.compressAndShiftOddBits(0xAAAAAAAAAAAAAAAAL));
    }

    @Test
    void compressAllEvenBitsSetIsZero()
    {
        assertEquals(0L, BitCompression.compressAndShiftOddBits(0x5555555555555555L));
    }

    @Test
    void compressResultNeverExceeds32Bits()
    {
        final Random random = new Random(2);
        for (int i = 0; i < 10_000; i++)
        {
            final long v = random.nextLong();
            final long r = BitCompression.compressAndShiftOddBits(v);
            assertEquals(0L, r & 0xFFFFFFFF00000000L, "upper 32 bits must be clear for " + v);
        }
    }

    @Test
    void compressMatchesReferenceGatherOfOddBits()
    {
        final Random random = new Random(3);
        for (int i = 0; i < 10_000; i++)
        {
            final long v = random.nextLong();
            assertEquals(referenceCompressOddBits(v), BitCompression.compressAndShiftOddBits(v), "mismatch for " + v);
        }
    }

    // ----------------------------------------------------------------
    // Composition: combine + compress == halve the bit index
    // ----------------------------------------------------------------

    @Test
    void combineThenCompressHalvesSingleBitIndex()
    {
        for (int i = 0; i < 64; i++)
        {
            final long v = 1L << i;
            final long combined = BitCompression.combineAdjacentBits(v);
            final long compressed = BitCompression.compressAndShiftOddBits(combined);
            assertEquals(1L << (i / 2), compressed, "bit " + i + " must map to " + (i / 2));
        }
    }

    @Test
    void combineThenCompressJoinsAdjacentPairIntoOneBucket()
    {
        // two adjacent bits (2k, 2k+1) collapse into a single bucket k
        for (int k = 0; k < 31; k++)
        {
            final long v = (1L << (2 * k)) | (1L << (2 * k + 1));
            final long compressed = BitCompression.compressAndShiftOddBits(BitCompression.combineAdjacentBits(v));
            assertEquals(1L << k, compressed, "pair " + k);
        }
    }

    // ----------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------

    /**
     * Straightforward reference implementation: gather the odd bits
     * (1, 3, 5, ..., 63) into the low 32 bit positions.
     */
    private static long referenceCompressOddBits(final long value)
    {
        long result = 0;
        for (int k = 0; k < 32; k++)
        {
            if ((value & (1L << (2 * k + 1))) != 0)
            {
                result |= (1L << k);
            }
        }
        return result;
    }
}
