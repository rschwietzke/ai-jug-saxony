package org.jugsaxony.demo3;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SimpleMathCleanTest
{
    @Test
    void defaultConstructorCreatesAnInstance()
    {
        assertNotNull(new SimpleMathClean());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maxCases")
    void primitiveMaxReturnsTheGreaterValue(final String scenario, final int first, final int second, final int expected)
    {
        final int actual = SimpleMathClean.max(first, second);

        assertAll(
            () -> assertEquals(expected, actual, scenario),
            () -> assertEquals(Math.max(first, second), actual, "must agree with Math.max"),
            () -> assertEquals(actual, SimpleMathClean.max(second, first), "max must be commutative"),
            () -> assertTrue(actual == first || actual == second, "result must be one of the operands"),
            () -> assertTrue(actual >= first && actual >= second, "result must be an upper bound")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maxCases")
    void boxedMaxUsesNumericValueSemantics(final String scenario, final int first, final int second, final int expected)
    {
        final Integer boxedFirst = Integer.valueOf(first);
        final Integer boxedSecond = Integer.valueOf(second);
        final int actual = SimpleMathClean.max(boxedFirst, boxedSecond);

        assertAll(
            () -> assertEquals(expected, actual, scenario),
            () -> assertEquals(SimpleMathClean.max(first, second), actual, "boxed and primitive overloads must agree"),
            () -> assertEquals(actual, SimpleMathClean.max(boxedSecond, boxedFirst), "max must be commutative")
        );
    }

    @Test
    void boxedMaxRejectsEveryNullOperandCombination()
    {
        assertAll(
            () -> assertThrows(NullPointerException.class,
                () -> SimpleMathClean.max((Integer) null, Integer.valueOf(1))),
            () -> assertThrows(NullPointerException.class,
                () -> SimpleMathClean.max(Integer.valueOf(1), (Integer) null)),
            () -> assertThrows(NullPointerException.class,
                () -> SimpleMathClean.max((Integer) null, (Integer) null))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sumCases")
    void sumReturnsTheMathematicalTotal(final String scenario, final int[] numbers, final long expected)
    {
        assertEquals(expected, SimpleMathClean.sum(numbers), scenario);
    }

    @Test
    void sumIsIndependentOfElementOrder()
    {
        final int[] ascending = {Integer.MIN_VALUE, -17, 17, 42, Integer.MAX_VALUE};
        final int[] descending = {Integer.MAX_VALUE, 42, 17, -17, Integer.MIN_VALUE};
        final int[] shuffled = {42, Integer.MIN_VALUE, Integer.MAX_VALUE, -17, 17};

        assertAll(
            () -> assertEquals(41L, SimpleMathClean.sum(ascending)),
            () -> assertEquals(41L, SimpleMathClean.sum(descending)),
            () -> assertEquals(41L, SimpleMathClean.sum(shuffled))
        );
    }

    @Test
    void sumIsAdditiveWhenArraysAreConcatenated()
    {
        final int[] first = {Integer.MAX_VALUE, 10, -10};
        final int[] second = {1, 2, 3};
        final int[] combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);

        assertEquals(SimpleMathClean.sum(first) + SimpleMathClean.sum(second), SimpleMathClean.sum(combined));
    }

    @Test
    void sumDoesNotModifyItsInput()
    {
        final int[] numbers = {3, 1, 4, 1, 5, 9, Integer.MIN_VALUE, Integer.MAX_VALUE};
        final int[] original = numbers.clone();

        SimpleMathClean.sum(numbers);

        assertTrue(Arrays.equals(original, numbers), "sum must not modify its input array");
    }

    @Test
    void sumRejectsANullArray()
    {
        assertThrows(NullPointerException.class, () -> SimpleMathClean.sum(null));
    }

    private static Stream<Arguments> maxCases()
    {
        return Stream.of(
            Arguments.of("equal zero operands", 0, 0, 0),
            Arguments.of("equal positive operands", 17, 17, 17),
            Arguments.of("equal negative operands", -17, -17, -17),
            Arguments.of("equal values outside the Integer cache", 10_000, 10_000, 10_000),
            Arguments.of("first positive operand is greater", 9, 3, 9),
            Arguments.of("second positive operand is greater", 3, 9, 9),
            Arguments.of("first negative operand is greater", -2, -5, -2),
            Arguments.of("second negative operand is greater", -5, -2, -2),
            Arguments.of("positive first operand beats negative", 1, -1, 1),
            Arguments.of("positive second operand beats negative", -1, 1, 1),
            Arguments.of("zero first operand beats negative", 0, -1, 0),
            Arguments.of("zero second operand beats negative", -1, 0, 0),
            Arguments.of("positive second operand beats zero", 0, 1, 1),
            Arguments.of("positive first operand beats zero", 1, 0, 1),
            Arguments.of("maximum beats minimum", Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE),
            Arguments.of("maximum beats minimum in reverse order", Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
            Arguments.of("maximum beats its predecessor", Integer.MAX_VALUE, Integer.MAX_VALUE - 1, Integer.MAX_VALUE),
            Arguments.of("maximum predecessor loses in first position", Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE),
            Arguments.of("minimum loses to its successor", Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1),
            Arguments.of("minimum loses to its successor in reverse order", Integer.MIN_VALUE + 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1),
            Arguments.of("equal maximum operands", Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
            Arguments.of("equal minimum operands", Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE)
        );
    }

    private static Stream<Arguments> sumCases()
    {
        return Stream.of(
            Arguments.of("empty array has the additive identity", new int[] {}, 0L),
            Arguments.of("single zero", new int[] {0}, 0L),
            Arguments.of("single positive value", new int[] {42}, 42L),
            Arguments.of("single negative value", new int[] {-42}, -42L),
            Arguments.of("single maximum value", new int[] {Integer.MAX_VALUE}, (long) Integer.MAX_VALUE),
            Arguments.of("single minimum value", new int[] {Integer.MIN_VALUE}, (long) Integer.MIN_VALUE),
            Arguments.of("multiple positive values", new int[] {1, 2, 3, 4, 5}, 15L),
            Arguments.of("multiple negative values", new int[] {-1, -2, -3, -4, -5}, -15L),
            Arguments.of("positive negative and zero values", new int[] {10, -5, 20, -15, 30, 0}, 40L),
            Arguments.of("opposite values cancel", new int[] {100, -100, 50, -50}, 0L),
            Arguments.of("duplicate values are all counted", new int[] {7, 7, 7, 7}, 28L),
            Arguments.of("maximum and minimum combine to negative one",
                new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE}, -1L),
            Arguments.of("extremes and one cancel to zero",
                new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE, 1}, 0L),
            Arguments.of("total one above Integer.MAX_VALUE",
                new int[] {Integer.MAX_VALUE, 1}, (long) Integer.MAX_VALUE + 1L),
            Arguments.of("two maximum values require a long result",
                new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 2L * Integer.MAX_VALUE),
            Arguments.of("total one below Integer.MIN_VALUE",
                new int[] {Integer.MIN_VALUE, -1}, (long) Integer.MIN_VALUE - 1L),
            Arguments.of("two minimum values require a long result",
                new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE}, 2L * Integer.MIN_VALUE),
            Arguments.of("three large positive values", new int[] {1_000_000_000, 1_000_000_000, 1_000_000_000}, 3_000_000_000L),
            Arguments.of("three large negative values", new int[] {-1_000_000_000, -1_000_000_000, -1_000_000_000}, -3_000_000_000L),
            Arguments.of("large intermediate total can return to int range",
                new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, -Integer.MAX_VALUE}, (long) Integer.MAX_VALUE),
            Arguments.of("mixed extremes can still require a long result",
                new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, 10}, 2_147_483_656L)
        );
    }
}
