package org.jeromq.api;

import org.zeromq.ZMQ;
import zmq.DecoderBase;
import zmq.EncoderBase;

import java.nio.channels.SelectableChannel;
import java.util.EnumSet;

public class Socket {
    private final ZMQ.Socket socket;

    ZMQ.Socket getDelegate() {
        return socket;
    }

    Socket(ZMQ.Socket socket) {
        this.socket = socket;
    }

    public boolean connect(String address) {
        return socket.connect(address);
    }

    public int bind(String address) {
        return socket.bind(address);
    }

    public int bindToRandomPort(String address, int min, int max) {
        return socket.bindToRandomPort(address, min, max);
    }

    public int bindToRandomPort(String addr) {
        return socket.bindToRandomPort(addr);
    }

    public void close() {
        socket.close();
    }

    public byte[] receive() {
        return socket.recv();
    }

    public byte[] receive(SendReceiveOption option) {
        return socket.recv(option.getCValue());
    }

    public int receive(byte[] buffer, int offset, int len, SendReceiveOption option) {
        return socket.recv(buffer, offset, len, option.getCValue());
    }

    public String receiveString() {
        return socket.recvStr();
    }

    public String receiveString(SendReceiveOption option) {
        return socket.recvStr(option.getCValue());
    }

    public boolean hasMoreToReceive() {
        return socket.hasReceiveMore();
    }

    public void subscribe(byte[] topic) {
        socket.subscribe(topic);
    }

    public void subscribe(String topic) {
        socket.subscribe(topic);
    }

    public void unsubscribe(byte[] topic) {
        socket.unsubscribe(topic);
    }

    public void unsubscribe(String topic) {
        socket.unsubscribe(topic);
    }

    public boolean send(byte[] data) {
        return socket.send(data);
    }

    public boolean send(String data) {
        return socket.send(data);
    }

    public boolean send(byte[] data, SendReceiveOption option) {
        return socket.send(data, option.getCValue());
    }

    public boolean send(String data, SendReceiveOption option) {
        return socket.send(data, option.getCValue());
    }

    public boolean sendMore(byte[] data) {
        return socket.sendMore(data);
    }

    public boolean sendMore(String data) {
        return socket.sendMore(data);
    }

    public boolean monitor(String addr, EnumSet<TransportEvent> events) {
        return socket.monitor(addr, TransportEvent.getCValue(events));
    }

    public EnumSet<TransportEvent> getEvents() {
        return TransportEvent.decode(socket.getEvents());
    }

    public void dumpToConsole() {
        socket.dump();
    }

    public SocketType getType() {
        return SocketType.typeOf(socket.getType());
    }

    public void setRecoveryInterval(long value) {
        socket.setRecoveryInterval(value);
    }

    public long getRecoveryInterval() {
        return socket.getRecoveryInterval();
    }

    public void setReconnectInterval(long value) {
        socket.setReconnectIVL(value);
    }

    public long getReconnectInterval() {
        return socket.getReconnectIVL();
    }

    public void setMaxReconnectInterval(long value) {
        socket.setReconnectIVLMax(value);
    }

    public void setMaxMessageSize(long value) {
        socket.setMaxMsgSize(value);
    }

    public long getMaxMessageSize() {
        return socket.getMaxMsgSize();
    }

    public long getLinger() {
        return socket.getLinger();
    }

    public void setLinger(long value) {
        socket.setLinger(value);
    }

    public void setRate(long value) {
        socket.setRate(value);
    }

    public long getRate() {
        return socket.getRate();
    }

    public void setReceiveHighWaterMark(long value) {
        socket.setRcvHWM(value);
    }

    public long getReceiveHighWaterMark() {
        return socket.getRcvHWM();
    }

    public void setSendHighWaterMark(long value) {
        socket.setSndHWM(value);
    }

    public long getSendHighWaterMark() {
        return socket.getSndHWM();
    }

    public void setHighWaterMarks(long highWaterMark) {
        socket.setHWM(highWaterMark);
    }

    public void setAffinity(long value) {
        socket.setAffinity(value);
    }

    public long getAffinity() {
        return socket.getAffinity();
    }

    public void setReceiveTimeOut(int milliseconds) {
        socket.setReceiveTimeOut(milliseconds);
    }

    public int getReceiveTimeOut() {
        return socket.getReceiveTimeOut();
    }

    public void setSendTimeOut(int value) {
        socket.setSendTimeOut(value);
    }

    public int getSendTimeOut() {
        return socket.getSendTimeOut();
    }

    public long getReconnectIntervalMax() {
        return socket.getReconnectIVLMax();
    }

    public void setSendBufferSize(long value) {
        socket.setSendBufferSize(value);
    }

    public long getSendBufferSize() {
        return socket.getSendBufferSize();
    }

    public void setReceiveBufferSize(long value) {
        socket.setReceiveBufferSize(value);
    }

    public long getReceiveBufferSize() {
        return socket.getReceiveBufferSize();
    }

    /**
     * Sets the identity of the specified 'socket'. Socket
     * identity determines if existing 0MQ infrastructure (_message queues_, _forwarding
     * devices_) shall be identified with a specific application and persist across multiple
     * runs of the application.
     *
     * If the socket has no identity, each run of an application is completely separate from
     * other runs. However, with identity set the socket shall re-use any existing 0MQ
     * infrastructure configured by the previous run(s). Thus the application may receive
     * messages that were sent in the meantime, _message queue_ limits shall be shared with
     * previous run(s) and so on.
     *
     * Identity should be at least one byte and at most 255 bytes long. Identities starting with
     * binary zero are reserved for use by 0MQ infrastructure.
     */
    public void setIdentity(String identity) {
        socket.setIdentity(identity);
    }

    /**
     * Sets the identity of the specified 'socket'. Socket
     * identity determines if existing 0MQ infrastructure (_message queues_, _forwarding
     * devices_) shall be identified with a specific application and persist across multiple
     * runs of the application.
     *
     * If the socket has no identity, each run of an application is completely separate from
     * other runs. However, with identity set the socket shall re-use any existing 0MQ
     * infrastructure configured by the previous run(s). Thus the application may receive
     * messages that were sent in the meantime, _message queue_ limits shall be shared with
     * previous run(s) and so on.
     *
     * Identity should be at least one byte and at most 255 bytes long. Identities starting with
     * binary zero are reserved for use by 0MQ infrastructure.
     */
    public void setIdentity(byte[] identity) {
        socket.setIdentity(identity);
    }

    /**
     * @see #setIdentity(byte[])
     */
    public byte[] getIdentity() {
        return socket.getIdentity();
    }

    public void setRouterMandatory(boolean mandatory) {
        socket.setRouterMandatory(mandatory);
    }

    public boolean isRouterMandatory() {
        return socket.getRouterMandatory();
    }

    public void setBacklog(long value) {
        socket.setBacklog(value);
    }

    public long getBacklog() {
        return socket.getBacklog();
    }

    public void setMulticastHops(long value) {
        socket.setMulticastHops(value);
    }

    public long getMulticastHops() {
        return socket.getMulticastHops();
    }

    public void setDecoder(Class<? extends DecoderBase> cls) {
        socket.setDecoder(cls);
    }

    public void setEncoder(Class<? extends EncoderBase> cls) {
        socket.setEncoder(cls);
    }

    public SelectableChannel getFileDescriptor() {
        return socket.getFD();
    }
}
