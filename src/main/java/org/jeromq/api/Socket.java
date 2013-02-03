package org.jeromq.api;

import org.zeromq.ZMQ;
import zmq.DecoderBase;
import zmq.EncoderBase;

import java.nio.channels.SelectableChannel;

public class Socket {
    private final ZMQ.Socket socket;

    ZMQ.Socket getDelegate() {
        return socket;
    }

    Socket(ZMQ.Socket socket) {
        this.socket = socket;
    }

    public void setRecoveryInterval(long value) {
        socket.setRecoveryInterval(value);
    }

    public long getReconnectInterval() {
        return socket.getReconnectIVL();
    }

    public void setMaxMsgSize(long value) {
        socket.setMaxMsgSize(value);
    }

    public void setLinger(long value) {
        socket.setLinger(value);
    }

    public void setRate(long value) {
        socket.setRate(value);
    }

    public long getLinger() {
        return socket.getLinger();
    }

    public boolean hasReceiveMore() {
        return socket.hasReceiveMore();
    }

    public void setReconnectInterval(long value) {
        socket.setReconnectIVL(value);
    }

    public void unsubscribe(String topic) {
        socket.unsubscribe(topic);
    }

    public void setIdentity(String identity) {
        socket.setIdentity(identity);
    }

    public byte[] receive() {
        return socket.recv();
    }

    public byte[] receive(int flags) {
        return socket.recv(flags);
    }

    public boolean connect(String address) {
        return socket.connect(address);
    }

    public String receiveString() {
        return socket.recvStr();
    }

    public long getReceiveHighWaterMark() {
        return socket.getRcvHWM();
    }

    public void getSendHighWaterMark(long value) {
        socket.setSndHWM(value);
    }

    public boolean send(String data) {
        return socket.send(data);
    }

    public void setHighWaterMark(long highWaterMark) {
        socket.setHWM(highWaterMark);
    }

    public void setReconnectIntervalMax(long value) {
        socket.setReconnectIVLMax(value);
    }

    public void subscribe(String topic) {
        socket.subscribe(topic);
    }

    public long getAffinity() {
        return socket.getAffinity();
    }

    public int getReceiveTimeOut() {
        return socket.getReceiveTimeOut();
    }

    public void setSendTimeOut(int value) {
        socket.setSendTimeOut(value);
    }

    public boolean sendMore(String data) {
        return socket.sendMore(data);
    }

    public boolean send(String data, int flags) {
        return socket.send(data, flags);
    }

    public long getReconnectIntervalMax() {
        return socket.getReconnectIVLMax();
    }

    public void setSendBufferSize(long value) {
        socket.setSendBufferSize(value);
    }

    public boolean send(byte[] data, int flags) {
        return socket.send(data, flags);
    }

    public int bindToRandomPort(String addr, int min, int max) {
        return socket.bindToRandomPort(addr, min, max);
    }

    public void setIdentity(byte[] identity) {
        socket.setIdentity(identity);
    }

    public long getRecoveryInterval() {
        return socket.getRecoveryInterval();
    }

    public int getType() {
        return socket.getType();
    }

    public long getRate() {
        return socket.getRate();
    }

    public void subscribe(byte[] topic) {
        socket.subscribe(topic);
    }

    public long getMaxMsgSize() {
        return socket.getMaxMsgSize();
    }

    public void close() {
        socket.close();
    }

    public int getEvents() {
        return socket.getEvents();
    }

    public boolean getRouterMandatory() {
        return socket.getRouterMandatory();
    }

    public void setDecoder(Class<? extends DecoderBase> cls) {
        socket.setDecoder(cls);
    }

    public void setEncoder(Class<? extends EncoderBase> cls) {
        socket.setEncoder(cls);
    }

    public long getSendBufferSize() {
        return socket.getSendBufferSize();
    }

    public void setReceiveTimeOut(int value) {
        socket.setReceiveTimeOut(value);
    }

    public long getBacklog() {
        return socket.getBacklog();
    }

    public void setAffinity(long value) {
        socket.setAffinity(value);
    }

    public byte[] getIdentity() {
        return socket.getIdentity();
    }

    public void setRouterMandatory(boolean mandatory) {
        socket.setRouterMandatory(mandatory);
    }

    public boolean sendMore(byte[] data) {
        return socket.sendMore(data);
    }

    public String receivString(int flags) {
        return socket.recvStr(flags);
    }

    public long getSendHighWaterMark() {
        return socket.getSndHWM();
    }

    public void setMulticastHops(long value) {
        socket.setMulticastHops(value);
    }

    public void unsubscribe(byte[] topic) {
        socket.unsubscribe(topic);
    }

    public long getReceiveBufferSize() {
        return socket.getReceiveBufferSize();
    }

    public void setBacklog(long value) {
        socket.setBacklog(value);
    }

    public void setRceiveHighWaterMark(long value) {
        socket.setRcvHWM(value);
    }

    public int bindToRandomPort(String addr) {
        return socket.bindToRandomPort(addr);
    }

    public int receive(byte[] buffer, int offset, int len, int flags) {
        return socket.recv(buffer, offset, len, flags);
    }

    public int bind(String address) {
        return socket.bind(address);
    }

    public void dump() {
        socket.dump();
    }

    public boolean send(byte[] data) {
        return socket.send(data);
    }

    public boolean monitor(String addr, int events) {
        return socket.monitor(addr, events);
    }

    public int getSendTimeOut() {
        return socket.getSendTimeOut();
    }

    public void setReceiveBufferSize(long value) {
        socket.setReceiveBufferSize(value);
    }

    public long getMulticastHops() {
        return socket.getMulticastHops();
    }

    public SelectableChannel getFileDescriptor() {
        return socket.getFD();
    }
}
