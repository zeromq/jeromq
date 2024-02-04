# Contexts

You may have noticed that there are several different types of "context" classes
in JeroMQ.

## tl;dr: Which one do I use?

Use ZContext. It is the current state of the art for JeroMQ.

## zmq.Ctx

[Ctx][ctx], part of the zmq package, contains low-level implementation details
of a ZeroMQ context.

It should not be used directly in code that uses the JeroMQ library.

## org.zeromq.ZMQ.Context

[ZMQ.Context][zmq-context] was the first cut at a higher-level API for a ZeroMQ
context.

Before destroying a ZMQ.Context, care must be taken to close any
[sockets](sockets.md) and [pollers](pollers.md) that may have been created via
the context. ZContext, by comparison, does this for you.

## org.zeromq.ZContext

[ZContext][zcontext] is an improvement over ZMQ.Context, lending itself to more
concise, convenient, and safe usage.

ZContext implements the [java.io.Closable][closable] interface, which makes it
convenient to use in a [try-with-resources statement][try-with-resources].  When
a ZContext is closed, resources such as sockets and pollers are cleaned up
automatically.

## See also

* [zguide: Getting the Context Right][zguide-contexts]: general information
  about contexts in ZeroMQ

* [ZSocket][zsocket]: The next evolution of contexts (or lack thereof) in
  ZeroMQ?


[ctx]: http://static.javadoc.io/org.zeromq/jeromq/0.4.3/zmq/Ctx.html
[zmq-context]: https://static.javadoc.io/org.zeromq/jeromq/0.6.0/org/zeromq/ZMQ.Context.html
[zcontext]: https://static.javadoc.io/org.zeromq/jeromq/0.6.0/org/zeromq/ZContext.html
[closable]: https://docs.oracle.com/javase/8/docs/api/java/io/Closeable.html
[try-with-resources]: https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
[zguide-contexts]: https://zguide.zeromq.org/docs/chapter1/#Getting-the-Context-Right
[zsocket]: https://static.javadoc.io/org.zeromq/jeromq/0.6.0/org/zeromq/ZSocket.html
