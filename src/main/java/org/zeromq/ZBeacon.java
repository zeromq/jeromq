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

package org.zeromq;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;

public class ZBeacon
{
    public static final long DEFAULT_BROADCAST_INTERVAL = 1000L;
    public static final String DEFAULT_BROADCAST_HOST = "255.255.255.255";

    private final int port;
    private InetAddress broadcastInetAddress;
    private final BroadcastClient broadcastClient;
    private final BroadcastServer broadcastServer;
    private final byte[] beacon;
    private byte[] prefix = {};
    private long broadcastInterval = DEFAULT_BROADCAST_INTERVAL;
    private Listener listener = null;

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
        this.port = port;
        this.beacon = beacon;
        try {
            broadcastInetAddress = InetAddress.getByName(host);
        }
        catch (UnknownHostException unknownHostException) {
            throw new RuntimeException(unknownHostException);
        }

        broadcastServer = new BroadcastServer(ignoreLocalAddress);
        broadcastServer.setDaemon(true);
        broadcastClient = new BroadcastClient();
        broadcastClient.setDaemon(true);
    }

    public void start()
    {
        if (listener != null) {
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

    public void setPrefix(byte[] prefix)
    {
        this.prefix = prefix;
    }

    public byte[] getPrefix()
    {
        return prefix;
    }

    public void setListener(Listener listener)
    {
        this.listener = listener;
    }

    public Listener getListener()
    {
        return listener;
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
        private DatagramChannel broadcastChannel;
        private final InetSocketAddress broadcastInetSocketAddress;

        public BroadcastClient()
        {
            broadcastInetSocketAddress = new InetSocketAddress(broadcastInetAddress, port);
        }

        @Override
        public void run()
        {
            try {
                broadcastChannel = DatagramChannel.open();
                broadcastChannel.socket().setBroadcast(true);
                while (!interrupted()) {
                    try {
                        broadcastChannel.send(ByteBuffer.wrap(beacon), broadcastInetSocketAddress);
                        Thread.sleep(broadcastInterval);
                    }
                    catch (InterruptedException interruptedException) {
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
        private DatagramChannel handle; // Socket for send/recv
        private final boolean ignoreLocalAddress;

        public BroadcastServer(boolean ignoreLocalAddress)
        {
            this.ignoreLocalAddress = ignoreLocalAddress;
            try {
                // Create UDP socket
                handle = DatagramChannel.open();
                handle.configureBlocking(false);
                DatagramSocket sock = handle.socket();
                sock.setReuseAddress(true);
                sock.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] { 0, 0, 0, 0 }), port));
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

                    if (ignoreLocalAddress &&
                                (InetAddress.getLocalHost().getHostAddress().equals(senderAddress.getHostAddress())
                                         || senderAddress.isAnyLocalAddress()
                                         || senderAddress.isLoopbackAddress())) {
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
            if (size < prefix.length) {
                return;
            }
            byte[] bytes = buffer.array();
            // Compare prefix
            for (int i = 0; i < prefix.length; i++) {
                if (bytes[i] != prefix[i]) {
                    return;
                }
            }
            listener.onBeacon(from, Arrays.copyOf(bytes, size));
        }
    }

    public long getBroadcastInterval()
    {
        return broadcastInterval;
    }

    public void setBroadcastInterval(long broadcastInterval)
    {
        this.broadcastInterval = broadcastInterval;
    }
}
