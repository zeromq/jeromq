package org.zeromq;

import java.io.IOException;
import java.net.InetAddress;

import org.junit.Test;
import org.zeromq.ZBeacon.Listener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ZBeaconTest
{
    @Test
    public void test() throws InterruptedException, IOException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        byte[] beacon = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01, 0x12, 0x34 };
        byte[] prefix = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01 };
        int port = Utils.findOpenPort();
        ZBeacon zbeacon = new ZBeacon("255.255.255.255", port, beacon, false);
        zbeacon.setPrefix(prefix);
        zbeacon.setListener(new Listener()
        {
            @Override
            public void onBeacon(InetAddress sender, byte[] beacon)
            {
                latch.countDown();
            }
        });

        zbeacon.start();
        latch.await(20, TimeUnit.SECONDS);
        assertEquals(latch.getCount(), 0);
        zbeacon.stop();
    }
}
