package zmq.socket.pubsub;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

import zmq.ZObject;
import zmq.pipe.Pipe;

public class MTrieTest
{
    private Pipe pipe;

    private static final byte[] prefix = { 1, 2, 3, 4, 5 };

    @Before
    public void setUp()
    {
        ZObject object = new ZObject(null, 0)
        {
        };
        Pipe[] pair = Pipe.pair(new ZObject[] { object, object }, new int[2], new boolean[2]);
        pipe = pair[0];
    }

    @Test
    public void testRemoveIssue476()
    {
        Mtrie mtrie = new Mtrie();

        boolean rc = mtrie.add(prefix, prefix.length, pipe);
        assertThat(rc, is(true));

        rc = mtrie.rm(prefix, prefix.length, pipe);
        assertThat(rc, is(true));
    }
}
