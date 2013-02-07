package org.jeromq.api;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Message {

    private final List<Frame> frames = new ArrayList<Frame>();

    public Message() {
    }

    public Message(byte[] firstFrame) {
        if (firstFrame != null) {
            frames.add(new Frame(firstFrame));
        }
    }

    public Message(List<Frame> frames) {
        this.frames.addAll(frames);
    }

    public Message(Message workMessage) {
        frames.addAll(workMessage.getFrames());
    }

    public Message(String firstFrame) {
        frames.add(new Frame(firstFrame));
    }

    public Message(String firstFrame, Charset encoding) {
        frames.add(new Frame(firstFrame, encoding));
    }

    public List<Frame> getFrames() {
        return Collections.unmodifiableList(frames);
    }

    public void addFrame(byte[] data) {
        if (data == null) {
            return;
        }
        frames.add(new Frame(data));
    }

    /**
     * Add a frame with default charset encoding.
     */
    public void addFrame(String data) {
        frames.add(new Frame(data.getBytes()));
    }

    public void addFrame(String data, Charset encoding) {
        frames.add(new Frame(data.getBytes(encoding)));
    }

    public void addEmptyFrame() {
        frames.add(new Frame(new byte[0]));
    }

    //todo: these getFirstFrame methods are really for debugging.  Perhaps provide a general method to pretty-print a message, rather than this very limited-use method.
    public byte[] getFirstFrame() {
        return frames.get(0).data;
    }

    public String getFirstFrameAsString() {
        return new String(frames.get(0).data);
    }

    public void addFrames(Message payload) {
        frames.addAll(payload.getFrames());
    }

    public boolean isMissing() {
        return frames.size() == 0;
    }

    public static class Frame {
        private final byte[] data;

        Frame(byte[] data) {
            this.data = data;
        }

        Frame(String data) {
            this(data.getBytes());
        }

        Frame(String data, Charset encoding) {
            this(data.getBytes(encoding));
        }

        public byte[] getData() {
            return data;
        }

        public String getString(Charset encoding) {
            return new String(data, encoding);
        }

        public String getString() {
            return new String(data);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Frame frame = (Frame) o;

            if (!Arrays.equals(data, frame.data)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return data != null ? Arrays.hashCode(data) : 0;
        }

        @Override
        public String toString() {
            if (data == null) {
                return "Frame{data=null}";
            }
            return "Frame{data=" + new String(data) + '}';
        }

        public boolean isBlank() {
            return data.length == 0;
        }
    }

}


