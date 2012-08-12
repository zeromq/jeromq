package zmq;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class StreamEngine implements IEngine, IPollEvents {
    
    //final private IOObject io_object;
    //  Underlying socket.
    private SocketChannel s;

    private SelectableChannel handle;

    ByteBuffer inpos_buf;
    int insize;
    DecoderBase decoder;
    boolean input_error;

    ByteBuffer outpos_buf;
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
    
    
    public StreamEngine (SocketChannel fd_, final Options options_, final String endpoint_) {
        s = fd_;
        inpos_buf = null;
        insize = 0;
        input_error = false;
        outpos_buf = null;
        outsize = 0;
        session = null;
        options = options_;
        plugged = false;
        endpoint = endpoint_;

        try {
            if (options_.decoder != null) {
                Constructor<? extends DecoderBase> con = 
                        options_.decoder.getConstructor(Integer.class, Long.class);
                decoder = con.newInstance(Config.in_batch_size.getValue(), options_.maxmsgsize);
            } else {
                decoder = new Decoder(Config.in_batch_size.getValue(), options_.maxmsgsize);
            }
            encoder = new Encoder(Config.out_batch_size.getValue());
        } catch (SecurityException e) {
            throw new ZException.InstantiationException(e);
        } catch (NoSuchMethodException e) {
            throw new ZException.InstantiationException(e);
        } catch (InvocationTargetException e) {
            throw new ZException.InstantiationException(e);
        } catch (IllegalAccessException e) {
            throw new ZException.InstantiationException(e);
        } catch (InstantiationException e) {
            throw new ZException.InstantiationException(e);
        }
        
        //  Put the socket into non-blocking mode.
        try {
            Utils.unblock_socket (s);
            
            //  Set the socket buffer limits for the underlying socket.
            if (options.sndbuf != 0) {
                s.socket().setSendBufferSize(options.sndbuf);
            }
            if (options.rcvbuf != 0) {
                s.socket().setReceiveBufferSize(options.rcvbuf);
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
            decoder.process_buffer (inpos_buf);
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
    
    public void plug (IOThread io_thread_,
            SessionBase session_)
    {
        assert (!plugged);
        plugged = true;

        //  Connect to session object.
        assert (session == null);
        assert (session_ != null);
        encoder.set_session (session_);
        decoder.set_session (session_);
        session = session_;

        io_object = new IOObject(null);
        io_object.set_handler(this);
        //  Connect to I/O threads poller object.
        io_object.plug (io_thread_);
        handle = io_object.add_fd (s);
        io_object.set_pollin (handle);
        io_object.set_pollout (handle);
        //  Flush all the data that may have been already received downstream.
        in_event ();
        
    }

    @Override
    public void in_event() {
        boolean disconnection = false;

        //  If there's no data to process in the buffer...
        if (insize == 0) {

            //  Retrieve the buffer and read as much data as possible.
            //  Note that buffer can be arbitrarily large. However, we assume
            //  the underlying TCP layer has fixed buffer size and thus the
            //  number of bytes read will be always limited.
            inpos_buf = decoder.get_buffer ();
            insize = read (inpos_buf);

            //  Check whether the peer has closed the connection.
            if (insize == -1) {
                insize = 0;
                disconnection = true;
            }
        }

        //  Push the data to the decoder.
        inpos_buf.flip();
        int processed = decoder.process_buffer (inpos_buf);

        if (processed == -1) {
            disconnection = true;
        }
        else {

            //  Stop polling for input if we got stuck.
            if (processed < insize)
                io_object.reset_pollin (handle);

            //  Adjust the buffer.
            //inpos += processed;
            insize -= processed;
        }

        //  Flush all messages the decoder may have produced.
        session.flush ();

        //  Input error has occurred. If the last decoded
        //  message has already been accepted, we terminate
        //  the engine immediately. Otherwise, we stop
        //  waiting for input events and postpone the termination
        //  until after the session has accepted the message.
        if (disconnection) {
            input_error = true;
            if (decoder.stalled ())
                io_object.reset_pollin (handle);
            else
                error ();
        }

    }
    
    private int read (ByteBuffer buf) {
        int nbytes = 0 ;
        try {
            nbytes = s.read(buf);
        } catch (IOException e) {
            return -1;
        }
        
        return nbytes;
    }
    
    private int write (ByteBuffer buf) {
        int nbytes = 0 ;
        try {
            nbytes = s.write(buf);
        } catch (IOException e) {
            return -1;
        }
        
        return nbytes;
    }

    @Override
    public void out_event() {
        //  If write buffer is empty, try to read new data from the encoder.
        if (outsize == 0) {

            outpos_buf = encoder.get_data (null, null);
            outsize = outpos_buf.remaining();
            //  If there is no data to send, stop polling for output.
            if (outpos_buf.remaining() == 0) {
                io_object.reset_pollout (handle);
                return;
            }
        }

        //  If there are any data to write in write buffer, write as much as
        //  possible to the socket. Note that amount of data to write can be
        //  arbitratily large. However, we assume that underlying TCP layer has
        //  limited transmission buffer and thus the actual number of bytes
        //  written should be reasonably modest.
        int nbytes = write (outpos_buf);

        //  IO error has occurred. We stop waiting for output events.
        //  The engine is not terminated until we detect input error;
        //  this is necessary to prevent losing incomming messages.
        if (nbytes == -1) {
            io_object.reset_pollout (handle);
            return;
        }

        //outpos += nbytes;
        outsize -= nbytes;
        
    }

    @Override
    public void connect_event() {
        throw new UnsupportedOperationException();
        
    }

    @Override
    public void accept_event() {
        throw new UnsupportedOperationException();
        
    }

    @Override
    public void timer_event(int id_) {
        throw new UnsupportedOperationException();
        
    }

    @Override
    public void activate_out() {
        io_object.set_pollout (handle);

        //  Speculative write: The assumption is that at the moment new message
        //  was sent by the user the socket is probably available for writing.
        //  Thus we try to write the data to socket avoiding polling for POLLOUT.
        //  Consequently, the latency should be better in request/reply scenarios.
        out_event ();
        
    }

    @Override
    public void plug_decoder(DecoderBase decoder_) {
        if (decoder_ != null)
            decoder = decoder_;
    }


}
