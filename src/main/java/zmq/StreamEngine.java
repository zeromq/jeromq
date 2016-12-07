package zmq;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

public class StreamEngine implements IEngine, IPollEvents, IMsgSink
{
    //  Size of the greeting message:
    //  Preamble (10 bytes) + version (1 byte) + socket type (1 byte).
    private static final int GREETING_SIZE = 12;

    //  True iff we are registered with an I/O poller.
    private boolean ioEnabled;

    //final private IOObject ioObject;
    private SocketChannel handle;

    private ByteBuffer inbuf;
    private int insize;
    private DecoderBase decoder;

    private Transfer outbuf;
    private int outsize;
    private EncoderBase encoder;

    //  When true, we are still trying to determine whether
    //  the peer is using versioned protocol, and if so, which
    //  version.  When false, normal message flow has started.
    private boolean handshaking;

    //  The receive buffer holding the greeting message
    //  that we are receiving from the peer.
    private final ByteBuffer greeting;

    //  The send buffer holding the greeting message
    //  that we are sending to the peer.
    private final ByteBuffer greetingOutputBuffer;

    //  The session this engine is attached to.
    private SessionBase session;

    //  Detached transient session.
    //private SessionBase leftover_session;

    private Options options;

    // String representation of endpoint
    private String endpoint;

    private boolean plugged;

    // Socket
    private SocketBase socket;

    private IOObject ioObject;

    public StreamEngine(SocketChannel handle, final Options options, final String endpoint)
    {
        this.handle = handle;
        inbuf = null;
        insize = 0;
        ioEnabled = false;
        outbuf = null;
        outsize = 0;
        handshaking = true;
        session = null;
        this.options = options;
        plugged = false;
        this.endpoint = endpoint;
        socket = null;
        greeting = ByteBuffer.allocate(GREETING_SIZE).order(ByteOrder.BIG_ENDIAN);
        greetingOutputBuffer = ByteBuffer.allocate(GREETING_SIZE).order(ByteOrder.BIG_ENDIAN);
        encoder = null;
        decoder = null;

        //  Put the socket into non-blocking mode.
        try {
            Utils.unblockSocket(this.handle);

            //  Set the socket buffer limits for the underlying socket.
            if (this.options.sndbuf != 0) {
                this.handle.socket().setSendBufferSize(this.options.sndbuf);
            }
            if (this.options.rcvbuf != 0) {
                this.handle.socket().setReceiveBufferSize(this.options.rcvbuf);
            }
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }
    }

    private DecoderBase newDecoder(int size, long max, SessionBase session, int version)
    {
       DecoderBase decoder;
       if (options.decoder == null) {
            if (version == V1Protocol.VERSION) {
               decoder = new V1Decoder(size, max, session);
            }
            else {
               decoder = new Decoder(size, max);
            }
        }
        else {
           try {
               Constructor<? extends DecoderBase> dcon;

               if (version == 0)  {
                   dcon = options.decoder.getConstructor(int.class, long.class);
                   decoder = dcon.newInstance(size, max);
               }
               else {
                   dcon = options.decoder.getConstructor(int.class, long.class, IMsgSink.class, int.class);
                   decoder = dcon.newInstance(size, max, session, version);
               }
           }
           catch (SecurityException e) {
               throw new ZError.InstantiationException(e);
           }
           catch (NoSuchMethodException e) {
               throw new ZError.InstantiationException(e);
           }
           catch (InvocationTargetException e) {
               throw new ZError.InstantiationException(e);
           }
           catch (IllegalAccessException e) {
               throw new ZError.InstantiationException(e);
           }
           catch (InstantiationException e) {
               throw new ZError.InstantiationException(e);
           }
        }

        if (options.msgAllocator != null) {
           decoder.setMsgAllocator(options.msgAllocator);
        }
        return decoder;
    }

