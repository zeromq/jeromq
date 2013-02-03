package org.jeromq.api;

import zmq.ZMQ;

//todo: javadocs!
public enum SendReceiveOption {
    DONT_WAIT(ZMQ.ZMQ_DONTWAIT),
    NO_BLOCK(ZMQ.ZMQ_NOBLOCK);
    private final int cValue;

    SendReceiveOption(int cValue) {
        this.cValue = cValue;
    }

    public int getCValue() {
        return cValue;
    }
}
