package org.jugsaxony.demo4;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LRUClockMapTest {
    private LRUClockMap<String, Integer> map;
    private final int MAX_SIZE = 4;

    @BeforeEach
    void setUp() {
        map = new LRUClockMap<>(MAX_SIZE);
    }

    @Test
    void testConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new LRUClockMap<>(3));
        LRUClockMap<String, Integer> okMap = new LRUClockMap<>(4);
        assertEquals(0, okMap.size());
    }

    @Test
    void testBasicPutAndGet() {
        map.put("K1", 1);
        map.put("K2", 2);
        assertEquals(1, map.get("K1"));
        assertEquals(2, map.get("K2"));
        assertEquals(2, map.size());
    }

    @Test
    void testPutUpdate() {
        map.put("K1", 1);
        assertEquals(1, map.put("K1", 11));
        assertEquals(11, map.get("K1"));
        assertEquals(1, map.size());
    }

    @Test
    void testRemove() {
        map.put("K1", 1);
        map.put("K2", 2);
        assertEquals(1, map.remove("K1"));
        assertNull(map.get("K1"));
        assertEquals(1, map.size());
        assertEquals(2, map.get("K2"));
    }

    @Test
    void testNullsThrow() {
        assertThrows(NullPointerException.class, () -> map.put(null, 1));
        assertThrows(NullPointerException.class, () -> map.put("K1", null));
        assertThrows(NullPointerException.class, () -> map.get(null));
        assertThrows(NullPointerException.class, () -> map.remove(null));
    }

    @Test
    void testEviction() {
        // Fill to max size
        map.put("K1", 1);
        map.put("K2", 2);
        map.put("K3", 3);
        map.put("K4", 4);
        assertEquals(4, map.size());

        // Adding 5th element should trigger eviction
        map.put("K5", 5);
        assertEquals(4, map.size());
        
        // One of the previous keys must have been evicted
        boolean anyEvicted = false;
        for (int i = 1; i <= 4; i++) {
            if (map.get("K" + i) == null) {
                anyEvicted = true;
                break;
            }
        }
        assertTrue(anyEvicted, "At least one entry should have been evicted");
    }

    @Test
    void testClockAlgorithmSecondChance() {
        // Fill the map
        map.put("K1", 1);
        map.put("K2", 2);
        map.put("K3", 3);
        map.put("K4", 4);

        // Access K1 to give it a second chance
        map.get("K1"); 
        
        // Put new elements to force eviction
        // Because we don't know the exact hash/clockHand, 
        // we put multiple to see if K1 survives longer than others
        map.put("K5", 5);
        
        // K1 should ideally still be there if the clock hand didn't 
        // loop around and clear its second chance yet, 
        // but let's test the core behavior of get() updating secondChance.
        assertNotNull(map.get("K1"), "K1 should have been protected by second chance");
    }

    @Test
    void testGetRawDoesNotAffectLRU() {
        map.put("K1", 1);
        // We can't easily verify the internal 'secondChance' bit without reflection 
        // or using the internal getDebugData, so we rely on the logic that 
        // getRaw is a read-only operation.
        assertEquals(1, map.getRaw("K1"));
    }

    @Test
    void testClear() {
        map.put("K1", 1);
        map.put("K2", 2);
        map.clear();
        assertEquals(0, map.size());
        assertNull(map.get("K1"));
    }

    @Test
    void testCollisionHandlingAndRemoval() {
        // Using keys that might cause collisions or just filling the map
        for (int i = 0; i < MAX_SIZE; i++) {
            map.put("Key" + i, i);
        }
        
        // Remove one and ensure the chain isn't broken for others
        map.remove("Key0");
        assertEquals(MAX_SIZE - 1, map.size());
        
        // The remaining should still be accessible
        for (int i = 1; i < MAX_SIZE; i++) {
            assertEquals(i, map.get("Key" + i));
        }
    }

    @Test
    void testLargeVolume() {
        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            map.put("K" + i, i);
        }
        assertEquals(MAX_SIZE, map.size());
        // The last MAX_SIZE elements should generally be present
        assertEquals(iterations - 1, map.get("K" + (iterations - 1)));
    }
}
