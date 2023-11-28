package org.zeromq;

public abstract class UncheckedZMQException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    protected int code; // Pulled-up variable

    public UncheckedZMQException() {
        super();
    }

    public UncheckedZMQException(String message) {
        super(message);
    }

    public UncheckedZMQException(Throwable cause) {
        super(cause);
    }

    public UncheckedZMQException(String message, Throwable cause) {
        super(message, cause);
    }

    public UncheckedZMQException(String message, int errno) {
        super(message);
        this.code = errno;
    }

    public UncheckedZMQException(String message, int errno, Throwable cause) {
        super(message, cause);
        this.code = errno;
    }

    public int getErrorCode() { // Pulled-up method
        return code;
    }

    @Override
    public String toString() { // Pulled-up method
        return super.toString() + (code != 0 ? " : " + zmq.ZError.toString(code) : "");
    }
}
