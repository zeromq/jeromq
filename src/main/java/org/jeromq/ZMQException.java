package org.jeromq;

public class ZMQException extends RuntimeException {

    private static final long serialVersionUID = 5957233088499712341L;

    private final long code;
    
    public ZMQException(int errno) {
        code = errno;
    }

    public long getErrorCode() {
        return code;
    }

}
