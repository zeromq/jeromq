package zmq.socket.pubsub;

import org.junit.Before;
import org.junit.Test;
import zmq.Msg;
import zmq.ZObject;
import zmq.pipe.Pipe;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DistTest
{
    private Dist dist;
    private Pipe first;
    private Pipe second;

    private static final class Parent extends ZObject
    {
        Parent()
        {
            super(null, 0);
        }
    }

    @Before
    public void setup()
    {
        dist = new Dist();
        Pipe[] pipes = Pipe.pair(new ZObject[] {new Parent(), new Parent()}, new int[2], new boolean[2]);
        first = pipes[0];
        second = pipes[1];
        assertThat(dist.eligible(), is(0));
        assertThat(dist.active(), is(0));
    }

    @Test
    public void testActivated()
    {
        dist.attach(first);
        assertThat(dist.eligible(), is(1));
        assertThat(dist.active(), is(1));

        // check that there is no modification when activating a pipe straight away
        dist.activated(first);
        assertThat(dist.eligible(), is(1));
        assertThat(dist.active(), is(1));

        // check re-entrance: no modification
        dist.activated(first);
        assertThat(dist.eligible(), is(1));
        assertThat(dist.active(), is(1));
    }

    @Test
    public void testAttachedWhenSendingMultipartMessage()
    {
        testActivated();

        Msg msg = new Msg();
        msg.setFlags(Msg.MORE);
        dist.sendToMatching(msg);

        dist.attach(second);
        assertThat(dist.eligible(), is(2));
        assertThat(dist.active(), is(1));

        dist.activated(second);
        assertThat(dist.eligible(), is(2));
        assertThat(dist.active(), is(1));
    }

    @Test
    public void testAttachedWhenSendingNoMoreMessage()
    {
        testActivated();

        Msg msg = new Msg();
        dist.sendToMatching(msg);

        dist.attach(second);
        assertThat(dist.eligible(), is(2));
        assertThat(dist.active(), is(2));

        dist.activated(second);
        assertThat(dist.eligible(), is(2));
        assertThat(dist.active(), is(2));
    }
}
