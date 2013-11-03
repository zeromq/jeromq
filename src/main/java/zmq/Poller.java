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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Poller extends PollerBase implements Runnable {

    private static class PollSet {
        protected IPollEvents handler;
        protected SelectionKey key;
        protected int ops;
        protected boolean cancelled;
        
        protected PollSet(IPollEvents handler) {
            this.handler = handler;
            key = null;
            cancelled = false;
            ops = 0;
        }
    }
    //  This table stores data for registered descriptors.
    final private Map<SelectableChannel, PollSet> fd_table;

    //  If true, there's at least one retired event source.
    private boolean retired;

    //  If true, thread is in the process of shutting down.
    volatile private boolean stopping;
    volatile private boolean stopped;
    
    private Thread worker;
    private Selector selector;
    final private String name;
    
    public Poller() {
        this("poller");
    }
    
    public Poller(String name_) {
        
        name = name_;
        retired = false;
        stopping = false;
        stopped = false;
        
        fd_table = new HashMap<SelectableChannel, PollSet>();
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new ZError.IOException(e);
        }
    }

    public void destroy() {

        if (!stopped) {
            try {
                worker.join();
            } catch (InterruptedException e) {
            }
        }
            
        try {
            selector.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public final void add_fd (SelectableChannel fd_, IPollEvents events_)
    {
        fd_table.put(fd_, new PollSet(events_));
        
        adjust_load (1);
        
    }
    

    public final void rm_fd(SelectableChannel handle) {
        
        fd_table.get(handle).cancelled = true;
        retired = true;

        //  Decrease the load metric of the thread.
        adjust_load (-1);
    }
    

    public final void set_pollin (SelectableChannel handle_)
    {
        register(handle_, SelectionKey.OP_READ, false);
    }
    

    public final void reset_pollin (SelectableChannel handle_) {
        register(handle_, SelectionKey.OP_READ, true);
    }
    
    public final void set_pollout (SelectableChannel handle_)
    {
        register(handle_,  SelectionKey.OP_WRITE, false);
    }
    
    public final void reset_pollout (SelectableChannel handle_) {
        register(handle_, SelectionKey.OP_WRITE, true);
    }

    public final void set_pollconnect(SelectableChannel handle_) {
        register(handle_, SelectionKey.OP_CONNECT, false);
    }
    
    public final void set_pollaccept(SelectableChannel handle_) {
        register(handle_, SelectionKey.OP_ACCEPT, false);        
    }

    private final void register (SelectableChannel handle_, int ops, boolean negate)
    {
        PollSet pollset = fd_table.get(handle_);
        
        if (negate) 
            pollset.ops = pollset.ops &~ ops;
        else
            pollset.ops = pollset.ops | ops;
        
        if (pollset.key != null)
            pollset.key.interestOps(pollset.ops);
        else
            retired = true;
    }
    
    public void start() {
        worker = new Thread(this, name);
        worker.start();
    }
    
    public void stop() {
        stopping = true;
        selector.wakeup();
    }
    
    @Override
    public void run () {
        int returnsImmediately = 0;

        while (!stopping) {

            //  Execute any due timers.
            long timeout = execute_timers ();
            
            if (retired) {
                
                Iterator <Map.Entry <SelectableChannel,PollSet>> it = fd_table.entrySet ().iterator ();
                while (it.hasNext ()) {
                    Map.Entry <SelectableChannel,PollSet> entry = it.next ();
                    SelectableChannel ch = entry.getKey ();
                    PollSet pollset = entry.getValue ();
                    if (pollset.key == null) {
                        try {
                            pollset.key = ch.register(selector, pollset.ops, pollset.handler);
                        } catch (ClosedChannelException e) {
                        }
                    } 
                    
                    
                    if (pollset.cancelled || !ch.isOpen()) {
                        if(pollset.key != null) {
                            pollset.key.cancel();
                        }
                        it.remove ();
                    }
                }
                retired = false;
                
            }

            //  Wait for events.
            int rc;
            long start = System.currentTimeMillis ();
            try {
                rc = selector.select (timeout);
            } catch (IOException e) {
                throw new ZError.IOException (e);
            }
            
            if (rc == 0) {
                //  Guess JDK epoll bug
                if (timeout == 0 ||
                        System.currentTimeMillis () - start < timeout / 2)
                    returnsImmediately ++;
                else
                    returnsImmediately = 0;

                if (returnsImmediately > 10) {
                    rebuildSelector ();
                    returnsImmediately = 0;
                }
                continue;
            }


            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                
                SelectionKey key = it.next();
                IPollEvents evt = (IPollEvents) key.attachment();
                it.remove();

                try {
                    if (key.isReadable() ) {
                        evt.in_event();
                    } else if (key.isAcceptable()) {
                        evt.accept_event();
                    } else if (key.isConnectable()) {
                        evt.connect_event();
                    } 
                    if (key.isWritable()) {
                        evt.out_event();
                    } 
                } catch (CancelledKeyException e) {
                    // channel might have been closed
                }
                
            }

        }
        
        stopped = true;
        
    }

    private void rebuildSelector ()
    {
        Selector newSelector;

        try {
            newSelector = Selector.open();
        } catch (IOException e) {
            throw new ZError.IOException(e);
        }

        try {
            selector.close ();
        } catch (IOException e) {
        }

        selector = newSelector;

        for (PollSet pollSet : fd_table.values ()) {
            pollSet.key = null;
        }

        retired = true;
    }


}
