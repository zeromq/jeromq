package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

import org.junit.Test;
import org.zeromq.ZMQ.Error;
import org.zeromq.ZMonitor.ProtocolCode;

import zmq.Ctx;
import zmq.SocketBase;

public class TestEventResolution
{
    private interface SendEvent
    {
        void send(SocketBase s, String addr);
    }

    public ZMQ.Event make(SendEvent sender, int eventFilter)
    {
        Ctx ctx = new Ctx();
        @SuppressWarnings("deprecation")
        SocketBase s = ctx.createSocket(ZMQ.PUB);
        @SuppressWarnings("deprecation")
        SocketBase m = ctx.createSocket(ZMQ.PAIR);
        m.connect("inproc://TestEventResolution");
        s.monitor("inproc://TestEventResolution", eventFilter);
        sender.send(s, "tcp://127.0.0.1:8000");
        zmq.ZMQ.Event ev = zmq.ZMQ.Event.read(m);
        return new ZMQ.Event(ev.event, ev.arg, ev.addr);
    }

    @Test(timeout = 1000)
    public void testEventHandshakeFailedProtocol()
    {
        ZMQ.Event ev = make((s, a) -> s.eventHandshakeFailedProtocol(a, zmq.ZMQ.ZMQ_PROTOCOL_ERROR_ZMTP_UNEXPECTED_COMMAND), zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_PROTOCOL);
        ProtocolCode protocol = ev.resolveValue();
        assertThat(protocol, is(ProtocolCode.ZMQ_PROTOCOL_ERROR_ZMTP_UNEXPECTED_COMMAND));
        assertThat(ev.isError(), is(true));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventHandshakeFailedAuth()
    {
        ZMQ.Event ev = make((s, a) -> s.eventHandshakeFailedAuth(a, 200), zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_AUTH);
        int statusCode = ev.resolveValue();
        assertThat(statusCode, is(200));
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(true));
    }

    @Test(timeout = 1000)
    public void testEventDisconnected() throws IOException
    {
        SelectableChannel sc = SocketChannel.open();
        ZMQ.Event ev = make((s, a) -> s.eventDisconnected(a, sc), zmq.ZMQ.ZMQ_EVENT_DISCONNECTED);
        Object value = ev.resolveValue();
        assertThat(value, nullValue());
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventClosed() throws IOException
    {
        SelectableChannel sc = SocketChannel.open();
        ZMQ.Event ev = make((s, a) -> s.eventClosed(a, sc), zmq.ZMQ.ZMQ_EVENT_CLOSED);
        Object value = ev.resolveValue();
        assertThat(value, nullValue());
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventListening() throws IOException
    {
        SelectableChannel sc = SocketChannel.open();
        ZMQ.Event ev = make((s, a) -> s.eventListening(a, sc), zmq.ZMQ.ZMQ_EVENT_LISTENING);
        Object value = ev.resolveValue();
        assertThat(value, nullValue());
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventConnected() throws IOException
    {
        SelectableChannel sc = SocketChannel.open();
        ZMQ.Event ev = make((s, a) -> s.eventConnected(a, sc), zmq.ZMQ.ZMQ_EVENT_CONNECTED);
        Object value = ev.resolveValue();
        assertThat(value, nullValue());
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventAccepted() throws IOException
    {
        SelectableChannel sc = SocketChannel.open();
        ZMQ.Event ev = make((s, a) -> s.eventAccepted(a, sc), zmq.ZMQ.ZMQ_EVENT_ACCEPTED);
        Object value = ev.resolveValue();
        assertThat(value, nullValue());
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventConnectDelayed()
    {
        ZMQ.Event ev = make((s, a) -> s.eventConnectDelayed(a, -1), zmq.ZMQ.ZMQ_EVENT_CONNECT_DELAYED);
        Object value = ev.resolveValue();
        assertThat(value, nullValue());
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventConnectRetried()
    {
        ZMQ.Event ev = make((s, a) -> s.eventConnectRetried(a, 10), zmq.ZMQ.ZMQ_EVENT_CONNECT_RETRIED);
        Object value = ev.resolveValue();
        assertThat(value, is(10));
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventCloseFailed()
    {
        ZMQ.Event ev = make((s, a) -> s.eventCloseFailed(a, Error.EINTR.getCode()), zmq.ZMQ.ZMQ_EVENT_CLOSE_FAILED);
        Error err = ev.resolveValue();
        assertThat(err, is(Error.EINTR));
        assertThat(ev.isError(), is(true));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventAcceptFailed()
    {
        ZMQ.Event ev = make((s, a) -> s.eventAcceptFailed(a, Error.EINTR.getCode()), zmq.ZMQ.ZMQ_EVENT_ACCEPT_FAILED);
        Error err = ev.resolveValue();
        assertThat(err, is(Error.EINTR));
        assertThat(ev.isError(), is(true));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventBindFailed()
    {
        ZMQ.Event ev = make((s, a) -> s.eventBindFailed(a, Error.EADDRNOTAVAIL.getCode()), zmq.ZMQ.ZMQ_EVENT_BIND_FAILED);
        Error err = ev.resolveValue();
        assertThat(err, is(Error.EADDRNOTAVAIL));
        assertThat(ev.isError(), is(true));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventHandshaken()
    {
        ZMQ.Event ev = make((s, a) -> s.eventHandshaken(a, -1), zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL);
        int zmtp = ev.resolveValue();
        assertThat(zmtp, is(-1));
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventHandshakeSucceeded()
    {
        ZMQ.Event ev = make((s, a) -> s.eventHandshakeSucceeded(a, 0), zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_SUCCEEDED);
        Object value = ev.resolveValue();
        assertThat(value, nullValue());
        assertThat(ev.isError(), is(false));
        assertThat(ev.isWarn(), is(false));
    }

    @Test(timeout = 1000)
    public void testEventHandshakeFailedNoDetail()
    {
        ZMQ.Event ev = make((s, a) -> s.eventHandshakeFailedNoDetail(a, 0), zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_NO_DETAIL);
        Object value = ev.resolveValue();
        assertThat(value, nullValue());
        assertThat(ev.isError(), is(true));
        assertThat(ev.isWarn(), is(false));
    }
}
