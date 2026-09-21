package org.jugsaxony.demo1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("SimpleMathClean Tests")
class SimpleMathCleanTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor can be instantiated")
        void testInstantiation() {
            SimpleMathClean mathClean = new SimpleMathClean();
            assertThat(mathClean).isNotNull();
        }
    }

    @Nested
    @DisplayName("max(int, int) Tests")
    class MaxPrimitiveIntTests {

        @Test
        @DisplayName("First argument is greater than second (positive numbers)")
        void testFirstArgumentGreaterPositive() {
            assertThat(SimpleMathClean.max(10, 5)).isEqualTo(10);
            assertThat(SimpleMathClean.max(100, 99)).isEqualTo(100);
            assertThat(SimpleMathClean.max(2, 1)).isEqualTo(2);
        }

        @Test
        @DisplayName("Second argument is greater than first (positive numbers)")
        void testSecondArgumentGreaterPositive() {
            assertThat(SimpleMathClean.max(5, 10)).isEqualTo(10);
            assertThat(SimpleMathClean.max(99, 100)).isEqualTo(100);
            assertThat(SimpleMathClean.max(1, 2)).isEqualTo(2);
        }

        @Test
        @DisplayName("Both arguments are equal (positive numbers)")
        void testArgumentsEqualPositive() {
            assertThat(SimpleMathClean.max(42, 42)).isEqualTo(42);
            assertThat(SimpleMathClean.max(1, 1)).isEqualTo(1);
        }

        @Test
        @DisplayName("Both arguments are zero")
        void testBothZero() {
            assertThat(SimpleMathClean.max(0, 0)).isZero();
        }

        @Test
        @DisplayName("Negative numbers comparisons")
        void testNegativeNumbers() {
            // -2 is greater than -5
            assertThat(SimpleMathClean.max(-2, -5)).isEqualTo(-2);
            assertThat(SimpleMathClean.max(-5, -2)).isEqualTo(-2);
            assertThat(SimpleMathClean.max(-10, -10)).isEqualTo(-10);
            assertThat(SimpleMathClean.max(-100, -50)).isEqualTo(-50);
            assertThat(SimpleMathClean.max(-50, -100)).isEqualTo(-50);
        }

        @Test
        @DisplayName("Mixed signs comparisons (positive and negative)")
        void testMixedSigns() {
            assertThat(SimpleMathClean.max(10, -10)).isEqualTo(10);
            assertThat(SimpleMathClean.max(-10, 10)).isEqualTo(10);
            assertThat(SimpleMathClean.max(1, -100)).isEqualTo(1);
            assertThat(SimpleMathClean.max(-100, 1)).isEqualTo(1);
        }

        @Test
        @DisplayName("Zero with positive and negative numbers")
        void testZeroWithPositiveAndNegative() {
            assertThat(SimpleMathClean.max(0, 5)).isEqualTo(5);
            assertThat(SimpleMathClean.max(5, 0)).isEqualTo(5);
            assertThat(SimpleMathClean.max(0, -5)).isZero();
            assertThat(SimpleMathClean.max(-5, 0)).isZero();
        }

        @Test
        @DisplayName("Boundary values (Integer.MAX_VALUE and Integer.MIN_VALUE)")
        void testBoundaryValues() {
            assertThat(SimpleMathClean.max(Integer.MAX_VALUE, Integer.MIN_VALUE)).isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(Integer.MIN_VALUE, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(Integer.MAX_VALUE, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(Integer.MIN_VALUE, Integer.MIN_VALUE)).isEqualTo(Integer.MIN_VALUE);

            assertThat(SimpleMathClean.max(Integer.MAX_VALUE, 0)).isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(0, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(Integer.MIN_VALUE, 0)).isZero();
            assertThat(SimpleMathClean.max(0, Integer.MIN_VALUE)).isZero();

            assertThat(SimpleMathClean.max(Integer.MAX_VALUE, -1)).isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(-1, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(Integer.MIN_VALUE, 1)).isEqualTo(1);
            assertThat(SimpleMathClean.max(1, Integer.MIN_VALUE)).isEqualTo(1);

            assertThat(SimpleMathClean.max(Integer.MAX_VALUE, Integer.MAX_VALUE - 1)).isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(Integer.MIN_VALUE, Integer.MIN_VALUE + 1)).isEqualTo(Integer.MIN_VALUE + 1);
            assertThat(SimpleMathClean.max(Integer.MIN_VALUE + 1, Integer.MIN_VALUE)).isEqualTo(Integer.MIN_VALUE + 1);
        }

        @ParameterizedTest(name = "max({0}, {1}) should equal Math.max({0}, {1}) -> {2}")
        @CsvSource({
                "0, 0, 0",
                "1, 2, 2",
                "2, 1, 2",
                "-1, -2, -1",
                "-2, -1, -1",
                "-1, 1, 1",
                "1, -1, 1",
                "2147483647, -2147483648, 2147483647",
                "-2147483648, 2147483647, 2147483647",
                "2147483647, 0, 2147483647",
                "-2147483648, 0, 0"
        })
        @DisplayName("Parameterized comparison with Math.max")
        void testParameterizedMax(int a, int b, int expected) {
            assertThat(SimpleMathClean.max(a, b)).isEqualTo(expected).isEqualTo(Math.max(a, b));
        }
    }

    @Nested
    @DisplayName("max(Integer, Integer) Tests")
    class MaxBoxedIntegerTests {

        @Test
        @DisplayName("First argument greater with boxed Integers")
        void testFirstArgumentGreater() {
            assertThat(SimpleMathClean.max(Integer.valueOf(10), Integer.valueOf(5))).isEqualTo(10);
            assertThat(SimpleMathClean.max(Integer.valueOf(100), Integer.valueOf(99))).isEqualTo(100);
        }

        @Test
        @DisplayName("Second argument greater with boxed Integers")
        void testSecondArgumentGreater() {
            assertThat(SimpleMathClean.max(Integer.valueOf(5), Integer.valueOf(10))).isEqualTo(10);
            assertThat(SimpleMathClean.max(Integer.valueOf(99), Integer.valueOf(100))).isEqualTo(100);
        }

        @Test
        @DisplayName("Equal values with boxed Integers")
        void testEqualValues() {
            assertThat(SimpleMathClean.max(Integer.valueOf(42), Integer.valueOf(42))).isEqualTo(42);
            assertThat(SimpleMathClean.max(Integer.valueOf(0), Integer.valueOf(0))).isZero();
            assertThat(SimpleMathClean.max(Integer.valueOf(-42), Integer.valueOf(-42))).isEqualTo(-42);
        }

        @Test
        @DisplayName("Equal values outside Integer cache range (-128 to 127)")
        void testEqualValuesOutsideCache() {
            Integer a = Integer.valueOf(10000);
            Integer b = Integer.valueOf(10000);
            assertThat(SimpleMathClean.max(a, b)).isEqualTo(10000);
        }

        @Test
        @DisplayName("Negative boxed Integers")
        void testNegativeIntegers() {
            assertThat(SimpleMathClean.max(Integer.valueOf(-2), Integer.valueOf(-5))).isEqualTo(-2);
            assertThat(SimpleMathClean.max(Integer.valueOf(-5), Integer.valueOf(-2))).isEqualTo(-2);
        }

        @Test
        @DisplayName("Mixed signs with boxed Integers")
        void testMixedSigns() {
            assertThat(SimpleMathClean.max(Integer.valueOf(10), Integer.valueOf(-10))).isEqualTo(10);
            assertThat(SimpleMathClean.max(Integer.valueOf(-10), Integer.valueOf(10))).isEqualTo(10);
            assertThat(SimpleMathClean.max(Integer.valueOf(0), Integer.valueOf(-10))).isZero();
            assertThat(SimpleMathClean.max(Integer.valueOf(0), Integer.valueOf(10))).isEqualTo(10);
        }

        @Test
        @DisplayName("Boundary values with boxed Integers")
        void testBoundaryValues() {
            assertThat(SimpleMathClean.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MIN_VALUE)))
                    .isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MAX_VALUE)))
                    .isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE)))
                    .isEqualTo(Integer.MAX_VALUE);
            assertThat(SimpleMathClean.max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MIN_VALUE)))
                    .isEqualTo(Integer.MIN_VALUE);
        }

        @ParameterizedTest(name = "max(Integer.valueOf({0}), Integer.valueOf({1})) -> {2}")
        @CsvSource({
                "10, 20, 20",
                "20, 10, 20",
                "-30, -50, -30",
                "-50, -30, -30",
                "0, 0, 0",
                "-5, 5, 5",
                "5, -5, 5",
                "2147483647, -2147483648, 2147483647"
        })
        @DisplayName("Parameterized boxed Integer max checks")
        void testParameterizedBoxedMax(int a, int b, int expected) {
            assertThat(SimpleMathClean.max(Integer.valueOf(a), Integer.valueOf(b))).isEqualTo(expected);
        }

        @Test
        @DisplayName("Null handling: first argument null throws NullPointerException")
        void testFirstArgumentNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> SimpleMathClean.max(null, Integer.valueOf(5)));
        }

        @Test
        @DisplayName("Null handling: second argument null throws NullPointerException")
        void testSecondArgumentNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> SimpleMathClean.max(Integer.valueOf(5), null));
        }

        @Test
        @DisplayName("Null handling: both arguments null throws NullPointerException")
        void testBothArgumentsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> SimpleMathClean.max((Integer) null, (Integer) null));
        }
    }

    @Nested
    @DisplayName("sum(int[]) Tests")
    class SumArrayTests {

        @Test
        @DisplayName("Empty array returns 0L")
        void testEmptyArray() {
            assertThat(SimpleMathClean.sum(new int[]{})).isEqualTo(0L);
        }

        @Test
        @DisplayName("Single element array")
        void testSingleElement() {
            assertThat(SimpleMathClean.sum(new int[]{42})).isEqualTo(42L);
            assertThat(SimpleMathClean.sum(new int[]{-42})).isEqualTo(-42L);
            assertThat(SimpleMathClean.sum(new int[]{0})).isEqualTo(0L);
            assertThat(SimpleMathClean.sum(new int[]{Integer.MAX_VALUE})).isEqualTo((long) Integer.MAX_VALUE);
            assertThat(SimpleMathClean.sum(new int[]{Integer.MIN_VALUE})).isEqualTo((long) Integer.MIN_VALUE);
        }

        @Test
        @DisplayName("Multiple positive numbers within int range")
        void testMultiplePositiveNumbers() {
            assertThat(SimpleMathClean.sum(new int[]{1, 2, 3, 4, 5})).isEqualTo(15L);
            assertThat(SimpleMathClean.sum(new int[]{10, 20, 30, 40})).isEqualTo(100L);
            assertThat(SimpleMathClean.sum(new int[]{100, 200, 300})).isEqualTo(600L);
        }

        @Test
        @DisplayName("Multiple negative numbers within int range")
        void testMultipleNegativeNumbers() {
            assertThat(SimpleMathClean.sum(new int[]{-1, -2, -3, -4, -5})).isEqualTo(-15L);
            assertThat(SimpleMathClean.sum(new int[]{-10, -20, -30, -40})).isEqualTo(-100L);
        }

        @Test
        @DisplayName("Mixed positive, negative, and zero numbers")
        void testMixedNumbers() {
            assertThat(SimpleMathClean.sum(new int[]{10, -5, 20, -15, 30, 0})).isEqualTo(40L);
            assertThat(SimpleMathClean.sum(new int[]{100, -100, 50, -50})).isEqualTo(0L);
            assertThat(SimpleMathClean.sum(new int[]{-10, 10, -20, 20, -30, 30})).isEqualTo(0L);
        }

        @Test
        @DisplayName("Array of all zeroes")
        void testAllZeroes() {
            assertThat(SimpleMathClean.sum(new int[]{0, 0, 0, 0, 0})).isEqualTo(0L);
        }

        @Test
        @DisplayName("Null array throws NullPointerException")
        void testNullArray() {
            assertThatNullPointerException()
                    .isThrownBy(() -> SimpleMathClean.sum(null));
        }

        @Test
        @DisplayName("Input array is not modified (immutability)")
        void testArrayNotModified() {
            int[] original = new int[]{3, 1, 4, 1, 5, 9};
            int[] copy = Arrays.copyOf(original, original.length);

            SimpleMathClean.sum(original);

            assertThat(original).containsExactly(copy);
        }

        @Test
        @DisplayName("Sum exceeding Integer.MAX_VALUE (64-bit long accumulator expectation)")
        void testSumOverflowBeyondIntegerMaxValue() {
            // Integer.MAX_VALUE = 2_147_483_647
            // Sum = 2_147_483_648L which requires long accumulator
            int[] numbers = new int[]{Integer.MAX_VALUE, 1};
            long expected = (long) Integer.MAX_VALUE + 1L;
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Sum of two Integer.MAX_VALUE elements")
        void testSumTwoMaxValues() {
            int[] numbers = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE};
            long expected = 2L * Integer.MAX_VALUE;
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Sum of large positive integers exceeding Integer.MAX_VALUE")
        void testSumThreeLargeIntegers() {
            int[] numbers = new int[]{1_000_000_000, 1_000_000_000, 1_000_000_000};
            long expected = 3_000_000_000L;
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Sum below Integer.MIN_VALUE (negative 64-bit underflow expectation)")
        void testSumUnderflowBelowIntegerMinValue() {
            // Integer.MIN_VALUE = -2_147_483_648
            // Sum = -2_147_483_649L
            int[] numbers = new int[]{Integer.MIN_VALUE, -1};
            long expected = (long) Integer.MIN_VALUE - 1L;
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Sum of two Integer.MIN_VALUE elements")
        void testSumTwoMinValues() {
            int[] numbers = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE};
            long expected = 2L * Integer.MIN_VALUE;
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Sum of large negative integers below Integer.MIN_VALUE")
        void testSumThreeLargeNegativeIntegers() {
            int[] numbers = new int[]{-1_000_000_000, -1_000_000_000, -1_000_000_000};
            long expected = -3_000_000_000L;
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Boundary values canceling out: Integer.MAX_VALUE + Integer.MIN_VALUE = -1L")
        void testBoundaryValuesCanceling() {
            int[] numbers = new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE};
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(-1L);
        }

        @Test
        @DisplayName("Boundary values with opposites canceling to 0L")
        void testBoundaryValuesOppositesCanceling() {
            int[] numbers = new int[]{Integer.MAX_VALUE, -Integer.MAX_VALUE, 100, -100};
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(0L);
        }

        @Test
        @DisplayName("Large volume array with 100,000 ones")
        void testLargeArrayOfOnes() {
            int size = 100_000;
            int[] numbers = new int[size];
            Arrays.fill(numbers, 1);
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("Large volume array with 100,000 negative ones")
        void testLargeArrayOfNegativeOnes() {
            int size = 100_000;
            int[] numbers = new int[size];
            Arrays.fill(numbers, -1);
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(-100_000L);
        }

        @Test
        @DisplayName("Large volume array exceeding 32-bit int sum")
        void testLargeArrayExceedingIntMax() {
            int size = 10_000;
            int[] numbers = new int[size];
            Arrays.fill(numbers, 1_000_000);
            // 10,000 * 1,000,000 = 10,000,000,000L
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(10_000_000_000L);
        }

        @Test
        @DisplayName("Large volume array of alternating +1 and -1")
        void testLargeArrayAlternating() {
            int size = 100_000;
            int[] numbers = new int[size];
            for (int i = 0; i < size; i++) {
                numbers[i] = (i % 2 == 0) ? 1 : -1;
            }
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(0L);
        }

        @ParameterizedTest(name = "sum({0}) -> {1}")
        @MethodSource("provideArraysForSum")
        @DisplayName("Parameterized sum validation across assorted patterns")
        void testParameterizedSum(int[] numbers, long expected) {
            assertThat(SimpleMathClean.sum(numbers)).isEqualTo(expected);
        }

        private static Stream<Arguments> provideArraysForSum() {
            return Stream.of(
                    Arguments.of(new int[]{}, 0L),
                    Arguments.of(new int[]{0}, 0L),
                    Arguments.of(new int[]{7}, 7L),
                    Arguments.of(new int[]{-7}, -7L),
                    Arguments.of(new int[]{1, 2, 3}, 6L),
                    Arguments.of(new int[]{-1, -2, -3}, -6L),
                    Arguments.of(new int[]{-10, 10}, 0L),
                    Arguments.of(new int[]{100, 200, 300, 400}, 1000L),
                    Arguments.of(new int[]{Integer.MAX_VALUE}, (long) Integer.MAX_VALUE),
                    Arguments.of(new int[]{Integer.MIN_VALUE}, (long) Integer.MIN_VALUE)
            );
        }
    }
}

