/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2009 iMatix Corporation
    Copyright (c) 2007-2011 Other contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package zmq;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

public class StreamEngine implements IEngine, IPollEvents, IMsgSink {
    //  Size of the greeting message:
    //  Preamble (10 bytes) + version (1 byte) + socket type (1 byte).
    private static final int GREETING_SIZE = 12;
    
    //  True iff we are registered with an I/O poller.
    private boolean io_enabled;
    
    //final private IOObject io_object;
    private SocketChannel handle;

    private ByteBuffer inbuf;
    private int insize;
    private DecoderBase decoder;

    private Transfer outbuf;
    private int outsize;
    private EncoderBase encoder;

    //  When true, we are still trying to determine whether
    //  the peer is using versioned protocol, and if so, which
    //  version.  When false, normal message flow has started.
    private boolean handshaking;
    
    //  The receive buffer holding the greeting message
    //  that we are receiving from the peer.
    private final ByteBuffer greeting;

    //  The send buffer holding the greeting message
    //  that we are sending to the peer.
    private final ByteBuffer greeting_output_buffer;
    
    //  The session this engine is attached to.
    private SessionBase session;

    //  Detached transient session.
    //private SessionBase leftover_session;

    private Options options;

    // String representation of endpoint
    private String endpoint;

    private boolean plugged;
    private boolean terminating;
    
    // Socket
    private SocketBase socket;
    
    private IOObject io_object;
    
    
    public StreamEngine (SocketChannel fd_, final Options options_, final String endpoint_) 
    {
        handle = fd_;
        inbuf = null;
        insize = 0;
        io_enabled = false;
        outbuf = null;
        outsize = 0;
        handshaking = true;
        session = null;
        options = options_;
        plugged = false;
        terminating = false;
        endpoint = endpoint_;
        socket = null;
        greeting = ByteBuffer.allocate(GREETING_SIZE).order(ByteOrder.BIG_ENDIAN);
        greeting_output_buffer = ByteBuffer.allocate(GREETING_SIZE).order(ByteOrder.BIG_ENDIAN);
        encoder = null;
        decoder = null;

        //  Put the socket into non-blocking mode.
        try {
            Utils.unblock_socket (handle);
            
            //  Set the socket buffer limits for the underlying socket.
            if (options.sndbuf != 0) {
                handle.socket().setSendBufferSize((int)options.sndbuf);
            }
            if (options.rcvbuf != 0) {
                handle.socket().setReceiveBufferSize((int)options.rcvbuf);
            }

        } catch (IOException e) {
            throw new ZError.IOException(e);
        }

    }
    
    private DecoderBase new_decoder (int size, long max, SessionBase session, int version) {
        
        if (options.decoder == null) {
            if (version == V1Protocol.VERSION)
                return new V1Decoder (size, max, session);
            return new Decoder (size, max);
        }
        
        try {
            Constructor<? extends DecoderBase> dcon;
            
            if (version == 0)  {
                dcon = options.decoder.getConstructor (int.class, long.class);
                return dcon.newInstance (size, max);
            } else {
                dcon = options.decoder.getConstructor (int.class, long.class, IMsgSink.class, int.class);
                return dcon.newInstance (size, max, session, version);
            }
        } catch (SecurityException e) {
            throw new ZError.InstantiationException (e);
        } catch (NoSuchMethodException e) {
            throw new ZError.InstantiationException (e);
        } catch (InvocationTargetException e) {
            throw new ZError.InstantiationException (e);
        } catch (IllegalAccessException e) {
            throw new ZError.InstantiationException (e);
        } catch (InstantiationException e) {
            throw new ZError.InstantiationException (e);
        }
    }
    
