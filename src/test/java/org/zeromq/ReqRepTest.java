package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;

import zmq.util.AndroidProblematic;

public class ReqRepTest
{
    private interface GetServer
    {
        Server303 get(String address, AtomicBoolean keepRunning, boolean verbose);
    }

    private static final class Server532 extends Server303
    {
        private final byte[] repMsg = new byte[1024 * 1024];

        private Server532(String address, AtomicBoolean keepRunning, boolean verbose)
        {
            super(address, keepRunning, verbose);
            Arrays.fill(repMsg, (byte) 'e');
        }

        @Override
        protected boolean send(int currentServCount, Socket responder, String msg)
        {
            int size = Math.min(Integer.parseInt(msg.substring(6)), repMsg.length);
            return responder.send(repMsg, 0, size, 0);
        }
    }

    private static class Server303 implements Callable<Integer>
    {
        private final String  address;
        private final boolean verbose;
        private final AtomicBoolean keepRunning;

        private Server303(String address, AtomicBoolean keepRunning, boolean verbose)
        {
            this.address = address;
            this.verbose = verbose;
            this.keepRunning = keepRunning;
        }

        @SuppressWarnings("deprecation")
        @Override
        public Integer call()
        {
            int currentServCount = 0;
            try (
                 ZMQ.Context context = ZMQ.context(1);
                 ZMQ.Socket responder = context.socket(SocketType.REP);) {
                assertThat(responder, notNullValue());
                boolean rc = responder.bind(address);
                assertThat(rc, is(true));
                int count;
                for (count = 0; keepRunning.get(); count++) {
                    try {
                        String incomingMessage = responder.recvStr();
                        assertThat(incomingMessage, notNullValue());
                        rc = send(currentServCount, responder, incomingMessage);
                        assertThat(rc, is(true));
                        currentServCount++;

                        if (verbose && currentServCount % 1000 == 0) {
                            System.out.println("Served " + currentServCount);
                        }
                    }
                    catch (ZMQException ex) {
                        if (ex.getErrorCode() == ZMQ.Error.EINTR.getCode()) {
                            continue;
                        }
                        else {
                            throw ex;
                        }
                    }
                }
                return count;
            }
        }

        protected boolean send(int currentServCount, ZMQ.Socket responder, String incomingMessage)
        {
            return responder.send(
                                  String.format(
                                                "Server Replied [%1$s/%2$s] of %3$s",
                                                currentServCount,
                                                Thread.currentThread().getId(),
                                                incomingMessage));
        }
    }

    private static final class Client303 implements Callable<Integer>
    {
        private final AtomicBoolean keepRunning;
        private final String  address;
        private final boolean verbose;

        private Client303(String address, AtomicBoolean keepRunning, boolean verbose)
        {
            this.keepRunning = keepRunning;
            this.address = address;
            this.verbose = verbose;
        }

        @SuppressWarnings("deprecation")
        @Override
        public Integer call()
        {
            try (
                 ZMQ.Context context = ZMQ.context(10);
                 ZMQ.Socket socket = context.socket(SocketType.REQ);) {
                boolean rc = socket.connect(address);
                assertThat(rc, is(true));
                int idx;
                for (idx = 0; keepRunning.get(); idx++) {
                    long tid = Thread.currentThread().getId();
                    String msg = "hello-" + idx;
                    if (verbose) {
                        System.out.println(tid + " sending " + msg);
                    }
                    rc = socket.send(msg);
                    assertThat(rc, is(true));
                    if (verbose) {
                        System.out.println(tid + " waiting response");
                    }
                    String s = socket.recvStr();
                    assertThat(s, notNullValue());
                    if (verbose) {
                        System.out.println(tid + " client received [" + s + "]");
                    }
                }
                return idx;
            }
        }
    }

    private void runTest(GetServer getter) throws IOException, InterruptedException, ExecutionException
    {
        final int threads = 1;
        final int port = Utils.findOpenPort();
        final String address = "tcp://localhost:" + port;

        final AtomicBoolean keepRunning = new AtomicBoolean(true);
        final boolean verbose = false;

        ExecutorService executor = Executors.newFixedThreadPool(1 + threads);

        Set<Future<Integer>> clientsf = new HashSet<>(threads);
        for (int idx = 0; idx < threads; ++idx) {
            clientsf.add(executor.submit(new Client303(address, keepRunning, verbose)));
        }
        Future<Integer> resultf = executor.submit(getter.get(address, keepRunning, verbose));
        executor.shutdown();
        ZMQ.sleep(4);
        keepRunning.set(false);
        int clientMessage = 0;
        for (Future<Integer> c : clientsf) {
            clientMessage += c.get();
        }
        executor.shutdownNow();
        assertEquals(clientMessage, resultf.get().intValue());
    }

    @Test(timeout = 5000)
    public void testIssue532() throws IOException, InterruptedException, ExecutionException
    {
        runTest(Server532::new);
    }

    @Test(timeout = 5000)
    public void testWaitForeverOnSignalerIssue303() throws IOException, InterruptedException, ExecutionException
    {
        runTest(Server303::new);
    }

    @SuppressWarnings("deprecation")
    @Test(timeout = 5000)
    @AndroidProblematic // triggers OutofMemoryError on Android
    public void testDisconnectOnLargeMessageIssue334() throws Exception
    {
        final int msgSizeMB = 100;
        final ZMQ.Context context = ZMQ.context(1);

        final int oneMb = 1024 * 1024;
        final byte[] payloadBytes = new byte[msgSizeMB * oneMb];
        for (int idx = 0; idx < msgSizeMB; ++idx) {
            int offset = oneMb * idx;
            Arrays.fill(payloadBytes, offset, offset + oneMb, (byte) ('a' + idx));
        }
        final ZMsg request = new ZMsg();
        request.add(payloadBytes);

        final String host = "localhost";
        final int port = Utils.findOpenPort();
        final String addr = "tcp://" + host + ":" + port;

        final CountDownLatch latch = new CountDownLatch(1);
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<Boolean> senderf = executorService.submit(new Callable<Boolean>()
        {
            @Override
            // simulates a server reply
            public Boolean call()
            {
                final ZMQ.Socket rep = context.socket(SocketType.REP);
                rep.bind(addr);
                latch.countDown();

                // just send the message back...
                final ZMsg msg = ZMsg.recvMsg(rep);
                msg.send(rep);
                // shut down the socket to cause a disconnect while REQ socket receives the msg
                rep.close();
                // btw.: setting linger time did not change the result
                return true;
            }
        });

        executorService.shutdown();

        // wait till server socket is bound
        latch.await();
        try (
             final ZMQ.Socket req = context.socket(SocketType.REQ);) {
            req.connect(addr);
            request.send(req);
            final ZMsg response = ZMsg.recvMsg(req);
            // the messages should be equal
            assertThat(Arrays.equals(response.getLast().getData(), payloadBytes), is(true));
            assertTrue(senderf.get());
        }
        finally {
            context.close();
        }
    }
}
