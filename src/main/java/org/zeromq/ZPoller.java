package org.zeromq;

import java.io.Closeable;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import zmq.poll.PollItem;
import zmq.util.Objects;
import zmq.util.function.BiFunction;
import zmq.util.function.Function;

/**
 * Rewritten poller for ØMQ.
 * <br/>
 * Polls selectable channels and sockets for specified events.
 *
 * <p>This poller can be used in two ways:</p>
 *
 * <ul>
 * <li> the traditional one, where you make something like
 * <pre>
 * {@code
            ZPoller poller = ...
            poller.register(socket, ZPoller.POLLIN);
            poller.register(channel, ZPoller.OUT);

            int events = poller.poll(-1L);

            if (poller.isReadable(socket)) {
                ...
            }
            if (poller.writable(channel)) {
                ...
            }
}
</pre>
 * <li> the event-driven way
 * <pre>
 * {@code
 *
            ZPoller poller = ...

            poller.setGlobalHandler(...)
            ZPoller.EventsHandler handler = ...

            // The events method of the handler will be called
            poller.register(channel, handler, ZPoller.IN);

            // The events method of the global handler will be called
            poller.register(socket, ZPoller.POLLOUT);

            poller.poll(-1L);
            // handlers have been called
}
</pre>
</ul>
 * The motivations of this rewriting are:
 * <ul>
 * <li> the bare poller used {@link zmq.ZMQ#poll(Selector, PollItem[], int, long)}. This method did not allow
 * to choose the selector used for polling, relying on a ThreadLocal, which is dangerous.
 * <li> the bare poller use algorithms tailored for languages with manual allocation.
 * No need here as Java allows more flexibility. TODO There still may be a small penalty cost.
 * </ul>
 *
 * <p>It’s thread safe, but newly registered items will only be handled at the next poll iteration,
 * so if {@code poller.poll(-1L)} is used, adding items will have no effect. The situation is the same with the global handler</p>
 */
// Poller for 0MQ.
public class ZPoller implements Closeable
{
    // contract for events
    public interface EventsHandler
    {
        /**
         * Called when the poller intercepts events.
         *
         * @param socket    the socket with events
         * @param events    the interesting events as an ORed combination of IN, OUT, ERR
         * @return <b>true to continue the polling</b>, false to stop it
         */
        boolean events(Socket socket, int events);

        /**
         * Called when the poller intercepts events.
         *
         * @param channel   the channel with events
         * @param events    the interesting events as an ORed combination of IN, OUT, ERR
         * @return <b>true to continue the polling</b>, false to stop it
         */
        boolean events(SelectableChannel channel, int events);
    }

    public static class ComposeEventsHandler implements EventsHandler
    {
        private final BiFunction<Socket, Integer, Boolean>            sockets;
        private final BiFunction<SelectableChannel, Integer, Boolean> channels;

        public ComposeEventsHandler(BiFunction<Socket, Integer, Boolean> sockets,
                                    BiFunction<SelectableChannel, Integer, Boolean> channels)
        {
            super();
            this.sockets = sockets;
            this.channels = channels;
        }

        @Override
        public boolean events(Socket socket, int events)
        {
            assert (sockets != null) : "A Socket Handler needs to be set";
            return sockets.apply(socket, events);
        }

        @Override
        public boolean events(SelectableChannel channel, int events)
        {
            assert (channels != null) : "A SelectableChannel Handler needs to be set";
            return channels.apply(channel, events);
        }
    }

    // contract for items. Useful for providing own implementation.
    public interface ItemHolder
    {
        // the inner ZMQ poll item
        PollItem item();

        // the related ZeroMQ socket
        Socket socket();

        // the associated events handler
        EventsHandler handler();
    }

    // contract for items creation. Useful for delegating.
    public interface ItemCreator
    {
        /**
         * Creates a new holder for a poll item.
         *
         * @param socket    the socket to poll
         * @param handler   the optional handler for polled events
         * @param events    the interested events
         * @return a new poll item holder
         */
        ItemHolder create(Socket socket, EventsHandler handler, int events);

        /**
         * Creates a new holder for a poll item.
         *
         * @param channel   the channel to poll
         * @param handler   the optional handler for polled events.
         * @param events    the interested events
         * @return a new poll item holder
         */
        ItemHolder create(SelectableChannel channel, EventsHandler handler, int events);
    }

