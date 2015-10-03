/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.zeromq;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import zmq.ZError;

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
    private volatile Context context;  //  Created lazily, use getContext() to access.

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
     * HWM timeout, default 1
     */
    private int hwm;

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
        this(null, true, 1);
    }

    public ZContext(int ioThreads)
    {
        this(null, true, ioThreads);
    }

    private ZContext(Context context, boolean main, int ioThreads)
    {
        sockets = new CopyOnWriteArrayList<Socket>();
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
            try {
                socket.setLinger(linger);
            }
            catch (ZError.CtxTerminatedException e) {
            }
            socket.close();
        }
        sockets.clear();

        // Only terminate context if we are on the main thread
        if (isMain() && context != null) {
            context.term();
        }

        context = null;
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
            try {
                s.setLinger(linger);
            }
            catch (ZError.CtxTerminatedException e) {
            }
            s.close();
        }
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
     * @return the HWM
     */
    public int getHWM()
    {
        return hwm;
    }

    /**
     * @param hwm the HWM to set
     */
    public void setHWM(int hwm)
    {
        this.hwm = hwm;
    }

    /**
     * @return the main
     */
    public boolean isMain()
    {
        return main;
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
}
