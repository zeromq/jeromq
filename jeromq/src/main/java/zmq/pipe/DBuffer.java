package zmq.pipe;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import zmq.Msg;

class DBuffer<T extends Msg>
{
    private T back;
    private T front;

    private final Lock sync = new ReentrantLock();

    private boolean hasMsg;

    public T back()
    {
        return back;
    }

    public T front()
    {
        return front;
    }

    void write(T msg)
    {
        assert (msg.check());
        sync.lock();
        try {
            back = front;
            front = msg;
            hasMsg = true;
        }
        finally {
            sync.unlock();
        }
    }

    T read()
    {
        sync.lock();
        try {
            if (!hasMsg) {
                return null;
            }

            assert (front.check());
            // TODO front->init ();     // avoid double free
            hasMsg = false;

            return front;
        }
        finally {
            sync.unlock();
        }
    }

    boolean checkRead()
    {
        sync.lock();
        try {
            return hasMsg;
        }
        finally {
            sync.unlock();
        }
    }

    T probe()
    {
        sync.lock();
        try {
            return front;
        }
        finally {
            sync.unlock();
        }
    }
}
