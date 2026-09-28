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
package com.xceptance.xlt.report.util.rework;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The test suite for {@link IntTimeSeriesEntry}.
 *
 * <p>An entry is the bucket of one time slot. It carries two very different kinds of state and the
 * tests are split along that line.
 *
 * <p>The first kind is exact: count, error count, sum, minimum, maximum and the concurrency counter.
 * Those can be asserted to the last digit and section B does that.
 *
 * <p>The second kind is a deliberately lossy sketch of the distinct values that were seen. It is 128
 * bits wide, held in two longs, and bit {@code i} means "a value in bucket {@code i} occurred".
 * While all values stay below 128 the sketch is exact. As soon as a larger value arrives the whole
 * sketch is halved - two neighbouring buckets are ORed into one - and the scale exponent goes up by
 * one. From then on {@link IntTimeSeriesEntry#getValues()} reports bucket floors, so a value of 127
 * comes back as 126. Section C pins that down; the important property is that a reported value is
 * never larger than the value that produced it and never more than one bucket width below it.
 *
 * <p>Section E holds the behaviour that is wrong rather than merely lossy. The rescaling inside
 * {@link IntTimeSeriesEntry#merge(IntTimeSeriesEntry)} is not the same operation as the rescaling
 * inside the value update, and buckets above 63 come out of a merge at roughly 1.6 times their real
 * value. The tests document what happens today so a fix has a visible before and after; see
 * {@code doc/XLT-DATA.md} section on defects.
 */
class IntTimeSeriesEntryTest
{
    /**
     * A fixed seed keeps the randomized tests reproducible.
     */
    private static final long SEED = 20260906L;

    /**
     * The width of the distinct value sketch in buckets.
     */
    private static final int SKETCH_BUCKETS = 128;

    /**
     * Feeds all values into a fresh entry, none of them marked as failed.
     */
    private static IntTimeSeriesEntry entryOf(final int... values)
    {
        final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        for (final int value : values)
        {
            entry.updateValue(value, false);
        }

        return entry;
    }

    /**
     * The bucket width the entry must be using given the largest value it has seen. The sketch holds
     * 128 buckets, so the width is the smallest power of two that makes the largest value fit.
     */
    private static int expectedSlotWidth(final int largestValue)
    {
        int width = 1;
        while (largestValue / width >= SKETCH_BUCKETS)
        {
            width = width * 2;
        }

        return width;
    }

    @Nested
    @DisplayName("A. Construction and empty state")
    class ConstructionAndEmptyState
    {
        @Test
        @DisplayName("T01 a fresh entry reports zero everywhere, sentinels included")
        void freshEntry()
        {
            final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();

            assertEquals(0, entry.getCount());
            assertEquals(0, entry.getConcurrentCount());
            assertEquals(0, entry.getErrorCount());
            assertEquals(0L, entry.getTotalValue());
            assertEquals(0, entry.getAverageValue(), "no division by zero for an empty entry");
            assertEquals(0, entry.getMinimumValue(), "the MAX_VALUE sentinel is hidden from callers");
            assertEquals(0, entry.getMaximumValue(), "the MIN_VALUE sentinel is hidden from callers");
            assertArrayEquals(new double[0], entry.getValues());
        }

        @Test
        @DisplayName("T02 the value constructor is the same as a single update")
        void valueConstructor()
        {
            final IntTimeSeriesEntry constructed = new IntTimeSeriesEntry(5, false);
            final IntTimeSeriesEntry updated = entryOf(5);

            assertEquals(updated, constructed);
            assertEquals(1, constructed.getCount());
            assertEquals(1, constructed.getConcurrentCount());
            assertEquals(5, constructed.getMinimumValue());
            assertEquals(5, constructed.getMaximumValue());
            assertEquals(0, constructed.getErrorCount());
        }

        @Test
        @DisplayName("T03 the value constructor can start with a failed sample")
        void valueConstructorWithError()
        {
            final IntTimeSeriesEntry entry = new IntTimeSeriesEntry(7, true);

            assertEquals(1, entry.getCount());
            assertEquals(1, entry.getErrorCount());
            assertEquals(7L, entry.getTotalValue());
        }
    }

    @Nested
    @DisplayName("B. The exact statistics")
    class ExactStatistics
    {
        @Test
        @DisplayName("T04 count, sum, minimum and maximum follow the samples")
        void accumulates()
        {
            final IntTimeSeriesEntry entry = entryOf(10, 30, 20);

            assertEquals(3, entry.getCount());
            assertEquals(60L, entry.getTotalValue());
            assertEquals(10, entry.getMinimumValue());
            assertEquals(30, entry.getMaximumValue());
            assertEquals(20, entry.getAverageValue());
        }

        @Test
        @DisplayName("T05 the average is an integer division and truncates")
        void averageTruncates()
        {
            assertEquals(3, entryOf(3, 4).getAverageValue(), "7/2 is reported as 3");
            assertEquals(0, entryOf(0, 1).getAverageValue());
            assertEquals(1, entryOf(1, 1, 2).getAverageValue(), "4/3 is reported as 1");
        }

        @Test
        @DisplayName("T06 the sum is a long and survives an int overflow")
        void sumIsALong()
        {
            final IntTimeSeriesEntry entry = entryOf(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

            assertEquals(3L * Integer.MAX_VALUE, entry.getTotalValue());
            assertEquals(Integer.MAX_VALUE, entry.getMaximumValue());
            assertEquals(Integer.MAX_VALUE, entry.getAverageValue());
        }

        @Test
        @DisplayName("T07 only failed samples raise the error count")
        void errorCount()
        {
            final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(1, true);
            entry.updateValue(2, false);
            entry.updateValue(3, true);

            assertEquals(3, entry.getCount());
            assertEquals(2, entry.getErrorCount());
            assertEquals(6L, entry.getTotalValue(), "a failed sample still counts towards the runtime");
        }

        @Test
        @DisplayName("T08 every sample counts as one concurrent request, extra concurrency can be added on its own")
        void concurrency()
        {
            final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            assertEquals(0, entry.getConcurrentCount());

            entry.updateValue(1, false);
            assertEquals(1, entry.getConcurrentCount(), "a sample implies a concurrent request");

            entry.updateConcurrency();
            entry.updateConcurrency();
            assertEquals(3, entry.getConcurrentCount());
            assertEquals(1, entry.getCount(), "a pure concurrency bump is not a sample");
            assertEquals(1L, entry.getTotalValue(), "and it does not contribute a runtime either");
        }

        @ParameterizedTest
        @ValueSource(ints =
        {
            -1, -100, Integer.MIN_VALUE
        })
        @DisplayName("T09 a negative sample is clamped to zero before anything else happens")
        void negativeValuesAreClamped(final int value)
        {
            final IntTimeSeriesEntry entry = entryOf(value);

            assertEquals(1, entry.getCount(), "it is still counted as a sample");
            assertEquals(0L, entry.getTotalValue());
            assertEquals(0, entry.getMinimumValue());
            assertEquals(0, entry.getMaximumValue());
            assertArrayEquals(new double[]
            {
                0.0
            }, entry.getValues(), "and it lands in bucket zero of the sketch");
        }

        @Test
        @DisplayName("T10 a negative sample after a positive one pulls the minimum down to zero")
        void negativeAfterPositive()
        {
            final IntTimeSeriesEntry entry = entryOf(50, -7, 80);

            assertEquals(3, entry.getCount());
            assertEquals(130L, entry.getTotalValue());
            assertEquals(0, entry.getMinimumValue());
            assertEquals(80, entry.getMaximumValue());
        }

        @Test
        @DisplayName("T11 zero is a perfectly normal sample")
        void zeroIsASample()
        {
            final IntTimeSeriesEntry entry = entryOf(0, 0);

            assertEquals(2, entry.getCount());
            assertEquals(0L, entry.getTotalValue());
            assertEquals(0, entry.getMinimumValue());
            assertEquals(0, entry.getMaximumValue());
            assertArrayEquals(new double[]
            {
                0.0
            }, entry.getValues());
        }
    }

    @Nested
    @DisplayName("C. The distinct value sketch")
    class ValueSketch
    {
        @Test
        @DisplayName("T12 below 128 the sketch is exact and sorted")
        void exactBelow128()
        {
            final IntTimeSeriesEntry entry = entryOf(5, 1, 127, 64, 63, 0);

            assertArrayEquals(new double[]
            {
                0.0, 1.0, 5.0, 63.0, 64.0, 127.0
            }, entry.getValues(), "ascending, one entry per distinct value");
        }

        @Test
        @DisplayName("T13 the sketch is a set, repeats collapse")
        void isASet()
        {
            final IntTimeSeriesEntry entry = entryOf(7, 7, 7, 7);

            assertArrayEquals(new double[]
            {
                7.0
            }, entry.getValues());
            assertEquals(4, entry.getCount(), "but the count still sees all four samples");
        }

        @Test
        @DisplayName("T14 both halves of the 128 bit sketch are used")
        void bothWordsAreUsed()
        {
            assertArrayEquals(new double[]
            {
                63.0
            }, entryOf(63).getValues(), "the last bucket of the low word");
            assertArrayEquals(new double[]
            {
                64.0
            }, entryOf(64).getValues(), "the first bucket of the high word");
            assertEquals(SKETCH_BUCKETS, entryOf(buckets(0, SKETCH_BUCKETS)).getValues().length, "all 128 buckets can be set at once");
        }

        @Test
        @DisplayName("T15 a value of 128 halves the resolution of the whole sketch")
        void firstRescale()
        {
            final IntTimeSeriesEntry entry = entryOf(0, 1, 2, 3);
            assertArrayEquals(new double[]
            {
                0.0, 1.0, 2.0, 3.0
            }, entry.getValues());

            entry.updateValue(128, false);

            assertArrayEquals(new double[]
            {
                0.0, 2.0, 128.0
            }, entry.getValues(), "0 and 1 merged into bucket 0, 2 and 3 into bucket 1 which reports as 2");
            assertEquals(0, entry.getMinimumValue(), "the exact statistics are untouched by the rescale");
            assertEquals(128, entry.getMaximumValue());
            assertEquals(5, entry.getCount());
        }

        @Test
        @DisplayName("T16 the sketch keeps rescaling as the values grow")
        void repeatedRescale()
        {
            final IntTimeSeriesEntry entry = entryOf(100);
            assertArrayEquals(new double[]
            {
                100.0
            }, entry.getValues());

            entry.updateValue(255, false);
            assertArrayEquals(new double[]
            {
                100.0, 254.0
            }, entry.getValues(), "bucket width 2, so 255 reports as 254");

            entry.updateValue(511, false);
            assertArrayEquals(new double[]
            {
                100.0, 252.0, 508.0
            }, entry.getValues(), "bucket width 4 now");
        }

        @ParameterizedTest
        @ValueSource(ints =
        {
            0, 1, 127, 128, 129, 255, 256, 1_000, 100_000, 1 << 20, Integer.MAX_VALUE
        })
        @DisplayName("T17 a single sample is reported within one bucket width below itself")
        void singleSampleIsReportedCloseToItself(final int value)
        {
            final IntTimeSeriesEntry entry = entryOf(value);
            final double[] values = entry.getValues();
            final int width = expectedSlotWidth(value);

            assertEquals(1, values.length);
            assertTrue(values[0] <= value, "the sketch must never report more than the real value");
            assertTrue(values[0] > (double) value - width, "and never more than one bucket width less");
            assertEquals(value - (value % width), values[0], "it is the floor of the bucket the value fell into");
        }

        @Test
        @DisplayName("T18 an arbitrary sample set is approximated, never overstated")
        void randomSamplesAreApproximated()
        {
            final Random random = new Random(SEED);

            for (int round = 0; round < 200; round++)
            {
                final int bound = 1 << (1 + random.nextInt(20));
                final int[] samples = new int[1 + random.nextInt(30)];
                final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();

                int largest = 0;
                for (int i = 0; i < samples.length; i++)
                {
                    samples[i] = random.nextInt(bound);
                    largest = Math.max(largest, samples[i]);
                    entry.updateValue(samples[i], false);
                }

                final double[] sketch = entry.getValues();
                final int width = expectedSlotWidth(largest);

                assertTrue(sketch.length <= samples.length, "the sketch cannot hold more buckets than there were samples");
                assertTrue(sketch.length <= SKETCH_BUCKETS, "and never more than 128");

                for (final int sample : samples)
                {
                    final double bucketFloor = sample - (sample % width);
                    assertTrue(contains(sketch, bucketFloor), "sample " + sample + " is missing from the sketch " + java.util.Arrays.toString(sketch));
                }

                for (final double reported : sketch)
                {
                    assertTrue(reported <= largest, "the sketch reported " + reported + " which is above the largest sample " + largest);
                }
            }
        }

        @Test
        @DisplayName("T19 the sketch never grows beyond 128 buckets, whatever arrives")
        void isBounded()
        {
            final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            for (int i = 0; i < 10_000; i++)
            {
                entry.updateValue(i, false);
                assertTrue(entry.getValues().length <= SKETCH_BUCKETS, "after " + i + " samples");
            }

            assertEquals(10_000, entry.getCount());
            assertEquals(9_999, entry.getMaximumValue(), "the exact statistics stay exact");
        }

        private static boolean contains(final double[] values, final double value)
        {
            for (final double candidate : values)
            {
                if (candidate == value)
                {
                    return true;
                }
            }

            return false;
        }

        private static int[] buckets(final int from, final int toExclusive)
        {
            final int[] values = new int[toExclusive - from];
            for (int i = 0; i < values.length; i++)
            {
                values[i] = from + i;
            }

            return values;
        }
    }

    @Nested
    @DisplayName("D. Merging two entries")
    class Merge
    {
        @Test
        @DisplayName("T20 merge adds up the samples and returns the receiver")
        void mergeAccumulates()
        {
            final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
            a.updateValue(10, false);
            a.updateValue(20, true);

            final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
            b.updateValue(30, false);

            final IntTimeSeriesEntry merged = a.merge(b);

            assertSame(a, merged, "merge works in place on the receiver");
            assertEquals(3, merged.getCount());
            assertEquals(60L, merged.getTotalValue());
            assertEquals(1, merged.getErrorCount());
            assertEquals(10, merged.getMinimumValue());
            assertEquals(30, merged.getMaximumValue());
            assertEquals(20, merged.getAverageValue());
            assertArrayEquals(new double[]
            {
                10.0, 20.0, 30.0
            }, merged.getValues(), "the sketches are ORed together");
        }

        @Test
        @DisplayName("T21 concurrency is the maximum, not the sum - the slots overlap in time")
        void concurrencyIsAMaximum()
        {
            final IntTimeSeriesEntry a = new IntTimeSeriesEntry();
            a.updateValue(1, false);
            a.updateConcurrency();
            a.updateConcurrency();

            final IntTimeSeriesEntry b = new IntTimeSeriesEntry();
            b.updateValue(1, false);

            assertEquals(3, a.getConcurrentCount());
            assertEquals(1, b.getConcurrentCount());
            assertEquals(3, a.merge(b).getConcurrentCount(), "3 and 1 merge to 3, not to 4");
        }

        @Test
        @DisplayName("T22 merging an empty entry changes nothing that is visible")
        void mergeWithEmpty()
        {
            final IntTimeSeriesEntry filled = entryOf(5, 15);
            final IntTimeSeriesEntry expected = entryOf(5, 15);

            assertEquals(expected, filled.merge(new IntTimeSeriesEntry()));
            assertEquals(5, filled.getMinimumValue(), "the MAX_VALUE sentinel of the empty entry did not win");
            assertEquals(15, filled.getMaximumValue());
            assertEquals(2, filled.getCount());
        }

        @Test
        @DisplayName("T23 merging into an empty entry copies the other side over")
        void mergeIntoEmpty()
        {
            final IntTimeSeriesEntry empty = new IntTimeSeriesEntry();
            final IntTimeSeriesEntry filled = entryOf(5, 15);

            assertEquals(filled, empty.merge(filled));
            assertEquals(5, empty.getMinimumValue());
            assertEquals(15, empty.getMaximumValue());
            assertEquals(2, empty.getCount());
        }

        @Test
        @DisplayName("T24 two empty entries merge into an empty entry")
        void mergeTwoEmpty()
        {
            final IntTimeSeriesEntry merged = new IntTimeSeriesEntry().merge(new IntTimeSeriesEntry());

            assertEquals(new IntTimeSeriesEntry(), merged);
            assertEquals(0, merged.getMinimumValue());
            assertEquals(0, merged.getMaximumValue());
            assertArrayEquals(new double[0], merged.getValues());
        }

        @Test
        @DisplayName("T25 the exact statistics of a merge do not depend on the direction")
        void exactStatisticsAreSymmetric()
        {
            final Random random = new Random(SEED);

            for (int round = 0; round < 100; round++)
            {
                final IntTimeSeriesEntry a1 = new IntTimeSeriesEntry();
                final IntTimeSeriesEntry a2 = new IntTimeSeriesEntry();
                final IntTimeSeriesEntry b1 = new IntTimeSeriesEntry();
                final IntTimeSeriesEntry b2 = new IntTimeSeriesEntry();

                for (int i = 0; i < 5; i++)
                {
                    final int left = random.nextInt(1_000);
                    final int right = random.nextInt(1_000);
                    final boolean leftFailed = random.nextBoolean();
                    final boolean rightFailed = random.nextBoolean();

                    a1.updateValue(left, leftFailed);
                    a2.updateValue(left, leftFailed);
                    b1.updateValue(right, rightFailed);
                    b2.updateValue(right, rightFailed);
                }

                final IntTimeSeriesEntry forward = a1.merge(b1);
                final IntTimeSeriesEntry backward = b2.merge(a2);

                assertEquals(forward.getCount(), backward.getCount());
                assertEquals(forward.getTotalValue(), backward.getTotalValue());
                assertEquals(forward.getErrorCount(), backward.getErrorCount());
                assertEquals(forward.getMinimumValue(), backward.getMinimumValue());
                assertEquals(forward.getMaximumValue(), backward.getMaximumValue());
                assertEquals(forward.getConcurrentCount(), backward.getConcurrentCount());
            }
        }

        @Test
        @DisplayName("T26 merging a coarser entry rescales the receiver")
        void mergeRescalesTheReceiver()
        {
            final IntTimeSeriesEntry fine = entryOf(10);
            final IntTimeSeriesEntry coarse = entryOf(10, 200);

            assertArrayEquals(new double[]
            {
                10.0
            }, fine.getValues());
            assertArrayEquals(new double[]
            {
                10.0, 200.0
            }, coarse.getValues(), "bucket width 2 already");

            final IntTimeSeriesEntry merged = fine.merge(coarse);

            assertArrayEquals(new double[]
            {
                10.0, 200.0
            }, merged.getValues(), "the receiver was pulled to the coarser scale");
            assertEquals(3, merged.getCount());
            assertEquals(220L, merged.getTotalValue());
        }

        @Test
        @DisplayName("T27 merging a finer entry rescales the argument - merge is not read only")
        void mergeMutatesTheArgument()
        {
            final IntTimeSeriesEntry coarse = entryOf(10, 200);
            final IntTimeSeriesEntry fine = entryOf(11);

            assertArrayEquals(new double[]
            {
                11.0
            }, fine.getValues());

            coarse.merge(fine);

            assertArrayEquals(new double[]
            {
                10.0
            }, fine.getValues(),
                              "the javadoc warns about it: the argument was rescaled in place and now reports 10 instead of 11");
            assertEquals(1, fine.getCount(), "only the sketch is touched, the statistics of the argument stay put");
        }
    }

    @Nested
    @DisplayName("E. equals, toString and the documented defects")
    class ContractAndDefects
    {
        @Test
        @DisplayName("T28 equals compares every field")
        void equalsComparesEverything()
        {
            final IntTimeSeriesEntry reference = entryOf(5, 10);

            assertEquals(reference, reference, "reflexive");
            assertEquals(reference, entryOf(5, 10), "same samples, same state");
            assertEquals(entryOf(5, 10), reference, "symmetric");

            assertNotEquals(reference, entryOf(5, 11), "a different maximum");
            assertNotEquals(reference, entryOf(4, 10), "a different minimum");
            assertNotEquals(reference, entryOf(5, 10, 5), "a different count");
            assertFalse(reference.equals(null), "null is never equal");
            assertFalse(reference.equals("not an entry"), "a foreign type is never equal");
        }

        @Test
        @DisplayName("T29 equals sees the error count, the concurrency and the sketch scale too")
        void equalsSeesTheHiddenState()
        {
            final IntTimeSeriesEntry withError = new IntTimeSeriesEntry(5, true);
            final IntTimeSeriesEntry withoutError = new IntTimeSeriesEntry(5, false);
            assertNotEquals(withError, withoutError);

            final IntTimeSeriesEntry concurrent = new IntTimeSeriesEntry(5, false);
            concurrent.updateConcurrency();
            assertNotEquals(new IntTimeSeriesEntry(5, false), concurrent);

            // same sample, but one of them was forced to a coarser sketch and back to the same
            // reported values, which equals still tells apart because it compares the scale
            final IntTimeSeriesEntry coarse = entryOf(0, 128);
            final IntTimeSeriesEntry alsoCoarse = entryOf(1, 128);
            assertArrayEquals(coarse.getValues(), alsoCoarse.getValues(), "both report 0 and 128");
            assertNotEquals(coarse, alsoCoarse, "but their sums differ, so they are not equal");
        }

        @Test
        @DisplayName("T30 equals looks at every single field on its own")
        void equalsChecksEveryFieldIndividually()
        {
            // one pair per field, built so that everything equals compares before that field is
            // already identical - otherwise the earlier check would decide the outcome
            assertNotEquals(entryOf(1, 2, 3), entryOf(1, 3, 3), "same count, minimum and maximum, different sum");
            assertNotEquals(entryOf(0, 1, 1, 4), entryOf(0, 2, 0, 4), "same statistics, different sketch in the low word");
            assertNotEquals(entryOf(0, 100, 101, 127), entryOf(0, 99, 102, 127), "same statistics and low word, different high word");

            // and the scale on its own: merging as the argument rescales an entry in place without
            // touching anything else, and bucket 0 survives a rescale unchanged
            final IntTimeSeriesEntry rescaled = entryOf(0);
            final IntTimeSeriesEntry untouched = entryOf(0);
            entryOf(0, 200).merge(rescaled);

            assertEquals(untouched.getCount(), rescaled.getCount());
            assertEquals(untouched.getTotalValue(), rescaled.getTotalValue());
            assertArrayEquals(untouched.getValues(), rescaled.getValues(), "both still report the same single value");
            assertNotEquals(untouched, rescaled, "but the sketch scale differs, so they are not equal any more");
        }

        @Test
        @DisplayName("T31 hashCode is not implemented, so equal entries do not share a hash bucket")
        void hashCodeIsMissing()
        {
            final IntTimeSeriesEntry one = entryOf(5);
            final IntTimeSeriesEntry two = entryOf(5);

            assertEquals(one, two);
            assertNotEquals(one.hashCode(), two.hashCode(),
                            "identity hash only - never put these into a HashSet or use them as a HashMap key");
        }

        @Test
        @DisplayName("T32 toString is the debugging one liner and ends with a newline")
        void toStringFormat()
        {
            final IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
            entry.updateValue(10, false);
            entry.updateValue(20, true);
            entry.updateConcurrency();

            assertEquals("2 / 3 / 1 / 30 / 15 / 10 / 20 / [10.0, 20.0]\n", entry.toString());
            assertEquals("0 / 0 / 0 / 0 / 0 / 0 / 0 / []\n", new IntTimeSeriesEntry().toString());
        }

        @Test
        @DisplayName("T33 DEFECT the merge rescale misplaces every bucket above 63")
        void mergeRescaleMisplacesHighBuckets()
        {
            // updateValue folds the compressed high word into the upper half of the low word.
            // merge does not, it compresses both words in place, so a bucket in the high word ends
            // up at half its old index inside the high word instead of moving into the low word.
            final IntTimeSeriesEntry withHighBucket = entryOf(100);
            final IntTimeSeriesEntry coarser = entryOf(0, 200);

            assertArrayEquals(new double[]
            {
                100.0
            }, withHighBucket.getValues());

            final double[] merged = withHighBucket.merge(coarser).getValues();

            assertArrayEquals(new double[]
            {
                0.0, 164.0, 200.0
            }, merged, "the 100 should have stayed at 100, it is reported as 164");
            assertEquals(0, withHighBucket.getMinimumValue(), "the exact statistics are still right");
            assertEquals(200, withHighBucket.getMaximumValue());
            assertEquals(3, withHighBucket.getCount());

            // and the damage is systematic: bucket b in the high word moves to 64 + (b - 64) / 2
            // instead of to b / 2, which is a factor of about 1.64 on the reported value
            final IntTimeSeriesEntry other = entryOf(80);
            other.merge(entryOf(0, 200));
            assertArrayEquals(new double[]
            {
                0.0, 144.0, 200.0
            }, other.getValues(), "80 is reported as 144");
        }

        @Test
        @DisplayName("T34 the same rescale done by updateValue is correct, which is what makes T32 a defect")
        void updateValueRescaleIsCorrect()
        {
            final IntTimeSeriesEntry entry = entryOf(100);
            entry.updateValue(200, false);

            assertArrayEquals(new double[]
            {
                100.0, 200.0
            }, entry.getValues(), "the very same rescale, but triggered by a sample instead of a merge");
        }
    }
}
