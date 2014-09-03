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

//  Helper base class for decoders that know the amount of data to read
//  in advance at any moment. Knowing the amount in advance is a property
//  of the protocol used. 0MQ framing protocol is based size-prefixed
//  paradigm, which qualifies it to be parsed by this class.
//  On the other hand, XML-based transports (like XMPP or SOAP) don't allow
//  for knowing the size of data to read in advance and should use different
//  decoding algorithms.
//
//  This class implements the state machine that parses the incoming buffer.
//  Derived class should implement individual state machine actions.

public class Decoder extends DecoderBase
{
    private static final int ONE_BYTE_SIZE_READY = 0;
    private static final int EIGHT_BYTE_SIZE_READY = 1;
    private static final int FLAGS_READY = 2;
    private static final int MESSAGE_READY = 3;

    private final byte[] tmpbuf;
    private Msg inProgress;
    private final long maxmsgsize;
    private IMsgSink msgSink;

    public Decoder(int bufsize, long maxmsgsize)
    {
        super(bufsize);
        this.maxmsgsize = maxmsgsize;
        tmpbuf = new byte[8];

        //  At the beginning, read one byte and go to oneByteSizeReady state.
        nextStep(tmpbuf, 1, ONE_BYTE_SIZE_READY);
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
        //  First byte of size is read. If it is 0xff(-1 for java byte) read 8-byte size.
        //  Otherwise allocate the buffer for message data and read the
        //  message data into it.
        byte first = tmpbuf[0];
        if (first == -1) {
            nextStep(tmpbuf, 8, EIGHT_BYTE_SIZE_READY);
        }
        else {
            //  There has to be at least one byte (the flags) in the message).
            if (first == 0) {
                decodingError();
                return false;
            }

            int size = (int) first;
            if (size < 0) {
                size = (0xFF) & first;
            }

            //  inProgress is initialised at this point so in theory we should
            //  close it before calling zmq_msg_init_size, however, it's a 0-byte
            //  message and thus we can treat it as uninitialised...
            if (maxmsgsize >= 0 && (long) (size - 1) > maxmsgsize) {
                decodingError();
                return false;

            }
            else {
                inProgress = new Msg(size - 1);
            }

            nextStep(tmpbuf, 1, FLAGS_READY);
        }
        return true;

    }

    private boolean eightByteSizeReady()
    {
        //  8-byte payload length is read. Allocate the buffer
        //  for message body and read the message data into it.
        final long payloadLength = ByteBuffer.wrap(tmpbuf).getLong();

        //  There has to be at least one byte (the flags) in the message).
        if (payloadLength <= 0) {
            decodingError();
            return false;
        }

        //  Message size must not exceed the maximum allowed size.
        if (maxmsgsize >= 0 && payloadLength - 1 > maxmsgsize) {
            decodingError();
            return false;
        }

        //  Message size must fit within range of size_t data type.
        if (payloadLength - 1 > Integer.MAX_VALUE) {
            decodingError();
            return false;
        }

        final int msgSize = (int) (payloadLength - 1);
        //  inProgress is initialized at this point so in theory we should
        //  close it before calling init_size, however, it's a 0-byte
        //  message and thus we can treat it as uninitialized...
        inProgress = new Msg(msgSize);

        nextStep(tmpbuf, 1, FLAGS_READY);

        return true;
    }

    private boolean flagsReady()
    {
        //  Store the flags from the wire into the message structure.

        int first = tmpbuf[0];

        inProgress.setFlags(first & Msg.MORE);

        nextStep(inProgress,
                MESSAGE_READY);

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
            return false;
        }

        nextStep(tmpbuf, 1, ONE_BYTE_SIZE_READY);

        return true;
    }
}
