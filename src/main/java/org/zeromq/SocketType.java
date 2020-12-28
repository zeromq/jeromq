package org.zeromq;

import zmq.util.Draft;

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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
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
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
     * <tr><td>Compatible peer sockets</td><td>none</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Unrestricted</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>See text</td></tr>
     * <tr><td>Action in mute state</td><td>EAGAIN</td></tr>
     * </table>
     */
    STREAM(ZMQ.STREAM),

    /**
     * <p>Flag to specify CLIENT socket.</p>
     *
     * <p>The CLIENT socket type talks to one or more SERVER peers. If connected to multiple peers, it scatters sent
     * messages among these peers in a round-robin fashion. On reading, it reads fairly, from each peer in turn. It is
     * reliable, insofar as it does not drop messages in normal cases.</p>
     *
     * <p>If the CLIENT socket has established a connection, send operations will accept messages, queue them, and send
     * them as rapidly as the network allows. The outgoing buffer limit is defined by the high water mark for the
     * socket. If the outgoing buffer is full, or if there is no connected peer, send operations will block, by default.
     * The CLIENT socket will not drop messages.</p>
     *
     * <p>
     *  CLIENT sockets are threadsafe.
     *  They do not accept the ZMQ_SNDMORE option on sends not ZMQ_RCVMORE on receives.
     *  This limits them to single part data.
     *  The intention is to extend the API to allow scatter/gather of multi-part data.
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.SocketType#SERVER}</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Unrestricted</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>Round Robin</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Action in mute state</td><td>Block</td></tr>
     * </table>
     */
    CLIENT(zmq.ZMQ.ZMQ_CLIENT),

    /**
     * <p>
     * Flag to specify SERVER socket.
     * </p>
     * <p>
     * The SERVER socket type talks to zero or more CLIENT peers. Each outgoing message is sent to a specific peer
     * CLIENT. A SERVER socket can only reply to an incoming message: the CLIENT peer must always initiate a
     * conversation.
     * </p>
     * <p>
     * Each received message has a routing_id that is a 32-bit unsigned integer. To send a message to a given CLIENT
     * peer the application must set the peer’s routing_id on the message.
     * </p>
     * <p>
     *  SERVER sockets are threadsafe.
     *  They do not accept the ZMQ_SNDMORE option on sends not ZMQ_RCVMORE on receives.
     *  This limits them to single part data.
     *  The intention is to extend the API to allow scatter/gather of multi-part data.
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.SocketType#CLIENT}</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Unrestricted</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>See text</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Action in mute state</td><td>Fail</td></tr>
     * </table>
     */
    SERVER(zmq.ZMQ.ZMQ_SERVER),

    /**
     * <p>Flag to specify RADIO socket.</p>
     * <p>
     * The radio-dish pattern is used for one-to-many distribution of data from a single publisher to multiple subscribers in a fan out fashion.
     * Radio-dish is using groups (vs Pub-sub topics), Dish sockets can join a group and each message sent by Radio sockets belong to a group.
     * </p>
     * <p>
     * Groups are strings limited to 16 chars length (including null).
     * The intention is to increase the length to 40 chars (including null).
     * The encoding of groups shall be UTF8.
     * Groups are matched using exact matching (vs prefix matching of PubSub).
     * </p>
     * <p>A socket of type RADIO is used by a publisher to distribute data.
     * Each message belong to a group, a group is specified with {@link ZFrame#setGroup(String)}.
     * Messages are distributed to all members of a group.
     * The {@link ZMQ.Socket#recv(int)} function is not implemented for this socket type.
     * </p>
     * <p>When a RADIO socket enters the mute state due to having reached the high water mark for a subscriber,
     * then any messages that would be sent to the subscriber in question shall instead be dropped
     * until the mute state ends. The {@link ZMQ.Socket#send(byte[], int)} function shall never block for this socket type.
     * </p>
     * <p>
     * NOTE: RADIO sockets are threadsafe.
     * They do not accept the ZMQ_SNDMORE option on sends.
     * This limits them to single part data.
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.SocketType#DISH}</td></tr>
     * <tr><td>Direction</td><td>Unidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Send only</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>Fan out</td></tr>
     * <tr><td>Incoming routing strategy</td><td>N/A</td></tr>
     * <tr><td>Action in mute state</td><td>Drop</td></tr>
     * </table>
     * <p>
     * NOTE: RADIO is still in draft phase.
     * </p>
     */
    @Draft
    RADIO(zmq.ZMQ.ZMQ_RADIO),

    /**
     * <p>Flag to specify DISH socket.</p>
     *
     * <p>
     * The radio-dish pattern is used for one-to-many distribution of data from a single publisher to multiple subscribers in a fan out fashion.
     * Radio-dish is using groups (vs Pub-sub topics), Dish sockets can join a group and each message sent by Radio sockets belong to a group.
     * </p>
     * <p>
     * Groups are strings limited to 16 chars length (including null).
     * The intention is to increase the length to 40 chars (including null).
     * The encoding of groups shall be UTF8.
     * Groups are matched using exact matching (vs prefix matching of PubSub).
     * </p>
     *
     * A socket of type DISH is used by a subscriber to subscribe to groups distributed by a radio.
     * Initially a DISH socket is not subscribed to any groups, use {@link org.zeromq.ZMQ.Socket#join(String)} to join a group.
     * To get the group the message belong, call {@link ZFrame#getGroup()}.
     * The {@link org.zeromq.ZMQ.Socket#send(byte[], int)} function is not implemented for this socket type.
     *
     * <p>
     * NOTE: DISH sockets are threadsafe.
     * They do not accept ZMQ_RCVMORE on receives.
     * This limits them to single part data.
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.SocketType#RADIO}</td></tr>
     * <tr><td>Direction</td><td>Unidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Receive only</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>N/A</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * </table>
     * NOTE: DISH is still in draft phase.
     */
    @Draft
    DISH(zmq.ZMQ.ZMQ_DISH),

    /**
     * <p>Flag to specify CHANNEL socket.</p>
     * <p>
     * The channel pattern is the thread-safe version of the exclusive pair pattern.
     * The channel pattern is used to connect a peer to precisely one other peer.
     * This pattern is used for inter-thread communication across the inproc transport.
     * </p>
     * <p>
     * A socket of type 'CHANNEL' can only be connected to a single peer at any one
     * time.  No message routing or filtering is performed on messages sent over a
     * 'CHANNEL' socket.
     * </p>
     * <p>
     * When a 'CHANNEL' socket enters the 'mute' state due to having reached the
     * high water mark for the connected peer, or, for connection-oriented transports,
     * if the ZMQ_IMMEDIATE option is set and there is no connected peer, then
     * any {@link org.zeromq.ZMQ.Socket#send(byte[], int)} operations on the socket shall block until the peer
     * becomes available for sending; messages are not discarded.
     * </p>
     * <p>
     * While 'CHANNEL' sockets can be used over transports other than 'inproc',
     * their inability to auto-reconnect coupled with the fact new incoming connections will
     * be terminated while any previous connections (including ones in a closing state)
     * exist makes them unsuitable for TCP in most cases.
     * </p>
     * <p>
     * NOTE: 'CHANNEL' sockets are designed for inter-thread communication across
     * the 'inproc' transport and do not implement functionality such
     * as auto-reconnection.
     * </p>
     * <p>
     * NOTE: 'CHANNEL' sockets are threadsafe. They do not accept ZMQ_RCVMORE on receives.
     * This limits them to single part data.
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
     * <tr><td>Compatible peer sockets</td><td>CHANNEL</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Unrestricted</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>N/A</td></tr>
     * <tr><td>Incoming routing strategy</td><td>N/A</td></tr>
     * <tr><td>Action in mute state</td><td>Block</td></tr>
     * </table>
     * <p>
     * NOTE: CHANNEL is still in draft phase.
     * </p>
     */
    @Draft
    CHANNEL(zmq.ZMQ.ZMQ_CHANNEL),

    /**
     * <p>Flag to specify PEER socket.
     * </p>
     * <p>
     * A 'PEER' socket talks to a set of 'PEER' sockets.
     * </p>
     * <p>
     * To connect and fetch the 'routing_id' of the peer use {@link ZMQ.Socket#connectPeer(String)}.
     * </p>
     * <p>
     * Each received message has a 'routing_id' that is a 32-bit unsigned integer.
     * The application can fetch this with {@link ZFrame#getRoutingId()}.
     * </p>
     * <p>
     * To send a message to a given 'PEER' peer the application must set the peer's
     * 'routing_id' on the message, using {@link ZFrame#setRoutingId(int)}.
     * </p>
     * <p>
     * If the 'routing_id' is not specified, or does not refer to a connected client
     * peer, the send call will fail with EHOSTUNREACH. If the outgoing buffer for
     * the peer is full, the send call shall block, unless ZMQ_DONTWAIT is
     * used in the send, in which case it shall fail with EAGAIN. The 'PEER'
     * socket shall not drop messages in any case.
     * </p>
     * <p>
     * NOTE: 'PEER' sockets are threadsafe. They do not accept the ZMQ_SNDMORE
     * option on sends not ZMQ_RCVMORE on receives. This limits them to single part
     * data.
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
     * <tr><td>Compatible peer sockets</td><td>PEER</td></tr>
     * <tr><td>Direction</td><td>Bidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Unrestricted</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>See text</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Action in mute state</td><td>Return EAGAIN</td></tr>
     * </table>
     * NOTE: PEER is still in draft phase.
     */
    @Draft
    PEER(zmq.ZMQ.ZMQ_PEER),

    /**
     * <p>Flag to specify RAW socket.</p>
     *
     */
    @Draft
    RAW(zmq.ZMQ.ZMQ_RAW),

    /**
     * <p>Flag to specify SCATTER socket.
     * </p>
     * <p>
     * The scatter-gather pattern is the thread-safe version of the pipeline pattern.
     * The scatter-gather pattern is used for distributing data to nodes arranged in a pipeline.
     * Data always flows down the pipeline, and each stage of the pipeline
     * is connected to at least one node.
     * When a pipeline stage is connected to multiple nodes data is round-robined among all connected nodes.
     * </p>
     * <p>
     * When a 'SCATTER' socket enters the 'mute' state due to having reached the
     * high water mark for all downstream nodes, or, for connection-oriented transports,
     * if the ZMQ_IMMEDIATE option is set and there are no downstream nodes at all,
     * then any {@link org.zeromq.ZMQ.Socket#send(byte[], int)} operations on the socket shall block until the mute
     * state ends or at least one downstream node becomes available for sending;
     * messages are not discarded.
     * </p>
     * <p>
     * NOTE: 'SCATTER' sockets are threadsafe. They do not accept ZMQ_RCVMORE on receives.
     * This limits them to single part data.
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.SocketType#GATHER}</td></tr>
     * <tr><td>Direction</td><td>Unidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Send only</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>Round-robin</td></tr>
     * <tr><td>Incoming routing strategy</td><td>N/A</td></tr>
     * <tr><td>Action in mute state</td><td>Block</td></tr>
     * </table>
     * NOTE: SCATTER is still in draft phase.
     */
    @Draft
    SCATTER(zmq.ZMQ.ZMQ_SCATTER),

    /**
     * <p>Flag to specify GATHER socket.
     * </p>
     * <p>
     * The scatter-gather pattern is the thread-safe version of the pipeline pattern.
     * The scatter-gather pattern is used for distributing data to nodes arranged in a pipeline.
     * Data always flows down the pipeline, and each stage of the pipeline
     * is connected to at least one node.
     * When a pipeline stage is connected to multiple nodes data is round-robined among all connected nodes.
     * </p>
     * <p>
     * A socket of type 'GATHER' is used by a scatter-gather node to receive messages
     * from upstream scatter-gather nodes. Messages are fair-queued from among all
     * connected upstream nodes. The {@link org.zeromq.ZMQ.Socket#send(byte[], int)} function is not implemented for
     * this socket type.
     * </p>
     * <p>
     * NOTE: 'GATHER' sockets are threadsafe. They do not accept ZMQ_RCVMORE on receives.
     * This limits them to single part data.
     * <p>
     * <table border="1">
     * <caption><strong>Summary of socket characteristics</strong></caption>
     * <tr><td>Compatible peer sockets</td><td>{@link org.zeromq.SocketType#SCATTER}</td></tr>
     * <tr><td>Direction</td><td>Unidirectional</td></tr>
     * <tr><td>Send/receive pattern</td><td>Receive only</td></tr>
     * <tr><td>Outgoing routing strategy</td><td>N/A</td></tr>
     * <tr><td>Incoming routing strategy</td><td>Fair-queued</td></tr>
     * <tr><td>Action in mute state</td><td>Block</td></tr>
     * </table>
     * NOTE: SCATTER is still in draft phase.
     */
    @Draft
    GATHER(zmq.ZMQ.ZMQ_GATHER);

    public final int type;

    SocketType(int socketType)
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
