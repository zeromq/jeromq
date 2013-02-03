package org.jeromq.api;

import zmq.PollItem;
import zmq.ZError;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class Poller {
    private static final ThreadLocal<Reusable> holder = new ThreadLocal<Reusable>();

    private final Map<Socket, PollItem> items = new HashMap<Socket, PollItem>();
    private Selector selector;

    /**
     * Poller with default initial size.
     */
    protected Poller() {
        open();
    }

    /**
     * Register a Socket for polling on all events.
     *
     * @param socket the Socket we are registering.
     */
    public void register(Socket socket) {
        register(socket, PollOption.POLL_IN, PollOption.POLL_OUT, PollOption.POLL_ERR);
    }

    /**
     * Register a Socket for polling on the specified events.
     */
    public void register(Socket socket, EnumSet<PollOption> options) {
        items.put(socket, new PollItem(socket.getDelegate().base(), PollOption.getCValue(options)));
    }

    /**
     * Register a Socket for polling on the specified events.
     */
    public void register(Socket socket, PollOption option1, PollOption... additionalOptions) {
        register(socket, EnumSet.<PollOption>of(option1, additionalOptions));
    }

    private void open() {
        if (holder.get() == null) {
            synchronized (holder) {
                try {
                    if (selector == null) {
                        Reusable s = new Reusable();
                        selector = s.open();
                        holder.set(s);
                    }
                } catch (IOException e) {
                    throw new ZError.IOException(e);
                }
            }
        }
        selector = holder.get().get();
    }

    /**
     * Unregister a Socket for polling on the specified events.
     *
     * @param socket the Socket to be unregistered
     */
    public void unregister(Socket socket) {
        items.remove(socket);
    }

    /**
     * Get the current poll set size.
     *
     * @return the current poll set size.
     */
    public int getSize() {
        return items.size();
    }

    /**
     * Issue a poll call, blocking indefinitely.
     *
     * @return how many objects where signaled by poll ().
     */
    public int poll() {
        return poll(-1L);
    }

    /**
     * Issue a poll call, using the specified timeout value.
     * <p/>
     * Since ZeroMQ 3.0, the timeout parameter is in <i>milliseconds<i>,
     * but prior to this the unit was <i>microseconds</i>.
     *
     * @param timeoutMillis the timeout, as per zmq_poll ();
     *                      if -1, it will block indefinitely until an event
     *                      happens; if 0, it will return immediately;
     *                      otherwise, it will wait for at most that many
     *                      milliseconds/microseconds (see above).
     * @return how many objects where signaled by poll ()
     * @see "http://api.zeromq.org/3-0:zmq-poll"
     */
    public int poll(long timeoutMillis) {
        return zmq.ZMQ.zmq_poll(selector, items.values().toArray(new PollItem[items.size()]), timeoutMillis);
    }

    /**
     * Check whether the specified element in the poll set was signaled for input.
     */
    public boolean signaledForInput(Socket socket) {
        return items.get(socket).isReadable();
    }

    /**
     * Check whether the specified element in the poll set was signaled for output.
     *
     * @return true if the element was signaled.
     */
    public boolean signaledForOutput(Socket socket) {
        return items.get(socket).isWriteable();
    }

    /**
     * Check whether the specified element in the poll set was signaled for error.
     *
     * @return true if the element was signaled.
     */
    public boolean signaledForError(Socket socket) {
        return items.get(socket).isError();
    }

    // GC closes selector handle
    protected static class Reusable {

        private Selector selector;

        protected Reusable() {
            selector = null;
        }

        public Selector open() throws IOException {
            selector = Selector.open();
            return selector;
        }

        public Selector get() {
            assert (selector != null);
            assert (selector.isOpen());
            return selector;
        }

        @Override
        public void finalize() {
            try {
                selector.close();
            } catch (IOException e) {
            }
            try {
                super.finalize();
            } catch (Throwable e) {
            }
        }

    }

}
