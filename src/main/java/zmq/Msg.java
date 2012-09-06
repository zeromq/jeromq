/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2011 Other contributors as noted in the AUTHORS file

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
import java.util.Arrays;

public class Msg {

    //  Size in bytes of the largest message that is still copied around
    //  rather than being reference-counted.
    
    public final static byte more = 1;
    public final static byte identity = 64;
    public final static byte shared = -128;
    
    private final static byte type_min = 101;
    private final static byte type_vsm = 102;
    private final static byte type_lmsg = 103;
    private final static byte type_delimiter = 104;
    private final static byte type_max = 105;
    
    private byte type;
    private byte flags;
    private int size;
    private byte[] header;
    private byte[] data;
    private ByteBuffer buf;
    
    public Msg() {
        init(type_vsm);
    }

    public Msg(boolean buffered) {
        if (buffered)
            init(type_lmsg);
        else
            init(type_vsm);
    }

    public Msg(int size) {
        init(type_vsm);
        size(size);
    }
    
    public Msg(int size, boolean buffered) {
        if (buffered)
            init(type_lmsg);
        else
            init(type_vsm);
        size(size);
    }

    
    public Msg(Msg m) {
        clone(m);
    }
    
    public Msg(byte[] src) {
        this(src, false);
    }
    
    public Msg(String src) {
        this(src.getBytes(), false);
    }
    
    public Msg(byte[] src, boolean copy) {
        this();
        if (src != null) {
            size = src.length;
            if (copy)
                data = Arrays.copyOf(src, src.length);
            else
                data = src;
        }
    }
    
    public Msg(ByteBuffer src) {
        init(type_lmsg);
        buf = src.duplicate();
        size = buf.remaining();
    }
    

    public boolean is_delimiter ()
    {
        return type == type_delimiter;
    }


    public boolean check ()
    {
         return type >= type_min && type <= type_max;
    }

    private void init(byte type_) {
        type = type_;
        flags = 0;
        size = 0;
        data = null;
        buf = null;
        header = null;
    }

    public void size (int size_)
    {
        size = size_;
        if (type == type_lmsg) {
            flags = 0;
            
            buf = ByteBuffer.allocate(size_);
            data = null;
        }
        else {
            flags = 0;
            data = new byte[size_];
            buf = null;
        }
    }

    public byte flags ()
    {
        return flags;
    }
    
    public boolean has_more ()
    {
        return (flags & Msg.more) > 0;
    }
    
    public byte type ()
    {
        return type;
    }
    
    public void set_flags (byte flags_)
    {
        flags |= flags_;
    }


    
    public void init_delimiter() {
        type = type_delimiter;
        flags = 0;
    }

    
    public byte[] data ()
    {
        if (data == null && type == type_lmsg) 
            data = buf.array();
        return data;
    }
    
    public ByteBuffer buf()
    {
        if (buf == null && type != type_lmsg)
            buf = ByteBuffer.wrap(data);
        return buf;
    }
    
    
    public int size ()
    {
        //  Check the validity of the message.
        assert (check ());

        return size;
    }
    

    public int header_size ()
    {
        if (header == null) {
            if (size < 255)
                return 2;
            else
                return 10;
        }
        else if (header[0] == 0xff)
            return 10;
        else
            return 2;
    }
    public ByteBuffer header_buf()
    {
        ByteBuffer hbuf; 
        if (header == null) {
            header = new byte[10];
            hbuf = ByteBuffer.wrap(header);
            if (size < 255) {
                hbuf.put((byte)size);
                hbuf.put(flags);
            } else {
                hbuf.put((byte)0xff);
                hbuf.put(flags);
                hbuf.putLong((long)size);
            }
            hbuf.rewind();
        } else {
            hbuf = ByteBuffer.wrap(header);
        }
        hbuf.limit(header_size());
        return hbuf;
    }

    public void close ()
    {
        if (!check ()) {
            throw new IllegalStateException();
        }

        init(type_vsm);
    }



    @Override
    public String toString () {
        return super.toString() + "[" + type + "," + size + "," + flags + "]";
    }

    private void clone (Msg m) {
        type = m.type;
        flags = m.flags;
        size = m.size;
        buf = m.buf;
        data = m.data;
    }

    public void reset_flags (byte f) {
        flags = (byte) (flags &~ f);
    }
    
    public void put(byte[] src, int i) {
        
        if (src == null)
            return;

        System.arraycopy(src, 0, data, i, src.length);
    }
    
    public void put(byte[] src, int i, int len_) {
        
        if (len_ == 0 || src == null)
            return;
        
        System.arraycopy(src, 0, data, i, len_);
    }

    public boolean is_vsm() {
        return type == type_vsm;
    }

    
    public void put(byte b) {
        data[0] = b;
    }

    public void put(byte b, int i) {
        data[i] = b;
    }

    public void put(String str, int i) {
        put(str.getBytes(), i);
    }

    public void put(Msg data, int i) {
        put(data.data, i);
    }
    

}
