package org.zeromq;

import java.io.Closeable;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import zmq.util.Draft;

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
    private final Context context;

    /**
     * List of sockets managed by this ZContext
     */
    private final Set<Socket> sockets;

    /**
     * List of selectors managed by this ZContext
     */
    private final Set<Selector> selectors;

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
    private volatile int linger;

    /**
     * Send/receive HWM for pipes
     */
    private int pipehwm;

    /**
     * ZMQ_SNDHWM for normal sockets
     */
    private volatile int sndhwm;

    /**
     * ZMQ_RCVHWM for normal sockets
     */
    private volatile int rcvhwm;

    /**
     * Class Constructor
     */
    public ZContext()
    {
        this(1);
    }

    public ZContext(int ioThreads)
    {
        this(ZMQ.context(ioThreads), true, ioThreads);
    }

    private ZContext(Context context, boolean main, int ioThreads)
    {
        // Android compatibility: not using ConcurrentHashMap.newKeySet()
        this.sockets = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.selectors = Collections.newSetFromMap(new ConcurrentHashMap<>());
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

        for (Selector selector : selectors) {
            context.close(selector);
        }
        selectors.clear();

        // Only terminate context if we are on the main thread
        if (isMain()) {
            context.term();
        }
    }

    /**
     * Creates a new managed socket within this ZContext instance.
     * Use this to get automatic management of the socket at shutdown
     * @param type
     *          socket type
     * @return
     *          Newly created Socket object
     */
    public Socket createSocket(SocketType type)
    {
        // Create and register socket
        Socket socket = context.socket(type);
        socket.setRcvHWM(this.rcvhwm);
        socket.setSndHWM(this.sndhwm);
        sockets.add(socket);
        return socket;
    }

    /**
     * @deprecated use {@link #createSocket(SocketType)}
     * @param type
     *          socket type (see ZMQ static class members)
     * @return
     *          Newly created Socket object
     */
    @Deprecated
    public Socket createSocket(int type)
    {
        return createSocket(SocketType.type(type));
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
        this.sockets.remove(s);
    }

    /**
     * Creates a selector. It needs to be closed by {@link #closeSelector(Selector)}.
     *
     * @return a newly created selector.
     * @deprecated this was exposed by mistake.
     */
    @Deprecated
    public Selector createSelector()
    {
        return selector();
    }

    /**
     * Creates a selector. Resource will be released when context will be closed.
     *
     * @return a newly created selector.
     */
    Selector selector()
    {
        Selector selector = context.selector();
        selectors.add(selector);
        return selector;
    }

    /**
     * Closes a selector.
     * This is a DRAFT method, and may change without notice.
     *
     * @param selector the selector to close. It needs to have been created by {@link #createSelector()}.
     * @deprecated {@link #createSelector()} was exposed by mistake. while waiting for the API to disappear, this method is provided to allow releasing resources.
     */
    @Deprecated
    @Draft
    public void closeSelector(Selector selector)
    {
        if (selectors.remove(selector)) {
            context.close(selector);
        }
    }

    public Poller createPoller(int size)
    {
        return new Poller(context, size);
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
        ZContext context = new ZContext(ctx.context, false, ctx.ioThreads);
        context.linger = ctx.linger;
        context.sndhwm = ctx.sndhwm;
        context.rcvhwm = ctx.rcvhwm;
        context.pipehwm = ctx.pipehwm;
        return context;
    }

    /**
     * Create an attached thread, An attached thread gets a ctx and a PAIR pipe back to its
     * parent. It must monitor its pipe, and exit if the pipe becomes unreadable
     *
     * @param runnable attached thread
     * @param args forked runnable args
     * @return pipe or null if there was an error
     */
    public Socket fork(ZThread.IAttachedRunnable runnable, Object... args)
    {
        return ZThread.fork(this, runnable, args);
    }

    /**
     * @return the ioThreads
     */
    public int getIoThreads()
    {
        return ioThreads;
    }

    /**
     * A deprecated function that does nothing.
     *
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
        this.linger = linger;
    }

    /**
     * Set initial receive HWM for all new normal sockets created in context.
     * You can set this per-socket after the socket is created.
     * The default, no matter the underlying ZeroMQ version, is 1,000.
     * @param rcvhwm the rcvhwm
     */
    public void setRcvHWM(int rcvhwm)
    {
        this.rcvhwm = rcvhwm;
    }

    /**
     * Set initial receive HWM for all new normal sockets created in context.
     * You can set this per-socket after the socket is created.
     * The default, no matter the underlying ZeroMQ version, is 1,000.
     * @param sndhwm the sndhwm
     */
    public void setSndHWM(int sndhwm)
    {
        this.sndhwm = sndhwm;
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
        return context;
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
     * Return a copy of the list of currently open sockets. Order is not meaningful.
     * @return the sockets
     */
    public List<Socket> getSockets()
    {
        return new ArrayList<>(sockets);
    }

    @Override
    public void close()
    {
        destroy();
    }

    public boolean isClosed()
    {
        return context.isClosed();
    }
}
