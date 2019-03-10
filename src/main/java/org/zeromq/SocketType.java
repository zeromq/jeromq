package org.zeromq;

/**
 * Socket Type enumeration
 *
 * @author Isa Hekmatizadeh
 */
@SuppressWarnings("deprecation")
public enum SocketType
{
    /**
     * <p>Flag to specify a exclusive pair of sockets.</p>
     *
     * A socket of type PAIR can only be connected to a single peer at any one time.
     * <br>
     * No message routing or filtering is performed on messages sent over a PAIR socket.
     * <br>
     * When a PAIR socket enters the mute state due to having reached the high water mark for the connected peer,
     * or if no peer is connected, then any send() operations on the socket shall block until the peer becomes available for sending;
     * messages are not discarded.
     * <br>
     * <table border="1" >
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>PAIR</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Unrestricted</td></tr>
     * <tr><td>Incoming routing strategy</td><td>N/A</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>N/A</td></tr>
     * <tr><td>Action in mute state</td><td>Block</td></tr>
     * </table>
     * <p>
     * <strong>PAIR sockets are designed for inter-thread communication across the inproc transport
     * and do not implement functionality such as auto-reconnection.
     * PAIR sockets are considered experimental and may have other missing or broken aspects.</strong>
     */
    PAIR(ZMQ.PAIR),

    /**
     * <p>Flag to specify a PUB socket, receiving side must be a SUB or XSUB.</p>
     *
     * A socket of type PUB is used by a publisher to distribute data.
     * <br>
     * Messages sent are distributed in a fan out fashion to all connected peers.
     * <br>
     * The {@link org.zeromq.ZMQ.Socket#recv()} function is not implemented for this socket type.
     * <br>
     * When a PUB socket enters the mute state due to having reached the high water mark for a subscriber,
     * then any messages that would be sent to the subscriber in question shall instead be dropped until the mute state ends.
     * <br>
     * The send methods shall never block for this socket type.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.ZMQ#SUB}, {@link org.zeromq.ZMQ#XSUB}</td></tr>
     * <tr><td>Direction</td><td>Unidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Send only</td></tr>
     * <tr><td>Incoming routing strategy</td><td>N/A</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>Fan out</td></tr>
     * <tr><td>Action in mute state</td><td>Drop</td></tr>
     * </table>
     */
    PUB(ZMQ.PUB),

    /**
     * <p>Flag to specify the receiving part of the PUB or XPUB socket.</p>
     *
     * A socket of type SUB is used by a subscriber to subscribe to data distributed by a publisher.
     * <br>
     * Initially a SUB socket is not subscribed to any messages,
     * use the {@link org.zeromq.ZMQ.Socket#subscribe(byte[])} option to specify which messages to subscribe to.
     * <br>
     * The send methods are not implemented for this socket type.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.ZMQ#PUB}, {@link org.zeromq.ZMQ#XPUB}</td></tr>
     * <tr><td>Direction</td><td>Unidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Receive only</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>N/A</td></tr>
     * </table>
     */
    SUB(ZMQ.SUB),

    /**
     * <p>Flag to specify a REQ socket, receiving side must be a REP or ROUTER.</p>
     *
     * A socket of type REQ is used by a client to send requests to and receive replies from a service.
     * <br>
     * This socket type allows only an alternating sequence of send(request) and subsequent recv(reply) calls.
     * <br>
     * Each request sent is round-robined among all services, and each reply received is matched with the last issued request.
     * <br>
     * If no services are available, then any send operation on the socket shall block until at least one service becomes available.
     * <br>
     * The REQ socket shall not discard messages.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.ZMQ#REP}, {@link org.zeromq.ZMQ#ROUTER}</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Send, Receive, Send, Receive, ...</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Last peer</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>Round-robin</td></tr>
     * <tr><td>Action in mute state</td><td>Block</td></tr>
     * </table>
     */
    REQ(ZMQ.REQ),

    /**
     * <p>Flag to specify the receiving part of a REQ or DEALER socket.</p>
     *
     * A socket of type REP is used by a service to receive requests from and send replies to a client.
     * <br>
     * This socket type allows only an alternating sequence of recv(request) and subsequent send(reply) calls.
     * <br>
     * Each request received is fair-queued from among all clients, and each reply sent is routed to the client that issued the last request.
     * <br>
     * If the original requester does not exist any more the reply is silently discarded.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.ZMQ#REQ}, {@link org.zeromq.ZMQ#DEALER}</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Receive, Send, Receive, Send, ...</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>Last peer</td></tr>
     * </table>
     */
    REP(ZMQ.REP),

