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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BitCompression Tests")
class BitCompressionTest
{
    @Test
    @DisplayName("Private constructor can be invoked via reflection for coverage")
    void privateConstructor() throws Exception
    {
        Constructor<BitCompression> constructor = BitCompression.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        BitCompression instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @Test
    @DisplayName("Zero input results in zero output")
    void zeroInput()
    {
        assertThat(BitCompression.combineAdjacentBits(0L)).isEqualTo(0L);
        assertThat(BitCompression.compressAndShiftOddBits(0L)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Single bits at even and odd positions map to correct combined/compressed bits")
    void singleBits()
    {
        for (int i = 0; i < 64; i++)
        {
            long input = 1L << i;
            long combined = BitCompression.combineAdjacentBits(input);
            long compressed = BitCompression.compressAndShiftOddBits(combined);

            int expectedBucket = i / 2;
            long expectedCompressed = 1L << expectedBucket;

            assertThat(compressed)
                    .as("Bit position " + i + " should map to compressed bucket " + expectedBucket)
                    .isEqualTo(expectedCompressed);
        }
    }

    @Test
    @DisplayName("Adjacent bit pairs merge into a single compressed bit")
    void adjacentPairs()
    {
        for (int pair = 0; pair < 32; pair++)
        {
            long bitEven = 1L << (2 * pair);
            long bitOdd = 1L << (2 * pair + 1);
            long pairMask = bitEven | bitOdd;

            long combined = BitCompression.combineAdjacentBits(pairMask);
            long compressed = BitCompression.compressAndShiftOddBits(combined);

            assertThat(compressed)
                    .as("Pair " + pair + " should compress to bit " + pair)
                    .isEqualTo(1L << pair);
        }
    }

    @Test
    @DisplayName("All bits set (0xFFFFFFFFFFFFFFFFL) compresses to 0xFFFFFFFFL (32 bits set)")
    void allBitsSet()
    {
        long allOnes = -1L;
        long combined = BitCompression.combineAdjacentBits(allOnes);
        long compressed = BitCompression.compressAndShiftOddBits(combined);

        assertThat(compressed).isEqualTo(0xFFFFFFFFL);
        assertThat(Long.bitCount(compressed)).isEqualTo(32);
    }

    @Test
    @DisplayName("Alternating bits compress correctly")
    void alternatingBits()
    {
        // 0x5555555555555555L has even bits 0, 2, 4, ..., 62 set
        long evenBits = 0x5555555555555555L;
        long combinedEven = BitCompression.combineAdjacentBits(evenBits);
        long compressedEven = BitCompression.compressAndShiftOddBits(combinedEven);
        assertThat(compressedEven).isEqualTo(0xFFFFFFFFL);

        // 0xAAAAAAAAAAAAAAAA has odd bits 1, 3, 5, ..., 63 set
        long oddBits = 0xAAAAAAAAAAAAAAAAL;
        long combinedOdd = BitCompression.combineAdjacentBits(oddBits);
        long compressedOdd = BitCompression.compressAndShiftOddBits(combinedOdd);
        assertThat(compressedOdd).isEqualTo(0xFFFFFFFFL);
    }

    @Test
    @DisplayName("Random bit patterns compress consistently with reference algorithm")
    void randomPatterns()
    {
        Random rng = new Random(42);
        for (int trial = 0; trial < 10_000; trial++)
        {
            long val = rng.nextLong();
            long combined = BitCompression.combineAdjacentBits(val);
            long compressed = BitCompression.compressAndShiftOddBits(combined);

            long expected = 0;
            for (int k = 0; k < 32; k++)
            {
                boolean bit2k = (val & (1L << (2 * k))) != 0;
                boolean bit2k1 = (val & (1L << (2 * k + 1))) != 0;
                if (bit2k || bit2k1)
                {
                    expected |= (1L << k);
                }
            }

            assertThat(compressed)
                    .as("Mismatch on random value: 0x" + Long.toHexString(val))
                    .isEqualTo(expected);
        }
    }
}
