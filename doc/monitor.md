# Monitor

ZMQ does not provide a logging API, but instead used monitors for notifications and debug. It uses a dedicated socket that
will receive events from all sockets using a custom serialization, which is not compatible with the one described in zmq_socket_monitor(3).

Or it can also handle directly the event in the caller’s context using a hook that consumes events.

There is 4 classes handling events.

## zmq.ZMQ.Event

It’s the low level implementation, close to the C implementation.

It doesn’t try to resolve argument as types; they are simply integer that needs further processing to be resolved to
high level objects.

It provides a `zmq.ZMQ.Event.getChannel(zmq.Ctx)` to map an internal file descriptor integer value to an effective
`java.nio.channels.SelectableChannel` object. If used through a monitoring socket, the status of the channel might be
different from when the event was generated, as processing is asynchronous.

A hook that consume those kind of events can be declared by using `zmq.SocketBase.setEventHook(ZMQ.EventConsummer consumer, int events)`

A socket that will received serialized events of this kind can be declared by using `zmq.SocketBase.monitor(String addr, int events)`. 
The address is the endpoint of an IPC PAIR socket.

## org.zeromq.ZMQ.Event

A first try at implement a high level wrapper. It is not very consistent and being a nested class reduces code readability.

## org.zeromq.ZMonitor.ZEvent

Another incomplete implementation of a high level wrapper. Again, being a nested class reduce code readability.
It also stores the value as a String, which is not very usable and uses `System.out.println on many places.

## org.zeromq.ZEvent

A more advanced implementation, that return high level java object whenever possible and is more readable.

A hook that consume those kind of events can be declared by using `org.zeromq.ZMQ.Socket.setEventHook(ZEvent.ZEventConsummer consumer, int events)`

A socket that will received serialized events of this kind can be declared by using `org.zeromq.ZMQ.Socket.monitor(String addr, int events)`.
The address is the endpoint of an IPC PAIR socket.
