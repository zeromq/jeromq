package zmq;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.atomic.AtomicInteger;

//  This is a cross-platform equivalent to signal_fd. However, as opposed
//  to signal_fd there can be at most one signal in the signaler at any
//  given moment. Attempt to send a signal before receiving the previous
//  one will result in undefined behaviour.

public class Signaler
        implements Closeable
{
    //  Underlying write & read file descriptor.
    private final Pipe.SinkChannel w;
    private final Pipe.SourceChannel r;
    private final Selector selector;
    private final ByteBuffer rdummy = ByteBuffer.allocate(1);

    // Selector.selectNow at every sending message doesn't show enough performance
    private final AtomicInteger wcursor = new AtomicInteger(0);
    private int rcursor = 0;

    public Signaler()
    {
        //  Create the socketpair for signaling.
        Pipe pipe;

        try {
            pipe = Pipe.open();
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }
        r = pipe.source();
        w = pipe.sink();

        //  Set both fds to non-blocking mode.
        try {
            Utils.unblockSocket(w);
            Utils.unblockSocket(r);
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }

        try {
            selector = Selector.open();
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
            exception = e;
        }
        try {
            w.close();
        }
        catch (IOException e) {
            exception = e;
        }
        try {
            selector.close();
        }
        catch (IOException e) {
            exception = e;
        }
        if (exception != null) {
            throw exception;
        }
    }

    public SelectableChannel getFd()
    {
        return r;
    }

    public void send()
    {
        int nbytes = 0;
        ByteBuffer dummy = ByteBuffer.allocate(1);

        while (true) {
            try {
                Thread.interrupted();
                nbytes = w.write(dummy);
            }
            catch (IOException e) {
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

    public boolean waitEvent(long timeout)
    {
        int rc = 0;

        try {
            if (timeout == 0) {
                // waitEvent(0) is called every read/send of SocketBase
                // instant readiness is not strictly required
                // On the other hand, we can save lots of system call and increase performance
                return rcursor < wcursor.get();

            }
            else if (timeout < 0) {
                rc = selector.select(0);
            }
            else {
                rc = selector.select(timeout);
            }
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }

        if (rc == 0) {
            return false;
        }

        selector.selectedKeys().clear();

        return true;
    }

    public void recv()
    {
        int nbytes = 0;
        while (nbytes == 0) {
            try {
                nbytes = r.read(rdummy);
                rdummy.rewind();
                // assert nbytes == 1; This was introduced in 0.3.5 and fails on windows causing tests to hang
            }
            catch (IOException e) {
                throw new ZError.IOException(e);
            }
        }
        rcursor++;
    }
}
