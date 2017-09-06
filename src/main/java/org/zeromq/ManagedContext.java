package org.zeromq;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import zmq.Ctx;
import zmq.SocketBase;
import zmq.ZMQ;

// This is to avoid people trying to initialize a Context
class ManagedContext
{
    static {
        // Release ManagedSocket resources when catching SIGINT
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                getInstance().close();
            }
        });
    }

    private final Lock            lock;
    private final Ctx             ctx;
    private final Set<SocketBase> sockets;

    private ManagedContext()
    {
        this.ctx = ZMQ.init(ZMQ.ZMQ_IO_THREADS_DFLT);
        this.lock = new ReentrantLock();
        this.sockets = new HashSet<>();
    }

    static ManagedContext getInstance()
    {
        return ContextHolder.INSTANCE;
    }

    SocketBase createSocket(int type)
    {
        final SocketBase base = ctx.createSocket(type);
        lock.lock();
        try {
            sockets.add(base);
        }
        finally {
            lock.unlock();
        }
        return base;
    }

    void destroy(SocketBase socketBase)
    {
        try {
            socketBase.setSocketOpt(ZMQ.ZMQ_LINGER, 0);
            socketBase.close();
        }
        catch (Exception e) {
        }
        lock.lock();
        try {
            sockets.remove(socketBase);
        }
        finally {
            lock.unlock();
        }
    }

    /*
     * This should only be called when SIGINT is received
     */
    private void close()
    {
        lock.lock();
        try {
            for (SocketBase s : sockets) {
                try {
                    s.setSocketOpt(ZMQ.ZMQ_LINGER, 0);
                    s.close();
                }
                catch (Exception ignore) {
                }
            }
            sockets.clear();
        }
        finally {
            lock.unlock();
        }
    }

    // Lazy singleton pattern to avoid double lock checking
    private static class ContextHolder
    {
        private static final ManagedContext INSTANCE = new ManagedContext();
    }
}