    private EncoderBase newEncoder(int size, SessionBase session, int version)
    {
        if (options.encoder == null) {
            if (version == V1Protocol.VERSION) {
                return new V1Encoder(size, session);
            }
            return new Encoder(size);
        }

        try {
            Constructor<? extends EncoderBase> econ;

            if (version == 0) {
                econ = options.encoder.getConstructor(int.class);
                return econ.newInstance(size);
            }
            else {
                econ = options.encoder.getConstructor(int.class, IMsgSource.class, int.class);
                return econ.newInstance(size, session, version);
            }
        }
        catch (SecurityException e) {
            throw new ZError.InstantiationException(e);
        }
        catch (NoSuchMethodException e) {
            throw new ZError.InstantiationException(e);
        }
        catch (InvocationTargetException e) {
            throw new ZError.InstantiationException(e);
        }
        catch (IllegalAccessException e) {
            throw new ZError.InstantiationException(e);
        }
        catch (InstantiationException e) {
            throw new ZError.InstantiationException(e);
        }
    }

    public void destroy()
    {
        assert (!plugged);

        if (handle != null) {
            try {
                handle.close();
            }
            catch (IOException e) {
            }
            handle = null;
        }
    }

    public void plug(IOThread ioThread, SessionBase session)
    {
        assert (!plugged);
        plugged = true;

        //  Connect to session object.
        assert (this.session == null);
        assert (session != null);
        this.session = session;
        socket = this.session.getSocket();

        ioObject = new IOObject(null);
        ioObject.setHandler(this);
        //  Connect to I/O threads poller object.
        ioObject.plug(ioThread);
        ioObject.addHandle(handle);
        ioEnabled = true;

        //  Send the 'length' and 'flags' fields of the identity message.
        //  The 'length' field is encoded in the long format.
        greetingOutputBuffer.put((byte) 0xff);
        greetingOutputBuffer.putLong(options.identitySize + 1);
        greetingOutputBuffer.put((byte) 0x7f);

        ioObject.setPollIn(handle);
        //  When there's a raw custom encoder, we don't send 10 bytes frame
        boolean custom = false;
        try {
            custom = options.encoder != null && options.encoder.getDeclaredField("RAW_ENCODER") != null;
        }
        catch (SecurityException e) {
        }
        catch (NoSuchFieldException e) {
        }

        if (!custom) {
            outsize = greetingOutputBuffer.position();
            greetingOutputBuffer.flip();
            outbuf = new Transfer.ByteBufferTransfer(greetingOutputBuffer);
            ioObject.setPollOut(handle);
        }

        //  Flush all the data that may have been already received downstream.
        inEvent();
    }

    private void unplug()
    {
        assert (plugged);
        plugged = false;

        //  Cancel all fd subscriptions.
        if (ioEnabled) {
            ioObject.removeHandle(handle);
            ioEnabled = false;
        }

        //  Disconnect from I/O threads poller object.
        ioObject.unplug();

        //  Disconnect from session object.
        if (encoder != null) {
            encoder.setMsgSource(null);
        }
        if (decoder != null) {
            decoder.setMsgSink(null);
        }
        session = null;
    }

    @Override
    public void terminate()
    {
        unplug();
        destroy();
    }

    @Override
    public void inEvent()
    {
        //  If still handshaking, receive and process the greeting message.
        if (handshaking) {
            if (!handshake()) {
                return;
            }
        }

        assert (decoder != null);
        boolean disconnection = false;

        //  If there's no data to process in the buffer...
        if (insize == 0) {
            //  Retrieve the buffer and read as much data as possible.
            //  Note that buffer can be arbitrarily large. However, we assume
            //  the underlying TCP layer has fixed buffer size and thus the
            //  number of bytes read will be always limited.
            inbuf = decoder.getBuffer();
            insize = read(inbuf);
            inbuf.flip();

            //  Check whether the peer has closed the connection.
            if (insize == -1) {
                insize = 0;
                disconnection = true;
            }
        }

        //  Push the data to the decoder.
        int processed = decoder.processBuffer(inbuf, insize);

        if (processed == -1) {
            disconnection = true;
        }
        else {
            //  Stop polling for input if we got stuck.
            if (processed < insize) {
                ioObject.resetPollIn(handle);
            }

            //  Adjust the buffer.
            insize -= processed;
        }

        //  Flush all messages the decoder may have produced.
        session.flush();

        //  An input error has occurred. If the last decoded message
        //  has already been accepted, we terminate the engine immediately.
        //  Otherwise, we stop waiting for socket events and postpone
        //  the termination until after the message is accepted.
        if (disconnection) {
            if (decoder.stalled()) {
                ioObject.removeHandle(handle);
                ioEnabled = false;
            }
            else {
                error();
            }
        }
    }

