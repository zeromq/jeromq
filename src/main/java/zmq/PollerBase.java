package zmq;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

abstract public class PollerBase {
	final AtomicInteger load;
	
	final private class Timer {
        IPollEvents sink;
        int id;
        
        public Timer(IPollEvents sink_, int id_) {
            sink = sink_;
            id = id_;
        }
	}
	final private Map<Long, Timer> timers;
	
	protected PollerBase() {
		load = new AtomicInteger(0);
		timers = new TreeMap<Long, Timer>();
	}
	
	public void adjust_load (int amount_)
	{       
        load.addAndGet(amount_);
	}    
	
	public int get_load ()
	{
	    return load.get ();
	}

	long execute_timers ()
	{
	    //  Fast track.
	    if (timers.isEmpty ())
	        return 0;

	    //  Get the current time.
	    long current = System.currentTimeMillis(); // clock.now_ms ();

	    //   Execute the timers that are already due.
	    Iterator<Entry<Long, Timer>> it = timers.entrySet().iterator();
	    while (it.hasNext()) {

	        //  If we have to wait to execute the item, same will be true about
	        //  all the following items (multimap is sorted). Thus we can stop
	        //  checking the subsequent timers and return the time to wait for
	        //  the next timer (at least 1ms).
	        Entry<Long, Timer> o = it.next();
	        if (o.getKey() > current)
	            return o.getKey() - current;

	        //  Trigger the timer.
	        o.getValue().sink.timer_event (o.getValue().id);

	        //  Remove it from the list of active timers.
	        //timers_t::iterator o = it;
	        //++it;
	        //timers.erase (o);
	        it.remove();
	    }

	    //  There are no more timers.
	    return 0;
	}
	
    public void add_timer (int timeout_, IPollEvents sink_, int id_)
    {
        long expiration = System.currentTimeMillis() + timeout_;
        Timer info = new Timer(sink_, id_);
        timers.put(expiration, info);
    }


}
