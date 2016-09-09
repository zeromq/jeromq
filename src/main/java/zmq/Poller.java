package zmq;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Poller extends PollerBase implements Runnable
{
    private static class PollSet
    {
        protected IPollEvents handler;
        protected SelectionKey key;
        protected int ops;
        protected boolean cancelled;

        protected PollSet(IPollEvents handler)
        {
            this.handler = handler;
            key = null;
            cancelled = false;
            ops = 0;
        }
    }
    //  This table stores data for registered descriptors.
    private final Map<SelectableChannel, PollSet> fdTable;

    //  If true, there's at least one retired event source.
    private final AtomicBoolean retired = new AtomicBoolean(false);

    //  If true, thread is in the process of shutting down.
    private volatile boolean stopping;
    private volatile boolean stopped;

    private Thread worker;
    private Selector selector;
    private final String name;

    public Poller()
    {
        this("poller");
    }

    public Poller(String name)
    {
        this.name = name;
        stopping = false;
        stopped = false;

        fdTable = new HashMap<SelectableChannel, PollSet>();
        try {
            selector = Selector.open();
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }
    }

    public void destroy()
    {
        if (!stopped) {
            try {
                worker.join();
            }
            catch (InterruptedException e) {
            }
        }

        try {
            selector.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public final void addHandle(SelectableChannel fd, IPollEvents events)
    {
        fdTable.put(fd, new PollSet(events));

        adjustLoad(1);
    }

    public final void removeHandle(SelectableChannel handle)
    {
        fdTable.get(handle).cancelled = true;
        retired.set(true);

        //  Decrease the load metric of the thread.
        adjustLoad(-1);
    }

    public final void setPollIn(SelectableChannel handle)
    {
        register(handle, SelectionKey.OP_READ, false);
    }

    public final void resetPollOn(SelectableChannel handle)
    {
        register(handle, SelectionKey.OP_READ, true);
    }

    public final void setPollOut(SelectableChannel handle)
    {
        register(handle,  SelectionKey.OP_WRITE, false);
    }

    public final void resetPollOut(SelectableChannel handle)
    {
        register(handle, SelectionKey.OP_WRITE, true);
    }

    public final void setPollConnect(SelectableChannel handle)
    {
        register(handle, SelectionKey.OP_CONNECT, false);
    }

    public final void setPollAccept(SelectableChannel handle)
    {
        register(handle, SelectionKey.OP_ACCEPT, false);
    }

    private final void register(SelectableChannel handle, int ops, boolean negate)
    {
        PollSet pollset = fdTable.get(handle);

        if (negate) {
            pollset.ops = pollset.ops & ~ops;
        }
        else {
            pollset.ops = pollset.ops | ops;
        }

        if (pollset.key != null) {
            pollset.key.interestOps(pollset.ops);
        }
        else {
            retired.set(true);
        }
    }

    public void start()
    {
        worker = new Thread(this, name);
        worker.setDaemon(true);
        worker.start();
    }

    public void stop()
    {
        stopping = true;
        selector.wakeup();
    }

    @Override
    public void run()
    {
        int returnsImmediately = 0;

        while (!stopping) {
            //  Execute any due timers.
            long timeout = executeTimers();

            while (retired.compareAndSet(true, false)) {
                Iterator<Map.Entry<SelectableChannel, PollSet>> it = fdTable.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<SelectableChannel, PollSet> entry = it.next();
                    SelectableChannel ch = entry.getKey();
                    PollSet pollset = entry.getValue();
                    if (pollset.key == null) {
                        try {
                            pollset.key = ch.register(selector, pollset.ops, pollset.handler);
                        }
                        catch (ClosedChannelException e) {
                        }
                    }

                    if (pollset.cancelled || !ch.isOpen()) {
                        if (pollset.key != null) {
                            pollset.key.cancel();
                        }
                        it.remove();
                    }
                }
            }

            //  Wait for events.
            int rc;
            long start = System.currentTimeMillis();
            try {
                rc = selector.select(timeout);
            }
            catch (IOException e) {
                throw new ZError.IOException(e);
            }

            if (rc == 0) {
                //  Guess JDK epoll bug
                if (timeout == 0 ||
                        System.currentTimeMillis() - start < timeout / 2) {
                    returnsImmediately++;
                }
                else {
                    returnsImmediately = 0;
                }

                if (returnsImmediately > 10) {
                    rebuildSelector();
                    returnsImmediately = 0;
                }
                continue;
            }

            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                IPollEvents evt = (IPollEvents) key.attachment();
                it.remove();

                try {
                    if (key.isReadable()) {
                        evt.inEvent();
                    }
                    else if (key.isAcceptable()) {
                        evt.acceptEvent();
                    }
                    else if (key.isConnectable()) {
                        evt.connectEvent();
                    }
                    if (key.isWritable()) {
                        evt.outEvent();
                    }
                }
                catch (CancelledKeyException e) {
                    // channel might have been closed
                }
            }

        }
        stopped = true;
    }

    private void rebuildSelector()
    {
        Selector newSelector;

        try {
            newSelector = Selector.open();
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }

        try {
            selector.close();
        }
        catch (IOException e) {
        }

        selector = newSelector;

        for (PollSet pollSet : fdTable.values()) {
            pollSet.key = null;
        }

        retired.set(true);
    }
}
