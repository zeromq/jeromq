package org.jeromq.api;

import org.jeromq.ZContext;

public class ZeroMQContext {
    private final ZContext zContext;

    public ZeroMQContext(int numberOfThreads) {
        this.zContext = new ZContext(numberOfThreads);
    }

    public ZeroMQContext() {
        this(1);
    }


}
