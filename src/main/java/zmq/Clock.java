package zmq;

public class Clock {

    public static long rdtsc() {
        return System.nanoTime() >> 3;
    }

    public long now_ms() {
        return System.currentTimeMillis();
    }
}
