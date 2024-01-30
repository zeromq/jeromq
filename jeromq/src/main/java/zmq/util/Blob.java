package zmq.util;

import java.util.Arrays;

import zmq.Msg;

public class Blob
{
    private final byte[] buf;

    private Blob(byte[] data)
    {
        buf = data;
    }

    private static Blob createBlob(byte[] data, boolean copy)
    {
        if (copy) {
            byte[] b = new byte[data.length];
            System.arraycopy(data, 0, b, 0, data.length);
            return new Blob(b);
        }
        else {
            return new Blob(data);
        }
    }

    public static Blob createBlob(Msg msg)
    {
        return createBlob(msg.data(), true);
    }

    public static Blob createBlob(byte[] data)
    {
        return createBlob(data, false);
    }

    public int size()
    {
        return buf.length;
    }

    public byte[] data()
    {
        return buf;
    }

    @Override
    public boolean equals(Object t)
    {
        if (t instanceof Blob) {
            return Arrays.equals(buf, ((Blob) t).buf);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(buf);
    }
}
