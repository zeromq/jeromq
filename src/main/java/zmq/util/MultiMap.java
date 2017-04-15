package zmq.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

// custom implementation of a collection mapping multiple values, tailored for use in the lib
public final class MultiMap<K extends Comparable<? super K>, V>
{
    // sorts entries according to the natural order of the keys
    private final class EntryComparator implements Comparator<Entry<V, K>>
    {
        @Override
        public int compare(Entry<V, K> first, Entry<V, K> second)
        {
            return first.getValue().compareTo(second.getValue());
        }
    }

    private final Comparator<? super Entry<V, K>> comparator = new EntryComparator();

    // where all the data will be put
    private final Map<K, List<V>> data;
    // inverse mapping to speed-up the process
    private final Map<V, K> inverse;

    public MultiMap()
    {
        data = new HashMap<>();
        inverse = new HashMap<>();
    }

    public void clear()
    {
        data.clear();
        inverse.clear();
    }

    public Collection<Entry<V, K>> entries()
    {
        ArrayList<Entry<V, K>> list = new ArrayList<>(inverse.entrySet());
        Collections.sort(list, comparator);
        return list;
    }

    public Collection<V> values()
    {
        return inverse.keySet();
    }

    public V find(V copy)
    {
        K key = inverse.get(copy);
        if (key != null) {
            List<V> list = data.get(key);
            return list.get(list.indexOf(copy));
        }
        return null;
    }

    public boolean hasValues(K key)
    {
        List<V> list = data.get(key);
        if (list == null) {
            return false;
        }
        return !list.isEmpty();
    }

    public boolean isEmpty()
    {
        return inverse.isEmpty();
    }

    private List<V> getList(K key)
    {
        List<V> list = data.get(key);
        if (list == null) {
            list = new ArrayList<V>();
            data.put(key, list);
        }
        return list;
    }

    public void insert(K key, V value)
    {
        getList(key).add(value);

        K old = inverse.put(value, key);
        assert (old == null);
    }

    private void putAll(K key, Collection<V> values)
    {
        getList(key).addAll(values);

        for (V value : values) {
            K old = inverse.put(value, key);
            assert (old == null);
        }
    }

    public void putAll(MultiMap<K, V> src)
    {
        for (Entry<K, List<V>> entry : src.data.entrySet()) {
            putAll(entry.getKey(), entry.getValue());
        }
    }

    public Collection<V> remove(K key)
    {
        List<V> removed = data.remove(key);
        if (removed != null) {
            for (V val : removed) {
                inverse.remove(val);
            }
        }
        return removed;
    }

    public boolean remove(V value)
    {
        K key = inverse.remove(value);
        if (key != null) {
            return getList(key).remove(value);
        }
        return false;
    }

    public void remove(Entry<V, K> entry)
    {
        K key = entry.getValue();
        V value = entry.getKey();

        List<V> list = data.get(key);
        if (list != null) {
            list.remove(value);
        }
        inverse.remove(value);
    }

    @Override
    public String toString()
    {
        return data.toString();
    }
}
