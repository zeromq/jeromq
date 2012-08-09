/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2010 iMatix Corporation
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
import java.net.Socket;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TcpListener extends Own implements IPollEvents {

    //  Address to listen on.
    final private TcpAddress address;

    //  Underlying socket.
    private ServerSocketChannel s;

    //  Handle corresponding to the listening socket.
    private SelectableChannel handle;

    //  Socket the listerner belongs to.
    private SocketBase socket;

    // String representation of endpoint to bind to
    private String endpoint;

    private final IOObject io_object;
    
    public TcpListener(IOThread io_thread_, SocketBase socket_, final Options options_) {
        super(io_thread_, options_);
        
        io_object = new IOObject(io_thread_);
        address = new TcpAddress();
        s = null;
        socket = socket_;
    }
    
    protected void process_plug ()
    {
        //  Start polling for incoming connections.
        io_object.set_handler(this);
        handle = io_object.add_fd (s);
        io_object.set_pollaccept (handle);
    }

    protected void process_term (int linger_)
    {
        io_object.set_handler(this);
        io_object.rm_fd (handle);
        close ();
        super.process_term (linger_);
    }

    @Override
    public void accept_event ()
    {
        SocketChannel fd = null ;
        
        try {
            fd = accept ();
            Utils.tune_tcp_socket (fd);
            Utils.tune_tcp_keepalives (fd, options.tcp_keepalive, options.tcp_keepalive_cnt, options.tcp_keepalive_idle, options.tcp_keepalive_intvl);
        } catch (IOException e) {
            //  If connection was reset by the peer in the meantime, just ignore it.
            //  TODO: Handle specific errors like ENFILE/EMFILE etc.
            socket.monitor_event (ZMQ.ZMQ_EVENT_ACCEPT_FAILED, endpoint, e);
            return;
        }


        //  Create the engine object for this connection.
        StreamEngine engine = new StreamEngine (fd, options, endpoint);
        //  Choose I/O thread to run connecter in. Given that we are already
        //  running in an I/O thread, there must be at least one available.
        IOThread io_thread = choose_io_thread (options.affinity);

        //  Create and launch a session object. 
        SessionBase session = SessionBase.create (io_thread, false, socket,
            options, null);
        session.inc_seqnum ();
        launch_child (session);
        send_attach (session, engine, false);
        socket.monitor_event (ZMQ.ZMQ_EVENT_ACCEPTED, endpoint, fd);
    }
    
    public void close () {
        if (s == null) 
            return;
        
        try {
            s.close();
            socket.monitor_event (ZMQ.ZMQ_EVENT_CLOSED, endpoint, s);
        } catch (IOException e) {
            socket.monitor_event (ZMQ.ZMQ_EVENT_CLOSE_FAILED, endpoint, e);
        }
        s = null;
    }

    public String get_address() {
        return address.toString();
    }

    //  Set address to listen on.
    public void set_address(final String addr_) throws IOException {
        address.resolve(addr_, options.ipv4only > 0 ? true : false);
        
        endpoint = address.toString();
        s = ServerSocketChannel.open();
        s.configureBlocking(false);
        s.socket().setReuseAddress(true);
        s.socket().bind(address.address(), options.backlog);
        
        socket.monitor_event(ZMQ.ZMQ_EVENT_LISTENING, endpoint, s);
    }

    private SocketChannel accept() {
        Socket sock = null;
        try {
            sock = s.socket().accept();
        } catch (IllegalBlockingModeException e) {
            return null;
        } catch (IOException e) {
            throw new ZException.IOException(e);
        }
        
        if (!options.tcp_accept_filters.isEmpty ()) {
            boolean matched = false;
            for (TcpAddress.TcpAddressMask am : options.tcp_accept_filters) {
                if (am.match_address (address.address())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                try {
                    sock.close();
                } catch (IOException e) {
                    throw new ZException.IOException(e);
                }
                return null;
            }
        }
        return sock.getChannel();
    }

    @Override
    public void in_event() {
        throw new UnsupportedOperationException();
    }

    
    @Override
    public void out_event() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connect_event() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void timer_event(int id_) {
        throw new UnsupportedOperationException();
    }
}
