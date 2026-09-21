package org.jugsaxony.demo6;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The shared test suite for the two hand-written math helpers, {@code SimpleMath} and
 * {@code SimpleMathClean}. Both expose the same three static methods and, apart from formatting and
 * Javadoc, the same implementation, so they have to satisfy the same contract. This class holds that
 * contract once; a small subclass per helper binds it to the actual class under test.
 *
 * <p><b>The contract the tests assume.</b> Neither class documents edge cases, so the suite assumes the
 * most reasonable reading of the signatures:
 *
 * <ul>
 *   <li>{@code int max(int, int)} returns the larger of the two arguments for every pair of ints,
 *       including the extremes. It never overflows, because a comparison cannot overflow - the classic
 *       {@code (a - b) > 0} mistake is explicitly probed with {@code MIN_VALUE} against small positive
 *       values.</li>
 *   <li>{@code int max(Integer, Integer)} behaves like the primitive overload for the values the
 *       arguments carry. Comparison is by value, never by reference, so two distinct boxes holding
 *       equal values are equal for this purpose. Since the method unboxes both arguments and returns a
 *       primitive, {@code null} cannot be given a meaning, and the expected reaction is a
 *       {@link NullPointerException}.</li>
 *   <li>{@code long sum(int[])} returns the mathematically exact total of the array. The declared
 *       return type is the contract here: a method that returns {@code long} while taking {@code int}
 *       values promises headroom that {@code int} arithmetic does not have, and callers will read it
 *       that way. An empty array sums to zero, a {@code null} array has no total and is expected to
 *       raise a {@link NullPointerException}, and the argument is an input only and must come back
 *       untouched.</li>
 * </ul>
 *
 * <p><b>Groups F and H are expected to fail as the code stands.</b> Group F pins the exact total for
 * arrays whose sum leaves the {@code int} range; the implementations accumulate into an {@code int} and
 * only widen the wrapped result on return, so they answer with the wrap-around value. Group H pins the
 * usual shape of a class that holds nothing but static helpers - final, with a private constructor -
 * which neither class has yet. Both groups are kept separate so the failures are easy to read and easy
 * to drop if the conventions are not wanted.
 *
 * <p>One API observation that cannot be expressed as a test: because the two {@code max} overloads are
 * {@code (int, int)} and {@code (Integer, Integer)}, a mixed call such as {@code max(1, Integer.valueOf(2))}
 * is ambiguous and does not compile. Every call below therefore passes two primitives or two boxes.
 */
abstract class AbstractSimpleMathContract
{
    /**
     * Values worth trying in every combination: the extremes and their neighbours, the edges of the
     * {@link Integer} box cache, zero, and a few ordinary magnitudes. A grid over these covers the sign
     * combinations, the equality case and the overflow-prone spreads in one go.
     */
    private static final int[] INTERESTING = { Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1_000_000_000,
                                               -1_000_000, -129, -128, -127, -1, 0, 1, 42, 127, 128,
                                               1_000_000, 1_000_000_000, Integer.MAX_VALUE - 1,
                                               Integer.MAX_VALUE };

    // ------------------------------------------------------------------------------------- adapters

    /**
     * Calls the primitive {@code max} of the class under test.
     *
     * @param a value one
     * @param b value two
     * @return the larger value of both
     */
    protected abstract int max(final int a, final int b);

    /**
     * Calls the boxed {@code max} of the class under test.
     *
     * @param a value one
     * @param b value two
     * @return the larger value of both
     */
    protected abstract int max(final Integer a, final Integer b);

    /**
     * Calls {@code sum} of the class under test.
     *
     * @param numbers the array to total
     * @return the total
     */
    protected abstract long sum(final int[] numbers);

    /**
     * The class under test, for the reflective checks in group H.
     *
     * @return the class under test
     */
    protected abstract Class<?> subject();

    // -------------------------------------------------------------------------------------- helpers

    /**
     * An independent total that cannot overflow for any {@code int} array, used as the oracle the
     * implementation is compared against. An {@code int[]} has at most {@code 2^31 - 1} elements, each
     * of magnitude below {@code 2^31}, so the exact total always fits into a {@code long}.
     *
     * @param numbers the array to total
     * @return the exact total
     */
    private static long exactSum(final int[] numbers)
    {
        long total = 0;
        for (final int n : numbers)
        {
            total += n;
        }

        return total;
    }

