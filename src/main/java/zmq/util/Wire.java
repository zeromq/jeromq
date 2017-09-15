package zmq.util;

import java.nio.ByteBuffer;

import zmq.Msg;

// Helper functions to convert different integer
// types to/from network byte order.
public class Wire
{
    private Wire()
    {
    }

    public static int getUInt16(byte[] bytes)
    {
        return (bytes[0] & 0xff) << 8 | bytes[1] & 0xff;
    }

    public static byte[] putUInt16(int value)
    {
        assert (value >= 0); // it has to be an *unsigned* int
        byte[] bytes = new byte[2];

        bytes[0] = (byte) ((value >>> 8) & 0xff);
        bytes[1] = (byte) (value & 0xff);

        return bytes;
    }

    public static int getUInt32(ByteBuffer buf)
    {
        return getUInt32(buf, 0);
    }

    public static int getUInt32(Msg msg, int offset)
    {
        return msg.getInt(offset);
    }

    public static int getUInt32(ByteBuffer buf, int offset)
    {
        return (buf.get(offset) & 0xff) << 24 | (buf.get(offset + 1) & 0xff) << 16 | (buf.get(offset + 2) & 0xff) << 8
                | (buf.get(offset + 3) & 0xff);
    }

    public static int getUInt32(byte[] bytes, int offset)
    {
        return (bytes[offset] & 0xff) << 24 | (bytes[offset + 1] & 0xff) << 16 | (bytes[offset + 2] & 0xff) << 8
                | (bytes[offset + 3] & 0xff);
    }

    public static byte[] putUInt32(int value)
    {
        assert (value >= 0); // it has to be an *unsigned* int
        byte[] bytes = new byte[4];

        bytes[0] = (byte) ((value >>> 24) & 0xff);
        bytes[1] = (byte) ((value >>> 16) & 0xff);
        bytes[2] = (byte) ((value >>> 8) & 0xff);
        bytes[3] = (byte) ((value & 0xff));

        return bytes;
    }

    public static ByteBuffer putUInt32(ByteBuffer buf, int value)
    {
        buf.put((byte) ((value >>> 24) & 0xff));
        buf.put((byte) ((value >>> 16) & 0xff));
        buf.put((byte) ((value >>> 8) & 0xff));
        buf.put((byte) ((value & 0xff)));

        return buf;
    }

    public static Msg putUInt32(Msg msg, int value)
    {
        msg.put((byte) ((value >>> 24) & 0xff));
        msg.put((byte) ((value >>> 16) & 0xff));
        msg.put((byte) ((value >>> 8) & 0xff));
        msg.put((byte) ((value & 0xff)));

        return msg;
    }

    public static ByteBuffer putUInt64(ByteBuffer buf, long value)
    {
        buf.put((byte) ((value >>> 56) & 0xff));
        buf.put((byte) ((value >>> 48) & 0xff));
        buf.put((byte) ((value >>> 40) & 0xff));
        buf.put((byte) ((value >>> 32) & 0xff));
        buf.put((byte) ((value >>> 24) & 0xff));
        buf.put((byte) ((value >>> 16) & 0xff));
        buf.put((byte) ((value >>> 8) & 0xff));
        buf.put((byte) ((value) & 0xff));

        return buf;
    }

    public static long getUInt64(ByteBuffer buf, int offset)
    {
        return (long) (buf.get(offset) & 0xff) << 56 | (long) (buf.get(offset + 1) & 0xff) << 48
                | (long) (buf.get(offset + 2) & 0xff) << 40 | (long) (buf.get(offset + 3) & 0xff) << 32
                | (long) (buf.get(offset + 4) & 0xff) << 24 | (long) (buf.get(offset + 5) & 0xff) << 16
                | (long) (buf.get(offset + 6) & 0xff) << 8 | (long) buf.get(offset + 7) & 0xff;
    }

    public static long getUInt64(Msg msg, int offset)
    {
        return msg.getLong(offset);
    }
}
