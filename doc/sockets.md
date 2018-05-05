# Sockets

There are multiple classes implementing socket behavior in JeroMQ.

## tl;dr: How do I construct a Socket?

Use [ZContext.createSocket][create-socket]. This returns a
[ZMQ.Socket][zmq-socket].

## zmq.SocketBase

[zmq.SocketBase][socket-base] contains low-level implementation details of
ZeroMQ socket behavior.

It should not be used directly in code that uses the JeroMQ library.

## org.zeromq.ZMQ.Socket

[ZMQ.Socket][zmq-socket] is the user-facing API for working with sockets in
JeroMQ.

Sockets are constructed by calling [ZContext.createSocket][create-socket]. This
is essential because it registers the poller with the context, so that when the
context is closed, the poller and selector resources are cleaned up properly.

## org.zeromq.ZPoller

[ZPoller][zpoller] is a work-in-progress rewrite of the polling API.

> If you use ZPoller, please update these docs with more information!

## See also

* [zguide: Handling Multiple Sockets][zguide-polling]: general
  information about polling in ZeroMQ


[socket-base]: http://static.javadoc.io/org.zeromq/jeromq/0.4.3/zmq/SocketBase.html
