package zmq.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;

import org.junit.Test;

import zmq.ZMQ;

public class TestBlob
{
    @Test
    public void testBlobMap()
    {
        HashMap<Blob, String> map = new HashMap<>();

        Blob b = Blob.createBlob("a".getBytes(ZMQ.CHARSET));
        map.put(b, "aa");

        assertThat(map.remove(b), notNullValue());
        assertThat(map.size(), is(0));
    }
}
