package org.jeromq.api;

import zmq.ZMQ;

public enum SendReceiveOption {
    DONTWAIT(ZMQ.ZMQ_DONTWAIT), NOBLOCK(ZMQ.ZMQ_NOBLOCK);
    private final int cValue;

    SendReceiveOption(int cValue) {
        this.cValue = cValue;
    }

    public int getCValue() {
        return cValue;
    }
}
