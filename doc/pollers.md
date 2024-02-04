# Pollers

There are multiple classes implementing polling behavior in JeroMQ.

## tl;dr: How do I construct a Poller?

Use [ZContext.createPoller][create-poller]. This returns a
[ZMQ.Poller][zmq-poller].

## zmq.poll.Poller

[zmq.poll.Poller][zmq-poll-poller] contains low-level implementation details of
ZeroMQ polling behavior.

It should not be used directly in code that uses the JeroMQ library.

## org.zeromq.ZMQ.Poller

[ZMQ.Poller][zmq-poller] is the user-facing API for working with pollers in
JeroMQ.

Pollers are constructed by calling [ZContext.createPoller][create-poller]. This
is essential because it registers the poller with the context, so that when the
context is closed, the poller and selector resources are cleaned up properly.

## org.zeromq.ZPoller

[ZPoller][zpoller] is a work-in-progress rewrite of the polling API.

> If you use ZPoller, please update these docs with more information!

## See also

* [zguide: Handling Multiple Sockets][zguide-polling]: general
  information about polling in ZeroMQ


[zmq-poll-poller]: https://static.javadoc.io/org.zeromq/jeromq/0.6.0/zmq/poll/Poller.html
[zmq-poller]: https://static.javadoc.io/org.zeromq/jeromq/0.6.0/org/zeromq/ZMQ.Poller.html
[create-poller]: https://static.javadoc.io/org.zeromq/jeromq/0.6.0/org/zeromq/ZContext.html#createPoller(int)
[zpoller]: https://static.javadoc.io/org.zeromq/jeromq/0.6.0/org/zeromq/ZPoller.html
[zguide-polling]: https://zguide.zeromq.org/docs/chapter2/#Handling-Multiple-Sockets
