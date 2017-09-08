package zmq.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import zmq.Config;
import zmq.Msg;
import zmq.Options;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.coder.IDecoder;
import zmq.io.coder.IDecoder.Step;
import zmq.io.coder.IEncoder;
import zmq.io.coder.raw.RawDecoder;
import zmq.io.coder.raw.RawEncoder;
import zmq.io.coder.v1.V1Decoder;
import zmq.io.coder.v1.V1Encoder;
import zmq.io.coder.v2.V2Decoder;
import zmq.io.coder.v2.V2Encoder;
import zmq.io.mechanism.Mechanism;
import zmq.io.mechanism.Mechanisms;
import zmq.io.net.Address;
import zmq.poll.IPollEvents;
import zmq.poll.Poller;
import zmq.util.Blob;
import zmq.util.Errno;
import zmq.util.Utils;
import zmq.util.ValueReference;
import zmq.util.Wire;

// This engine handles any socket with SOCK_STREAM semantics,
// e.g. TCP socket or an UNIX domain socket.
public class StreamEngine implements IEngine, IPollEvents
{
    private final class HandshakeCommand extends MessageProcessor.Adapter
    {
        @Override
        public Msg nextMsg()
        {
            return nextHandshakeCommand();
        }

        @Override
        public boolean processMsg(Msg msg)
        {
            return processHandshakeCommand(msg);
        }
    }

    private final class PushMsgToSession extends MessageProcessor.Adapter
    {
        @Override
        public Msg nextMsg()
        {
            return pullMsgFromSession();
        }

        @Override
        public boolean processMsg(Msg msg)
        {
            return pushMsgToSession(msg);
        }
    }

    private final class PushRawMsgToSession extends MessageProcessor.Adapter
    {
        @Override
        public boolean processMsg(Msg msg)
        {
            return pushRawMsgToSession(msg);
        }
    }

    private final class WriteCredential extends MessageProcessor.Adapter
    {
        @Override
        public boolean processMsg(Msg msg)
        {
            return writeCredential(msg);
        }
    }

    private final class DecodeAndPush extends MessageProcessor.Adapter
    {
        @Override
        public boolean processMsg(Msg msg)
        {
            assert (mechanism != null);

            msg = mechanism.decode(msg);
            if (msg == null) {
                return false;
            }
            if (metadata != null) {
                msg.setMetadata(metadata);
            }
            boolean rc = session.pushMsg(msg);
            if (!rc) {
                if (errno.is(ZError.EAGAIN)) {
                    processMsg = pushOneThenDecodeAndPush;
                }
                return false;
            }
            return true;
        }
    }

    private final class PullAndEncode extends MessageProcessor.Adapter
    {
        @Override
        public Msg nextMsg()
        {
            assert (mechanism != null);

            Msg msg = session.pullMsg();
            if (msg == null) {
                return null;

            }
            msg = mechanism.encode(msg);
            return msg;
        }
    }

    private final class PushOneThenDecodeAndPush extends MessageProcessor.Adapter
    {
        @Override
        public boolean processMsg(Msg msg)
        {
            boolean rc = session.pushMsg(msg);
            if (rc) {
                processMsg = decodeAndPush;
            }
            return rc;
        }
    }

    private final class Identity implements MessageProcessor
    {
        @Override
        public Msg nextMsg()
        {
            return identityMsg();
        }

        @Override
        public boolean processMsg(Msg msg)
        {
            return processIdentityMsg(msg);
        }
    }

    // used to implement FSM actions
    private interface MessageProcessor
    {
        Msg nextMsg();

        boolean processMsg(Msg msg);

        public static class Adapter implements MessageProcessor
        {
            @Override
            public Msg nextMsg()
            {
                throw new UnsupportedOperationException("nextMsg is not implemented and should not be used here");
            }

            @Override
            public boolean processMsg(Msg msg)
            {
                throw new UnsupportedOperationException("processMsg is not implemented and should not be used here");
            }
        }
    }

