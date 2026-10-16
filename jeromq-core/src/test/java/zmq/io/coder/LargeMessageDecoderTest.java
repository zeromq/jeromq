package zmq.io.coder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import zmq.Msg;
import zmq.io.coder.IDecoder.Step;
import zmq.io.coder.v1.V1Decoder;
import zmq.io.coder.v1.V1Protocol;
import zmq.io.coder.v2.V2Decoder;
import zmq.io.coder.v2.V2Protocol;
import zmq.msg.MsgAllocator;
import zmq.msg.MsgAllocatorHeap;
import zmq.util.Errno;
import zmq.util.ValueReference;
import zmq.util.Wire;

public class LargeMessageDecoderTest
{
    private static final int INITIAL_ALLOCATION_LIMIT = 1024 * 1024;

    // records the sizes of the messages it allocates
    private static final class RecordingAllocator implements MsgAllocator
    {
        private final MsgAllocator underlying = new MsgAllocatorHeap();
        final List<Integer>        sizes      = new ArrayList<>();

        @Override
        public Msg allocate(int size)
        {
            sizes.add(size);
            return underlying.allocate(size);
        }

        long total()
        {
            long total = 0;
            for (int size : sizes) {
                total += size;
            }
            return total;
        }
    }

    private static byte[] payload(int size)
    {
        byte[] payload = new byte[size];
        for (int i = 0; i < size; i++) {
            payload[i] = (byte) (i * 31 + i / 7);
        }
        return payload;
    }

    private static ByteBuffer v2Frame(byte[] payload, long announcedSize, boolean more)
    {
        ByteBuffer frame = ByteBuffer.allocate(9 + payload.length);
        frame.put((byte) (V2Protocol.LARGE_FLAG | (more ? V2Protocol.MORE_FLAG : 0)));
        Wire.putUInt64(frame, announcedSize);
        frame.put(payload);
        frame.flip();
        return frame;
    }

    private static ByteBuffer v1Frame(byte[] payload, boolean more)
    {
        ByteBuffer frame = ByteBuffer.allocate(10 + payload.length);
        frame.put((byte) 0xff);
        Wire.putUInt64(frame, payload.length + 1L);
        frame.put((byte) (more ? V1Protocol.MORE_FLAG : 0));
        frame.put(payload);
        frame.flip();
        return frame;
    }

    // feeds wire to the decoder by chunks of at most chunkSize bytes, like StreamEngine does,
    // and returns the decoded messages
    private static List<Msg> feed(DecoderBase decoder, ByteBuffer wire, int chunkSize)
    {
        List<Msg> decoded = new ArrayList<>();
        ValueReference<Integer> processed = new ValueReference<>(0);
        while (wire.hasRemaining()) {
            ByteBuffer in = decoder.getBuffer();
            int size = Math.min(Math.min(in.remaining(), chunkSize), wire.remaining());
            ByteBuffer chunk = wire.duplicate();
            chunk.limit(chunk.position() + size);
            in.put(chunk);
            wire.position(wire.position() + size);
            in.flip();

            int done = 0;
            while (done < size) {
                Step.Result result = decoder.decode(in, size - done, processed);
                assertThat(result == Step.Result.ERROR, is(false));
                done += processed.get();
                if (result == Step.Result.DECODED) {
                    decoded.add(decoder.msg());
                }
            }
        }
        return decoded;
    }

    @Test
    public void testAnnouncedSizeIsNotAllocatedUpfront()
    {
        RecordingAllocator allocator = new RecordingAllocator();
        Decoder decoder = new V2Decoder(new Errno(), 8192, -1, allocator, INITIAL_ALLOCATION_LIMIT);

        // announces a 1 GB message, but only sends a few bytes of it
        List<Msg> decoded = feed(decoder, v2Frame(payload(100), 1L << 30, false), 8192);

        assertThat(decoded.size(), is(0));
        assertThat(allocator.total(), is((long) INITIAL_ALLOCATION_LIMIT));
    }

