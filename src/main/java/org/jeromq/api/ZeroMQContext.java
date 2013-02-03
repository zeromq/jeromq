package org.jeromq.api;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class ZeroMQContext {
    private final ZContext zContext;

    /**
     * Create a new ZeroMQContext with the specified number of backing threads.
     */
    public ZeroMQContext(int numberOfThreads) {
        this.zContext = new ZContext(numberOfThreads);
    }

    /**
     * Create a new ZeroMQContext with a single backing thread.
     */
    public ZeroMQContext() {
        this(1);
    }

    /**
     * Create a new socket of the specified type.
     */
    public Socket createSocket(SocketType type) {
        return new Socket(zContext.createSocket(type.getCValue()));
    }

    /**
     * Closes all sockets associated with this context and terminates the context itself.
     */
    public void terminate() {
        zContext.destroy();
    }

    /**
     * @return the ZeroMQ version as a nicely formatted String (major.minor.patch).
     */
    public String getVersionString() {
        return ZMQ.getVersionString();
    }

    /**
     * @return the ZeroMQ version as an int.
     */
    public int getFullVersion() {
        return ZMQ.getFullVersion();
    }
}
