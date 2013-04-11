/*
    Copyright other contributors as noted in the AUTHORS file.
        
    This file is part of 0MQ.
            
    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.
            
    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.
        
    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package zmq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MultiMap<K extends Comparable<? super K>, V> implements Map<K, V> {

    private long id;
    private final HashMap<Long, V> values;
    private final TreeMap<K, ArrayList<Long>> keys;

    public MultiMap () {
        id = 0;
        values = new HashMap<Long, V>();
        keys = new TreeMap<K, ArrayList<Long>>();
    }
    
    public class MultiMapEntry implements Map.Entry<K, V> {

        private K key;
        private V value;
        public MultiMapEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }
        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            V old = this.value;
            this.value = value;
            return old;
        }
        
    }
    public class MultiMapEntrySet implements Set<Map.Entry<K,V>>, Iterator<Map.Entry<K, V>> {

        private MultiMap<K,V> map;
        private Iterator<Map.Entry<K, ArrayList<Long>>> it;
        private Iterator<Long> iit;
        private K key;
        private long id;
        public MultiMapEntrySet(MultiMap<K,V> map) {
            this.map = map;
        }
        @Override
        public boolean add(java.util.Map.Entry<K, V> arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(
                Collection<? extends java.util.Map.Entry<K, V>> arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(Object arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEmpty() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<java.util.Map.Entry<K, V>> iterator() {
            it = map.keys.entrySet().iterator();
            return this;
        }

        @Override
        public boolean remove(Object arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object[] toArray() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T[] toArray(T[] arg0) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean hasNext() {
            if (iit == null || !iit.hasNext()) {
                
                if (!it.hasNext()) {
                    return false;
                }
                
                Map.Entry<K, ArrayList<Long>> item = it.next();
                key = item.getKey();
                iit = item.getValue().iterator();
            } 
            
            return true;
                
        }
        @Override
        public Map.Entry<K, V> next() {
            id = iit.next();
            return new MultiMapEntry(key, map.values.get(id));
        }
        
        @Override
        public void remove() {
            iit.remove();
            map.values.remove(id);
            if (map.keys.get(key).isEmpty())
                it.remove();
        }
        
    }
    
    @Override
    public void clear() {
        keys.clear();
        values.clear();
    }

    @Override
    public boolean containsKey(Object key) {
        return keys.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return values.containsValue(value);
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new MultiMapEntrySet(this);
    }

    @Override
    public V get(Object key) {
        ArrayList<Long> l = keys.get(key);
        if (l == null)
            return null;
        return values.get(l.get(0));
    }

    @Override
    public boolean isEmpty() {
        return keys.isEmpty();
    }

    @Override
    public Set<K> keySet() {
        return keys.keySet();
    }

    @Override
    public V put(K key, V value) {
        ArrayList<Long> ids = keys.get(key);
        if (ids == null) {
            ids = new ArrayList<Long>();
            ids.add(id);
            keys.put(key, ids);
        } else {
            ids.add(id);
        }
        values.put(id, value);
        id++;
        
        return null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> src) {

        for (Entry <? extends K, ? extends V> o : src.entrySet ()) {
            put(o.getKey(), o.getValue());
        }
    }

    @Override
    public V remove (Object key) 
    {
        ArrayList <Long> l = keys.get (key);
        if (l == null)
            return null;
        V old = values.remove (l.remove(0));
        if (l.isEmpty ())
            keys.remove (key);

        return old;
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public Collection<V> values() {
        return values.values();
    }

}