    //  Protocol revisions
    private enum Protocol
    {
        V0(-1),
        V1(0),
        V2(1),
        V3(3);

        private final byte revision;

        Protocol(int revision)
        {
            this.revision = (byte) revision;
        }
    }

    public enum ErrorReason
    {
        PROTOCOL,
        CONNECTION,
        TIMEOUT,
    }

    private IOObject ioObject;

    //  Underlying socket.
    private SocketChannel fd;

    //  True if this is server's engine.
    //    private boolean asServer;

    //    private Msg txMsg;

    private Poller.Handle handle;

    private ByteBuffer inpos;
    private int        insize;
    private IDecoder   decoder;

    private final ValueReference<ByteBuffer> outpos;
    private int                              outsize;
    private IEncoder                         encoder;

    private Metadata metadata;

    //  When true, we are still trying to determine whether
    //  the peer is using versioned protocol, and if so, which
    //  version.  When false, normal message flow has started.
    private boolean handshaking;

    private static final int SIGNATURE_SIZE = 10;
    //  Size of ZMTP/1.0 and ZMTP/2.0 greeting message
    private static final int V2_GREETING_SIZE = 12;
    //  Size of ZMTP/3.0 greeting message
    private static final int V3_GREETING_SIZE = 64;

    //  Expected greeting size.
    private int greetingSize;

    //  Greeting received from, and sent to peer
    private final ByteBuffer greetingRecv;
    private final ByteBuffer greetingSend;

    // handy reminder of the used ZMTP protocol version
    private Protocol zmtpVersion;

    //  The session this engine is attached to.
    private SessionBase session;

    private Options options;

    // String representation of endpoint
    private String endpoint;

    private boolean plugged;

    private MessageProcessor nextMsg;
    private MessageProcessor processMsg;

    private boolean ioError;

    //  Indicates whether the engine is to inject a phantom
    //  subscription message into the incoming stream.
    //  Needed to support old peers.
    private boolean subscriptionRequired;

    private Mechanism mechanism;

    //  True if the engine couldn't consume the last decoded message.
    private boolean inputStopped;

    //  True if the engine doesn't have any message to encode.
    private boolean outputStopped;

    //  ID of the handshake timer
    private static final int HANDSHAKE_TIMER_ID = 0x40;

    //  True is linger timer is running.
    private boolean hasHandshakeTimer;

    // Socket
    private SocketBase socket;

    private Address peerAddress;

    private final Errno errno;

    public StreamEngine(SocketChannel fd, final Options options, final String endpoint)
    {
        this.errno = options.errno;
        this.fd = fd;
        this.handshaking = true;
        greetingSize = V2_GREETING_SIZE;
        this.options = options;
        this.endpoint = endpoint;
        nextMsg = identity;
        processMsg = identity;

        outpos = new ValueReference<>();

        greetingRecv = ByteBuffer.allocate(V3_GREETING_SIZE);
        greetingSend = ByteBuffer.allocate(V3_GREETING_SIZE);

        //  Put the socket into non-blocking mode.
        try {
            Utils.unblockSocket(this.fd);
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }

        peerAddress = Utils.getPeerIpAddress(fd);
    }

    public void destroy()
    {
        assert (!plugged);

        if (fd != null) {
            try {
                fd.close();
            }
            catch (IOException e) {
                assert (false);
            }
            fd = null;
        }
        if (encoder != null) {
            encoder.destroy();
        }
        if (decoder != null) {
            decoder.destroy();
        }
        if (mechanism != null) {
            mechanism.destroy();
        }
    }

