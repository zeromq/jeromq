package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Ignore;
import org.junit.Test;

public class ZBeaconTest
{
    @Test(timeout = 2000)
    public void testUseBuilder() throws InterruptedException, IOException
    {
        long interval = ZBeacon.DEFAULT_BROADCAST_INTERVAL / 2;
        AtomicLong beacon1 = new AtomicLong(0);
        AtomicLong beacon2 = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(2);
        int port = Utils.findOpenPort();
        byte[] beacondata = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01, 0x12, 0x34 };
        AtomicReference<byte[]> receivedBeacon = new AtomicReference<>();
        ZBeacon.Builder builder = new ZBeacon.Builder()
                .beacon(beacondata)
                .ignoreLocalAddress(false)
                .broadcastInterval(interval)
                .client(InetAddress.getLoopbackAddress()).port(port)
                .server(InetAddress.getLoopbackAddress())
                .prefix(new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01 })
                .listener((sender, received) -> {
                    if (beacon1.get() == 0) {
                        beacon1.set(System.currentTimeMillis());
                    }
                    else if (beacon2.get() == 0) {
                        beacon2.set(System.currentTimeMillis());
                        receivedBeacon.set(received);
                    }
                    latch.countDown();
                });
        ZBeacon beacon = builder.build();
        try {
            beacon.start();
            assertThat(latch.await(interval * 3, TimeUnit.MILLISECONDS), is(Boolean.TRUE));
        }
        finally {
            beacon.stop();
        }

        //Ensure that the real interval is almost the required interval
        long delta = beacon2.get() - beacon1.get();
        assertTrue(String.format("expected %d, got %d",  interval, delta), (delta > interval - 10) && (delta < interval + 10));
        assertThat(receivedBeacon.get(), is(beacondata));

    }

    @SuppressWarnings("deprecation")
    @Test(timeout = 2000)
    public void testReceiveOwnBeacons() throws InterruptedException, IOException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        byte[] beacon = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01, 0x12, 0x34 };
        byte[] prefix = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01 };
        int port = Utils.findOpenPort();
        ZBeacon zbeacon = new ZBeacon("127.0.0.1", port, beacon, false);
        zbeacon.setPrefix(prefix);
        zbeacon.setListener((sender, beacon1) -> latch.countDown());

        try {
            zbeacon.start();
            latch.await(20, TimeUnit.SECONDS);
            assertThat(latch.getCount(), is(0L));
        }
        finally {
            zbeacon.stop();
        }
    }

    @SuppressWarnings("deprecation")
    @Test(timeout = 2000)
    @Ignore
    public void testIgnoreOwnBeacon() throws IOException, InterruptedException
    {
        final int port = Utils.findOpenPort();

        final byte[] beacon = new byte[] { 'Z', 'R', 'E', 0x01, 0x2 };
        final byte[] prefix = new byte[] { 'Z', 'R', 'E', 0x01 };
        final ZBeacon zbeacon = new ZBeacon(ZBeacon.DEFAULT_BROADCAST_HOST, port, beacon, true);
        zbeacon.setPrefix(prefix);

        final AtomicLong counter = new AtomicLong();

        zbeacon.setListener((sender, beacon1) -> {
            counter.incrementAndGet();
            System.out.println(sender.toString());
            try {
                System.out.println(InetAddress.getLocalHost().getHostAddress());
            }
            catch (Exception e) {
            }
            System.out.println(new String(beacon1));
        });
        zbeacon.start();
        zmq.ZMQ.sleep(1);
        zbeacon.stop();

        assertThat(counter.get(), is(0L));
    }

    @SuppressWarnings("deprecation")
    @Test(timeout = 2000)
    public void testReceiveOwnBeaconsBlocking() throws InterruptedException, IOException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        byte[] beacon = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01, 0x12, 0x34 };
        byte[] prefix = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01 };
        int port = Utils.findOpenPort();
        ZBeacon zbeacon = new ZBeacon("127.0.0.1", port, beacon, false, true);
        zbeacon.setPrefix(prefix);
        zbeacon.setListener((sender, beacon1) -> latch.countDown());

        try {
            zbeacon.start();
            latch.await(20, TimeUnit.SECONDS);
            assertThat(latch.getCount(), is(0L));
        }
        finally {
            zbeacon.stop();
        }
    }

    @SuppressWarnings("deprecation")
    @Test(timeout = 2000)
    @Ignore
    public void testIgnoreOwnBeaconBlocking() throws IOException, InterruptedException
    {
        final int port = Utils.findOpenPort();

        final byte[] beacon = new byte[] { 'Z', 'R', 'E', 0x01, 0x2 };
        final byte[] prefix = new byte[] { 'Z', 'R', 'E', 0x01 };
        final ZBeacon zbeacon = new ZBeacon(ZBeacon.DEFAULT_BROADCAST_HOST, port, beacon, true, true);
        zbeacon.setPrefix(prefix);

        final AtomicLong counter = new AtomicLong();

        zbeacon.setListener((sender, beacon1) -> {
            counter.incrementAndGet();
            System.out.println(sender.toString());
            try {
                System.out.println(InetAddress.getLocalHost().getHostAddress());
            }
            catch (Exception e) {
            }
            System.out.println(new String(beacon1));
        });
        zbeacon.start();
        zmq.ZMQ.sleep(1);
        zbeacon.stop();

        assertThat(counter.get(), is(0L));
    }
}
