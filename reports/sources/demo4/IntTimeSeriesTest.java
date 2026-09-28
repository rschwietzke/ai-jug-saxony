package com.xceptance.xlt.report.util.rework;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntTimeSeriesTest {
    @Test
    void testBasicAdd() {
        IntTimeSeries ts = new IntTimeSeries(100);
        long now = System.currentTimeMillis();
        ts.addValue(now, now + 100, 50, false);
        
        assertEquals(1, ts.getCount());
        assertEquals(50, ts.getMean(), 0.1);
        assertEquals(50, ts.getPercentile(50));
    }

    @Test
    void testCondense() {
        IntTimeSeries ts = new IntTimeSeries(4); // Small size to force condense
        long start = 1000L * 1000;
        ts.addValue(start, start, 10, false);
        // Add value far in future to force condense
        ts.addValue(start + 10000L * 1000, start + 10000L * 1000, 20, false);
        
        assertTrue(ts.getScale() > 1);
        assertEquals(2, ts.getCount());
    }
}