    @Override
    public void plug(IOThread ioThread, SessionBase session)
    {
        assert (!plugged);
        plugged = true;

        //  Connect to session object.
        assert (this.session == null);
        assert (session != null);
        this.session = session;
        socket = session.getSocket();

        //  Connect to I/O threads poller object.
        ioObject = new IOObject(ioThread, this);
        ioObject.plug();
        handle = ioObject.addFd(fd);
        ioError = false;

        if (options.rawSocket) {
            decoder = (IDecoder) instantiate(options.decoder, Config.IN_BATCH_SIZE.getValue(), options.maxMsgSize);
            if (decoder == null) {
                decoder = new RawDecoder(Config.IN_BATCH_SIZE.getValue());
            }
            encoder = (IEncoder) instantiate(options.encoder, Config.OUT_BATCH_SIZE.getValue(), options.maxMsgSize);
            if (encoder == null) {
                encoder = new RawEncoder(errno, Config.OUT_BATCH_SIZE.getValue());
            }

            // disable handshaking for raw socket
            handshaking = false;

            nextMsg = pullMsgFromSession;
            processMsg = pushRawMsgToSession;

            if (peerAddress != null && !peerAddress.address().isEmpty()) {
                assert (metadata == null);
                // Compile metadata
                metadata = new Metadata();
                metadata.set(Metadata.PEER_ADDRESS, peerAddress.address());
            }

            //  For raw sockets, send an initial 0-length message to the
            // application so that it knows a peer has connected.
            Msg connector = new Msg();
            pushRawMsgToSession(connector);
            session.flush();
        }
        else {
            // start optional timer, to prevent handshake hanging on no input
            setHandshakeTimer();

            //  Send the 'length' and 'flags' fields of the identity message.
            //  The 'length' field is encoded in the long format.
            greetingSend.put((byte) 0xff);
            Wire.putUInt64(greetingSend, options.identitySize + 1);
            greetingSend.put((byte) 0x7f);

            outpos.set(greetingSend);
            outsize = greetingSend.position();
            greetingSend.flip();
        }

        ioObject.setPollIn(handle);
        ioObject.setPollOut(handle);

        //  Flush all the data that may have been already received downstream.
        inEvent();
    }

