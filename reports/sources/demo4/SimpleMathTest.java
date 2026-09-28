package org.jugsaxony.demo4;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SimpleMathTest {

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
        assertEquals(expected, SimpleMath.max(a, b));
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
        assertEquals(expected, SimpleMath.max(a, b));
    }

    @Test
    void testMaxIntegerNulls() {
        assertThrows(NullPointerException.class, () -> SimpleMath.max(null, 1));
        assertThrows(NullPointerException.class, () -> SimpleMath.max(1, null));
        assertThrows(NullPointerException.class, () -> SimpleMath.max(null, null));
    }

    @Test
    void testSum() {
        assertEquals(0L, SimpleMath.sum(new int[]{}));
        assertEquals(10L, SimpleMath.sum(new int[]{1, 2, 3, 4}));
        assertEquals(-10L, SimpleMath.sum(new int[]{-1, -2, -3, -4}));
        assertEquals(0L, SimpleMath.sum(new int[]{-1, 1, -2, 2}));
    }

    @Test
    void testSumOverflow() {
        // SimpleMath.sum uses int for internal accumulation, should overflow
        int[] numbers = {Integer.MAX_VALUE, 1};
        long expectedOverflow = (long) (Integer.MAX_VALUE + 1);
        // The implementation: int s = 0; s += i; return s; 
        // This will overflow to Integer.MIN_VALUE and return that as long.
        assertEquals((long) Integer.MIN_VALUE, SimpleMath.sum(numbers));
    }

    @Test
    void testSumNull() {
        assertThrows(NullPointerException.class, () -> SimpleMath.sum(null));
    }
}
