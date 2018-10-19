package zmq.io.net;

import java.nio.channels.spi.SelectorProvider;

import zmq.Options;

/**
 * By implementing this class, it's possible to change the kind of channel used in tcp connections.<p>
 * It allows to easily wrap ZMQ socket in custom socket for TLS protection or other kind of trick.
 *
 * @author Fabrice Bacchella
 *
 */
public interface SelectorProviderChooser
{
    public SelectorProvider choose(Address.IZAddress addr, Options options);
}
