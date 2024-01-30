package zmq.io;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;

import org.junit.Test;
import org.zeromq.ZMQ;

import zmq.Msg;

public class MsgsTest
{
    @Test
    public void testPutMax()
    {
        assertPutStartsWith(255);
    }

    @Test
    public void testPutMiddle()
    {
        assertPutStartsWith(127);
    }

    @Test
    public void testPutMiddlePlusOne()
    {
        assertPutStartsWith(128);
    }

    @Test
    public void testPutZero()
    {
        assertPutStartsWith(0);
    }

    @Test
    public void testPutEncodesWithLength()
    {
        String test = "test1";

        Msg msg = new Msg(test.length() + 1);

        msg.putShortString(test);

        String read = new String(msg.data(), ZMQ.CHARSET);
        assertThat(read, is("\5test1"));
    }

    private void assertPutStartsWith(int max)
    {
        Msg msg = new Msg(max + 1);

        byte[] bytes = new byte[max];
        Arrays.fill(bytes, (byte) 'a');
        String string = new String(bytes);

        msg.putShortString(string);
        boolean rc = Msgs.startsWith(msg, string, true);

        assertThat(rc, is(true));
    }
}
