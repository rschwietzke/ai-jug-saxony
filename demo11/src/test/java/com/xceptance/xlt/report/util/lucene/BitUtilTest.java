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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BitUtil Tests")
class BitUtilTest
{
    @Test
    @DisplayName("Private constructor can be invoked via reflection for coverage")
    void privateConstructor() throws Exception
    {
        Constructor<BitUtil> constructor = BitUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        BitUtil instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @Nested
    @DisplayName("Single Word Popcount (pop)")
    class PopTest
    {
        @Test
        @DisplayName("Counts bits in special constant values")
        void specialConstants()
        {
            assertThat(BitUtil.pop(0L)).isEqualTo(0);
            assertThat(BitUtil.pop(1L)).isEqualTo(1);
            assertThat(BitUtil.pop(-1L)).isEqualTo(64);
            assertThat(BitUtil.pop(0x5555555555555555L)).isEqualTo(32);
            assertThat(BitUtil.pop(0xAAAAAAAAAAAAAAAAL)).isEqualTo(32);
            assertThat(BitUtil.pop(0x0F0F0F0F0F0F0F0FL)).isEqualTo(32);
            assertThat(BitUtil.pop(0x3333333333333333L)).isEqualTo(32);
            assertThat(BitUtil.pop(Long.MIN_VALUE)).isEqualTo(1);
            assertThat(BitUtil.pop(Long.MAX_VALUE)).isEqualTo(63);
        }

        @Test
        @DisplayName("Matches Long.bitCount for all single-bit masks")
        void singleBitMasks()
        {
            for (int i = 0; i < 64; i++)
            {
                long val = 1L << i;
                assertThat(BitUtil.pop(val)).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("Matches Long.bitCount across random 64-bit values")
        void randomValues()
        {
            Random rng = new Random(42);
            for (int i = 0; i < 10_000; i++)
            {
                long val = rng.nextLong();
                assertThat(BitUtil.pop(val)).isEqualTo(Long.bitCount(val));
            }
        }
    }

    @Nested
    @DisplayName("Array Popcount (pop_array)")
    class PopArrayTest
    {
        @Test
        @DisplayName("Handles zero words")
        void zeroWords()
        {
            long[] array = new long[]{1L, 2L, 3L};
            assertThat(BitUtil.pop_array(array, 0, 0)).isEqualTo(0L);
            assertThat(BitUtil.pop_array(array, 1, 0)).isEqualTo(0L);
        }

        @Test
        @DisplayName("Correctly handles different word count thresholds (1 to 35 words) with dirty padding")
        void varyingLengths()
        {
            Random rng = new Random(1234);
            for (int offset = 0; offset <= 5; offset++)
            {
                for (int len = 0; len <= 35; len++)
                {
                    long[] array = new long[offset + len + 10];
                    for (int k = 0; k < array.length; k++)
                    {
                        array[k] = rng.nextLong() | 1L;
                    }
                    long expectedCount = 0;
                    for (int i = 0; i < len; i++)
                    {
                        expectedCount += Long.bitCount(array[offset + i]);
                    }

                    assertThat(BitUtil.pop_array(array, offset, len))
                            .as("Mismatch for offset " + offset + ", length " + len)
                            .isEqualTo(expectedCount);
                }
            }
        }

        @Test
        @DisplayName("Correctly counts all ones and all zeros in large array")
        void allOnesAndZeros()
        {
            long[] allOnes = new long[64];
            java.util.Arrays.fill(allOnes, -1L);
            assertThat(BitUtil.pop_array(allOnes, 0, 64)).isEqualTo(64L * 64L);

            long[] allZeros = new long[64];
            assertThat(BitUtil.pop_array(allZeros, 0, 64)).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Set Operations Popcount (pop_intersect, pop_union, pop_andnot, pop_xor)")
    class SetOperationsTest
    {
        @Test
        @DisplayName("Handles zero words with non-zero buffer contents")
        void zeroWords()
        {
            long[] a = new long[]{12345L, 67890L, -1L};
            long[] b = new long[]{54321L, 98760L, -1L};
            assertThat(BitUtil.pop_intersect(a, b, 0, 0)).isEqualTo(0L);
            assertThat(BitUtil.pop_intersect(a, b, 1, 0)).isEqualTo(0L);
            assertThat(BitUtil.pop_union(a, b, 0, 0)).isEqualTo(0L);
            assertThat(BitUtil.pop_union(a, b, 1, 0)).isEqualTo(0L);
            assertThat(BitUtil.pop_andnot(a, b, 0, 0)).isEqualTo(0L);
            assertThat(BitUtil.pop_andnot(a, b, 1, 0)).isEqualTo(0L);
            assertThat(BitUtil.pop_xor(a, b, 0, 0)).isEqualTo(0L);
            assertThat(BitUtil.pop_xor(a, b, 1, 0)).isEqualTo(0L);
        }

        @Test
        @DisplayName("pop_intersect matches (A[i] & B[i]) across array lengths and offsets with dirty padding")
        void popIntersect()
        {
            Random rng = new Random(5678);
            for (int offset = 0; offset <= 5; offset++)
            {
                for (int len = 0; len <= 40; len++)
                {
                    long[] a = new long[offset + len + 10];
                    long[] b = new long[offset + len + 10];
                    // Fill entire arrays with non-zero noise
                    for (int k = 0; k < a.length; k++)
                    {
                        a[k] = rng.nextLong() | 1L;
                        b[k] = rng.nextLong() | 1L;
                    }

                    long expected = 0;
                    for (int i = 0; i < len; i++)
                    {
                        expected += Long.bitCount(a[offset + i] & b[offset + i]);
                    }
                    assertThat(BitUtil.pop_intersect(a, b, offset, len))
                            .as("Intersection mismatch at offset " + offset + ", len " + len)
                            .isEqualTo(expected);
                }
            }
        }

        @Test
        @DisplayName("pop_union matches (A[i] | B[i]) across array lengths and offsets with dirty padding")
        void popUnion()
        {
            Random rng = new Random(9012);
            for (int offset = 0; offset <= 5; offset++)
            {
                for (int len = 0; len <= 40; len++)
                {
                    long[] a = new long[offset + len + 10];
                    long[] b = new long[offset + len + 10];
                    for (int k = 0; k < a.length; k++)
                    {
                        a[k] = rng.nextLong() | 1L;
                        b[k] = rng.nextLong() | 1L;
                    }

                    long expected = 0;
                    for (int i = 0; i < len; i++)
                    {
                        expected += Long.bitCount(a[offset + i] | b[offset + i]);
                    }
                    assertThat(BitUtil.pop_union(a, b, offset, len))
                            .as("Union mismatch at offset " + offset + ", len " + len)
                            .isEqualTo(expected);
                }
            }
        }

        @Test
        @DisplayName("pop_andnot matches (A[i] & ~B[i]) across array lengths and offsets with dirty padding")
        void popAndNot()
        {
            Random rng = new Random(3456);
            for (int offset = 0; offset <= 5; offset++)
            {
                for (int len = 0; len <= 40; len++)
                {
                    long[] a = new long[offset + len + 10];
                    long[] b = new long[offset + len + 10];
                    for (int k = 0; k < a.length; k++)
                    {
                        a[k] = rng.nextLong() | 1L;
                        b[k] = rng.nextLong() | 1L;
                    }

                    long expected = 0;
                    for (int i = 0; i < len; i++)
                    {
                        expected += Long.bitCount(a[offset + i] & ~b[offset + i]);
                    }
                    assertThat(BitUtil.pop_andnot(a, b, offset, len))
                            .as("AndNot mismatch at offset " + offset + ", len " + len)
                            .isEqualTo(expected);
                }
            }
        }

        @Test
        @DisplayName("pop_xor matches (A[i] ^ B[i]) across array lengths and offsets with dirty padding")
        void popXor()
        {
            Random rng = new Random(7890);
            for (int offset = 0; offset <= 5; offset++)
            {
                for (int len = 0; len <= 40; len++)
                {
                    long[] a = new long[offset + len + 10];
                    long[] b = new long[offset + len + 10];
                    for (int k = 0; k < a.length; k++)
                    {
                        a[k] = rng.nextLong() | 1L;
                        b[k] = rng.nextLong() | 1L;
                    }

                    long expected = 0;
                    for (int i = 0; i < len; i++)
                    {
                        expected += Long.bitCount(a[offset + i] ^ b[offset + i]);
                    }
                    assertThat(BitUtil.pop_xor(a, b, offset, len))
                            .as("XOR mismatch at offset " + offset + ", len " + len)
                            .isEqualTo(expected);
                }
            }
        }
    }

    @Nested
    @DisplayName("Number of Trailing Zeros (ntz, ntz2, ntz3)")
    class NumberOfTrailingZerosTest
    {
        @Test
        @DisplayName("ntz(long) matches Long.numberOfTrailingZeros")
        void ntzLong()
        {
            assertThat(BitUtil.ntz(0L)).isEqualTo(64);

            for (int i = 0; i < 64; i++)
            {
                long val = 1L << i;
                assertThat(BitUtil.ntz(val)).isEqualTo(i);
            }

            Random rng = new Random(42);
            for (int i = 0; i < 10_000; i++)
            {
                long val = rng.nextLong();
                assertThat(BitUtil.ntz(val)).isEqualTo(Long.numberOfTrailingZeros(val));
            }
        }

        @Test
        @DisplayName("ntz(int) matches Integer.numberOfTrailingZeros")
        void ntzInt()
        {
            assertThat(BitUtil.ntz(0)).isEqualTo(32);

            for (int i = 0; i < 32; i++)
            {
                int val = 1 << i;
                assertThat(BitUtil.ntz(val)).isEqualTo(i);
            }

            Random rng = new Random(84);
            for (int i = 0; i < 10_000; i++)
            {
                int val = rng.nextInt();
                assertThat(BitUtil.ntz(val)).isEqualTo(Integer.numberOfTrailingZeros(val));
            }
        }

        @Test
        @DisplayName("ntz2(long) matches Long.numberOfTrailingZeros for non-zero values")
        void ntz2Long()
        {
            for (int i = 0; i < 64; i++)
            {
                long val = 1L << i;
                assertThat(BitUtil.ntz2(val)).isEqualTo(i);
            }

            Random rng = new Random(168);
            for (int i = 0; i < 10_000; i++)
            {
                long val = rng.nextLong();
                if (val == 0) continue;
                assertThat(BitUtil.ntz2(val)).isEqualTo(Long.numberOfTrailingZeros(val));
            }
        }

        @Test
        @DisplayName("ntz3(long) matches Long.numberOfTrailingZeros for non-zero values")
        void ntz3Long()
        {
            for (int i = 0; i < 64; i++)
            {
                long val = 1L << i;
                assertThat(BitUtil.ntz3(val)).isEqualTo(i);
            }

            Random rng = new Random(336);
            for (int i = 0; i < 10_000; i++)
            {
                long val = rng.nextLong();
                if (val == 0) continue;
                assertThat(BitUtil.ntz3(val)).isEqualTo(Long.numberOfTrailingZeros(val));
            }
        }
    }

    @Nested
    @DisplayName("Number of Leading Zeros (nlz)")
    class NumberOfLeadingZerosTest
    {
        @Test
        @DisplayName("nlz(long) matches Long.numberOfLeadingZeros")
        void nlzLong()
        {
            assertThat(BitUtil.nlz(0L)).isEqualTo(64);

            for (int i = 0; i < 64; i++)
            {
                long val = 1L << i;
                assertThat(BitUtil.nlz(val)).isEqualTo(63 - i);
            }

            Random rng = new Random(999);
            for (int i = 0; i < 10_000; i++)
            {
                long val = rng.nextLong();
                assertThat(BitUtil.nlz(val)).isEqualTo(Long.numberOfLeadingZeros(val));
            }
        }
    }

    @Nested
    @DisplayName("Power of Two Functions")
    class PowerOfTwoTest
    {
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 65536, 1 << 30})
        @DisplayName("isPowerOfTwo(int) returns true for powers of two and zero")
        void isPowerOfTwoIntPositive(int v)
        {
            assertThat(BitUtil.isPowerOfTwo(v)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {3, 5, 6, 7, 9, 10, 15, 100, 1000, Integer.MAX_VALUE})
        @DisplayName("isPowerOfTwo(int) returns false for non-powers of two")
        void isPowerOfTwoIntNegative(int v)
        {
            assertThat(BitUtil.isPowerOfTwo(v)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, 1L, 2L, 4L, 8L, 16L, 1024L, 1L << 32, 1L << 62})
        @DisplayName("isPowerOfTwo(long) returns true for powers of two and zero")
        void isPowerOfTwoLongPositive(long v)
        {
            assertThat(BitUtil.isPowerOfTwo(v)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(longs = {3L, 5L, 6L, 7L, 9L, 1000L, Long.MAX_VALUE})
        @DisplayName("isPowerOfTwo(long) returns false for non-powers of two")
        void isPowerOfTwoLongNegative(long v)
        {
            assertThat(BitUtil.isPowerOfTwo(v)).isFalse();
        }

        @Test
        @DisplayName("nextHighestPowerOfTwo(int) calculates next power of two")
        void nextHighestPowerOfTwoInt()
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
        @DisplayName("nextHighestPowerOfTwo(long) calculates next power of two")
        void nextHighestPowerOfTwoLong()
        {
            assertThat(BitUtil.nextHighestPowerOfTwo(0L)).isEqualTo(0L);
            assertThat(BitUtil.nextHighestPowerOfTwo(1L)).isEqualTo(1L);
            assertThat(BitUtil.nextHighestPowerOfTwo(2L)).isEqualTo(2L);
            assertThat(BitUtil.nextHighestPowerOfTwo(3L)).isEqualTo(4L);
            assertThat(BitUtil.nextHighestPowerOfTwo(4L)).isEqualTo(4L);
            assertThat(BitUtil.nextHighestPowerOfTwo(5L)).isEqualTo(8L);
            assertThat(BitUtil.nextHighestPowerOfTwo(1000L)).isEqualTo(1024L);
            assertThat(BitUtil.nextHighestPowerOfTwo(1L << 40)).isEqualTo(1L << 40);
            assertThat(BitUtil.nextHighestPowerOfTwo((1L << 40) + 1)).isEqualTo(1L << 41);
        }
    }
}
