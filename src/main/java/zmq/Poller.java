package zmq;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Poller extends PollerBase implements Runnable {

    //  This table stores data for registered descriptors.
    //typedef std::vector <fd_entry_t> fd_table_t;
    //List<FdEntry> fd_table;
    final private Map<SelectableChannel, IPollEvents> fd_table;

    //  Pollset to pass to the poll function.
    //typedef std::vector <pollfd> pollset_t;
    //pollset_t pollset;
    
    final private Map<SelectableChannel, Integer> pollset;

    //  If true, there's at least one retired event source.
    volatile private boolean retired;

    //  If true, thread is in the process of shutting down.
    volatile private boolean stopping;
    
    private Thread worker;
    final private Selector selector;
    final private String name;
    
    public Poller(String name_) {
        
        name = name_;
        retired = false;
        stopping = false;
        
        fd_table = new HashMap<SelectableChannel, IPollEvents>();
        pollset = new HashMap<SelectableChannel, Integer>();
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    SelectableChannel add_fd (SelectableChannel fd_, IPollEvents events_)
    {
        
        //  If the file descriptor table is too small expand it.
        
        
        fd_table.put(fd_, events_);
        pollset.put(fd_, 0);
        
        adjust_load (1);
        return fd_;
    }
    

    public void rm_fd(SelectableChannel handle) {
        
        fd_table.remove(handle);
        pollset.remove(handle);
        retired = true;

        selector.wakeup();
        //  Decrease the load metric of the thread.
        adjust_load (-1);
    }
    

    public void set_pollin (SelectableChannel handle_)
    {
        register(handle_, SelectionKey.OP_READ);
    }
    
    public void set_pollout (SelectableChannel handle_)
    {
        register(handle_, SelectionKey.OP_WRITE);
    }
    public void set_pollconnect(SelectableChannel handle_) {
        register(handle_, SelectionKey.OP_CONNECT);
    }
    
    private void register (SelectableChannel handle_, int ops)
    {
        SelectionKey key = handle_.keyFor(selector);
        pollset.put(handle_, pollset.get(handle_) + ops);
        if (key == null) {
            try {
                handle_.register(selector, pollset.get(handle_));
            } catch (ClosedChannelException e) {
                throw new ZException.IOException(e);
            }
        } else {
            retired = true;
            selector.wakeup();
        }
    }
    
    public void start() {
        worker = new Thread(this, name);
        worker.start();
    }
    
    public void stop() {
        stopping = true;
        try {
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    protected void finalize() throws Throwable {
         try {
             stop();
             worker.interrupt();
         } finally {
             super.finalize();
         }
    }
    


    @Override
    public void run() {
        
        while (!stopping) {

            //  Execute any due timers.
            long timeout = execute_timers ();
            
            if (retired) {
                for (SelectionKey key: selector.keys()) {
                    Integer ops = pollset.get(key.channel());
                    
                    if (ops == null) {
                        // removed
                        key.cancel();
                    } else if (ops != key.interestOps()) {
                        key.interestOps(ops);
                    }
                }
                retired = false;
            }

            //  Wait for events.
            int rc;
            try {
                rc = selector.select(timeout);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            
            if (rc > 0) {
                Set<SelectionKey> keys = selector.selectedKeys();
                for (SelectionKey key: keys) {
                    
                    SelectableChannel channel = key.channel();
                    if (!key.isValid() ) {
                        //fd_table.get(channel).in_event();
                        throw new UnsupportedOperationException();
                    }
                    
                    if (key.isWritable()) {
                        fd_table.get(channel).out_event();
                    }
                    
                    if (key.isReadable() ) {
                        fd_table.get(channel).in_event();
                    } else if (key.isConnectable()) {
                        fd_table.get(channel).connect_event();
                    }
    
                }
                keys.clear();
            } 
            

        }
        
    }


    
}
