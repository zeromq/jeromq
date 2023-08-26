package zmq.pipe;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.Msg;
import zmq.ZMQ;

public class YQueueTest
{
    @Test
    public void testReuse()
    {
        // yqueue has a first empty entry
        YQueue<Msg> p = new YQueue<>(3);

        Msg m1 = new Msg(1);
        Msg m2 = new Msg(2);
        Msg m3 = new Msg(3);
        Msg m4 = new Msg(4);
        Msg m5 = new Msg(5);
        Msg m6 = new Msg(6);
        Msg m7 = new Msg(7);
        m7.put("1234567".getBytes(ZMQ.CHARSET));

        p.push(m1);
        assertThat(p.backPos(), is(1));

        p.push(m2); // might allocated new chunk
        p.push(m3);
        assertThat(p.backPos(), is(3));

        assertThat(p.frontPos(), is(0));
        p.pop();
        p.pop();
        p.pop(); // offer the old chunk
        assertThat(p.frontPos(), is(3));

        p.push(m4);
        p.push(m5); // might reuse the old chunk
        p.push(m6);

        assertThat(p.backPos(), is(0));

    }
}
