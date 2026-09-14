package com.xceptance.xlt.report.util.lucene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitUtilTest {
    @Test
    void testPop() {
        assertEquals(0, BitUtil.pop(0L));
        assertEquals(1, BitUtil.pop(1L));
        assertEquals(1, BitUtil.pop(2L));
        assertEquals(2, BitUtil.pop(3L));
        assertEquals(64, BitUtil.pop(-1L));
    }

    @Test
    void testIsPowerOfTwo() {
        assertTrue(BitUtil.isPowerOfTwo(0));
        assertTrue(BitUtil.isPowerOfTwo(1));
        assertTrue(BitUtil.isPowerOfTwo(2));
        assertTrue(BitUtil.isPowerOfTwo(4));
        assertFalse(BitUtil.isPowerOfTwo(3));
        assertFalse(BitUtil.isPowerOfTwo(5));
    }

    @Test
    void testNextHighestPowerOfTwo() {
        assertEquals(1, BitUtil.nextHighestPowerOfTwo(0));
        assertEquals(1, BitUtil.nextHighestPowerOfTwo(1));
        assertEquals(2, BitUtil.nextHighestPowerOfTwo(2));
        assertEquals(4, BitUtil.nextHighestPowerOfTwo(3));
        assertEquals(4, BitUtil.nextHighestPowerOfTwo(4));
        assertEquals(8, BitUtil.nextHighestPowerOfTwo(5));
    }

    @Test
    void testNtz() {
        assertEquals(0, BitUtil.ntz(1L));
        assertEquals(1, BitUtil.ntz(2L));
        assertEquals(2, BitUtil.ntz(4L));
        assertEquals(3, BitUtil.ntz(8L));
        assertEquals(63, BitUtil.ntz(1L << 63));
    }

    @Test
    void testNlz() {
        assertEquals(63, BitUtil.nlz(1L));
        assertEquals(62, BitUtil.nlz(2L));
        assertEquals(0, BitUtil.nlz(-1L));
    }
}
