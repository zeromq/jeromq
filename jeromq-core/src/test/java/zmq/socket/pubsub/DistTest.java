package zmq.socket.pubsub;

import org.junit.Before;
import org.junit.Test;

import zmq.Msg;
import zmq.ZObject;
import zmq.pipe.Pipe;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
        Pipe[] pipes = Pipe.pair(new ZObject[] {new Parent(), new Parent()}, new int[] {0, 1}, new boolean[2]);
        first = pipes[0];
        second = pipes[1];
        assertThat(dist.eligible(), is(0));
        assertThat(dist.active(), is(0));

        dist.attach(first);
        assertThat(dist.eligible(), is(1));
        assertThat(dist.active(), is(1));
    }

    @Test
    public void testActivatedWhileActive()
    {
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
        Msg msg = new Msg();
        msg.setFlags(Msg.MORE);
        dist.sendToAll(msg);
        // no change in eligible/active states
        assertThat(dist.eligible(), is(1));
        assertThat(dist.active(), is(1));

        dist.attach(second);
        assertThat(dist.eligible(), is(2));
        // active should not have changed, as we are in the middle a sending a multi-part message
        assertThat(dist.active(), is(1));

        // no change in eligible/active states
        dist.activated(second);
        assertThat(dist.eligible(), is(2));
        assertThat(dist.active(), is(1));

        dist.sendToAll(new Msg());
        // end of multi-part message
        assertThat(dist.eligible(), is(2));
        assertThat(dist.active(), is(2));
    }

    @Test
    public void testAttachedWhenSendingNoMoreMessage()
    {
        Msg msg = new Msg();
        dist.sendToAll(msg);
        // no change in eligible/active states
        assertThat(dist.eligible(), is(1));
        assertThat(dist.active(), is(1));

        dist.attach(second);
        assertThat(dist.eligible(), is(2));
        // active should have changed, as we are NOT in the middle a sending a multi-part message
        assertThat(dist.active(), is(2));

        dist.sendToAll(msg);
        assertThat(dist.eligible(), is(2));
        assertThat(dist.active(), is(2));

        // no change in eligible/active states
        dist.activated(second);
        assertThat(dist.eligible(), is(2));
        assertThat(dist.active(), is(2));
    }

    @Test
    public void testActivatedAfterReachingHWM()
    {
        dist.attach(second);
        assertThat(dist.eligible(), is(2));
        assertThat(dist.active(), is(2));

        // simulate HWM
        second.write(new Msg());

        // mark the pipe as not active and not eligible
        dist.sendToAll(new Msg());

        // now, second pipe has been put out of the active and eligible ones
        assertThat(dist.eligible(), is(1));
        assertThat(dist.active(), is(1));

        // reactivate pipe having reached HWM
        dist.activated(second);
        assertThat(dist.eligible(), is(2));
        assertThat(dist.active(), is(2));
    }

    @Test
    public void testMatch()
    {
        dist.attach(second);
        assertThat(dist.matching(), is(0));

        // simulate HWM
        second.write(new Msg());

        // mark the pipe as not active and not eligible
        dist.sendToAll(new Msg());

        dist.match(first);
        assertThat(dist.matching(), is(1));

        dist.match(second);
        // second pipe is not a matching one
        assertThat(dist.matching(), is(1));
    }
}
