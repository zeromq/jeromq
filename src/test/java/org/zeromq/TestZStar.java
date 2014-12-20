package org.zeromq;

import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZStar.Fortune;

public class TestZStar
{
    private final class BlackHole implements ZStar.Fortune
    {
        @Override
        public String premiere(Socket mic, Object[] args)
        {
            return "Test " + Arrays.toString(args);
        }

        @Override
        public ZStar.Star create(ZContext ctx, Socket pipe, Selector sel, int count,
                ZStar.Star previous, Object[] args)
        {
            return new NoNo();
        }

        @Override
        public boolean interview(Socket mic)
        {
            System.out.print("Fortune is not here anymore ...");
            // tell that star is not here anymore
            return true;
        }

        @Override
        public void party(ZContext ctx)
        {
            // do nothing
            System.out.print(" Cleaning the remains.. ");
        }
    }

    private final class NoNo implements ZStar.Star
    {
        @Override
        public void prepare()
        {
            // do nothing
        }

        @Override
        public int breathe()
        {
            return -1;
        }

        @Override
        public boolean act(int events)
        {
            return false;
        }

        @Override
        public boolean entract()
        {
            return false;
        }

        @Override
        public boolean renews()
        {
            return false;
        }
    }

    @Test
    public void testNoStar()
    {
        System.out.print("No star: ");
        ZStar.Fortune fortune = new BlackHole();
        ZStar.Entourage entourage = new ZStar.Entourage()
        {
            @Override
            public void breakaleg(ZContext ctx, Fortune fortune, Socket phone,
                    Object[] bags)
            {
                // Crepi il lupo!
            }

            @Override
            public void party(ZContext ctx)
            {
                // right now there are some random closing issues
                ZStar.party(30, TimeUnit.MILLISECONDS);
                // waited a bit here seems to arrange that.
                // no user penalty cost, the show is over.
            }

        };
        ZStar star = new ZStar(fortune, "motdelafin", Arrays.asList("TEST", entourage).toArray());
        ZMsg msg = star.recv();
        Assert.assertNull("Able to receive a message from a black hole",  msg);
        boolean rc = star.sign();
        Assert.assertFalse("Able to detect the presence of a black hole",  rc);
        rc = star.send("whatever");
        Assert.assertFalse("Able to send a command to a black hole",  rc);

        // don't try it
        // rc = star.pipe().send("boom ?!");
//        star.retire();
        System.out.println(".");
    }
}
