package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZStar.Fortune;
import org.zeromq.ZStar.Star;

public class TestZStar
{
    private final class BlackHole implements ZStar.Fortune
    {
        @Override
        public String premiere(Socket mic, Object... args)
        {
            return "Test " + Arrays.toString(args);
        }

        @Override
        public Star create(ZContext ctx, Socket pipe, int count, Star previous, Object... args)
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
        final AtomicBoolean ended = new AtomicBoolean();
        ZStar.Entourage entourage = new ZStar.Entourage()
        {
            @Override
            public void breakaleg(ZContext ctx, Fortune fortune, Socket phone, Object... bags)
            {
                // Crepi il lupo!
            }

            @Override
            public void party(ZContext ctx)
            {
                ended.set(true);
                // right now there are some random closing issues
                ZStar.party(30, TimeUnit.MILLISECONDS);
                // waited a bit here seems to arrange that.
                // no user penalty cost, the show is over.
            }

        };
        ZStar star = new ZStar(fortune, "motdelafin", Arrays.asList("TEST", entourage).toArray());
        ZMsg msg = star.recv();
        assertThat("Able to receive a message from a black hole", msg, nullValue());
        boolean rc = star.sign();
        assertThat("Able to detect the presence of a black hole", rc, is(false));
        rc = star.send("whatever");
        assertThat("Able to send a command to a black hole", rc, is(false));

        star.exit().awaitSilent();
        assertThat(ended.get(), is(true));
        // don't try it
        // rc = star.pipe().send("boom ?!");
        System.out.println(".");
    }

    @Test
    public void testNoStarExternalContext()
    {
        System.out.print("No star: ");
        ZStar.Fortune fortune = new BlackHole();
        final AtomicBoolean ended = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(1);
        ZStar.Entourage entourage = new ZStar.Entourage()
        {
            @Override
            public void breakaleg(ZContext ctx, Fortune fortune, Socket phone, Object... bags)
            {
                try {
                    latch.await(200, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void party(ZContext ctx)
            {
                ended.set(true);
            }
        };
        ZContext ctx = new ZContext();
        ZStar star = new ZStar(ctx, fortune, "motdelafin", Arrays.asList("TEST", entourage).toArray());
        ctx.close();
        latch.countDown();

        star.exit().awaitSilent();
        assertThat(ended.get(), is(true));
        System.out.println(".");
    }
}
