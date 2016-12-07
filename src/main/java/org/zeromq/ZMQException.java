package org.zeromq;

import zmq.ZError;

public class ZMQException extends RuntimeException
{
    private static final long serialVersionUID = -978820750094924644L;

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
