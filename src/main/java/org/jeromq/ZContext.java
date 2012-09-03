/*
    Copyright (c) 1991-2011 iMatix Corporation <www.imatix.com>
    Copyright other contributors as noted in the AUTHORS file.
                
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
package org.jeromq;

import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jeromq.ZMQ.Context;
import org.jeromq.ZMQ.Socket;

/**
 * ZContext provides a high-level ZeroMQ context management class
 * 
 *  @author rsmith (at) rsbatechnology (dot) co (dot) uk
 *  
 * It manages open sockets in the context and automatically closes these before terminating the context. 
 * It provides a simple way to set the linger timeout on sockets, and configure contexts for number of I/O threads. 
 * Sets-up signal (interrrupt) handling for the process.
 * 
 */

public class ZContext {
    /**
     * Reference to underlying Context object
     */
    private Context context;
    
    /**
     * List of sockets managed by this ZContext
     */
    private List<Socket> sockets;
    
    /**
     * Number of io threads allocated to this context, default 1
     */
    private int ioThreads;
    
    /**
     * Linger timeout, default 0
     */
    private int linger;
    
    /**
     * Indicates if context object is owned by main thread
     * (useful for multi-threaded applications)
     */
    private boolean main;
    
    /**
     * Class Constructor
     */
    public ZContext() {
        this(1);
    }

    public ZContext(int ioThreads_) {
        context = null;     // Don't create Context until create 1st 0MQ socket
        sockets = new CopyOnWriteArrayList<Socket>();
        ioThreads = ioThreads_;
        linger = 0;
        main = true;
    }

    /**
     * Destructor.  Call this to gracefully terminate context and close any managed 0MQ sockets
     */
    public void destroy() {
        ListIterator<Socket> itr = sockets.listIterator();
        while (itr.hasNext()) {
            destroySocket(itr.next());
        }
        sockets.clear();
        
        // Only terminate context if we are on the main thread
        if (isMain() && context != null)
            context.term();
        
    }
    
    /**
     * Creates a new managed socket within this ZContext instance.
     * Use this to get automatic management of the socket at shutdown
     * @param type
     *          socket type (see ZMQ static class members)
     * @return
     *          Newly created Socket object
     */
    public Socket createSocket(int type) {
        if (context == null)
            context = ZMQ.context(ioThreads);

        // Create and register socket
        Socket socket = context.socket(type);
        sockets.add(socket);
        return socket;
    }
    
    /**
     * Destroys managed socket within this context
     * and remove from sockets list
     * @param s
     *          org.zeromq.Socket object to destroy
     */
    public void destroySocket(Socket s) {
        if (s == null)
            return;
        
        if (sockets.contains(s)) {
            s.setLinger(linger);
            s.close();
            sockets.remove(s);
        }
    }
    
    /**
     * Creates new shadow context.
     * Shares same underlying org.zeromq.Context instance but has own list
     * of managed sockets, io thread count etc. 
     * @param ctx   Original ZContext to create shadow of
     * @return  New ZContext
     */
    public static ZContext shadow(ZContext ctx) {
        ZContext shadow = new ZContext();
        shadow.setContext(ctx.getContext());
        return shadow;
    }
    
    /**
     * @return the ioThreads
     */
    public int getIoThreads() {
        return ioThreads;
    }

    /**
     * @param ioThreads the ioThreads to set
     */
    public void setIoThreads(int ioThreads) {
        this.ioThreads = ioThreads;
    }

    /**
     * @return the linger
     */
    public int getLinger() {
        return linger;
    }

    /**
     * @param linger the linger to set
     */
    public void setLinger(int linger) {
        this.linger = linger;
    }

    /**
     * @return the main
     */
    public boolean isMain() {
        return main;
    }

    /**
     * @param main the main to set
     */
    public void setMain(boolean main) {
        this.main = main;
    }

    /**
     * @return the context
     */
    public Context getContext() {
        return context;
    }

    /**
     * @param ctx   sets the underlying org.zeromq.Context associated with this ZContext wrapper object
     */
    public void setContext(Context ctx) {
        this.context = ctx;
    }
    
    /**
     * @return the sockets
     */
    public List<Socket> getSockets() {
        return sockets;
    }
}
