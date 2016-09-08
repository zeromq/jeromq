package zmq;

public class ValueReference<V>
{
    private V value;

    public ValueReference(V value)
    {
        this.value = value;
    }

    public ValueReference()
    {
    }

    public final V get()
    {
        return value;
    }

    public final void set(V value)
    {
        this.value = value;
    }
}
