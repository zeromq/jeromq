package org.zeromq;

public abstract class UncheckedZMQException extends RuntimeException
{
    public UncheckedZMQException()
    {
        super();
    }

    public UncheckedZMQException(String message)
    {
        super(message);
    }

    public UncheckedZMQException(Throwable cause)
    {
        super(cause);
    }

    public UncheckedZMQException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
