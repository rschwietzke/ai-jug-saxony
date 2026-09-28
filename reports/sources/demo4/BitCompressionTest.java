package com.xceptance.xlt.report.util.misc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitCompressionTest {
    @Test
    void testCombineAdjacentBits() {
        assertEquals(0L, BitCompression.combineAdjacentBits(0L));
        assertEquals(3L, BitCompression.combineAdjacentBits(1L)); // 1 | 2 = 3
        assertEquals(6L, BitCompression.combineAdjacentBits(2L)); // 2 | 4 = 6
    }

    @Test
    void testCompressAndShiftOddBits() {
        // Value: 0b...0101 (bits 0 and 2 set). 
        // Odd bits are 1, 3, 5...
        // Shifted right 1: 0b...0010 (bit 1 set).
        // Compressed odd bits 1, 3... -> 0, 1...
        assertEquals(1L, BitCompression.compressAndShiftOddBits(2L)); // Bit 1 set -> bit 0
        assertEquals(2L, BitCompression.compressAndShiftOddBits(8L)); // Bit 3 set -> bit 1
    }
}
