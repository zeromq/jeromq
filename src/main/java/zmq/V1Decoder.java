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

public class V1Decoder extends DecoderBase
{
    private static final int ONE_BYTE_SIZE_READY = 0;
    private static final int EIGHT_BYTE_SIZE_READY = 1;
    private static final int FLAGS_READY = 2;
    private static final int MESSAGE_READY = 3;

    private final byte[] tmpbuf;
    private Msg inProgress;
    private IMsgSink msgSink;
    private final long maxmsgsize;
    private int msgFlags;

    public V1Decoder(int bufsize, long maxmsgsize, IMsgSink session)
    {
        super(bufsize);

        this.maxmsgsize = maxmsgsize;
        msgSink = session;

        tmpbuf = new byte[8];

        //  At the beginning, read one byte and go to ONE_BYTE_SIZE_READY state.
        nextStep(tmpbuf, 1, FLAGS_READY);
    }

    //  Set the receiver of decoded messages.
    @Override
    public void setMsgSink(IMsgSink msgSink)
    {
        this.msgSink = msgSink;
    }

    @Override
    protected boolean next()
    {
        switch(state()) {
        case ONE_BYTE_SIZE_READY:
            return oneByteSizeReady();
        case EIGHT_BYTE_SIZE_READY:
            return eightByteSizeReady();
        case FLAGS_READY:
            return flagsReady();
        case MESSAGE_READY:
            return messageReady();
        default:
            return false;
        }
    }

    private boolean oneByteSizeReady()
    {
        int size = tmpbuf[0];
        if (size < 0) {
            size = (0xff) & size;
        }

        //  Message size must not exceed the maximum allowed size.
        if (maxmsgsize >= 0) {
            if (size > maxmsgsize) {
                decodingError();
                return false;
            }
        }

        //  inProgress is initialised at this point so in theory we should
        //  close it before calling zmq_msg_init_size, however, it's a 0-byte
        //  message and thus we can treat it as uninitialised...
        inProgress = new Msg(size);

        inProgress.setFlags(msgFlags);
        nextStep(inProgress.data(), inProgress.size(),
                MESSAGE_READY);

        return true;
    }

    private boolean eightByteSizeReady()
    {
        //  The payload size is encoded as 64-bit unsigned integer.
        //  The most significant byte comes first.
        final long msgSize = ByteBuffer.wrap(tmpbuf).getLong();

        //  Message size must not exceed the maximum allowed size.
        if (maxmsgsize >= 0) {
            if (msgSize > maxmsgsize) {
                decodingError();
                return false;
            }
        }

        //  Message size must fit within range of size_t data type.
        if (msgSize > Integer.MAX_VALUE) {
            decodingError();
            return false;
        }

        //  inProgress is initialised at this point so in theory we should
        //  close it before calling init_size, however, it's a 0-byte
        //  message and thus we can treat it as uninitialised.
        inProgress = new Msg((int) msgSize);

        inProgress.setFlags(msgFlags);
        nextStep(inProgress.data(), inProgress.size(),
                MESSAGE_READY);

        return true;
    }

    private boolean flagsReady()
    {
        //  Store the flags from the wire into the message structure.
        msgFlags = 0;
        int first = tmpbuf[0];
        if ((first & V1Protocol.MORE_FLAG) > 0) {
            msgFlags |= Msg.MORE;
        }

        //  The payload length is either one or eight bytes,
        //  depending on whether the 'large' bit is set.
        if ((first & V1Protocol.LARGE_FLAG) > 0) {
            nextStep(tmpbuf, 8, EIGHT_BYTE_SIZE_READY);
        }
        else {
            nextStep(tmpbuf, 1, ONE_BYTE_SIZE_READY);
        }

        return true;
    }

    private boolean messageReady()
    {
        //  Message is completely read. Push it further and start reading
        //  new message. (inProgress is a 0-byte message after this point.)

        if (msgSink == null) {
            return false;
        }

        int rc = msgSink.pushMsg(inProgress);
        if (rc != 0) {
            if (rc != ZError.EAGAIN) {
                decodingError();
            }

            return false;
        }

        nextStep(tmpbuf, 1, FLAGS_READY);

        return true;
    }
}
