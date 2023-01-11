package zmq.poll;

class PollerBaseTested extends PollerBase
{
    private long clock = 0;

    PollerBaseTested()
    {
        super("test", (s, r) -> Thread.currentThread());
    }

    void clock(long clock)
    {
        this.clock = clock;
    }

    @Override
    long clock()
    {
        return clock;
    }

    @Override
    public void run()
    {
    }
}
