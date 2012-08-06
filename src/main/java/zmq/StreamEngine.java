package zmq;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class StreamEngine implements IEngine {
    
    //final private IOObject io_object;
    //  Underlying socket.
    final private Socket s;

    private SelectableChannel handle;

    byte[] inpos;
    int insize;
    final Decoder decoder;
    boolean input_error;

    byte[] outpos;
    int outsize;
    final Encoder encoder;

    //  The session this engine is attached to.
    SessionBase session;

    //  Detached transient session.
    SessionBase leftover_session;

    Options options;

    // String representation of endpoint
    String endpoint;

    boolean plugged;
    
    
    public StreamEngine (Socket fd_, final Options options_, final String endpoint_) {
        s = fd_;
        inpos = null;
        insize = 0;
        decoder = new Decoder(Config.in_batch_size.getValue(), options_.maxmsgsize);
        input_error = false;
        outpos = null;
        outsize = 0;
        encoder = new Encoder(Config.out_batch_size.getValue());
        session = null;
        options = options_;
        plugged = false;
        endpoint = endpoint_;

        
        //  Put the socket into non-blocking mode.
        try {
            Utils.unblock_socket (s.getChannel());
            
            //  Set the socket buffer limits for the underlying socket.
            if (options.sndbuf != 0) {
                s.setSendBufferSize(options.sndbuf);
            }
            if (options.rcvbuf != 0) {
                s.setReceiveBufferSize(options.rcvbuf);
            }

        } catch (IOException e) {
            throw new ZException.IOException(e);
        }

    }
}
