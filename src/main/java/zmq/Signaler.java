package zmq;

import java.io.IOException;
import java.nio.channels.Pipe.SinkChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.Pipe;

public class Signaler {
    //  Underlying write & read file descriptor.
    Pipe.SinkChannel w;
    Pipe.SourceChannel r;
    Selector selector ;
    
    Signaler() {
        //  Create the socketpair for signaling.
        make_fdpair ();

        //  Set both fds to non-blocking mode.
        unblock_socket (w);
        unblock_socket (r);
    }
    
	private void unblock_socket(SelectableChannel channel) {
        try {
            channel.configureBlocking(false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void make_fdpair() {
	    Pipe pipe;
	    
	    try {
	        selector= Selector.open();
            pipe = Pipe.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
	    r = pipe.source();
	    w = pipe.sink();
	    
    }

    public SelectableChannel get_fd() {
		return r;
	}
	
	void send ()
	{
	    byte[] dummy = {0};
	    while (true) {
	        int nbytes = socket_send (w, dummy, dummy.length, 0);
	        if (nbytes == -1 && Errno.get() == Errno.EINTR)
	            continue;
	        assert (nbytes == dummy.length);
	        break;
	    }
	}


    void recv ()
    {
        byte[] dummy = new byte[1];
        int nbytes = socket_recv (r, dummy, dummy.length, 0);
        Errno.errno_assert (nbytes >= 0);
        assert (nbytes == dummy.length);
        assert (dummy[0] == 0);
    }
    
    int wait (int timeout_) {
        
        int rc = 0;
        if (timeout_ < 0) {
            timeout_ = 0;
        } else if (timeout_ == 0) {
            timeout_ = 1;
        }
        try {
            r.register(selector, SelectionKey.OP_READ);
            rc = selector.select((long)timeout_);
        } catch (IOException e) {
            Errno.set(Errno.EINTR);
            return -1;
        }
        
        
        if (rc == 0) {
            Errno.set(Errno.EAGAIN);
            return -1;
        }
        
        assert (rc == 1);
        return 0;
        /*
        struct pollfd pfd;
        pfd.fd = r;
        pfd.events = POLLIN;
        int rc = poll (&pfd, 1, timeout_);
        if (unlikely (rc < 0)) {
            errno_assert (errno == EINTR);
            return -1;
        }
        else if (unlikely (rc == 0)) { // timeout
            errno = EAGAIN;
            return -1;
        }
        zmq_assert (rc == 1);
        zmq_assert (pfd.revents & POLLIN);
        return 0;
        */
    }

    private int socket_send(SelectableChannel w2, byte[] dummy, int length, int j) {
        // XXX
        throw new UnsupportedOperationException();
    }

    private int socket_recv(SelectableChannel r2, byte[] dummy, int length, int i) {
        // XXX
        throw new UnsupportedOperationException();
    }

}
