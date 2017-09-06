package zmq;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import zmq.io.IOThread;
import zmq.util.Errno;

//  Base class for objects forming a part of ownership hierarchy.
//  It handles initialization and destruction of such objects.
public abstract class Own extends ZObject
{
    protected final Options options;

    //  True if termination was already initiated. If so, we can destroy
    //  the object if there are no more child objects or pending term acks.
    private boolean terminating;

    //  Sequence number of the last command sent to this object.
    private final AtomicLong sendSeqnum;

    //  Sequence number of the last command processed by this object.
    private long processedSeqnum;

    //  Socket owning this object. It's responsible for shutting down
    //  this object.
    private Own owner;

    //  List of all objects owned by this socket. We are responsible
    //  for deallocating them before we quit.
    //typedef std::set <own_t*> owned_t;
    private final Set<Own> owned;

    //  Number of events we have to get before we can destroy the object.
    private int termAcks;

    public final Errno errno;

    //  Note that the owner is unspecified in the constructor.
    //  It'll be supplied later on when the object is plugged in.

    //  The object is not living within an I/O thread. It has it's own
    //  thread outside of 0MQ infrastructure.
    protected Own(Ctx parent, int tid)
    {
        super(parent, tid);
        terminating = false;
        sendSeqnum = new AtomicLong(0);
        processedSeqnum = 0;
        owner = null;
        termAcks = 0;

        options = new Options();
        errno = options.errno;
        owned = new HashSet<>();
    }

    //  The object is living within I/O thread.
    protected Own(IOThread ioThread, Options options)
    {
        super(ioThread);
        this.options = options;
        terminating = false;
        sendSeqnum = new AtomicLong(0);
        processedSeqnum = 0;
        owner = null;
        termAcks = 0;
        errno = options.errno;

        owned = new HashSet<>();
    }

    protected abstract void destroy();

    //  A place to hook in when physical destruction of the object
    //  is to be delayed.
    protected void processDestroy()
    {
        destroy();
    }

    private void setOwner(Own owner)
    {
        assert (this.owner == null);
        this.owner = owner;
    }

    //  When another owned object wants to send command to this object
    //  it calls this function to let it know it should not shut down
    //  before the command is delivered.
    protected void incSeqnum()
    {
        //  This function may be called from a different thread!
        sendSeqnum.incrementAndGet();
    }

    @Override
    protected final void processSeqnum()
    {
        //  Catch up with counter of processed commands.
        processedSeqnum++;

        //  We may have caught up and still have pending terms acks.
        checkTermAcks();
    }

    //  Launch the supplied object and become its owner.
    protected final void launchChild(Own object)
    {
        //  Specify the owner of the object.
        object.setOwner(this);

        //  Plug the object into the I/O thread.
        sendPlug(object);

        //  Take ownership of the object.
        sendOwn(this, object);
    }

    //  Terminate owned object
    protected final void termChild(Own object)
    {
        processTermReq(object);
    }

    @Override
    protected final void processTermReq(Own object)
    {
        //  When shutting down we can ignore termination requests from owned
        //  objects. The termination request was already sent to the object.
        if (terminating) {
            return;
        }

        //  If I/O object is well and alive let's ask it to terminate.

        //  If not found, we assume that termination request was already sent to
        //  the object so we can safely ignore the request.
        if (!owned.remove(object)) {
            return;
        }

        registerTermAcks(1);

        //  Note that this object is the root of the (partial shutdown) thus, its
        //  value of linger is used, rather than the value stored by the children.
        sendTerm(object, options.linger);
    }

    @Override
    protected final void processOwn(Own object)
    {
        //  If the object is already being shut down, new owned objects are
        //  immediately asked to terminate. Note that linger is set to zero.
        if (terminating) {
            registerTermAcks(1);
            sendTerm(object, 0);
            return;
        }

        //  Store the reference to the owned object.
        owned.add(object);
    }

    //  Ask owner object to terminate this object. It may take a while
    //  while actual termination is started. This function should not be
    //  called more than once.
    protected final void terminate()
    {
        //  If termination is already underway, there's no point
        //  in starting it anew.
        if (terminating) {
            return;
        }

        //  As for the root of the ownership tree, there's no one to terminate it,
        //  so it has to terminate itself.
        if (owner == null) {
            processTerm(options.linger);
            return;
        }

        //  If I am an owned object, I'll ask my owner to terminate me.
        sendTermReq(owner, this);
    }

    //  Returns true if the object is in process of termination.
    protected final boolean isTerminating()
    {
        return terminating;
    }

    //  Term handler is protected rather than private so that it can
    //  be intercepted by the derived class. This is useful to add custom
    //  steps to the beginning of the termination process.
    @Override
    protected void processTerm(int linger)
    {
        //  Double termination should never happen.
        assert (!terminating);

        //  Send termination request to all owned objects.
        for (Own it : owned) {
            sendTerm(it, linger);
        }
        registerTermAcks(owned.size());
        owned.clear();

        //  Start termination process and check whether by chance we cannot
        //  terminate immediately.
        terminating = true;
        checkTermAcks();
    }

    //  Use following two functions to wait for arbitrary events before
    //  terminating. Just add number of events to wait for using
    //  register_tem_acks functions. When event occurs, call
    //  remove_term_ack. When number of pending acks reaches zero
    //  object will be deallocated.
    final void registerTermAcks(int count)
    {
        termAcks += count;
    }

    final void unregisterTermAck()
    {
        assert (termAcks > 0);
        termAcks--;

        //  This may be a last ack we are waiting for before termination...
        checkTermAcks();
    }

    @Override
    protected final void processTermAck()
    {
        unregisterTermAck();
    }

    private void checkTermAcks()
    {
        if (terminating && processedSeqnum == sendSeqnum.get() && termAcks == 0) {
            //  Sanity check. There should be no active children at this point.
            assert (owned.isEmpty());

            //  The root object has nobody to confirm the termination to.
            //  Other nodes will confirm the termination to the owner.
            if (owner != null) {
                sendTermAck(owner);
            }

            //  Deallocate the resources.
            processDestroy();
        }
    }
}
