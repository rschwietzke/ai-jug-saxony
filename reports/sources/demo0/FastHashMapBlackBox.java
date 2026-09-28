/*
 * Copyright (c) 2005-2026 Xceptance Software Technologies GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jugsaxony.demo0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.util.FastRandom;

public class FastHashMapBlackBox
{
    @Test 
    public void ctr()
    {
        final FastHashMap<String, Integer> f = new FastHashMap<>();
        assertEquals(0, f.size());
    }

    @Test 
    public void ctrParams()
    {
        final FastHashMap<String, Integer> f = new FastHashMap<>(31, 0.54f);
        assertEquals(0, f.size());
    }

    @Test 
    public void ctrParams_FillTooSmall()
    {
        var ex = assertThrows(IllegalArgumentException.class, () -> new FastHashMap<>(31, -0.54f));
        assertEquals("FillFactor must be in (0, 1)", ex.getMessage());
    }

    @Test 
    public void ctrParams_FillTooBig()
    {
        var ex = assertThrows(IllegalArgumentException.class, () -> new FastHashMap<>(31, 1.54f));
        assertEquals("FillFactor must be in (0, 1)", ex.getMessage());
    }

    @Test 
    public void ctrParams_Size0()
    {
        var ex = assertThrows(IllegalArgumentException.class, () -> new FastHashMap<>(0, 0.54f));
        assertEquals("Size must be positive!", ex.getMessage());
    }

    @Test 
    public void ctrParams_SizeTooSmall()
    {
        var ex = assertThrows(IllegalArgumentException.class, () -> new FastHashMap<>(-31, 0.54f));
        assertEquals("Size must be positive!", ex.getMessage());
    }

    @Test
    public void happyPath()
    {
        final FastHashMap<String, Integer> f = new FastHashMap<>(3, 0.5f);
        f.put("a", 1);
        f.put("b", 2);
        f.put("c", 3);
        f.put("d", 4);
        f.put("e", 5);

        assertEquals(5, f.size());
        assertEquals(Integer.valueOf(1), f.get("a"));
        assertEquals(Integer.valueOf(4), f.get("d"));
        assertEquals(Integer.valueOf(3), f.get("c"));
        assertEquals(Integer.valueOf(5), f.get("e"));
        assertEquals(Integer.valueOf(2), f.get("b"));

        f.put("b", 20);
        assertEquals(5, f.size());
        assertEquals(Integer.valueOf(1), f.get("a"));
        assertEquals(Integer.valueOf(4), f.get("d"));
        assertEquals(Integer.valueOf(20), f.get("b"));
        assertEquals(Integer.valueOf(3), f.get("c"));
        assertEquals(Integer.valueOf(5), f.get("e"));
    }

    @Test
    public void get()
    {
        final FastHashMap<String, String> f = new FastHashMap<>(32, 0.5f);

        // empty get
        assertNull(f.get("nothere"));

        // non-existing get
        f.put("a1", "v1");
        assertNull(f.get("a10"));

        // existing get
        assertEquals("v1", f.get("a1"));

        // get after removal
        f.remove("a1");
        assertNull(f.get("a1"));

        // get after removal and put
        f.put("a2", "v2");
        assertEquals("v2", f.get("a2"));
        f.remove("a2");
        assertNull(f.get("a2"));
        f.put("a2", "v22");
        assertEquals("v22", f.get("a2"));
    }

    @Test
    public void getNull()
    {
        final FastHashMap<String, String> f = new FastHashMap<>(32, 0.5f);
        assertThrows(NullPointerException.class, () -> f.get(null));

        f.put("e1", "asdf");
        assertThrows(NullPointerException.class, () -> f.get(null));
    }

    @Test
    public void getCollision()
    {
        final FastHashMap<Mock<String, String>, String> f = new FastHashMap<>(32, 0.5f);
     
        var k1 = new Mock<>(751, "k1", "v1");
        var k2 = new Mock<>(751, "k2", "v2");
        var k3 = new Mock<>(751, "k3", "v3");
        var k4 = new Mock<>(751, "k4", "v4");
        var k5 = new Mock<>(751, "k5", "v5");
        var k6 = new Mock<>(751, "k6", "v6");

        // collision get
        f.put(k1, k1.value);        
        assertEquals(k1.value, f.get(k1));

        f.put(k2, k2.value);        
        assertEquals(k1.value, f.get(k1));
        assertEquals(k2.value, f.get(k2));

        f.put(k3, k3.value);        
        assertEquals(k1.value, f.get(k1));
        assertEquals(k2.value, f.get(k2));
        assertEquals(k3.value, f.get(k3));

        f.put(k4, k4.value);        
        assertEquals(k1.value, f.get(k1));
        assertEquals(k2.value, f.get(k2));
        assertEquals(k3.value, f.get(k3));
        assertEquals(k4.value, f.get(k4));

        f.put(k5, k5.value);        
        assertEquals(k1.value, f.get(k1));
        assertEquals(k2.value, f.get(k2));
        assertEquals(k3.value, f.get(k3));
        assertEquals(k4.value, f.get(k4));
        assertEquals(k5.value, f.get(k5));

        f.put(k6, k6.value);       
        assertEquals(k1.value, f.get(k1));
        assertEquals(k2.value, f.get(k2));
        assertEquals(k3.value, f.get(k3));
        assertEquals(k4.value, f.get(k4));
        assertEquals(k5.value, f.get(k5));
        assertEquals(k6.value, f.get(k6));

        assertEquals(6, f.size());
    }

    @Test
    public void put()
    {
        final FastHashMap<String, String> f = new FastHashMap<>(32, 0.5f);

        // put with null key
        assertThrows(NullPointerException.class, () -> f.put(null, "v1"));

        // put with null value
        f.put("e1", null);
        assertNull(f.get("e1"));
        
        // put with same key to update value
        f.put("e1", "v2");
        assertEquals("v2", f.get("e1"));
        
        // remove key and put it back again
        f.remove("e1");
        assertNull(f.get("e1"));
        f.put("e1", "v22");
        assertEquals("v22", f.get("e1"));

        assertEquals(1, f.size());
    }

    @Test
    public void putCollision()
    {
        final var f = new FastHashMap<Mock<String, String>, String>(32, 0.5f);

        var k1 = new Mock<>(711, "k1", "v1");
        var k2 = new Mock<>(711, "k2", "v2");
        var k3 = new Mock<>(711, "k3", "v3");

        // put with collision key
        f.put(k1, k1.value);
        assertEquals(k1.value, f.get(k1));

        f.put(k2, k2.value);
        f.put(k3, k3.value);

        // update values
        f.put(k2, "v22");
        f.put(k1, "v11");
        f.put(k3, "v33");

        assertEquals("v11", f.get(k1));
        assertEquals("v22", f.get(k2));
        assertEquals("v33", f.get(k3));

        assertEquals(3, f.size());
    }

    @Test
    public void keys()
    {
        final FastHashMap<String, Integer> f = new FastHashMap<>(3, 0.5f);

        // keys of empty
        assertEquals(0, f.keys().size());

        f.put("aa", 1);
        f.put("bb", 2);
        f.put("cc", 3);
        f.put("dd", 4);
        f.put("ee", 5);

        {
            final List<String> k = f.keys();
            assertEquals(5, k.size());
            assertTrue(k.contains("aa"));
            assertTrue(k.contains("bb"));
            assertTrue(k.contains("cc"));
            assertTrue(k.contains("dd"));
            assertTrue(k.contains("ee"));
        }

        assertEquals(Integer.valueOf(3), f.remove("cc"));
        f.remove("c");
        {
            final List<String> k = f.keys();
            assertEquals(4, k.size());
            assertTrue(k.contains("aa"));
            assertTrue(k.contains("bb"));
            assertTrue(k.contains("dd"));
            assertTrue(k.contains("ee"));
        }

        f.put("zz", 10);
        f.remove("c");
        {
            final List<String> k = f.keys();
            assertEquals(5, k.size());
            assertTrue(k.contains("aa"));
            assertTrue(k.contains("bb"));
            assertTrue(k.contains("dd"));
            assertTrue(k.contains("ee"));
            assertTrue(k.contains("zz"));
        }

        // ask for something unknown
        assertNull(f.get("unknown"));
    }

    @Test
    public void values()
    {
        final FastHashMap<String, Integer> f = new FastHashMap<>(3, 0.5f);

        // values of empty
        assertEquals(0, f.values().size());

        f.put("aa", 1);
        f.put("bb", 2);
        f.put("cc", 3);
        f.put("dd", 4);
        f.put("ee", 5);

        {
            final List<Integer> values = f.values();
            assertEquals(5, values.size());
            assertTrue(values.contains(1));
            assertTrue(values.contains(2));
            assertTrue(values.contains(3));
            assertTrue(values.contains(4));
            assertTrue(values.contains(5));
        }

        assertEquals(Integer.valueOf(3), f.remove("cc"));
        f.remove("c");
        {
            final List<Integer> values = f.values();
            assertEquals(4, values.size());
            assertTrue(values.contains(1));
            assertTrue(values.contains(2));
            assertTrue(values.contains(4));
            assertTrue(values.contains(5));
        }
    }

    @Test
    public void remove()
    {
        final FastHashMap<String, Integer> f = new FastHashMap<>(3, 0.5f);
        f.put("a", 1);
        f.put("b", 2);
        f.put("c", 3);
        f.put("d", 4);
        f.put("e", 5);

        f.remove("b");
        f.remove("d");

        assertEquals(3, f.size());
        assertEquals(Integer.valueOf(1), f.get("a"));
        assertEquals(Integer.valueOf(3), f.get("c"));
        assertEquals(Integer.valueOf(5), f.get("e"));
        assertNull(f.get("d"));
        assertNull(f.get("b"));

        // remove again
        assertNull(f.remove("b"));
        assertNull(f.remove("d"));

        f.put("d", 6);
        f.put("b", 7);
        assertEquals(Integer.valueOf(7), f.get("b"));
        assertEquals(Integer.valueOf(6), f.get("d"));
    }

    @Test 
    public void removeNull()
    {
        final FastHashMap<String, Integer> f = new FastHashMap<>(31, 0.5f);
        assertThrows(NullPointerException.class, () -> f.remove(null));
    }

    @Test 
    public void removeEmpty()
    {
        final FastHashMap<String, Integer> f = new FastHashMap<>(3, 0.5f);
        assertNull(f.remove("a"));
    }

    @Test 
    public void removeTwice()
    {
        final FastHashMap<String, String> f = new FastHashMap<>(3, 0.5f);
        f.put("a", "a1");
        assertEquals("a1", f.remove("a"));
        assertNull(f.remove("a"));
    }

    @Test 
    public void removeCollision()
    {
        // remove first
        {
            final FastHashMap<Mock<String, String>, String> f = new FastHashMap<>(32, 0.5f);
            var k1 = new Mock<>(711, "k1", "v1");
            var k2 = new Mock<>(711, "k2", "v2");
            var k3 = new Mock<>(711, "k3", "v3");

            f.put(k1, k1.value);
            f.put(k2, k2.value);
            f.put(k3, k3.value);

            assertEquals("v1", f.remove(k1));
            assertEquals(k2.value, f.get(k2));
            assertEquals(k3.value, f.get(k3));
            
            assertEquals(2, f.size());
        }
        // remove second
        {
            final FastHashMap<Mock<String, String>, String> f = new FastHashMap<>(32, 0.5f);
            var k1 = new Mock<>(711, "k1", "v1");
            var k2 = new Mock<>(711, "k2", "v2");
            var k3 = new Mock<>(711, "k3", "v3");

            f.put(k1, k1.value);
            f.put(k2, k2.value);
            f.put(k3, k3.value);

            assertEquals("v2", f.remove(k2));
            assertEquals(k1.value, f.get(k1));
            assertEquals(k3.value, f.get(k3));
            
            assertEquals(2, f.size());
        }
        // remove last
        {
            final FastHashMap<Mock<String, String>, String> f = new FastHashMap<>(32, 0.5f);
            var k1 = new Mock<>(711, "k1", "v1");
            var k2 = new Mock<>(711, "k2", "v2");
            var k3 = new Mock<>(711, "k3", "v3");

            f.put(k1, k1.value);
            f.put(k2, k2.value);
            f.put(k3, k3.value);

            assertEquals("v3", f.remove(k3));
            assertEquals(k1.value, f.get(k1));
            assertEquals(k2.value, f.get(k2));
            
            assertEquals(2, f.size());
        }
    }

    @Test
    public void rehashing()
    {
        // we will provoke rehashing and check the outcome afterwards
        final FastHashMap<Integer, String> f = new FastHashMap<>(4, 0.37f);
        for (int i = 0; i < 17711; i++)
        {
            f.put(i, "abc" + String.valueOf(i));
            assertEquals(i + 1 , f.size());    
        }

        for (int i = 0; i < 17711; i++)
        {
            assertEquals("abc" + String.valueOf(i), f.get(i));   
        }
    }

    @Test
    public void rehashingWithTombstones()
    {
        final FastHashMap<Integer, String> f = new FastHashMap<>(7, 0.37f);
        for (int i = 0; i < 8; i++)
        {
            f.put(i, "abc" + String.valueOf(i));
            assertEquals(1, f.size());    

            f.remove(i);
            assertEquals(0, f.size());    
        }

        for (int i = 0; i < 21; i++)
        {
            f.put(i, "abc" + String.valueOf(i));
            assertEquals(i + 1 , f.size());    

            for (int h = 0; h <= i; h++)
            {
                assertEquals("abc" + String.valueOf(h), f.get(h));   
            }
        }
    }

    @Test
    public void rehashingWithCollisions()
    {
        // always have two collisions per key
        final var f = new FastHashMap<MockKey<String>, String>(13, 0.5f);

        var size = 0;
        for (int i = 0; i < 1651; i++)
        {
            f.put(new MockKey<String>(i, "k1" + i), "v1" + i);
            size++;
            assertEquals(size , f.size());    

            f.put(new MockKey<String>(i, "k2" + i), "v2" + i);
            size++;
            assertEquals(size , f.size());    
        }

        for (int i = 0; i < 1651; i++)
        {
            var k1 = new MockKey<String>(i, "k1" + i);
            assertEquals("v1" + i, f.get(k1));    

            var k2 = new MockKey<String>(i, "k2" + i);
            assertEquals("v2" + i, f.get(k2));    
        }
    }

    @Test
    public void rehashingWithCollisionsAndTombstones()
    {
        // always have two collisions per key
        final var f = new FastHashMap<MockKey<String>, String>(13, 0.5f);

        final var random = FastRandom.get(187612L);
        final var storedData = new HashMap<MockKey<String>, String>();

        int i = 0;
        while (i < 1651)
        {
            var k1 = new MockKey<String>(i, "k1" + i);
            var k2 = new MockKey<String>(i, "k2" + i);
            var k3 = new MockKey<String>(i, "k3" + i);
            var k4 = new MockKey<String>(i, "k4" + i);

            storedData.put(k1, "v1" + k1.toString());
            storedData.put(k2, "v2" + k1.toString());
            storedData.put(k3, "v3" + k1.toString());
            storedData.put(k4, "v4" + k1.toString());

            f.put(k1, "v1" + k1.toString());
            f.put(k2, "v2" + k1.toString());
            f.put(k3, "v3" + k1.toString());
            f.put(k4, "v4" + k1.toString());

            var k = switch(random.nextInt(1, 4))
            {
                case 1 -> k1;
                case 2 -> k2;
                case 3 -> k3;
                default -> k4;
            };

            f.remove(k);
            storedData.remove(k);

            // different distances between our entries
            i += random.nextInt(1, 3);
        }

        assertEquals(storedData.size(), f.size());

        for (var e : storedData.entrySet())
        {
            assertEquals(e.getValue(), f.get(e.getKey()));    
        }
    }

    @Test
    public void clear()
    {
        var m = new FastHashMap<String, Integer>();
        m.put("a", 1);
        assertEquals(1, m.size());

        m.clear();
        assertEquals(0, m.size());
        assertEquals(0, m.keys().size());
        assertEquals(0, m.values().size());
        assertNull(m.get("a"));

        m.put("b", 2);
        assertEquals(1, m.size());
        m.put("a", 3);
        assertEquals(2, m.size());

        m.clear();
        assertEquals(0, m.size());
        assertEquals(0, m.keys().size());
        assertEquals(0, m.values().size());

        m.put("a", 1);
        m.put("b", 2);
        m.put("c", 3);
        m.put("c", 3);
        assertEquals(3, m.size());
        assertEquals(3, m.keys().size());
        assertEquals(3, m.values().size());

        assertEquals(Integer.valueOf(1), m.get("a"));
        assertEquals(Integer.valueOf(2), m.get("b"));
        assertEquals(Integer.valueOf(3), m.get("c"));
    }

    @Test
    public void collision()
    {
        var f = new FastHashMap<MockKey<String>, String>(13, 0.5f);
        IntStream.range(0, 15).forEach(i -> {
            f.put(new MockKey<String>(12, "k" + i), "v" + i);
        });

        assertEquals(15, f.size());

        IntStream.range(0, 15).forEach(i -> {
            assertEquals("v" + i, f.get(new MockKey<String>(12, "k" + i)));
        });

        // round 2
        IntStream.range(0, 20).forEach(i -> {
            f.put(new MockKey<String>(12, "k" + i), "v" + i);
        });

        assertEquals(20, f.size());

        IntStream.range(0, 20).forEach(i -> {
            assertEquals("v" + i, f.get(new MockKey<String>(12, "k" + i)));
        });

        // round 3
        IntStream.range(0, 10).forEach(i -> {
            assertEquals("v" + i, f.remove(new MockKey<String>(12, "k" + i)));
        });
        IntStream.range(10, 20).forEach(i -> {
            assertEquals("v" + i, f.get(new MockKey<String>(12, "k" + i)));
        });
    }

    /**
     * Overflow initial size with collision keys. Some hash code for all keys.
     */
    @Test
    public void overflow()
    {
        final FastHashMap<MockKey<String>, Integer> m = new FastHashMap<>(5, 0.5f);
        var data = IntStream.range(0, 152)
            .mapToObj(Integer::valueOf)
            .collect(
                     Collectors.toMap(i -> new MockKey<String>(1, "k" + i),
                                      i -> i));

        // add all
        data.forEach((k, v) -> m.put(k, v));

        // verify
        data.forEach((k, v) -> assertEquals(v, m.get(k)));
        assertEquals(152, m.size());
        assertEquals(152, m.keys().size());
        assertEquals(152, m.values().size());
    }

    /**
     * Try to hit all slots with bad hashcodes
     */
    @Test
    public void hitEachSlot()
    {
        final FastHashMap<MockKey<String>, Integer> m = new FastHashMap<>(15, 0.9f);

        var data = IntStream.range(0, 150)
            .mapToObj(Integer::valueOf)
            .collect(
                     Collectors.toMap(i -> new MockKey<String>(i, "k1" + i),
                                      i -> i));

        // add the same hash codes again but other keys
        data.putAll(IntStream.range(0, 150)
            .mapToObj(Integer::valueOf)
            .collect(
                     Collectors.toMap(i -> new MockKey<String>(i, "k2" + i),
                                      i -> i)));
        // add all
        data.forEach((k, v) -> m.put(k, v));
        // verify
        data.forEach((k, v) -> assertEquals(v, m.get(k)));
        assertEquals(300, m.size());
        assertEquals(300, m.keys().size());
        assertEquals(300, m.values().size());

        // remove all
        data.forEach((k, v) -> m.remove(k));
        // verify
        assertEquals(0, m.size());
        assertEquals(0, m.keys().size());
        assertEquals(0, m.values().size());

        // add all
        var keys = data.keySet().stream().collect(Collectors.toList());
        keys.stream().sorted().forEach(k -> m.put(k, data.get(k)));
        // put in different order
        Collections.shuffle(keys);
        keys.forEach(k -> m.put(k, data.get(k) + 42));

        // verify
        data.forEach((k, v) -> assertEquals(Integer.valueOf(v + 42), m.get(k)));
        assertEquals(300, m.size());
        assertEquals(300, m.keys().size());
        assertEquals(300, m.values().size());

        // remove in different order
        Collections.shuffle(keys);
        keys.forEach(k -> m.remove(k));

        // verify
        data.forEach((k, v) -> assertNull(m.get(k)));
        assertEquals(0, m.size());
        assertEquals(0, m.keys().size());
        assertEquals(0, m.values().size());
    }

    static class MockKey<T extends Comparable<T>> implements Comparable<MockKey<T>>
    {
        public final T key;
        public final int hash;

        public MockKey(int hash, T key)
        {
            this.hash = hash;
            this.key = key;
        }

        @Override
        public int hashCode()
        {
            return hash;
        }

        @Override
        public boolean equals(Object o)
        {
            var t = (MockKey<T>) o;
            return hash == o.hashCode() && key.equals(t.key);
        }

        @Override
        public String toString()
        {
            return "MockKey [key=" + key + ", hash=" + hash + "]";
        }

        @Override
        public int compareTo(MockKey<T> o)
        {
            return o.key.compareTo(this.key);
        }
    }

    static class Mock<K extends Comparable<K>, V> implements Comparable<Mock<K, V>>
    {
        public final K key;
        public final V value;
        public final int hash;

        public Mock(final int hash, final K key, final V value)
        {
            this.hash = hash;
            this.key = key;
            this.value = value;
        }

        @Override
        public int hashCode()
        {
            return hash;
        }

        @Override
        public boolean equals(final Object o)
        {
            final var t = (Mock<K, V>) o;
            return hash == o.hashCode() && key.equals(t.key);
        }

        @Override
        public String toString()
        {
            return "Mock [key=" + key + ", value=" + value + ", hash=" + hash + "]";
        }

        @Override
        public int compareTo(final Mock<K, V> o)
        {
            return o.key.compareTo(this.key);
        }
    }
}
