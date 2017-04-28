package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ReqRepTest
{
    private final class Server303 implements Runnable
    {
        private final String  address;
        private final int     threadCount;
        private final boolean verbose;
        private final int     loopCount;

        private Server303(String address, int loopCount, int threadCount, boolean verbose)
        {
            this.address = address;
            this.threadCount = threadCount;
            this.verbose = verbose;
            this.loopCount = loopCount;
        }

        @Override
        public void run()
        {
            int currentServCount = 0;
            try (
                 ZMQ.Context context = ZMQ.context(1);
                 ZMQ.Socket responder = context.socket(ZMQ.REP);) {
                responder.bind(address);
                int count = loopCount * threadCount;
                while (count-- > 0) {
                    try {
                        String incomingMessage = responder.recvStr();
                        responder.send(String.format(
                                                     "Server Replied [%1$s/%2$s] of %3$s",
                                                     currentServCount,
                                                     Thread.currentThread().getId(),
                                                     incomingMessage));
                        currentServCount++;

                        if (currentServCount % 1000 == 0 && verbose) {
                            System.out.println("Served " + currentServCount);
                        }
                    }
                    catch (Exception e2) {
                        System.out.println(">>>>>>>>>>got exception " + e2.getMessage());
                    }

                }
            }
        }
    }

    private final class Client303 implements Runnable
    {
        private final int     loopCount;
        private final String  address;
        private final boolean verbose;

        private Client303(String address, int loopCount, boolean verbose)
        {
            this.loopCount = loopCount;
            this.address = address;
            this.verbose = verbose;
        }

        @Override
        public void run()
        {
            try (
                 ZMQ.Context context = ZMQ.context(10);
                 ZMQ.Socket socket = context.socket(ZMQ.REQ);) {
                socket.connect(address);
                for (int idx = 0; idx < loopCount; idx++) {
                    long tid = Thread.currentThread().getId();
                    String msg = "hello-" + idx;
                    if (verbose) {
                        System.out.println(tid + " sending " + msg);
                    }
                    socket.send(msg);
                    if (verbose) {
                        System.out.println(tid + " waiting response");
                    }
                    String s = socket.recvStr();
                    if (verbose) {
                        System.out.println(tid + " client received [" + s + "]");
                    }
                }
            }
        }
    }

    @Test
    public void testWaitForeverOnSignalerIssue303() throws IOException, InterruptedException
    {
        final int port = Utils.findOpenPort();
        final String address = "tcp://localhost:" + port;

        final int threads = 2;
        final int messages = 50000;
        final boolean verbose = false;

        ExecutorService executor = Executors.newFixedThreadPool(1 + threads);

        long start = System.currentTimeMillis();
        executor.submit(new Client303(address, messages, verbose));
        executor.submit(new Client303(address, messages, verbose));
        executor.submit(new Server303(address, messages, threads, verbose));

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        System.out.println(String.format(
                                         "Req/Rep with %1$s threads for %2$s messages in %3$s millis.",
                                         threads,
                                         messages,
                                         (end - start)));
    }

    @Test
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
        executorService.submit(new Runnable()
        {
            @Override
            // simulates a server reply
            public void run()
            {
                final ZMQ.Socket rep = context.socket(ZMQ.REP);
                rep.bind(addr);
                latch.countDown();

                // just send the message back...
                final ZMsg msg = ZMsg.recvMsg(rep);
                msg.send(rep);
                // shut down the socket to cause a disconnect while REQ socket receives the msg
                rep.close();
                // btw.: setting linger time did not change the result
            }

        });

        executorService.shutdown();

        // wait till server socket is bound
        latch.await(1, TimeUnit.SECONDS);
        final long start = System.currentTimeMillis();
        try (
             final ZMQ.Socket req = context.socket(ZMQ.REQ);) {
            req.connect(addr);
            request.send(req);
            final ZMsg response = ZMsg.recvMsg(req);
            // the messages should be equal
            assertThat(Arrays.equals(response.getLast().getData(), payloadBytes), is(true));
        }
        finally {
            long end = System.currentTimeMillis();
            System.out.println("Large Message received in  " + (end - start) + " millis.");
            context.close();
        }
    }
}