    /**
     * Builds a reversed copy, so the order independence of a total can be checked without touching the
     * original array.
     *
     * @param numbers the array to reverse
     * @return a reversed copy
     */
    private static int[] reversed(final int[] numbers)
    {
        final int[] copy = new int[numbers.length];
        for (int i = 0; i < numbers.length; i++)
        {
            copy[i] = numbers[numbers.length - 1 - i];
        }

        return copy;
    }

    /**
     * Builds an array of {@code count} copies of {@code value}.
     *
     * @param count how many elements
     * @param value the value every element gets
     * @return the filled array
     */
    private static int[] repeat(final int count, final int value)
    {
        final int[] numbers = new int[count];
        Arrays.fill(numbers, value);

        return numbers;
    }

    // ------------------------------------------------------------------------------ argument sources

    /**
     * The explicit truth table for {@code max}, shared by the primitive and the boxed overload so both
     * are held to exactly the same expectations. It walks the sign combinations, both argument orders,
     * the equality case, the immediate neighbours of both extremes and the spreads that break a
     * subtraction-based implementation.
     *
     * @return triples of first value, second value and expected result
     */
    static Stream<Arguments> maxCases()
    {
        return Stream.of(
                         // zero and the small neighbourhood around it
                         arguments(0, 0, 0),
                         arguments(1, 0, 1),
                         arguments(0, 1, 1),
                         arguments(-1, 0, 0),
                         arguments(0, -1, 0),
                         arguments(1, -1, 1),
                         arguments(-1, 1, 1),

                         // both positive, both orders
                         arguments(3, 5, 5),
                         arguments(5, 3, 5),
                         arguments(99, 100, 100),
                         arguments(100, 99, 100),

                         // both negative, both orders - the larger value is the one closer to zero
                         arguments(-3, -5, -3),
                         arguments(-5, -3, -3),
                         arguments(-100, -99, -99),
                         arguments(-99, -100, -99),

                         // mixed signs, both orders
                         arguments(-5, 5, 5),
                         arguments(5, -5, 5),
                         arguments(-1_000_000, 1, 1),
                         arguments(1, -1_000_000, 1),

                         // equal arguments have to come back unchanged, whatever the sign
                         arguments(7, 7, 7),
                         arguments(-7, -7, -7),
                         arguments(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
                         arguments(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),

                         // the extremes against zero
                         arguments(Integer.MAX_VALUE, 0, Integer.MAX_VALUE),
                         arguments(0, Integer.MAX_VALUE, Integer.MAX_VALUE),
                         arguments(Integer.MIN_VALUE, 0, 0),
                         arguments(0, Integer.MIN_VALUE, 0),

                         // adjacent values at both ends, where an off-by-one comparison shows up
                         arguments(Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE),
                         arguments(Integer.MAX_VALUE, Integer.MAX_VALUE - 1, Integer.MAX_VALUE),
                         arguments(Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1),
                         arguments(Integer.MIN_VALUE + 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1),

                         // spreads whose difference does not fit into an int - a comparison must not
                         // be replaced by a subtraction
                         arguments(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
                         arguments(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE),
                         arguments(Integer.MIN_VALUE, 1, 1),
                         arguments(1, Integer.MIN_VALUE, 1),
                         arguments(Integer.MAX_VALUE, -1, Integer.MAX_VALUE),
                         arguments(-1, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    /**
     * Arrays whose exact total still fits into an {@code int}, so the accumulator width cannot matter.
     *
     * @return triples of description, input array and expected total
     */
    static Stream<Arguments> sumCasesInsideIntRange()
    {
        return Stream.of(
                         arguments("an empty array", new int[] {}, 0L),
                         arguments("a single zero", new int[] { 0 }, 0L),
                         arguments("a single positive value", new int[] { 42 }, 42L),
                         arguments("a single negative value", new int[] { -42 }, -42L),
                         arguments("a single MAX_VALUE", new int[] { Integer.MAX_VALUE }, 2147483647L),
                         arguments("a single MIN_VALUE", new int[] { Integer.MIN_VALUE }, -2147483648L),
                         arguments("nothing but zeros", new int[] { 0, 0, 0, 0, 0 }, 0L),
                         arguments("two positive values", new int[] { 20, 22 }, 42L),
                         arguments("only positive values", new int[] { 1, 2, 3, 4, 5 }, 15L),
                         arguments("only negative values", new int[] { -1, -2, -3, -4, -5 }, -15L),
                         arguments("mixed signs, positive total", new int[] { 10, -3, 5, -2 }, 10L),
                         arguments("mixed signs, negative total", new int[] { -10, 3, -5, 2 }, -10L),
                         arguments("mixed signs cancelling out", new int[] { 7, -7, 13, -13 }, 0L),
                         arguments("MIN_VALUE and MAX_VALUE", new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE }, -1L),
                         arguments("MAX_VALUE and MIN_VALUE", new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE }, -1L),
                         arguments("MAX_VALUE reached from below", new int[] { Integer.MAX_VALUE - 1, 1 }, 2147483647L),
                         arguments("MIN_VALUE reached from above", new int[] { Integer.MIN_VALUE + 1, -1 }, -2147483648L),

                         // these two leave the int range in between and come back - the wrapped
                         // intermediate cancels out, so even an int accumulator lands on the exact total
                         arguments("a total that leaves and re-enters the int range",
                                   new int[] { Integer.MAX_VALUE, 1, -1 }, 2147483647L),
                         arguments("MAX_VALUE twice, then taken away again",
                                   new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE, -Integer.MAX_VALUE },
                                   2147483647L));
    }

    /**
     * Arrays whose exact total does not fit into an {@code int} any more, while it fits comfortably into
     * the declared {@code long} return type.
     *
     * @return triples of description, input array and expected total
     */
    static Stream<Arguments> sumCasesBeyondIntRange()
    {
        return Stream.of(
                         arguments("one past MAX_VALUE", new int[] { Integer.MAX_VALUE, 1 }, 2147483648L),
                         arguments("one past MAX_VALUE, other order", new int[] { 1, Integer.MAX_VALUE }, 2147483648L),
                         arguments("one below MIN_VALUE", new int[] { Integer.MIN_VALUE, -1 }, -2147483649L),
                         arguments("one below MIN_VALUE, other order", new int[] { -1, Integer.MIN_VALUE }, -2147483649L),
                         arguments("MAX_VALUE twice", new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE }, 4294967294L),
                         arguments("MAX_VALUE three times",
                                   new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE }, 6442450941L),
                         arguments("MIN_VALUE twice", new int[] { Integer.MIN_VALUE, Integer.MIN_VALUE }, -4294967296L),
                         arguments("MIN_VALUE three times",
                                   new int[] { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE }, -6442450944L),
                         arguments("two billion twice", new int[] { 2_000_000_000, 2_000_000_000 }, 4_000_000_000L),
                         arguments("minus two billion twice", new int[] { -2_000_000_000, -2_000_000_000 },
                                   -4_000_000_000L),
                         arguments("a thousand times ten million", repeat(1_000, 10_000_000), 10_000_000_000L),
                         arguments("a million times five thousand", repeat(1_000_000, 5_000), 5_000_000_000L));
    }

