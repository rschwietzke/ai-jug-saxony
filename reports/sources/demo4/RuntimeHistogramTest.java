package com.xceptance.xlt.report.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeHistogramTest {
    @Test
    void testBasicStats() {
        RuntimeHistogram rh = new RuntimeHistogram();
        rh.addValue(10);
        rh.addValue(20);
        rh.addValue(30);
        
        assertEquals(3, rh.getValueCount());
        assertEquals(20.0, rh.getMedianValue());
        assertEquals(10.0, rh.getPercentile(0.0)); // adjusted to 0.0 in impl but logic says first
        assertEquals(30.0, rh.getPercentile(100.0));
    }

    @Test
    void testPrecision() {
        RuntimeHistogram rh = new RuntimeHistogram(8); // precision 8
        rh.addValue(10); // 10 >> 3 = 1
        rh.addValue(11); // 11 >> 3 = 1
        assertEquals(1, rh.getNumberOfBuckets());
    }

    @Test
    void testCountForValue() {
        RuntimeHistogram rh = new RuntimeHistogram();
        rh.addValue(10);
        rh.addValue(20);
        rh.addValue(30);
        assertEquals(1, rh.getCountForValue(10, 15));
        assertEquals(2, rh.getCountForValue(10, 25));
        assertEquals(0, rh.getCountForValue(40, 50));
    }
}
