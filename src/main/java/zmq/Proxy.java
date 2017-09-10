package zmq;

import java.nio.channels.Selector;
import java.util.Arrays;

import zmq.poll.PollItem;

class Proxy
{
    public static boolean proxy(SocketBase frontend, SocketBase backend, SocketBase capture)
    {
        return new Proxy().start(frontend, backend, capture, null);
    }

    public static boolean proxy(SocketBase frontend, SocketBase backend, SocketBase capture, SocketBase control)
    {
        return new Proxy().start(frontend, backend, capture, control);
    }

    public enum State
    {
        ACTIVE,
        PAUSED,
        TERMINATED
    }

    private State state;

    private Proxy()
    {
        state = State.ACTIVE;
    }

    private boolean start(SocketBase frontend, SocketBase backend, SocketBase capture, SocketBase control)
    {
        //  The algorithm below assumes ratio of requests and replies processed
        //  under full load to be 1:1.

        //  TODO: The current implementation drops messages when
        //  any of the pipes becomes full.

        int rc;
        int more;
        Msg msg;
        int count = control == null ? 2 : 3;

        PollItem[] items = new PollItem[count];

        items[0] = new PollItem(frontend, ZMQ.ZMQ_POLLIN);
        items[1] = new PollItem(backend, ZMQ.ZMQ_POLLIN);
        if (control != null) {
            items[2] = new PollItem(control, ZMQ.ZMQ_POLLIN);
        }
        PollItem[] itemsout = new PollItem[2];

        itemsout[0] = new PollItem(frontend, ZMQ.ZMQ_POLLOUT);
        itemsout[1] = new PollItem(backend, ZMQ.ZMQ_POLLOUT);

        Selector selector = frontend.getCtx().createSelector();

        try {
            while (state != State.TERMINATED) {
                //  Wait while there are either requests or replies to process.
                rc = ZMQ.poll(selector, items, -1);
                if (rc < 0) {
                    return false;
                }

                //  Get the pollout separately because when combining this with pollin it makes the CPU
                //  because pollout shall most of the time return directly.
                //  POLLOUT is only checked when frontend and backend sockets are not the same.
                if (frontend != backend) {
                    rc = ZMQ.poll(selector, itemsout, 0L);
                    if (rc < 0) {
                        return false;
                    }
                }

                //  Process a control command if any
                if (control != null && items[2].isReadable()) {
                    msg = control.recv(0);
                    if (msg == null) {
                        return false;
                    }
                    more = control.getSocketOpt(ZMQ.ZMQ_RCVMORE);

                    if (more < 0) {
                        return false;
                    }

                    //  Copy message to capture socket if any
                    boolean success = capture(capture, msg, more);
                    if (!success) {
                        return false;
                    }

                    byte[] command = msg.data();
                    if (Arrays.equals(command, ZMQ.PROXY_PAUSE)) {
                        state = State.PAUSED;
                    }
                    else if (Arrays.equals(command, ZMQ.PROXY_RESUME)) {
                        state = State.ACTIVE;
                    }
                    else if (Arrays.equals(command, ZMQ.PROXY_TERMINATE)) {
                        state = State.TERMINATED;
                    }
                    else {
                        //  This is an API error, we should assert
                        System.out
                                .printf("E: invalid command sent to proxy '%s'%n", new String(command, ZMQ.CHARSET));
                        assert false;
                    }
                }
                //  Process a request.
                if (process(items[0], itemsout[1], frontend, backend)) {
                    if (!forward(frontend, backend, capture)) {
                        return false;
                    }
                }
                //  Process a reply.
                if (process(items[1], itemsout[0], frontend, backend)) {
                    if (!forward(backend, frontend, capture)) {
                        return false;
                    }
                }
            }
        }
        finally {
            frontend.getCtx().closeSelector(selector);
        }

        return true;
    }

    private boolean process(PollItem read, PollItem write, SocketBase frontend, SocketBase backend)
    {
        return state == State.ACTIVE && read.isReadable() && (frontend == backend || write.isWritable());
    }

    private boolean forward(SocketBase from, SocketBase to, SocketBase capture)
    {
        int more;
        boolean success;
        while (true) {
            Msg msg = from.recv(0);
            if (msg == null) {
                return false;
            }
            more = from.getSocketOpt(ZMQ.ZMQ_RCVMORE);
            if (more < 0) {
                return false;
            }

            //  Copy message to capture socket if any
            success = capture(capture, msg, more);
            if (!success) {
                return false;
            }
            success = to.send(msg, more > 0 ? ZMQ.ZMQ_SNDMORE : 0);
            if (!success) {
                return false;
            }
            if (more == 0) {
                break;
            }
        }
        return true;
    }

    private boolean capture(SocketBase capture, Msg msg, int more)
    {
        if (capture != null) {
            Msg ctrl = new Msg(msg);
            boolean success = capture.send(ctrl, more > 0 ? ZMQ.ZMQ_SNDMORE : 0);
            if (!success) {
                return false;
            }
        }
        return true;
    }
}
