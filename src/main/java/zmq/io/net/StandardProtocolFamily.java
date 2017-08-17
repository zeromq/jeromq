package zmq.io.net;

/**
 * Replacement of StandardProtocolFamily from SDK so it can be used in Android environments.
 */
public enum StandardProtocolFamily implements ProtocolFamily
{
    INET,
    INET6;
}
