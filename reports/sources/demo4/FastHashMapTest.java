package org.jugsaxony.demo4;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FastHashMapTest {
    private FastHashMap<String, Integer> map;

    @BeforeEach
    void setUp() {
        map = new FastHashMap<>();
    }

    @Test
    void testPutAndGet() {
        assertNull(map.put("one", 1));
        assertNull(map.put("two", 2));
        assertEquals(1, map.get("one"));
        assertEquals(2, map.get("two"));
    }

    @Test
    void testPutUpdate() {
        map.put("one", 1);
        assertEquals(1, map.put("one", 2));
        assertEquals(2, map.get("one"));
        assertEquals(1, map.size());
    }

    @Test
    void testRemove() {
        map.put("one", 1);
        map.put("two", 2);
        assertEquals(1, map.remove("one"));
        assertNull(map.get("one"));
        assertEquals(2, map.get("two"));
        assertEquals(1, map.size());
    }

    @Test
    void testRemoveNonExistent() {
        map.put("one", 1);
        assertNull(map.remove("two"));
        assertEquals(1, map.size());
    }

    @Test
    void testNullKeyThrows() {
        assertThrows(NullPointerException.class, () -> map.put(null, 1));
        assertThrows(NullPointerException.class, () -> map.get(null));
        assertThrows(NullPointerException.class, () -> map.remove(null));
    }

    @Test
    void testNullValueAllowed() {
        assertNull(map.put("nullVal", null));
        assertNull(map.get("nullVal"));
        assertEquals(1, map.size());
        // Verify that get(nullVal) returning null does not mean it's missing
        assertFalse(map.keys().contains("nullVal") == false); 
    }

    @Test
    void testResize() {
        for (int i = 0; i < 100; i++) {
            map.put("key" + i, i);
        }
        assertEquals(100, map.size());
        for (int i = 0; i < 100; i++) {
            assertEquals(i, map.get("key" + i));
        }
    }

    @Test
    void testClear() {
        map.put("one", 1);
        map.put("two", 2);
        map.clear();
        assertEquals(0, map.size());
        assertNull(map.get("one"));
        assertTrue(map.keys().isEmpty());
    }

    @Test
    void testKeysAndValues() {
        map.put("a", 1);
        map.put("b", 2);
        List<String> keys = map.keys();
        List<Integer> values = map.values();
        assertEquals(2, keys.size());
        assertEquals(2, values.size());
        assertTrue(keys.contains("a"));
        assertTrue(keys.contains("b"));
        assertTrue(values.contains(1));
        assertTrue(values.contains(2));
    }

    @Test
    void testCollisions() {
        // Create keys that might collide (depend on hash implementation)
        // In our simplified hash, using keys with same hash if possible.
        // For String "Aa" and "BB" they often have same hashCode.
        map.put("Aa", 1);
        map.put("BB", 2);
        assertEquals(1, map.get("Aa"));
        assertEquals(2, map.get("BB"));
        assertEquals(2, map.size());
    }
}
