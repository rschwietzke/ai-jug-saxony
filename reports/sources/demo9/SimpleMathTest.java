package org.jugsaxony.demo9;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("SimpleMath Tests")
class SimpleMathTest
{
    @Test
    @DisplayName("Default constructor can be instantiated")
    void testInstantiation()
    {
        final SimpleMath instance = new SimpleMath();
        assertThat(instance).isNotNull();
    }

    @Nested
    @DisplayName("max(int, int) Tests")
    class MaxPrimitiveIntTests
    {
        @Nested
        @DisplayName("Positive Numbers")
        class PositiveNumbers
        {
            @Test
            @DisplayName("first argument is greater (a > b)")
            void firstArgumentGreater()
            {
                assertThat(SimpleMath.max(10, 5)).isEqualTo(10);
                assertThat(SimpleMath.max(100, 1)).isEqualTo(100);
            }

            @Test
            @DisplayName("second argument is greater (a < b)")
            void secondArgumentGreater()
            {
                assertThat(SimpleMath.max(5, 10)).isEqualTo(10);
                assertThat(SimpleMath.max(1, 100)).isEqualTo(100);
            }

            @Test
            @DisplayName("arguments are equal (a == b)")
            void argumentsEqual()
            {
                assertThat(SimpleMath.max(7, 7)).isEqualTo(7);
                assertThat(SimpleMath.max(1, 1)).isEqualTo(1);
            }
        }

        @Nested
        @DisplayName("Negative Numbers")
        class NegativeNumbers
        {
            @Test
            @DisplayName("first argument is greater (closer to zero)")
            void firstArgumentGreater()
            {
                assertThat(SimpleMath.max(-3, -8)).isEqualTo(-3);
                assertThat(SimpleMath.max(-1, -100)).isEqualTo(-1);
            }

            @Test
            @DisplayName("second argument is greater (closer to zero)")
            void secondArgumentGreater()
            {
                assertThat(SimpleMath.max(-8, -3)).isEqualTo(-3);
                assertThat(SimpleMath.max(-100, -1)).isEqualTo(-1);
            }

            @Test
            @DisplayName("arguments are equal (a == b)")
            void argumentsEqual()
            {
                assertThat(SimpleMath.max(-4, -4)).isEqualTo(-4);
                assertThat(SimpleMath.max(-1, -1)).isEqualTo(-1);
            }
        }

        @Nested
        @DisplayName("Mixed Signs")
        class MixedSigns
        {
            @Test
            @DisplayName("positive first, negative second")
            void positiveFirstNegativeSecond()
            {
                assertThat(SimpleMath.max(15, -20)).isEqualTo(15);
                assertThat(SimpleMath.max(1, -1)).isEqualTo(1);
            }

            @Test
            @DisplayName("negative first, positive second")
            void negativeFirstPositiveSecond()
            {
                assertThat(SimpleMath.max(-20, 15)).isEqualTo(15);
                assertThat(SimpleMath.max(-1, 1)).isEqualTo(1);
            }

            @Test
            @DisplayName("equal absolute values")
            void equalAbsoluteValues()
            {
                assertThat(SimpleMath.max(42, -42)).isEqualTo(42);
                assertThat(SimpleMath.max(-42, 42)).isEqualTo(42);
            }
        }

        @Nested
        @DisplayName("Zero Values")
        class ZeroValues
        {
            @Test
            @DisplayName("both arguments are zero")
            void bothZero()
            {
                assertThat(SimpleMath.max(0, 0)).isEqualTo(0);
            }

            @Test
            @DisplayName("zero and positive number")
            void zeroAndPositive()
            {
                assertThat(SimpleMath.max(0, 5)).isEqualTo(5);
                assertThat(SimpleMath.max(5, 0)).isEqualTo(5);
            }

            @Test
            @DisplayName("zero and negative number")
            void zeroAndNegative()
            {
                assertThat(SimpleMath.max(0, -5)).isEqualTo(0);
                assertThat(SimpleMath.max(-5, 0)).isEqualTo(0);
            }
        }

