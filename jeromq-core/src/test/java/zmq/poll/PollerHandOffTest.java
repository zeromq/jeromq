package zmq.poll;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import zmq.Ctx;

/**
 * A handle that is retired and a handle that is added for the <b>same channel</b> can reach one
 * registration pass of {@link Poller#run()}. That is what happens on a TCP connect:
 * {@code AbstractSocketConnecter.connectEvent} removes its handle and sends the connected channel
 * to the session, and {@code StreamEngine.plug} adds a new handle for that channel. Normally the
 * two changes are one poller iteration apart; when the connect completion and the I/O thread's
 * mailbox are returned by the same select, they land in the same pass.
 * <p>
 * A cancelled {@link java.nio.channels.SelectionKey} is only deregistered by the next selection
 * operation, so in that pass {@code keyFor} still returns the old, invalid key for the new handle:
 * it is neither {@code null} (register) nor valid (update interest ops), and the new handle stays
 * in the table without a key until something else sets {@code retired} again. For a connecting
 * socket that means the ZMTP greeting is never sent and the connection sits idle until the
 * handshake timer ({@code ZMQ_HANDSHAKE_IVL}, 30 s by default) tears it down and reconnects.
 * <p>
 * The tests below force the two changes into one pass from inside an event callback, which makes
 * the race deterministic without sockets.
 */
public class PollerHandOffTest
{
    private static final int  ITERATIONS        = 20;
    private static final long AFTER_PASS_MILLIS = 20;
    private static final long TIMER_MILLIS      = 10;
    private static final long EVENT_TIMEOUT_MS  = 2000;

    @Test(timeout = 60_000)
    public void handleReplacedInSamePassReceivesEvents() throws Exception
    {
        for (int i = 0; i < ITERATIONS; i++) {
            try (Fixture fixture = new Fixture("same-pass-" + i)) {
                // the retiring handler swaps itself for the replacement from inside its callback:
                // removeHandle + addHandle + setPollIn for one channel, all before the next pass
                fixture.start(fixture::retireAndAddReplacementNow);

                fixture.writeByte();
                assertTrue("retiring handler ran (" + i + ")", fixture.awaitRetiring());
                Thread.sleep(AFTER_PASS_MILLIS);
                fixture.writeByte();

                assertTrue("replacement handle registered in the same pass as the retirement (" + i + ")",
                           fixture.awaitReplacement());
            }
        }
    }

    @Test(timeout = 60_000)
    public void handleReplacedInLaterPassReceivesEvents() throws Exception
    {
        for (int i = 0; i < ITERATIONS; i++) {
            try (Fixture fixture = new Fixture("later-pass-" + i)) {
                // the same hand-off, but the replacement is added from a timer that fires after
                // the poller has run a pass and a select on its own: the normal, non-racing order
                fixture.start(fixture::retireNowAndAddReplacementLater);

                fixture.writeByte();
                assertTrue("retiring handler ran (" + i + ")", fixture.awaitRetiring());
                Thread.sleep(TIMER_MILLIS + AFTER_PASS_MILLIS);
                fixture.writeByte();

                assertTrue("replacement handle registered in a later pass (" + i + ")",
                           fixture.awaitReplacement());
            }
        }
    }

    /** One poller, one pipe, two handlers that take turns on the pipe's read end. */
    private static final class Fixture implements AutoCloseable
    {
        private final Ctx            ctx = new Ctx();
        private final Poller         poller;
        private final Pipe           pipe;
        private final CountDownLatch retiringSeen    = new CountDownLatch(1);
        private final CountDownLatch replacementSeen = new CountDownLatch(1);

        private Poller.Handle retiringHandle;

        private final IPollEvents replacement = new IPollEvents()
        {
            @Override
            public void inEvent()
            {
                drain();
                replacementSeen.countDown();
            }
        };

        private Fixture(String name) throws IOException
        {
            poller = new Poller(ctx, name);
            pipe = Pipe.open();
            pipe.source().configureBlocking(false);
            pipe.sink().configureBlocking(false);
        }

        void start(Runnable actionOnPollerThread)
        {
            IPollEvents retiring = new IPollEvents()
            {
                @Override
                public void inEvent()
                {
                    drain();
                    actionOnPollerThread.run();
                    retiringSeen.countDown();
                }
            };
            retiringHandle = poller.addHandle(pipe.source(), retiring);
            poller.setPollIn(retiringHandle);
            poller.start();
        }

        void retireAndAddReplacementNow()
        {
            poller.removeHandle(retiringHandle);
            Poller.Handle handle = poller.addHandle(pipe.source(), replacement);
            poller.setPollIn(handle);
        }

        void retireNowAndAddReplacementLater()
        {
            poller.removeHandle(retiringHandle);
            poller.addTimer(TIMER_MILLIS, new IPollEvents()
            {
                @Override
                public void timerEvent(int id)
                {
                    Poller.Handle handle = poller.addHandle(pipe.source(), replacement);
                    poller.setPollIn(handle);
                }
            }, 1);
        }

        boolean awaitRetiring() throws InterruptedException
        {
            return retiringSeen.await(EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        boolean awaitReplacement() throws InterruptedException
        {
            return replacementSeen.await(EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        void writeByte() throws IOException
        {
            pipe.sink().write(ByteBuffer.wrap(new byte[] { 1 }));
        }

        private void drain()
        {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(16);
                while (pipe.source().read(buffer) > 0) {
                    buffer.clear();
                }
            }
            catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() throws IOException
        {
            poller.destroy();
            pipe.sink().close();
            pipe.source().close();
            ctx.terminate();
        }
    }
}
