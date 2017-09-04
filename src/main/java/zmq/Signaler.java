package zmq;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.atomic.AtomicInteger;

import zmq.util.Errno;
import zmq.util.Utils;

//  This is a cross-platform equivalent to signal_fd. However, as opposed
//  to signal_fd there can be at most one signal in the signaler at any
//  given moment. Attempt to send a signal before receiving the previous
//  one will result in undefined behaviour.
final class Signaler implements Closeable
{
    //  Underlying write & read file descriptor.
    private final Pipe.SinkChannel   w;
    private final Pipe.SourceChannel r;
    private final Selector           selector;
    private final ByteBuffer         wdummy = ByteBuffer.allocate(1);
    private final ByteBuffer         rdummy = ByteBuffer.allocate(1);

    // Selector.selectNow at every sending message doesn't show enough performance
    private final AtomicInteger wcursor = new AtomicInteger(0);
    private int                 rcursor = 0;

    private final Errno errno;
    private final int   pid;
    private final Ctx   ctx;

    Signaler(Ctx ctx, int pid, Errno errno)
    {
        this.ctx = ctx;
        this.pid = pid;
        this.errno = errno;
        //  Create the socket pair for signaling.

        try {
            Pipe pipe = Pipe.open();

            r = pipe.source();
            w = pipe.sink();

            //  Set both fds to non-blocking mode.
            Utils.unblockSocket(w, r);

            selector = ctx.createSelector();
            r.register(selector, SelectionKey.OP_READ);
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }
    }

    @Override
    public void close() throws IOException
    {
        IOException exception = null;
        try {
            r.close();
        }
        catch (IOException e) {
            e.printStackTrace();
            exception = e;
        }
        try {
            w.close();
        }
        catch (IOException e) {
            e.printStackTrace();
            exception = e;
        }
        ctx.closeSelector(selector);
        if (exception != null) {
            throw exception;
        }
    }

    SelectableChannel getFd()
    {
        return r;
    }

    void send()
    {
        int nbytes;

        while (true) {
            try {
                wdummy.clear();
                nbytes = w.write(wdummy);
            }
            catch (IOException e) {
                e.printStackTrace();
                throw new ZError.IOException(e);
            }
            if (nbytes == 0) {
                continue;
            }
            assert (nbytes == 1);
            wcursor.incrementAndGet();
            break;
        }
    }

    boolean waitEvent(long timeout)
    {
        int rc;
        boolean brc = (rcursor < wcursor.get());
        if (brc) {
            return true;
        }
        try {
            if (timeout == 0) {
                // waitEvent(0) is called every read/send of SocketBase
                // instant readiness is not strictly required
                // On the other hand, we can save lots of system call and increase performance
                errno.set(ZError.EAGAIN);
                return false;

            }
            else if (timeout < 0) {
                rc = selector.select(0);
            }
            else {
                rc = selector.select(timeout);
            }
        }
        catch (ClosedSelectorException | IOException e) {
            e.printStackTrace();
            errno.set(ZError.EINTR);
            return false;
        }

        if (rc == 0) {
            errno.set(ZError.EAGAIN);
            return false;
        }

        selector.selectedKeys().clear();

        return true;
    }

    void recv()
    {
        int nbytes = 0;
        // On windows, there may be a need to try several times until it succeeds
        while (nbytes == 0) {
            try {
                rdummy.clear();
                nbytes = r.read(rdummy);
            }
            catch (ClosedChannelException e) {
                e.printStackTrace();
                errno.set(ZError.EINTR);
                return;
            }
            catch (IOException e) {
                throw new ZError.IOException(e);
            }
        }
        assert (nbytes == 1);
        rcursor++;
    }

    @Override
    public String toString()
    {
        return "Signaler[" + pid + "]";
    }
}
