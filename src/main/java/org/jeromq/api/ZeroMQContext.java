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
        return new Socket(zContext.createSocket(type.getCValue()), zContext);
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

    /**
     * Create a new Poller.
     */
    public Poller createPoller() {
        return new Poller();
    }

    /**
     * Starts the built-in ØMQ proxy in the current application thread.
     * The proxy connects a frontend socket to a backend socket. Conceptually, data flows from frontend to backend.
     * Depending on the socket types, replies may flow in the opposite direction. The direction is conceptual only;
     * the proxy is fully symmetric and there is no technical difference between frontend and backend.
     * <p/>
     * Before calling startProxy() you must set any socket options, and connect or bind both frontend and backend sockets.
     * <p/>
     * startProxy() runs in the current thread and returns only if/when the current context is closed.
     *
     * @param capture If the capture socket is not NULL, the proxy shall send all messages, received on both
     *                frontend and backend, to the capture socket. The capture socket should be a
     *                ZMQ_PUB, ZMQ_DEALER, ZMQ_PUSH, or ZMQ_PAIR socket.
     * @return If the proxy is successfully started.
     */
    public boolean startProxy(Socket frontend, Socket backend, Socket capture) {
        return ZMQ.proxy(frontend.getDelegate(), backend.getDelegate(), capture == null ? null : capture.getDelegate());
    }

    /**
     * Start a proxy with no capture socket.
     *
     * @return If the proxy is successfully started.
     * @see #startProxy(Socket, Socket, Socket)
     */
    public boolean startProxy(Socket frontend, Socket backend) {
        return startProxy(frontend, backend, null);
    }
}
