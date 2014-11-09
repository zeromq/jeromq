/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

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

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

abstract class PollerBase
{
    //  Load of the poller. Currently the number of file descriptors
    //  registered.
    private final AtomicInteger load;

    private final class TimerInfo
    {
        IPollEvents sink;
        int id;

        public TimerInfo(IPollEvents sink, int id)
        {
            this.sink = sink;
            this.id = id;
        }
    }
    private final Map<Long, TimerInfo> timers;
    private final Map<Long, TimerInfo> addingTimers;

    protected PollerBase()
    {
        load = new AtomicInteger(0);
        timers = new MultiMap<Long, TimerInfo>();
        addingTimers = new MultiMap<Long, TimerInfo>();
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
        long expiration = Clock.nowMS() + timeout;
        TimerInfo info = new TimerInfo(sink, id);
        addingTimers.put(expiration, info);

    }

    //  Cancel the timer created by sink_ object with ID equal to id_.
    public void cancelTimer(IPollEvents sink, int id)
    {
        //  Complexity of this operation is O(n). We assume it is rarely used.

        if (!addingTimers.isEmpty()) {
            timers.putAll(addingTimers);
            addingTimers.clear();
        }

        Iterator<Entry<Long, TimerInfo>> it = timers.entrySet().iterator();
        while (it.hasNext()) {
            TimerInfo v = it.next().getValue();
            if (v.sink == sink && v.id == id) {
                it.remove();
                return;
            }
        }

        //  Timer not found.
        assert (false);
    }

    //  Executes any timers that are due. Returns number of milliseconds
    //  to wait to match the next timer or 0 meaning "no timers".
    protected long executeTimers()
    {
        if (!addingTimers.isEmpty()) {
            timers.putAll(addingTimers);
            addingTimers.clear();
        }
        //  Fast track.
        if (timers.isEmpty()) {
            return 0L;
        }

        //  Get the current time.
        long current = Clock.nowMS();

        //   Execute the timers that are already due.
        Iterator<Entry<Long, TimerInfo>> it = timers.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Long, TimerInfo> o = it.next();
            //  If we have to wait to execute the item, same will be true about
            //  all the following items (multimap is sorted). Thus we can stop
            //  checking the subsequent timers and return the time to wait for
            //  the next timer (at least 1ms).

            if (o.getKey() > current) {
                return o.getKey() - current;
            }

            //  Trigger the timer.
            o.getValue().sink.timerEvent(o.getValue().id);
            //  Remove it from the list of active timers.
            it.remove();
        }

        if (!addingTimers.isEmpty()) {
            return executeTimers();
        }

        //  There are no more timers.
        return 0L;
    }
}