    // ------------------------------------------------------------------------- A. max(int, int)

    @Nested
    @DisplayName("A. max(int, int)")
    class MaxPrimitive
    {
        @ParameterizedTest(name = "T01 max({0}, {1}) == {2}")
        @MethodSource("org.jugsaxony.demo6.AbstractSimpleMathContract#maxCases")
        @DisplayName("T01 the truth table")
        void truthTable(final int a, final int b, final int expected)
        {
            assertEquals(expected, max(a, b));
        }

        @Test
        @DisplayName("T02 matches Math.max for every combination of interesting values")
        void matchesMathMax()
        {
            for (final int a : INTERESTING)
            {
                for (final int b : INTERESTING)
                {
                    assertEquals(Math.max(a, b), max(a, b), () -> "max(" + a + ", " + b + ")");
                }
            }
        }

        @Test
        @DisplayName("T03 the argument order does not change the result")
        void isSymmetric()
        {
            for (final int a : INTERESTING)
            {
                for (final int b : INTERESTING)
                {
                    assertEquals(max(a, b), max(b, a), () -> "max is not symmetric for " + a + " and " + b);
                }
            }
        }

        @ParameterizedTest(name = "T04 max({0}, {0}) == {0}")
        @ValueSource(ints = { Integer.MIN_VALUE, -1_000_000, -1, 0, 1, 42, 1_000_000, Integer.MAX_VALUE })
        @DisplayName("T04 two equal arguments come back unchanged")
        void isIdempotent(final int value)
        {
            assertEquals(value, max(value, value));
        }

