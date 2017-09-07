package zmq.poll;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import zmq.Ctx;
import zmq.ZError;

public final class Poller extends PollerBase implements Runnable
{
    // opaque class to mimic libzmq behaviour.
    // extra interest is we do not need to look through the fdTable to perform common operations.
    public static final class Handle
    {
        private final SelectableChannel fd;
        private final IPollEvents       handler;

        private int     ops;
        private boolean cancelled;

        public Handle(SelectableChannel fd, IPollEvents handler)
        {
            assert (fd != null);
            assert (handler != null);
            this.fd = fd;
            this.handler = handler;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + fd.hashCode();
            result = prime * result + handler.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) {
                return true;
            }
            if (other == null) {
                return false;
            }
            if (!(other instanceof Handle)) {
                return false;
            }
            Handle that = (Handle) other;
            return this.fd.equals(that.fd) && this.handler.equals(that.handler);
        }

        @Override
        public String toString()
        {
            return "Handle-" + fd;
        }
    }

    // Reference to ZMQ context.
    private final Ctx ctx;

    //  stores data for registered descriptors.
    private final Set<Handle> fdTable;

    //  If true, there's at least one retired event source.
    private boolean retired = false;

    //  If true, thread is in the process of shutting down.
    private final AtomicBoolean  stopping = new AtomicBoolean();
    private final CountDownLatch stopped  = new CountDownLatch(1);

    private Selector selector;

    public Poller(Ctx ctx, String name)
    {
        super(name);
        this.ctx = ctx;

        fdTable = new HashSet<>();
        selector = ctx.createSelector();
    }

    public void destroy()
    {
        try {
            stop();
            stopped.await();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
            // Re-interrupt the thread so the caller can handle it.
            Thread.currentThread().interrupt();
        }
        finally {
            ctx.closeSelector(selector);
        }
    }

    public Handle addHandle(SelectableChannel fd, IPollEvents events)
    {
        assert (Thread.currentThread() == worker || !worker.isAlive());

        Handle handle = new Handle(fd, events);
        fdTable.add(handle);

        //  Increase the load metric of the thread.
        adjustLoad(1);
        return handle;
    }

    public void removeHandle(Handle handle)
    {
        assert (Thread.currentThread() == worker || !worker.isAlive());

        //  Mark the fd as unused.
        handle.cancelled = true;
        retired = true;

        //  Decrease the load metric of the thread.
        adjustLoad(-1);
    }

    public void setPollIn(Handle handle)
    {
        register(handle, SelectionKey.OP_READ, true);
    }

    public void resetPollIn(Handle handle)
    {
        register(handle, SelectionKey.OP_READ, false);
    }

    public void setPollOut(Handle handle)
    {
        register(handle, SelectionKey.OP_WRITE, true);
    }

    public void resetPollOut(Handle handle)
    {
        register(handle, SelectionKey.OP_WRITE, false);
    }

    public void setPollConnect(Handle handle)
    {
        register(handle, SelectionKey.OP_CONNECT, true);
    }

    public void setPollAccept(Handle handle)
    {
        register(handle, SelectionKey.OP_ACCEPT, true);
    }

    private void register(Handle handle, int ops, boolean add)
    {
        assert (Thread.currentThread() == worker || !worker.isAlive());

        if (add) {
            handle.ops |= ops;
        }
        else {
            handle.ops &= ~ops;
        }
        retired = true;
    }

    public void start()
    {
        worker.start();
    }

    public void stop()
    {
        stopping.set(true);
        retired = false;
        selector.wakeup();
    }

    @Override
    public void run()
    {
        int returnsImmediately = 0;

        while (!stopping.get()) {
            //  Execute any due timers.
            long timeout = executeTimers();

            if (retired) {
                retired = false;
                Iterator<Handle> iter = fdTable.iterator();
                while (iter.hasNext()) {
                    Handle handle = iter.next();
                    SelectionKey key = handle.fd.keyFor(selector);
                    if (handle.cancelled || !handle.fd.isOpen()) {
                        if (key != null) {
                            key.cancel();
                        }
                        iter.remove();
                        continue;
                    }
                    if (key == null) {
                        if (handle.fd.isOpen()) {
                            try {
                                key = handle.fd.register(selector, handle.ops, handle);
                                assert (key != null);
                            }
                            catch (CancelledKeyException | ClosedSelectorException | ClosedChannelException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    else if (key.isValid()) {
                        key.interestOps(handle.ops);
                    }
                }
            }

            //  Wait for events.
            int rc;
            long start = System.currentTimeMillis();
            try {
                rc = selector.select(timeout);
            }
            catch (ClosedSelectorException e) {
                rebuildSelector();
                e.printStackTrace();
                ctx.errno().set(ZError.EINTR);
                continue;
            }
            catch (IOException e) {
                throw new ZError.IOException(e);
            }

            //  If there are no events (i.e. it's a timeout) there's no point
            //  in checking the keys.
            if (rc == 0) {
                returnsImmediately = maybeRebuildSelector(returnsImmediately, timeout, start);
                continue;
            }

            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                Handle pollset = (Handle) key.attachment();
                it.remove();
                if (pollset.cancelled) {
                    continue;
                }

                try {
                    if (key.isValid() && key.isAcceptable()) {
                        pollset.handler.acceptEvent();
                    }
                    if (key.isValid() && key.isConnectable()) {
                        pollset.handler.connectEvent();
                    }
                    if (key.isValid() && key.isWritable()) {
                        pollset.handler.outEvent();
                    }
                    if (key.isValid() && key.isReadable()) {
                        pollset.handler.inEvent();
                    }
                }
                catch (CancelledKeyException e) {
                    // key may have been cancelled (?)
                    e.printStackTrace();
                }
                catch (RuntimeException e) {
                    // avoid the thread death by continuing to iterate
                    e.printStackTrace();
                }
            }
        }
        stopped.countDown();
    }

    private int maybeRebuildSelector(int returnsImmediately, long timeout, long start)
    {
        //  Guess JDK epoll bug
        if (timeout == 0 || System.currentTimeMillis() - start < timeout / 2) {
            returnsImmediately++;
        }
        else {
            returnsImmediately = 0;
        }

        if (returnsImmediately > 10) {
            rebuildSelector();
            returnsImmediately = 0;
        }
        return returnsImmediately;
    }

    private void rebuildSelector()
    {
        System.out.println(this + " rebuilding selector");
        Selector newSelector = ctx.createSelector();
        Selector oldSelector = selector;

        selector = newSelector;
        retired = true;

        ctx.closeSelector(oldSelector);
    }
}
