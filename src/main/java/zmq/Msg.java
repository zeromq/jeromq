/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

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
    private ByteBuffer buf;

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
        if (src.position() > 0) {
            throw new IllegalArgumentException("ByteBuffer position is not zero, did you forget to flip it?");
        }
        this.type = Type.DATA;
        this.flags = 0;
        this.buf = src.duplicate();
        if (buf.hasArray()) {
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
        this.data = new byte[this.size];
        System.arraycopy(m.data, 0, this.data, 0, m.size);
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
        if (buf.isDirect()) {
            int length = buf.remaining();
            byte[] bytes = new byte[length];
            buf.duplicate().get(bytes);
            return bytes;
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
        return buf.get();
    }

    public byte get(int index)
    {
        return buf.get(index);
    }

    public Msg put(byte b)
    {
        buf.put(b);
        return this;
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
        buf.put(src, off, len);
        return this;
    }

    public Msg put(ByteBuffer src)
    {
        buf.put(src);
        return this;
    }

    public int getBytes(int index, byte[] dst, int off, int len)
    {
        int count = Math.min(len, size);
        if (buf.isDirect()) {
            ByteBuffer dup = buf.duplicate();
            dup.position(index);
            dup.put(dst, off, count);
            return count;
        }
        System.arraycopy(data, index, dst, off, count);
        return count;
    }

    public int getBytes(int index, ByteBuffer bb, int len)
    {
        int count = Math.min(bb.remaining(), size - index);
        count = Math.min(count, len);
        bb.put(buf);
        return count;
    }

    @Override
    public String toString()
    {
        return String.format("#zmq.Msg{type=%s, size=%s, flags=%s}", type, size, flags);
    }
}
