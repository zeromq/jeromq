# Exceptions

JeroMQ defines a handful of custom exceptions, which are thrown as a means of
signaling various exceptional internal states.

These exceptions are defined within the [ZError][zerror] class.

## ZError.CtxTerminatedException

This exception is thrown when an action is attempted which requires an open
context, but the context in question has been terminated.

## ZError.InstantiationException

> If you know what this exception is for, please update this document with an
> explanation!
>
> Anecdotally, I can't find anywhere in the source code where this exception is
> ever thrown, so perhaps it should be removed?

## ZError.IOException

This exception wraps [java.io.IOException][ioexception]. When JeroMQ throws one
of these, it is an acknowledgment that a java.io.IOException has occurred, but
it is not JeroMQ's responsibility to resolve it; it is up to the caller.


[zerror]: https://static.javadoc.io/org.zeromq/jeromq/0.6.0/zmq/ZError.html
[ioexception]: https://docs.oracle.com/javase/8/docs/api/java/io/IOException.html
