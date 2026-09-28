package com.xceptance.xlt.report.util.rework;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntTimeSeriesEntryTest {
    @Test
    void testUpdateValue() {
        IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(100, false);
        entry.updateValue(200, true);
        
        assertEquals(2, entry.getCount());
        assertEquals(1, entry.getErrorCount());
        assertEquals(200, entry.getMaximumValue());
        assertEquals(100, entry.getMinimumValue());
        assertEquals(150, entry.getAverageValue());
    }

    @Test
    void testDistinctValuesScaling() {
        IntTimeSeriesEntry entry = new IntTimeSeriesEntry();
        entry.updateValue(10, false);
        entry.updateValue(1000, false); // Forces scale
        
        double[] vals = entry.getValues();
        assertTrue(vals.length > 0);
    }

    @Test
    void testMerge() {
        IntTimeSeriesEntry e1 = new IntTimeSeriesEntry(10, false);
        IntTimeSeriesEntry e2 = new IntTimeSeriesEntry(20, false);
        e1.merge(e2);
        
        assertEquals(2, e1.getCount());
        assertEquals(20, e1.getMaximumValue());
        assertEquals(10, e1.getMinimumValue());
    }
}