    @Override
    public void outEvent()
    {
        //  If write buffer is empty, try to read new data from the encoder.
        if (outsize == 0) {
            //  Even when we stop polling as soon as there is no
            //  data to send, the poller may invoke outEvent one
            //  more time due to 'speculative write' optimisation.
            if (encoder == null) {
                 assert (handshaking);
                 return;
            }

            outbuf = encoder.getData(null);
            outsize = outbuf.remaining();
            //  If there is no data to send, stop polling for output.
            if (outbuf.remaining() == 0) {
                ioObject.resetPollOut(handle);

                // when we use custom encoder, we might want to close
                if (encoder.isError()) {
                    error();
                }

                return;
            }
        }

        //  If there are any data to write in write buffer, write as much as
        //  possible to the socket. Note that amount of data to write can be
        //  arbitratily large. However, we assume that underlying TCP layer has
        //  limited transmission buffer and thus the actual number of bytes
        //  written should be reasonably modest.
        int nbytes = write(outbuf);

        //  IO error has occurred. We stop waiting for output events.
        //  The engine is not terminated until we detect input error;
        //  this is necessary to prevent losing incomming messages.
        if (nbytes == -1) {
            ioObject.resetPollOut(handle);
            return;
        }

        outsize -= nbytes;

        //  If we are still handshaking and there are no data
        //  to send, stop polling for output.
        if (handshaking) {
            if (outsize == 0) {
                ioObject.resetPollOut(handle);
            }
        }

        // when we use custom encoder, we might want to close after sending a response
        if (outsize == 0) {
            if (encoder != null && encoder.isError()) {
                error();
            }
        }
    }

    @Override
    public void connectEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void acceptEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void timerEvent(int id)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void activateOut()
    {
        ioObject.setPollOut(handle);

        //  Speculative write: The assumption is that at the moment new message
        //  was sent by the user the socket is probably available for writing.
        //  Thus we try to write the data to socket avoiding polling for POLLOUT.
        //  Consequently, the latency should be better in request/reply scenarios.
        outEvent();
    }

    @Override
    public void activateIn()
    {
        if (!ioEnabled) {
            //  There was an input error but the engine could not
            //  be terminated (due to the stalled decoder).
            //  Flush the pending message and terminate the engine now.
            decoder.processBuffer(inbuf, 0);
            assert (!decoder.stalled());
            session.flush();
            error();
            return;
        }

        ioObject.setPollIn(handle);

        //  Speculative read.
        ioObject.inEvent();
    }