    /**
     * <p>Flag to specify a DEALER socket (aka XREQ).</p>
     *
     * DEALER is really a combined ventilator / sink
     * that does load-balancing on output and fair-queuing on input
     * with no other semantics. It is the only socket type that lets
     * you shuffle messages out to N nodes and shuffle the replies
     * back, in a raw bidirectional asynch pattern.
     * <br>
     * A socket of type DEALER is an advanced pattern used for extending request/reply sockets.
     * <br>
     * Each message sent is round-robined among all connected peers, and each message received is fair-queued from all connected peers.
     * <br>
     * When a DEALER socket enters the mute state due to having reached the high water mark for all peers,
     * or if there are no peers at all, then any send() operations on the socket shall block
     * until the mute state ends or at least one peer becomes available for sending; messages are not discarded.
     * <br>
     * When a DEALER socket is connected to a {@link org.zeromq.ZMQ#REP} socket each message sent must consist of
     * an empty message part, the delimiter, followed by one or more body parts.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.ZMQ#ROUTER}, {@link org.zeromq.ZMQ#REP}, {@link org.zeromq.ZMQ#DEALER}</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Unrestricted</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>Round-robin</td></tr>
     * <tr><td>Action in mute state</td><td>Block</td></tr>
     * </table>
     */
    DEALER(ZMQ.DEALER),

    /**
     * <p>Flag to specify ROUTER socket (aka XREP).</p>
     *
     * ROUTER is the socket that creates and consumes request-reply
     * routing envelopes. It is the only socket type that lets you route
     * messages to specific connections if you know their identities.
     * <br>
     * A socket of type ROUTER is an advanced socket type used for extending request/reply sockets.
     * <br>
     * When receiving messages a ROUTER socket shall prepend a message part containing the identity
     * of the originating peer to the message before passing it to the application.
     * <br>
     * Messages received are fair-queued from among all connected peers.
     * <br>
     * When sending messages a ROUTER socket shall remove the first part of the message
     * and use it to determine the identity of the peer the message shall be routed to.
     * If the peer does not exist anymore the message shall be silently discarded by default,
     * unless {@link org.zeromq.ZMQ.Socket#setRouterMandatory(boolean)} socket option is set to true.
     * <br>
     * When a ROUTER socket enters the mute state due to having reached the high water mark for all peers,
     * then any messages sent to the socket shall be dropped until the mute state ends.
     * <br>
     * Likewise, any messages routed to a peer for which the individual high water mark has been reached shall also be dropped,
     * unless {@link org.zeromq.ZMQ.Socket#setRouterMandatory(boolean)} socket option is set to true.
     * <br>
     * When a {@link org.zeromq.ZMQ#REQ} socket is connected to a ROUTER socket, in addition to the identity of the originating peer
     * each message received shall contain an empty delimiter message part.
     * <br>
     * Hence, the entire structure of each received message as seen by the application becomes: one or more identity parts,
     * delimiter part, one or more body parts.
     * <br>
     * When sending replies to a REQ socket the application must include the delimiter part.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.ZMQ#DEALER}, {@link org.zeromq.ZMQ#REQ}, {@link org.zeromq.ZMQ#ROUTER}</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Unrestricted</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>See text</td></tr>
     * <tr><td>Action in mute state</td><td>Drop (See text)</td></tr>
     * </table>
     */
    ROUTER(ZMQ.ROUTER),

    /**
     * <p>Flag to specify the receiving part of a PUSH socket.</p>
     *
     * A socket of type ZMQ_PULL is used by a pipeline node to receive messages from upstream pipeline nodes.
     * <br>
     * Messages are fair-queued from among all connected upstream nodes.
     * <br>
     * The send() function is not implemented for this socket type.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.ZMQ#PUSH}</td></tr>
     * <tr><td>Direction</td><td>Unidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Receive only</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>N/A</td></tr>
     * <tr><td>Action in mute state</td><td>Block</td></tr>
     * </table>
     */
    PULL(ZMQ.PULL),

