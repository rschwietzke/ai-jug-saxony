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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The test suite for {@link BitCompression}.
 *
 * <p>The two methods here are only ever used as a pair. Together they halve the resolution of a bit
 * set that is used as a "which values did we see" sketch: bucket {@code 2k} and bucket {@code 2k+1}
 * of the input become bucket {@code k} of the output, and a bucket is set in the output when either
 * of its two sources was set. That composed behaviour is the real contract, so it gets its own
 * exhaustive test over all 64 bit positions (T05) on top of the tests for the individual steps.
 *
 * <p>Note what the pair does <em>not</em> do: it never carries anything into the upper 32 bits. The
 * output of a compression round always fits into bits 0..31. Callers that keep a 128 bucket sketch
 * in two longs have to fold the compressed high word into the upper half of the low word themselves
 * - see {@code IntTimeSeriesEntry} for one caller that does and one that forgets.
 */
class BitCompressionTest
{
    /**
     * A fixed seed keeps the randomized tests reproducible.
     */
    private static final long SEED = 20260906L;

    /**
     * Applies the two steps the way every production caller does.
     */
    private static long compressionRound(final long value)
    {
        return BitCompression.compressAndShiftOddBits(BitCompression.combineAdjacentBits(value));
    }

    @Nested
    @DisplayName("A. combineAdjacentBits")
    class CombineAdjacentBits
    {
        @Test
        @DisplayName("T01 a set bit is smeared into the next higher position")
        void smearsUpwards()
        {
            assertEquals(0b11L, BitCompression.combineAdjacentBits(0b01L));
            assertEquals(0b110L, BitCompression.combineAdjacentBits(0b10L));
            assertEquals(0b111L, BitCompression.combineAdjacentBits(0b11L));
            assertEquals(0L, BitCompression.combineAdjacentBits(0L));
        }

        @Test
        @DisplayName("T02 the highest bit is shifted out, everything else survives")
        void topBitFallsOff()
        {
            assertEquals(1L << 63, BitCompression.combineAdjacentBits(1L << 63), "nothing above bit 63 to smear into");
            assertEquals((1L << 63) | (1L << 62), BitCompression.combineAdjacentBits(1L << 62));
            assertEquals(-1L, BitCompression.combineAdjacentBits(-1L));
        }

        @Test
        @DisplayName("T03 it is exactly v | (v << 1) for arbitrary words")
        void matchesTheDefinition()
        {
            final Random random = new Random(SEED);
            for (int i = 0; i < 10_000; i++)
            {
                final long value = random.nextLong();
                assertEquals(value | (value << 1), BitCompression.combineAdjacentBits(value));
            }
        }
    }

    @Nested
    @DisplayName("B. compressAndShiftOddBits")
    class CompressAndShiftOddBits
    {
        @Test
        @DisplayName("T04 the odd bits are packed into the lower 32 bits")
        void packsOddBits()
        {
            assertEquals(0L, BitCompression.compressAndShiftOddBits(0L));
            assertEquals(0xFFFFFFFFL, BitCompression.compressAndShiftOddBits(-1L), "all 32 odd bits set");
            assertEquals(0xFFFFFFFFL, BitCompression.compressAndShiftOddBits(0xAAAAAAAAAAAAAAAAL), "only the odd bits matter");
            assertEquals(0L, BitCompression.compressAndShiftOddBits(0x5555555555555555L), "even bits are dropped");

            for (int bit = 0; bit < 64; bit++)
            {
                final long compressed = BitCompression.compressAndShiftOddBits(1L << bit);
                final long expected = (bit % 2 == 0) ? 0L : (1L << (bit / 2));
                assertEquals(expected, compressed, "input bit " + bit);
            }
        }

        @Test
        @DisplayName("T05 the result never uses the upper 32 bits")
        void resultFitsIn32Bits()
        {
            final Random random = new Random(SEED);
            for (int i = 0; i < 10_000; i++)
            {
                final long compressed = BitCompression.compressAndShiftOddBits(random.nextLong());
                assertEquals(0L, compressed >>> 32, "the upper half must stay clean");
                assertTrue(compressed >= 0, "and therefore the result is never negative");
            }
        }

        @Test
        @DisplayName("T06 it agrees with a straightforward bit by bit reference implementation")
        void matchesReferenceImplementation()
        {
            final Random random = new Random(SEED);
            for (int i = 0; i < 5_000; i++)
            {
                final long value = random.nextLong();

                long expected = 0;
                for (int bit = 1; bit < 64; bit += 2)
                {
                    if ((value & (1L << bit)) != 0)
                    {
                        expected |= 1L << (bit / 2);
                    }
                }

                assertEquals(expected, BitCompression.compressAndShiftOddBits(value), "mismatch for " + Long.toHexString(value));
            }
        }
    }

    @Nested
    @DisplayName("C. the composed compression round - what callers actually use")
    class CompressionRound
    {
        @Test
        @DisplayName("T07 bucket 2k and bucket 2k+1 both end up in bucket k")
        void halvesTheResolution()
        {
            for (int bit = 0; bit < 64; bit++)
            {
                assertEquals(1L << (bit / 2), compressionRound(1L << bit), "input bit " + bit + " must land in bucket " + (bit / 2));
            }
        }

        @Test
        @DisplayName("T08 a round is lossy but never invents or loses a populated bucket")
        void isLossyButComplete()
        {
            final Random random = new Random(SEED);
            for (int i = 0; i < 5_000; i++)
            {
                final long value = random.nextLong();
                final long compressed = compressionRound(value);

                for (int bit = 0; bit < 64; bit++)
                {
                    final boolean sourceSet = (value & (1L << bit)) != 0;
                    final boolean targetSet = (compressed & (1L << (bit / 2))) != 0;

                    if (sourceSet)
                    {
                        assertTrue(targetSet, "bucket " + (bit / 2) + " lost the value from bit " + bit);
                    }
                }

                for (int bit = 0; bit < 32; bit++)
                {
                    if ((compressed & (1L << bit)) != 0)
                    {
                        final boolean anySourceSet = (value & ((1L << (2 * bit)) | (1L << (2 * bit + 1)))) != 0;
                        assertTrue(anySourceSet, "bucket " + bit + " was invented out of nothing");
                    }
                }

                assertTrue(Long.bitCount(compressed) <= Long.bitCount(value), "compression can only merge buckets, never split them");
            }
        }

        @Test
        @DisplayName("T09 repeated rounds converge to a single bucket")
        void repeatedRoundsConverge()
        {
            long value = -1L;
            for (int round = 1; round <= 6; round++)
            {
                value = compressionRound(value);
                assertEquals(64 >> round, Long.bitCount(value), "after round " + round);
            }

            // six rounds turn 64 set buckets into one
            assertEquals(1L, value);
            assertEquals(1L, compressionRound(value), "bucket 0 stays bucket 0 forever");
            assertEquals(0L, compressionRound(0L), "and an empty sketch stays empty");
        }
    }

    @Nested
    @DisplayName("D. the class itself")
    class ClassShape
    {
        @Test
        @DisplayName("T10 it is a final utility class that cannot be instantiated from the outside")
        void isAUtilityClass() throws Exception
        {
            assertTrue(Modifier.isFinal(BitCompression.class.getModifiers()));

            final Constructor<BitCompression> constructor = BitCompression.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            assertFalse(constructor.canAccess(null));

            constructor.setAccessible(true);
            assertNotNull(constructor.newInstance(), "the constructor is empty, it must not blow up when forced");
        }
    }
}
