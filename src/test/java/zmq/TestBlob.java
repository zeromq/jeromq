package zmq;

import java.util.HashMap;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestBlob
{
    @Test
    public void testBlobMap()
    {
        HashMap<Blob, String> map = new HashMap<Blob, String>();

        Blob b = Blob.createBlob("a".getBytes(ZMQ.CHARSET), false);
        map.put(b, "aa");

        assertThat(map.remove(b), notNullValue());
        assertThat(map.size(), is(0));
    }
}
