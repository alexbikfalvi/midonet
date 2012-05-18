/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.util.collections;

import java.util.HashMap;

/**
 * HashMap-wrapping implementation of a {@link TypedMap}.
 * See {@link TypedMap} for an example of the intended usage.
 */
public class TypedHashMap<K, V> implements TypedMap<K, V> {
    private HashMap<K, V> map;

    @Override
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public V remove(K key) {
        return map.remove(key);
    }

    @Override
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    @Override
    public V put(K key, V value) {
        return map.put(key, value);
    }

    @Override
    public void clear() {
        map.clear();
    }
}
