package zmq.poll;

import java.util.Map.Entry;
import java.util.Objects;
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
            this.sink = sink;
            this.id = id;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(id, sink);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof TimerInfo) {
                TimerInfo other = (TimerInfo) obj;
                return this.id == other.id && Objects.equals(this.sink, other.sink);
            }
            return false;
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

    protected PollerBase(String name)
    {
        worker = new Thread(this, name);
        worker.setDaemon(true);

        load = new AtomicInteger(0);
        timers = new MultiMap<>();
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

        final long expiration = Clock.nowMS() + timeout;
        TimerInfo info = new TimerInfo(sink, id);
        timers.insert(expiration, info);
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

        //  Fast track.
        if (timers.isEmpty()) {
            return 0L;
        }

        //  Get the current time.
        long current = Clock.nowMS();

        //   Execute the timers that are already due.
        for (Entry<TimerInfo, Long> entry : timers.entries()) {
            final TimerInfo timerInfo = entry.getKey();
            if (timerInfo.cancelled) {
                timers.remove(entry);
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
            if (!timerInfo.cancelled) {
                timerInfo.sink.timerEvent(timerInfo.id);
            }

            //  Remove it from the list of active timers.
            timers.remove(entry);
        }

        //  There are no more timers.
        return 0L;
    }
}
