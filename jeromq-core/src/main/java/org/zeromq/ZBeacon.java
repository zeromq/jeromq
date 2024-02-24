package org.zeromq;

import java.io.IOException;
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

/**
 * This class implements a peer-to-peer discovery service for local networks.
 * A beacon can broadcast and/or capture service announcements using UDP messages
 * on the local area network. This implementation uses IPv4 UDP broadcasts. You can
 * define the format of your outgoing beacons, and set a filter that validates incoming
 * beacons. Beacons are sent and received asynchronously in the background.
 *
 */
public class ZBeacon
{
    public static final long    DEFAULT_BROADCAST_INTERVAL = 1000L;
    public static final String  DEFAULT_BROADCAST_HOST     = "255.255.255.255"; // is this the source/interface address? or the broadcast address
    private static final InetAddress DEFAULT_BROADCAST_HOST_ADDRESS;
    private static final InetAddress DEFAULT_BROADCAST_ADDRESS;
    static {
        try {
            DEFAULT_BROADCAST_HOST_ADDRESS = InetAddress.getByName(DEFAULT_BROADCAST_HOST);
            DEFAULT_BROADCAST_ADDRESS = InetAddress.getByName("0.0.0.0");
        }
        catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid default broadcast address", e);
        }
    }

    private final BroadcastClient           broadcastClient;
    private final BroadcastServer           broadcastServer;
    private final AtomicReference<byte[]>   prefix = new AtomicReference<>();
    private final AtomicReference<byte[]>   beacon = new AtomicReference<>();
    private final AtomicLong                broadcastInterval = new AtomicLong(DEFAULT_BROADCAST_INTERVAL);
    private final AtomicReference<Listener> listener          = new AtomicReference<>();
    private final AtomicReference<Thread.UncaughtExceptionHandler> clientExHandler = new AtomicReference<>();
    private final AtomicReference<Thread.UncaughtExceptionHandler> serverExHandler = new AtomicReference<>();

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
        this(host, DEFAULT_BROADCAST_ADDRESS.getAddress(), port, beacon, DEFAULT_BROADCAST_INTERVAL, ignoreLocalAddress, blocking);
    }

    public ZBeacon(InetAddress broadcastAddress, InetAddress serverAddress, int port, byte[] beacon, long broadcastInterval, boolean ignoreLocalAddress, boolean blocking)
    {
        Objects.requireNonNull(broadcastAddress, "Host cannot be null");
        Objects.requireNonNull(serverAddress, "Server address cannot be null");
        Objects.requireNonNull(beacon, "Beacon cannot be null");
        this.broadcastInterval.set(broadcastInterval);
        this.beacon.set(Arrays.copyOf(beacon, beacon.length));
        broadcastServer = new BroadcastServer(port, ignoreLocalAddress);
        broadcastClient = new BroadcastClient(serverAddress, broadcastAddress, port, this.broadcastInterval);
    }

    @Deprecated
    public ZBeacon(String broadcastAddress, byte[] serverAddress, int port, byte[] beacon, long broadcastInterval, boolean ignoreLocalAddress, boolean blocking)
    {
        Objects.requireNonNull(broadcastAddress, "Host cannot be null");
        Objects.requireNonNull(serverAddress, "Server address cannot be null");
        Objects.requireNonNull(beacon, "Beacon cannot be null");
        this.broadcastInterval.set(broadcastInterval);
        this.beacon.set(Arrays.copyOf(beacon, beacon.length));
        broadcastServer = new BroadcastServer(port, ignoreLocalAddress);
        try {
            broadcastClient = new BroadcastClient(InetAddress.getByAddress(serverAddress), InetAddress.getByName(broadcastAddress), port, this.broadcastInterval);
        }
        catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid server address", e);
        }
    }

    public static class Builder
    {
        private InetAddress  clientHost    = DEFAULT_BROADCAST_HOST_ADDRESS;
        private InetAddress  serverAddr    = DEFAULT_BROADCAST_ADDRESS;
        private int     port;
        private long    broadcastInterval  = DEFAULT_BROADCAST_INTERVAL;
        private byte[]  beacon;
        private boolean ignoreLocalAddress = true;
        private boolean blocking           = false;
        // Those arguments are not set using the constructor, so they are optional for backward compatibility
        private Listener listener          = null;
        private byte[]  prefix             = null;
        private Thread.UncaughtExceptionHandler clientExHandler = null;
        private Thread.UncaughtExceptionHandler serverExHandler = null;

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

        @Deprecated
        public Builder client(String host)
        {
            try {
                this.clientHost = InetAddress.getByName(host);
            }
            catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid server address", e);
            }
            return this;
        }

        public Builder client(InetAddress host)
        {
            this.clientHost = host;
            return this;
        }

        @Deprecated
        public Builder server(byte[] addr)
        {
            Utils.checkArgument(addr.length == 4 || addr.length == 16, "Server Address has to be 4 or 16 bytes long");
            try {
                this.serverAddr = InetAddress.getByAddress(addr);
            }
            catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid server address", e);
            }
            return this;
        }

        public Builder server(InetAddress addr)
        {
            this.serverAddr = addr;
            return this;
        }

        public Builder ignoreLocalAddress(boolean ignoreLocalAddress)
        {
            this.ignoreLocalAddress = ignoreLocalAddress;
            return this;
        }

        /**
         * @deprecated ignored
         * @param blocking
         * @return
         */
        @Deprecated
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

        public Builder listener(Listener listener)
        {
            this.listener = listener;
            return this;
        }

        public Builder prefix(byte[] prefix)
        {
            this.prefix = Arrays.copyOf(prefix, prefix.length);
            return this;
        }

        public Builder setClientUncaughtExceptionHandlers(Thread.UncaughtExceptionHandler clientExHandler)
        {
            this.clientExHandler = clientExHandler;
            return this;
        }

        public Builder setServerUncaughtExceptionHandlers(Thread.UncaughtExceptionHandler serverExHandler)
        {
            this.serverExHandler = serverExHandler;
            return this;
        }

        public ZBeacon build()
        {
            ZBeacon zbeacon = new ZBeacon(clientHost, serverAddr, port, beacon, broadcastInterval, ignoreLocalAddress, blocking);
            if (listener != null) {
                zbeacon.setListener(listener);
            }
            if (prefix != null) {
                zbeacon.setPrefix(prefix);
            }
            if (this.serverExHandler != null) {
                zbeacon.serverExHandler.set(this.serverExHandler);
            }
            if (this.clientExHandler != null) {
                zbeacon.clientExHandler.set(this.clientExHandler);
            }
            return zbeacon;
        }
    }

    /**
     * @deprecated use the builder
     * @param clientExHandler
     * @param serverExHandler
     */
    @Deprecated
    public void setUncaughtExceptionHandlers(Thread.UncaughtExceptionHandler clientExHandler,
                                             Thread.UncaughtExceptionHandler serverExHandler)
    {
        this.clientExHandler.set(clientExHandler);
        this.serverExHandler.set(serverExHandler);
    }

    public void startClient()
    {
        if (!broadcastClient.isRunning) {
            if (broadcastClient.thread == null) {
                broadcastClient.thread = new Thread(broadcastClient);
                broadcastClient.thread.setName("ZBeacon Client Thread");
                broadcastClient.thread.setDaemon(true);
                broadcastClient.thread.setUncaughtExceptionHandler(clientExHandler.get());
            }
            broadcastClient.thread.start();
        }
    }

    public void startServer()
    {
        if (!broadcastServer.isRunning) {
            if (listener.get() != null) {
                if (broadcastServer.thread == null) {
                    broadcastServer.thread = new Thread(broadcastServer);
                    broadcastServer.thread.setName("ZBeacon Server Thread");
                    broadcastServer.thread.setDaemon(true);
                    broadcastServer.thread.setUncaughtExceptionHandler(serverExHandler.get());
                }
                broadcastServer.thread.start();
            }
        }
    }

    public void start()
    {
        startClient();
        startServer();
    }

    public void stop() throws InterruptedException
    {
        if (broadcastClient.thread != null) {
            broadcastClient.thread.interrupt();
            broadcastClient.thread.join();
        }
        if (broadcastServer.thread != null) {
            broadcastServer.thread.interrupt();
            broadcastServer.thread.join();
        }
    }

    /**
     * @deprecated use the builder
     * @param beacon
     */
    @Deprecated
    public void setBeacon(byte[] beacon)
    {
        this.beacon.set(Arrays.copyOf(beacon, beacon.length));
    }

    public byte[] getBeacon()
    {
        byte[] beaconBuffer = beacon.get();
        return Arrays.copyOf(beaconBuffer, beaconBuffer.length);
    }

    /**
     * @deprecated use the builder
     * @param prefix
     */
    @Deprecated
    public void setPrefix(byte[] prefix)
    {
        this.prefix.set(Arrays.copyOf(prefix, prefix.length));
    }

    public byte[] getPrefix()
    {
        byte[] prefixBuffer = prefix.get();
        return Arrays.copyOf(prefixBuffer, prefixBuffer.length);
    }

    /**
     * @deprecated use the builder
     * @param listener
     */
    @Deprecated
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
    private class BroadcastClient implements Runnable
    {
        private final InetSocketAddress broadcastAddress;
        private final InetAddress       interfaceAddress;
        private final AtomicLong        broadcastInterval;
        private boolean                 isRunning;
        private Thread                  thread;

        public BroadcastClient(InetAddress interfaceAddress, InetAddress broadcastAddress, int port, AtomicLong broadcastInterval)
        {
            this.broadcastInterval = broadcastInterval;
            this.broadcastAddress = new InetSocketAddress(broadcastAddress, port);
            this.interfaceAddress = interfaceAddress;
        }

        @Override
        public void run()
        {
            try (DatagramChannel broadcastChannel = DatagramChannel.open()) {
                broadcastChannel.socket().setBroadcast(true);
                broadcastChannel.socket().setReuseAddress(true);
                broadcastChannel.socket().bind(new InetSocketAddress(interfaceAddress, 0));
                broadcastChannel.connect(broadcastAddress);

                isRunning = true;
                while (!Thread.interrupted() && isRunning) {
                    try {
                        broadcastChannel.send(ByteBuffer.wrap(beacon.get()), broadcastAddress);
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
                isRunning = false;
                thread = null;
            }
        }

    }

    /**
     * The broadcast server receives beacons.
     */
    private class BroadcastServer implements Runnable
    {
        private final DatagramChannel handle;            // Socket for send/recv
        private final boolean         ignoreLocalAddress;
        private Thread                thread;
        private boolean               isRunning;

        public BroadcastServer(int port, boolean ignoreLocalAddress)
        {
            this.ignoreLocalAddress = ignoreLocalAddress;
            try {
                // Create UDP socket
                handle = DatagramChannel.open();
                handle.configureBlocking(true);
                handle.socket().setReuseAddress(true);
                handle.socket().bind(new InetSocketAddress(port));
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
            isRunning = true;
            try {
                while (!Thread.interrupted() && isRunning) {
                    buffer.clear();
                    try {
                        sender = handle.receive(buffer);
                        InetAddress senderAddress = ((InetSocketAddress) sender).getAddress();

                        if (ignoreLocalAddress
                                && (InetAddress.getLocalHost().getHostAddress().equals(senderAddress.getHostAddress())
                                || senderAddress.isAnyLocalAddress() || senderAddress.isLoopbackAddress())) {
                            continue;
                        }

                        handleMessage(buffer, senderAddress);
                    }
                    catch (ClosedChannelException ioException) {
                        break;
                    }
                    catch (IOException ioException) {
                        isRunning = false;
                        throw new RuntimeException(ioException);
                    }
                }
            }
            finally {
                handle.socket().close();
                isRunning = false;
                thread = null;
            }
        }

        private void handleMessage(ByteBuffer buffer, InetAddress from)
        {
            byte[] prefix = ZBeacon.this.prefix.get();
            if (buffer.remaining() < prefix.length) {
                return;
            }
            buffer.flip();
            buffer.mark();
            byte[] prefixTry = new byte[prefix.length];
            buffer.get(prefixTry);
            if (Arrays.equals(prefix, prefixTry)) {
                buffer.reset();
                byte[] content = new byte[buffer.remaining()];
                buffer.get(content);
                listener.get().onBeacon(from, content);
           }
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