    private boolean handshake()
    {
        assert (handshaking);

        //  Receive the greeting.
        while (greeting.position() < GREETING_SIZE) {
            final int n = read(greeting);
            if (n == -1) {
                error();
                return false;
            }

            if (n == 0) {
                return false;
            }

            //  We have received at least one byte from the peer.
            //  If the first byte is not 0xff, we know that the
            //  peer is using unversioned protocol.
            if ((greeting.get(0) & 0xff) != 0xff) {
                break;
            }

            if (greeting.position() < 10) {
                continue;
            }

            //  Inspect the right-most bit of the 10th byte (which coincides
            //  with the 'flags' field if a regular message was sent).
            //  Zero indicates this is a header of identity message
            //  (i.e. the peer is using the unversioned protocol).
            if ((greeting.get(9) & 0x01) == 0) {
                break;
            }

            //  The peer is using versioned protocol.
            //  Send the rest of the greeting, if necessary.
            if (greetingOutputBuffer.limit() < GREETING_SIZE) {
                if (outsize == 0) {
                    ioObject.setPollOut(handle);
                }
                int pos = greetingOutputBuffer.position();
                greetingOutputBuffer.position(10).limit(GREETING_SIZE);
                greetingOutputBuffer.put((byte) 1); // Protocol version
                greetingOutputBuffer.put((byte) options.type);  // Socket type
                greetingOutputBuffer.position(pos);
                outsize += 2;
            }
        }

        //  Position of the version field in the greeting.
        final int versionPos = 10;

        //  Is the peer using the unversioned protocol?
        //  If so, we send and receive rests of identity
        //  messages.
        if ((greeting.get(0) & 0xff) != 0xff || (greeting.get(9) & 0x01) == 0) {
            encoder = newEncoder(Config.OUT_BATCH_SIZE.getValue(), null, 0);
            encoder.setMsgSource(session);

            decoder = newDecoder(Config.IN_BATCH_SIZE.getValue(), options.maxMsgSize, null, 0);
            decoder.setMsgSink(session);

            //  We have already sent the message header.
            //  Since there is no way to tell the encoder to
            //  skip the message header, we simply throw that
            //  header data away.
            final int headerSize = options.identitySize + 1 >= 255 ? 10 : 2;
            ByteBuffer tmp = ByteBuffer.allocate(headerSize);
            encoder.getData(tmp);
            if (tmp.remaining() != headerSize) {
                return false;
            }

            //  Make sure the decoder sees the data we have already received.
            inbuf = greeting;
            greeting.flip();
            insize = greeting.remaining();

            //  To allow for interoperability with peers that do not forward
            //  their subscriptions, we inject a phony subsription
            //  message into the incomming message stream. To put this
            //  message right after the identity message, we temporarily
            //  divert the message stream from session to ourselves.
            if (options.type == ZMQ.ZMQ_PUB || options.type == ZMQ.ZMQ_XPUB) {
                decoder.setMsgSink(this);
            }
        }
        else
        if (greeting.get(versionPos) == 0) {
            //  ZMTP/1.0 framing.
            encoder = newEncoder(Config.OUT_BATCH_SIZE.getValue(), null, 0);
            encoder.setMsgSource(session);

            decoder = newDecoder(Config.IN_BATCH_SIZE.getValue(), options.maxMsgSize, null, 0);
            decoder.setMsgSink(session);
        }
        else {
            //  v1 framing protocol.
            encoder = newEncoder(Config.OUT_BATCH_SIZE.getValue(), session, V1Protocol.VERSION);

            decoder = newDecoder(Config.IN_BATCH_SIZE.getValue(), options.maxMsgSize, session, V1Protocol.VERSION);
        }
        // Start polling for output if necessary.
        if (outsize == 0) {
            ioObject.setPollOut(handle);
        }

        //  Handshaking was successful.
        //  Switch into the normal message flow.
        handshaking = false;

        return true;
    }

    @Override
    public int pushMsg(Msg msg)
    {
        assert (options.type == ZMQ.ZMQ_PUB || options.type == ZMQ.ZMQ_XPUB);

        //  The first message is identity.
        //  Let the session process it.
        int rc = session.pushMsg(msg);
        assert (rc == 0);

        //  Inject the subscription message so that the ZMQ 2.x peer
        //  receives our messages.
        msg = new Msg(new byte[] { 1 });
        rc = session.pushMsg(msg);
        session.flush();

        //  Once we have injected the subscription message, we can
        //  Divert the message flow back to the session.
        assert (decoder != null);
        decoder.setMsgSink(session);

        return rc;
    }

    private void error()
    {
        assert (session != null);
        socket.eventDisconnected(endpoint, handle);
        session.detach();
        unplug();
        destroy();
    }

    private int write(Transfer buf)
    {
        int nbytes;
        try {
            nbytes = buf.transferTo(handle);
        }
        catch (IOException e) {
            return -1;
        }

        return nbytes;
    }

    private int read(ByteBuffer buf)
    {
        int nbytes;
        try {
            nbytes = handle.read(buf);
        }
        catch (IOException e) {
            return -1;
        }

        return nbytes;
    }
}
