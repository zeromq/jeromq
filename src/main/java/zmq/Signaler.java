package zmq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.Pipe;

public class Signaler {
    //  Underlying write & read file descriptor.
    Pipe.SinkChannel w;
    Pipe.SourceChannel r;
    Selector selector;
    
    Signaler() {
        //  Create the socketpair for signaling.
        make_fdpair ();

        //  Set both fds to non-blocking mode.
        try {
            Utils.unblock_socket (w);
            Utils.unblock_socket (r);
        } catch (IOException e) {
            throw new ZException.IOException(e);
        }
        
        try {
            selector = Selector.open();
            r.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            throw new ZException.IOException(e);
        }
    }
    
    public void close() {
        try {
            r.close();
            w.close();
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void make_fdpair() {
	    Pipe pipe;
	    
	    try {
            pipe = Pipe.open();
        } catch (IOException e) {
            throw new ZException.IOException(e);
        }
	    r = pipe.source();
	    w = pipe.sink();
    }

    public SelectableChannel get_fd() {
		return r;
	}
	
	void send ()
	{
	    ByteBuffer dummy = ByteBuffer.allocate(1);
	    dummy.put((byte)0).flip();
	    
	    while (true) {
	        int nbytes = 0;
            try {
                nbytes = w.write(dummy);
                if (nbytes < 1) {
                    continue;
                }
            } catch (ClosedChannelException e) {
                return;
            } catch (IOException e) {
                throw new ZException.IOException(e);
            }
	        assert (nbytes == 1);
	        break;
	    }
	}


    void recv ()
    {
        ByteBuffer dummy = ByteBuffer.allocate(1);
        int nbytes;
        try {
            nbytes = r.read(dummy);
        } catch (IOException e) {
            throw new ZException.IOException(e);
        } 
        assert (nbytes == 1);
    }
    
    boolean wait_event (long timeout_) {
        
        int rc = 0;
        
        try {
            
            if (timeout_ < 0) {
                rc = selector.select(0);
            } else if (timeout_ == 0) {
                rc = selector.selectNow();
            } else {
                rc = selector.select(timeout_);
            }
            
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        if (rc == 0) {
            return false;
        }
        selector.selectedKeys().clear();
        
        assert (rc == 1);
        return true;

    }



}
