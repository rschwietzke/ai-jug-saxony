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
package com.xceptance.xlt.report.util.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BitCompression}.
 */
class BitCompressionTest
{
    @Test
    void combineAdjacentBits_zero()
    {
        assertEquals(0L, BitCompression.combineAdjacentBits(0L));
    }

    @Test
    void combineAdjacentBits_singleBitAtPosition0()
    {
        // bit 0 set -> bit 0 | (bit 0 << 1) = bits 0 and 1 set
        assertEquals(0b11L, BitCompression.combineAdjacentBits(0b01L));
    }

    @Test
    void combineAdjacentBits_singleBitAtPosition2()
    {
        // bit 2 -> bit 2 | (bit 2 << 1) = bits 2,3
        assertEquals(0b1100L, BitCompression.combineAdjacentBits(0b0100L));
    }

    @Test
    void combineAdjacentBits_twoAdjacentBitsAlreadySet()
    {
        // bits 0,1 -> 0b11 | 0b110 = 0b111
        assertEquals(0b111L, BitCompression.combineAdjacentBits(0b11L));
    }

    @Test
    void combineAdjacentBits_alternatingPattern()
    {
        // 0b0101 -> 0b0101 | 0b1010 = 0b1111
        assertEquals(0b1111L, BitCompression.combineAdjacentBits(0b0101L));
    }

    @Test
    void combineAdjacentBits_highBitSet()
    {
        // bit 63 -> bit 63 | (bit 63 << 1) — shift drops off, result stays bit 63
        assertEquals(1L << 63, BitCompression.combineAdjacentBits(1L << 63));
    }

    @Test
    void combineAdjacentBits_allBitsSet()
    {
        // all bits -> stays all bits (overflow from << 1 discarded)
        assertEquals(-1L, BitCompression.combineAdjacentBits(-1L));
    }

    @Test
    void combineAdjacentBits_expandsRunsByOne()
    {
        // each application extends a run of set bits by one position to the left
        long once = BitCompression.combineAdjacentBits(0b0001L);
        assertEquals(0b11L, once);
        long twice = BitCompression.combineAdjacentBits(once);
        assertEquals(0b111L, twice);
        long thrice = BitCompression.combineAdjacentBits(twice);
        assertEquals(0b1111L, thrice);
    }

    @Test
    void compressAndShiftOddBits_zero()
    {
        assertEquals(0L, BitCompression.compressAndShiftOddBits(0L));
    }

    @Test
    void compressAndShiftOddBits_singleOddBit()
    {
        // bit 1 -> lower bit 0
        assertEquals(0b1L, BitCompression.compressAndShiftOddBits(0b10L));
    }

    @Test
    void compressAndShiftOddBits_singleOddBitAtPosition3()
    {
        // bit 3 -> bit position (3-1)/2 = 1
        assertEquals(0b10L, BitCompression.compressAndShiftOddBits(0b1000L));
    }

    @Test
    void compressAndShiftOddBits_singleOddBitAtPosition5()
    {
        // bit 5 -> bit position (5-1)/2 = 2
        assertEquals(0b100L, BitCompression.compressAndShiftOddBits(0b100000L));
    }

    @Test
    void compressAndShiftOddBits_multipleOddBits()
    {
        // bits 1 and 3 -> bits 0 and 1
        assertEquals(0b11L, BitCompression.compressAndShiftOddBits(0b1010L));
    }

    @Test
    void compressAndShiftOddBits_evenBitsAreIgnored()
    {
        // Only even bits set -> result is 0
        assertEquals(0L, BitCompression.compressAndShiftOddBits(0b0101L));
    }

    @Test
    void compressAndShiftOddBits_allOddBitsSet()
    {
        // all odd bits: 0xAAAA... -> lower 32 bits all 1
        assertEquals(0xFFFFFFFFL, BitCompression.compressAndShiftOddBits(0xAAAAAAAAAAAAAAAAL));
    }

    @Test
    void compressAndShiftOddBits_highestOddBit()
    {
        // bit 63 -> bit 31
        assertEquals(1L << 31, BitCompression.compressAndShiftOddBits(1L << 63));
    }

    @Test
    void compressAndShiftOddBits_allBitsSet()
    {
        // All bits: odd bits are 1,3,5,...,63 -> compressed = 0xFFFFFFFF
        assertEquals(0xFFFFFFFFL, BitCompression.compressAndShiftOddBits(-1L));
    }

    @Test
    void combined_compressionHalvesBitset()
    {
        // Simulate what IntTimeSeriesEntry does during scale-up:
        // set bits 0, 2, 4 in the "low" word -> combine -> compress
        long input = 0b10101L; // bits 0, 2, 4
        long combined = BitCompression.combineAdjacentBits(input);
        long compressed = BitCompression.compressAndShiftOddBits(combined);

        // bits 0,2,4 -> combine -> 0,1 + 2,3 + 4,5 = 0b111111
        // compress odd bits (1,3,5) -> 0b111
        assertEquals(0b111L, compressed);
    }

    @Test
    void combined_preservesInformationThatBucketWasUsed()
    {
        // Any set bit in a pair should mark the pair as used after compression
        for (int i = 0; i < 64; i++)
        {
            long input = 1L << i;
            long combined = BitCompression.combineAdjacentBits(input);
            long compressed = BitCompression.compressAndShiftOddBits(combined);

            int expectedPair = i / 2;
            if (expectedPair < 32)
            {
                assertEquals(1L, (compressed >> expectedPair) & 1L,
                    "bit " + i + " should set pair " + expectedPair);
            }
        }
    }
}
