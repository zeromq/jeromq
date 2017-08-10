package zmq.io.net;

/**
 * Replacement of ProtocolFamily from SDK so it can be used in Android environments.
 */
public interface ProtocolFamily
{
    String name();
}
