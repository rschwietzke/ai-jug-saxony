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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class BitCompressionTest
{
    @Test
    public void privateConstructor() throws Exception
    {
        Constructor<BitCompression> constructor = BitCompression.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        BitCompression instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    public void combineAdjacentBits()
    {
        assertEquals(0L, BitCompression.combineAdjacentBits(0L));
        // bit 0 -> 1 | (1<<1) = 3 (bits 0 and 1 set)
        assertEquals(3L, BitCompression.combineAdjacentBits(1L));
        // bit 1 -> 2 | (2<<1) = 6 (bits 1 and 2 set)
        assertEquals(6L, BitCompression.combineAdjacentBits(2L));
        // bits 0 and 1 -> 3 | 6 = 7
        assertEquals(7L, BitCompression.combineAdjacentBits(3L));

        // Test with various bit patterns
        for (int i = 0; i < 63; i++)
        {
            long val = 1L << i;
            long expected = val | (val << 1);
            assertEquals(expected, BitCompression.combineAdjacentBits(val));
        }
    }

    @Test
    public void compressAndShiftOddBits()
    {
        assertEquals(0L, BitCompression.compressAndShiftOddBits(0L));

        // Bit at pos 1 -> should become bit at pos 0 in result
        assertEquals(1L, BitCompression.compressAndShiftOddBits(1L << 1));

        // Bit at pos 3 -> should become bit at pos 1 in result
        assertEquals(1L << 1, BitCompression.compressAndShiftOddBits(1L << 3));

        // Bit at pos 5 -> should become bit at pos 2 in result
        assertEquals(1L << 2, BitCompression.compressAndShiftOddBits(1L << 5));

        // Bit at pos 63 -> should become bit at pos 31 in result
        assertEquals(1L << 31, BitCompression.compressAndShiftOddBits(1L << 63));

        // Even bits only should produce 0
        assertEquals(0L, BitCompression.compressAndShiftOddBits(0x5555555555555555L));

        // All odd bits set -> should produce 0xFFFFFFFFL (lower 32 bits all 1)
        assertEquals(0xFFFFFFFFL, BitCompression.compressAndShiftOddBits(0xAAAAAAAAAAAAAAAAL));

        // Test each individual odd bit position k in 0..31 -> original position 2k + 1
        for (int k = 0; k < 32; k++)
        {
            int oddBitPos = 2 * k + 1;
            long input = 1L << oddBitPos;
            long expected = 1L << k;
            assertEquals(expected, BitCompression.compressAndShiftOddBits(input), "Failed for k=" + k);
        }

        // Test combination and compression together (as used in IntTimeSeriesEntry)
        // If we set bit 0 or bit 1 in original, after combineAdjacentBits, bit 1 is set.
        // After compressAndShiftOddBits, bit 0 in lower 32 bits is set.
        long original = 1L; // bit 0 set
        long combined = BitCompression.combineAdjacentBits(original);
        long compressed = BitCompression.compressAndShiftOddBits(combined);
        assertEquals(1L, compressed);

        original = 2L; // bit 1 set
        combined = BitCompression.combineAdjacentBits(original);
        compressed = BitCompression.compressAndShiftOddBits(combined);
        assertEquals(1L, compressed);

        original = (1L << 4) | (1L << 7); // bit 4 (pair 4-5 -> index 2) and bit 7 (pair 6-7 -> index 3)
        combined = BitCompression.combineAdjacentBits(original);
        compressed = BitCompression.compressAndShiftOddBits(combined);
        assertEquals((1L << 2) | (1L << 3), compressed);
    }
}
