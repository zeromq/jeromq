package zmq.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Collection;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

public class MultimapTest
{
    private MultiMap<Long, Integer> map;

    @Before
    public void setup()
    {
        map = new MultiMap<>();

        final Integer value = 42;
        boolean rc = map.insert(1L, value);
        assertThat(rc, is(true));
    }

    @Test
    public void testInsertTwice()
    {
        boolean rc = map.insert(1L, 42);
        assertThat(rc, is(true));
        assertThat(map.entries().size(), is(1));
    }

    @Test
    public void testKey()
    {
        Long key = map.key(42);
        assertThat(key, is(1L));
    }

    @Test
    public void testContains()
    {
        assertThat(map.contains(42), is(true));
        assertSize(1);
    }

    @Test
    public void testEmpty()
    {
        assertThat(map.isEmpty(), is(false));
    }

    @Test
    public void testClear()
    {
        map.clear();
        assertSize(0);
    }

    @Test
    public void testHasValue()
    {
        assertThat(map.hasValues(1L), is(true));
        assertThat(map.hasValues(42L), is(false));
    }

    @Test
    public void testRemoveValue()
    {
        boolean rc = map.remove(42);
        assertThat(rc, is(true));

        assertSize(0);
    }

    @Test
    public void testRemoveWrongValue()
    {
        boolean rc = map.remove(41);
        assertThat(rc, is(false));

        assertSize(1);
    }

    @Test
    public void testRemoveKey()
    {
        Collection<Integer> removed = map.remove(1L);
        assertThat(removed, is(Collections.singletonList(42)));

        assertSize(0);
    }

    @Test
    public void testRemoveWrongKey()
    {
        Collection<Integer> removed = map.remove(1L);
        assertThat(removed, is(Collections.singletonList(42)));

        assertSize(0);
    }

    private void assertSize(int size)
    {
        assertThat(map.entries().size(), is(size));
        assertThat(map.isEmpty(), is(size == 0));
    }
}
