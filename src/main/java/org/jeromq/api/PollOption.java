package org.jeromq.api;

import zmq.ZMQ;

import java.util.EnumSet;

public enum PollOption {
    POLL_IN(ZMQ.ZMQ_POLLIN), POLL_OUT(ZMQ.ZMQ_POLLOUT), POLL_ERR(ZMQ.ZMQ_POLLERR);

    private final int cValue;

    PollOption(int cValue) {
        this.cValue = cValue;
    }

    static int getCValue(EnumSet<PollOption> options) {
        int result = 0;
        for (PollOption event : options) {
            result |= event.cValue;
        }
        return result;
    }
}
