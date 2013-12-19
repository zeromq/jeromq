/*
    Copyright (c) 2010-2011 250bpm s.r.o.
    Copyright (c) 2010-2011 Other contributors as noted in the AUTHORS file

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

public class Clock {

    //  TSC timestamp of when last time measurement was made.
    // private long last_tsc;

    //  Physical time corresponding to the TSC above (in milliseconds).
    // private long last_time;
    
    private Clock() {
    }

    //  High precision timestamp.
    public static final long now_us() {
        return System.nanoTime() / 1000L;
    }

    //  Low precision timestamp. In tight loops generating it can be
    //  10 to 100 times faster than the high precision timestamp.
    public static final long now_ms() {
        return System.currentTimeMillis();
    }
    
    //  CPU's timestamp counter. Returns 0 if it's not available.
    public static final long rdtsc() {
        return 0;
    }
}
