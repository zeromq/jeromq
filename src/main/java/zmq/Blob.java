package zmq;

import java.nio.ByteBuffer;

public class Blob {

    private ByteBuffer buf;
    
    public Blob(byte[] data_) {
        buf = ByteBuffer.wrap(data_);
    }
    
    public Blob(byte[] data_, int size_) {
        buf = ByteBuffer.wrap(data_,0, size_);
    }

    public Blob(int size) {
       buf = ByteBuffer.allocate(size);
    }
    
    public Blob put(byte b) {
        buf.put(b);
        return this;
    }
    
    public Blob put(byte[] data) {
        buf.put(data);
        return this;
    }
}
