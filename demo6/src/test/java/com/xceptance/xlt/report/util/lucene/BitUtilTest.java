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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The test suite for {@link BitUtil}.
 *
 * <p>This is imported Apache Lucene/Solr code, so the interesting question is not "what should it
 * do" but "does this copy still do what the JDK intrinsics do". Almost every test here is therefore
 * a differential test against the JDK reference ({@link Long#bitCount},
 * {@link Long#numberOfTrailingZeros}, {@link Long#numberOfLeadingZeros}) plus a handful of pinned
 * corner cases the JDK and this class disagree on.
 *
 * <p>The {@code pop_*} family carries the bulk of the risk. Those methods process eight words per
 * iteration with a carry-save-adder network and then handle the remaining words in a 4/2/1 cascade,
 * so the word count alone decides which code path runs. The tests below sweep the word count from 0
 * to 40 to walk through every combination of full blocks and tail branches instead of testing one
 * arbitrary array.
 *
 * <p>Only {@link BitUtil#nextHighestPowerOfTwo(int)} is actually used by the production code in this
 * module ({@code RuntimeHistogram} and {@code IntTimeSeries}), and its behaviour for 0, for negative
 * input and above 2^30 is pinned here because both callers depend on it.
 */
class BitUtilTest
{
    /**
     * A fixed seed keeps the randomized differential tests reproducible.
     */
    private static final long SEED = 20260906L;

    /**
     * Reference implementation for the pop_* family: count the bits of the words in range after
     * applying the given combiner to the two arrays.
     */
    private static long reference(final long[] a, final long[] b, final int wordOffset, final int numWords,
                                  final java.util.function.LongBinaryOperator op)
    {
        long sum = 0;
        for (int i = wordOffset; i < wordOffset + numWords; i++)
        {
            sum += Long.bitCount(op.applyAsLong(a[i], b == null ? 0L : b[i]));
        }

        return sum;
    }

    /**
     * Builds an array of pseudo random words. Every fourth word is forced to an extreme value so
     * the carry-save-adder chain sees all-zero and all-one words too, not only dense random noise.
     */
    private static long[] randomWords(final Random random, final int length)
    {
        final long[] words = new long[length];
        for (int i = 0; i < length; i++)
        {
            words[i] = switch (i % 4)
            {
                case 0 -> 0L;
                case 1 -> -1L;
                default -> random.nextLong();
            };
        }

        return words;
    }

    @Nested
    @DisplayName("A. pop - the single word population count")
    class Pop
    {
        @Test
        @DisplayName("T01 the fixed points 0, 1 and -1 are counted correctly")
        void fixedPoints()
        {
            assertEquals(0, BitUtil.pop(0L));
            assertEquals(1, BitUtil.pop(1L));
            assertEquals(64, BitUtil.pop(-1L));
            assertEquals(1, BitUtil.pop(Long.MIN_VALUE), "the sign bit must be counted like any other bit");
            assertEquals(63, BitUtil.pop(Long.MAX_VALUE));
        }

        @Test
        @DisplayName("T02 every single bit position is counted as exactly one")
        void everySingleBit()
        {
            for (int bit = 0; bit < 64; bit++)
            {
                assertEquals(1, BitUtil.pop(1L << bit), "bit " + bit + " was not counted");
            }
        }

        @Test
        @DisplayName("T03 pop agrees with Long.bitCount for random words")
        void agreesWithJdk()
        {
            final Random random = new Random(SEED);
            for (int i = 0; i < 10_000; i++)
            {
                final long value = random.nextLong();
                assertEquals(Long.bitCount(value), BitUtil.pop(value), "mismatch for " + value);
            }
        }
    }

    @Nested
    @DisplayName("B. pop_array and the set combining variants")
    class PopArray
    {
        @Test
        @DisplayName("T04 an empty range counts nothing")
        void emptyRange()
        {
            final long[] words =
            {
                -1L, -1L, -1L
            };
            assertEquals(0, BitUtil.pop_array(words, 0, 0));
            assertEquals(0, BitUtil.pop_array(words, 3, 0), "an offset at the very end is still an empty range");
        }

        /**
         * The word count drives which of the 8/4/2/1 code paths run, so it is swept instead of
         * sampled. 40 words are enough for five full carry-save blocks plus every tail combination.
         */
        @Test
        @DisplayName("T05 pop_array agrees with the reference for 0..40 words")
        void agreesWithReference()
        {
            final Random random = new Random(SEED);
            for (int length = 0; length <= 40; length++)
            {
                final long[] words = randomWords(random, length);
                assertEquals(reference(words, null, 0, length, (x, __) -> x), BitUtil.pop_array(words, 0, length),
                             "mismatch for " + length + " words");
            }
        }

        @Test
        @DisplayName("T06 the word offset skips the leading words")
        void respectsOffset()
        {
            final Random random = new Random(SEED);
            final long[] words = randomWords(random, 32);

            for (int offset = 0; offset <= 16; offset++)
            {
                final int numWords = 32 - offset;
                assertEquals(reference(words, null, offset, numWords, (x, __) -> x), BitUtil.pop_array(words, offset, numWords),
                             "mismatch for offset " + offset);
            }
        }

        @Test
        @DisplayName("T07 pop_intersect counts the bits of a & b")
        void intersect()
        {
            final Random random = new Random(SEED);
            for (int length = 0; length <= 40; length++)
            {
                final long[] a = randomWords(random, length);
                final long[] b = randomWords(random, length);
                assertEquals(reference(a, b, 0, length, (x, y) -> x & y), BitUtil.pop_intersect(a, b, 0, length),
                             "mismatch for " + length + " words");
            }
        }

        @Test
        @DisplayName("T08 pop_union counts the bits of a | b")
        void union()
        {
            final Random random = new Random(SEED);
            for (int length = 0; length <= 40; length++)
            {
                final long[] a = randomWords(random, length);
                final long[] b = randomWords(random, length);
                assertEquals(reference(a, b, 0, length, (x, y) -> x | y), BitUtil.pop_union(a, b, 0, length),
                             "mismatch for " + length + " words");
            }
        }

        @Test
        @DisplayName("T09 pop_andnot counts the bits of a & ~b")
        void andNot()
        {
            final Random random = new Random(SEED);
            for (int length = 0; length <= 40; length++)
            {
                final long[] a = randomWords(random, length);
                final long[] b = randomWords(random, length);
                assertEquals(reference(a, b, 0, length, (x, y) -> x & ~y), BitUtil.pop_andnot(a, b, 0, length),
                             "mismatch for " + length + " words");
            }
        }

        @Test
        @DisplayName("T10 pop_xor counts the bits of a ^ b")
        void xor()
        {
            final Random random = new Random(SEED);
            for (int length = 0; length <= 40; length++)
            {
                final long[] a = randomWords(random, length);
                final long[] b = randomWords(random, length);
                assertEquals(reference(a, b, 0, length, (x, y) -> x ^ y), BitUtil.pop_xor(a, b, 0, length),
                             "mismatch for " + length + " words");
            }
        }

        @Test
        @DisplayName("T11 the combining variants honour the word offset as well")
        void combiningVariantsRespectOffset()
        {
            final Random random = new Random(SEED);
            final long[] a = randomWords(random, 24);
            final long[] b = randomWords(random, 24);

            for (int offset = 0; offset <= 12; offset++)
            {
                final int numWords = 24 - offset;
                assertEquals(reference(a, b, offset, numWords, (x, y) -> x & y), BitUtil.pop_intersect(a, b, offset, numWords));
                assertEquals(reference(a, b, offset, numWords, (x, y) -> x | y), BitUtil.pop_union(a, b, offset, numWords));
                assertEquals(reference(a, b, offset, numWords, (x, y) -> x & ~y), BitUtil.pop_andnot(a, b, offset, numWords));
                assertEquals(reference(a, b, offset, numWords, (x, y) -> x ^ y), BitUtil.pop_xor(a, b, offset, numWords));
            }
        }

        @Test
        @DisplayName("T12 the set identities hold: |a| + |b| = |a & b| + |a | b| and |a ^ b| = |a| + |b| - 2|a & b|")
        void setIdentities()
        {
            final Random random = new Random(SEED);
            final int length = 17;
            final long[] a = randomWords(random, length);
            final long[] b = randomWords(random, length);

            final long popA = BitUtil.pop_array(a, 0, length);
            final long popB = BitUtil.pop_array(b, 0, length);
            final long and = BitUtil.pop_intersect(a, b, 0, length);
            final long or = BitUtil.pop_union(a, b, 0, length);

            assertEquals(popA + popB, and + or);
            assertEquals(popA + popB - 2 * and, BitUtil.pop_xor(a, b, 0, length));
            assertEquals(popA - and, BitUtil.pop_andnot(a, b, 0, length));
        }
            @Test
        @DisplayName("T13 nearly saturated words drive the carry save adder into its eight word carry")
        void saturatedWordsExerciseTheCarryChain()
        {
            // the accumulator only produces an 'eights' carry inside the trailing 4 and 2 word
            // branches when almost every word is saturated. Random data practically never gets
            // there, a run of all-ones words with a single hole in it does.
            for (int length = 8; length <= 20; length++)
            {
                for (int hole = 0; hole < length; hole++)
                {
                    final long[] a = new long[length];
                    final long[] b = new long[length];
                    Arrays.fill(a, -1L);
                    a[hole] = 0L;
                    b[(hole + 1) % length] = -1L;

                    final String message = "length " + length + ", hole at " + hole;
                    assertEquals(reference(a, null, 0, length, (x, __) -> x), BitUtil.pop_array(a, 0, length), message);
                    assertEquals(reference(a, b, 0, length, (x, y) -> x & y), BitUtil.pop_intersect(a, b, 0, length), message);
                    assertEquals(reference(a, b, 0, length, (x, y) -> x | y), BitUtil.pop_union(a, b, 0, length), message);
                    assertEquals(reference(a, b, 0, length, (x, y) -> x & ~y), BitUtil.pop_andnot(a, b, 0, length), message);
                    assertEquals(reference(a, b, 0, length, (x, y) -> x ^ y), BitUtil.pop_xor(a, b, 0, length), message);
                }
            }
        }
    }

    @Nested
    @DisplayName("C. trailing and leading zero counts")
    class ZeroCounts
    {
        @Test
        @DisplayName("T14 ntz(long) agrees with Long.numberOfTrailingZeros, including zero")
        void ntzLong()
        {
            assertEquals(64, BitUtil.ntz(0L), "an all-zero word has 64 trailing zeros");

            for (int bit = 0; bit < 64; bit++)
            {
                assertEquals(bit, BitUtil.ntz(1L << bit), "single bit " + bit);
                assertEquals(bit, BitUtil.ntz(-1L << bit), "all bits from " + bit + " upwards");
            }

            final Random random = new Random(SEED);
            for (int i = 0; i < 10_000; i++)
            {
                final long value = random.nextLong();
                assertEquals(Long.numberOfTrailingZeros(value), BitUtil.ntz(value), "mismatch for " + value);
            }
        }

        @Test
        @DisplayName("T15 ntz(int) agrees with Integer.numberOfTrailingZeros, including zero")
        void ntzInt()
        {
            assertEquals(32, BitUtil.ntz(0), "an all-zero int has 32 trailing zeros");

            for (int bit = 0; bit < 32; bit++)
            {
                assertEquals(bit, BitUtil.ntz(1 << bit), "single bit " + bit);
            }

            final Random random = new Random(SEED);
            for (int i = 0; i < 10_000; i++)
            {
                final int value = random.nextInt();
                assertEquals(Integer.numberOfTrailingZeros(value), BitUtil.ntz(value), "mismatch for " + value);
            }
        }

        @Test
        @DisplayName("T16 ntz2 is an equivalent alternative implementation, zero included")
        void ntz2()
        {
            assertEquals(64, BitUtil.ntz2(0L));

            // the binary search inside only takes its 16 and 8 bit steps for words whose low bytes
            // are empty, which random words practically never are - so sweep the bit positions
            for (int bit = 0; bit < 64; bit++)
            {
                assertEquals(bit, BitUtil.ntz2(1L << bit), "single bit " + bit);
                assertEquals(bit, BitUtil.ntz2(-1L << bit), "all bits from " + bit + " upwards");
            }

            final Random random = new Random(SEED);
            for (int i = 0; i < 10_000; i++)
            {
                final long value = random.nextLong();
                assertEquals(Long.numberOfTrailingZeros(value), BitUtil.ntz2(value), "mismatch for " + value);
            }
        }

        @Test
        @DisplayName("T17 ntz3 matches the others for every non-zero word but answers 63 for zero")
        void ntz3()
        {
            for (int bit = 0; bit < 64; bit++)
            {
                assertEquals(bit, BitUtil.ntz3(1L << bit), "single bit " + bit);
                assertEquals(bit, BitUtil.ntz3(-1L << bit), "all bits from " + bit + " upwards");
            }

            final Random random = new Random(SEED);
            for (int i = 0; i < 10_000; i++)
            {
                final long value = random.nextLong();
                if (value != 0)
                {
                    assertEquals(Long.numberOfTrailingZeros(value), BitUtil.ntz3(value), "mismatch for " + value);
                }
            }

            // the javadoc of ntz2 warns about x != 0, ntz3 is the one that actually breaks: it has
            // no branch left to distinguish "bit 63 is set" from "nothing is set"
            assertEquals(63, BitUtil.ntz3(0L), "ntz3 is documented as undefined for zero, this pins what it does today");
            assertEquals(63, BitUtil.ntz3(Long.MIN_VALUE), "and it cannot be told apart from bit 63 being set");
        }

        @Test
        @DisplayName("T18 nlz agrees with Long.numberOfLeadingZeros")
        void nlz()
        {
            assertEquals(64, BitUtil.nlz(0L));
            assertEquals(0, BitUtil.nlz(-1L));
            assertEquals(0, BitUtil.nlz(Long.MIN_VALUE));

            for (int bit = 0; bit < 64; bit++)
            {
                assertEquals(63 - bit, BitUtil.nlz(1L << bit), "single bit " + bit);
            }

            final Random random = new Random(SEED);
            for (int i = 0; i < 10_000; i++)
            {
                final long value = random.nextLong();
                assertEquals(Long.numberOfLeadingZeros(value), BitUtil.nlz(value), "mismatch for " + value);
            }
        }

        @Test
        @DisplayName("T19 the lookup tables hold what their names promise")
        void lookupTables()
        {
            final byte[] expectedNtz = new byte[256];
            final byte[] expectedNlz = new byte[256];
            for (int i = 0; i < 256; i++)
            {
                expectedNtz[i] = (byte) (i == 0 ? 8 : Integer.numberOfTrailingZeros(i));
                expectedNlz[i] = (byte) (i == 0 ? 8 : Integer.numberOfLeadingZeros(i) - 24);
            }

            assertArrayEquals(expectedNtz, BitUtil.ntzTable);
            assertArrayEquals(expectedNlz, BitUtil.nlzTable);
        }
    }

    @Nested
    @DisplayName("D. power of two helpers - the part the report code depends on")
    class PowerOfTwo
    {
        @ParameterizedTest
        @ValueSource(ints =
        {
            1, 2, 4, 8, 16, 1024, 1 << 30
        })
        @DisplayName("T20 powers of two are recognized")
        void recognizesPowersOfTwo(final int value)
        {
            assertTrue(BitUtil.isPowerOfTwo(value));
            assertTrue(BitUtil.isPowerOfTwo((long) value));
        }

        @ParameterizedTest
        @ValueSource(ints =
        {
            3, 5, 6, 7, 9, 1000, -3, -5
        })
        @DisplayName("T21 non powers of two are rejected")
        void rejectsOthers(final int value)
        {
            assertFalse(BitUtil.isPowerOfTwo(value));
            assertFalse(BitUtil.isPowerOfTwo((long) value));
        }

        @Test
        @DisplayName("T22 zero and the smallest int count as powers of two - the check is v & (v-1) only")
        void zeroAndMinValue()
        {
            assertTrue(BitUtil.isPowerOfTwo(0), "documented in the javadoc as 'or zero'");
            assertTrue(BitUtil.isPowerOfTwo(0L));
            assertTrue(BitUtil.isPowerOfTwo(Integer.MIN_VALUE), "not documented, but 0x80000000 really is 2^31 in two's complement");
            assertTrue(BitUtil.isPowerOfTwo(Long.MIN_VALUE));
        }

        @Test
        @DisplayName("T23 nextHighestPowerOfTwo rounds up and leaves exact powers alone")
        void nextHighestPowerOfTwo()
        {
            assertEquals(1, BitUtil.nextHighestPowerOfTwo(1));
            assertEquals(2, BitUtil.nextHighestPowerOfTwo(2));
            assertEquals(4, BitUtil.nextHighestPowerOfTwo(3));
            assertEquals(4, BitUtil.nextHighestPowerOfTwo(4));
            assertEquals(8, BitUtil.nextHighestPowerOfTwo(5));
            assertEquals(1024, BitUtil.nextHighestPowerOfTwo(1000));
            assertEquals(1024, BitUtil.nextHighestPowerOfTwo(1024));
            assertEquals(2048, BitUtil.nextHighestPowerOfTwo(1025));

            // this is the one the report code cares about: a 3600 second window becomes 4096 slots
            assertEquals(4096, BitUtil.nextHighestPowerOfTwo(3600));
        }

        @Test
        @DisplayName("T24 the long variant behaves like the int variant but reaches further")
        void nextHighestPowerOfTwoLong()
        {
            assertEquals(4L, BitUtil.nextHighestPowerOfTwo(3L));
            assertEquals(4096L, BitUtil.nextHighestPowerOfTwo(3600L));
            assertEquals(1L << 32, BitUtil.nextHighestPowerOfTwo((1L << 31) + 1), "an int would have overflowed here");
            assertEquals(1L << 62, BitUtil.nextHighestPowerOfTwo((1L << 61) + 1));
        }

        @Test
        @DisplayName("T25 zero, negative input and overflow all produce unusable sizes")
        void degenerateInput()
        {
            // callers in this module feed this straight into a 'new Object[size]', so these three
            // results are the reason a size of 0, a negative size or a size above 2^30 explodes
            assertEquals(0, BitUtil.nextHighestPowerOfTwo(0));
            assertEquals(0, BitUtil.nextHighestPowerOfTwo(-1));
            assertEquals(0, BitUtil.nextHighestPowerOfTwo(-1000));
            assertEquals(Integer.MIN_VALUE, BitUtil.nextHighestPowerOfTwo((1 << 30) + 1));
            assertEquals(Integer.MIN_VALUE, BitUtil.nextHighestPowerOfTwo(Integer.MAX_VALUE));

            assertEquals(0L, BitUtil.nextHighestPowerOfTwo(0L));
            assertEquals(0L, BitUtil.nextHighestPowerOfTwo(-1L));
            assertEquals(Long.MIN_VALUE, BitUtil.nextHighestPowerOfTwo(Long.MAX_VALUE));
        }
    }
}
