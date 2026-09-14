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
package com.xceptance.xlt.report.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The test suite for {@link RuntimeHistogram}.
 *
 * <p>The class is a counting histogram: it never stores a value, it only counts how often a bucket
 * was hit. Two things follow from that and both are tested here. First, the bucket array is a dense
 * window that grows to the left and to the right as values arrive, so the growth logic is where the
 * bugs would be and it is exercised in every direction. Second, everything that comes back out is a
 * bucket floor, never an original value, which is why {@code getPercentile(100)} does not return the
 * largest value that was added once the precision is coarser than 1.
 *
 * <p>The percentile definition is the empirical quantile from the German Wikipedia article the code
 * links to: for {@code n * p / 100} integral it is the mean of the two neighbouring order
 * statistics, otherwise it is the value at {@code ceil(n * p / 100)}. The fixed sample in section C
 * is checked against that definition by hand so the tests keep working if the implementation is
 * rewritten.
 *
 * <p>Nested class E collects behaviour that is surprising rather than wrong-by-contract. Those tests
 * exist to make the current state visible and to fail loudly when somebody changes it - see
 * {@code doc/XLT-DATA.md} for what the improved version should do instead.
 */
class RuntimeHistogramTest
{
    /**
     * A fixed seed keeps the randomized tests reproducible.
     */
    private static final long SEED = 20260906L;

    /**
     * Feeds all values into a fresh histogram.
     */
    private static RuntimeHistogram histogramOf(final int precision, final int... values)
    {
        final RuntimeHistogram histogram = new RuntimeHistogram(precision);
        for (final int value : values)
        {
            histogram.addValue(value);
        }

        return histogram;
    }

    /**
     * The empirical quantile of an already sorted sample, straight from the definition the class
     * documents. Used as an independent oracle.
     */
    private static double expectedPercentile(final int[] sortedValues, final double p)
    {
        final double np = sortedValues.length * (p / 100.0);

        if (np % 1.0 == 0.0)
        {
            return (sortedValues[(int) np - 1] + sortedValues[(int) np]) / 2.0;
        }

        return sortedValues[(int) Math.ceil(np) - 1];
    }

    @Nested
    @DisplayName("A. Construction, precision and the empty histogram")
    class ConstructionAndEmptyState
    {
        @Test
        @DisplayName("T01 a fresh histogram is empty and answers everything with zero")
        void freshHistogramIsEmpty()
        {
            final RuntimeHistogram histogram = new RuntimeHistogram();

            assertTrue(histogram.isEmpty());
            assertEquals(0, histogram.getValueCount());
            assertEquals(0, histogram.getNumberOfBuckets(), "no bucket array has been allocated yet");
            assertEquals(0.0, histogram.getPercentile(50.0));
            assertEquals(0.0, histogram.getPercentile(0.0));
            assertEquals(0.0, histogram.getPercentile(100.0));
            assertEquals(0.0, histogram.getMedianValue());
            assertEquals(0.0, histogram.getQuantile(0.9));
            assertEquals(0, histogram.getCountForValue(Integer.MIN_VALUE, Integer.MAX_VALUE));
        }

        @Test
        @DisplayName("T02 the default constructor keeps every value distinguishable")
        void defaultPrecisionIsOne()
        {
            assertEquals(1, new RuntimeHistogram().getPrecision());

            final RuntimeHistogram histogram = histogramOf(1, 0, 1, 2, 3);
            assertEquals(4, histogram.getNumberOfBuckets(), "one bucket per value with precision 1");
        }

        @ParameterizedTest
        @CsvSource(
        {
            "1, 1", "2, 2", "3, 4", "4, 4", "5, 8", "8, 8", "16, 16", "100, 128"
        })
        @DisplayName("T03 a requested precision is rounded up to the next power of two")
        void precisionIsRoundedUp(final int requested, final int effective)
        {
            assertEquals(effective, new RuntimeHistogram(requested).getPrecision());
        }

        @Test
        @DisplayName("T04 a precision of 8 puts eight adjacent values into one bucket")
        void precisionGroupsValues()
        {
            final RuntimeHistogram histogram = histogramOf(8, 0, 1, 2, 3, 4, 5, 6, 7);

            assertEquals(1, histogram.getNumberOfBuckets());
            assertEquals(8, histogram.getValueCount());
            assertEquals(8, histogram.getCountForValue(0, 7));

            histogram.addValue(8);
            assertEquals(2, histogram.getNumberOfBuckets(), "value 8 opens the second bucket");
        }
    }

