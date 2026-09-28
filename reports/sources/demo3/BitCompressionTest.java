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

class BitCompressionTest
{
    @Test
    void combineAdjacentBitsMapsEveryInputBit()
    {
        for (int bitIndex = 0; bitIndex < Long.SIZE; bitIndex++)
        {
            final long input = 1L << bitIndex;
            final long expected = input | (bitIndex == Long.SIZE - 1 ? 0 : 1L << (bitIndex + 1));

            assertEquals(expected, BitCompression.combineAdjacentBits(input), "bit " + bitIndex);
        }
    }

    @Test
    void compressAndShiftOddBitsMapsOddBitsAndIgnoresEvenBits()
    {
        for (int outputBit = 0; outputBit < Integer.SIZE; outputBit++)
        {
            assertEquals(0, BitCompression.compressAndShiftOddBits(1L << (outputBit * 2)),
                         "even input bit " + (outputBit * 2));
            assertEquals(1L << outputBit,
                         BitCompression.compressAndShiftOddBits(1L << (outputBit * 2 + 1)),
                         "odd input bit " + (outputBit * 2 + 1));
        }
    }

    @Test
    void compressionReturnsAZeroExtendedThirtyTwoBitValue()
    {
        assertEquals(0, BitCompression.compressAndShiftOddBits(0));
        assertEquals(0, BitCompression.compressAndShiftOddBits(0x5555555555555555L));
        assertEquals(0xFFFFFFFFL, BitCompression.compressAndShiftOddBits(0xAAAAAAAAAAAAAAAAL));
        assertEquals(0xFFFFFFFFL, BitCompression.compressAndShiftOddBits(-1L));
        assertEquals(0x80000000L, BitCompression.compressAndShiftOddBits(Long.MIN_VALUE));
    }

    @Test
    void operationsMatchIndependentBitByBitReferences()
    {
        final Random random = new Random(0xB17C0FFEE0L);

        for (int iteration = 0; iteration < 10_000; iteration++)
        {
            final long input = random.nextLong();
            final long combined = BitCompression.combineAdjacentBits(input);
            final long compressed = BitCompression.compressAndShiftOddBits(input);
            final long pairOccupancy = BitCompression.compressAndShiftOddBits(combined);

            assertEquals(combineReference(input), combined, "combine iteration " + iteration);
            assertEquals(compressOddBitsReference(input), compressed, "compress iteration " + iteration);
            assertEquals(pairOccupancyReference(input), pairOccupancy, "composition iteration " + iteration);
            assertEquals(0, compressed >>> Integer.SIZE, "upper bits at iteration " + iteration);
        }
    }

    private static long combineReference(final long input)
    {
        long result = 0;
        for (int bitIndex = 0; bitIndex < Long.SIZE; bitIndex++)
        {
            if ((input & (1L << bitIndex)) != 0)
            {
                result |= 1L << bitIndex;
                if (bitIndex + 1 < Long.SIZE)
                {
                    result |= 1L << (bitIndex + 1);
                }
            }
        }
        return result;
    }

    private static long compressOddBitsReference(final long input)
    {
        long result = 0;
        for (int outputBit = 0; outputBit < Integer.SIZE; outputBit++)
        {
            if ((input & (1L << (outputBit * 2 + 1))) != 0)
            {
                result |= 1L << outputBit;
            }
        }
        return result;
    }

    private static long pairOccupancyReference(final long input)
    {
        long result = 0;
        for (int pair = 0; pair < Integer.SIZE; pair++)
        {
            if (((input >>> (pair * 2)) & 0b11L) != 0)
            {
                result |= 1L << pair;
            }
        }
        return result;
    }
}
