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
package org.jugsaxony.demo5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link FastHashMap}.
 */
public class FastHashMapTest
{
    /** A key type that always hashes to the same value to force collisions. */
    private static final class CollisionKey
    {
        final int id;

        CollisionKey(final int id)
        {
            this.id = id;
        }

        @Override
        public int hashCode()
        {
            return 42;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (!(o instanceof CollisionKey other))
            {
                return false;
            }
            return this.id == other.id;
        }

        @Override
        public String toString()
        {
            return "CK(" + id + ")";
        }
    }

    @Test
    public void emptyMap()
    {
        final FastHashMap<String, String> map = new FastHashMap<>();
        assertEquals(0, map.size());
        assertNull(map.get("a"));
        assertNull(map.remove("a"));
        assertTrue(map.keys().isEmpty());
        assertTrue(map.values().isEmpty());
    }

    @Test
    public void putAndGet()
    {
        final FastHashMap<String, Integer> map = new FastHashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        assertEquals(3, map.size());
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
        assertEquals(3, map.get("c"));
        assertNull(map.get("d"));
    }

    @Test
    public void putReturnsPreviousValue()
    {
        final FastHashMap<String, String> map = new FastHashMap<>();
        assertNull(map.put("a", "1"));
        assertEquals("1", map.put("a", "2"));
        assertEquals("2", map.get("a"));
        assertEquals(1, map.size());
    }

    @Test
    public void nullKeysAreRejected()
    {
        final FastHashMap<String, String> map = new FastHashMap<>();
        assertThrows(NullPointerException.class, () -> map.get(null));
        assertThrows(NullPointerException.class, () -> map.put(null, "x"));
        assertThrows(NullPointerException.class, () -> map.remove(null));
    }

    @Test
    public void nullValuesAreAllowed()
    {
        final FastHashMap<String, String> map = new FastHashMap<>();
        map.put("a", null);
        assertEquals(1, map.size());
        assertNull(map.get("a"));
        assertNull(map.put("a", "x")); // previous value was null
        assertEquals("x", map.get("a"));
        assertEquals("x", map.put("a", null)); // returns previous value "x"
        assertNull(map.get("a"));
    }

    @Test
    public void removeExisting()
    {
        final FastHashMap<String, String> map = new FastHashMap<>();
        map.put("a", "1");
        map.put("b", "2");

        assertEquals("1", map.remove("a"));
        assertEquals(1, map.size());
        assertNull(map.get("a"));
        assertEquals("2", map.get("b"));
    }

    @Test
    public void removeAbsent()
    {
        final FastHashMap<String, String> map = new FastHashMap<>();
        map.put("a", "1");
        assertNull(map.remove("missing"));
        assertEquals(1, map.size());
    }

    @Test
    public void removeWithNullValue()
    {
        final FastHashMap<String, String> map = new FastHashMap<>();
        map.put("a", null);
        assertNull(map.remove("a"));
        assertEquals(0, map.size());
        assertNull(map.get("a"));
    }

    @Test
    public void clearEmptiesMap()
    {
        final FastHashMap<Integer, String> map = new FastHashMap<>();
        for (int i = 0; i < 100; i++)
        {
            map.put(i, "v" + i);
        }
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.keys().isEmpty());
        assertNull(map.get(50));

