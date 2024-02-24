package org.zeromq;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ.Error;
import org.zeromq.ZMonitor.ProtocolCode;

import zmq.SocketBase;
import zmq.ZError;
import zmq.util.function.Consumer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestEventResolution
{
    private interface SendEvent
    {
        void send(SocketBase s, String addr);
    }

    private ZEvent run1(SendEvent sender, int eventFilter) throws IOException
    {
        try (ZContext zctx = new ZContext(1);
            ZMQ.Socket s = zctx.createSocket(SocketType.PUB);
            ZMQ.Socket m = zctx.createSocket(SocketType.PAIR)) {
            s.monitor("inproc://TestEventResolution", eventFilter);
            m.connect("inproc://TestEventResolution");
            sender.send(s.base(), "tcp://127.0.0.1:" + Utils.findOpenPort());
            return ZEvent.recv(m);
        }
    }

    private ZEvent run2(SendEvent sender, int eventFilter) throws ExecutionException, InterruptedException,
                                                                          IOException
    {
        CompletableFuture<ZEvent> eventFuture = new CompletableFuture<>();
        try (ZContext zctx = new ZContext(1);
                ZMQ.Socket s = zctx.createSocket(SocketType.PUB)) {
            s.setEventHook(eventFuture::complete, eventFilter);
            sender.send(s.base(), "tcp://127.0.0.1:" + Utils.findOpenPort());
            return eventFuture.get();
        }
    }

    private void doTest(SendEvent sender, int eventFilter, Consumer<ZEvent> check)
            throws ExecutionException, InterruptedException, IOException
    {
        ZEvent event1 = run1(sender, eventFilter);
        assertThat(event1.getAddress(), startsWith("tcp://127.0.0.1:"));
        check.accept(event1);
        ZEvent event2 = run2(sender, eventFilter);
        assertThat(event1.getAddress(), startsWith("tcp://127.0.0.1:"));
        check.accept(event2);
    }

    @Test(timeout = 1000)
    public void testFailed()
    {
        ZContext zctx = new ZContext(1);
        ZMQ.Socket m = zctx.createSocket(SocketType.PAIR);
        m.connect("inproc://TestEventResolution");
        zctx.close();
        ZMQException ex = Assert.assertThrows(ZMQException.class, () -> ZEvent.recv(m));
        assertThat(ex.getErrorCode(), is(ZError.ETERM));
    }

    @Test(timeout = 1000)
    public void testEventHandshakeFailedProtocol() throws ExecutionException, InterruptedException, IOException
    {
        doTest((s, a) -> s.eventHandshakeFailedProtocol(a, zmq.ZMQ.ZMQ_PROTOCOL_ERROR_ZMTP_UNEXPECTED_COMMAND),
                zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_PROTOCOL,
                ev -> {
                    ProtocolCode protocol = ev.getValue();
                    assertThat(protocol, is(ProtocolCode.ZMQ_PROTOCOL_ERROR_ZMTP_UNEXPECTED_COMMAND));
                    assertThat(ev.isError(), is(true));
                    assertThat(ev.isWarn(), is(false));
                    assertThat(ev.isInformation(), is(false));
                    assertThat(ev.isDebug(), is(false));
                });
    }

    @Test(timeout = 1000)
    public void testEventHandshakeFailedAuth() throws ExecutionException, InterruptedException, IOException
    {
        doTest((s, a) -> s.eventHandshakeFailedAuth(a, 200),
                zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_AUTH,
                ev -> {
                    int statusCode = ev.getValue();
                    assertThat(statusCode, is(200));
                    assertThat(ev.isError(), is(false));
                    assertThat(ev.isWarn(), is(true));
                    assertThat(ev.isInformation(), is(false));
                    assertThat(ev.isDebug(), is(false));
                });
    }

    @Test(timeout = 1000)
    public void testEventDisconnected() throws IOException, ExecutionException, InterruptedException
    {
        try (SelectableChannel sc = SocketChannel.open()) {
            doTest((s, a) -> s.eventDisconnected(a, sc),
                    zmq.ZMQ.ZMQ_EVENT_DISCONNECTED,
                    ev -> {
                        Object value = ev.getValue();
                        assertThat(value, is(sc));
                        assertThat(ev.isError(), is(false));
                        assertThat(ev.isWarn(), is(false));
                        assertThat(ev.isInformation(), is(true));
                        assertThat(ev.isDebug(), is(false));
                    });
        }
    }

    @Test(timeout = 1000)
    public void testEventClosed() throws IOException, ExecutionException, InterruptedException
    {
        try (SelectableChannel sc = SocketChannel.open()) {
            doTest((s, a) -> s.eventClosed(a, sc), zmq.ZMQ.ZMQ_EVENT_CLOSED,
                    ev -> {
                        Object value = ev.getValue();
                        assertThat(value, is(sc));
                        assertThat(ev.isError(), is(false));
                        assertThat(ev.isWarn(), is(false));
                        assertThat(ev.isInformation(), is(false));
                        assertThat(ev.isDebug(), is(true));
                    });
        }
    }

    @Test(timeout = 1000)
    public void testEventListening() throws IOException, ExecutionException, InterruptedException
    {
        try (SelectableChannel sc = SocketChannel.open()) {
            doTest((s, a) -> s.eventListening(a, sc),
                    zmq.ZMQ.ZMQ_EVENT_LISTENING,
                    ev -> {
                        Object value = ev.getValue();
                        assertThat(value, is(sc));
                        assertThat(ev.isError(), is(false));
                        assertThat(ev.isWarn(), is(false));
                        assertThat(ev.isInformation(), is(false));
                        assertThat(ev.isDebug(), is(true));
                    });
        }
    }

    @Test(timeout = 1000)
    public void testEventConnected() throws IOException, ExecutionException, InterruptedException
    {
        try (SelectableChannel sc = SocketChannel.open()) {
            doTest((s, a) -> s.eventConnected(a, sc),
                    zmq.ZMQ.ZMQ_EVENT_CONNECTED,
                    ev -> {
                        Object value = ev.getValue();
                        assertThat(value, is(sc));
                        assertThat(ev.isError(), is(false));
                        assertThat(ev.isWarn(), is(false));
                        assertThat(ev.isInformation(), is(false));
                        assertThat(ev.isDebug(), is(true));
                    });
        }
    }

    @Test(timeout = 1000)
    public void testEventAccepted() throws IOException, ExecutionException, InterruptedException
    {
        try (SelectableChannel sc = SocketChannel.open()) {
            doTest((s, a) -> s.eventAccepted(a, sc),
                    zmq.ZMQ.ZMQ_EVENT_ACCEPTED,
                    ev -> {
                        Object value = ev.getValue();
                        assertThat(value, is(sc));
                        assertThat(ev.isError(), is(false));
                        assertThat(ev.isWarn(), is(false));
                        assertThat(ev.isInformation(), is(false));
                        assertThat(ev.isDebug(), is(true));
                    });
        }
    }

    @Test(timeout = 1000)
    public void testEventConnectDelayed() throws ExecutionException, InterruptedException, IOException
    {
        doTest((s, a) -> s.eventConnectDelayed(a, -1),
                zmq.ZMQ.ZMQ_EVENT_CONNECT_DELAYED,
                ev -> {
                    Object value = ev.getValue();
                    assertThat(value, is(Error.NOERROR));
                    assertThat(ev.isError(), is(false));
                    assertThat(ev.isWarn(), is(false));
                    assertThat(ev.isInformation(), is(false));
                    assertThat(ev.isDebug(), is(true));
                });
    }

    @Test(timeout = 1000)
    public void testEventConnectRetried() throws ExecutionException, InterruptedException, IOException
    {
        doTest((s, a) -> s.eventConnectRetried(a, 10),
                zmq.ZMQ.ZMQ_EVENT_CONNECT_RETRIED,
                ev -> {
                    Object value = ev.getValue();
                    assertThat(value, is(Duration.ofMillis(10)));
                    assertThat(ev.isError(), is(false));
                    assertThat(ev.isWarn(), is(false));
                    assertThat(ev.isInformation(), is(false));
                    assertThat(ev.isDebug(), is(true));
                });
    }

    @Test(timeout = 1000)
    public void testEventCloseFailed() throws ExecutionException, InterruptedException, IOException
    {
        doTest((s, a) -> s.eventCloseFailed(a, Error.EINTR.getCode()),
                zmq.ZMQ.ZMQ_EVENT_CLOSE_FAILED,
                ev -> {
                    Error err = ev.getValue();
                    assertThat(err, is(Error.EINTR));
                    assertThat(ev.isError(), is(true));
                    assertThat(ev.isWarn(), is(false));
                    assertThat(ev.isInformation(), is(false));
                    assertThat(ev.isDebug(), is(false));
                });
     }

    @Test(timeout = 1000)
    public void testEventAcceptFailed() throws ExecutionException, InterruptedException, IOException
    {
        doTest((s, a) -> s.eventAcceptFailed(a, Error.EINTR.getCode()),
                zmq.ZMQ.ZMQ_EVENT_ACCEPT_FAILED,
                ev -> {
                    Error err = ev.getValue();
                    assertThat(err, is(Error.EINTR));
                    assertThat(ev.isError(), is(true));
                    assertThat(ev.isWarn(), is(false));
                    assertThat(ev.isInformation(), is(false));
                    assertThat(ev.isDebug(), is(false));
                });
    }

    @Test(timeout = 1000)
    public void testEventBindFailed() throws ExecutionException, InterruptedException, IOException
    {
        doTest((s, a) -> s.eventBindFailed(a, Error.EADDRNOTAVAIL.getCode()),
                zmq.ZMQ.ZMQ_EVENT_BIND_FAILED,
                ev -> {
                    Error err = ev.getValue();
                    assertThat(err, is(Error.EADDRNOTAVAIL));
                    assertThat(ev.isError(), is(true));
                    assertThat(ev.isWarn(), is(false));
                    assertThat(ev.isInformation(), is(false));
                    assertThat(ev.isDebug(), is(false));
                });
    }

    @Test(timeout = 1000)
    public void testEventHandshaken() throws ExecutionException, InterruptedException, IOException
    {
        doTest((s, a) -> s.eventHandshaken(a, -1),
                zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL,
                ev -> {
                    int zmtp = ev.getValue();
                    assertThat(zmtp, is(-1));
                    assertThat(ev.isError(), is(false));
                    assertThat(ev.isWarn(), is(false));
                    assertThat(ev.isInformation(), is(false));
                    assertThat(ev.isDebug(), is(true));
                });
    }

    @Test(timeout = 1000)
    public void testEventHandshakeSucceeded() throws ExecutionException, InterruptedException, IOException
    {
        doTest((s, a) -> s.eventHandshakeSucceeded(a, 0),
                zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_SUCCEEDED,
                ev -> {
                    Error err = ev.getValue();
                    assertThat(err, is(Error.NOERROR));
                    assertThat(ev.isError(), is(false));
                    assertThat(ev.isWarn(), is(false));
                    assertThat(ev.isInformation(), is(false));
                    assertThat(ev.isDebug(), is(true));
                });
    }

    @Test(timeout = 1000)
    public void testEventHandshakeFailedNoDetail() throws ExecutionException, InterruptedException, IOException
    {
        doTest((s, a) -> s.eventHandshakeFailedNoDetail(a, Error.EFAULT.getCode()),
                zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_NO_DETAIL,
                ev -> {
                    Error err = ev.getValue();
                    assertThat(err, is(Error.EFAULT));
                    assertThat(ev.isError(), is(true));
                    assertThat(ev.isWarn(), is(false));
                    assertThat(ev.isInformation(), is(false));
                    assertThat(ev.isDebug(), is(false));
                });
    }

    @Test(timeout = 1000)
    public void testMonitorStop() throws ExecutionException, InterruptedException
    {
        CompletableFuture<ZEvent> eventFuture = new CompletableFuture<>();
        try (ZContext zctx = new ZContext(1);
             ZMQ.Socket s = zctx.createSocket(SocketType.PUB)) {
            s.setEventHook(eventFuture::complete, zmq.ZMQ.ZMQ_EVENT_MONITOR_STOPPED);
        }
        ZEvent ev = eventFuture.get();
        assertThat(ev.getEvent(), is(ZMonitor.Event.MONITOR_STOPPED));
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(false));
        assertThat(ev.isInformation(), is(false));
        assertThat(ev.isDebug(), is(true));
    }
}
