package zmq.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class TestUtils
{
    @Test
    public void testRealloc()
    {
        Integer[] src = new Integer[] { 1, 3, 5 };
        Integer[] dest = Utils.realloc(Integer.class, src, 3, true);

        assertThat(src.length, is(3));
        assertThat(src, is(dest));

        dest = Utils.realloc(Integer.class, src, 5, true);

        assertThat(dest.length, is(5));
        assertThat(dest[0], is(1));
        assertThat(dest[1], is(3));
        assertThat(dest[2], is(5));
        assertThat(dest[4], nullValue());

        dest = Utils.realloc(Integer.class, src, 6, false);
        assertThat(dest.length, is(6));
        assertThat(dest[0], nullValue());
        assertThat(dest[1], nullValue());
        assertThat(dest[2], nullValue());
        assertThat(dest[3], is(1));
        assertThat(dest[4], is(3));
        assertThat(dest[5], is(5));

        src = new Integer[] { 1, 3, 5, 7, 9, 11 };
        dest = Utils.realloc(Integer.class, src, 4, false);
        assertThat(dest.length, is(4));
        assertThat(dest[0], is(1));
        assertThat(dest[1], is(3));
        assertThat(dest[2], is(5));
        assertThat(dest[3], is(7));

        dest = Utils.realloc(Integer.class, src, 3, true);
        assertThat(dest.length, is(3));
        assertThat(dest[0], is(7));
        assertThat(dest[1], is(9));
        assertThat(dest[2], is(11));

    }
}
