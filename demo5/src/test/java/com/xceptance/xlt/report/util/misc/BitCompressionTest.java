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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link BitCompression} helper, the two step primitive that
 * {@code IntTimeSeriesEntry} uses to halve the resolution of its approximate
 * distinct value bitmap.
 */
public class BitCompressionTest
{
    @Test
    public void combineAdjacentBitsBasics()
    {
        assertEquals(0L, BitCompression.combineAdjacentBits(0L));
        assertEquals(-1L, BitCompression.combineAdjacentBits(-1L));
        // 0b101 -> 0b101 | 0b1010 = 0b1111
        assertEquals(0b1111L, BitCompression.combineAdjacentBits(0b101L));
        // every bit is ORed with its right neighbour -> never fewer bits set
        assertTrue(Long.bitCount(BitCompression.combineAdjacentBits(0x8000_0000_0000_0001L)) >= 2);
    }

    @Test
    public void compressAndShiftOddBitsBasics()
    {
        assertEquals(0L, BitCompression.compressAndShiftOddBits(0L));
        // only odd bits (positions 1, 3, 5, ...) are extracted:
        // 0xAAAA... has all odd bits set -> 32 ones packed into the lower 32 bits
        assertEquals(0xFFFF_FFFFL, BitCompression.compressAndShiftOddBits(0xAAAA_AAAA_AAAA_AAAAL));
        // 0x5555... has all even bits set -> nothing to extract
        assertEquals(0L, BitCompression.compressAndShiftOddBits(0x5555_5555_5555_5555L));
        // result never uses more than the lower 32 bits
        assertTrue(BitCompression.compressAndShiftOddBits(-1L) <= 0xFFFF_FFFFL);
    }

    @Test
    public void singleBitMapsToHalfItsIndex()
    {
        // one set bit at position j in the old word must end up at position j >> 1
        for (int j = 0; j < 64; j++)
        {
            final long combined = BitCompression.combineAdjacentBits(1L << j);
            final long compressed = BitCompression.compressAndShiftOddBits(combined);
            final long expected = 1L << (j >> 1);
            assertEquals(expected, compressed, "mapping of bit " + j);
        }
    }

    @Test
    public void adjacentPairsCollapseIntoOneBit()
    {
        // if both members of a pair (2k, 2k+1) are set, the result is still a single bit at k
        for (int k = 0; k < 32; k++)
        {
            final long pair = 3L << (2 * k);
            final long compressed = BitCompression.compressAndShiftOddBits(BitCompression.combineAdjacentBits(pair));
            assertEquals(1L, Long.bitCount(compressed), "expected exactly one bit for pair " + k);
            assertEquals(1L << k, compressed, "expected bit at position " + k);
        }
    }

    @Test
    public void compressionOresAdjacentBuckets()
    {
        // The combined operation must be semantically: new bit k is set iff at least one of
        // the old bits 2k or 2k+1 was set.
        final Random random = new Random(42);
        for (int trial = 0; trial < 500; trial++)
        {
            final long mask = random.nextLong();
            long expected = 0;
            for (int k = 0; k < 32; k++)
            {
                if ((mask & (3L << (2 * k))) != 0)
                {
                    expected |= (1L << k);
                }
            }
            final long actual = BitCompression.compressAndShiftOddBits(BitCompression.combineAdjacentBits(mask));
            assertEquals(expected, actual, "mask " + Long.toHexString(mask));
        }
    }

    @Test
    public void repeatedCompressionHalvesPositionsAgain()
    {
        // one fold moves bit j to j >> 1; a second fold of the *same* word must move it to j >> 2
        for (int j = 2; j < 64; j++)
        {
            final long first = BitCompression.compressAndShiftOddBits(BitCompression.combineAdjacentBits(1L << j));
            final long second = BitCompression.compressAndShiftOddBits(BitCompression.combineAdjacentBits(first));
            assertEquals(1L << (j >> 2), second, "double fold of bit " + j);
        }
    }
}
