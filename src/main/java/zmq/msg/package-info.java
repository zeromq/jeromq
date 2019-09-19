/**
 * Provides utility for message allocation within ØMQ.
 * <br>
 * This is a java-only construct, allowing to customize the creation of messages (potentially sharing buffers, for instance).
 *
 * <p>The classes of this package shall be used with {@link zmq.ZMQ#ZMQ_MSG_ALLOCATOR} or {@link zmq.ZMQ#ZMQ_MSG_ALLOCATION_HEAP_THRESHOLD}</p>
 */
package zmq.msg;