    private EncoderBase new_encoder (int size, SessionBase session, int version) {
        
        if (options.encoder == null) {
            if (version == V1Protocol.VERSION)
                return new V1Encoder (size, session);
            return new Encoder (size);
        }
        
        try {
            Constructor<? extends EncoderBase> econ;
            
            if (version == 0) {
                econ = options.encoder.getConstructor (int.class);
                return econ.newInstance (size);
            } else {
                econ = options.encoder.getConstructor (int.class, IMsgSource.class, int.class);
                return econ.newInstance (size, session, version);
            }
        } catch (SecurityException e) {
            throw new ZError.InstantiationException (e);
        } catch (NoSuchMethodException e) {
            throw new ZError.InstantiationException (e);
        } catch (InvocationTargetException e) {
            throw new ZError.InstantiationException (e);
        } catch (IllegalAccessException e) {
            throw new ZError.InstantiationException (e);
        } catch (InstantiationException e) {
            throw new ZError.InstantiationException (e);
        }
    }
    
    
    
    public void destroy () 
    {
        
        assert (!plugged);
        
        if (handle != null) {
            try {
                handle.close();
            } catch (IOException e) {
            }
            handle = null;
        }
    }

    public void plug (IOThread io_thread_,
            SessionBase session_)
    {
        assert (!plugged);
        plugged = true;
        
        //  Connect to session object.
        assert (session == null);
        assert (session_ != null);
        session = session_;
        socket = session.get_soket ();

        io_object = new IOObject(null);
        io_object.set_handler(this);
        //  Connect to I/O threads poller object.
        io_object.plug (io_thread_);
        io_object.add_fd (handle);
        io_enabled = true;
        
        //  Send the 'length' and 'flags' fields of the identity message.
        //  The 'length' field is encoded in the long format.
        greeting_output_buffer.put ((byte) 0xff);
        greeting_output_buffer.putLong (options.identity_size + 1);
        greeting_output_buffer.put ((byte) 0x7f);

        io_object.set_pollin (handle);
        //  When there's a raw custom encoder, we don't send 10 bytes frame
        boolean custom = false;
        try {
            custom = options.encoder != null && options.encoder.getDeclaredField ("RAW_ENCODER") != null;
        } catch (SecurityException e) {
        } catch (NoSuchFieldException e) {
        }
        
        if (!custom) {
            outsize = greeting_output_buffer.position ();
            greeting_output_buffer.flip();
            outbuf = new Transfer.ByteBufferTransfer (greeting_output_buffer);
            io_object.set_pollout (handle);
        }        
        
        //  Flush all the data that may have been already received downstream.
        in_event ();
    }
    
    private void unplug () {
        assert (plugged);
        plugged = false;

        //  Cancel all fd subscriptions.
        if (io_enabled) {
            io_object.rm_fd (handle);
            io_enabled = false;
        }

        //  Disconnect from I/O threads poller object.
        io_object.unplug ();

        //  Disconnect from session object.
        if (encoder != null)
            encoder.set_msg_source (null);
        if (decoder != null)
            decoder.set_msg_sink (null);
        session = null;
    }
    
    @Override
    public void terminate () 
    {
        if (!terminating && encoder != null && encoder.has_data ())
        {
            terminating = true;
            return;
        }
        unplug ();
        destroy ();
    }

    @Override
    public void in_event () 
    {
        
        //  If still handshaking, receive and process the greeting message.
        if (handshaking)
            if (!handshake ())
                return;
        
        assert (decoder != null);
        boolean disconnection = false;

        //  If there's no data to process in the buffer...
        if (insize == 0) {

            //  Retrieve the buffer and read as much data as possible.
            //  Note that buffer can be arbitrarily large. However, we assume
            //  the underlying TCP layer has fixed buffer size and thus the
            //  number of bytes read will be always limited.
            inbuf = decoder.get_buffer ();
            insize = read (inbuf);
            inbuf.flip();

            //  Check whether the peer has closed the connection.
            if (insize == -1) {
                insize = 0;
                disconnection = true;
            }
        }

        //  Push the data to the decoder.
        int processed = decoder.process_buffer (inbuf, insize);

        if (processed == -1) {
            disconnection = true;
        }
        else {

            //  Stop polling for input if we got stuck.
            if (processed < insize)
                io_object.reset_pollin (handle);

            //  Adjust the buffer.
            insize -= processed;
        }

        //  Flush all messages the decoder may have produced.
        session.flush ();

        //  An input error has occurred. If the last decoded message
        //  has already been accepted, we terminate the engine immediately.
        //  Otherwise, we stop waiting for socket events and postpone
        //  the termination until after the message is accepted.
        if (disconnection) {
            if (decoder.stalled ()) {
                io_object.rm_fd (handle);
                io_enabled = false;
            } else
                error ();
        }

    }
    
