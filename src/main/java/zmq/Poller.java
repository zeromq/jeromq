package zmq;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;

public class Poller extends PollerBase {


    //  This table stores data for registered descriptors.
    //typedef std::vector <fd_entry_t> fd_table_t;
    //List<FdEntry> fd_table;
    final Map<SelectableChannel, IPollEvents> fd_table;

    //  Pollset to pass to the poll function.
    //typedef std::vector <pollfd> pollset_t;
    //pollset_t pollset;
    
    final Map<SelectableChannel, Integer> pollset;

    //  If true, there's at least one retired event source.
    boolean retired;

    //  If true, thread is in the process of shutting down.
    volatile boolean stopping;
    
	final Thread worker;
	
	public Poller() {
		worker = new Thread();
		retired = false;
		stopping = false;
		
		fd_table = new HashMap<SelectableChannel, IPollEvents>();
		pollset = new HashMap<SelectableChannel, Integer>();
	}

	SelectableChannel add_fd (SelectableChannel fd_, IPollEvents events_)
	{
		
	    //  If the file descriptor table is too small expand it.
	    
		
		fd_table.put(fd_, events_);
		pollset.put(fd_, 0);
	    /*
	
	    pollfd pfd = {fd_, 0, 0};
	    pollset.push_back (pfd);
	    zmq_assert (fd_table [fd_].index == retired_fd);
	
	    fd_table [fd_].index = pollset.size() - 1;
	    fd_table [fd_].events = events_;
	
	    //  Increase the load metric of the thread.
	    adjust_load (1);
		*/
		
		adjust_load (1);
	    return fd_;
	}
	
	void set_pollin (SelectableChannel handle_)
	{
		/*
	    int index = fd_table [handle_].index;
	    pollset [index].events |= POLLIN;
	    */
		pollset.put(handle_, pollset.get(handle_) + SelectionKey.OP_READ);
	}
	
	public void start() {
		worker.start();
	}
	
	public void stop() {
		stopping = true;
	}
	
	protected void finalize() throws Throwable {
	     try {
	         worker.interrupt();
	     } finally {
	         super.finalize();
	     }
	}
}
