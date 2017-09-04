package zmq.io.mechanism;

import java.nio.ByteBuffer;
import java.util.Arrays;

import zmq.Options;
import zmq.ZMQ;
import zmq.io.SessionBase;
import zmq.io.mechanism.curve.CurveClientMechanism;
import zmq.io.mechanism.curve.CurveServerMechanism;
import zmq.io.mechanism.gssapi.GssapiClientMechanism;
import zmq.io.mechanism.gssapi.GssapiServerMechanism;
import zmq.io.mechanism.plain.PlainClientMechanism;
import zmq.io.mechanism.plain.PlainServerMechanism;
import zmq.io.net.Address;

public enum Mechanisms
{
    NULL {
        @Override
        public Mechanism create(SessionBase session, Address peerAddress, Options options)
        {
            return new NullMechanism(session, peerAddress, options);
        }
    },
    PLAIN {
        @Override
        public Mechanism create(SessionBase session, Address peerAddress, Options options)
        {
            if (options.asServer) {
                return new PlainServerMechanism(session, peerAddress, options);
            }
            else {
                return new PlainClientMechanism(options);
            }
        }
    },
    CURVE {
        @Override
        public Mechanism create(SessionBase session, Address peerAddress, Options options)
        {
            if (options.asServer) {
                return new CurveServerMechanism(session, peerAddress, options);
            }
            else {
                return new CurveClientMechanism(options);
            }
        }
    },
    GSSAPI {
        @Override
        public Mechanism create(SessionBase session, Address peerAddress, Options options)
        {
            if (options.asServer) {
                return new GssapiServerMechanism(session, peerAddress, options);
            }
            else {
                return new GssapiClientMechanism(options);
            }
        }
    };

    public abstract Mechanism create(SessionBase session, Address peerAddress, Options options);

    public boolean isMechanism(ByteBuffer greetingRecv)
    {
        byte[] dst = new byte[20];
        greetingRecv.get(dst, 0, dst.length);

        byte[] name = name().getBytes(ZMQ.CHARSET);
        byte[] comp = Arrays.copyOf(name, 20);
        return Arrays.equals(dst, comp);
    }
}
