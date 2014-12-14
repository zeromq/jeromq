package org.zeromq;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZActor.Actor;
import org.zeromq.ZMQ.Socket;

import zmq.ZError;

public class TestZActor
{
    private class MiniActor extends ZActor.SimpleActor
    {
    }

    @Test
    public void testMinimalistic()
    {
        Actor acting = new ZActor.SimpleActor()
        {
            @Override
            public List<Socket> createSockets(ZContext ctx, Object[] args)
            {
                assert ("TEST".equals(args[0]));
                return Arrays.asList(ctx.createSocket(ZMQ.PUB));
            }

            @Override
            public boolean backstage(Socket pipe, ZPoller poller, int events)
            {
                String string = pipe.recvStr();
                if ("HELLO".equals(string)) {
                    pipe.send("WORLD");
                    return false;
                }
                return true;
            }

        };
        ZActor actor = new ZActor(acting, "LOCK", Arrays.asList("TEST").toArray());
        Socket pipe = actor.pipe();
        boolean rc = pipe.send("HELLO");
        Assert.assertTrue("Unable to send a message through pipe", rc);
        ZMsg msg = actor.recv();
        String world = msg.popString();
        Assert.assertEquals("No matching response from actor", "WORLD", world);

        msg = actor.recv();
        Assert.assertNull("Able te receive a message from a locked actor", msg);

        rc = actor.sign();
        Assert.assertFalse("Locked actor is still here", rc);

        rc = actor.send("whatever");
        Assert.assertFalse("Able to send a message to a locked actor", rc);

        try {
            rc = pipe.send("boom ?!");
            Assert.assertTrue("actor pipe was closed pretty fast", rc);
        }
        catch (ZMQException e) {
            int errno = e.getErrorCode();
            Assert.assertEquals("Expected exception has the wrong code",  ZError.ETERM, errno);
        }
        System.out.println(".");
    }

    @Test
    public void testRecreateAgent()
    {
        MiniActor acting = new MiniActor()
        {
            private int counter = 0;

            @Override
            public List<Socket> createSockets(ZContext ctx, Object[] args)
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

            public boolean destroyed(Socket pipe, ZPoller poller)
            {
                if (counter == 2) {
                    System.out.print(".Acting Finished.");
                    return false;
                }
                // recreate a new agent
                return true;
            }
        };
        ZActor actor = new ZActor(acting, UUID.randomUUID().toString(), Arrays.asList("TEST").toArray());
        ZAgent agent = actor.agent();

        agent = actor;
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
        world = msg.popString();
        counter = msg.popString();
        assert (msg != null);
        assert ("WORLD".equals(world));
        assert ("2".equals(counter));

        msg = agent.recv();
        Assert.assertNull("Able te receive a message from a locked actor", msg);

        rc = agent.sign();
        Assert.assertFalse("Locked actor is still here", rc);

        rc = agent.send("whatever");
        Assert.assertFalse("Able to send a message to a locked actor", rc);

        try {
            rc = pipe.send("boom ?!");
            Assert.assertTrue("actor pipe was closed pretty fast", rc);
        }
        catch (ZMQException e) {
            int errno = e.getErrorCode();
            Assert.assertEquals("Expected exception has the wrong code",  ZError.ETERM, errno);
        }
        System.out.println();
    }
}
