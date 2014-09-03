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

// Encoder for 0MQ framing protocol. Converts messages into data stream.

public class V1Encoder extends EncoderBase
{
    private static final int SIZE_READY = 0;
    private static final int MESSAGE_READY = 1;

    private Msg inProgress;
    private final byte[] tmpbuf;
    private IMsgSource msgSource;

    public V1Encoder(int bufsize, IMsgSource session)
    {
        super(bufsize);
        tmpbuf = new byte[9];
        msgSource = session;

        //  Write 0 bytes to the batch and go to messageReady state.
        nextStep((byte[]) null, 0, MESSAGE_READY, true);
    }

    @Override
    public void setMsgSource(IMsgSource msgSource)
    {
        this.msgSource = msgSource;
    }

    @Override
    protected boolean next()
    {
        switch(state()) {
        case SIZE_READY:
            return sizeReady();
        case MESSAGE_READY:
            return messageReady();
        default:
            return false;
        }
    }

    private final boolean sizeReady()
    {
        //  Write message body into the buffer.
        nextStep(inProgress.data(), inProgress.size(),
                MESSAGE_READY, !inProgress.hasMore());
        return true;
    }

    private final boolean messageReady()
    {
        //  Read new message. If there is none, return false.
        //  Note that new state is set only if write is successful. That way
        //  unsuccessful write will cause retry on the next state machine
        //  invocation.

        if (msgSource == null) {
            return false;
        }

        inProgress = msgSource.pullMsg();
        if (inProgress == null) {
            return false;
        }

        int protocolFlags = 0;
        if (inProgress.hasMore()) {
            protocolFlags |= V1Protocol.MORE_FLAG;
        }
        if (inProgress.size() > 255) {
            protocolFlags |= V1Protocol.LARGE_FLAG;
        }
        tmpbuf[0] = (byte) protocolFlags;

        //  Encode the message length. For messages less then 256 bytes,
        //  the length is encoded as 8-bit unsigned integer. For larger
        //  messages, 64-bit unsigned integer in network byte order is used.
        final int size = inProgress.size();
        if (size > 255) {
            ByteBuffer b = ByteBuffer.wrap(tmpbuf);
            b.position(1);
            b.putLong(size);
            nextStep(tmpbuf, 9, SIZE_READY, false);
        }
        else {
            tmpbuf[1] = (byte) (size);
            nextStep(tmpbuf, 2, SIZE_READY, false);
        }
        return true;
    }
}
