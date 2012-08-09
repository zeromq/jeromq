package zmq;

import java.nio.ByteBuffer;

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
    /*
    class Content
    {
        ByteBuffer data;
        final int size;
        IMsgFree ffn;
        byte[] hint;
        final AtomicLong refcnt;
        
        public Content(int size_) {
            size = size_;
            data = ByteBuffer.allocate(size_);
            refcnt = new AtomicLong();
            hint = null;
            ffn = null;
        }

    };
    
    interface IMsgFree {
        void free(Content c);
    }
    */
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
    private ByteBuffer data;
    private ByteBuffer content;
    
    public Msg() {
        data = ByteBuffer.allocate(max_vsm_size);
        init();
    }
    
    public Msg(int size) {
        this();
        init_size(size);
    }
    
    public Msg(Msg m) {
        data = ByteBuffer.allocate(max_vsm_size);
        clone(m);
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
        if (size_ <= max_vsm_size) {
            type = type_vsm;
            flags = 0;
            size = size_;
            data.limit(size_);
        }
        else {
            type = type_lmsg;
            flags = 0;
            content = ByteBuffer.allocate(size_); 

            //content.data = null ; //XXX lmsg().content + 1;
            //content.size = size_;
            //content.ffn = null;
            //content.hint = null;
            //content.refcnt = new AtomicLong(); 
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

    
    public ByteBuffer data ()
    {
        return data(true);
    }
    
    public ByteBuffer data(boolean rewind) {
        //  Check the validity of the message.
        assert (check ());

        ByteBuffer b = get_buffer();

        if (rewind) {
            b.rewind();
        }
        return b;
    }

    public void close ()
    {
        //  Check the validity of the message.
        if (!check ()) {
            throw new IllegalStateException();
        }

        throw new IllegalStateException();
        //content = null;
        //  Make the message invalid.
        //type = 0;
    }

    private void init() {
        type = type_vsm;
        flags = 0;
        size = 0;
        data.clear();
    }
    

    @Override
    public String toString () {
        return super.toString() + "[" + type + "]";
    }

    private void clone (Msg m) {
        type = m.type;
        flags = m.flags;
        size = m.size;
        data.clear();
        if (type == type_vsm) {
            m.data.rewind();
            data.put(m.data);
            data.flip();
            content = null;
        } else if (type == type_lmsg) {
            content = m.content.duplicate();
        }
    }

    public void reset_flags (byte f) {
        flags = (byte) (flags &~ f);
    }

    /*
    public void move (Msg src_)
    {
        //  Check the validity of the source.
        if (!src_.check ()) {
            throw new IllegalStateException();
        }

        close ();

        clone(src_);

        src_.init ();
    }
    */
    
    private ByteBuffer get_buffer() {

        ByteBuffer b = null;
        switch (type) {
        case type_vsm:
            b = data;
            break;
        case type_lmsg:
            b = content;
            break;
        default:
            assert (false);
        }
        return b;
    }

    public void put(ByteBuffer buf) {
        if (type == type_vsm) {
            data.put(buf);
        } else {
            content = buf.duplicate();
        }
    }
    
    public void put(byte[] src) {
        put(src, 0, src.length);
    }

    public void put(byte[] src, int start, int len_) {
        
        if (len_ == 0 || src == null)
            return;
        
        if (type == type_vsm)
            data.put(src, start, len_);
        else
            content = ByteBuffer.wrap(src, start, len_);
    }

    @Override
    public void replace(Object src) {
        clone((Msg)src);
    }



}
