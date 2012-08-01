package zmq;

import java.util.concurrent.atomic.AtomicInteger;

abstract public class PollerBase {
	final AtomicInteger load;
	
	protected PollerBase() {
		load = new AtomicInteger(0);
	}
	
	void adjust_load (int amount_)
	{       
        load.addAndGet(amount_);
	}    
	
	int get_load ()
	{
	    return load.get ();
	}

}