    @Override
    public void out_event () 
    {
        //  If write buffer is empty, try to read new data from the encoder.
        if (outsize == 0) {

            //  Even when we stop polling as soon as there is no
            //  data to send, the poller may invoke out_event one
            //  more time due to 'speculative write' optimisation.
            if (encoder == null) {
                 assert (handshaking);
                 return;
            }
            
            outbuf = encoder.get_data (null);
            outsize = outbuf.remaining();
            //  If there is no data to send, stop polling for output.
            if (outbuf.remaining() == 0) {
                io_object.reset_pollout (handle);
                
                // when we use custom encoder, we might want to close
                if (encoder.is_error()) {
                    error();
                }

                return;
            }
        }

        //  If there are any data to write in write buffer, write as much as
        //  possible to the socket. Note that amount of data to write can be
        //  arbitratily large. However, we assume that underlying TCP layer has
        //  limited transmission buffer and thus the actual number of bytes
        //  written should be reasonably modest.
        int nbytes = write (outbuf);

        //  IO error has occurred. We stop waiting for output events.
        //  The engine is not terminated until we detect input error;
        //  this is necessary to prevent losing incomming messages.
        if (nbytes == -1) {
            io_object.reset_pollout (handle);

            if (terminating)
                terminate ();

            return;
        }

        outsize -= nbytes;

        //  If we are still handshaking and there are no data
        //  to send, stop polling for output.
        if (handshaking)
            if (outsize == 0)
                io_object.reset_pollout (handle);
        
        // when we use custom encoder, we might want to close after sending a response
        if (outsize == 0) {
            if (encoder != null && encoder.is_error ()) {
                error();
                return;
            }
            if (terminating)
                terminate ();
        }

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
    public void activate_in ()
    {
        if (!io_enabled) {
            //  There was an input error but the engine could not
            //  be terminated (due to the stalled decoder).
            //  Flush the pending message and terminate the engine now.
            decoder.process_buffer (inbuf, 0);
            assert (!decoder.stalled ());
            session.flush ();
            error ();
            return;
        }

        io_object.set_pollin (handle);

        //  Speculative read.
        io_object.in_event ();
    }
    
    private boolean handshake ()
    {
        assert (handshaking);

        //  Receive the greeting.
        while (greeting.position () < GREETING_SIZE) {
            final int n = read (greeting);
            if (n == -1) {
                error ();
                return false;
            }

            if (n == 0)
                return false;

            //  We have received at least one byte from the peer.
            //  If the first byte is not 0xff, we know that the
            //  peer is using unversioned protocol.
            if ((greeting.get (0) & 0xff) != 0xff)
                break;

            if (greeting.position () < 10)
                continue;
            //  Inspect the right-most bit of the 10th byte (which coincides
            //  with the 'flags' field if a regular message was sent).
            //  Zero indicates this is a header of identity message
            //  (i.e. the peer is using the unversioned protocol).
            if ((greeting.get (9) & 0x01) == 0)
                break;

            //  The peer is using versioned protocol.
            //  Send the rest of the greeting, if necessary.
            if (greeting_output_buffer.limit () < GREETING_SIZE) {
                if (outsize == 0)
                    io_object.set_pollout (handle);
                int pos = greeting_output_buffer.position ();
                greeting_output_buffer.position (10).limit (GREETING_SIZE);
                greeting_output_buffer.put ((byte) 1); // Protocol version
                greeting_output_buffer.put ((byte) options.type);  // Socket type
                greeting_output_buffer.position (pos);
                outsize += 2;
            }
        }

        //  Position of the version field in the greeting.
        final int version_pos = 10;

        //  Is the peer using the unversioned protocol?
        //  If so, we send and receive rests of identity
        //  messages.
        if ((greeting.get (0) & 0xff) != 0xff || (greeting.get (9) & 0x01) == 0) {
            encoder = new_encoder (Config.OUT_BATCH_SIZE.getValue (), null, 0);
            encoder.set_msg_source (session);

            decoder = new_decoder (Config.IN_BATCH_SIZE.getValue (), options.maxmsgsize, null, 0);
            decoder.set_msg_sink (session);

            //  We have already sent the message header.
            //  Since there is no way to tell the encoder to
            //  skip the message header, we simply throw that
            //  header data away.
            final int header_size = options.identity_size + 1 >= 255 ? 10 : 2;
            ByteBuffer tmp = ByteBuffer.allocate (header_size);
            encoder.get_data (tmp);
            assert (tmp.remaining () == header_size);
            
            //  Make sure the decoder sees the data we have already received.
            inbuf = greeting;
            greeting.flip ();
            insize = greeting.remaining ();

            //  To allow for interoperability with peers that do not forward
            //  their subscriptions, we inject a phony subsription
            //  message into the incomming message stream. To put this
            //  message right after the identity message, we temporarily
            //  divert the message stream from session to ourselves.
            if (options.type == ZMQ.ZMQ_PUB || options.type == ZMQ.ZMQ_XPUB)
                decoder.set_msg_sink (this);
        }
        else
        if (greeting.get (version_pos) == 0) {
            //  ZMTP/1.0 framing.
            encoder = new_encoder (Config.OUT_BATCH_SIZE.getValue (), null, 0);
            encoder.set_msg_source (session);

            decoder = new_decoder (Config.IN_BATCH_SIZE.getValue (), options.maxmsgsize, null, 0);
            decoder.set_msg_sink (session);
        }
        else {
            //  v1 framing protocol.
            encoder = new_encoder (Config.OUT_BATCH_SIZE.getValue (), session, V1Protocol.VERSION);

            decoder = new_decoder (Config.IN_BATCH_SIZE.getValue (), options.maxmsgsize, session, V1Protocol.VERSION);
        }
        // Start polling for output if necessary.
        if (outsize == 0)
            io_object.set_pollout (handle);

        //  Handshaking was successful.
        //  Switch into the normal message flow.
        handshaking = false;

        return true;
    }
    
    @Override
    public int push_msg (Msg msg_)
    {
        assert (options.type == ZMQ.ZMQ_PUB || options.type == ZMQ.ZMQ_XPUB);

        //  The first message is identity.
        //  Let the session process it.
        int rc = session.push_msg (msg_);
        assert (rc == 0);

        //  Inject the subscription message so that the ZMQ 2.x peer
        //  receives our messages.
        msg_ = new Msg (new byte[] { 1 });
        rc = session.push_msg (msg_);
        session.flush ();

        //  Once we have injected the subscription message, we can
        //  Divert the message flow back to the session.
        assert (decoder != null);
        decoder.set_msg_sink (session);

        return rc;
    }

    private void error () 
    {
        assert (session != null);
        socket.event_disconnected (endpoint, handle);
        session.detach ();
        unplug ();
        destroy ();
    }

    private int write (Transfer buf) 
    {
        int nbytes = 0 ;
        try {
            nbytes = buf.transferTo(handle);
        } catch (IOException e) {
            return -1;
        }
        
        return nbytes;
    }

    
    
    private int read (ByteBuffer buf) 
    {
        int nbytes = 0 ;
        try {
            nbytes = handle.read (buf);
        } catch (IOException e) {
            return -1;
        }
        
        return nbytes;
    }
}
