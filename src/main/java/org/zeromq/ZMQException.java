package org.zeromq;

public class ZMQException extends UncheckedZMQException {
    private static final long serialVersionUID = -978820750094924644L;

    public ZMQException(int errno) {
        super("Errno " + errno, errno);
    }

    public ZMQException(String message, int errno) {
        super(message, errno);
    }

    public ZMQException(String message, int errno, Throwable cause) {
        super(message, errno, cause);
    }
}