    /**
     * <p>Flag to specify a PUSH socket, receiving side must be a PULL.</p>
     *
     * A socket of type PUSH is used by a pipeline node to send messages to downstream pipeline nodes.
     * <br>
     * Messages are round-robined to all connected downstream nodes.
     * <br>
     * The recv() function is not implemented for this socket type.
     * <br>
     * When a PUSH socket enters the mute state due to having reached the high water mark for all downstream nodes,
     * or if there are no downstream nodes at all, then any send() operations on the socket shall block until the mute state ends
     * or at least one downstream node becomes available for sending; messages are not discarded.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.ZMQ#PULL}</td></tr>
     * <tr><td>Direction</td><td>Unidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Send only</td></tr>
     * <tr><td>Incoming routing strategy</td><td>N/A</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>Round-robin</td></tr>
     * <tr><td>Action in mute state</td><td>Block</td></tr>
     * </table>
     */
    PUSH(ZMQ.PUSH),

    /**
     * <p>Flag to specify a XPUB socket, receiving side must be a SUB or XSUB.</p>
     *
     * Subscriptions can be received as a message. Subscriptions start with
     * a '1' byte. Unsubscriptions start with a '0' byte.
     * <br>
     * Same as {@link org.zeromq.ZMQ#PUB} except that you can receive subscriptions from the peers in form of incoming messages.
     * <br>
     * Subscription message is a byte '1' (for subscriptions) or byte '0' (for unsubscriptions) followed by the subscription body.
     * <br>
     * Messages without a sub/unsub prefix are also received, but have no effect on subscription status.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.ZMQ#SUB}, {@link org.zeromq.ZMQ#XSUB}</td></tr>
     * <tr><td>Direction</td><td>Unidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Send messages, receive subscriptions</td></tr>
     * <tr><td>Incoming routing strategy</td><td>N/A</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>Fan out</td></tr>
     * <tr><td>Action in mute state</td><td>Drop</td></tr>
     * </table>
     */
    XPUB(ZMQ.XPUB),

    /**
     * <p>Flag to specify the receiving part of the PUB or XPUB socket.</p>
     *
     * Same as {@link org.zeromq.ZMQ#SUB} except that you subscribe by sending subscription messages to the socket.
     * <br>
     * Subscription message is a byte '1' (for subscriptions) or byte '0' (for unsubscriptions) followed by the subscription body.
     * <br>
     * Messages without a sub/unsub prefix may also be sent, but have no effect on subscription status.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.ZMQ#PUB}, {@link org.zeromq.ZMQ#XPUB}</td></tr>
     * <tr><td>Direction</td><td>Unidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Receive messages, send subscriptions</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>N/A</td></tr>
     * <tr><td>Action in mute state</td><td>Drop</td></tr>
     * </table>
     */
    XSUB(ZMQ.XSUB),

    /**
     * <p>Flag to specify a STREAM socket.</p>
     *
     * A socket of type STREAM is used to send and receive TCP data from a non-ØMQ peer, when using the tcp:// transport.
     * A STREAM socket can act as client and/or server, sending and/or receiving TCP data asynchronously.
     * <br>
     * When receiving TCP data, a STREAM socket shall prepend a message part containing the identity
     * of the originating peer to the message before passing it to the application.
     * <br>
     * Messages received are fair-queued from among all connected peers.
     * When sending TCP data, a STREAM socket shall remove the first part of the message
     * and use it to determine the identity of the peer the message shall be routed to,
     * and unroutable messages shall cause an EHOSTUNREACH or EAGAIN error.
     * <br>
     * To open a connection to a server, use the {@link org.zeromq.ZMQ.Socket#connect(String)} call, and then fetch the socket identity using the {@link org.zeromq.ZMQ.Socket#getIdentity()} call.
     * To close a specific connection, send the identity frame followed by a zero-length message.
     * When a connection is made, a zero-length message will be received by the application.
     * Similarly, when the peer disconnects (or the connection is lost), a zero-length message will be received by the application.
     * The {@link org.zeromq.ZMQ#SNDMORE} flag is ignored on data frames. You must send one identity frame followed by one data frame.
     * <br>
     * Also, please note that omitting the SNDMORE flag will prevent sending further data (from any client) on the same socket.
     * <br>
     * <table border="1">
     * <caption> </caption>
     * <tr><th colspan="2">Summary of socket characteristics</th></tr>
     * <tr><td>Compatible peer sockets</td><td>none</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Unrestricted</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>See text</td></tr>
     * <tr><td>Action in mute state</td><td>EAGAIN</td></tr>
     * </table>
     */
    STREAM(ZMQ.STREAM);

    public final int type;

    private SocketType(int socketType)
    {
        this.type = socketType;
    }

    public static SocketType type(int baseType)
    {
        for (SocketType type : values()) {
            if (type.type == baseType) {
                return type;
            }
        }
        throw new IllegalArgumentException("no socket type found with value " + baseType);
    }

    public int type()
    {
        return type;
    }
}
