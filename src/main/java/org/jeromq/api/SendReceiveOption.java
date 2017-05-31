package org.jeromq.api;

import zmq.ZMQ;

//todo: javadocs!
public enum SendReceiveOption {
    DONT_WAIT(ZMQ.ZMQ_DONTWAIT),
    NO_BLOCK(ZMQ.ZMQ_NOBLOCK),
    SEND_MORE(ZMQ.ZMQ_SNDMORE),
    NONE(0);

    private final int cValue;

    SendReceiveOption(int cValue) {
        this.cValue = cValue;
    }

    public int getCValue() {
        return cValue;
    }
}
