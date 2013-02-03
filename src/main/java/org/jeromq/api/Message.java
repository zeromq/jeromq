package org.jeromq.api;

import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;

public class Message implements Iterable<ZFrame>, Deque<ZFrame> {
    private final ZMsg zMsg;

    public Message(ZMsg zMsg) {
        this.zMsg = zMsg;
    }

    public void destroy() {
        zMsg.destroy();
    }

    public static Message recvMsg(Socket socket) {
        return new Message(ZMsg.recvMsg(socket.getDelegate()));
    }

    public static Message receiveMessage(ZMQ.Socket socket, SendReceiveOption flag) {
        return new Message(ZMsg.recvMsg(socket, flag.getCValue()));
    }

    public ZFrame first() {
        return zMsg.first();
    }

    public boolean add(String stringValue) {
        return zMsg.add(stringValue);
    }

    @Override
    public void clear() {
        zMsg.clear();
    }

    public boolean save(DataOutputStream file) {
        return ZMsg.save(zMsg, file);
    }

    public static Message load(DataInputStream file) {
        return new Message(ZMsg.load(file));
    }

    @Override
    public ZFrame getFirst() {
        return zMsg.getFirst();
    }

    @Override
    public ZFrame pollLast() {
        return zMsg.pollLast();
    }

    public void addLast(String stringValue) {
        zMsg.addLast(stringValue);
    }

    @Override
    public ZFrame getLast() {
        return zMsg.getLast();
    }

    public long contentSize() {
        return zMsg.contentSize();
    }

    public void wrap(ZFrame frame) {
        zMsg.wrap(frame);
    }

    @Override
    public boolean offerLast(ZFrame e) {
        return zMsg.offerLast(e);
    }

    public ZFrame last() {
        return zMsg.last();
    }

    public Message duplicate() {
        return new Message(zMsg.duplicate());
    }

    @Override
    public void addLast(ZFrame e) {
        zMsg.addLast(e);
    }

    public boolean add(byte[] data) {
        return zMsg.add(data);
    }

    public void dump(Appendable out) {
        zMsg.dump(out);
    }

    public void addLast(byte[] data) {
        zMsg.addLast(data);
    }

    @Override
    public ZFrame peekLast() {
        return zMsg.peekLast();
    }

    public void addString(String str) {
        zMsg.addString(str);
    }

    @Override
    public void push(ZFrame e) {
        zMsg.push(e);
    }

    @Override
    public boolean offerFirst(ZFrame e) {
        return zMsg.offerFirst(e);
    }

    @Override
    public boolean offer(ZFrame e) {
        return zMsg.offer(e);
    }

    @Override
    public int size() {
        return zMsg.size();
    }

    public void addFirst(byte[] data) {
        zMsg.addFirst(data);
    }

    @Override
    public boolean removeAll(Collection<?> values) {
        return zMsg.removeAll(values);
    }

    @Override
    public void addFirst(ZFrame frame) {
        zMsg.addFirst(frame);
    }

    @Override
    public ZFrame pop() {
        return zMsg.pop();
    }

    @Override
    public <T> T[] toArray(T[] base) {
        return zMsg.toArray(base);
    }

    @Override
    public ZFrame removeFirst() {
        return zMsg.removeFirst();
    }

    @Override
    public boolean add(ZFrame frame) {
        return zMsg.add(frame);
    }

    @Override
    public Iterator<ZFrame> iterator() {
        return zMsg.iterator();
    }

    @Override
    public boolean remove(Object o) {
        return zMsg.remove(o);
    }

    public String popString() {
        return zMsg.popString();
    }

    public boolean send(Socket socket) {
        return zMsg.send(socket.getDelegate());
    }

    public void dump() {
        zMsg.dump();
    }

    @Override
    public ZFrame element() {
        return zMsg.element();
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        return zMsg.removeLastOccurrence(o);
    }

    @Override
    public boolean isEmpty() {
        return zMsg.isEmpty();
    }

    @Override
    public Object[] toArray() {
        return zMsg.toArray();
    }

    @Override
    public boolean containsAll(Collection<?> values) {
        return zMsg.containsAll(values);
    }

    @Override
    public ZFrame remove() {
        return zMsg.remove();
    }

    public void push(String string) {
        zMsg.push(string);
    }

    public ZFrame unwrap() {
        return zMsg.unwrap();
    }

    @Override
    public boolean retainAll(Collection<?> values) {
        return zMsg.retainAll(values);
    }

    @Override
    public ZFrame pollFirst() {
        return zMsg.pollFirst();
    }

    @Override
    public boolean addAll(Collection<? extends ZFrame> values) {
        return zMsg.addAll(values);
    }

    @Override
    public boolean contains(Object o) {
        return zMsg.contains(o);
    }

    @Override
    public Iterator<ZFrame> descendingIterator() {
        return zMsg.descendingIterator();
    }

    @Override
    public ZFrame removeLast() {
        return zMsg.removeLast();
    }

    @Override
    public ZFrame peek() {
        return zMsg.peek();
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        return zMsg.removeFirstOccurrence(o);
    }

    @Override
    public ZFrame peekFirst() {
        return zMsg.peekFirst();
    }

    public void addFirst(String stringValue) {
        zMsg.addFirst(stringValue);
    }

    @Override
    public ZFrame poll() {
        return zMsg.poll();
    }

    public static Message newStringMsg(String... strings) {
        return new Message(ZMsg.newStringMsg(strings));
    }

    public void push(byte[] data) {
        zMsg.push(data);
    }

    /**
     * Send message to 0MQ socket, destroys contents after sending if destroy param is set to true.
     * If the message has no frames, sends nothing but still destroy()s the ZMsg object
     * @param socket 0MQ socket to send on.
     * @return true if send is success, false otherwise
     */
    public boolean send(Socket socket, boolean destroy) {
        return zMsg.send(socket.getDelegate(), destroy);
    }
}


