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

public class ZBeacon
{
    public static final long    DEFAULT_BROADCAST_INTERVAL = 1000L;
    public static final String  DEFAULT_BROADCAST_HOST     = "255.255.255.255"; // is this the source/interface address? or the broadcast address
    private static final byte[] DEFAULT_BROADCAST_ADDRESS = { 0, 0, 0, 0 };

    private final BroadcastClient           broadcastClient;
    private final BroadcastServer           broadcastServer;
    private final AtomicReference<byte[]>   prefix            = new AtomicReference<>(new byte[0]);
    private final AtomicReference<byte[]>   beacon            = new AtomicReference<>(new byte[0]);
    private final AtomicLong                broadcastInterval = new AtomicLong(DEFAULT_BROADCAST_INTERVAL);
    private final AtomicReference<Listener> listener          = new AtomicReference<>();
    private AtomicReference<Thread.UncaughtExceptionHandler> clientHandler = new AtomicReference<>();
    private AtomicReference<Thread.UncaughtExceptionHandler> serverHandler = new AtomicReference<>();

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
        this(host, DEFAULT_BROADCAST_ADDRESS, port, beacon, DEFAULT_BROADCAST_INTERVAL, ignoreLocalAddress, blocking);
    }

    public ZBeacon(String broadcastAddress, byte[] serverAddress, int port, byte[] beacon, long broadcastInterval, boolean ignoreLocalAddress, boolean blocking)
    {
        Objects.requireNonNull(broadcastAddress, "Host cannot be null");
        Objects.requireNonNull(serverAddress, "Server address cannot be null");
        Objects.requireNonNull(beacon, "Beacon cannot be null");
        this.broadcastInterval.set(broadcastInterval);
        this.beacon.set(beacon);
        broadcastServer = new BroadcastServer(serverAddress, port, ignoreLocalAddress, blocking);
        broadcastClient = new BroadcastClient(serverAddress, broadcastAddress, port, this.broadcastInterval);
    }

    public static class Builder
    {
        private String  clientHost         = DEFAULT_BROADCAST_HOST;
        private byte[]  serverAddr         = DEFAULT_BROADCAST_ADDRESS;
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
        this.clientHandler.set(clientHandler);
        this.serverHandler.set(serverHandler);
    }

    public void startClient()
    {
        if (!broadcastClient.isRunning) {
            if (broadcastClient.thread == null) {
                broadcastClient.thread = new Thread(broadcastClient);
                broadcastClient.thread.setName("ZBeacon Client Thread");
                broadcastClient.thread.setDaemon(true);
                broadcastClient.thread.setUncaughtExceptionHandler(clientHandler.get());
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
                    broadcastServer.thread.setUncaughtExceptionHandler(serverHandler.get());
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
    private class BroadcastClient implements Runnable
    {
        private DatagramChannel         broadcastChannel;
        private final InetSocketAddress broadcastAddress;
        private final InetAddress interfaceAddress;
        private final AtomicLong        broadcastInterval;
        private boolean                 isRunning;
        private Thread                  thread;

        public BroadcastClient(byte[] interfaceAddress, String broadcastAddress, int port, AtomicLong broadcastInterval)
        {
            this.broadcastInterval = broadcastInterval;
            try {
                this.broadcastAddress = new InetSocketAddress(InetAddress.getByName(broadcastAddress), port);
                this.interfaceAddress = InetAddress.getByAddress(interfaceAddress);
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
                //broadcastChannel.setOption(StandardSocketOptions.SO_BROADCAST, true);
                //broadcastChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                //broadcastChannel.setOption(StandardSocketOptions.SO_REUSEPORT, true);
                broadcastChannel.socket().setBroadcast(true);
                broadcastChannel.socket().setReuseAddress(true);
                broadcastChannel.socket().bind(new InetSocketAddress(interfaceAddress, 0));
                broadcastChannel.connect(broadcastAddress);

                isRunning = true;
                while (!thread.interrupted() && isRunning) {
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
    private class BroadcastServer implements Runnable
    {
        private final DatagramChannel handle;            // Socket for send/recv
        private final boolean         ignoreLocalAddress;
        private Thread                thread;
        private boolean               isRunning;

        public BroadcastServer(byte[] serverAddress, int port, boolean ignoreLocalAddress, boolean blocking)
        {
            this.ignoreLocalAddress = ignoreLocalAddress;
            try {
                // Create UDP socket
                handle = DatagramChannel.open();
                handle.configureBlocking(blocking);
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
            int size;
            isRunning = true;
            try {
                while (!thread.interrupted() && isRunning) {
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