    // re-re-implementation of poll item
    public static class ZPollItem extends ZMQ.PollItem implements ItemHolder
    {
        private final EventsHandler handler;

        public ZPollItem(final Socket socket, final EventsHandler handler, final int ops)
        {
            super(socket, ops);
            this.handler = handler;
            assert (item() != null);
        }

        public ZPollItem(final SelectableChannel channel, final EventsHandler handler, final int ops)
        {
            super(channel, ops);
            this.handler = handler;
            assert (item() != null);
        }

        @Override
        public PollItem item()
        {
            return base();
        }

        @Override
        public Socket socket()
        {
            return getSocket();
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((item() == null) ? 0 : item().hashCode());
            result = prime * result + ((getRawSocket() == null) ? 0 : getRawSocket().hashCode());
            result = prime * result + ((socket() == null) ? 0 : socket().hashCode());
            result = prime * result + ((handler() == null) ? 0 : handler().hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof ItemHolder)) {
                return false;
            }
            ItemHolder other = (ItemHolder) obj;
            // this.item() is never null
            if (other.item() == null) {
                return false;
            }
            // no checking on item() as zmq.PollItem does not override equals()
            if (item().getRawSocket() == null) {
                if (other.item().getRawSocket() != null) {
                    return false;
                }
            }
            else if (!item().getRawSocket().equals(other.item().getRawSocket())) {
                return false;
            }
            if (socket() == null) {
                if (other.socket() != null) {
                    return false;
                }
            }
            else if (!socket().equals(other.socket())) {
                return false;
            }
            if (handler() == null) {
                return other.handler() == null;
            }
            else {
                return handler().equals(other.handler());
            }
        }

