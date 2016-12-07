package zmq;

import java.io.IOException;
import java.nio.channels.Selector;

public class Proxy
{
    private Proxy()
    {
    }

    public static boolean proxy(SocketBase frontend,
            SocketBase backend, SocketBase capture)
    {
        //  The algorithm below assumes ratio of requests and replies processed
        //  under full load to be 1:1.

        //  TODO: The current implementation drops messages when
        //  any of the pipes becomes full.

        boolean success = true;
        int rc;
        long more;
        Msg msg;
        PollItem[] items = new PollItem[2];

        items[0] = new PollItem(frontend, ZMQ.ZMQ_POLLIN);
        items[1] = new PollItem(backend, ZMQ.ZMQ_POLLIN);

        Selector selector;
        try {
            selector = Selector.open();
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }

        try {
            while (!Thread.currentThread().isInterrupted()) {
                //  Wait while there are either requests or replies to process.
                rc = ZMQ.poll(selector, items, -1);
                if (rc < 0) {
                    return false;
                }

                //  Process a request.
                if (items[0].isReadable()) {
                    while (true) {
                        msg = frontend.recv(0);
                        if (msg == null) {
                            return false;
                        }

                        more = frontend.getSocketOpt(ZMQ.ZMQ_RCVMORE);

                        if (more < 0) {
                            return false;
                        }

                        //  Copy message to capture socket if any
                        if (capture != null) {
                            Msg ctrl = new Msg(msg);
                            success = capture.send(ctrl, more > 0 ? ZMQ.ZMQ_SNDMORE : 0);
                            if (!success) {
                                return false;
                            }
                        }

                        success = backend.send(msg, more > 0 ? ZMQ.ZMQ_SNDMORE : 0);
                        if (!success) {
                            return false;
                        }
                        if (more == 0) {
                            break;
                        }
                    }
                }
                //  Process a reply.
                if (items[1].isReadable()) {
                    while (true) {
                        msg = backend.recv(0);
                        if (msg == null) {
                            return false;
                        }

                        more = backend.getSocketOpt(ZMQ.ZMQ_RCVMORE);

                        if (more < 0) {
                            return false;
                        }

                        //  Copy message to capture socket if any
                        if (capture != null) {
                            Msg ctrl = new Msg(msg);
                            success = capture.send(ctrl, more > 0 ? ZMQ.ZMQ_SNDMORE : 0);
                            if (!success) {
                                return false;
                            }
                        }

                        success = frontend.send(msg, more > 0 ? ZMQ.ZMQ_SNDMORE : 0);
                        if (!success) {
                            return false;
                        }
                        if (more == 0) {
                            break;
                        }
                    }
                }
            }
        }
        finally {
            try {
                selector.close();
            }
            catch (Exception e) {
            }
        }

        return true;
    }
}