        @Nested
        @DisplayName("Boundary and Extreme Values")
        class BoundaryValues
        {
            @Test
            @DisplayName("Integer.MAX_VALUE and Integer.MIN_VALUE")
            void maxAndMinValue()
            {
                assertThat(SimpleMath.max(Integer.MAX_VALUE, Integer.MIN_VALUE)).isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(Integer.MIN_VALUE, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
            }

            @Test
            @DisplayName("identical boundary values")
            void identicalBoundaries()
            {
                assertThat(SimpleMath.max(Integer.MAX_VALUE, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(Integer.MIN_VALUE, Integer.MIN_VALUE)).isEqualTo(Integer.MIN_VALUE);
            }

            @Test
            @DisplayName("boundary values with zero")
            void boundaryValuesWithZero()
            {
                assertThat(SimpleMath.max(Integer.MAX_VALUE, 0)).isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(0, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(Integer.MIN_VALUE, 0)).isEqualTo(0);
                assertThat(SimpleMath.max(0, Integer.MIN_VALUE)).isEqualTo(0);
            }

            @Test
            @DisplayName("adjacent boundary values (MAX_VALUE - 1, MIN_VALUE + 1)")
            void adjacentBoundaryValues()
            {
                assertThat(SimpleMath.max(Integer.MAX_VALUE, Integer.MAX_VALUE - 1)).isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(Integer.MIN_VALUE, Integer.MIN_VALUE + 1)).isEqualTo(Integer.MIN_VALUE + 1);
                assertThat(SimpleMath.max(Integer.MIN_VALUE + 1, Integer.MIN_VALUE)).isEqualTo(Integer.MIN_VALUE + 1);
            }

            @Test
            @DisplayName("boundary values with opposite sign 1 (verifies no subtraction overflow)")
            void boundaryWithOppositeUnit()
            {
                assertThat(SimpleMath.max(Integer.MAX_VALUE, -1)).isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(-1, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(Integer.MIN_VALUE, 1)).isEqualTo(1);
                assertThat(SimpleMath.max(1, Integer.MIN_VALUE)).isEqualTo(1);
            }
        }

        @Nested
        @DisplayName("Algebraic Properties")
        class AlgebraicProperties
        {
            @ParameterizedTest(name = "max({0}, {1}) == max({1}, {0})")
            @CsvSource({
                "1, 2",
                "10, -5",
                "-20, -30",
                "0, 100",
                "0, -100",
                "2147483647, -2147483648"
            })
            @DisplayName("Commutativity: max(a, b) == max(b, a)")
            void commutativity(final int a, final int b)
            {
                assertThat(SimpleMath.max(a, b)).isEqualTo(SimpleMath.max(b, a));
            }

            @ParameterizedTest(name = "max({0}, {0}) == {0}")
            @CsvSource({
                "0",
                "42",
                "-42",
                "2147483647",
                "-2147483648"
            })
            @DisplayName("Idempotence: max(a, a) == a")
            void idempotence(final int a)
            {
                assertThat(SimpleMath.max(a, a)).isEqualTo(a);
            }

            @ParameterizedTest(name = "max({0}, {1}) matches Math.max")
            @CsvSource({
                "15, 25",
                "-10, -5",
                "-100, 50",
                "0, 0",
                "2147483647, 0",
                "-2147483648, 0"
            })
            @DisplayName("Consistency with java.lang.Math.max(a, b)")
            void consistencyWithMathMax(final int a, final int b)
            {
                assertThat(SimpleMath.max(a, b)).isEqualTo(Math.max(a, b));
            }
        }
    }

    @Nested
    @DisplayName("max(Integer, Integer) Tests")
    class MaxBoxedIntegerTests
    {
        @Nested
        @DisplayName("Numerical Values")
        class NumericalValues
        {
            @Test
            @DisplayName("positive integers: a > b, a < b, a == b")
            void positiveIntegers()
            {
                assertThat(SimpleMath.max(Integer.valueOf(25), Integer.valueOf(10))).isEqualTo(25);
                assertThat(SimpleMath.max(Integer.valueOf(10), Integer.valueOf(25))).isEqualTo(25);
                assertThat(SimpleMath.max(Integer.valueOf(20), Integer.valueOf(20))).isEqualTo(20);
            }

            @Test
            @DisplayName("negative integers: a > b, a < b, a == b")
            void negativeIntegers()
            {
                assertThat(SimpleMath.max(Integer.valueOf(-5), Integer.valueOf(-15))).isEqualTo(-5);
                assertThat(SimpleMath.max(Integer.valueOf(-15), Integer.valueOf(-5))).isEqualTo(-5);
                assertThat(SimpleMath.max(Integer.valueOf(-8), Integer.valueOf(-8))).isEqualTo(-8);
            }

            @Test
            @DisplayName("mixed sign integers")
            void mixedSigns()
            {
                assertThat(SimpleMath.max(Integer.valueOf(10), Integer.valueOf(-10))).isEqualTo(10);
                assertThat(SimpleMath.max(Integer.valueOf(-10), Integer.valueOf(10))).isEqualTo(10);
            }

            @Test
            @DisplayName("zero values")
            void zeroValues()
            {
                assertThat(SimpleMath.max(Integer.valueOf(0), Integer.valueOf(0))).isEqualTo(0);
                assertThat(SimpleMath.max(Integer.valueOf(0), Integer.valueOf(5))).isEqualTo(5);
                assertThat(SimpleMath.max(Integer.valueOf(5), Integer.valueOf(0))).isEqualTo(5);
                assertThat(SimpleMath.max(Integer.valueOf(0), Integer.valueOf(-5))).isEqualTo(0);
                assertThat(SimpleMath.max(Integer.valueOf(-5), Integer.valueOf(0))).isEqualTo(0);
            }

