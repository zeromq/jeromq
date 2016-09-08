package org.zeromq;

import zmq.ZError;

public class ZMQException extends RuntimeException
{
    public static class IOException extends RuntimeException
    {
        private static final long serialVersionUID = 8440355423370109164L;

        public IOException(java.io.IOException cause)
        {
            super(cause);
        }
    }

    private static final long serialVersionUID = 5957233088499712341L;

    private final int code;

    public ZMQException(int errno)
    {
        super("Errno " + errno);
        code = errno;
    }

    public ZMQException(String message, int errno)
    {
        super(message);
        code = errno;
    }

    public ZMQException(ZMQException cause)
    {
        super(cause.getMessage(), cause);
        code = cause.code;
    }

    public int getErrorCode()
    {
        return code;
    }

    @Override
    public String toString()
    {
        return super.toString() + " : " + ZError.toString(code);
    }
}
