package zmq.util;

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

    @Override
    public String toString()
    {
        return value == null ? "null" : value.toString();
    }
}
