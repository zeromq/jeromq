package zmq.util;

import java.util.concurrent.TimeUnit;

public class Clock
{
    //  TSC timestamp of when last time measurement was made.
    // private long last_tsc;

    //  Physical time corresponding to the TSC above (in milliseconds).
    // private long last_time;

    private Clock()
    {
    }

    //  High precision timestamp.
    public static long nowUS()
    {
        return TimeUnit.MICROSECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    //  Low precision timestamp. In tight loops generating it can be
    //  10 to 100 times faster than the high precision timestamp.
    public static long nowMS()
    {
        return System.currentTimeMillis();
    }

    //  CPU's timestamp counter. Returns 0 if it's not available.
    public static long rdtsc()
    {
        return 0;
    }
}
