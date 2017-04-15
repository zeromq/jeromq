package zmq.socket;

import java.util.Arrays;
import java.util.List;

import zmq.Ctx;
import zmq.Options;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.socket.pipeline.Pull;
import zmq.socket.pipeline.Push;
import zmq.socket.pubsub.Pub;
import zmq.socket.pubsub.Sub;
import zmq.socket.pubsub.XPub;
import zmq.socket.pubsub.XSub;
import zmq.socket.reqrep.Dealer;
import zmq.socket.reqrep.Rep;
import zmq.socket.reqrep.Req;
import zmq.socket.reqrep.Router;

public enum Sockets
{
    PAIR("PAIR") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Pair(parent, tid, sid);
        }
    },
    PUB("SUB", "XSUB") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Pub(parent, tid, sid);
        }
    },
    SUB("PUB", "XPUB") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Sub(parent, tid, sid);
        }
    },
    REQ("REP", "ROUTER") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Req(parent, tid, sid);
        }

        @Override
        public SessionBase create(IOThread ioThread, boolean connect, SocketBase socket, Options options, Address addr)
        {
            return new Req.ReqSession(ioThread, connect, socket, options, addr);
        }
    },
    REP("REQ", "DEALER") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Rep(parent, tid, sid);
        }
    },
    DEALER("REP", "DEALER", "ROUTER") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Dealer(parent, tid, sid);
        }
    },
    ROUTER("REQ", "DEALER", "ROUTER") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Router(parent, tid, sid);
        }
    },
    PULL("PUSH") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Pull(parent, tid, sid);
        }
    },
    PUSH("PULL") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Push(parent, tid, sid);
        }
    },
    XPUB("SUB", "XSUB") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new XPub(parent, tid, sid);
        }
    },
    XSUB("PUB", "XPUB") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new XSub(parent, tid, sid);
        }
    },
    STREAM {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Stream(parent, tid, sid);
        }
    };

    private final List<String> compatible;

    Sockets(String... compatible)
    {
        this.compatible = Arrays.asList(compatible);
    }

    //  Create a socket of a specified type.
    abstract SocketBase create(Ctx parent, int tid, int sid);

    public SessionBase create(IOThread ioThread, boolean connect, SocketBase socket, Options options, Address addr)
    {
        return new SessionBase(ioThread, connect, socket, options, addr);
    }

    public static SessionBase createSession(IOThread ioThread, boolean connect, SocketBase socket, Options options,
                                            Address addr)
    {
        return values()[options.type].create(ioThread, connect, socket, options, addr);
    }

    public static SocketBase create(int socketType, Ctx parent, int tid, int sid)
    {
        return values()[socketType].create(parent, tid, sid);
    }

    public static String name(int socketType)
    {
        return values()[socketType].name();
    }

    public static Sockets fromType(int socketType)
    {
        return values()[socketType];
    }

    public static boolean compatible(int self, String peer)
    {
        return values()[self].compatible.contains(peer);
    }
}
