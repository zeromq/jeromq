package zmq;

import java.util.concurrent.atomic.AtomicLong;

public class Msg {

    //  Size in bytes of the largest message that is still copied around
    //  rather than being reference-counted.
    private final static int max_vsm_size = 29;
    
    public final static byte more = 1;
    public final static byte identity = 64;
    public final static byte shared = -128;
    
    private final static byte type_min = 101;
    private final static byte type_vsm = 101;
    private final static byte type_lmsg = 102;
    private final static byte type_delimiter = 103;
    private final static byte type_max = 103;
    
    //  Shared message buffer. Message data are either allocated in one
    //  continuous block along with this structure - thus avoiding one
    //  malloc/free pair or they are stored in used-supplied memory.
    //  In the latter case, ffn member stores pointer to the function to be
    //  used to deallocate the data. If the buffer is actually shared (there
    //  are at least 2 references to it) refcount member contains number of
    //  references.
    class Content
    {
        byte[] data;
        final int size;
        IMsgFree ffn;
        byte[] hint;
        final AtomicLong refcnt;
        
        public Content(int size_) {
            size = size_;
            data = new byte[size_];
            refcnt = new AtomicLong();
            hint = null;
            ffn = null;
        }

    };
    
    interface IMsgFree {
        void free(Content c);
    }
    
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
    private byte size;
    private byte[] data;
    private Content content;
    
    Msg() {
        data = new byte[max_vsm_size];
        content = null;
        size = -1;
    }
    
    boolean is_delimiter ()
    {
        return type == type_delimiter;
    }


    boolean check ()
    {
         return type >= type_min && type <= type_max;
    }

    
    void init_size (int size_)
    {
        if (size_ <= max_vsm_size) {
            type = type_vsm;
            flags = 0;
            size = (byte) size_;
        }
        else {
            type = type_lmsg;
            flags = 0;
            content = new Content(size_); 

            //content.data = null ; //XXX lmsg().content + 1;
            //content.size = size_;
            //content.ffn = null;
            //content.hint = null;
            //content.refcnt = new AtomicLong(); 
        }
    }

    byte flags ()
    {
        return flags;
    }
    
    byte type ()
    {
        return type;
    }
    
    void set_flags (byte flags_)
    {
        flags |= flags_;
    }

    
    int size ()
    {
        //  Check the validity of the message.
        assert (check ());

        switch (type) {
        case type_vsm:
            return size;
        case type_lmsg:
            return content.size;
        default:
            assert (false);
            return 0;
        }
    }
    
    
    public int init_delimiter() {
        type = type_delimiter;
        flags = 0;
        return 0;
    }

    
    byte[] data ()
    {
        //  Check the validity of the message.
        assert (check ());

        switch (type) {
        case type_vsm:
            return data;
        case type_lmsg:
            return content.data;
        default:
            assert (false);
            return null;
        }
    }

    void close ()
    {
        //  Check the validity of the message.
        if (!check ()) {
            throw new IllegalStateException();
        }

        if (type == type_lmsg) {

            //  If the content is not shared, or if it is shared and the reference
            //  count has dropped to zero, deallocate it.
            if ((flags & shared) == 0 ||
                  content.refcnt.decrementAndGet() == 0) {

                //  We used "placement new" operator to initialize the reference
                //  counter so we call the destructor explicitly now.
                //content.refcnt = null; //.~atomic_counter_t ();

                if (content.ffn != null)
                    content.ffn.free (content);
                content.data = null;
                content = null;  
            }
        }

        //  Make the message invalid.
        type = 0;

    }

    public int init() {
        type = type_vsm;
        flags = 0;
        size = 0;
        return 0;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + type + "]";
    }

    public void clone(Msg m) {
        type = m.type;
        flags = m.flags;
        size = m.size;
        data = m.data;
        content = m.content;
    }

    public void reset_flags(byte f) {
        flags = (byte) (flags & (~f));
    }

}