    @Test
    public void testAllocationsFollowReceivedData()
    {
        RecordingAllocator allocator = new RecordingAllocator();
        Decoder decoder = new V2Decoder(new Errno(), 8192, -1, allocator, INITIAL_ALLOCATION_LIMIT);

        // announces a 1 GB message, but only sends 3 MB of it
        int sent = 3 * 1024 * 1024;
        List<Msg> decoded = feed(decoder, v2Frame(payload(sent), 1L << 30, false), 65536);

        assertThat(decoded.size(), is(0));
        int largest = 0;
        for (int size : allocator.sizes) {
            largest = Math.max(largest, size);
        }
        assertThat(largest <= 2L * sent, is(true));
    }

    @Test
    public void testV2LargeMessages()
    {
        RecordingAllocator allocator = new RecordingAllocator();
        Decoder decoder = new V2Decoder(new Errno(), 8192, -1, allocator, INITIAL_ALLOCATION_LIMIT);

        int largeSize = 5 * INITIAL_ALLOCATION_LIMIT + 123;
        byte[] large = payload(largeSize);
        byte[] exact = payload(INITIAL_ALLOCATION_LIMIT);
        byte[] small = payload(42);

        ByteBuffer wire = ByteBuffer.allocate(3 * 9 + largeSize + exact.length + small.length);
        wire.put(v2Frame(large, large.length, true));
        wire.put(v2Frame(exact, exact.length, true));
        wire.put(v2Frame(small, small.length, false));
        wire.flip();

        for (int chunkSize : new int[] { 1000, 8192, 65536, 3 * 1024 * 1024 }) {
            List<Msg> decoded = feed(decoder, wire.duplicate(), chunkSize);

            assertThat(decoded.size(), is(3));
            assertThat(decoded.get(0).size(), is(largeSize));
            assertThat(decoded.get(0).data(), is(large));
            assertThat(decoded.get(0).hasMore(), is(true));
            assertThat(decoded.get(1).data(), is(exact));
            assertThat(decoded.get(1).hasMore(), is(true));
            assertThat(decoded.get(2).data(), is(small));
            assertThat(decoded.get(2).hasMore(), is(false));
        }
    }

    @Test
    public void testV1LargeMessage()
    {
        RecordingAllocator allocator = new RecordingAllocator();
        Decoder decoder = new V1Decoder(new Errno(), 8192, -1, allocator, INITIAL_ALLOCATION_LIMIT);

        int largeSize = 3 * INITIAL_ALLOCATION_LIMIT + 17;
        byte[] large = payload(largeSize);
        byte[] small = payload(5);

        ByteBuffer wire = ByteBuffer.allocate(2 * 10 + largeSize + small.length);
        wire.put(v1Frame(large, true));
        wire.put(v1Frame(small, false));
        wire.flip();

        List<Msg> decoded = feed(decoder, wire, 65536);

        assertThat(decoded.size(), is(2));
        assertThat(decoded.get(0).data(), is(large));
        assertThat(decoded.get(0).hasMore(), is(true));
        assertThat(decoded.get(1).data(), is(small));
        assertThat(decoded.get(1).hasMore(), is(false));
    }

    @Test
    public void testAnnouncedSizeIsAllocatedUpfrontByDefault()
    {
        RecordingAllocator allocator = new RecordingAllocator();
        Decoder decoder = new V2Decoder(new Errno(), 8192, -1, allocator);

        // announces a 5 MB message, but only sends a few bytes of it
        int announced = 5 * 1024 * 1024;
        List<Msg> decoded = feed(decoder, v2Frame(payload(100), announced, false), 8192);

        assertThat(decoded.size(), is(0));
        assertThat(allocator.total(), is((long) announced));
    }

    @Test
    public void testMaxMsgSizeStillEnforced()
    {
        Decoder decoder = new V2Decoder(new Errno(), 8192, 1000, new MsgAllocatorHeap(), INITIAL_ALLOCATION_LIMIT);

        ByteBuffer in = decoder.getBuffer();
        in.put(v2Frame(new byte[0], 1001, false));
        in.flip();

        Step.Result result = decoder.decode(in, 9, new ValueReference<>(0));

        assertThat(result, is(Step.Result.ERROR));
    }
}
