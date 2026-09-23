package org.jugsaxony.demo8;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleMathTest
{
    @Test
    public void testInstantiation()
    {
        SimpleMath sm = new SimpleMath();
        assertNotNull(sm);
    }

    @Nested
    class MaxPrimitiveIntTest
    {
        @ParameterizedTest(name = "max({0}, {1}) = {2}")
        @CsvSource({
            "1, 2, 2",
            "2, 1, 2",
            "0, 0, 0",
            "-1, -2, -1",
            "-2, -1, -1",
            "-5, 5, 5",
            "5, -5, 5",
            "0, 10, 10",
            "10, 0, 10",
            "0, -10, 0",
            "-10, 0, 0",
            "2147483647, 0, 2147483647",
            "0, 2147483647, 2147483647",
            "-2147483648, 0, 0",
            "0, -2147483648, 0",
            "2147483647, -2147483648, 2147483647",
            "-2147483648, 2147483647, 2147483647",
            "2147483647, 2147483647, 2147483647",
            "-2147483648, -2147483648, -2147483648",
            "2147483646, 2147483647, 2147483647",
            "-2147483648, -2147483647, -2147483647"
        })
        public void testMaxPrimitive(int a, int b, int expected)
        {
            assertEquals(expected, SimpleMath.max(a, b));
        }
    }

    @Nested
    class MaxIntegerObjectTest
    {
        @ParameterizedTest(name = "max({0}, {1}) = {2}")
        @MethodSource("provideIntegerArguments")
        public void testMaxInteger(Integer a, Integer b, Integer expected)
        {
            assertEquals(expected, SimpleMath.max(a, b));
        }

        private static Stream<Arguments> provideIntegerArguments()
        {
            return Stream.of(
                Arguments.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(2)),
                Arguments.of(Integer.valueOf(2), Integer.valueOf(1), Integer.valueOf(2)),
                Arguments.of(Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0)),
                Arguments.of(Integer.valueOf(-1), Integer.valueOf(-2), Integer.valueOf(-1)),
                Arguments.of(Integer.valueOf(-2), Integer.valueOf(-1), Integer.valueOf(-1)),
                Arguments.of(Integer.valueOf(-5), Integer.valueOf(5), Integer.valueOf(5)),
                Arguments.of(Integer.valueOf(5), Integer.valueOf(-5), Integer.valueOf(5)),
                Arguments.of(Integer.valueOf(0), Integer.valueOf(10), Integer.valueOf(10)),
                Arguments.of(Integer.valueOf(10), Integer.valueOf(0), Integer.valueOf(10)),
                Arguments.of(Integer.valueOf(0), Integer.valueOf(-10), Integer.valueOf(0)),
                Arguments.of(Integer.valueOf(-10), Integer.valueOf(0), Integer.valueOf(0)),
                Arguments.of(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(0), Integer.valueOf(Integer.MAX_VALUE)),
                Arguments.of(Integer.valueOf(0), Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE)),
                Arguments.of(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(0), Integer.valueOf(0)),
                Arguments.of(Integer.valueOf(0), Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(0)),
                Arguments.of(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MAX_VALUE)),
                Arguments.of(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE)),
                Arguments.of(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE)),
                Arguments.of(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MIN_VALUE)),
                Arguments.of(Integer.valueOf(128), Integer.valueOf(128), Integer.valueOf(128)),
                Arguments.of(Integer.valueOf(-129), Integer.valueOf(-129), Integer.valueOf(-129))
            );
        }

        @Test
        public void testMaxIntegerNullFirstArgument()
        {
            assertThrows(NullPointerException.class, () -> SimpleMath.max((Integer) null, Integer.valueOf(5)));
        }

        @Test
        public void testMaxIntegerNullSecondArgument()
        {
            assertThrows(NullPointerException.class, () -> SimpleMath.max(Integer.valueOf(5), (Integer) null));
        }

        @Test
        public void testMaxIntegerBothNull()
        {
            assertThrows(NullPointerException.class, () -> SimpleMath.max((Integer) null, (Integer) null));
        }
    }

    @Nested
    class SumTest
    {
        @Test
        public void testSumEmptyArray()
        {
            assertEquals(0L, SimpleMath.sum(new int[]{}));
        }

        @Test
        public void testSumSingleElementPositive()
        {
            assertEquals(42L, SimpleMath.sum(new int[]{42}));
        }

        @Test
        public void testSumSingleElementNegative()
        {
            assertEquals(-42L, SimpleMath.sum(new int[]{-42}));
        }

        @Test
        public void testSumSingleElementZero()
        {
            assertEquals(0L, SimpleMath.sum(new int[]{0}));
        }

        @Test
        public void testSumMultipleElementsPositive()
        {
            assertEquals(15L, SimpleMath.sum(new int[]{1, 2, 3, 4, 5}));
        }

        @Test
        public void testSumMultipleElementsNegative()
        {
            assertEquals(-15L, SimpleMath.sum(new int[]{-1, -2, -3, -4, -5}));
        }

        @Test
        public void testSumMixedElements()
        {
            assertEquals(0L, SimpleMath.sum(new int[]{-5, 5, -10, 10, 0}));
            assertEquals(3L, SimpleMath.sum(new int[]{-2, 5, -1, 1}));
        }

        @Test
        public void testSumZeros()
        {
            assertEquals(0L, SimpleMath.sum(new int[]{0, 0, 0, 0}));
        }

        @Test
        public void testSumNullArray()
        {
            assertThrows(NullPointerException.class, () -> SimpleMath.sum(null));
        }

        @Test
        public void testSumLargeValuesNoOverflow()
        {
            assertEquals((long) Integer.MAX_VALUE, SimpleMath.sum(new int[]{Integer.MAX_VALUE}));
            assertEquals((long) Integer.MIN_VALUE, SimpleMath.sum(new int[]{Integer.MIN_VALUE}));
        }

        /**
         * Expectation based on return type 'long':
         * A sum returning long is expected to accommodate sums exceeding 32-bit integer range without 32-bit overflow.
         */
        @Test
        public void testSumOverflowBeyondIntegerMax()
        {
            long expected = (long) Integer.MAX_VALUE + (long) Integer.MAX_VALUE; // 4294967294L
            assertEquals(expected, SimpleMath.sum(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}));
        }

        /**
         * Expectation based on return type 'long':
         * A sum returning long is expected to accommodate sums below 32-bit integer minimum without 32-bit overflow.
         */
        @Test
        public void testSumUnderflowBelowIntegerMin()
        {
            long expected = (long) Integer.MIN_VALUE + (long) Integer.MIN_VALUE; // -4294967296L
            assertEquals(expected, SimpleMath.sum(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE}));
        }

        @Test
        public void testSumLargeArray()
        {
            int[] array = new int[100_000];
            for (int i = 0; i < array.length; i++)
            {
                array[i] = 100_000;
            }
            long expected = 100_000L * 100_000L; // 10_000_000_000L
            assertEquals(expected, SimpleMath.sum(array));
        }
    }
}
