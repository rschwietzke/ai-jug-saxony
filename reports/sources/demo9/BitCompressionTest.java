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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class BitCompressionTest
{
    @Test
    @DisplayName("combineAdjacentBits with edge case values")
    void testCombineAdjacentBitsEdgeCases()
    {
        assertThat(BitCompression.combineAdjacentBits(0L)).isEqualTo(0L);
        assertThat(BitCompression.combineAdjacentBits(-1L)).isEqualTo(-1L);
        assertThat(BitCompression.combineAdjacentBits(0x5555555555555555L)).isEqualTo(-1L);
    }

    @Test
    @DisplayName("combineAdjacentBits sets bit at 2k and 2k+1 when bit 2k is set")
    void testCombineAdjacentBitsEvenBit()
    {
        for (int k = 0; k < 32; k++)
        {
            long input = 1L << (2 * k);
            long expected = (1L << (2 * k)) | (1L << (2 * k + 1));
            assertThat(BitCompression.combineAdjacentBits(input)).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("compressAndShiftOddBits extracts only odd bits into lower 32 bits")
    void testCompressAndShiftOddBitsSingleBits()
    {
        assertThat(BitCompression.compressAndShiftOddBits(0L)).isEqualTo(0L);
        assertThat(BitCompression.compressAndShiftOddBits(-1L)).isEqualTo(0xFFFFFFFFL);
        assertThat(BitCompression.compressAndShiftOddBits(0x5555555555555555L)).isEqualTo(0L);
        assertThat(BitCompression.compressAndShiftOddBits(0xAAAAAAAAAAAAAAAAL)).isEqualTo(0xFFFFFFFFL);

        // Every odd bit position (2k + 1) maps to position k
        for (int k = 0; k < 32; k++)
        {
            long oddBitInput = 1L << (2 * k + 1);
            long expected = 1L << k;
            assertThat(BitCompression.compressAndShiftOddBits(oddBitInput)).isEqualTo(expected);

            // Even bit input produces 0
            long evenBitInput = 1L << (2 * k);
            assertThat(BitCompression.compressAndShiftOddBits(evenBitInput)).isEqualTo(0L);
        }
    }

    @Test
    @DisplayName("combineAdjacentBits followed by compressAndShiftOddBits performs pair-wise OR downsampling")
    void testCombineAndCompressPairwiseOr()
    {
        Random rng = new Random(42);
        for (int iteration = 0; iteration < 1000; iteration++)
        {
            long input = rng.nextLong();
            long combined = BitCompression.combineAdjacentBits(input);
            long compressed = BitCompression.compressAndShiftOddBits(combined);

            // Verify each of the 32 resulting bits
            for (int k = 0; k < 32; k++)
            {
                boolean bit2k = ((input >>> (2 * k)) & 1L) == 1L;
                boolean bit2kPlus1 = ((input >>> (2 * k + 1)) & 1L) == 1L;
                boolean expectedBitK = bit2k || bit2kPlus1;

                boolean actualBitK = ((compressed >>> k) & 1L) == 1L;
                assertThat(actualBitK).as("Mismatch at bit %d for input 0x%X", k, input).isEqualTo(expectedBitK);
            }

            // High 32 bits must always be zero
            assertThat(compressed >>> 32).isEqualTo(0L);
        }
    }
}
