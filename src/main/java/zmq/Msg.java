package zmq;

import java.util.concurrent.atomic.AtomicLong;

public class Msg {

    //  Size in bytes of the largest message that is still copied around
    //  rather than being reference-counted.
    private final static int max_vsm_size = 29;
    
    public final static short more = 1;
    public final static short identity = 64;
    public final static short shared = 128;
    
    private final static int type_min = 101;
    private final static int type_vsm = 101;
    private final static int type_lmsg = 102;
    private final static int type_delimiter = 103;
    private final static int type_max = 103;
    
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
        int size;
        //msg_free_fn *ffn;
        byte[] hint;
        AtomicLong refcnt;
    };
    
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
    class Base {
        byte[] unused;
        byte type;
        short flags;
        Base() {
            //XXX unused = ByteBuffer.allocate(max_vsm_size + 1);
        }
    } ;
    
    class Vsm {
        byte[] data;
        byte size;
        byte type;
        byte flags;
        
        Vsm() {
            //XXX data = ByteBuffer.allocate(max_vsm_size);
        }
    };
    class Lmsg {
        Content content;
        byte[] unsigned; 
        byte type;
        byte flags;
        
        Lmsg() {
            //XXX unsigned = ByteBuffer.allocate(max_vsm_size + 1 - 4 ) ; //sizeof (content_t*));
        }
    } ;
    
    class Delimiter {
        byte[] unused ; // [max_vsm_size + 1];
        byte type;
        byte flags;
    };

    Base _base;
    Vsm _vsm;
    Lmsg _lmsg;
    Delimiter _delimiter;
    
    Msg() {
    }
    
    boolean is_delimiter ()
    {
        return base().type == type_delimiter;
    }


    boolean check ()
    {
         return base().type >= type_min && base().type <= type_max;
    }

    private Base base() {
        if (_base == null) _base = new Base();
        return _base;
    }

    private Vsm vsm() {
        if (_vsm == null) _vsm = new Vsm();
        return _vsm;
    }

    private Lmsg lmsg() {
        if (_lmsg == null) _lmsg = new Lmsg();
        return _lmsg;
    }
    
    private Delimiter delimiter() {
        if (_delimiter == null) _delimiter = new Delimiter();
        return _delimiter;
    }
    
    
    int init_size (int size_)
    {
        if (size_ <= max_vsm_size) {
            vsm().type = type_vsm;
            vsm().flags = 0;
            vsm().size = (byte) size_;
        }
        else {
            lmsg().type = type_lmsg;
            lmsg().flags = 0;
            lmsg().content = null ; //XXX (content_t*) malloc (sizeof (content_t) + size_);
            if (lmsg().content == null) {
                Errno.set(Errno.ENOMEM);
                return -1;
            }

            lmsg().content.data = null ; //XXX lmsg().content + 1;
            lmsg().content.size = size_;
            //lmsg().content.ffn = null;
            lmsg().content.hint = null;
            lmsg().content.refcnt = null; // XXX
            //new (&lmsg.content->refcnt) zmq::atomic_counter_t ();
        }
        return 0;
    }

    short flags ()
    {
        return base().flags;
    }
    
    void set_flags (short flags_)
    {
        base().flags |= flags_;
    }

    
    int size ()
    {
        //  Check the validity of the message.
        assert (check ());

        switch (base().type) {
        case type_vsm:
            return vsm().size;
        case type_lmsg:
            return lmsg().content.size;
        default:
            assert (false);
            return 0;
        }
    }
    
    
    public int init_delimiter() {
        delimiter().type = type_delimiter;
        delimiter().flags = 0;
        return 0;
    }

    
    byte[] data ()
    {
        //  Check the validity of the message.
        assert (check ());

        switch (base().type) {
        case type_vsm:
            return vsm().data;
        case type_lmsg:
            return lmsg().content.data;
        default:
            assert (false);
            return null;
        }
    }

    int close ()
    {
        //  Check the validity of the message.
        if (!check ()) {
            Errno.set(Errno.EFAULT);
            return -1;
        }

        if (base().type == type_lmsg) {

            //  If the content is not shared, or if it is shared and the reference
            //  count has dropped to zero, deallocate it.
            if ((lmsg().flags & shared) == 0 ||
                  lmsg().content.refcnt.decrementAndGet() == 0) {

                //  We used "placement new" operator to initialize the reference
                //  counter so we call the destructor explicitly now.
                lmsg().content.refcnt = null; //.~atomic_counter_t ();

                //if (u.lmsg.content->ffn)
                //    u.lmsg.content->ffn (u.lmsg.content->data,
                //        u.lmsg.content->hint);
                //free (u.lmsg.content);
                lmsg().content = null;  
            }
        }

        //  Make the message invalid.
        base().type = 0;

        return 0;

    }


}
