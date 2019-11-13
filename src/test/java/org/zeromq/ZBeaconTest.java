package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Ignore;
import org.junit.Test;

public class ZBeaconTest
{
    @Test
    public void testUseBuilder() throws InterruptedException, IOException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        int port = Utils.findOpenPort();
        ZBeacon.Builder builder = new ZBeacon.Builder().beacon(new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01, 0x12, 0x34 })
                .ignoreLocalAddress(false).blocking(false).broadcastInterval(2000L).client("127.0.0.1").port(port)
                .server(new byte[] { 127, 0, 0, 1 });
        byte[] prefix = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01 };
        ZBeacon beacon = builder.build();
        beacon.setPrefix(prefix);
        beacon.setListener((sender, beacon1) -> latch.countDown());

        beacon.start();
        latch.await(20, TimeUnit.SECONDS);
        assertThat(latch.getCount(), is(0L));
        beacon.stop();
    }

    @Test
    public void testReceiveOwnBeacons() throws InterruptedException, IOException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        byte[] beacon = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01, 0x12, 0x34 };
        byte[] prefix = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01 };
        int port = Utils.findOpenPort();
        ZBeacon zbeacon = new ZBeacon("127.0.0.1", port, beacon, false);
        zbeacon.setPrefix(prefix);
        zbeacon.setListener((sender, beacon1) -> latch.countDown());

        zbeacon.start();
        latch.await(20, TimeUnit.SECONDS);
        assertThat(latch.getCount(), is(0L));
        zbeacon.stop();
    }

    @Test
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
            catch (Exception ignored) {
            }
            System.out.println(new String(beacon1));
        });
        zbeacon.start();
        zmq.ZMQ.sleep(1);
        zbeacon.stop();

        assertThat(counter.get(), is(0L));
    }

    @Test
    public void testReceiveOwnBeaconsBlocking() throws InterruptedException, IOException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        byte[] beacon = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01, 0x12, 0x34 };
        byte[] prefix = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01 };
        int port = Utils.findOpenPort();
        ZBeacon zbeacon = new ZBeacon("127.0.0.1", port, beacon, false, true);
        zbeacon.setPrefix(prefix);
        zbeacon.setListener((sender, beacon1) -> latch.countDown());

        zbeacon.start();
        latch.await(20, TimeUnit.SECONDS);
        assertThat(latch.getCount(), is(0L));
        zbeacon.stop();
    }

    @Test
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
            catch (Exception ignored) {
            }
            System.out.println(new String(beacon1));
        });
        zbeacon.start();
        zmq.ZMQ.sleep(1);
        zbeacon.stop();

        assertThat(counter.get(), is(0L));
    }
}
