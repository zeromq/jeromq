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

package guide;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
/**
 * 
 * A simple getKey value message class
 * @author Danish Shrestha <dshrestha06@gmail.com>
 *
 */
public class kvsimple {
	private final String key;
	private long sequence;
	private final byte[] body;

	public kvsimple(String key, long sequence, byte[] body) {
		this.key = key;
		this.sequence = sequence;
		this.body = body; // clone if needed
	}

	public String getKey() {
		return key;
	}

	public long getSequence() {
		return sequence;
	}

    public void setSequence(long sequence)
    {
        this.sequence = sequence;
    }

    public byte[] getBody() {
		return body;
	}

	public void send(Socket publisher) {

		publisher.send(key.getBytes(ZMQ.CHARSET), ZMQ.SNDMORE);

		ByteBuffer bb = ByteBuffer.allocate(8);
		bb.asLongBuffer().put(sequence);
		publisher.send(bb.array(), ZMQ.SNDMORE);

		publisher.send(body, 0);
	}

	public static kvsimple recv(Socket updates) {
        byte [] data = updates.recv(0);
        if (data == null || !updates.hasReceiveMore())
            return null;
		String key = new String(data, ZMQ.CHARSET);
        data = updates.recv(0);
        if (data == null || !updates.hasReceiveMore())
            return null;
		Long sequence = ByteBuffer.wrap(data).getLong();
		byte[] body = updates.recv(0);
        if (body == null || updates.hasReceiveMore())
            return null;

		return new kvsimple(key, sequence, body);
	}

	@Override
	public String toString() {
		return "kvsimple [getKey=" + key + ", getSequence=" + sequence + ", body=" + Arrays.toString(body) + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(body);
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		result = prime * result + (int) (sequence ^ (sequence >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		kvsimple other = (kvsimple) obj;
		if (!Arrays.equals(body, other.body))
			return false;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		if (sequence != other.sequence)
			return false;
		return true;
	}

}
