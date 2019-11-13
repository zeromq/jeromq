package org.zeromq;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Assert;
import org.junit.Test;

public class ByteBuffersTest
{
    @Test
    public void testByteBufferSend() throws InterruptedException
    {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        try (ZMQ.Context context = ZMQ.context(1);
            ZMQ.Socket push = context.socket(SocketType.PUSH);
            ZMQ.Socket pull = context.socket(SocketType.PULL)) {
            pull.bind("tcp://*:*");
            push.connect(pull.getLastEndpoint());

            bb.put("PING".getBytes(ZMQ.CHARSET));
            bb.flip();

            Thread.sleep(1000);

            push.sendByteBuffer(bb, 0);
            String actual = new String(pull.recv(), ZMQ.CHARSET);
            assertEquals("PING", actual);
        }
    }

    @Test
    public void testByteBufferRecv() throws InterruptedException
    {
        ByteBuffer bb = ByteBuffer.allocate(6).order(ByteOrder.nativeOrder());
        try (ZMQ.Context context = ZMQ.context(1);
            ZMQ.Socket push = context.socket(SocketType.PUSH);
            ZMQ.Socket pull = context.socket(SocketType.PULL)) {
            pull.bind("tcp://*:*");
            push.connect(pull.getLastEndpoint());

            Thread.sleep(1000);

            push.send("PING".getBytes(ZMQ.CHARSET), 0);
            pull.recvByteBuffer(bb, 0);

            bb.flip();
            byte[] b = new byte[bb.remaining()];
            bb.duplicate().get(b);
            assertEquals("PING", new String(b, ZMQ.CHARSET));
        }
    }

    @Test
    public void testByteBufferLarge()
    {
        int[] array = new int[2048 * 2000];
        for (int i = 0; i < array.length; ++i) {
            array[i] = i;
        }
        ByteBuffer bSend = ByteBuffer.allocate(Integer.SIZE / 8 * array.length).order(ByteOrder.nativeOrder());
        bSend.asIntBuffer().put(array);
        ByteBuffer bRec = ByteBuffer.allocate(bSend.capacity()).order(ByteOrder.nativeOrder());

        int size = bSend.capacity() / (1024 * 1024);
        System.out.println("Test sending large message (~" + size + "Mb)");

        try (ZMQ.Context context = ZMQ.context(1);
            ZMQ.Socket push = context.socket(SocketType.PUSH);
            ZMQ.Socket pull = context.socket(SocketType.PULL)) {
            pull.bind("tcp://*:*");
            push.connect(pull.getLastEndpoint());

            Thread.sleep(1000);

            long start = System.currentTimeMillis();
            push.sendByteBuffer(bSend, 0);
            pull.recvByteBuffer(bRec, 0);
            long end = System.currentTimeMillis();
            System.out.println("Received ~" + size + "Mb msg in " + (end - start) + " millisec.");
            bRec.flip();
            assertEquals(bSend, bRec);
        }
        catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testByteBufferLargeDirect()
    {
        int[] array = new int[2048 * 2000];
        for (int i = 0; i < array.length; ++i) {
            array[i] = i;
        }
        ByteBuffer bSend = ByteBuffer.allocateDirect(Integer.SIZE / 8 * array.length).order(ByteOrder.nativeOrder());
        bSend.asIntBuffer().put(array);
        ByteBuffer bRec = ByteBuffer.allocateDirect(bSend.capacity()).order(ByteOrder.nativeOrder());
        int[] recArray = new int[array.length];

        int size = bSend.capacity() / (1024 * 1024);
        System.out.println("Test sending direct large message (~" + size + "Mb)");

        try (ZMQ.Context context = ZMQ.context(1);
            ZMQ.Socket push = context.socket(SocketType.PUSH);
            ZMQ.Socket pull = context.socket(SocketType.PULL)) {
            pull.bind("tcp://*:*");
            push.connect(pull.getLastEndpoint());

            Thread.sleep(1000);

            long start = System.currentTimeMillis();
            push.sendByteBuffer(bSend, 0);
            pull.recvByteBuffer(bRec, 0);
            long end = System.currentTimeMillis();
            System.out.println("Received ~" + size + "Mb msg in " + (end - start) + " millisec.");

            bRec.flip();
            bRec.asIntBuffer().get(recArray);
            assertArrayEquals(array, recArray);
        }
        catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
