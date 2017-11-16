package org.zeromq;

import java.io.Closeable;
import java.nio.channels.Selector;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

/**
 * ZContext provides a high-level ZeroMQ context management class
 *
 * It manages open sockets in the context and automatically closes these before terminating the context.
 * It provides a simple way to set the linger timeout on sockets, and configure contexts for number of I/O threads.
 * Sets-up signal (interrupt) handling for the process.
 *
 */

public class ZContext implements Closeable
{
    /**
     * Reference to underlying Context object
     */
    private Context context;

    /**
     * List of sockets managed by this ZContext
     */
    private final List<Socket> sockets;

    /**
     * Number of io threads allocated to this context, default 1
     */
    private final int ioThreads;

    /**
     * Indicates if context object is owned by main thread
     * (useful for multi-threaded applications)
     */
    private final boolean main;

    /**
     * Linger timeout, default 0
     */
    private int linger;

    /**
     * Send/receive HWM for pipes
     */
    private int pipehwm;

    /**
     * ZMQ_SNDHWM for normal sockets
     */
    private int sndhwm;

    /**
     * ZMQ_RCVHWM for normal sockets
     */
    private int rcvhwm;

    /**
     * Make ZContext threadsafe
     */
    private final Lock mutex;

    /**
     * Class Constructor
     */
    public ZContext()
    {
        this(1);
    }

    public ZContext(int ioThreads)
    {
        this(null, true, ioThreads);
    }

    private ZContext(Context context, boolean main, int ioThreads)
    {
        this.sockets = new CopyOnWriteArrayList<>();
        this.mutex = new ReentrantLock();
        this.context = context;
        this.ioThreads = ioThreads;
        this.main = main;
        this.linger = 0;
        this.pipehwm = 1000;
        this.sndhwm = 1000;
        this.rcvhwm = 1000;
    }

    /**
     * Destructor.  Call this to gracefully terminate context and close any managed 0MQ sockets
     */
    public void destroy()
    {
        for (Socket socket : sockets) {
            destroySocket(socket);
        }
        sockets.clear();
        // Only terminate context if we are on the main thread
        if (isMain() && context != null) {
            context.term();
            context = null;
        }
    }

    /**
     * Creates a new managed socket within this ZContext instance.
     * Use this to get automatic management of the socket at shutdown
     * @param type
     *          socket type (see ZMQ static class members)
     * @return
     *          Newly created Socket object
     */
    public Socket createSocket(int type)
    {
        // Create and register socket
        Socket socket = getContext().socket(type);
        socket.setRcvHWM(this.rcvhwm);
        socket.setSndHWM(this.sndhwm);
        try {
            mutex.lock();
            sockets.add(socket);
        }
        finally {
            mutex.unlock();
        }
        return socket;
    }

    /**
     * Destroys managed socket within this context
     * and remove from sockets list
     * @param s
     *          org.zeromq.Socket object to destroy
     */
    public void destroySocket(Socket s)
    {
        if (s == null) {
            return;
        }
        s.setLinger(linger);
        s.close();
        try {
            mutex.lock();
            this.sockets.remove(s);
        }
        finally {
            mutex.unlock();
        }
    }

    public Selector createSelector()
    {
        return getContext().selector();
    }

    public Poller createPoller(int size)
    {
        return new Poller(getContext(), size);
    }

    /**
     * Creates new shadow context.
     * Shares same underlying org.zeromq.Context instance but has own list
     * of managed sockets, io thread count etc.
     * @param ctx   Original ZContext to create shadow of
     * @return  New ZContext
     */
    public static ZContext shadow(ZContext ctx)
    {
        ZContext context = new ZContext(ctx.getContext(), false, ctx.ioThreads);
        context.linger = ctx.linger;
        context.sndhwm = ctx.sndhwm;
        context.rcvhwm = ctx.rcvhwm;
        context.pipehwm = ctx.pipehwm;
        return context;
    }

    /**
     * @return the ioThreads
     */
    public int getIoThreads()
    {
        return ioThreads;
    }

    /**
     * @param ioThreads the number of ioThreads to set
     * @deprecated This value should not be changed after the context is initialized.
     */
    @Deprecated
    public void setIoThreads(int ioThreads)
    {
        return;
    }

    /**
     * @return the linger
     */
    public int getLinger()
    {
        return linger;
    }

    /**
     * @param linger the linger to set
     */
    public void setLinger(int linger)
    {
        try {
            mutex.lock();
            this.linger = linger;
        }
        finally {
            mutex.unlock();
        }
    }

    /**
     * Set initial receive HWM for all new normal sockets created in context.
     * You can set this per-socket after the socket is created.
     * The default, no matter the underlying ZeroMQ version, is 1,000.
     * @param rcvhwm the rcvhwm
     */
    public void setRcvHWM(int rcvhwm)
    {
        try {
            mutex.lock();
            this.rcvhwm = rcvhwm;
        }
        finally {
            mutex.unlock();
        }
    }

    /**
     * Set initial receive HWM for all new normal sockets created in context.
     * You can set this per-socket after the socket is created.
     * The default, no matter the underlying ZeroMQ version, is 1,000.
     * @param sndhwm the sndhwm
     */
    public void setSndHWM(int sndhwm)
    {
        try {
            mutex.lock();
            this.sndhwm = sndhwm;
        }
        finally {
            mutex.unlock();
        }
    }
    /**
     * @return the main
     */
    public boolean isMain()
    {
        return main;
    }

    /**
     * @param main whether or not the context is being set to main
     * @deprecated This value should not be changed after the context is initialized.
     */
    @Deprecated
    public void setMain(boolean main)
    {
        return;
    }

    /**
     * @return the context
     */
    public Context getContext()
    {
        try {
            mutex.lock();
            if (this.context == null) {
                this.context = ZMQ.context(this.ioThreads);
            }
        }
        finally {
            mutex.unlock();
        }
        return this.context;
    }

    /**
     * @param ctx sets the underlying zmq.Context associated with this ZContext wrapper object
     * @deprecated This value should not be changed after the ZContext is initialized.
     */
    @Deprecated
    public void setContext(Context ctx)
    {
        return;
    }

    /**
     * @return the sockets
     */
    public List<Socket> getSockets()
    {
        return sockets;
    }

    @Override
    public void close()
    {
        destroy();
    }

    public boolean isClosed()
    {
        synchronized (this) {
            if (context == null) {
                return true;
            }
            return context.isClosed();
        }
    }
}
