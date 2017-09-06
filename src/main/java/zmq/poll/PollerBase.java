package zmq.poll;

import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import zmq.util.Clock;
import zmq.util.MultiMap;

abstract class PollerBase implements Runnable
{
    private static final class TimerInfo
    {
        private final IPollEvents sink;
        private final int         id;

        private boolean cancelled;

        public TimerInfo(IPollEvents sink, int id)
        {
            assert (sink != null);
            this.sink = sink;
            this.id = id;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + id;
            result = prime * result + sink.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) {
                return true;
            }
            if (other == null) {
                return false;
            }
            if (!(other instanceof TimerInfo)) {
                return false;
            }
            TimerInfo that = (TimerInfo) other;
            return this.id == that.id && this.sink.equals(that.sink);
        }

        @Override
        public String toString()
        {
            return "TimerInfo [id=" + id + ", sink=" + sink + "]";
        }
    }

    //  Load of the poller. Currently the number of file descriptors
    //  registered.
    private final AtomicInteger load;

    private final MultiMap<Long, TimerInfo> timers;

    // the thread where all events will be dispatched. So, the actual IO or Reaper threads.
    protected final Thread worker;

    // did timers expiration add timer ?
    private boolean changed;

    protected PollerBase(String name)
    {
        worker = createWorker(name);

        load = new AtomicInteger(0);
        timers = new MultiMap<>();
    }

    Thread createWorker(String name)
    {
        Thread worker = new Thread(this, name);
        worker.setDaemon(true);
        return worker;
    }

    long clock()
    {
        return Clock.nowMS();
    }

    final boolean isEmpty()
    {
        return timers.isEmpty();
    }

    //  Returns load of the poller. Note that this function can be
    //  invoked from a different thread!
    public final int getLoad()
    {
        return load.get();
    }

    //  Called by individual poller implementations to manage the load.
    protected void adjustLoad(int amount)
    {
        load.addAndGet(amount);
    }

    //  Add a timeout to expire in timeout_ milliseconds. After the
    //  expiration timerEvent on sink_ object will be called with
    //  argument set to id_.
    public void addTimer(long timeout, IPollEvents sink, int id)
    {
        assert (Thread.currentThread() == worker);

        final long expiration = clock() + timeout;
        TimerInfo info = new TimerInfo(sink, id);
        timers.insert(expiration, info);

        changed = true;
    }

    //  Cancel the timer created by sink_ object with ID equal to id_.
    public void cancelTimer(IPollEvents sink, int id)
    {
        assert (Thread.currentThread() == worker);

        TimerInfo copy = new TimerInfo(sink, id);
        //  Complexity of this operation is O(n). We assume it is rarely used.

        TimerInfo timerInfo = timers.find(copy);
        assert (timerInfo != null);
        // let's defer the removal during the loop
        timerInfo.cancelled = true;
    }

    //  Executes any timers that are due. Returns number of milliseconds
    //  to wait to match the next timer or 0 meaning "no timers".
    protected long executeTimers()
    {
        assert (Thread.currentThread() == worker);

        changed = false;

        //  Fast track.
        if (timers.isEmpty()) {
            return 0L;
        }

        //  Get the current time.
        long current = clock();

        //   Execute the timers that are already due.
        for (Entry<TimerInfo, Long> entry : timers.entries()) {
            final TimerInfo timerInfo = entry.getKey();
            if (timerInfo.cancelled) {
                timers.remove(entry.getValue(), timerInfo);
                continue;
            }
            //  If we have to wait to execute the item, same will be true about
            //  all the following items (multimap is sorted). Thus we can stop
            //  checking the subsequent timers and return the time to wait for
            //  the next timer (at least 1ms).

            final Long key = entry.getValue();

            if (key > current) {
                return key - current;
            }

            //  Trigger the timer.
            timerInfo.sink.timerEvent(timerInfo.id);

            //  Remove it from the list of active timers.
            timers.remove(key, timerInfo);
        }

        if (changed) {
            return executeTimers();
        }
        //  There are no more timers.
        return 0L;
    }
}
