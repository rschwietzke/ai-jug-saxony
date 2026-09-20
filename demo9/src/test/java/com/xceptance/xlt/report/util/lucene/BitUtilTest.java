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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class BitUtilTest
{
    @Nested
    @DisplayName("popcount tests")
    class PopCountTests
    {
        @Test
        @DisplayName("pop single long boundary and known values")
        void testPopKnownValues()
        {
            assertThat(BitUtil.pop(0L)).isEqualTo(0);
            assertThat(BitUtil.pop(1L)).isEqualTo(1);
            assertThat(BitUtil.pop(-1L)).isEqualTo(64);
            assertThat(BitUtil.pop(Long.MAX_VALUE)).isEqualTo(63);
            assertThat(BitUtil.pop(Long.MIN_VALUE)).isEqualTo(1);
            assertThat(BitUtil.pop(0x5555555555555555L)).isEqualTo(32);
            assertThat(BitUtil.pop(0xAAAAAAAAAAAAAAAAL)).isEqualTo(32);
            assertThat(BitUtil.pop(0x0F0F0F0F0F0F0F0FL)).isEqualTo(32);
        }

        @Test
        @DisplayName("pop matches Long.bitCount for single bit positions and random values")
        void testPopMatchesJdk()
        {
            for (int i = 0; i < 64; i++)
            {
                long val = 1L << i;
                assertThat(BitUtil.pop(val)).isEqualTo(Long.bitCount(val));
                assertThat(BitUtil.pop(~val)).isEqualTo(Long.bitCount(~val));
            }

            Random rng = new Random(42);
            for (int i = 0; i < 1000; i++)
            {
                long val = rng.nextLong();
                assertThat(BitUtil.pop(val)).isEqualTo(Long.bitCount(val));
            }
        }

        @Test
        @DisplayName("pop_array with varying sizes and offsets")
        void testPopArray()
        {
            Random rng = new Random(123);
            int[] testSizes = {0, 1, 2, 3, 4, 5, 7, 8, 9, 15, 16, 23, 25, 32, 64};

            for (int size : testSizes)
            {
                long[] array = new long[size + 10];
                for (int i = 0; i < array.length; i++)
                {
                    array[i] = rng.nextLong();
                }

                for (int offset : new int[]{0, 1, 3})
                {
                    if (offset + size <= array.length)
                    {
                        long expected = 0;
                        for (int i = offset; i < offset + size; i++)
                        {
                            expected += Long.bitCount(array[i]);
                        }
                        assertThat(BitUtil.pop_array(array, offset, size)).isEqualTo(expected);
                    }
                }
            }
        }

        @Test
        @DisplayName("pop_intersect matches element-wise bitwise AND count")
        void testPopIntersect()
        {
            Random rng = new Random(456);
            int[] testSizes = {0, 1, 3, 7, 8, 9, 15, 16, 20};

            for (int size : testSizes)
            {
                long[] a = new long[size + 5];
                long[] b = new long[size + 5];
                for (int i = 0; i < a.length; i++)
                {
                    a[i] = rng.nextLong();
                    b[i] = rng.nextLong();
                }

                for (int offset : new int[]{0, 2})
                {
                    long expected = 0;
                    for (int i = offset; i < offset + size; i++)
                    {
                        expected += Long.bitCount(a[i] & b[i]);
                    }
                    assertThat(BitUtil.pop_intersect(a, b, offset, size)).isEqualTo(expected);
                }
            }
        }

        @Test
        @DisplayName("pop_union matches element-wise bitwise OR count")
        void testPopUnion()
        {
            Random rng = new Random(789);
            int[] testSizes = {0, 1, 3, 7, 8, 9, 15, 16, 20};

            for (int size : testSizes)
            {
                long[] a = new long[size + 5];
                long[] b = new long[size + 5];
                for (int i = 0; i < a.length; i++)
                {
                    a[i] = rng.nextLong();
                    b[i] = rng.nextLong();
                }

                for (int offset : new int[]{0, 2})
                {
                    long expected = 0;
                    for (int i = offset; i < offset + size; i++)
                    {
                        expected += Long.bitCount(a[i] | b[i]);
                    }
                    assertThat(BitUtil.pop_union(a, b, offset, size)).isEqualTo(expected);
                }
            }
        }

        @Test
        @DisplayName("pop_andnot matches element-wise bitwise AND NOT count")
        void testPopAndNot()
        {
            Random rng = new Random(101);
            int[] testSizes = {0, 1, 3, 7, 8, 9, 15, 16, 20};

            for (int size : testSizes)
            {
                long[] a = new long[size + 5];
                long[] b = new long[size + 5];
                for (int i = 0; i < a.length; i++)
                {
                    a[i] = rng.nextLong();
                    b[i] = rng.nextLong();
                }

                for (int offset : new int[]{0, 2})
                {
                    long expected = 0;
                    for (int i = offset; i < offset + size; i++)
                    {
                        expected += Long.bitCount(a[i] & ~b[i]);
                    }
                    assertThat(BitUtil.pop_andnot(a, b, offset, size)).isEqualTo(expected);
                }
            }
        }

        @Test
        @DisplayName("pop_xor matches element-wise bitwise XOR count")
        void testPopXor()
        {
            Random rng = new Random(202);
            int[] testSizes = {0, 1, 3, 7, 8, 9, 15, 16, 20};

            for (int size : testSizes)
            {
                long[] a = new long[size + 5];
                long[] b = new long[size + 5];
                for (int i = 0; i < a.length; i++)
                {
                    a[i] = rng.nextLong();
                    b[i] = rng.nextLong();
                }

                for (int offset : new int[]{0, 2})
                {
                    long expected = 0;
                    for (int i = offset; i < offset + size; i++)
                    {
                        expected += Long.bitCount(a[i] ^ b[i]);
                    }
                    assertThat(BitUtil.pop_xor(a, b, offset, size)).isEqualTo(expected);
                }
            }
        }
    }

    @Nested
    @DisplayName("ntz (Number of Trailing Zeros) tests")
    class TrailingZerosTests
    {
        @Test
        @DisplayName("ntzTable correctness against byte trailing zeros")
        void testNtzTable()
        {
            assertThat(BitUtil.ntzTable[0]).isEqualTo((byte) 8);
            for (int i = 1; i < 256; i++)
            {
                assertThat(BitUtil.ntzTable[i]).isEqualTo((byte) Integer.numberOfTrailingZeros(i));
            }
        }

        @Test
        @DisplayName("ntz(long) boundary and bit positions")
        void testNtzLong()
        {
            assertThat(BitUtil.ntz(0L)).isEqualTo(64);

            for (int i = 0; i < 64; i++)
            {
                long val = 1L << i;
                assertThat(BitUtil.ntz(val)).isEqualTo(i);
                // Combine with higher bits set
                long highMask = (i < 63) ? ~((1L << (i + 1)) - 1) : 0L;
                assertThat(BitUtil.ntz(val | highMask)).isEqualTo(i);
            }

            Random rng = new Random(303);
            for (int i = 0; i < 1000; i++)
            {
                long val = rng.nextLong();
                if (val != 0)
                {
                    assertThat(BitUtil.ntz(val)).isEqualTo(Long.numberOfTrailingZeros(val));
                }
            }
        }

        @Test
        @DisplayName("ntz(int) boundary and bit positions")
        void testNtzInt()
        {
            assertThat(BitUtil.ntz(0)).isEqualTo(32);

            for (int i = 0; i < 32; i++)
            {
                int val = 1 << i;
                assertThat(BitUtil.ntz(val)).isEqualTo(i);
                int highMask = (i < 31) ? ~((1 << (i + 1)) - 1) : 0;
                assertThat(BitUtil.ntz(val | highMask)).isEqualTo(i);
            }

            Random rng = new Random(404);
            for (int i = 0; i < 1000; i++)
            {
                int val = rng.nextInt();
                if (val != 0)
                {
                    assertThat(BitUtil.ntz(val)).isEqualTo(Integer.numberOfTrailingZeros(val));
                }
            }
        }

        @Test
        @DisplayName("ntz2(long) and ntz3(long) for non-zero values")
        void testNtz2AndNtz3()
        {
            for (int i = 0; i < 64; i++)
            {
                long val = 1L << i;
                assertThat(BitUtil.ntz2(val)).isEqualTo(i);
                assertThat(BitUtil.ntz3(val)).isEqualTo(i);
            }

            Random rng = new Random(505);
            for (int i = 0; i < 1000; i++)
            {
                long val = rng.nextLong();
                if (val != 0)
                {
                    assertThat(BitUtil.ntz2(val)).isEqualTo(Long.numberOfTrailingZeros(val));
                    assertThat(BitUtil.ntz3(val)).isEqualTo(Long.numberOfTrailingZeros(val));
                }
            }
        }
    }

    @Nested
    @DisplayName("nlz (Number of Leading Zeros) tests")
    class LeadingZerosTests
    {
        @Test
        @DisplayName("nlzTable correctness")
        void testNlzTable()
        {
            for (int i = 0; i < 256; i++)
            {
                int expected;
                if (i == 0)
                {
                    expected = 8;
                }
                else
                {
                    expected = Integer.numberOfLeadingZeros(i) - 24;
                }
                assertThat(BitUtil.nlzTable[i]).isEqualTo((byte) expected);
            }
        }

        @Test
        @DisplayName("nlz(long) boundary and bit positions")
        void testNlzLong()
        {
            assertThat(BitUtil.nlz(0L)).isEqualTo(64);

            for (int i = 0; i < 64; i++)
            {
                long val = 1L << i;
                assertThat(BitUtil.nlz(val)).isEqualTo(63 - i);
            }

            Random rng = new Random(606);
            for (int i = 0; i < 1000; i++)
            {
                long val = rng.nextLong();
                assertThat(BitUtil.nlz(val)).isEqualTo(Long.numberOfLeadingZeros(val));
            }
        }
    }

    @Nested
    @DisplayName("isPowerOfTwo tests")
    class PowerOfTwoTests
    {
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 1024, 65536, 1 << 30, Integer.MIN_VALUE})
        void testIsPowerOfTwoIntTrue(int val)
        {
            assertThat(BitUtil.isPowerOfTwo(val)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {3, 5, 6, 7, 9, 10, 15, 100, 3600, -1, -2, -4, -3})
        void testIsPowerOfTwoIntFalse(int val)
        {
            assertThat(BitUtil.isPowerOfTwo(val)).isFalse();
        }

        @Test
        @DisplayName("isPowerOfTwo(long) boundaries")
        void testIsPowerOfTwoLong()
        {
            assertThat(BitUtil.isPowerOfTwo(0L)).isTrue();
            assertThat(BitUtil.isPowerOfTwo(1L)).isTrue();
            assertThat(BitUtil.isPowerOfTwo(1L << 62)).isTrue();
            assertThat(BitUtil.isPowerOfTwo(Long.MIN_VALUE)).isTrue();

            assertThat(BitUtil.isPowerOfTwo(3L)).isFalse();
            assertThat(BitUtil.isPowerOfTwo(Long.MAX_VALUE)).isFalse();
            assertThat(BitUtil.isPowerOfTwo(-1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("nextHighestPowerOfTwo tests")
    class NextHighestPowerOfTwoTests
    {
        @Test
        @DisplayName("nextHighestPowerOfTwo(int) edge cases and exact powers")
        void testNextHighestPowerOfTwoInt()
        {
            assertThat(BitUtil.nextHighestPowerOfTwo(0)).isEqualTo(0);
            assertThat(BitUtil.nextHighestPowerOfTwo(1)).isEqualTo(1);
            assertThat(BitUtil.nextHighestPowerOfTwo(2)).isEqualTo(2);
            assertThat(BitUtil.nextHighestPowerOfTwo(3)).isEqualTo(4);
            assertThat(BitUtil.nextHighestPowerOfTwo(4)).isEqualTo(4);
            assertThat(BitUtil.nextHighestPowerOfTwo(5)).isEqualTo(8);
            assertThat(BitUtil.nextHighestPowerOfTwo(7)).isEqualTo(8);
            assertThat(BitUtil.nextHighestPowerOfTwo(8)).isEqualTo(8);
            assertThat(BitUtil.nextHighestPowerOfTwo(9)).isEqualTo(16);
            assertThat(BitUtil.nextHighestPowerOfTwo(1000)).isEqualTo(1024);
            assertThat(BitUtil.nextHighestPowerOfTwo(3600)).isEqualTo(4096);
            assertThat(BitUtil.nextHighestPowerOfTwo(1 << 29)).isEqualTo(1 << 29);
            assertThat(BitUtil.nextHighestPowerOfTwo((1 << 29) + 1)).isEqualTo(1 << 30);
        }

        @Test
        @DisplayName("nextHighestPowerOfTwo(long) edge cases and exact powers")
        void testNextHighestPowerOfTwoLong()
        {
            assertThat(BitUtil.nextHighestPowerOfTwo(0L)).isEqualTo(0L);
            assertThat(BitUtil.nextHighestPowerOfTwo(1L)).isEqualTo(1L);
            assertThat(BitUtil.nextHighestPowerOfTwo(2L)).isEqualTo(2L);
            assertThat(BitUtil.nextHighestPowerOfTwo(3L)).isEqualTo(4L);
            assertThat(BitUtil.nextHighestPowerOfTwo(4L)).isEqualTo(4L);
            assertThat(BitUtil.nextHighestPowerOfTwo(5L)).isEqualTo(8L);
            assertThat(BitUtil.nextHighestPowerOfTwo(3600L)).isEqualTo(4096L);
            assertThat(BitUtil.nextHighestPowerOfTwo(1L << 40)).isEqualTo(1L << 40);
            assertThat(BitUtil.nextHighestPowerOfTwo((1L << 40) + 1)).isEqualTo(1L << 41);
        }
    }
}
