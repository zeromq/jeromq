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
import java.nio.channels.SocketChannel;

public class StreamEngine implements IEngine, IPollEvents {
    
    //final private IOObject io_object;
    private SocketChannel handle;

    ByteBuffer inbuf;
    int insize;
    final DecoderBase decoder;
    boolean input_error;

    private Transfer outbuf;
    int outsize;
    final EncoderBase encoder;

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
        handle = fd_;
        inbuf = null;
        insize = 0;
        input_error = false;
        outbuf = null;
        outsize = 0;
        session = null;
        options = options_;
        plugged = false;
        endpoint = endpoint_;

        try {
            Constructor<? extends DecoderBase> dcon = 
                        options_.decoder.getConstructor(int.class, long.class);
            Constructor<? extends EncoderBase> econ = 
                    options_.encoder.getConstructor(int.class);
            decoder = dcon.newInstance(Config.in_batch_size.getValue(), options_.maxmsgsize);
            encoder = econ.newInstance(Config.in_batch_size.getValue());
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
            Utils.unblock_socket (handle);
            
            //  Set the socket buffer limits for the underlying socket.
            if (options.sndbuf != 0) {
                handle.socket().setSendBufferSize((int)options.sndbuf);
            }
            if (options.rcvbuf != 0) {
                handle.socket().setReceiveBufferSize((int)options.rcvbuf);
            }

        } catch (IOException e) {
            throw new ZException.IOException(e);
        }

    }
    
    public void destroy() {
        
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
        encoder.set_session (session_);
        decoder.set_session (session_);
        session = session_;

        io_object = new IOObject(null);
        io_object.set_handler(this);
        //  Connect to I/O threads poller object.
        io_object.plug (io_thread_);
        io_object.add_fd (handle);
        io_object.set_pollin (handle);
        io_object.set_pollout (handle);
        //  Flush all the data that may have been already received downstream.
        in_event ();
        
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
    
    @Override
    public void terminate() {
        unplug ();
        destroy();
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
    
    @Override
    public void out_event() {
        //  If write buffer is empty, try to read new data from the encoder.
        if (outsize == 0) {

            outbuf = encoder.get_data ();
            outsize = outbuf.remaining();
            //  If there is no data to send, stop polling for output.
            if (outbuf.remaining() == 0) {
                io_object.reset_pollout (handle);
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
    public void activate_in ()
    {
        if (input_error) {
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

    private void error() {
        assert (session!=null);
        session.monitor_event (ZMQ.ZMQ_EVENT_DISCONNECTED, endpoint, handle);
        session.detach ();
        unplug ();
        destroy();
    }

    private int write (Transfer buf) {
        int nbytes = 0 ;
        try {
            nbytes = buf.transferTo(handle);
        } catch (IOException e) {
            return -1;
        }
        
        return nbytes;
    }

    
    
    private int read (ByteBuffer buf) {
        int nbytes = 0 ;
        try {
            nbytes = handle.read(buf);
        } catch (IOException e) {
            return -1;
        }
        
        return nbytes;
    }
    



}
