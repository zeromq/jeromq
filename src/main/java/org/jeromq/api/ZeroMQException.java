package org.jeromq.api;

import org.zeromq.ZMQException;

public class ZeroMQException extends RuntimeException {

    public ZeroMQException(String message, ZMQException cause) {
        super(message + " [" + ErrorCode.findByCode(cause.getErrorCode()) + "]", cause);
    }

}
