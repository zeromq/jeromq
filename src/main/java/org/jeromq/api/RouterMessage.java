package org.jeromq.api;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class RouterMessage extends Message {

    public RouterMessage(List<Frame> frames) {
        super(frames);
    }

    public RouterMessage() {

    }

    public byte[] getIdentity() {
        //identity is the first frame
        return getFrames().get(0).getData();
    }

    public String getIdentityAsString() {
        return new String(getIdentity());
    }

    public String getIdentityAsString(Charset encoding) {
        return new String(getIdentity(), encoding);
    }

    public Message getPayload() {
        List<Frame> frames = getFrames();
        return new Message(frames.subList(2, frames.size()));
    }

    public static Builder build() {
        return new Builder();
    }

    public static class Builder {
        private List<Frame> frames;

        public Builder copy(Message message) {
            frames = new ArrayList<Frame>(message.getFrames());
            return this;
        }

        public Builder withAddress(byte[] address) {
            frames.add(0, new Frame(address));
            frames.add(1, new Frame(new byte[0]));
            return this;
        }

        public Builder withAddress(String address) {
            frames.add(0, new Frame(address));
            frames.add(1, new Frame(new byte[0]));
            return this;
        }

        public Builder withAddress(String address, Charset encoding) {
            frames.add(0, new Frame(address, encoding));
            frames.add(1, new Frame(new byte[0]));
            return this;
        }

        public RouterMessage create() {
            return new RouterMessage(frames);
        }
    }
}