    @Nested
    @DisplayName("B. Adding values and growing the bucket window")
    class AddAndGrow
    {
        @Test
        @DisplayName("T05 a single value allocates exactly one bucket")
        void firstValueAllocatesOneBucket()
        {
            final RuntimeHistogram histogram = histogramOf(1, 42);

            assertFalse(histogram.isEmpty());
            assertEquals(1, histogram.getValueCount());
            assertEquals(1, histogram.getNumberOfBuckets());
            assertEquals(1, histogram.getCountForValue(42, 42));
            assertEquals(42.0, histogram.getPercentile(50.0));
        }

        @Test
        @DisplayName("T06 repeating a value only bumps its counter")
        void repeatedValueDoesNotGrow()
        {
            final RuntimeHistogram histogram = histogramOf(1, 7, 7, 7, 7);

            assertEquals(4, histogram.getValueCount());
            assertEquals(1, histogram.getNumberOfBuckets());
            assertEquals(4, histogram.getCountForValue(7, 7));
        }

        @Test
        @DisplayName("T07 a larger value grows the window to the right")
        void growsRight()
        {
            final RuntimeHistogram histogram = histogramOf(1, 10, 13);

            assertEquals(4, histogram.getNumberOfBuckets(), "buckets 10..13");
            assertEquals(1, histogram.getCountForValue(10, 10));
            assertEquals(0, histogram.getCountForValue(11, 12), "the gap stays empty");
            assertEquals(1, histogram.getCountForValue(13, 13));
        }

        @Test
        @DisplayName("T08 a smaller value grows the window to the left and shifts the existing counts")
        void growsLeft()
        {
            final RuntimeHistogram histogram = histogramOf(1, 10, 10, 6);

            assertEquals(5, histogram.getNumberOfBuckets(), "buckets 6..10");
            assertEquals(1, histogram.getCountForValue(6, 6));
            assertEquals(0, histogram.getCountForValue(7, 9));
            assertEquals(2, histogram.getCountForValue(10, 10), "the two earlier values moved with the shift");
        }

        @Test
        @DisplayName("T09 growing in both directions keeps every count")
        void growsBothWays()
        {
            final RuntimeHistogram histogram = histogramOf(1, 10, 10, 5, 5, 20);

            assertEquals(16, histogram.getNumberOfBuckets(), "buckets 5..20");
            assertEquals(5, histogram.getValueCount());
            assertEquals(2, histogram.getCountForValue(5, 5));
            assertEquals(2, histogram.getCountForValue(10, 10));
            assertEquals(1, histogram.getCountForValue(20, 20));
            assertEquals(5, histogram.getCountForValue(0, 100), "nothing was lost on the way");
        }

        @Test
        @DisplayName("T10 the bucket counts always add up to the number of values added")
        void countsAreConserved()
        {
            final Random random = new Random(SEED);
            final RuntimeHistogram histogram = new RuntimeHistogram();

            for (int i = 0; i < 5_000; i++)
            {
                histogram.addValue(random.nextInt(2_000));
            }

            assertEquals(5_000, histogram.getValueCount());
            assertEquals(5_000, histogram.getCountForValue(0, 1_999));
        }

        @Test
        @DisplayName("T11 an ascending and a descending insertion order produce the same histogram")
        void insertionOrderDoesNotMatter()
        {
            final RuntimeHistogram ascending = new RuntimeHistogram();
            final RuntimeHistogram descending = new RuntimeHistogram();

            for (int i = 0; i < 100; i++)
            {
                ascending.addValue(i);
                descending.addValue(99 - i);
            }

            assertEquals(ascending.getNumberOfBuckets(), descending.getNumberOfBuckets());
            assertEquals(ascending.getValueCount(), descending.getValueCount());
            for (double p : new double[]
            {
                0.0, 25.0, 50.0, 75.0, 100.0
            })
            {
                assertEquals(ascending.getPercentile(p), descending.getPercentile(p), "p" + p);
            }
        }
    }

    @Nested
    @DisplayName("C. Percentiles")
    class Percentiles
    {
        /**
         * The sample used throughout this section, sorted: 3, 3, 5, 7, 9.
         */
        private final int[] sample =
        {
            5, 3, 9, 3, 7
        };

        @ParameterizedTest
        @CsvSource(
        {
            "0.0, 3.0", "1.0, 3.0", "25.0, 3.0", "50.0, 5.0", "60.0, 6.0", "75.0, 7.0", "99.0, 9.0", "100.0, 9.0"
        })
        @DisplayName("T12 the percentiles of a known sample match the empirical quantile definition")
        void knownSample(final double p, final double expected)
        {
            assertEquals(expected, histogramOf(1, sample).getPercentile(p), "p" + p);
        }

        @Test
        @DisplayName("T13 an integral n*p yields the mean of the two neighbouring values")
        void integralRankAveragesTwoValues()
        {
            // 60% of 5 values is exactly 3, so the 3rd (5) and the 4th (7) value are averaged
            assertEquals(6.0, histogramOf(1, sample).getPercentile(60.0));

            // and with two values the median is their mean
            assertEquals(15.0, histogramOf(1, 10, 20).getPercentile(50.0));
        }

