package zmq.poll;

class PollerBaseTested extends PollerBase
{
    private long clock = 0;

    PollerBaseTested()
    {
        super("test");
    }

    @Override
    Thread createWorker(String name)
    {
        return Thread.currentThread();
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
