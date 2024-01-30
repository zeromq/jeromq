package zmq.socket;

import java.util.Arrays;
import java.util.List;

import zmq.Ctx;
import zmq.Options;
import zmq.SocketBase;
import zmq.io.HelloMsgSession;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.socket.pipeline.Pull;
import zmq.socket.pipeline.Push;
import zmq.socket.pubsub.Pub;
import zmq.socket.pubsub.Sub;
import zmq.socket.pubsub.XPub;
import zmq.socket.pubsub.XSub;
import zmq.socket.radiodish.Dish;
import zmq.socket.radiodish.Radio;
import zmq.socket.reqrep.Dealer;
import zmq.socket.reqrep.Rep;
import zmq.socket.reqrep.Req;
import zmq.socket.reqrep.Router;
import zmq.socket.clientserver.Server;
import zmq.socket.clientserver.Client;
import zmq.socket.scattergather.Gather;
import zmq.socket.scattergather.Scatter;

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
    },
    SERVER("CLIENT") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Server(parent, tid, sid);
        }
    },
    CLIENT("SERVER") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Client(parent, tid, sid);
        }
    },
    RADIO("DISH") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Radio(parent, tid, sid);
        }

        @Override
        public SessionBase create(IOThread ioThread, boolean connect, SocketBase socket, Options options, Address addr)
        {
            return new Radio.RadioSession(ioThread, connect, socket, options, addr);
        }
    },
    DISH("RADIO") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Dish(parent, tid, sid);
        }

        @Override
        public SessionBase create(IOThread ioThread, boolean connect, SocketBase socket, Options options, Address addr)
        {
            return new Dish.DishSession(ioThread, connect, socket, options, addr);
        }
    },
    CHANNEL("CHANNEL") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Channel(parent, tid, sid);
        }
    },
    PEER("PEER") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Peer(parent, tid, sid);
        }
    },
    RAW {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Raw(parent, tid, sid);
        }
    },
    SCATTER("GATHER") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Scatter(parent, tid, sid);
        }
    },
    GATHER("SCATTER") {
        @Override
        SocketBase create(Ctx parent, int tid, int sid)
        {
            return new Gather(parent, tid, sid);
        }
    };

    private static final Sockets[] VALUES = values();

    private final List<String> compatible;

    Sockets(String... compatible)
    {
        this.compatible = Arrays.asList(compatible);
    }

    //  Create a socket of a specified type.
    abstract SocketBase create(Ctx parent, int tid, int sid);

    public SessionBase create(IOThread ioThread, boolean connect, SocketBase socket, Options options, Address addr)
    {
        if (options.canSendHelloMsg && options.helloMsg != null) {
            return new HelloMsgSession(ioThread, connect, socket, options, addr);
        }
        else {
            return new SessionBase(ioThread, connect, socket, options, addr);
        }
    }

    public static SessionBase createSession(IOThread ioThread, boolean connect, SocketBase socket, Options options,
                                            Address addr)
    {
        return VALUES[options.type].create(ioThread, connect, socket, options, addr);
    }

    public static SocketBase create(int socketType, Ctx parent, int tid, int sid)
    {
        return VALUES[socketType].create(parent, tid, sid);
    }

    public static String name(int socketType)
    {
        return VALUES[socketType].name();
    }

    public static Sockets fromType(int socketType)
    {
        return VALUES[socketType];
    }

    public static boolean compatible(int self, String peer)
    {
        return VALUES[self].compatible.contains(peer);
    }
}
