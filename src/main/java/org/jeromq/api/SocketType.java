package org.jeromq.api;

import zmq.ZMQ;

public enum SocketType {
    /**
     * An exclusive pair of sockets.
     */
    PAIR(ZMQ.ZMQ_PAIR),
    /**
     * A PUB socket, receiving side must be a SUB or XSUB.
     */
    PUB(ZMQ.ZMQ_PUB),
    /**
     * The receiving part of the PUB or XPUB socket.
     */
    SUB(ZMQ.ZMQ_SUB),
    /**
     * A REQ socket, receiving side must be a REP.
     */
    REQ(ZMQ.ZMQ_REQ),
    /**
     * The receiving part of a REQ socket.
     */
    REP(ZMQ.ZMQ_REP),
    /**
     * DEALER socket (aka XREQ).
     * DEALER is really a combined ventilator / sink
     * that does load-balancing on output and fair-queuing on input
     * with no other semantics. It is the only socket type that lets
     * you shuffle messages out to N nodes and shuffle the replies
     * back, in a raw bidirectional asynch pattern.
     */
    DEALER(ZMQ.ZMQ_DEALER),
    /**
     * ROUTER socket (aka XREP).
     * ROUTER is the socket that creates and consumes request-reply
     * routing envelopes. It is the only socket type that lets you route
     * messages to specific connections if you know their identities.
     */
    ROUTER(ZMQ.ZMQ_ROUTER),
    /**
     * Receiving part of a PUSH socket.
     */
    PULL(ZMQ.ZMQ_PULL),
    /**
     * A PUSH socket, receiving side must be a PULL.
     */
    PUSH(ZMQ.ZMQ_PUSH),
    /**
     * An XPUB socket, receiving side must be a SUB or XSUB.
     * Subscriptions can be received as a message. Subscriptions start with
     * a '1' byte. Unsubscriptions start with a '0' byte.
     */
    XPUB(ZMQ.ZMQ_XPUB),
    /**
     * The receiving part of the PUB or XPUB socket. Allows
     */
    XSUB(ZMQ.ZMQ_XSUB);

    private final int cValue;

    SocketType(int cValue) {
        this.cValue = cValue;
    }

    public int getCValue() {
        return cValue;
    }
}
