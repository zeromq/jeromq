package org.zeromq;

import java.io.Closeable;
import java.nio.channels.Selector;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private volatile Context context; //  Created lazily, use getContext() to access.

    /**
     * List of sockets managed by this ZContext
     */
    private final List<Socket> sockets;

    /**
     * Number of io threads allocated to this context, default 1
     */
    private final int ioThreads;

    /**
     * Linger timeout, default 0
     */
    private int linger;

    /**
     * Indicates if context object is owned by main thread
     * (useful for multi-threaded applications)
     */
    private final boolean main;

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
        sockets = new CopyOnWriteArrayList<>();
        this.context = context;
        this.ioThreads = ioThreads;
        this.main = main;
        linger = 0;
    }

    /**
     * Destructor.  Call this to gracefully terminate context and close any managed 0MQ sockets
     */
    public void destroy()
    {
        for (Socket socket : sockets) {
            socket.setLinger(linger);
            socket.close();
        }
        sockets.clear();

        // Only terminate context if we are on the main thread
        if (isMain() && context != null) {
            context.term();
        }

        synchronized (this) {
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
        sockets.add(socket);
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

        if (sockets.remove(s)) {
            s.setLinger(linger);
            s.close();
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
        return new ZContext(ctx.getContext(), false, ctx.getIoThreads());
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
        this.linger = linger;
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
        Context result = context;
        if (result == null) {
            synchronized (this) {
                result = context;
                if (result == null) {
                    result = ZMQ.context(ioThreads);
                    context = result;
                }
            }
        }
        return result;
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