        @Test
        @DisplayName("T05 the result is always one of the two arguments and never smaller than either")
        void resultIsAnUpperBoundAndOneOfTheArguments()
        {
            for (final int a : INTERESTING)
            {
                for (final int b : INTERESTING)
                {
                    final int result = max(a, b);

                    assertTrue(result == a || result == b,
                               () -> "max(" + a + ", " + b + ") returned " + result + ", which is neither argument");
                    assertTrue(result >= a && result >= b,
                               () -> "max(" + a + ", " + b + ") returned " + result + ", which is not an upper bound");
                }
            }
        }

        @Test
        @DisplayName("T06 a spread wider than the int range is compared correctly")
        void survivesSpreadsWiderThanTheIntRange()
        {
            // the difference of each pair does not fit into an int, so these only pass if the
            // implementation compares instead of subtracting
            assertEquals(Integer.MAX_VALUE, max(Integer.MIN_VALUE, Integer.MAX_VALUE));
            assertEquals(Integer.MAX_VALUE, max(Integer.MAX_VALUE, Integer.MIN_VALUE));
            assertEquals(1, max(Integer.MIN_VALUE, 1));
            assertEquals(0, max(Integer.MIN_VALUE, 0));
            assertEquals(-1, max(Integer.MIN_VALUE, -1));
            assertEquals(Integer.MAX_VALUE, max(Integer.MAX_VALUE, -1));
            assertEquals(Integer.MAX_VALUE, max(-1, Integer.MAX_VALUE));
        }
    }

    // --------------------------------------------------------------------- B. max(Integer, Integer)

    @Nested
    @DisplayName("B. max(Integer, Integer)")
    class MaxBoxed
    {
        @ParameterizedTest(name = "T07 max(Integer {0}, Integer {1}) == {2}")
        @MethodSource("org.jugsaxony.demo6.AbstractSimpleMathContract#maxCases")
        @DisplayName("T07 the same truth table as the primitive overload")
        void truthTable(final int a, final int b, final int expected)
        {
            assertEquals(expected, max(Integer.valueOf(a), Integer.valueOf(b)));
        }

        @Test
        @DisplayName("T08 matches Math.max for every combination of interesting values")
        void matchesMathMax()
        {
            for (final int a : INTERESTING)
            {
                for (final int b : INTERESTING)
                {
                    assertEquals(Math.max(a, b), max(Integer.valueOf(a), Integer.valueOf(b)),
                                 () -> "max(Integer " + a + ", Integer " + b + ")");
                }
            }
        }

        @Test
        @DisplayName("T09 the argument order does not change the result")
        void isSymmetric()
        {
            for (final int a : INTERESTING)
            {
                for (final int b : INTERESTING)
                {
                    final Integer boxedA = Integer.valueOf(a);
                    final Integer boxedB = Integer.valueOf(b);

                    assertEquals(max(boxedA, boxedB), max(boxedB, boxedA),
                                 () -> "max is not symmetric for " + a + " and " + b);
                }
            }
        }

        @Test
        @DisplayName("T10 two distinct boxes holding equal values are treated as equal")
        void comparesByValueNotByReference()
        {
            // far outside any plausible Integer cache, so these really are two objects
            final Integer first = Integer.valueOf(1_000_000);
            final Integer second = Integer.valueOf(1_000_000);
            assertNotSame(first, second, "the test needs two different instances");

            assertEquals(1_000_000, max(first, second));
            assertEquals(1_000_000, max(second, first));
        }

        @Test
        @DisplayName("T11 distinct boxes are ordered by their value, not by identity")
        void ordersDistinctBoxesByValue()
        {
            final Integer smaller = Integer.valueOf(1_000_000);
            final Integer larger = Integer.valueOf(1_000_001);
            assertNotSame(smaller, larger, "the test needs two different instances");

            assertEquals(1_000_001, max(smaller, larger));
            assertEquals(1_000_001, max(larger, smaller));

            final Integer negativeSmaller = Integer.valueOf(-1_000_001);
            final Integer negativeLarger = Integer.valueOf(-1_000_000);

            assertEquals(-1_000_000, max(negativeSmaller, negativeLarger));
            assertEquals(-1_000_000, max(negativeLarger, negativeSmaller));
        }

