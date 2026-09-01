package org.jugsaxony.demo4;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FastHashMap<K, V> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final double LOAD_FACTOR = 0.75;

    private K[] keys;
    private V[] values;
    private boolean[] occupied;
    private boolean[] deleted;
    private int size;
    private int capacity;

    @SuppressWarnings("unchecked")
    public FastHashMap() {
        this.capacity = DEFAULT_CAPACITY;
        this.keys = (K[]) new Object[capacity];
        this.values = (V[]) new Object[capacity];
        this.occupied = new boolean[capacity];
        this.deleted = new boolean[capacity];
        this.size = 0;
    }

    private int hash(K key) {
        int h = key.hashCode();
        h ^= (h >>> 16);
        return Math.abs(h) % capacity;
    }

    public V put(final K key, final V value) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }

        if (size >= capacity * LOAD_FACTOR) {
            resize();
        }

        int index = hash(key);
        int firstDeleted = -1;

        while (occupied[index]) {
            if (Objects.equals(keys[index], key)) {
                V old = values[index];
                values[index] = value;
                return old;
            }
            if (deleted[index] && firstDeleted == -1) {
                firstDeleted = index;
            }
            index = (index + 1) % capacity;
        }

        int targetIndex = (firstDeleted != -1) ? firstDeleted : index;
        keys[targetIndex] = key;
        values[targetIndex] = value;
        occupied[targetIndex] = true;
        deleted[targetIndex] = false;
        size++;
        return null;
    }

    public V get(final K key) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }

        int index = hash(key);
        int start = index;

        while (occupied[index] || deleted[index]) {
            if (occupied[index] && Objects.equals(keys[index], key)) {
                return values[index];
            }
            index = (index + 1) % capacity;
            if (index == start) break;
        }
        return null;
    }

    public V remove(final K key) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }

        int index = hash(key);
        int start = index;

        while (occupied[index] || deleted[index]) {
            if (occupied[index] && Objects.equals(keys[index], key)) {
                V old = values[index];
                keys[index] = null;
                values[index] = null;
                occupied[index] = false;
                deleted[index] = true;
                size--;
                return old;
            }
            index = (index + 1) % capacity;
            if (index == start) break;
        }
        return null;
    }

    public int size() {
        return size;
    }

    public List<K> keys() {
        List<K> result = new ArrayList<>(size);
        for (int i = 0; i < capacity; i++) {
            if (occupied[i]) {
                result.add(keys[i]);
            }
        }
        return result;
    }

    public List<V> values() {
        List<V> result = new ArrayList<>(size);
        for (int i = 0; i < capacity; i++) {
            if (occupied[i]) {
                result.add(values[i]);
            }
        }
        return result;
    }

    public void clear() {
        capacity = DEFAULT_CAPACITY;
        keys = (K[]) new Object[capacity];
        values = (V[]) new Object[capacity];
        occupied = new boolean[capacity];
        deleted = new boolean[capacity];
        size = 0;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        K[] oldKeys = keys;
        V[] oldValues = values;
        boolean[] oldOccupied = occupied;
        int oldCapacity = capacity;

        capacity *= 2;
        keys = (K[]) new Object[capacity];
        values = (V[]) new Object[capacity];
        occupied = new boolean[capacity];
        deleted = new boolean[capacity];
        size = 0;

        for (int i = 0; i < oldCapacity; i++) {
            if (oldOccupied[i]) {
                put(oldKeys[i], oldValues[i]);
            }
        }
    }
}
