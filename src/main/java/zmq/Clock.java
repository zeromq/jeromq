package zmq;

public class Clock {

    public static long rdtsc() {
        return System.nanoTime() >> 3;
    }
}
