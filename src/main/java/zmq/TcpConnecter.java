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
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;

//  If 'delay' is true connecter first waits for a while, then starts
//  connection process.
public class TcpConnecter extends Own implements IPollEvents {

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
    private boolean delayed_start;

    //  True iff a timer has been started.
    private boolean timer_started;

    //  Reference to the session we belong to.
    private SessionBase session;

    //  Current reconnect ivl, updated for backoff strategy
    private int current_reconnect_ivl;

    // String representation of endpoint to connect to
    private Address address;

    // Socket
    private SocketBase socket;

    public TcpConnecter (IOThread io_thread_,
      SessionBase session_, final Options options_,
      final Address addr_, boolean delayed_start_) {

        super (io_thread_, options_);
        io_object = new IOObject(io_thread_);
        addr = addr_;
        handle = null;
        handle_valid = false;
        delayed_start = delayed_start_;
        timer_started = false;
        session = session_;
        current_reconnect_ivl = options.reconnect_ivl;

        assert (addr != null);
        address = addr;
        socket = session_.get_soket ();
    }

    public void destroy ()
    {
        assert (!timer_started);
        assert (!handle_valid);
        assert (handle == null);
    }

    @Override
    protected void process_plug ()
    {
        io_object.set_handler(this);
        if (delayed_start)
            add_reconnect_timer();
        else {
            start_connecting ();
        }
    }

    @Override
    public void process_term (int linger_)
    {
        if (timer_started) {
            io_object.cancel_timer (reconnect_timer_id);
            timer_started = false;
        }

        if (handle_valid) {
            io_object.rm_fd (handle);
            handle_valid = false;
        }

        if (handle != null)
            close ();

        super.process_term (linger_);
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
        } catch (SocketTimeoutException e) {
            err = true;
        } catch (IOException e) {
            throw new ZError.IOException(e);
        }

        io_object.rm_fd (handle);
        handle_valid = false;

        if (err) {
            //  Handle the error condition by attempt to reconnect.
            close ();
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
            engine = new StreamEngine (fd, options, address.toString());
        } catch (ZError.InstantiationException e) {
            socket.event_connect_delayed (address.toString(), -1);
            return;
        }

        //  Attach the engine to the corresponding session object.
        send_attach (session, engine);

        //  Shut the connecter down.
        terminate ();

        socket.event_connected (address.toString(), fd);
    }

    @Override
    public void timer_event(int id_) {
        timer_started = false;
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
            }

            //  Connection establishment may be delayed. Poll for its completion.
            else {
                io_object.add_fd (handle);
                handle_valid = true;
                io_object.set_pollconnect (handle);
                socket.event_connect_delayed (address.toString(), -1);
            }
        } catch (IOException e) {
            //  Handle any other error condition by eventual reconnect.
            if (handle != null)
                close ();
            add_reconnect_timer();
        }
    }

    //  Internal function to add a reconnect timer
    private void add_reconnect_timer()
    {
        int rc_ivl = get_new_reconnect_ivl();
        io_object.add_timer(rc_ivl, reconnect_timer_id);

        // resolve address again to take into account other addresses
        // besides the failing one (e.g. multiple dns entries).
        try {
            address.resolve();
        } catch ( Exception ignored ) {
            // This will fail if the network goes away and the
            // address cannot be resolved for some reason. Try
            // not to fail as the event loop will quit
        }

        socket.event_connect_retried(address.toString(), rc_ivl);
        timer_started = true;
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
        if (addr == null) {
            throw new IOException("Null address");
        }
        Address.IZAddress resolved = addr.resolved();
        if (resolved == null) {
            throw new IOException("Address not resolved");
        }
        SocketAddress sa = resolved.address();
        if (sa == null) {
            throw new IOException("Socket address not resolved");
        }
        boolean rc = false;
        try {
            rc = handle.connect(sa);
        } catch (IllegalArgumentException e) {
            // this will happen if sa is bad.  Address validation is not documented but
            // I've found that IAE is thrown in openjdk as well as on android.
            throw new IOException(e.getMessage(), e);
        }

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
            socket.event_closed (address.toString(), handle);
        } catch (IOException e) {
            socket.event_close_failed (address.toString(), ZError.exccode(e));
        }
        handle = null;
    }
}
