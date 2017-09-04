package zmq.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

// custom implementation of a collection mapping multiple values, tailored for use in the lib.
// this class is definitely not thread-safe, and allows only one mapping per key-value
// aka if the same value is correlated to a new key, the old mapping is removed.
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
        List<Entry<V, K>> list = new ArrayList<>(inverse.entrySet());
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

    private List<V> getValues(K key)
    {
        List<V> list = data.get(key);
        if (list == null) {
            list = new ArrayList<>();
            data.put(key, list);
        }
        return list;
    }

    public boolean insert(K key, V value)
    {
        K old = inverse.get(value);
        if (old != null) {
            removeData(old, value);
        }
        boolean inserted = getValues(key).add(value);
        if (inserted) {
            inverse.put(value, key);
        }
        return inserted;
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
            return removeData(key, value);
        }
        return false;
    }

    public boolean remove(K key, V value)
    {
        boolean removed = removeData(key, value);
        if (removed) {
            inverse.remove(value);
        }
        return removed;
    }

    private boolean removeData(K key, V value)
    {
        boolean removed = false;
        List<V> list = data.get(key);
        if (list != null) {
            removed = list.remove(value);
            if (list.isEmpty()) {
                data.remove(key);
            }
        }
        return removed;
    }

    @Override
    public String toString()
    {
        return data.toString();
    }
}
