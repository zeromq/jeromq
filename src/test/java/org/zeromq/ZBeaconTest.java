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

import java.net.InetAddress;

import org.junit.Test;
import org.zeromq.ZBeacon.Listener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ZBeaconTest
{
    @Test
    public void test() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        byte[] beacon = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01, 0x12, 0x34 };
        byte[] prefix = new byte[] { 'H', 'Y', 'D', 'R', 'A', 0x01 };
        ZBeacon zbeacon = new ZBeacon("255.255.255.255", 5670, beacon, false);
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
