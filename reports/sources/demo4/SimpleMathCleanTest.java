package org.jugsaxony.demo4;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class SimpleMathCleanTest {

    @ParameterizedTest
    @CsvSource({
        "1, 2, 2",
        "2, 1, 2",
        "0, 0, 0",
        "-1, -2, -1",
        "-2, -1, -1",
        "2147483647, 0, 2147483647",
        "0, -2147483648, 0",
        "-2147483648, -2147483648, -2147483648"
    })
    void testMaxPrimitive(int a, int b, int expected) {
        assertEquals(expected, SimpleMathClean.max(a, b));
    }

    @ParameterizedTest
    @CsvSource({
        "1, 2, 2",
        "2, 1, 2",
        "0, 0, 0",
        "-1, -2, -1",
        "-2, -1, -1"
    })
    void testMaxInteger(Integer a, Integer b, int expected) {
        assertEquals(expected, SimpleMathClean.max(a, b));
    }

    @Test
    void testMaxIntegerNulls() {
        assertThrows(NullPointerException.class, () -> SimpleMathClean.max(null, 1));
        assertThrows(NullPointerException.class, () -> SimpleMathClean.max(1, null));
        assertThrows(NullPointerException.class, () -> SimpleMathClean.max(null, null));
    }

    @Test
    void testSum() {
        assertEquals(0L, SimpleMathClean.sum(new int[]{}));
        assertEquals(10L, SimpleMathClean.sum(new int[]{1, 2, 3, 4}));
        assertEquals(-10L, SimpleMathClean.sum(new int[]{-1, -2, -3, -4}));
        assertEquals(0L, SimpleMathClean.sum(new int[]{-1, 1, -2, 2}));
    }

    @Test
    void testSumOverflow() {
        // SimpleMathClean.sum uses int for internal accumulation, should overflow
        int[] numbers = {Integer.MAX_VALUE, 1};
        assertEquals((long) Integer.MIN_VALUE, SimpleMathClean.sum(numbers));
    }

    @Test
    void testSumNull() {
        assertThrows(NullPointerException.class, () -> SimpleMathClean.sum(null));
    }
}
