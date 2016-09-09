package zmq;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Msg
{
    enum Type {
        DATA,
        DELIMITER
    }

    public static final int MORE = 1;
    public static final int COMMAND = 2;
    public static final int IDENTITY = 64;
    public static final int SHARED = 128;

    private int flags;
    private Type type;

    private int size;
    private byte[] data;
    private final ByteBuffer buf;
    // keep track of relative write position
    private int writeIndex = 0;
    // keep track of relative read position
    private int readIndex = 0;

    public Msg()
    {
        this.type = Type.DATA;
        this.flags = 0;
        this.size = 0;
        this.buf = ByteBuffer.wrap(new byte[0]).order(ByteOrder.BIG_ENDIAN);
        this.data = buf.array();
    }

    public Msg(int capacity)
    {
        this.type = Type.DATA;
        this.flags = 0;
        this.size = capacity;
        this.buf = ByteBuffer.wrap(new byte[capacity]).order(ByteOrder.BIG_ENDIAN);
        this.data = buf.array();
    }

    public Msg(byte[] src)
    {
        if (src == null) {
            src = new byte[0];
        }
        this.type = Type.DATA;
        this.flags = 0;
        this.size = src.length;
        this.data = src;
        this.buf = ByteBuffer.wrap(src).order(ByteOrder.BIG_ENDIAN);
    }

    public Msg(final ByteBuffer src)
    {
        if (src == null) {
            throw new IllegalArgumentException("ByteBuffer cannot be null");
        }
        this.type = Type.DATA;
        this.flags = 0;
        this.buf = src.duplicate();
        if (buf.hasArray() && buf.position() == 0 && buf.limit() == buf.capacity()) {
            this.data = buf.array();
        }
        else {
            this.data = null;
        }
        this.size = buf.remaining();
    }

    public Msg(final Msg m)
    {
        if (m == null) {
            throw new IllegalArgumentException("Msg cannot be null");
        }
        this.type = m.type;
        this.flags = m.flags;
        this.size = m.size;
        this.buf = m.buf != null ? m.buf.duplicate() : null;
        if (m.data != null) {
           this.data = new byte[this.size];
           System.arraycopy(m.data, 0, this.data, 0, m.size);
        }
    }

    public boolean isIdentity()
    {
        return (flags & IDENTITY) == IDENTITY;
    }

    public boolean isDelimiter()
    {
        return type == Type.DELIMITER;
    }

    public boolean check()
    {
        return true; // type >= TYPE_MIN && type <= TYPE_MAX;
    }

    public int flags()
    {
        return flags;
    }

    public boolean hasMore()
    {
        return (flags & MORE) > 0;
    }

    public void setFlags(int flags)
    {
        this.flags |= flags;
    }

    public void initDelimiter()
    {
        type = Type.DELIMITER;
        flags = 0;
    }

    public byte[] data()
    {
        if (data == null) {
            data = new byte[buf.remaining()];
            buf.duplicate().get(data);
        }
        return data;
    }

    public ByteBuffer buf()
    {
        return buf.duplicate();
    }

    public int size()
    {
        return size;
    }

    public void resetFlags(int f)
    {
        flags = flags & ~f;
    }

    public byte get()
    {
        return get(readIndex++);
    }

    public byte get(int index)
    {
        return buf.get(index);
    }

    public Msg put(byte b)
    {
        return put(writeIndex++, b);
    }

    public Msg put(int index, byte b)
    {
        buf.put(index, b);
        return this;
    }

    public Msg put(byte[] src)
    {
        return put(src, 0, src.length);
    }

    public Msg put(byte[] src, int off, int len)
    {
        if (src == null) {
            return this;
        }
        ByteBuffer dup = buf.duplicate();
        dup.position(writeIndex);
        writeIndex += len;
        dup.put(src, off, len);
        return this;
    }

    public Msg put(ByteBuffer src)
    {
        ByteBuffer dup = buf.duplicate();
        dup.position(writeIndex);
        writeIndex += Math.min(dup.remaining(), src.remaining());
        dup.put(src);
        return this;
    }

    public int getBytes(int index, byte[] dst, int off, int len)
    {
        int count = Math.min(len, size - index);
        if (data == null) {
            ByteBuffer dup = buf.duplicate();
            dup.position(index);
            dup.put(dst, off, count);
        }
        else {
           System.arraycopy(data, index, dst, off, count);
        }

        return count;
    }

    public int getBytes(int index, ByteBuffer bb, int len)
    {
        ByteBuffer dup = buf.duplicate();
        dup.position(index);
        int count = Math.min(bb.remaining(), dup.remaining());
        count = Math.min(count, len);
        bb.put(dup);
        return count;
    }

    @Override
    public String toString()
    {
        return String.format("#zmq.Msg{type=%s, size=%s, flags=%s}", type, size, flags);
    }
}
