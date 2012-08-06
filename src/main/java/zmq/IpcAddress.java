package zmq;

import java.net.SocketAddress;

public class IpcAddress implements Address.IZAddress {

    @Override
    public String toString(String addr_) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resolve(String name_, boolean local_, boolean ip4only_) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress address() {
        throw new UnsupportedOperationException();
    }

}
