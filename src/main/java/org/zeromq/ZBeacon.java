package org.zeromq;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import zmq.util.Objects;

public class ZBeacon
{
    public static final long    DEFAULT_BROADCAST_INTERVAL = 1000L;
    public static final String  DEFAULT_BROADCAST_HOST     = "255.255.255.255";
    private static final byte[] DEFAULT_BRODACAST_ADDRESS  = { 0, 0, 0, 0 };

    private final BroadcastClient           broadcastClient;
    private final BroadcastServer           broadcastServer;
    private final AtomicReference<byte[]>   prefix            = new AtomicReference<>(new byte[0]);
    private final AtomicReference<byte[]>   beacon            = new AtomicReference<>(new byte[0]);
    private final AtomicLong                broadcastInterval = new AtomicLong(DEFAULT_BROADCAST_INTERVAL);
    private final AtomicReference<Listener> listener          = new AtomicReference<>();

    public ZBeacon(int port, byte[] beacon)
    {
        this(DEFAULT_BROADCAST_HOST, port, beacon);
    }

    public ZBeacon(String host, int port, byte[] beacon)
    {
        this(host, port, beacon, true);
    }

    public ZBeacon(String host, int port, byte[] beacon, boolean ignoreLocalAddress)
    {
        this(host, port, beacon, ignoreLocalAddress, false);
    }

    public ZBeacon(String host, int port, byte[] beacon, boolean ignoreLocalAddress, boolean blocking)
    {
        this(host, DEFAULT_BRODACAST_ADDRESS, port, beacon, DEFAULT_BROADCAST_INTERVAL, ignoreLocalAddress, blocking);
    }

    private ZBeacon(String host, byte[] serverAddress, int port, byte[] beacon, long broadcastInterval,
                    boolean ignoreLocalAddress, boolean blocking)
    {
        Objects.requireNonNull(host, "Host cannot be null");
        Objects.requireNonNull(serverAddress, "Server address cannot be null");
        Objects.requireNonNull(beacon, "Beacon cannot be null");
        this.broadcastInterval.set(broadcastInterval);
        broadcastServer = new BroadcastServer(serverAddress, port, ignoreLocalAddress, blocking);
        broadcastServer.setDaemon(true);
        broadcastClient = new BroadcastClient(host, port, this.broadcastInterval);
        broadcastClient.setDaemon(true);
    }

    public static class Builder
    {
        private String  clientHost         = DEFAULT_BROADCAST_HOST;
        private byte[]  serverAddr         = DEFAULT_BRODACAST_ADDRESS;
        private int     port;
        private long    broadcastInterval  = DEFAULT_BROADCAST_INTERVAL;
        private byte[]  beacon;
        private boolean ignoreLocalAddress = true;
        private boolean blocking           = false;

        public Builder port(int port)
        {
            this.port = port;
            return this;
        }

        public Builder beacon(byte[] beacon)
        {
            this.beacon = beacon;
            return this;
        }

        public Builder client(String host)
        {
            this.clientHost = host;
            return this;
        }

        public Builder server(byte[] addr)
        {
            Utils.checkArgument(addr.length == 4 || addr.length == 16, "Server Address has to be 4 or 16 bytes long");
            this.serverAddr = addr;
            return this;
        }

        public Builder ignoreLocalAddress(boolean ignoreLocalAddress)
        {
            this.ignoreLocalAddress = ignoreLocalAddress;
            return this;
        }

        public Builder blocking(boolean blocking)
        {
            this.blocking = blocking;
            return this;
        }

        public Builder broadcastInterval(long broadcastInterval)
        {
            this.broadcastInterval = broadcastInterval;
            return this;
        }

        public ZBeacon build()
        {
            return new ZBeacon(clientHost, serverAddr, port, beacon, broadcastInterval, ignoreLocalAddress, blocking);
        }
    }

    public void setUncaughtExceptionHandlers(Thread.UncaughtExceptionHandler clientHandler,
                                             Thread.UncaughtExceptionHandler serverHandler)
    {
        broadcastClient.setUncaughtExceptionHandler(clientHandler);
        broadcastServer.setUncaughtExceptionHandler(serverHandler);
    }

    public void start()
    {
        if (listener.get() != null) {
            broadcastServer.start();
        }
        broadcastClient.start();
    }

    public void stop() throws InterruptedException
    {
        if (broadcastClient != null) {
            broadcastClient.interrupt();
            broadcastClient.join();
        }
        if (broadcastServer != null) {
            broadcastServer.interrupt();
            broadcastServer.join();
        }
    }

