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
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//  If 'delay' is true connecter first waits for a while, then starts
//  connection process.
public class TcpConnecter extends Own implements IPollEvents {

    private static Logger LOG = LoggerFactory.getLogger(TcpConnecter.class);
    
    //  ID of the timer used to delay the reconnection.
    private final static int reconnect_timer_id = 1;
    
    private final IOObject io_object;
    
    //  Address to connect to. Owned by session_base_t.
    private final Address addr;

    //  Underlying socket.
    private SocketChannel handle;

    //  If true file descriptor is registered with the poller and 'handle'
    //  contains valid value.
    private boolean handle_valid;

    //  If true, connecter is waiting a while before trying to connect.
    private boolean wait;

    //  Reference to the session we belong to.
    private SessionBase session;

    //  Current reconnect ivl, updated for backoff strategy
    private int current_reconnect_ivl;

    // String representation of endpoint to connect to
    private String endpoint;

    // Socket
    private SocketBase socket;
    
    public TcpConnecter (IOThread io_thread_,
      SessionBase session_, final Options options_,
      final Address addr_, boolean wait_) {
        
        super (io_thread_, options_);
        io_object = new IOObject(io_thread_);
        addr = addr_;
        handle = null; 
        handle_valid = false;
        wait = wait_;
        session = session_;
        current_reconnect_ivl = options.reconnect_ivl;
        
        assert (addr != null);
        endpoint = addr.toString ();
        socket = session_.get_soket ();
    }
    
    public void destroy ()
    {
        if (wait)
            io_object.cancel_timer (reconnect_timer_id);
        if (handle_valid)
            io_object.rm_fd (handle);

        if (handle != null)
            close ();
        
    }
    
    @Override
    protected void process_plug ()
    {
        io_object.set_handler(this);
        if (wait)
            add_reconnect_timer();
        else {
            start_connecting ();
        }
    }

    @Override
    public void in_event() {
        // connected but attaching to stream engine is not completed. do nothing
        return;
    }
    
    @Override
    public void out_event() {
        // connected but attaching to stream engine is not completed. do nothing
        return;
    }
    
    @Override
    public void accept_event() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void connect_event ()
    {
        boolean err = false;
        SocketChannel fd = null;
        try {
            fd = connect ();
        } catch (ConnectException e) {
            err = true;
        } catch (SocketException e) {
            err = true;
        } catch (IOException e) {
            throw new ZError.IOException(e);
        }

        io_object.rm_fd (handle);
        handle_valid = false;
        
        if (err) {
            //  Handle the error condition by attempt to reconnect.
            close ();
            wait = true;
            add_reconnect_timer();
            return;
        }
        
        handle = null;
        
        try {
            
            Utils.tune_tcp_socket (fd);
            Utils.tune_tcp_keepalives (fd, options.tcp_keepalive, options.tcp_keepalive_cnt, options.tcp_keepalive_idle, options.tcp_keepalive_intvl);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        //  Create the engine object for this connection.
        StreamEngine engine = null;
        try {
            engine = new StreamEngine (fd, options, endpoint);
        } catch (ZError.InstantiationException e) {
            LOG.error("Failed to initialize StreamEngine", e.getCause());
            socket.event_connect_delayed (endpoint, ZError.errno());
            return;
        }
            //alloc_assert (engine);

        //  Attach the engine to the corresponding session object.
        send_attach (session, engine);

        //  Shut the connecter down.
        terminate ();

        socket.event_connected (endpoint, fd);
    }
    
    @Override
    public void timer_event(int id_) {
        wait = false;
        start_connecting ();
    }
    
    //  Internal function to start the actual connection establishment.
    private void start_connecting ()
    {
        //  Open the connecting socket.
       
        try {
            boolean rc = open ();
    
            //  Connect may succeed in synchronous manner.
            if (rc) {
                io_object.add_fd (handle);
                handle_valid = true;
                io_object.connect_event();
                return;
            }
    
            //  Connection establishment may be delayed. Poll for its completion.
            else {
                io_object.add_fd (handle);
                handle_valid = true;
                io_object.set_pollconnect (handle);
                socket.event_connect_delayed (endpoint, ZError.errno());
                return;
            }
        } catch (IOException e) {
            //  Handle any other error condition by eventual reconnect.
            close ();
            wait = true;
            add_reconnect_timer();
        }
    }

    //  Internal function to add a reconnect timer
    private void add_reconnect_timer()
    {
        int rc_ivl = get_new_reconnect_ivl();
        io_object.add_timer (rc_ivl, reconnect_timer_id);
        socket.event_connect_retried (endpoint, rc_ivl);
    }
    
    //  Internal function to return a reconnect backoff delay.
    //  Will modify the current_reconnect_ivl used for next call
    //  Returns the currently used interval
    private int get_new_reconnect_ivl ()
    {
        //  The new interval is the current interval + random value.
        int this_interval = current_reconnect_ivl +
            (Utils.generate_random () % options.reconnect_ivl);

        //  Only change the current reconnect interval  if the maximum reconnect
        //  interval was set and if it's larger than the reconnect interval.
        if (options.reconnect_ivl_max > 0 &&
            options.reconnect_ivl_max > options.reconnect_ivl) {

            //  Calculate the next interval
            current_reconnect_ivl = current_reconnect_ivl * 2;
            if(current_reconnect_ivl >= options.reconnect_ivl_max) {
                current_reconnect_ivl = options.reconnect_ivl_max;
            }
        }
        return this_interval;
    }
    
    //  Open TCP connecting socket. Returns -1 in case of error,
    //  true if connect was successfull immediately. Returns false with
    //  if async connect was launched.
    private boolean open () throws IOException
    {
        assert (handle == null);

        //  Create the socket.
        handle = SocketChannel.open();

        // Set the socket to non-blocking mode so that we get async connect().
        Utils.unblock_socket(handle);

        //  Connect to the remote peer.
        boolean rc = handle.connect(addr.resolved().address());

        return rc;

    }
    
    //  Get the file descriptor of newly created connection. Returns
    //  retired_fd if the connection was unsuccessfull.
    private SocketChannel connect () throws IOException
    {
        boolean finished = handle.finishConnect();
        assert finished;
        SocketChannel ret = handle;
        
        return ret;
    }
    
    //  Close the connecting socket.
    private void close() {
        assert (handle != null);
        try {
            handle.close();
            socket.event_closed (endpoint, handle);
            handle = null;
        } catch (IOException e) {
            ZError.exc (e);
            socket.event_close_failed (endpoint, ZError.errno());
        }
        
    }
}
