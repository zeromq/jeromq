package org.zeromq;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

/**
 * Rewritten poller for 0MQ.
 *
 * Polls selectable channels and sockets for specified events.
 *
 * This poller can be used in two ways:
 *
 * <p>
 * - the traditional one, where you make something like
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
</p></pre>
<p>
 * - the event-driven way
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
</p></pre>
<p>
 * The motivations of this rewriting are:
 * <br/>
 * - the bare poller used zmq.ZMQ#poll(zmq.poll.PollItem[], int, long). This method did not allow
 * to choose the selector used for polling, relying on a ThreadLocal, which is dangerous.
 * <br/>
 * - the bare poller use algorithms tailored for languages with manual allocation.
 * No need here as Java allows more flexibility. TODO There still may be a small penalty cost.
 *
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

    // contract for items. Useful for providing own implementation.
    public interface ItemHolder
    {
        // the inner ZMQ poll item
        zmq.poll.PollItem item();

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

        public ZPollItem(final Socket socket, final EventsHandler handler, int ops)
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
        public zmq.poll.PollItem item()
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
                if (other.handler() != null) {
                    return false;
                }
            }
            else if (!handler().equals(other.handler())) {
                return false;
            }
            return true;
        }

        @Override
        public EventsHandler handler()
        {
            return handler;
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
        this(new SimpleCreator(), context.createSelector());
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
        this(creator, context.createSelector());
    }

    /**
     * Creates a new poller.
     *
     * @param creator   the items creator
     * @param selector  the selector to use for polling.
     */
    public ZPoller(final ItemCreator creator, final Selector selector)
    {
        this.creator = creator;
        this.selector = selector;
        items = new HashMap<>();
        all = createContainer(0);
    }

    // creates a new poll item
    protected ItemHolder create(final Socket socket, final EventsHandler handler, final int events)
    {
        assert (socket != null);
        return creator.create(socket, handler, events);
    }

    // creates a new poll item
    protected ItemHolder create(final SelectableChannel channel, final EventsHandler handler, final int events)
    {
        assert (channel != null);
        return creator.create(channel, handler, events);
    }

    /**
     * Sets the global events handler for all registered sockets.
     *
     * @param globalHandler the events handler to set
     */
    public void setGlobalHandler(final EventsHandler globalHandler)
    {
        this.globalHandler = globalHandler;
    }

    /**
     * Returns the global events handler for all registered sockets.
     *
     * @return the global events handler for all registered sockets.
     */
    public EventsHandler getGlobalHandler()
    {
        return globalHandler;
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

    public final boolean register(final Socket socket, final EventsHandler handler)
    {
        return register(socket, handler, POLLIN | POLLOUT | POLLERR);
    }

    public final boolean register(final Socket socket, final int events)
    {
        return register(socket, globalHandler, events);
    }

    /**
     * Register a SelectableChannel for polling on specified events.
     *
     * @param channel   the registering channel.
     * @param handler   the events handler for this socket
     * @param events    the events to listen to, as a mask composed by XORing POLLIN, POLLOUT and POLLERR.
     * @return true if registered, otherwise false
     */
    public final boolean register(final SelectableChannel channel, final EventsHandler handler, final int events)
    {
        if (channel == null) {
            return false;
        }
        return add(channel, create(channel, handler, events));
    }

    public final boolean register(final SelectableChannel channel, final EventsHandler handler)
    {
        return register(channel, handler, IN | OUT | ERR);
    }

    public final boolean register(final SelectableChannel channel, final int events)
    {
        return register(channel, globalHandler, events);
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
    public final boolean unregister(final Object socketOrChannel)
    {
        if (socketOrChannel == null) {
            return false;
        }
        Set<ItemHolder> items = this.items.remove(socketOrChannel);
        boolean rc = items != null;
        if (rc) {
            all.removeAll(items);
        }
        return rc;
    }

    /******************************************************************************/
    /* POLLING | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | |*/
    /******************************************************************************/
    /**
     * Issue a poll call, using the specified timeout value.
     * <p>
     * Since ZeroMQ 3.0, the timeout parameter is in <i>milliseconds<i>,
     * but prior to this the unit was <i>microseconds</i>.
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
     *
     * @param timeout           the timeout, as per zmq_poll ();
     * @param dispatchEvents    true to dispatch events using items handler and the global one.
     * @see "http://api.zeromq.org/3-0:zmq-poll"
     * @return how many objects where signaled by poll ()
     */
    protected int poll(final long timeout, final boolean dispatchEvents)
    {
        // get all the raw items
        final Collection<ItemHolder> all = items();
        final Set<zmq.poll.PollItem> pollItems = new HashSet<>(all.size());
        for (ItemHolder holder : all) {
            pollItems.add(holder.item());
        }
        // polling time
        final int rc = poll(selector, timeout, pollItems);

        if (!dispatchEvents) {
            // raw result
            return rc;
        }

        if (dispatch(all, pollItems.size())) {
            // returns event counts after dispatch if everything went fine
            return rc;
        }
        // error in dispatching
        return -1;
    }

    // does the effective polling

    protected int poll(final Selector selector, final long tout, final Collection<zmq.poll.PollItem> items)
    {
        final int size = items.size();
        return zmq.ZMQ.poll(selector, items.toArray(new zmq.poll.PollItem[size]), size, tout);
    }

    /**
     * Dispatches the polled events.
     *
     * @param all   the items used for dispatching
     * @param size  the number of items to dispatch
     * @return true if correctly dispatched, false in case of error
     */
    protected boolean dispatch(final Collection<ItemHolder> all, int size)
    {
        ItemHolder[] array = all.toArray(new ItemHolder[all.size()]);
        // protected against handlers unregistering during this loop
        for (ItemHolder holder : array) {
            EventsHandler handler = holder.handler();
            if (handler == null) {
                handler = globalHandler;
            }
            if (handler == null) {
                // no handler, short-circuit
                continue;
            }
            final zmq.poll.PollItem item = holder.item();
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
            if (channel != null) {
                // dispatch on channel
                assert (socket == null);
                if (!handler.events(channel, events)) {
                    return false;
                }
            }
        }
        return true;
    }

    // dispatches all the polled events to their respective handlers
    public boolean dispatch()
    {
        return dispatch(all, all.size());
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
        final zmq.poll.PollItem it = filter(socketOrChannel, READABLE);
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
        final zmq.poll.PollItem it = filter(socketOrChannel, WRITABLE);
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
        final zmq.poll.PollItem it = filter(socketOrChannel, ERR);
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
    public void close() throws IOException
    {
        destroy();
    }

    /**
     * Destroys the poller and the associated selector without exception.
     */
    public void destroy()
    {
        // we didn't create the selector,
        // it is not our responsibility to close it.
    }

    // selector used for polling
    private final Selector selector;

    // creator of items
    private final ItemCreator creator;
    // managed items
    private final Map<Object, Set<ItemHolder>> items;
    // all managed items to avoid penalty cost when dispatching
    private final Set<ItemHolder> all;

    // TODO set of handlers, each with its specified events?
    // optional global events handler
    private EventsHandler globalHandler;

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
    protected boolean add(Object socketOrChannel, final ItemHolder holder)
    {
        if (socketOrChannel == null) {
            Socket socket = holder.socket();
            SelectableChannel ch = holder.item().getRawSocket();
            if (ch == null) {
                // not a channel
                assert (socket != null);
                socketOrChannel = socket;
            }
            else if (socket == null) {
                // not a socket
                assert (ch != null);
                socketOrChannel = ch;
            }
        }
        assert (socketOrChannel != null);

        Set<ItemHolder> holders = items.get(socketOrChannel);
        if (holders == null) {
            holders = createContainer(1);
            items.put(socketOrChannel, holders);
        }
        final boolean rc = holders.add(holder);
        if (rc) {
            all.add(holder);
        }
        return rc;
    }

    // create the container of holders
    protected Set<ItemHolder> createContainer(int size)
    {
        return new HashSet<>(size);
    }

    // gets all the items of this poller
    protected Collection<ItemHolder> items()
    {
        return all;
    }

    // gets all the items of this poller regarding the given input
    protected Iterable<ItemHolder> items(final Object socketOrChannel)
    {
        final Set<ItemHolder> set = items.get(socketOrChannel);
        if (set == null) {
            return Collections.emptySet();
        }
        return set;
    }

    // filters items to get the first one matching the criteria, or null if none found
    protected zmq.poll.PollItem filter(final Object socketOrChannel, int events)
    {
        if (socketOrChannel == null) {
            return null;
        }

        final Iterable<ItemHolder> items = items(socketOrChannel);
        for (ItemHolder item : items) {
            final zmq.poll.PollItem it = item.item();
            if (it.hasEvent(events)) {
                return it;
            }
        }
        return null;
    }
}