        @Override
        public EventsHandler handler()
        {
            return handler;
        }
    }

    private class CompositePollItem implements ItemHolder, EventsHandler
    {
        private final Set<ItemHolder>        holders = ConcurrentHashMap.newKeySet();
        private final Socket                 socket;
        private final SelectableChannel      channel;

        private PollItem      item;

        public CompositePollItem(final Object socketOrChannel)
        {
            this.socket = socketOrChannel instanceof Socket ? (Socket) socketOrChannel : null;
            this.channel = socketOrChannel instanceof SelectableChannel ? (SelectableChannel) socketOrChannel : null;
            assert (socket != null || channel != null);
        }

        @Override
        public PollItem item()
        {
            if (item == null) {
                item = createItem();
            }
            return item;
        }

        private PollItem createItem()
        {
            if (socket == null) {
                return new PollItem(channel, ops());
            }
            else {
                return new PollItem(socket.base(), ops());
            }
        }

        private int ops()
        {
            int ops = 0;
            for (ItemHolder holder : holders) {
                int interest = holder.item().zinterestOps();
                ops |= interest;
            }
            return ops;
        }

        @Override
        public Socket socket()
        {
            return socket;
        }

        @Override
        public EventsHandler handler()
        {
            return this;
        }

        @Override
        public boolean events(Socket socket, int events)
        {
            return doEvent(h -> h.events(socket, events), events);
        }

        @Override
        public boolean events(SelectableChannel channel, int events)
        {
            return doEvent(h -> h.events(channel, events), events);
        }

        private boolean doEvent(Function<EventsHandler, Boolean> handle, int events)
        {
            boolean has = false;
            boolean first = true;
            for (ItemHolder holder : holders) {
                if (holder.item().hasEvent(events)) {
                    if (first) {
                        first = false;
                        has = true;
                    }
                    EventsHandler handler = holder.handler() == null ? currentGlobalHandler.get() : holder.handler();
                    if (handler != null) {
                        has &= handle.apply(handler);
                    }
                }
            }
            return has;
        }
    }

    /******************************************************************************/
    /* 0MQ socket events */
    /******************************************************************************/
    /*
     * These values can be ORed to specify what we want to poll for.
     */
    public static final int POLLIN  = Poller.POLLIN;
    public static final int POLLOUT = Poller.POLLOUT;
    public static final int POLLERR = Poller.POLLERR;

    // same values with shorter writing
    public static final int IN  = POLLIN;
    public static final int OUT = POLLOUT;
    public static final int ERR = POLLERR;

    // same values with semantic consistency
    public static final int READABLE = POLLIN;
    public static final int WRITABLE = POLLOUT;

    /**
     * Creates a new poller based on the current one.
     * This will be a shadow poller, sharing the same selector and items creator.
     * The global events handler will not be shared.
     *
     * @param poller    the main poller.
     */
    public ZPoller(final ZPoller poller)
    {
        this(poller.creator, poller.selector);
    }

    /**
     * Creates a new poller with a given selector for operational polling.
     *
     * @param selector  the selector to use for polling.
     */
    public ZPoller(final Selector selector)
    {
        this(new SimpleCreator(), selector);
    }

    /**
     * Creates a new poller attached to a given context that will provide
     * selector for operational polling.
     *
     * @param context
     *            the context that will provide the selector to use for polling.
     */
    public ZPoller(final ZContext context)
    {
        this(new SimpleCreator(), context);
    }

    /**
     * Creates a new poller based on the current one. This will be a shadow
     * poller, sharing the same selector. The global events handler will not be
     * shared.
     *
     * @param creator   the items creator
     * @param poller    the main poller.
     */
    public ZPoller(final ItemCreator creator, final ZPoller poller)
    {
        this(creator, poller.selector);
    }

    /**
     * Creates a new poller attached to a given context that will provide
     * selector for operational polling.
     *
     * @param creator
     *            the items creator
     * @param context
     *            the context that will provide the selector to use for polling.
     */
    public ZPoller(final ItemCreator creator, final ZContext context)
    {
        this(creator, context.selector());
    }

    /**
     * Creates a new poller.
     *
     * @param creator   the items creator
     * @param selector  the selector to use for polling.
     */
    private ZPoller(final ItemCreator creator, final Selector selector)
    {
        Objects.requireNonNull(creator, "Item creator is mandatory for ZPoller");
        Objects.requireNonNull(selector, "Selector is mandatory for ZPoller");

        this.creator = creator;
        this.selector = selector;
        items = new ConcurrentHashMap<>();
    }

    // creates a new poll item
    protected ItemHolder create(final Socket socket, final EventsHandler handler, final int events)
    {
        Objects.requireNonNull(socket, "Socket has to be non-null");
        return creator.create(socket, handler, events);
    }

    // creates a new poll item
    protected ItemHolder create(final SelectableChannel channel, final EventsHandler handler, final int events)
    {
        Objects.requireNonNull(channel, "Channel has to be non-null");
        return creator.create(channel, handler, events);
    }

    /**
     * Sets the global events handler for all registered sockets.
     *
     * @param globalHandler the events handler to set
     */
    public void setGlobalHandler(final EventsHandler globalHandler)
    {
        this.globalHandler.set(globalHandler);
    }

    /**
     * Returns the global events handler for all registered sockets.
     *
     * @return the global events handler for all registered sockets.
     */
    public EventsHandler getGlobalHandler()
    {
        return globalHandler.get();
    }

    /**
     * Register a Socket for polling on specified events.
     *
     * @param socket   the registering socket.
     * @param handler  the events handler for this socket
     * @param events   the events to listen to, as a mask composed by ORing POLLIN, POLLOUT and POLLERR.
     * @return true if registered, otherwise false
     */
    public final boolean register(final Socket socket, final BiFunction<Socket, Integer, Boolean> handler,
                                  final int events)
    {
        return register(socket, new ComposeEventsHandler(handler, null), events);
    }

    /**
     * Register a Socket for polling on specified events.
     *
     * @param socket   the registering socket.
     * @param handler  the events handler for this socket
     * @param events   the events to listen to, as a mask composed by ORing POLLIN, POLLOUT and POLLERR.
     * @return true if registered, otherwise false
     */
    public final boolean register(final Socket socket, final EventsHandler handler, final int events)
    {
        if (socket == null) {
            return false;
        }
        return add(socket, create(socket, handler, events));
    }

    /**
     * Register a Socket for polling on all events.
     *
     * @param socket   the registering socket.
     * @param handler  the events handler for this socket
     * @return true if registered, otherwise false
     */
    public final boolean register(final Socket socket, final EventsHandler handler)
    {
        return register(socket, handler, POLLIN | POLLOUT | POLLERR);
    }

    /**
     * Register a Socket for polling on specified events, using the global {@link EventsHandler}.
     *
     * @param socket   the registering socket.
     * @param events   the events to listen to, as a mask composed by ORing POLLIN, POLLOUT and POLLERR.
     * @return true if registered, otherwise false
     */
    public final boolean register(final Socket socket, final int events)
    {
        return register(socket, globalHandler.get(), events);
    }

    /**
     * Registers a SelectableChannel for polling on specified events.
     *
     * @param channel   the registering channel.
     * @param handler   the events handler for this channel
     * @param events    the events to listen to, as a mask composed by ORing POLLIN, POLLOUT and POLLERR.
     * @return true if registered, otherwise false
     */
    public final boolean register(final SelectableChannel channel,
                                  final BiFunction<SelectableChannel, Integer, Boolean> handler, final int events)
    {
        return register(channel, new ComposeEventsHandler(null, handler), events);
    }

    /**
     * Registers a SelectableChannel for polling on specified events.
     *
     * @param channel   the registering channel.
     * @param handler   the events handler for this channel
     * @param events    the events to listen to, as a mask composed by ORing POLLIN, POLLOUT and POLLERR.
     * @return true if registered, otherwise false
     */
    public final boolean register(final SelectableChannel channel, final EventsHandler handler, final int events)
    {
        if (channel == null) {
            return false;
        }
        return add(channel, create(channel, handler, events));
    }

    /**
     * Registers a SelectableChannel for polling on all events.
     *
     * @param channel   the registering channel.
     * @param handler   the events handler for this channel
     * @return true if registered, otherwise false
     */
    public final boolean register(final SelectableChannel channel, final EventsHandler handler)
    {
        return register(channel, handler, IN | OUT | ERR);
    }

    /**
     * Registers a SelectableChannel for polling on specified events.
     *
     * @param channel   the registering channel.
     * @param events    the events to listen to, as a mask composed by ORing POLLIN, POLLOUT and POLLERR.
     * @return true if registered, otherwise false
     */
    public final boolean register(final SelectableChannel channel, final int events)
    {
        return register(channel, globalHandler.get(), events);
    }

    /**
     *
     * @return the number of registered object, mixing Sockets and SelectableChannels
     */
    public int registered()
    {
        return items.size();
    }
    /**
     * Register an ItemHolder for polling on specified events.
     *
     * @param item   the registering item.
     * @return true if registered, otherwise false
     */
    public final boolean register(final ItemHolder item)
    {
        return add(null, item);
    }

    /**
     * Unregister a Socket or SelectableChannel for polling on the specified events.
     *
     * @param socketOrChannel   the Socket or SelectableChannel to be unregistered
     * @return true if unregistered, otherwise false
     * TODO would it be useful to unregister only for specific events ?
     */
    public final synchronized boolean unregister(final Object socketOrChannel)
    {
        if (socketOrChannel == null) {
            return false;
        }
        CompositePollItem removedItems = items.remove(socketOrChannel);
        return removedItems != null;
    }

    /******************************************************************************/
    /* POLLING | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | |*/
    /******************************************************************************/
    /**
     * Issue a poll call, using the specified timeout value.
     * <p>
     * Since ZeroMQ 3.0, the timeout parameter is in <i>milliseconds</i>,
     * but prior to this the unit was <i>microseconds</i>.
     * <p>If timeout is -1 and there is no object registered, it will return immediately</p>
     *
     * @param timeout
     *            the timeout, as per zmq_poll ();
     *            if -1, it will block indefinitely until an event
     *            happens; if 0, it will return immediately;
     *            otherwise, it will wait for at most that many
     *            milliseconds/microseconds (see above).
     * @see "http://api.zeromq.org/3-0:zmq-poll"
     * @return how many objects where signaled by poll ()
     */
    public int poll(final long timeout)
    {
        return poll(timeout, true);
    }

    /**
     * Issue a poll call, using the specified timeout value.
     * <p>If timeout is -1 and there is no object registered, it will return immediately</p>
     *
     * @param timeout           the timeout, as per zmq_poll ();
     * @param dispatchEvents    true to dispatch events using items handler and the global one.
     * @see "http://api.zeromq.org/3-0:zmq-poll"
     * @return how many objects where signaled by poll ()
     */
    protected int poll(final long timeout, final boolean dispatchEvents)
    {
        // Local copy of the items, for consistency
        Set<CompositePollItem> allPolled = new HashSet<>(items.values());
        // get all the raw items
        final Set<PollItem> pollItems = allPolled.stream()
                                                 .map(CompositePollItem::item)
                                                 .collect(Collectors.toSet());
        // polling time
        final int rc = poll(selector, timeout, pollItems);

        if (!dispatchEvents) {
            // raw result
            return rc;
        }

        if (dispatch(allPolled)) {
            // returns event counts after dispatch if everything went fine
            return rc;
        }
        // error in dispatching
        return -1;
    }

    // does the effective polling
    protected int poll(final Selector selector, final long tout, final Collection<zmq.poll.PollItem> items)
    {
        return zmq.ZMQ.poll(selector, items.toArray(new PollItem[0]), items.size(), tout);
    }

    private boolean dispatch(Set<CompositePollItem> allPolled)
    {
        return subDispatch(allPolled);
    }

    /**
     * Dispatches the polled events.
     *
     * @param all   the items used for dispatching
     * @param size  Unused
     * @return true if correctly dispatched, false in case of error
     * @deprecated Uses {@link #dispatch(Collection)} instead, as size is unused
     */
    @SuppressWarnings("unused")
    @Deprecated
    protected boolean dispatch(final Collection<? extends ItemHolder> all, int size)
    {
        return subDispatch(all);
    }

    /**
     * Dispatches the polled events.
     *
     * @param allDispatched   the items used for dispatching
     * @return true if correctly dispatched, false in case of error
     */
    protected boolean dispatch(final Collection<? extends ItemHolder> allDispatched)
    {
        return subDispatch(allDispatched);
    }

    /**
     * dispatches all the polled events to their respective handlers
     *
     * @return true if correctly dispatched, false in case of error
     */
    public boolean dispatch()
    {
        return subDispatch(new HashSet<>(items.values()));
    }

    private boolean subDispatch(final Collection<? extends ItemHolder> allDispatched)
    {
        EventsHandler localGlobalHandler = currentGlobalHandler.get();
        for (ItemHolder holder : allDispatched) {
            EventsHandler handler = holder.handler();
            if (handler == null) {
                handler = localGlobalHandler;
            }
            if (handler == null) {
                // no handler, short-circuit
                continue;
            }
            final PollItem item = holder.item();
            final int events = item.readyOps();

            if (events <= 0) {
                // no events, short-circuit
                continue;
            }
            final Socket socket = holder.socket();
            final SelectableChannel channel = holder.item().getRawSocket();

            if (socket != null) {
                assert (channel == null);
                // dispatch on socket
                if (!handler.events(socket, events)) {
                    return false;
                }
            }
            // dispatch on channel
            if (channel != null && !handler.events(channel, events)) {
                return false;
            }
        }
        // Release the global handler, so it can be refreshed
        currentGlobalHandler.remove();
        return true;
    }

    /******************************************************************************/
    /*| | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | POLLING */
    /******************************************************************************/

    /**
     * Tells if a channel is readable from this poller.
     *
     * @param channel    the channel to ask for.
     * @return true if readable, otherwise false
     */
    public boolean isReadable(final SelectableChannel channel)
    {
        return readable((Object) channel);
    }

    public boolean readable(final SelectableChannel channel)
    {
        return readable((Object) channel);
    }

    /**
     * Tells if a socket is readable from this poller.
     *
     * @param socket    the socket to ask for.
     * @return true if readable, otherwise false
     */
    public boolean isReadable(final Socket socket)
    {
        return readable(socket);
    }

    public boolean readable(final Socket socket)
    {
        return readable((Object) socket);
    }

    // checks for read event
    public boolean readable(final Object socketOrChannel)
    {
        final PollItem it = filter(socketOrChannel, READABLE);
        if (it == null) {
            return false;
        }
        return it.isReadable();
    }

    public boolean pollin(final Socket socket)
    {
        return isReadable(socket);
    }

    public boolean pollin(final SelectableChannel channel)
    {
        return isReadable(channel);
    }

    /**
     * Tells if a channel is writable from this poller.
     *
     * @param channel     the channel to ask for.
     * @return true if writable, otherwise false
     */
    public boolean isWritable(final SelectableChannel channel)
    {
        return writable((Object) channel);
    }

    public boolean writable(final SelectableChannel channel)
    {
        return writable((Object) channel);
    }

    /**
     * Tells if a socket is writable from this poller.
     *
     * @param socket     the socket to ask for.
     * @return true if writable, otherwise false
     */
    public boolean isWritable(final Socket socket)
    {
        return writable((Object) socket);
    }

    public boolean writable(final Socket socket)
    {
        return writable((Object) socket);
    }

    // checks for write event
    public boolean writable(final Object socketOrChannel)
    {
        final PollItem it = filter(socketOrChannel, WRITABLE);
        if (it == null) {
            return false;
        }
        return it.isWritable();
    }

    public boolean pollout(final Socket socket)
    {
        return isWritable(socket);
    }

    public boolean pollout(final SelectableChannel channel)
    {
        return isWritable(channel);
    }

    /**
     * Tells if a channel is in error from this poller.
     *
     * @param channel     the channel to ask for.
     * @return true if in error, otherwise false
     */
    public boolean isError(final SelectableChannel channel)
    {
        return error((Object) channel);
    }

    public boolean error(final SelectableChannel channel)
    {
        return error((Object) channel);
    }

    /**
     * Tells if a socket is in error from this poller.
     *
     * @param socket     the socket to ask for.
     * @return true if in error, otherwise false
     */
    public boolean isError(final Socket socket)
    {
        return error((Object) socket);
    }

    public boolean error(final Socket socket)
    {
        return error((Object) socket);
    }

    // checks for error event
    public boolean error(final Object socketOrChannel)
    {
        final PollItem it = filter(socketOrChannel, ERR);
        if (it == null) {
            return false;
        }
        return it.isError();
    }

    public boolean pollerr(final Socket socket)
    {
        return isError(socket);
    }

    public boolean pollerr(final SelectableChannel channel)
    {
        return isError(channel);
    }

    /**
     * Destroys the poller. Does actually nothing.
     */
    @Override
    public void close()
    {
        destroy();
    }

    /**
     * Destroys the poller without exception.
     */
    public void destroy()
    {
        // Nothing to do
    }

    // selector used for polling
    private final Selector selector;

    // creator of items
    private final ItemCreator creator;
    // managed items
    private final Map<Object, CompositePollItem> items;

    // TODO set of handlers, each with its specified events?
    // optional global events handler
    private final AtomicReference<EventsHandler> globalHandler = new AtomicReference<>(null);
    // Use to cache the global handler for the current thread
    private final ThreadLocal<EventsHandler> currentGlobalHandler = ThreadLocal.withInitial(globalHandler::get);

    // simple creator for poll items
    public static class SimpleCreator implements ItemCreator
    {
        @Override
        public ItemHolder create(final Socket socket, final EventsHandler handler, final int events)
        {
            return new ZPollItem(socket, handler, events);
        }

        @Override
        public ItemHolder create(final SelectableChannel channel, final EventsHandler handler, final int events)
        {
            return new ZPollItem(channel, handler, events);
        }
    }

    // add an item to this poller
    protected boolean add(final Object socketOrChannel, final ItemHolder holder)
    {
        Object key = Optional.ofNullable(socketOrChannel).orElseGet(() -> computeSocketOrChannel(holder));
        CompositePollItem aggregate = items.computeIfAbsent(key, CompositePollItem::new);
        return aggregate.holders.add(holder);
    }

    private Object computeSocketOrChannel(final ItemHolder holder)
    {
        Socket socket = holder.socket();
        SelectableChannel ch = holder.item().getRawSocket();
        if (ch == null && socket == null) {
            throw new IllegalArgumentException("Needs a socket or a channel to register");
        }
        else if (socket != null) {
            return socket;
        }
        else {
            return ch;
        }
    }

    /**
     * Return a new HashSet
     * @param size the initial capacity of the hash table
     * @return a new hash set
     * @deprecated useless
     */
    @Deprecated
    protected Set<ItemHolder> createContainer(int size)
    {
        return new HashSet<>(size);
    }

    /**
     * Return a copy of all the items of this poller.
     */
    protected Collection<ItemHolder> items()
    {
        return new HashSet<>(items.values());
    }

    // gets all the items of this poller regarding the given input
    protected Iterable<ItemHolder> items(final Object socketOrChannel)
    {
        if (socketOrChannel == null || ! items.containsKey(socketOrChannel)) {
            return Collections.emptySet();
        }
        else {
            return items.get(socketOrChannel).holders;
        }
    }

    // filters items to get the first one matching the criteria, or null if none found
    protected PollItem filter(final Object socketOrChannel, final int events)
    {
        return Optional.ofNullable(socketOrChannel)
                       .map(items::get)
                       .map(CompositePollItem::item)
                       .filter(pi -> pi.hasEvent(events))
                       .orElse(null);
    }
}
