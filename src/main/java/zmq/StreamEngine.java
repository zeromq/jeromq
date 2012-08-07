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
    private Socket s;

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
    
    private IOObject io_object;
    
    
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

        // io_object = new IOObject(); xxxx at plug call
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
    
    public void close() {
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            s = null;
        }
    }

    @Override
    public void activate_in ()
    {
        if (input_error) {
            //  There was an input error but the engine could not
            //  be terminated (due to the stalled decoder).
            //  Flush the pending message and terminate the engine now.
            decoder.process_buffer (inpos, 0, 0);
            assert (!decoder.stalled ());
            session.flush ();
            error ();
            return;
        }

        io_object.set_pollin (handle);

        //  Speculative read.
        io_object.in_event ();
    }

    private void error() {
        assert (session!=null);
        session.monitor_event (ZMQ.ZMQ_EVENT_DISCONNECTED, endpoint, s);
        session.detach ();
        unplug ();
        close();
    }

    private void unplug() {
        assert (plugged);
        plugged = false;

        //  Cancel all fd subscriptions.
        io_object.rm_fd (handle);

        //  Disconnect from I/O threads poller object.
        io_object.unplug ();

        //  Disconnect from session object.
        encoder.set_session (null);
        decoder.set_session (null);
        session = null;
    }

}