            @Test
            @DisplayName("boundary values (MAX_VALUE, MIN_VALUE)")
            void boundaryValues()
            {
                assertThat(SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MIN_VALUE)))
                    .isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MAX_VALUE)))
                    .isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE)))
                    .isEqualTo(Integer.MAX_VALUE);
                assertThat(SimpleMath.max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MIN_VALUE)))
                    .isEqualTo(Integer.MIN_VALUE);
            }
        }

        @Nested
        @DisplayName("Object Identity vs Value Equality")
        class ObjectIdentity
        {
            @Test
            @DisplayName("distinct Integer instances outside cache [-128, 127] with same value")
            void distinctInstancesOutsideCacheSameValue()
            {
                final Integer a = Integer.valueOf(1000);
                final Integer b = Integer.parseInt(new String("1000"));

                assertThat(SimpleMath.max(a, b)).isEqualTo(1000);
            }

            @Test
            @DisplayName("same instance passed to both parameters")
            void sameInstance()
            {
                final Integer instance = Integer.valueOf(999);
                assertThat(SimpleMath.max(instance, instance)).isEqualTo(999);
            }
        }

        @Nested
        @DisplayName("Null Parameter Handling")
        class NullHandling
        {
            @Test
            @DisplayName("first argument null throws NullPointerException")
            void firstArgumentNullThrowsNpe()
            {
                assertThatNullPointerException()
                    .isThrownBy(() -> SimpleMath.max((Integer) null, Integer.valueOf(5)));
            }

            @Test
            @DisplayName("second argument null throws NullPointerException")
            void secondArgumentNullThrowsNpe()
            {
                assertThatNullPointerException()
                    .isThrownBy(() -> SimpleMath.max(Integer.valueOf(5), (Integer) null));
            }

            @Test
            @DisplayName("both arguments null throws NullPointerException")
            void bothArgumentsNullThrowsNpe()
            {
                assertThatNullPointerException()
                    .isThrownBy(() -> SimpleMath.max((Integer) null, (Integer) null));
            }
        }
    }

    @Nested
    @DisplayName("sum(int[]) Tests")
    class SumTests
    {
        @Nested
        @DisplayName("Empty and Single Element Arrays")
        class EmptyAndSingleElement
        {
            @Test
            @DisplayName("empty array returns 0L")
            void emptyArrayReturnsZero()
            {
                assertThat(SimpleMath.sum(new int[0])).isEqualTo(0L);
            }

            @Test
            @DisplayName("single zero element returns 0L")
            void singleZeroReturnsZero()
            {
                assertThat(SimpleMath.sum(new int[]{0})).isEqualTo(0L);
            }

            @Test
            @DisplayName("single positive element returns its value as long")
            void singlePositive()
            {
                assertThat(SimpleMath.sum(new int[]{42})).isEqualTo(42L);
            }

            @Test
            @DisplayName("single negative element returns its value as long")
            void singleNegative()
            {
                assertThat(SimpleMath.sum(new int[]{-42})).isEqualTo(-42L);
            }

            @Test
            @DisplayName("single Integer.MAX_VALUE element")
            void singleMaxValue()
            {
                assertThat(SimpleMath.sum(new int[]{Integer.MAX_VALUE})).isEqualTo(2147483647L);
            }

            @Test
            @DisplayName("single Integer.MIN_VALUE element")
            void singleMinValue()
            {
                assertThat(SimpleMath.sum(new int[]{Integer.MIN_VALUE})).isEqualTo(-2147483648L);
            }
        }

        @Nested
        @DisplayName("Multiple Elements within 32-bit Integer Range")
        class MultipleElementsStandard
        {
            @Test
            @DisplayName("all positive integers")
            void allPositive()
            {
                assertThat(SimpleMath.sum(new int[]{1, 2, 3, 4, 5})).isEqualTo(15L);
                assertThat(SimpleMath.sum(new int[]{10, 20, 30, 40})).isEqualTo(100L);
            }

            @Test
            @DisplayName("all negative integers")
            void allNegative()
            {
                assertThat(SimpleMath.sum(new int[]{-1, -2, -3, -4, -5})).isEqualTo(-15L);
                assertThat(SimpleMath.sum(new int[]{-10, -20, -30})).isEqualTo(-60L);
            }

            @Test
            @DisplayName("mixed positive and negative summing to zero")
            void mixedSummingToZero()
            {
                assertThat(SimpleMath.sum(new int[]{10, -10, 25, -25})).isEqualTo(0L);
            }

            @Test
            @DisplayName("mixed positive and negative summing to positive")
            void mixedSummingToPositive()
            {
                assertThat(SimpleMath.sum(new int[]{100, -30, 10})).isEqualTo(80L);
            }

            @Test
            @DisplayName("mixed positive and negative summing to negative")
            void mixedSummingToNegative()
            {
                assertThat(SimpleMath.sum(new int[]{-100, 30, -10})).isEqualTo(-80L);
            }

            @Test
            @DisplayName("array containing multiple zeros")
            void arrayContainingZeros()
            {
                assertThat(SimpleMath.sum(new int[]{0, 0, 0})).isEqualTo(0L);
                assertThat(SimpleMath.sum(new int[]{0, 5, 0, -3, 0})).isEqualTo(2L);
            }
        }

        @Nested
        @DisplayName("Large Sums / 32-bit Integer Overflow & Underflow (Contract Expectation for long return)")
        class OverflowAndUnderflow
        {
            @Test
            @DisplayName("sum of two Integer.MAX_VALUE elements should not overflow 32-bit int")
            void twoMaxValuesShouldNotOverflow()
            {
                // Mathematical sum: 2147483647L + 2147483647L = 4294967294L
                // SimpleMath accumulates as 32-bit int, which overflows to -2L
                assertThat(SimpleMath.sum(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}))
                    .isEqualTo(4294967294L);
            }

            @Test
            @DisplayName("sum of Integer.MAX_VALUE and 1 should not overflow into negative")
            void maxValuePlusOneShouldNotOverflow()
            {
                // Mathematical sum: 2147483647L + 1L = 2147483648L
                // SimpleMath accumulates as 32-bit int, overflowing to Integer.MIN_VALUE (-2147483648L)
                assertThat(SimpleMath.sum(new int[]{Integer.MAX_VALUE, 1}))
                    .isEqualTo(2147483648L);
            }

            @Test
            @DisplayName("sum of three large positive integers exceeding Integer.MAX_VALUE")
            void cumulativePositiveOverflow()
            {
                // 1,000,000,000 * 3 = 3,000,000,000L > Integer.MAX_VALUE
                assertThat(SimpleMath.sum(new int[]{1_000_000_000, 1_000_000_000, 1_000_000_000}))
                    .isEqualTo(3_000_000_000L);
            }

            @Test
            @DisplayName("sum of two Integer.MIN_VALUE elements should not underflow 32-bit int")
            void twoMinValuesShouldNotUnderflow()
            {
                // Mathematical sum: -2147483648L + -2147483648L = -4294967296L
                // SimpleMath accumulates as 32-bit int, underflowing to 0L
                assertThat(SimpleMath.sum(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE}))
                    .isEqualTo(-4294967296L);
            }

            @Test
            @DisplayName("sum of Integer.MIN_VALUE and -1 should not underflow into positive")
            void minValueMinusOneShouldNotUnderflow()
            {
                // Mathematical sum: -2147483648L + -1L = -2147483649L
                // SimpleMath accumulates as 32-bit int, underflowing to 2147483647L (Integer.MAX_VALUE)
                assertThat(SimpleMath.sum(new int[]{Integer.MIN_VALUE, -1}))
                    .isEqualTo(-2147483649L);
            }

            @Test
            @DisplayName("sum of three large negative integers below Integer.MIN_VALUE")
            void cumulativeNegativeUnderflow()
            {
                // -1,000,000,000 * 3 = -3,000,000,000L < Integer.MIN_VALUE
                assertThat(SimpleMath.sum(new int[]{-1_000_000_000, -1_000_000_000, -1_000_000_000}))
                    .isEqualTo(-3_000_000_000L);
            }

            @Test
            @DisplayName("intermediate overflow should not corrupt calculation even if cancelling out")
            void intermediateOverflowCancellation()
            {
                // MAX_VALUE + 10 - 10 mathematically equals MAX_VALUE
                assertThat(SimpleMath.sum(new int[]{Integer.MAX_VALUE, 10, -10}))
                    .isEqualTo((long) Integer.MAX_VALUE);
            }
        }

        @Nested
        @DisplayName("Array Mutability and Null Handling")
        class ArrayIntegrityAndNull
        {
            @Test
            @DisplayName("sum does not modify the input array")
            void inputNotModified()
            {
                final int[] original = new int[]{5, -3, 42, 0};
                final int[] copy = original.clone();

                SimpleMath.sum(original);

                assertThat(original).containsExactly(copy);
            }

            @Test
            @DisplayName("sum(null) throws NullPointerException")
            void nullArrayThrowsNullPointerException()
            {
                assertThatNullPointerException()
                    .isThrownBy(() -> SimpleMath.sum(null));
            }
        }
    }
}
