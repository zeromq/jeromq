package zmq;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

import zmq.io.Metadata;
import zmq.util.Wire;

public class Msg
{
    // dynamic message building used when the size is not known in advance
    public static final class Builder extends Msg
    {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        public Builder()
        {
            super();
        }

        @Override
        public int size()
        {
            return out.size();
        }

        @Override
        protected Msg put(int index, byte b)
        {
            out.write(b);
            return this;
        }

        @Override
        public Msg put(byte[] src, int off, int len)
        {
            if (src == null) {
                return this;
            }
            out.write(src, off, len);
            setWriteIndex(getWriteIndex() + len);
            return this;
        }

        @Override
        public Msg put(ByteBuffer src, int off, int len)
        {
            if (src == null) {
                return this;
            }
            for (int idx = off; idx < off + len; ++idx) {
                out.write(src.get(idx));
            }
            setWriteIndex(getWriteIndex() + len);
            return this;
        }

        @Override
        public void setFlags(int flags)
        {
            super.setFlags(flags);
        }

        public Msg build()
        {
            return new Msg(this, out);
        }
    }

    enum Type
    {
        DATA,
        DELIMITER
    }

    public static final int MORE       = 1;  //  Followed by more parts
    public static final int COMMAND    = 2;  //  Command frame (see ZMTP spec)
    public static final int CREDENTIAL = 32;
    public static final int IDENTITY   = 64;
    public static final int SHARED     = 128;

    private Metadata metadata;
    private int      flags;
    private Type     type;

    // the file descriptor where this message originated, needs to be 64bit due to alignment
    private SocketChannel fileDesc;

    private int              size;
    private byte[]           data;
    private final ByteBuffer buf;
    // keep track of relative write position
    private int writeIndex = 0;
    // keep track of relative read position
    private int readIndex = 0;

    public Msg()
    {
        this(0);
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

    private Msg(Msg src, ByteArrayOutputStream out)
    {
        this(ByteBuffer.wrap(out.toByteArray()));
        this.type = src.type;
        this.flags = src.flags;
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

    public boolean isCommand()
    {
        return (flags & COMMAND) == COMMAND;
    }

    public boolean isCredential()
    {
        return (flags & CREDENTIAL) == CREDENTIAL;
    }

    public void setFlags(int flags)
    {
        this.flags |= flags;
    }

    public void initDelimiter()
    {
        type = Type.DELIMITER;
        metadata = null;
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

    public void setFd(SocketChannel fileDesc)
    {
        this.fileDesc = fileDesc;
    }

    // TODO V4 use the source channel
    public SocketChannel fd()
    {
        return fileDesc;
    }

    public Metadata getMetadata()
    {
        return metadata;
    }

    public Msg setMetadata(Metadata metadata)
    {
        this.metadata = metadata;
        return this;
    }

    public void resetMetadata()
    {
        setMetadata(null);
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

    public Msg put(int b)
    {
        return put(writeIndex++, (byte) b);
    }

    protected Msg put(int index, byte b)
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

    public Msg put(ByteBuffer src, int off, int len)
    {
        if (src == null) {
            return this;
        }
        int position = src.position();
        int limit = src.limit();
        src.limit(off + len).position(off);
        put(src);
        src.limit(limit).position(position);
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

    protected final int getWriteIndex()
    {
        return writeIndex;
    }

    protected final void setWriteIndex(int writeIndex)
    {
        this.writeIndex = writeIndex;
    }

    public long getLong(int offset)
    {
        return Wire.getUInt64(buf, offset);
    }

    public int getInt(int offset)
    {
        return Wire.getUInt32(buf, offset);
    }

    public void transfer(ByteBuffer destination, int srcOffset, int srcLength)
    {
        int position = buf.position();
        int limit = buf.limit();

        buf.limit(srcOffset + srcLength).position(srcOffset);
        destination.put(buf);
        buf.limit(limit).position(position);
    }
}
