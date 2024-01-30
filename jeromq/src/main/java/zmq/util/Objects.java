package zmq.util;

public class Objects
{
    private Objects()
    {
        // no instantiation
    }

    public static <T> T requireNonNull(T object, String msg)
    {
        Utils.checkArgument(object != null, msg);
        return object;
    }
}
