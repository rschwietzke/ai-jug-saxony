# Specification: FastHashMap

## Overview
`FastHashMap` is a high-performance, non-thread-safe, open-addressing hash map implementation. It is designed as a Maven subproject targeting Java 21 and JUnit 6.

## Requirements
- **Generics**: Supports keys of type `K` and values of type `V`.
- **Null Handling**: 
    - Null keys are **strictly forbidden** (should throw `NullPointerException`).
    - Null values are **allowed**.
- **Concurrency**: Not thread-safe.
- **Capacity**: Unbound (dynamically resizes).
- **Collision Strategy**: Linear Probing (chosen for cache efficiency and simplicity).
- **Package**: `org.jugsaxony.demo4`

## Method Signatures
- `public FastHashMap()`: Initializes an empty map.
- `public V get(final K key)`: Returns the value associated with the key, or `null` if not found.
- `public V put(final K key, final V value)`: Associates the specified value with the specified key. Returns the previous value associated with the key.
- `public V remove(final K key)`: Removes the mapping for the specified key. Returns the removed value.
- `public int size()`: Returns the number of key-value mappings.
- `public List<K> keys()`: Returns a list of all keys currently in the map.
- `public List<V> values()`: Returns a list of all values currently in the map.
- `public void clear()`: Removes all mappings from the map.

## Implementation Plan

### Phase 1: Project Structure
1. Create a Maven subproject within `demo4/` with the following structure:
   - `src/main/java/org/jugsaxony/demo4/FastHashMap.java`
   - `src/test/java/org/jugsaxony/demo4/FastHashMapTest.java`
   - `pom.xml` (configured for Java 21 and JUnit 6).

### Phase 2: Core Implementation
1. **Internal Storage**: 
   - Use two parallel arrays: `K[] keys` and `V[] values`.
   - Implement a `Tombstone` mechanism or specialized markers to handle deletions in open addressing.
2. **Hashing**:
   - Implement a hash function that spreads bits to minimize collisions.
3. **Linear Probing**:
   - `put`: Find the first available slot (empty or tombstone).
   - `get`: Probe until the key is found or an empty slot is encountered.
   - `remove`: Mark the slot as a tombstone to maintain probe chains.
4. **Resizing**:
   - Define a load factor (e.g., 0.75).
   - Implement `resize()` to double the array size and re-hash all existing entries when the threshold is reached.

### Phase 3: API Completion
1. Implement `size()`, `clear()`, `keys()`, and `values()`.
2. Ensure `keys()` and `values()` return fresh `ArrayList` copies.

### Phase 4: Testing
1. **Unit Tests**:
   - Basic put/get/remove operations.
   - Handling of null values.
   - Verification that null keys throw `NullPointerException`.
   - Testing map resizing (growth).
   - Testing `clear()` and `size()`.
   - Testing collision scenarios (multiple keys hashing to same index).
   - Boundary tests (empty map, large number of elements).