        @Test
        @DisplayName("T12 cached boxes, where both arguments are the very same object, work as well")
        void handlesSharedCachedInstances()
        {
            // the JLS guarantees -128..127 are cached, so these are the same object
            final Integer first = Integer.valueOf(127);
            final Integer second = Integer.valueOf(127);
            assertSame(first, second, "the test needs the cached instance");

            assertEquals(127, max(first, second));

            // just outside the guaranteed cache in both directions
            assertEquals(128, max(Integer.valueOf(128), Integer.valueOf(127)));
            assertEquals(-128, max(Integer.valueOf(-128), Integer.valueOf(-129)));
        }

        @Test
        @DisplayName("T13 the extremes survive the trip through the box")
        void handlesBoxedExtremes()
        {
            assertEquals(Integer.MAX_VALUE, max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MIN_VALUE)));
            assertEquals(Integer.MAX_VALUE, max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MAX_VALUE)));
            assertEquals(Integer.MIN_VALUE, max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MIN_VALUE)));
            assertEquals(Integer.MAX_VALUE, max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE)));
        }
    }

    // ----------------------------------------------------------------------- C. Overload consistency

    @Nested
    @DisplayName("C. Overload consistency")
    class OverloadConsistency
    {
        @Test
        @DisplayName("T14 both overloads answer the same for the same values")
        void bothOverloadsAgree()
        {
            for (final int a : INTERESTING)
            {
                for (final int b : INTERESTING)
                {
                    assertEquals(max(a, b), max(Integer.valueOf(a), Integer.valueOf(b)),
                                 () -> "the overloads disagree for " + a + " and " + b);
                }
            }
        }

        @Test
        @DisplayName("T15 both overloads agree with Math.max on random pairs")
        void bothOverloadsAgreeOnRandomPairs()
        {
            final Random random = new Random(20260921L);

            for (int round = 0; round < 10_000; round++)
            {
                final int a = random.nextInt();
                final int b = random.nextInt();
                final int expected = Math.max(a, b);

                assertEquals(expected, max(a, b), () -> "max(int " + a + ", int " + b + ")");
                assertEquals(expected, max(Integer.valueOf(a), Integer.valueOf(b)),
                             () -> "max(Integer " + a + ", Integer " + b + ")");
            }
        }
    }

    // ------------------------------------------------------------------------------ D. Null handling

    @Nested
    @DisplayName("D. Null handling")
    class NullHandling
    {
        @Test
        @DisplayName("T16 a null first argument of the boxed max is rejected")
        void boxedMaxRejectsNullFirstArgument()
        {
            final Integer missing = null;
            final Integer present = Integer.valueOf(1);

            assertThrows(NullPointerException.class, () -> max(missing, present));
        }

        @Test
        @DisplayName("T17 a null second argument of the boxed max is rejected")
        void boxedMaxRejectsNullSecondArgument()
        {
            final Integer present = Integer.valueOf(1);
            final Integer missing = null;

            assertThrows(NullPointerException.class, () -> max(present, missing));
        }

        @Test
        @DisplayName("T18 two null arguments of the boxed max are rejected")
        void boxedMaxRejectsTwoNullArguments()
        {
            final Integer missing = null;

            assertThrows(NullPointerException.class, () -> max(missing, missing));
        }

        @Test
        @DisplayName("T19 a null array has no total and is rejected")
        void sumRejectsNull()
        {
            final int[] missing = null;

            assertThrows(NullPointerException.class, () -> sum(missing));
        }
    }

    // ------------------------------------------------------------ E. sum(int[]) inside the int range

    /**
     * Totals that still fit into an {@code int}. Two of the cases below deliberately step outside the
     * {@code int} range in the middle of the array and come back, and they still have to produce the
     * exact total: {@code int} addition is exact arithmetic modulo {@code 2^32}, so as long as the final
     * total fits into an {@code int} it is congruent to - and therefore equal to - the exact total, no
     * matter what happened on the way. That is why the accumulator width cannot be observed in this
     * group, and why the defect is confined to group F.
     */
    @Nested
    @DisplayName("E. sum(int[]) inside the int range")
    class SumInsideIntRange
    {
        @ParameterizedTest(name = "T20 {0} sums to {2}")
        @MethodSource("org.jugsaxony.demo6.AbstractSimpleMathContract#sumCasesInsideIntRange")
        @DisplayName("T20 the truth table")
        void truthTable(final String description, final int[] numbers, final long expected)
        {
            assertEquals(expected, sum(numbers), description);
        }

        @ParameterizedTest(name = "T21 an array of one element {0}")
        @ValueSource(ints = { Integer.MIN_VALUE, -1_000_000, -1, 0, 1, 42, 1_000_000, Integer.MAX_VALUE })
        @DisplayName("T21 a single element array sums to that element")
        void singleElement(final int value)
        {
            assertEquals(value, sum(new int[] { value }));
        }

        @Test
        @DisplayName("T22 a long array of small values is totalled correctly")
        void longArrayOfSmallValues()
        {
            assertEquals(1_000_000L, sum(repeat(1_000_000, 1)));
            assertEquals(-1_000_000L, sum(repeat(1_000_000, -1)));
            assertEquals(0L, sum(repeat(1_000_000, 0)));
        }

        @Test
        @DisplayName("T23 the order of the elements does not change the total")
        void orderDoesNotMatter()
        {
            final int[] numbers = { 5, -3, 17, 0, -1_000, 999, -42, 7 };

            assertEquals(sum(numbers), sum(reversed(numbers)));
            assertEquals(-17L, sum(numbers));
        }

        @Test
        @DisplayName("T24 matches the exact total for random input that stays inside the int range")
        void matchesExactTotalForRandomInput()
        {
            final Random random = new Random(20260921L);

            for (int round = 0; round < 1_000; round++)
            {
                // at most 50 elements of magnitude at most a million, so the total cannot leave the
                // int range and the accumulator width cannot matter here
                final int[] numbers = new int[random.nextInt(50)];
                for (int i = 0; i < numbers.length; i++)
                {
                    numbers[i] = random.nextInt(2_000_001) - 1_000_000;
                }

                assertEquals(exactSum(numbers), sum(numbers), () -> "failed for " + Arrays.toString(numbers));
            }
        }
    }

    // ------------------------------------------------------------ F. sum(int[]) beyond the int range

    /**
     * The group that holds {@code sum} to its declared {@code long} return type. Every case here has an
     * exact total that fits into a {@code long} but not into an {@code int}, which is precisely the range
     * a {@code long} return value promises to cover.
     */
    @Nested
    @DisplayName("F. sum(int[]) beyond the int range")
    class SumBeyondIntRange
    {
        @ParameterizedTest(name = "T25 {0} sums to {2}")
        @MethodSource("org.jugsaxony.demo6.AbstractSimpleMathContract#sumCasesBeyondIntRange")
        @DisplayName("T25 the truth table")
        void truthTable(final String description, final int[] numbers, final long expected)
        {
            assertEquals(expected, sum(numbers), description);
        }

        @Test
        @DisplayName("T26 a total above MAX_VALUE is not wrapped around")
        void doesNotWrapAboveMaxValue()
        {
            final long total = sum(new int[] { Integer.MAX_VALUE, 1 });

            assertTrue(total > Integer.MAX_VALUE,
                       () -> "a total of MAX_VALUE + 1 must be above MAX_VALUE, but was " + total);
            assertEquals(2147483648L, total);
        }

        @Test
        @DisplayName("T27 a total below MIN_VALUE is not wrapped around")
        void doesNotWrapBelowMinValue()
        {
            final long total = sum(new int[] { Integer.MIN_VALUE, -1 });

            assertTrue(total < Integer.MIN_VALUE,
                       () -> "a total of MIN_VALUE - 1 must be below MIN_VALUE, but was " + total);
            assertEquals(-2147483649L, total);
        }

        @Test
        @DisplayName("T28 the sign of the total is not flipped by a large positive total")
        void keepsTheSignOfALargePositiveTotal()
        {
            final long total = sum(repeat(100, 1_000_000_000));

            assertTrue(total > 0, () -> "a total of a hundred billion must be positive, but was " + total);
            assertEquals(100_000_000_000L, total);
        }

        @Test
        @DisplayName("T29 the sign of the total is not flipped by a large negative total")
        void keepsTheSignOfALargeNegativeTotal()
        {
            final long total = sum(repeat(100, -1_000_000_000));

            assertTrue(total < 0, () -> "a total of minus a hundred billion must be negative, but was " + total);
            assertEquals(-100_000_000_000L, total);
        }

        @Test
        @DisplayName("T30 matches the exact total for random input that leaves the int range")
        void matchesExactTotalForRandomInput()
        {
            final Random random = new Random(20260921L);

            for (int round = 0; round < 100; round++)
            {
                // a hundred values of at least a billion each, so the total is far outside the int range
                final int[] numbers = new int[100];
                for (int i = 0; i < numbers.length; i++)
                {
                    numbers[i] = 1_000_000_000 + random.nextInt(1_000_000_000);
                }

                assertEquals(exactSum(numbers), sum(numbers), "the exact total of a hundred large values");
            }
        }

        @Test
        @DisplayName("T31 the declared return type is long, which is what the cases above rely on")
        void returnTypeIsLong() throws Exception
        {
            final Method method = subject().getMethod("sum", int[].class);

            assertEquals(long.class, method.getReturnType(),
                         "sum is declared to return long, so it promises room beyond the int range");
        }
    }

    // --------------------------------------------------------------------------- G. No side effects

    @Nested
    @DisplayName("G. No side effects")
    class NoSideEffects
    {
        @Test
        @DisplayName("T32 the input array is not modified")
        void doesNotModifyTheInput()
        {
            final int[] numbers = { 3, -1, 0, Integer.MAX_VALUE, Integer.MIN_VALUE, 17 };
            final int[] untouched = numbers.clone();

            sum(numbers);

            assertArrayEquals(untouched, numbers, "sum takes an input, it must not rewrite it");
        }

        @Test
        @DisplayName("T33 repeated calls on the same array give the same total")
        void isRepeatable()
        {
            final int[] numbers = { 7, -13, 1_000_000, -999_999 };

            final long first = sum(numbers);

            assertEquals(first, sum(numbers));
            assertEquals(first, sum(numbers));
            assertEquals(-5L, first);
        }

        @Test
        @DisplayName("T34 an empty array stays the neutral element for any total")
        void emptyArrayIsNeutral()
        {
            final int[] empty = new int[0];

            assertEquals(0L, sum(empty));
            assertEquals(0L, sum(empty), "a second call must not accumulate anything");
            assertEquals(0, empty.length);
        }
    }

    // ------------------------------------------------------------------------ H. Utility class shape

    /**
     * Conventions rather than behaviour: a class that holds nothing but static helpers is normally
     * {@code final} and not instantiable, so nobody can subclass it or create a pointless instance.
     * These checks are kept in their own group because they are about the shape of the class, not about
     * what the methods compute.
     */
    @Nested
    @DisplayName("H. Utility class shape")
    class UtilityClassShape
    {
        @Test
        @DisplayName("T35 a class of static helpers is final")
        void isFinal()
        {
            assertTrue(Modifier.isFinal(subject().getModifiers()),
                       () -> subject().getSimpleName() + " holds only static helpers and should be final");
        }

        @Test
        @DisplayName("T36 a class of static helpers cannot be instantiated")
        void isNotInstantiable()
        {
            final Constructor<?>[] constructors = subject().getDeclaredConstructors();

            assertEquals(1, constructors.length,
                         () -> subject().getSimpleName() + " should declare exactly one constructor");
            assertTrue(Modifier.isPrivate(constructors[0].getModifiers()),
                       () -> subject().getSimpleName()
                             + " should hide its constructor, an instance of it would be useless");
        }

        @Test
        @DisplayName("T37 every public method is static")
        void allPublicMethodsAreStatic()
        {
            for (final Method method : subject().getDeclaredMethods())
            {
                if (method.isSynthetic() || !Modifier.isPublic(method.getModifiers()))
                {
                    continue;
                }

                assertTrue(Modifier.isStatic(method.getModifiers()),
                           () -> method.getName() + " is public but not static");
            }
        }

        @Test
        @DisplayName("T38 the expected helpers are there with the expected signatures")
        void exposesTheExpectedApi() throws Exception
        {
            final Method maxPrimitive = subject().getMethod("max", int.class, int.class);
            final Method maxBoxed = subject().getMethod("max", Integer.class, Integer.class);
            final Method sum = subject().getMethod("sum", int[].class);

            assertEquals(int.class, maxPrimitive.getReturnType());
            assertEquals(int.class, maxBoxed.getReturnType());
            assertEquals(long.class, sum.getReturnType());

            assertTrue(Modifier.isStatic(maxPrimitive.getModifiers()));
            assertTrue(Modifier.isStatic(maxBoxed.getModifiers()));
            assertTrue(Modifier.isStatic(sum.getModifiers()));
        }
    }
}
