package zmq.util;

// Emulates the errno mechanism present in C++, in a per-thread basis.
public final class Errno
{
    private static final ThreadLocal<Integer> local = ThreadLocal.withInitial(() -> 0);

    public int get()
    {
        return local.get();
    }

    public void set(int errno)
    {
        local.set(errno);
    }

    public boolean is(int err)
    {
        return get() == err;
    }

    @Override
    public String toString()
    {
        return "Errno[" + get() + "]";
    }
}
