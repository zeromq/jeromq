package zmq.util;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.zeromq.ZMQ;

import zmq.Msg;

// Helper functions to convert different integer
// types to/from network byte order.
public class Wire
{
    private Wire()
    {
    }

    private static int getUInt8(ByteBuffer buf, int offset)
    {
        return buf.get(offset) & 0xff;
    }

    private static ByteBuffer putUInt8(ByteBuffer buf, int value)
    {
        buf.put((byte) (value & 0xff));
        return buf;
    }

    // 2 bytes value
    public static int getUInt16(byte[] bytes)
    {
        return (bytes[0] & 0xff) << 8 | bytes[1] & 0xff;
    }

    public static int getUInt16(ByteBuffer buf, int offset)
    {
        return (buf.get(offset) & 0xff) << 8 | (buf.get(offset + 1) & 0xff);
    }

    public static byte[] putUInt16(int value)
    {
        assert (value >= 0); // it has to be an *unsigned* int
        byte[] bytes = new byte[2];

        bytes[0] = (byte) ((value >>> 8) & 0xff);
        bytes[1] = (byte) ((value & 0xff));

        return bytes;
    }

    public static Msg putUInt16(Msg msg, int value)
    {
        msg.put((byte) ((value >>> 8) & 0xff));
        msg.put((byte) ((value & 0xff)));

        return msg;
    }

    public static ByteBuffer putUInt16(ByteBuffer buf, int value)
    {
        buf.put((byte) ((value >>> 8) & 0xff));
        buf.put((byte) ((value & 0xff)));

        return buf;
    }

    // 4 bytes value
    public static int getUInt32(ByteBuffer buf)
    {
        return getUInt32(buf, 0);
    }

    public static int getUInt32(ByteBuffer buf, int offset)
    {
        return (buf.get(offset) & 0xff) << 24 | (buf.get(offset + 1) & 0xff) << 16 | (buf.get(offset + 2) & 0xff) << 8
                | (buf.get(offset + 3) & 0xff);
    }

    public static int getUInt32(Msg msg, int offset)
    {
        return msg.getInt(offset);
    }

    public static int getUInt32(byte[] bytes, int offset)
    {
        return (bytes[offset] & 0xff) << 24 | (bytes[offset + 1] & 0xff) << 16 | (bytes[offset + 2] & 0xff) << 8
                | (bytes[offset + 3] & 0xff);
    }

    public static ByteBuffer putUInt32(ByteBuffer buf, int value)
    {
        buf.put((byte) ((value >>> 24) & 0xff));
        buf.put((byte) ((value >>> 16) & 0xff));
        buf.put((byte) ((value >>> 8) & 0xff));
        buf.put((byte) ((value & 0xff)));

        return buf;
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

    public static Msg putUInt32(Msg msg, int value)
    {
        msg.put((byte) ((value >>> 24) & 0xff));
        msg.put((byte) ((value >>> 16) & 0xff));
        msg.put((byte) ((value >>> 8) & 0xff));
        msg.put((byte) ((value & 0xff)));

        return msg;
    }

    // 8 bytes value
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

    // strings
    public static int putShortString(ByteBuffer buf, String value)
    {
        return putShortString(ZMQ.CHARSET, buf, value);
    }

    public static String getShortString(ByteBuffer buf, int offset)
    {
        return getShortString(ZMQ.CHARSET, buf, offset);
    }

    public static int putShortString(Charset charset, ByteBuffer buf, String value)
    {
        int length = value.length();
        Utils.checkArgument(length < 256, "String must be strictly smaller than 256 characters");
        putUInt8(buf, length);
        buf.put(value.getBytes(charset));
        return length + 1;
    }

    public static String getShortString(Charset charset, ByteBuffer buf, int offset)
    {
        int length = getUInt8(buf, offset);
        return extractString(charset, buf, offset, length, 1);
    }

    public static int putLongString(ByteBuffer buf, String value)
    {
        return putLongString(ZMQ.CHARSET, buf, value);
    }

    public static String getLongString(ByteBuffer buf, int offset)
    {
        return getLongString(ZMQ.CHARSET, buf, offset);
    }

    public static int putLongString(Charset charset, ByteBuffer buf, String value)
    {
        int length = value.length();
        Utils.checkArgument(length < 0x7fffffff, "String must be smaller than 2^31-1 characters");
        Wire.putUInt32(buf, length);
        buf.put(value.getBytes(charset));
        return length + 4;
    }

    public static String getLongString(Charset charset, ByteBuffer buf, int offset)
    {
        int length = Wire.getUInt32(buf, offset);
        return extractString(charset, buf, offset, length, 4);
    }

    private static String extractString(Charset charset, ByteBuffer buf, int offset, int length, int sizeOfSize)
    {
        byte[] text = new byte[length];
        int old = buf.position();
        buf.position(offset + sizeOfSize);
        buf.get(text, 0, length);
        buf.position(old);
        return new String(text, charset);
    }
}
