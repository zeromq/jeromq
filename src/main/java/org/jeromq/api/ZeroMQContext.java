package org.jeromq.api;

import org.zeromq.ZContext;

public class ZeroMQContext {
    private final ZContext zContext;

    public ZeroMQContext(int numberOfThreads) {
        this.zContext = new ZContext(numberOfThreads);
    }

    public ZeroMQContext() {
        this(1);
    }

    public Socket createSocket(SocketType type) {
        return new Socket(zContext.createSocket(type.getCValue()));
    }

}
