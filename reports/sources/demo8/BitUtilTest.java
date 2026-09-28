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
package com.xceptance.xlt.report.util.lucene;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class BitUtilTest
{
    @Test
    public void privateConstructor() throws Exception
    {
        Constructor<BitUtil> constructor = BitUtil.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        BitUtil instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    public void popLong()
    {
        assertEquals(0, BitUtil.pop(0L));
        assertEquals(1, BitUtil.pop(1L));
        assertEquals(1, BitUtil.pop(2L));
        assertEquals(2, BitUtil.pop(3L));
        assertEquals(64, BitUtil.pop(-1L));
        assertEquals(32, BitUtil.pop(0x5555555555555555L));
        assertEquals(32, BitUtil.pop(0xAAAAAAAAAAAAAAAAL));
        assertEquals(1, BitUtil.pop(1L << 63));
        assertEquals(1, BitUtil.pop(1L << 31));
        assertEquals(1, BitUtil.pop(1L << 32));

        Random rnd = new Random(42);
        for (int i = 0; i < 1000; i++)
        {
            long val = rnd.nextLong();
            assertEquals(Long.bitCount(val), BitUtil.pop(val));
        }
    }

    @Test
    public void popArray()
    {
        long[] empty = new long[0];
        assertEquals(0, BitUtil.pop_array(empty, 0, 0));

        long[] single = new long[]{0b1011L};
        assertEquals(3, BitUtil.pop_array(single, 0, 1));

        // Test lengths from 0 to 25 to hit all trailing branches (0, 1, 2, 3, 4, 5, 6, 7, >=8)
        Random rnd = new Random(12345);
        for (int len = 0; len <= 25; len++)
        {
            long[] arr = new long[len + 4];
            int offset = 2;
            long expected = 0;
            for (int i = 0; i < len; i++)
            {
                arr[offset + i] = rnd.nextLong();
                expected += Long.bitCount(arr[offset + i]);
            }
            long actual = BitUtil.pop_array(arr, offset, len);
            assertEquals(expected, actual, "Failed for length: " + len);
        }
    }

    @Test
    public void popIntersect()
    {
        Random rnd = new Random(54321);
        for (int len = 0; len <= 25; len++)
        {
            long[] A = new long[len + 4];
            long[] B = new long[len + 4];
            int offset = 2;
            long expected = 0;
            for (int i = 0; i < len; i++)
            {
                A[offset + i] = rnd.nextLong();
                B[offset + i] = rnd.nextLong();
                expected += Long.bitCount(A[offset + i] & B[offset + i]);
            }
            long actual = BitUtil.pop_intersect(A, B, offset, len);
            assertEquals(expected, actual, "Failed pop_intersect for length: " + len);
        }
    }

    @Test
    public void popUnion()
    {
        Random rnd = new Random(67890);
        for (int len = 0; len <= 25; len++)
        {
            long[] A = new long[len + 4];
            long[] B = new long[len + 4];
            int offset = 2;
            long expected = 0;
            for (int i = 0; i < len; i++)
            {
                A[offset + i] = rnd.nextLong();
                B[offset + i] = rnd.nextLong();
                expected += Long.bitCount(A[offset + i] | B[offset + i]);
            }
            long actual = BitUtil.pop_union(A, B, offset, len);
            assertEquals(expected, actual, "Failed pop_union for length: " + len);
        }
    }

    @Test
    public void popAndnot()
    {
        Random rnd = new Random(13579);
        for (int len = 0; len <= 25; len++)
        {
            long[] A = new long[len + 4];
            long[] B = new long[len + 4];
            int offset = 2;
            long expected = 0;
            for (int i = 0; i < len; i++)
            {
                A[offset + i] = rnd.nextLong();
                B[offset + i] = rnd.nextLong();
                expected += Long.bitCount(A[offset + i] & ~B[offset + i]);
            }
            long actual = BitUtil.pop_andnot(A, B, offset, len);
            assertEquals(expected, actual, "Failed pop_andnot for length: " + len);
        }
    }

    @Test
    public void popXor()
    {
        Random rnd = new Random(24680);
        for (int len = 0; len <= 25; len++)
        {
            long[] A = new long[len + 4];
            long[] B = new long[len + 4];
            int offset = 2;
            long expected = 0;
            for (int i = 0; i < len; i++)
            {
                A[offset + i] = rnd.nextLong();
                B[offset + i] = rnd.nextLong();
                expected += Long.bitCount(A[offset + i] ^ B[offset + i]);
            }
            long actual = BitUtil.pop_xor(A, B, offset, len);
            assertEquals(expected, actual, "Failed pop_xor for length: " + len);
        }
    }

    @Test
    public void ntzLong()
    {
        assertEquals(8, BitUtil.ntzTable[0]);
        assertEquals(0, BitUtil.ntzTable[1]);

        assertEquals(0, BitUtil.ntz(1L));
        assertEquals(1, BitUtil.ntz(2L));
        assertEquals(2, BitUtil.ntz(4L));
        assertEquals(63, BitUtil.ntz(1L << 63));
        assertEquals(0, BitUtil.ntz(-1L));

        // Test every single bit position
        for (int i = 0; i < 64; i++)
        {
            long val = 1L << i;
            assertEquals(i, BitUtil.ntz(val), "Failed for bit position: " + i);
            if (i < 63)
            {
                assertEquals(i, BitUtil.ntz(val | (0xFFL << (i + 1))), "Failed with higher bits set: " + i);
            }
        }

        // Test with different byte positions for lower and upper words
        assertEquals(8, BitUtil.ntz(0x0000000000000100L));
        assertEquals(16, BitUtil.ntz(0x0000000000010000L));
        assertEquals(24, BitUtil.ntz(0x0000000001000000L));
        assertEquals(32, BitUtil.ntz(0x0000000100000000L));
        assertEquals(40, BitUtil.ntz(0x0000010000000000L));
        assertEquals(48, BitUtil.ntz(0x0001000000000000L));
        assertEquals(56, BitUtil.ntz(0x0100000000000000L));
    }

    @Test
    public void ntzInt()
    {
        assertEquals(0, BitUtil.ntz(1));
        assertEquals(1, BitUtil.ntz(2));
        assertEquals(2, BitUtil.ntz(4));
        assertEquals(31, BitUtil.ntz(1 << 31));
        assertEquals(0, BitUtil.ntz(-1));

        for (int i = 0; i < 32; i++)
        {
            int val = 1 << i;
            assertEquals(i, BitUtil.ntz(val), "Failed for int bit: " + i);
        }

        assertEquals(8, BitUtil.ntz(0x00000100));
        assertEquals(16, BitUtil.ntz(0x00010000));
        assertEquals(24, BitUtil.ntz(0x01000000));
    }

    @Test
    public void ntz2AndNtz3()
    {
        for (int i = 0; i < 64; i++)
        {
            long val = 1L << i;
            assertEquals(i, BitUtil.ntz2(val), "ntz2 failed for bit: " + i);
            assertEquals(i, BitUtil.ntz3(val), "ntz3 failed for bit: " + i);
        }

        Random rnd = new Random(999);
        for (int i = 0; i < 1000; i++)
        {
            long val = rnd.nextLong();
            if (val == 0) continue;
            int expected = Long.numberOfTrailingZeros(val);
            assertEquals(expected, BitUtil.ntz2(val));
            assertEquals(expected, BitUtil.ntz3(val));
        }
    }

    @Test
    public void nlz()
    {
        assertEquals(64, BitUtil.nlz(0L));
        assertEquals(63, BitUtil.nlz(1L));
        assertEquals(0, BitUtil.nlz(1L << 63));
        assertEquals(0, BitUtil.nlz(-1L));

        for (int i = 0; i < 64; i++)
        {
            long val = 1L << i;
            int expected = 63 - i;
            assertEquals(expected, BitUtil.nlz(val), "nlz failed for bit: " + i);
        }

        Random rnd = new Random(777);
        for (int i = 0; i < 1000; i++)
        {
            long val = rnd.nextLong();
            if (val == 0) continue;
            int expected = Long.numberOfLeadingZeros(val);
            assertEquals(expected, BitUtil.nlz(val));
        }
    }

    @Test
    public void isPowerOfTwo()
    {
        assertTrue(BitUtil.isPowerOfTwo(0));
        assertTrue(BitUtil.isPowerOfTwo(1));
        assertTrue(BitUtil.isPowerOfTwo(2));
        assertTrue(BitUtil.isPowerOfTwo(4));
        assertTrue(BitUtil.isPowerOfTwo(1 << 30));
        assertFalse(BitUtil.isPowerOfTwo(3));
        assertFalse(BitUtil.isPowerOfTwo(5));
        assertFalse(BitUtil.isPowerOfTwo(6));
        assertFalse(BitUtil.isPowerOfTwo(100));

        assertTrue(BitUtil.isPowerOfTwo(0L));
        assertTrue(BitUtil.isPowerOfTwo(1L));
        assertTrue(BitUtil.isPowerOfTwo(2L));
        assertTrue(BitUtil.isPowerOfTwo(4L));
        assertTrue(BitUtil.isPowerOfTwo(1L << 62));
        assertFalse(BitUtil.isPowerOfTwo(3L));
        assertFalse(BitUtil.isPowerOfTwo(100L));
    }

    @Test
    public void nextHighestPowerOfTwoInt()
    {
        assertEquals(0, BitUtil.nextHighestPowerOfTwo(0));
        assertEquals(1, BitUtil.nextHighestPowerOfTwo(1));
        assertEquals(2, BitUtil.nextHighestPowerOfTwo(2));
        assertEquals(4, BitUtil.nextHighestPowerOfTwo(3));
        assertEquals(4, BitUtil.nextHighestPowerOfTwo(4));
        assertEquals(8, BitUtil.nextHighestPowerOfTwo(5));
        assertEquals(8, BitUtil.nextHighestPowerOfTwo(7));
        assertEquals(8, BitUtil.nextHighestPowerOfTwo(8));
        assertEquals(1024, BitUtil.nextHighestPowerOfTwo(1000));
        assertEquals(1024, BitUtil.nextHighestPowerOfTwo(1024));
        assertEquals(2048, BitUtil.nextHighestPowerOfTwo(1025));
        assertEquals(1 << 30, BitUtil.nextHighestPowerOfTwo((1 << 30) - 1));
        assertEquals(1 << 30, BitUtil.nextHighestPowerOfTwo(1 << 30));
    }

    @Test
    public void nextHighestPowerOfTwoLong()
    {
        assertEquals(0L, BitUtil.nextHighestPowerOfTwo(0L));
        assertEquals(1L, BitUtil.nextHighestPowerOfTwo(1L));
        assertEquals(2L, BitUtil.nextHighestPowerOfTwo(2L));
        assertEquals(4L, BitUtil.nextHighestPowerOfTwo(3L));
        assertEquals(4L, BitUtil.nextHighestPowerOfTwo(4L));
        assertEquals(8L, BitUtil.nextHighestPowerOfTwo(5L));
        assertEquals(1024L, BitUtil.nextHighestPowerOfTwo(1000L));
        assertEquals(1L << 40, BitUtil.nextHighestPowerOfTwo((1L << 40) - 1));
        assertEquals(1L << 40, BitUtil.nextHighestPowerOfTwo(1L << 40));
        assertEquals(1L << 62, BitUtil.nextHighestPowerOfTwo((1L << 62) - 1));
        assertEquals(1L << 62, BitUtil.nextHighestPowerOfTwo(1L << 62));
    }
}
