/*
    Copyright (c) 2010 250bpm s.r.o.
    Copyright (c) 2010-2011 Other contributors as noted in the AUTHORS file

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

import java.util.Arrays;

public class Blob {

    private final byte[] buf;

    private Blob(byte[] data_) {
        buf = data_;
    }
    
    public static Blob createBlob(byte[] data, boolean copy) {
        if(copy) {
            byte[] b = new byte[data.length];
            System.arraycopy(data, 0, b, 0, data.length);
            return new Blob(b);
        } else {
            return new Blob(data);
        }
    }

    public int size() {
        return buf.length;
    }

    public byte[] data() {
        return buf;
    }

    @Override
    public boolean equals(Object t) {

        if (t instanceof Blob)
            return Arrays.equals(buf, ((Blob) t).buf);
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(buf);
    }
}