        @Test
        @DisplayName("T14 the percentile agrees with a sorted reference sample for random data")
        void agreesWithSortedReference()
        {
            final Random random = new Random(SEED);
            final int[] values = new int[999];
            final RuntimeHistogram histogram = new RuntimeHistogram();

            for (int i = 0; i < values.length; i++)
            {
                values[i] = random.nextInt(500);
                histogram.addValue(values[i]);
            }
            Arrays.sort(values);

            for (double p : new double[]
            {
                1.0, 5.0, 25.0, 33.3, 50.0, 66.6, 75.0, 90.0, 95.0, 99.0
            })
            {
                assertEquals(expectedPercentile(values, p), histogram.getPercentile(p), "p" + p);
            }
        }

        @Test
        @DisplayName("T15 the median and the quantile are just other spellings of the percentile")
        void medianAndQuantileDelegate()
        {
            final RuntimeHistogram histogram = histogramOf(1, sample);

            assertEquals(histogram.getPercentile(50.0), histogram.getMedianValue());
            assertEquals(histogram.getPercentile(50.0), histogram.getQuantile(0.5));
            assertEquals(histogram.getPercentile(95.0), histogram.getQuantile(0.95));
            assertEquals(histogram.getPercentile(0.0), histogram.getQuantile(0.0));
            assertEquals(histogram.getPercentile(100.0), histogram.getQuantile(1.0));
        }

        @Test
        @DisplayName("T16 p0 and p100 report the smallest and the largest bucket")
        void extremePercentiles()
        {
            final RuntimeHistogram histogram = histogramOf(1, 5, 3, 9, 3, 7);

            assertEquals(3.0, histogram.getPercentile(0.0));
            assertEquals(9.0, histogram.getPercentile(100.0));
        }

        @ParameterizedTest
        @ValueSource(doubles =
        {
            -0.1, -1.0, 100.1, 101.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        })
        @DisplayName("T17 a percentile outside 0..100 is rejected")
        void rejectsOutOfRangePercentiles(final double p)
        {
            final RuntimeHistogram histogram = histogramOf(1, 1, 2, 3);

            assertThrows(IllegalArgumentException.class, () -> histogram.getPercentile(p));
        }

        @Test
        @DisplayName("T18 the percentile is monotonic over the whole range")
        void isMonotonic()
        {
            final Random random = new Random(SEED);
            final RuntimeHistogram histogram = new RuntimeHistogram();
            for (int i = 0; i < 1_000; i++)
            {
                histogram.addValue(random.nextInt(1_000));
            }

            double previous = Double.NEGATIVE_INFINITY;
            for (double p = 0.0; p <= 100.0; p += 0.5)
            {
                final double current = histogram.getPercentile(p);
                assertTrue(current >= previous, "p" + p + " (" + current + ") is smaller than the percentile before it (" + previous + ")");
                previous = current;
            }
        }
    }

    @Nested
    @DisplayName("D. Counting values in a range")
    class CountForValue
    {
        @Test
        @DisplayName("T19 a range fully inside the window counts what is in it")
        void insideTheWindow()
        {
            final RuntimeHistogram histogram = histogramOf(1, 1, 2, 2, 3, 4, 5);

            assertEquals(1, histogram.getCountForValue(1, 1));
            assertEquals(2, histogram.getCountForValue(2, 2));
            assertEquals(3, histogram.getCountForValue(1, 2));
            assertEquals(6, histogram.getCountForValue(1, 5));
        }

        @Test
        @DisplayName("T20 a range outside the window counts nothing")
        void outsideTheWindow()
        {
            final RuntimeHistogram histogram = histogramOf(1, 10, 11, 12);

            assertEquals(0, histogram.getCountForValue(0, 9), "completely to the left");
            assertEquals(0, histogram.getCountForValue(13, 100), "completely to the right");
            assertEquals(0, histogram.getCountForValue(-100, -1), "far to the left");
        }

        @Test
        @DisplayName("T21 an overlapping range is clamped to the window")
        void clampsToTheWindow()
        {
            final RuntimeHistogram histogram = histogramOf(1, 10, 11, 12);

            assertEquals(3, histogram.getCountForValue(Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2));
            assertEquals(2, histogram.getCountForValue(0, 11), "left half open");
            assertEquals(2, histogram.getCountForValue(11, 100), "right half open");
        }

        @Test
        @DisplayName("T22 both range ends are inclusive")
        void bothEndsAreInclusive()
        {
            final RuntimeHistogram histogram = histogramOf(1, 1, 2, 3);

            assertEquals(1, histogram.getCountForValue(2, 2));
            assertEquals(2, histogram.getCountForValue(2, 3));
            assertEquals(3, histogram.getCountForValue(1, 3));
        }

