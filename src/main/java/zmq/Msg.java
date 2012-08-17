package zmq;

import java.util.Arrays;

public class Msg implements IReplaceable {

    //  Size in bytes of the largest message that is still copied around
    //  rather than being reference-counted.
    private final static int max_vsm_size = 29;
    
    public final static byte more = 1;
    public final static byte identity = 64;
    public final static byte shared = -128;
    
    private final static byte type_min = 101;
    private final static byte type_vsm = 102;
    private final static byte type_lmsg = 103;
    private final static byte type_delimiter = 104;
    private final static byte type_max = 105;
    
    //  Shared message buffer. Message data are either allocated in one
    //  continuous block along with this structure - thus avoiding one
    //  malloc/free pair or they are stored in used-supplied memory.
    //  In the latter case, ffn member stores pointer to the function to be
    //  used to deallocate the data. If the buffer is actually shared (there
    //  are at least 2 references to it) refcount member contains number of
    //  references.
    //  Note that fields shared between different message types are not
    //  moved to tha parent class (msg_t). This way we ger tighter packing
    //  of the data. Shared fields can be accessed via 'base' member of
    //  the union.
    /*
    union {
        struct {
            unsigned char unused [max_vsm_size + 1];
            unsigned char type;
            unsigned char flags;
        } base;
        struct {
            unsigned char data [max_vsm_size];
            unsigned char size;
            unsigned char type;
            unsigned char flags;
        } vsm;
        struct {
            content_t *content;
            unsigned char unused [max_vsm_size + 1 - sizeof (content_t*)];
            unsigned char type;
            unsigned char flags;
        } lmsg;
        struct {
            unsigned char unused [max_vsm_size + 1];
            unsigned char type;
            unsigned char flags;
        } delimiter;
    } u;
    */

    private byte type;
    private byte flags;
    private int size;
    private byte[] data;
    
    public Msg() {
        init();
    }
    
    public Msg(int size) {
        this();
        init_size(size);
    }
    
    public Msg(Msg m) {
        clone(m);
    }
    
    public Msg(byte[] src) {
        this(src, false);
    }
    
    public Msg(byte[] src, boolean copy ) {
        this();
        size = src.length;
        if (copy)
            data = Arrays.copyOf(src, src.length);
        else
            data = src;
    }
    
    
    boolean is_delimiter ()
    {
        return type == type_delimiter;
    }


    public boolean check ()
    {
         return type >= type_min && type <= type_max;
    }

    
    private void init_size (int size_)
    {
        size = size_;
        if (size_ <= max_vsm_size) {
            type = type_vsm;
            flags = 0;
            
            data = new byte[size_];
        }
        else {
            type = type_lmsg;
            flags = 0;
            data = new byte[size_];
        }
    }

    public byte flags ()
    {
        return flags;
    }
    
    public boolean has_more ()
    {
        return (flags & Msg.more) > 0;
    }
    
    public byte type ()
    {
        return type;
    }
    
    public void set_flags (byte flags_)
    {
        flags |= flags_;
    }

    
    public int size ()
    {
        //  Check the validity of the message.
        assert (check ());

        return size;
    }
    
    
    public void init_delimiter() {
        type = type_delimiter;
        flags = 0;
    }

    
    public byte[] data ()
    {
        return data;
    }


    public void close ()
    {
        if (!check ()) {
            throw new IllegalStateException();
        }

        data = null;
    }

    private void init() {
        type = type_vsm;
        flags = 0;
        size = 0;
    }
    

    @Override
    public String toString () {
        return super.toString() + "[" + type + "," + size + "," + flags + "]";
    }

    private void clone (Msg m) {
        type = m.type;
        flags = m.flags;
        size = m.size;
        data = m.data;
    }

    public void reset_flags (byte f) {
        flags = (byte) (flags &~ f);
    }
    
    public void put(byte[] src, int i) {
        
        if (src == null)
            return;

        System.arraycopy(src, 0, data, i, src.length);
    }
    
    public void put(byte[] src, int i, int len_) {
        
        if (len_ == 0 || src == null)
            return;
        
        System.arraycopy(src, 0, data, i, len_);
    }

    public boolean is_vsm() {
        return type == type_vsm;
    }

    
    public void put(byte b) {
        data[0] = b;
    }

    public void put(byte b, int i) {
        data[i] = b;
    }

    public void put(String str, int i) {
        put(str.getBytes(), i);
    }
    
    @Override
    public void replace(Object src) {
        clone((Msg)src);
    }



}
