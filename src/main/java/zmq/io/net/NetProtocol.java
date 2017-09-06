package zmq.io.net;

import java.util.Arrays;
import java.util.List;

import zmq.socket.Sockets;

public enum NetProtocol
{
    inproc(true),
    ipc(true),
    tcp(true),
    pgm(false, Sockets.PUB, Sockets.SUB, Sockets.XPUB, Sockets.XPUB),
    epgm(false, Sockets.PUB, Sockets.SUB, Sockets.XPUB, Sockets.XPUB),
    tipc(false),
    norm(false);

    public final boolean  valid;
    private List<Sockets> compatibles;

    NetProtocol(boolean implemented, Sockets... compatibles)
    {
        valid = implemented;
        this.compatibles = Arrays.asList(compatibles);
    }

    public static NetProtocol getProtocol(String protocol)
    {
        for (NetProtocol candidate : values()) {
            if (candidate.name().equals(protocol)) {
                return candidate;
            }
        }
        return null;
    }

    public final boolean compatible(int type)
    {
        return compatibles.isEmpty() || compatibles.contains(Sockets.fromType(type));
    }
}
