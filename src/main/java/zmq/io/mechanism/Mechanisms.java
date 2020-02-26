package zmq.io.mechanism;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
        public void check(Options options)
        {
            // Nothing to check
        }
        @Override
        public Mechanism create(SessionBase session, Address peerAddress, Options options)
        {
            return new NullMechanism(session, peerAddress, options);
        }
    },
    PLAIN {
        @Override
        public void check(Options options)
        {
            if (! options.asServer) {
                Set<String> errors = new HashSet<>(2);
                if (options.plainUsername == null
                    || options.plainUsername.length() >= 256) {
                    errors.add("user name invalid");
                }
                if (options.plainPassword == null
                    || options.plainPassword.length() >= 256) {
                    errors.add("password is invalid");
                }
                if (!errors.isEmpty()) {
                    throw new IllegalStateException("Plain mechanism definition incomplete: " + errors);
                }
            }
        }
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
        public void check(Options options)
        {
            Set<String> errors = new HashSet<>(3);
            if (options.curvePublicKey == null || options.curvePublicKey.length != Options.CURVE_KEYSIZE) {
                errors.add("public key is invalid");
            }
            if (options.curveSecretKey == null || options.curveSecretKey.length != Options.CURVE_KEYSIZE) {
                errors.add("secret key is invalid");
            }
            if (!options.asServer && (options.curveServerKey == null || options.curveServerKey.length != Options.CURVE_KEYSIZE)) {
                errors.add("not a server and no server public key given");
            }
            if (!errors.isEmpty()) {
                throw new IllegalStateException("Curve mechanism definition incomplete: " +  errors);
            }
        }
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
        public void check(Options options)
        {
            throw new UnsupportedOperationException("GSSAPI mechanism is not yet implemented");
        }
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

    public abstract void check(Options options);

    public boolean isMechanism(ByteBuffer greetingRecv)
    {
        byte[] dst = new byte[20];
        greetingRecv.get(dst, 0, dst.length);

        byte[] name = name().getBytes(ZMQ.CHARSET);
        byte[] comp = Arrays.copyOf(name, 20);
        return Arrays.equals(dst, comp);
    }
}