    private Object instantiate(Class<?> decoder, int size, long max)
    {
        if (decoder == null) {
            return null;
        }
        try {
            return decoder.getConstructor(int.class, long.class).newInstance(size, max);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void unplug()
    {
        assert (plugged);
        plugged = false;

        //  Cancel all timers.
        if (hasHandshakeTimer) {
            ioObject.cancelTimer(HANDSHAKE_TIMER_ID);
            hasHandshakeTimer = false;
        }

        if (!ioError) {
            //  Cancel all fd subscriptions.
            ioObject.removeHandle(handle);
            handle = null;
        }

        //  Disconnect from I/O threads poller object.
        ioObject.unplug();

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
        assert (!ioError);

        //  If still handshaking, receive and process the greeting message.
        if (handshaking) {
            if (!handshake()) {
                return;
            }
        }

        assert (decoder != null);

        //  If there has been an I/O error, stop polling.
        if (inputStopped) {
            ioObject.removeHandle(handle);
            handle = null;
            ioError = true;
            return;
        }

        //  If there's no data to process in the buffer...
        if (insize == 0) {
            //  Retrieve the buffer and read as much data as possible.
            //  Note that buffer can be arbitrarily large. However, we assume
            //  the underlying TCP layer has fixed buffer size and thus the
            //  number of bytes read will be always limited.

            inpos = decoder.getBuffer();
            int rc = read(inpos);

            if (rc == 0) {
                error(ErrorReason.CONNECTION);
            }

            if (rc == -1) {
                if (!errno.is(ZError.EAGAIN)) {
                    error(ErrorReason.CONNECTION);
                }
                return;
            }
            //  Adjust input size
            inpos.flip();
            insize = rc;
        }

        boolean rc = false;
        ValueReference<Integer> processed = new ValueReference<>(0);

        while (insize > 0) {
            //  Push the data to the decoder.
            Step.Result result = decoder.decode(inpos, insize, processed);
            assert (processed.get() <= insize);
            insize -= processed.get();

            if (result == Step.Result.MORE_DATA) {
                rc = true;
                break;
            }
            if (result == Step.Result.ERROR) {
                rc = false;
                break;
            }

            Msg msg = decoder.msg();
            rc = processMsg.processMsg(msg);
            if (!rc) {
                break;
            }
        }

        // Tear down the connection if we have failed to decode input data
        //  or the session has rejected the message.
        if (!rc) {
            if (!errno.is(ZError.EAGAIN)) {
                error(ErrorReason.PROTOCOL);
                return;
            }

            inputStopped = true;
            ioObject.resetPollIn(handle);
        }

        //  Flush all messages the decoder may have produced.
        session.flush();
    }

    @Override
    public void outEvent()
    {
        assert (!ioError);

        //  If write buffer is empty, try to read new data from the encoder.
        if (outsize == 0) {
            //  Even when we stop polling as soon as there is no
            //  data to send, the poller may invoke outEvent one
            //  more time due to 'speculative write' optimization.
            if (encoder == null) {
                assert (handshaking);
                return;
            }
            outpos.set(null);
            outsize = encoder.encode(outpos, 0);

            while (outsize < Config.OUT_BATCH_SIZE.getValue()) {
                Msg msg = nextMsg.nextMsg();
                if (msg == null) {
                    break;
                }
                encoder.loadMsg(msg);
                int n = encoder.encode(outpos, Config.OUT_BATCH_SIZE.getValue() - outsize);
                assert (n > 0);
                outsize += n;
            }

            //  If there is no data to send, stop polling for output.
            if (outsize == 0) {
                outputStopped = true;
                ioObject.resetPollOut(handle);

                return;
            }

            ByteBuffer buf = outpos.get();
            assert (buf != null);

            if (outsize <= Config.OUT_BATCH_SIZE.getValue()) {
                buf.flip();
            }
        }

        //  If there are any data to write in write buffer, write as much as
        //  possible to the socket. Note that amount of data to write can be
        //  arbitrarily large. However, we assume that underlying TCP layer has
        //  limited transmission buffer and thus the actual number of bytes
        //  written should be reasonably modest.
        int nbytes = write(outpos.get());

        //  IO error has occurred. We stop waiting for output events.
        //  The engine is not terminated until we detect input error;
        //  this is necessary to prevent losing incoming messages.
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
    }

    @Override
    public void restartOutput()
    {
        if (ioError) {
            return;
        }

        if (outputStopped) {
            ioObject.setPollOut(handle);
            outputStopped = false;
        }

        //  Speculative write: The assumption is that at the moment new message
        //  was sent by the user the socket is probably available for writing.
        //  Thus we try to write the data to socket avoiding polling for POLLOUT.
        //  Consequently, the latency should be better in request/reply scenarios.
        outEvent();
    }

    @Override
    public void restartInput()
    {
        assert (inputStopped);
        assert (session != null);
        assert (decoder != null);

        boolean rc;
        Msg msg = decoder.msg();
        rc = processMsg.processMsg(msg);
        if (!rc) {
            if (errno.is(ZError.EAGAIN)) {
                session.flush();
            }
            else {
                error(ErrorReason.PROTOCOL);
            }
            return;
        }

        while (insize > 0) {
            ValueReference<Integer> processed = new ValueReference<>(0);
            Step.Result result = decoder.decode(inpos, insize, processed);
            assert (processed.get() <= insize);
            insize -= processed.get();
            if (result == Step.Result.MORE_DATA) {
                rc = true;
                break;
            }
            if (result == Step.Result.ERROR) {
                rc = false;
                break;
            }
            msg = decoder.msg();
            rc = processMsg.processMsg(msg);
            if (!rc) {
                break;
            }
        }
        if (!rc && errno.is(ZError.EAGAIN)) {
            session.flush();
        }
        else if (ioError) {
            error(ErrorReason.CONNECTION);
        }
        else if (!rc) {
            error(ErrorReason.PROTOCOL);
        }
        else {
            inputStopped = false;
            ioObject.setPollIn(handle);
            session.flush();

            //  Speculative read.
            inEvent();
        }
    }

    //  Detects the protocol used by the peer.
    private boolean handshake()
    {
        assert (handshaking);
        assert (greetingRecv.position() < greetingSize);

        //  Position of the version field in the greeting.
        final int revisionPos = SIGNATURE_SIZE;

        //  Receive the greeting.
        while (greetingRecv.position() < greetingSize) {
            final int n = read(greetingRecv);
            if (n == 0) {
                error(ErrorReason.CONNECTION);
                return false;
            }
            if (n == -1) {
                if (!errno.is(ZError.EAGAIN)) {
                    error(ErrorReason.CONNECTION);
                }
                return false;
            }

            //  We have received at least one byte from the peer.
            //  If the first byte is not 0xff, we know that the
            //  peer is using unversioned protocol.
            if ((greetingRecv.get(0) & 0xff) != 0xff) {
                // If this first byte is not %FF,
                // then the other peer is using ZMTP 1.0.
                break;
            }

            if (greetingRecv.position() < SIGNATURE_SIZE) {
                continue;
            }

            //  Inspect the right-most bit of the 10th byte (which coincides
            //  with the 'flags' field if a regular message was sent).
            //  Zero indicates this is a header of identity message
            //  (i.e. the peer is using the unversioned protocol).
            if ((greetingRecv.get(9) & 0x01) != 0x01) {
                break;
            }

            // If the least significant bit is 1, the peer is using ZMTP 2.0 or later
            // and has sent us the ZMTP signature.

            int outpos = greetingSend.position();
            //  The peer is using versioned protocol.
            //  Send the major version number.
            if (greetingSend.limit() == SIGNATURE_SIZE) {
                if (outsize == 0) {
                    ioObject.setPollOut(handle);
                }
                greetingSend.limit(SIGNATURE_SIZE + 1);
                greetingSend.put(revisionPos, Protocol.V3.revision); //  Major version number
                outsize += 1;
            }

            if (greetingRecv.position() > SIGNATURE_SIZE) {
                if (greetingSend.limit() == SIGNATURE_SIZE + 1) {
                    if (outsize == 0) {
                        ioObject.setPollOut(handle);
                    }
                    // We read a further byte, which indicates the ZMTP version.
                    byte protocol = greetingRecv.get(revisionPos);
                    if (protocol == Protocol.V1.revision || protocol == Protocol.V2.revision) {
                        // If this is V1 or V2, we have a ZMTP 2.0 peer.
                        greetingSend.limit(V2_GREETING_SIZE);
                        greetingSend.position(SIGNATURE_SIZE + 1);
                        greetingSend.put((byte) options.type); // Socket type
                        outsize += 1;
                    }
                    else {
                        // If this is 3 or greater, we have a ZMTP 3.0 peer.
                        greetingSend.limit(V3_GREETING_SIZE);
                        greetingSend.position(SIGNATURE_SIZE + 1);
                        greetingSend.put((byte) 0); //  Minor version number
                        outsize += 1;
                        greetingSend.mark();
                        greetingSend.put(new byte[20]);

                        assert (options.mechanism == Mechanisms.NULL || options.mechanism == Mechanisms.PLAIN
                                || options.mechanism == Mechanisms.CURVE || options.mechanism == Mechanisms.GSSAPI);
                        greetingSend.reset();
                        greetingSend.put(options.mechanism.name().getBytes(ZMQ.CHARSET));
                        greetingSend.reset();
                        greetingSend.position(greetingSend.position() + 20);
                        outsize += 20;
                        greetingSend.put(new byte[32]);
                        outsize += 32;

                        greetingSize = V3_GREETING_SIZE;
                    }
                }
            }
            greetingSend.position(outpos);
        }

        //  Is the peer using the unversioned protocol?
        //  If so, we send and receive rest of identity
        //  messages.
        if ((greetingRecv.get(0) & 0xff) != 0xff || (greetingRecv.get(9) & 0x01) == 0) {
            // If this first byte is %FF, then we read nine further bytes,
            // and inspect the last byte (the 10th in total).
            // If the least significant bit is 0, then the other peer is using ZMTP 1.0.
            if (session.zapEnabled()) {
                // reject ZMTP 1.0 connections if ZAP is enabled
                error(ErrorReason.PROTOCOL);
                return false;
            }

            zmtpVersion = Protocol.V0;

            encoder = new V1Encoder(errno, Config.OUT_BATCH_SIZE.getValue());
            decoder = new V1Decoder(errno, Config.IN_BATCH_SIZE.getValue(), options.maxMsgSize, options.allocator);

            //  We have already sent the message header.
            //  Since there is no way to tell the encoder to
            //  skip the message header, we simply throw that
            //  header data away.
            final int headerSize = options.identitySize + 1 >= 255 ? 10 : 2;
            ByteBuffer tmp = ByteBuffer.allocate(headerSize);

            //  Prepare the identity message and load it into encoder.
            //  Then consume bytes we have already sent to the peer.
            Msg txMsg = new Msg(options.identitySize);
            txMsg.put(options.identity, 0, options.identitySize);
            encoder.loadMsg(txMsg);

            ValueReference<ByteBuffer> bufferp = new ValueReference<>(tmp);
            int bufferSize = encoder.encode(bufferp, headerSize);
            assert (bufferSize == headerSize);

            //  Make sure the decoder sees the data we have already received.
            greetingRecv.flip();
            inpos = greetingRecv;
            insize = greetingRecv.limit();

            //  To allow for interoperability with peers that do not forward
            //  their subscriptions, we inject a phantom subscription message
            //  message into the incoming message stream.
            if (options.type == ZMQ.ZMQ_PUB || options.type == ZMQ.ZMQ_XPUB) {
                subscriptionRequired = true;
            }

            //  We are sending our identity now and the next message
            //  will come from the socket.
            nextMsg = pullMsgFromSession;
            //  We are expecting identity message.
            processMsg = identity;

        }
        else if (greetingRecv.get(revisionPos) == Protocol.V1.revision) {
            //  ZMTP/1.0 framing.

            zmtpVersion = Protocol.V1;

            if (session.zapEnabled()) {
                // reject ZMTP 1.0 connections if ZAP is enabled
                error(ErrorReason.PROTOCOL);
                return false;
            }
            encoder = new V1Encoder(errno, Config.OUT_BATCH_SIZE.getValue());
            decoder = new V1Decoder(errno, Config.IN_BATCH_SIZE.getValue(), options.maxMsgSize, options.allocator);
        }
        else if (greetingRecv.get(revisionPos) == Protocol.V2.revision) {
            //  ZMTP/2.0 framing.

            zmtpVersion = Protocol.V2;

            if (session.zapEnabled()) {
                // reject ZMTP 2.0 connections if ZAP is enabled
                error(ErrorReason.PROTOCOL);
                return false;
            }
            encoder = new V2Encoder(errno, Config.OUT_BATCH_SIZE.getValue());
            decoder = new V2Decoder(errno, Config.IN_BATCH_SIZE.getValue(), options.maxMsgSize, options.allocator);
        }
        else {
            zmtpVersion = Protocol.V3;

            encoder = new V2Encoder(errno, Config.OUT_BATCH_SIZE.getValue());
            decoder = new V2Decoder(errno, Config.IN_BATCH_SIZE.getValue(), options.maxMsgSize, options.allocator);

            greetingRecv.position(V2_GREETING_SIZE);
            if (options.mechanism == null) {
                error(ErrorReason.PROTOCOL);
                return false;
            }
            else {
                if (options.mechanism.isMechanism(greetingRecv)) {
                    mechanism = options.mechanism.create(session, peerAddress, options);
                }
                else {
                    error(ErrorReason.PROTOCOL);
                    return false;
                }
            }

            nextMsg = nextHandshakeCommand;
            processMsg = processHandshakeCommand;
        }

        // Start polling for output if necessary.
        if (outsize == 0) {
            ioObject.setPollOut(handle);
        }

        //  Handshaking was successful.
        //  Switch into the normal message flow.
        handshaking = false;

        if (hasHandshakeTimer) {
            ioObject.cancelTimer(HANDSHAKE_TIMER_ID);
            hasHandshakeTimer = false;
        }

        socket.eventHandshaken(endpoint, zmtpVersion.ordinal());

        return true;
    }

    private Msg identityMsg()
    {
        Msg msg = new Msg(options.identitySize);
        if (options.identitySize > 0) {
            msg.put(options.identity, 0, options.identitySize);
        }
        nextMsg = pullMsgFromSession;
        return msg;
    }

    private boolean processIdentityMsg(Msg msg)
    {
        if (options.recvIdentity) {
            msg.setFlags(Msg.IDENTITY);
            boolean rc = session.pushMsg(msg);
            assert (rc);
        }

        if (subscriptionRequired) {
            //  Inject the subscription message, so that also
            //  ZMQ 2.x peers receive published messages.
            Msg subscription = new Msg(1);
            subscription.put((byte) 1);
            boolean rc = session.pushMsg(subscription);
            assert (rc);
        }

        processMsg = pushMsgToSession;

        return true;
    }

    private final MessageProcessor identity = new Identity();

    private Msg nextHandshakeCommand()
    {
        assert (mechanism != null);

        if (mechanism.status() == Mechanism.Status.READY) {
            mechanismReady();

            return pullAndEncode.nextMsg();
        }
        else if (mechanism.status() == Mechanism.Status.ERROR) {
            errno.set(ZError.EPROTO);
            //            error(ErrorReason.PROTOCOL);
            return null;
        }
        else {
            Msg.Builder msg = new Msg.Builder();
            int rc = mechanism.nextHandshakeCommand(msg);
            if (rc == 0) {
                msg.setFlags(Msg.COMMAND);
                return msg.build();
            }
            else {
                errno.set(rc);
                return null;
            }
        }
    }

    private boolean processHandshakeCommand(Msg msg)
    {
        assert (mechanism != null);

        int rc = mechanism.processHandshakeCommand(msg);
        if (rc == 0) {
            if (mechanism.status() == Mechanism.Status.READY) {
                mechanismReady();
            }
            else if (mechanism.status() == Mechanism.Status.ERROR) {
                errno.set(ZError.EPROTO);
                return false;
            }
            if (outputStopped) {
                restartOutput();
            }
        }
        else {
            errno.set(rc);
        }
        return rc == 0;
    }

    private final MessageProcessor processHandshakeCommand = new HandshakeCommand();
    private final MessageProcessor nextHandshakeCommand    = processHandshakeCommand;

    @Override
    public void zapMsgAvailable()
    {
        assert (mechanism != null);

        int rc = mechanism.zapMsgAvailable();
        if (rc == -1) {
            error(ErrorReason.PROTOCOL);
            return;
        }
        if (inputStopped) {
            restartInput();
        }
        if (outputStopped) {
            restartOutput();
        }
    }

    private void mechanismReady()
    {
        if (options.recvIdentity) {
            Msg identity = mechanism.peerIdentity();
            boolean rc = session.pushMsg(identity);
            if (!rc && errno.is(ZError.EAGAIN)) {
                // If the write is failing at this stage with
                // an EAGAIN the pipe must be being shut down,
                // so we can just bail out of the identity set.
                return;
            }
            assert (rc);
            session.flush();
        }

        nextMsg = pullAndEncode;
        processMsg = writeCredential;

        //  Compile metadata.
        assert (metadata == null);

        metadata = new Metadata();

        //  If we have a peer_address, add it to metadata
        if (peerAddress != null && !peerAddress.address().isEmpty()) {
            metadata.set(Metadata.PEER_ADDRESS, peerAddress.address());
        }
        //  Add ZAP properties.
        metadata.set(mechanism.zapProperties);

        //  Add ZMTP properties.
        metadata.set(mechanism.zmtpProperties);

        if (metadata.isEmpty()) {
            metadata = null;
        }

    }

    private Msg pullMsgFromSession()
    {
        return session.pullMsg();
    }

    private boolean pushMsgToSession(Msg msg)
    {
        return session.pushMsg(msg);
    }

    private final MessageProcessor pushMsgToSession   = new PushMsgToSession();
    private final MessageProcessor pullMsgFromSession = pushMsgToSession;

    private boolean pushRawMsgToSession(Msg msg)
    {
        if (metadata != null && !metadata.equals(msg.getMetadata())) {
            msg.setMetadata(metadata);
        }
        return pushMsgToSession(msg);
    }

    private final MessageProcessor pushRawMsgToSession = new PushRawMsgToSession();

    private boolean writeCredential(Msg msg)
    {
        assert (mechanism != null);
        assert (session != null);

        Blob credential = mechanism.getUserId();
        if (credential != null && credential.size() > 0) {
            Msg cred = new Msg(credential.size());
            cred.put(credential.data(), 0, credential.size());
            cred.setFlags(Msg.CREDENTIAL);

            boolean rc = session.pushMsg(cred);
            if (!rc) {
                return false;
            }
        }
        processMsg = decodeAndPush;
        return decodeAndPush.processMsg(msg);
    }

    private final MessageProcessor writeCredential = new WriteCredential();

    private final MessageProcessor pullAndEncode = new PullAndEncode();

    private final MessageProcessor decodeAndPush = new DecodeAndPush();

    private final MessageProcessor pushOneThenDecodeAndPush = new PushOneThenDecodeAndPush();

    //  Function to handle network disconnections.
    private void error(ErrorReason error)
    {
        if (options.rawSocket) {
            //  For raw sockets, send a final 0-length message to the application
            //  so that it knows the peer has been disconnected.
            Msg terminator = new Msg();
            processMsg.processMsg(terminator);
        }
        assert (session != null);
        socket.eventDisconnected(endpoint, fd);
        session.flush();
        session.engineError(error);
        unplug();
        destroy();
    }

    private void setHandshakeTimer()
    {
        assert (!hasHandshakeTimer);

        if (!options.rawSocket && options.handshakeIvl > 0) {
            ioObject.addTimer(options.handshakeIvl, HANDSHAKE_TIMER_ID);
            hasHandshakeTimer = true;
        }
    }

    @Override
    public void timerEvent(int id)
    {
        assert (id == HANDSHAKE_TIMER_ID);
        hasHandshakeTimer = false;

        //  handshake timer expired before handshake completed, so engine fails
        error(ErrorReason.TIMEOUT);
    }

    //  Writes data to the socket. Returns the number of bytes actually
    //  written (even zero is to be considered to be a success). In case
    //  of error or orderly shutdown by the other peer -1 is returned.
    private int write(ByteBuffer outbuf)
    {
        int nbytes;
        try {
            nbytes = fd.write(outbuf);
            if (nbytes == 0) {
                errno.set(ZError.EAGAIN);
            }
        }
        catch (IOException e) {
            errno.set(ZError.ENOTCONN);
            nbytes = -1;
        }

        return nbytes;
    }

    //  Reads data from the socket (up to 'size' bytes).
    //  Returns the number of bytes actually read or -1 on error.
    //  Zero indicates the peer has closed the connection.
    private int read(ByteBuffer buf)
    {
        int nbytes;
        try {
            nbytes = fd.read(buf);
            if (nbytes == -1) {
                errno.set(ZError.ENOTCONN);
            }
            else if (nbytes == 0) {
                if (!fd.isBlocking()) {
                    //  If not a single byte can be read from the socket in non-blocking mode
                    //  we'll get an error (this may happen during the speculative read).

                    //  Several errors are OK. When speculative read is being done we may not
                    //  be able to read a single byte from the socket. Also, SIGSTOP issued
                    //  by a debugging tool can result in EINTR error.
                    errno.set(ZError.EAGAIN);
                    nbytes = -1;
                }
            }
        }
        catch (IOException e) {
            errno.set(ZError.ENOTCONN);
            nbytes = -1;
        }

        return nbytes;
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
    public String toString()
    {
        return getClass().getSimpleName() + socket + "-" + zmtpVersion;
    }
}
