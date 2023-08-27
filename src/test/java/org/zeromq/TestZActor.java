package org.zeromq;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZActor.Actor;
import org.zeromq.ZMQ.Socket;

public class TestZActor
{
    @Test
    public void testMinimalistic()
    {
        final Actor acting = new ZActor.SimpleActor()
        {
            @Override
            public List<Socket> createSockets(ZContext ctx, Object... args)
            {
                assert ("TEST".equals(args[0]));
                return Collections.singletonList(ctx.createSocket(SocketType.PUB));
            }

            @Override
            public boolean backstage(Socket pipe, ZPoller poller, int events)
            {
                String string = pipe.recvStr();
                if ("HELLO".equals(string)) {
                    pipe.send("WORLD");
                }
                if ("QUIT".equals(string)) {
                    pipe.send("EXIT");
                    return false;
                }
                return true;
            }
        };
        final ZContext context = new ZContext();
        final ZActor.Duo duo = new ZActor.Duo(acting, new ZActor.SimpleActor());
        final ZActor actor = new ZActor(context, duo, "LOCK", Collections.singletonList("TEST").toArray());
        final Socket pipe = actor.pipe();
        boolean rc = pipe.send("HELLO");
        Assert.assertTrue("Unable to send a message through pipe", rc);
        ZMsg msg = actor.recv();
        String world = msg.popString();
        Assert.assertEquals("No matching response from actor", "WORLD", world);

        rc = pipe.send("QUIT");
        msg = actor.recv();
        Assert.assertNotNull("Unable to receive EXIT message", msg);

        msg = actor.recv();
        Assert.assertNull("Able to receive a message from a locked actor", msg);

        rc = actor.sign();
        Assert.assertFalse("Locked actor is still here", rc);

        rc = actor.send("whatever");
        Assert.assertFalse("Able to send a message to a locked actor", rc);

        context.close();
        System.out.println(".");
    }

    @Test
    public void testRecreateAgent()
    {
        ZActor.Actor acting = new ZActor.SimpleActor()
        {
            private int counter = 0;

            @Override
            public List<Socket> createSockets(ZContext ctx, Object... args)
            {
                ++counter;
                System.out.print(".Acting Ready for a hello world.");
                assert ("TEST".equals(args[0]));
                return super.createSockets(ctx, args);
            }

            @Override
            public boolean backstage(Socket pipe, ZPoller poller, int events)
            {
                String string = pipe.recvStr();
                if ("HELLO".equals(string)) {
                    System.out.print("Hi! ");
                    pipe.send("WORLD", ZMQ.SNDMORE);
                    pipe.send(Integer.toString(counter));
                    return false;
                }
                return true;
            }

            @Override
            public boolean destroyed(ZContext ctx, Socket pipe, ZPoller poller)
            {
                if (counter == 2) {
                    System.out.print(".Acting Finished.");
                    return false;
                }
                // recreate a new agent
                return true;
            }
        };
        ZContext context = new ZContext();
        ZActor actor = new ZActor(context, acting, UUID.randomUUID().toString(), Collections.singletonList("TEST").toArray());
        ZAgent agent = actor.agent();

        agent = actor.agent();

        Socket pipe = agent.pipe();
        boolean rc = pipe.send("HELLO");
        assert (rc);
        ZMsg msg = actor.recv();
        String world = msg.popString();
        String counter = msg.popString();
        assert ("WORLD".equals(world));
        assert ("1".equals(counter));

        rc = actor.send("HELLO");
        assert (rc);
        msg = agent.recv();
        Assert.assertNotNull("unable to receive a message from an actor", msg);
        world = msg.popString();
        counter = msg.popString();
        assert (msg != null);
        assert ("WORLD".equals(world));
        assert ("2".equals(counter));

        msg = agent.recv();
        Assert.assertNull("Able to receive a message from a locked actor", msg);

        rc = agent.sign();
        Assert.assertFalse("Locked actor is still here", rc);

        rc = agent.send("whatever");
        Assert.assertFalse("Able to send a message to a locked actor", rc);

        context.close();

        System.out.println();
    }
}