        @Test
        @DisplayName("T23 an inverted range is rejected, an empty histogram is not")
        void invertedRange()
        {
            final RuntimeHistogram histogram = histogramOf(1, 1, 2, 3);

            assertThrows(IllegalArgumentException.class, () -> histogram.getCountForValue(3, 2));
            assertThrows(IllegalArgumentException.class, () -> new RuntimeHistogram().getCountForValue(3, 2),
                         "the argument check runs before the empty check");
            assertEquals(0, new RuntimeHistogram().getCountForValue(0, 100));
        }
    }

    @Nested
    @DisplayName("E. Documented quirks - correct by implementation, surprising by contract")
    class Quirks
    {
        @Test
        @DisplayName("T24 a precision of zero or below silently behaves like a precision of one")
        void nonPositivePrecision()
        {
            // nextHighestPowerOfTwo(0) is 0 and numberOfTrailingZeros(0) is 32, so the shift is 32,
            // which Java masks back to 0 for ints - the histogram works, the reported precision lies
            for (final int precision : new int[]
            {
                0, -1, -8
            })
            {
                final RuntimeHistogram histogram = histogramOf(precision, 3, 4);

                assertEquals(1, histogram.getPrecision(), "precision " + precision);
                assertEquals(2, histogram.getNumberOfBuckets(), "values are not grouped at all");
                assertEquals(3.0, histogram.getPercentile(0.0));
            }
        }

        @Test
        @DisplayName("T25 with a precision above one every result is a bucket floor, not a real value")
        void resultsAreBucketFloors()
        {
            final RuntimeHistogram histogram = histogramOf(8, 100, 200, 300);

            assertEquals(96.0, histogram.getPercentile(0.0), "100 is reported as 96, the floor of its bucket");
            assertEquals(200.0, histogram.getPercentile(50.0), "200 happens to be a bucket floor itself");
            assertEquals(296.0, histogram.getPercentile(100.0), "300 is reported as 296 - p100 is not the maximum");
        }

        @Test
        @DisplayName("T26 a range narrower than a bucket returns the whole bucket, so sub ranges overlap")
        void subBucketRangesOverlap()
        {
            final RuntimeHistogram histogram = histogramOf(8, 0, 1, 2, 3, 4, 5, 6, 7);

            // both halves of the bucket report all eight values, so a caller that walks a value
            // range in steps smaller than the precision counts the same values several times
            assertEquals(8, histogram.getCountForValue(0, 3));
            assertEquals(8, histogram.getCountForValue(4, 7));
            assertEquals(8, histogram.getCountForValue(0, 7));
            assertEquals(8, histogram.getCountForValue(3, 4));
        }

        @Test
        @DisplayName("T27 negative values are accepted and cost one bucket per unit of distance")
        void negativeValues()
        {
            final RuntimeHistogram histogram = histogramOf(1, -3, 5);

            assertEquals(9, histogram.getNumberOfBuckets(), "buckets -3..5, all of them allocated");
            assertEquals(-3.0, histogram.getPercentile(0.0));
            assertEquals(5.0, histogram.getPercentile(100.0));
            assertEquals(2, histogram.getCountForValue(-3, 5));

            // the bucket index is an arithmetic shift, so a negative value rounds away from zero
            assertEquals(-8.0, histogramOf(8, -1).getPercentile(0.0), "-1 lands in the bucket that starts at -8");
        }

        @Test
        @DisplayName("T28 a percentile of NaN slips through the range check and reports a value below the minimum")
        void nanPercentile()
        {
            // NaN fails both 'p < 0' and 'p > 100', so the guard lets it pass. The bucket search
            // then never enters its loop and reports the bucket in front of the first one.
            final RuntimeHistogram histogram = histogramOf(1, 5, 6, 7);

            assertEquals(4.0, histogram.getPercentile(Double.NaN), "one below the smallest value that was added");
            assertEquals(4.0, histogram.getQuantile(Double.NaN));
        }

        @Test
        @DisplayName("T29 the memory used depends on the spread of the values, not on their number")
        void memoryFollowsTheSpread()
        {
            final RuntimeHistogram dense = new RuntimeHistogram();
            for (int i = 0; i < 100_000; i++)
            {
                dense.addValue(500 + (i % 10));
            }

            final RuntimeHistogram sparse = histogramOf(1, 0, 100_000);

            assertEquals(10, dense.getNumberOfBuckets(), "100k values, 10 buckets");
            assertEquals(100_001, sparse.getNumberOfBuckets(), "2 values, 100k buckets");
        }
    }
}
