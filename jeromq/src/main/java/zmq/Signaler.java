package zmq;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.atomic.AtomicLong;

import zmq.util.Errno;
import zmq.util.Utils;

//  This is a cross-platform equivalent to signal_fd. However, as opposed
//  to signal_fd there can be at most one signal in the signaler at any
//  given moment. Attempt to send a signal before receiving the previous
//  one will result in undefined behaviour.
final class Signaler implements Closeable
{
    private interface IoOperation<O>
    {
        O call() throws IOException;
    }

    //  Underlying write & read file descriptor.
    private final Pipe.SinkChannel   w;
    private final Pipe.SourceChannel r;
    private final Selector           selector;
    private final ThreadLocal<ByteBuffer>         wdummy = ThreadLocal.withInitial(() -> ByteBuffer.allocate(1));
    private final ThreadLocal<ByteBuffer>         rdummy = ThreadLocal.withInitial(() -> ByteBuffer.allocate(1));

    // Selector.selectNow at every sending message doesn't show enough performance
    private final AtomicLong wcursor = new AtomicLong(0);
    private long             rcursor = 0;

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

    private <O> O maksInterrupt(IoOperation<O> operation) throws IOException
    {
        // This loop try to protect the current thread from external interruption.
        // If it happens, it mangles current context internal state.
        // So it keep trying until it succeed.
        // This must only be called on internal IO (using Pipe)
        boolean interrupted = Thread.interrupted();
        while (true) {
            try {
                return operation.call();
            }
            catch (ClosedByInterruptException e) {
                interrupted = true;
            }
            finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public void close() throws IOException
    {
        IOException exception = null;
        IoOperation<Object> op1 = () -> {
            r.close();
            return null;
        };
        IoOperation<Object> op2 = () -> {
            w.close();
            return null;
        };
        IoOperation<Object> op3 = () -> {
            ctx.closeSelector(selector);
            return null;
        };

        for (IoOperation<?> op : new IoOperation<?>[] {op1, op2, op3}) {
            try {
                maksInterrupt(op);
            }
            catch (IOException e) {
                if (exception != null) {
                    e.addSuppressed(exception);
                }
                exception = e;
            }
        }
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
        int nbytes = 0;

        while (nbytes == 0) {
            try {
                wdummy.get().clear();
                nbytes = maksInterrupt(() -> w.write(wdummy.get()));
            }
            catch (IOException e) {
                throw new ZError.IOException(e);
            }
        }
        wcursor.incrementAndGet();
    }

    boolean waitEvent(long timeout)
    {
        // Transform a interrupt signal in an errno EINTR
        if (Thread.interrupted()) {
            errno.set(ZError.EINTR);
            return false;
        }
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
        catch (ClosedSelectorException e) {
            errno.set(ZError.EINTR);
            return false;
        }
        catch (IOException e) {
            errno.set(ZError.exccode(e));
            return false;
        }

        if (Thread.interrupted() || (rc == 0 && timeout <= 0 && ! selector.keys().isEmpty())) {
            errno.set(ZError.EINTR);
            return false;
        }
        else if (rc == 0) {
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
                rdummy.get().clear();
                nbytes = maksInterrupt(() -> r.read(rdummy.get()));
            }
            catch (ClosedChannelException e) {
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