    public void setBeacon(byte[] beacon)
    {
        this.beacon.set(beacon);
    }

    public byte[] getBeacon()
    {
        return beacon.get();
    }

    public void setPrefix(byte[] prefix)
    {
        this.prefix.set(prefix);
    }

    public byte[] getPrefix()
    {
        return prefix.get();
    }

    public void setListener(Listener listener)
    {
        this.listener.set(listener);
    }

    public Listener getListener()
    {
        return listener.get();
    }

    /**
     * All beacons with matching prefix are passed to a listener.
     */
    public interface Listener
    {
        void onBeacon(InetAddress sender, byte[] beacon);
    }

    /**
     * The broadcast client periodically sends beacons via UDP to the network.
     */
    private class BroadcastClient extends Thread
    {
        private DatagramChannel         broadcastChannel;
        private final InetSocketAddress broadcastInetSocketAddress;
        private final AtomicLong        broadcastInterval;

        public BroadcastClient(String host, int port, AtomicLong broadcastInterval)
        {
            this.broadcastInterval = broadcastInterval;
            try {
                broadcastInetSocketAddress = new InetSocketAddress(InetAddress.getByName(host), port);
            }
            catch (UnknownHostException unknownHostException) {
                throw new RuntimeException(unknownHostException);
            }
        }

        @Override
        public void run()
        {
            try {
                broadcastChannel = DatagramChannel.open();
                broadcastChannel.socket().setBroadcast(true);
                while (!interrupted()) {
                    try {
                        broadcastChannel.send(ByteBuffer.wrap(beacon.get()), broadcastInetSocketAddress);
                        Thread.sleep(broadcastInterval.get());
                    }
                    catch (InterruptedException | ClosedByInterruptException interruptedException) {
                        // Re-interrupt the thread so the caller can handle it.
                        Thread.currentThread().interrupt();
                        break;
                    }
                    catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }
            }
            catch (IOException ioException) {
                throw new RuntimeException(ioException);
            }
            finally {
                try {
                    broadcastChannel.close();
                }
                catch (IOException ioException) {
                    throw new RuntimeException(ioException);
                }
            }
        }

    }

    /**
     * The broadcast server receives beacons.
     */
    private class BroadcastServer extends Thread
    {
        private final DatagramChannel handle;            // Socket for send/recv
        private final boolean         ignoreLocalAddress;

        public BroadcastServer(byte[] serverAddress, int port, boolean ignoreLocalAddress, boolean blocking)
        {
            this.ignoreLocalAddress = ignoreLocalAddress;
            try {
                // Create UDP socket
                handle = DatagramChannel.open();
                handle.configureBlocking(blocking);
                DatagramSocket sock = handle.socket();
                sock.setReuseAddress(true);
                sock.bind(new InetSocketAddress(InetAddress.getByAddress(serverAddress), port));
            }
            catch (IOException ioException) {
                throw new RuntimeException(ioException);
            }
        }

        @Override
        public void run()
        {
            ByteBuffer buffer = ByteBuffer.allocate(65535);
            SocketAddress sender;
            int size;
            while (!interrupted()) {
                buffer.clear();
                try {
                    int read = buffer.remaining();
                    sender = handle.receive(buffer);
                    if (sender == null) {
                        continue;
                    }

                    InetAddress senderAddress = ((InetSocketAddress) sender).getAddress();

                    if (ignoreLocalAddress
                            && (InetAddress.getLocalHost().getHostAddress().equals(senderAddress.getHostAddress())
                                    || senderAddress.isAnyLocalAddress() || senderAddress.isLoopbackAddress())) {
                        continue;
                    }

                    size = read - buffer.remaining();
                    handleMessage(buffer, size, senderAddress);
                }
                catch (ClosedChannelException ioException) {
                    break;
                }
                catch (IOException ioException) {
                    throw new RuntimeException(ioException);
                }
            }
            handle.socket().close();
        }

        private void handleMessage(ByteBuffer buffer, int size, InetAddress from)
        {
            byte[] prefix = ZBeacon.this.prefix.get();
            if (size < prefix.length) {
                return;
            }
            ByteBuffer buf = buffer.duplicate();
            buf.position(0);
            for (int i = 0; i < prefix.length; i++) {
                if (buf.get() != prefix[i]) {
                    return;
                }
            }
            // prefix matched
            listener.get().onBeacon(from, Arrays.copyOf(buffer.array(), size));
        }
    }

    public long getBroadcastInterval()
    {
        return broadcastInterval.get();
    }

    public void setBroadcastInterval(long broadcastInterval)
    {
        this.broadcastInterval.set(broadcastInterval);
    }
}
