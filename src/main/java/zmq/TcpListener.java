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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TcpListener extends Own implements IPollEvents {

    //  Address to listen on.
    private final TcpAddress address;

    //  Underlying socket.
    private ServerSocketChannel handle;

    //  Socket the listerner belongs to.
    private SocketBase socket;

    // String representation of endpoint to bind to
    private String endpoint;

    private final IOObject io_object;
    
    public TcpListener(IOThread io_thread_, SocketBase socket_, final Options options_) {
        super(io_thread_, options_);
        
        io_object = new IOObject(io_thread_);
        address = new TcpAddress();
        handle = null;
        socket = socket_;
    }
    
    @Override
    public void destroy () {
        assert (handle == null);
    }
    
    
    @Override
    protected void process_plug ()
    {
        //  Start polling for incoming connections.
        io_object.set_handler(this);
        io_object.add_fd (handle);
        io_object.set_pollaccept (handle);
    }

    @Override
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
            socket.event_accept_failed (endpoint, ZError.exccode(e));
            return;
        }

        
        //  Create the engine object for this connection.
        StreamEngine engine = null;
        try {
            engine = new StreamEngine (fd, options, endpoint);
        } catch (ZError.InstantiationException e) {
            socket.event_accept_failed (endpoint, ZError.EINVAL);
            return;
        }
        //  Choose I/O thread to run connecter in. Given that we are already
        //  running in an I/O thread, there must be at least one available.
        IOThread io_thread = choose_io_thread (options.affinity);

        //  Create and launch a session object. 
        SessionBase session = SessionBase.create (io_thread, false, socket,
            options, new Address(fd.socket().getRemoteSocketAddress()));
        session.inc_seqnum ();
        launch_child (session);
        send_attach (session, engine, false);
        socket.event_accepted (endpoint, fd);
    }
    

    //  Close the listening socket.
    private void close () {
        if (handle == null) 
            return;
        
        try {
            handle.close();
            socket.event_closed (endpoint, handle);
        } catch (IOException e) {
            socket.event_close_failed (endpoint, ZError.exccode(e));
        }
        handle = null;
    }

    public String get_address() {
        return address.toString();
    }

    //  Set address to listen on.
    public int set_address(final String addr_)  {
        address.resolve(addr_, options.ipv4only > 0 ? true : false);
        
        try {
            handle = ServerSocketChannel.open();
            handle.configureBlocking(false);
            handle.socket().setReuseAddress(true);
            handle.socket().bind(address.address(), options.backlog);
            if (address.getPort()==0)
                address.updatePort(handle.socket().getLocalPort());
        } catch (IOException e) {
            close ();
            return ZError.EADDRINUSE;
        }
        endpoint = address.toString();
        socket.event_listening(endpoint, handle);
        return 0;
    }

    //  Accept the new connection. Returns the file descriptor of the
    //  newly created connection. The function may return retired_fd
    //  if the connection was dropped while waiting in the listen backlog
    //  or was denied because of accept filters.
    private SocketChannel accept() {
        Socket sock = null;
        try {
            sock = handle.socket().accept();
        } catch (IOException e) {
            return null;
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