        // still usable after clear
        map.put(1, "one");
        assertEquals(1, map.size());
        assertEquals("one", map.get(1));
    }

    @Test
    public void growsBeyondInitialCapacity()
    {
        final FastHashMap<Integer, String> map = new FastHashMap<>();
        final int count = 20_000;
        for (int i = 0; i < count; i++)
        {
            map.put(i, "v" + i);
        }

        assertEquals(count, map.size());
        for (int i = 0; i < count; i++)
        {
            assertEquals("v" + i, map.get(i));
        }

        // overwrite after growth
        for (int i = 0; i < count; i++)
        {
            assertEquals("v" + i, map.put(i, "w" + i));
        }
        for (int i = 0; i < count; i++)
        {
            assertEquals("w" + i, map.get(i));
        }
    }

    @Test
    public void collidingKeys()
    {
        final FastHashMap<CollisionKey, String> map = new FastHashMap<>();
        final List<CollisionKey> keys = new ArrayList<>();
        final int count = 2_000;

        for (int i = 0; i < count; i++)
        {
            final CollisionKey key = new CollisionKey(i);
            keys.add(key);
            map.put(key, "v" + i);
        }

        assertEquals(count, map.size());
        for (int i = 0; i < count; i++)
        {
            assertEquals("v" + i, map.get(keys.get(i)));
        }

        // remove some and verify the probe chain stays intact
        for (int i = 0; i < count; i += 3)
        {
            assertEquals("v" + i, map.remove(keys.get(i)));
        }

        for (int i = 0; i < count; i++)
        {
            if (i % 3 == 0)
            {
                assertNull(map.get(keys.get(i)));
            }
            else
            {
                assertEquals("v" + i, map.get(keys.get(i)));
            }
        }
    }

    @Test
    public void keysReturnsSnapshot()
    {
        final FastHashMap<String, String> map = new FastHashMap<>();
        map.put("a", "1");
        map.put("b", "2");

        final List<String> keys = map.keys();
        assertEquals(2, keys.size());
        assertEquals(Set.of("a", "b"), new HashSet<>(keys));

        // mutating the returned list must not affect the map
        keys.clear();
        assertEquals(2, map.size());
        assertEquals("1", map.get("a"));
    }

    @Test
    public void valuesReturnSnapshot()
    {
        final FastHashMap<String, String> map = new FastHashMap<>();
        map.put("a", "1");
        map.put("b", null);
        map.put("c", "3");

        final List<String> values = map.values();
        assertEquals(3, values.size());
        final Set<String> expected = new HashSet<>();
        expected.add("1");
        expected.add("3");
        expected.add(null);
        assertEquals(expected, new HashSet<>(values));

        values.clear();
        assertEquals(3, map.size());
    }

    @Test
    public void keysAndValuesMatchSize()
    {
        final FastHashMap<Integer, Integer> map = new FastHashMap<>();
        for (int i = 0; i < 500; i++)
        {
            map.put(i, i * 2);
        }
        assertEquals(500, map.keys().size());
        assertEquals(500, map.values().size());
    }

    @Test
    public void randomOperationsMatchHashMap()
    {
        final Random random = new Random(42);
        final FastHashMap<Integer, Integer> map = new FastHashMap<>();
        final Map<Integer, Integer> reference = new HashMap<>();

        for (int i = 0; i < 50_000; i++)
        {
            final int key = random.nextInt(2_000);
            final int op = random.nextInt(10);

            if (op < 5)
            {
                final int value = random.nextInt(1_000_000);
                final Integer expected = reference.put(key, value);
                final Integer actual = map.put(key, value);
                assertEquals(expected, actual, "put mismatch at iteration " + i);
            }
            else if (op < 8)
            {
                final Integer expected = reference.get(key);
                final Integer actual = map.get(key);
                assertEquals(expected, actual, "get mismatch at iteration " + i);
            }
            else
            {
                final Integer expected = reference.remove(key);
                final Integer actual = map.remove(key);
                assertEquals(expected, actual, "remove mismatch at iteration " + i);
            }
        }

        assertEquals(reference.size(), map.size());

        final Set<Integer> expectedKeys = new HashSet<>(reference.keySet());
        final Set<Integer> actualKeys = new HashSet<>(map.keys());
        assertEquals(expectedKeys, actualKeys);

        for (final Map.Entry<Integer, Integer> e : reference.entrySet())
        {
            assertEquals(e.getValue(), map.get(e.getKey()));
        }
    }

    @Test
    public void randomCollisionOperationsMatchHashMap()
    {
        final Random random = new Random(7);
        final FastHashMap<CollisionKey, Integer> map = new FastHashMap<>();
        final Map<CollisionKey, Integer> reference = new HashMap<>();

        for (int i = 0; i < 20_000; i++)
        {
            final CollisionKey key = new CollisionKey(random.nextInt(1_000));
            final int op = random.nextInt(10);

            if (op < 5)
            {
                final int value = random.nextInt(1_000_000);
                final Integer expected = reference.put(key, value);
                final Integer actual = map.put(key, value);
                assertEquals(expected, actual, "put mismatch at iteration " + i);
            }
            else if (op < 8)
            {
                final Integer expected = reference.get(key);
                final Integer actual = map.get(key);
                assertEquals(expected, actual, "get mismatch at iteration " + i);
            }
            else
            {
                final Integer expected = reference.remove(key);
                final Integer actual = map.remove(key);
                assertEquals(expected, actual, "remove mismatch at iteration " + i);
            }
        }

        assertEquals(reference.size(), map.size());
        for (final Map.Entry<CollisionKey, Integer> e : reference.entrySet())
        {
            assertEquals(e.getValue(), map.get(e.getKey()));
        }
    }

    @Test
    public void customKeyUsesEquals()
    {
        final FastHashMap<MapKey, String> map = new FastHashMap<>();
        map.put(new MapKey("a"), "1");

        // equal but not identical key instance
        assertEquals("1", map.get(new MapKey("a")));
        assertNull(map.get(new MapKey("b")));
    }

    /** A value-based key type. */
    private static final class MapKey
    {
        final String name;

        MapKey(final String name)
        {
            this.name = name;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.name);
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (!(o instanceof MapKey other))
            {
                return false;
            }
            return Objects.equals(this.name, other.name);
        }
    }
}
