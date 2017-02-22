package zmq;

//  Note that pipe can be stored in three different arrays.
//  The array of inbound pipes (1), the array of outbound pipes (2) and
//  the generic array of pipes to deallocate (3).
class Pipe extends ZObject
{
    interface IPipeEvents
    {
        void readActivated(Pipe pipe);
        void writeActivated(Pipe pipe);
        void hiccuped(Pipe pipe);
        void pipeTerminated(Pipe pipe);
    }

    //  Underlying pipes for both directions.
    private YPipe<Msg> inpipe;
    private YPipe<Msg> outpipe;

    //  Can the pipe be read from / written to?
    private boolean inActive;
    private boolean outActive;

    //  High watermark for the outbound pipe.
    private int hwm;

    //  Low watermark for the inbound pipe.
    private int lwm;

    //  Number of messages read and written so far.
    private long msgsRead;
    private long msgsWritten;

    //  Last received peer's msgsRead. The actual number in the peer
    //  can be higher at the moment.
    private long peersMsgsRead;

    //  The pipe object on the other side of the pipepair.
    private Pipe peer;

    //  Sink to send events to.
    private IPipeEvents sink;

    //  State of the pipe endpoint. Active is common state before any
    //  termination begins. Delimited means that delimiter was read from
    //  pipe before term command was received. Pending means that term
    //  command was already received from the peer but there are still
    //  pending messages to read. Terminating means that all pending
    //  messages were already read and all we are waiting for is ack from
    //  the peer. Terminated means that 'terminate' was explicitly called
    //  by the user. Double_terminated means that user called 'terminate'
    //  and then we've got term command from the peer as well.
    enum State {
        ACTIVE,
        DELIMITED,
        PENDING,
        TERMINATING,
        TERMINATED,
        DOUBLE_TERMINATED
    }
    private State state;

    //  If true, we receive all the pending inbound messages before
    //  terminating. If false, we terminate immediately when the peer
    //  asks us to.
    private boolean delay;

    //  Identity of the writer. Used uniquely by the reader side.
    private Blob identity;

    // JeroMQ only
    private ZObject parent;

    //  Constructor is private. Pipe can only be created using
    //  pipepair function.
    private Pipe(ZObject parent, YPipe<Msg> inpipe, YPipe<Msg> outpipe,
              int inhwm, int outhwm, boolean delay)
    {
        super(parent);
        this.inpipe = inpipe;
        this.outpipe = outpipe;
        inActive = true;
        outActive = true;
        hwm = outhwm;
        lwm = computeLwm(inhwm);
        msgsRead = 0;
        msgsWritten = 0;
        peersMsgsRead = 0;
        peer = null;
        sink = null;
        state = State.ACTIVE;
        this.delay = delay;

        this.parent = parent;
    }

    //  Create a pipepair for bi-directional transfer of messages.
    //  First HWM is for messages passed from first pipe to the second pipe.
    //  Second HWM is for messages passed from second pipe to the first pipe.
    //  Delay specifies how the pipe behaves when the peer terminates. If true
    //  pipe receives all the pending messages before terminating, otherwise it
    //  terminates straight away.
    public static void pipepair(ZObject[] parents, Pipe[] pipes, int[] hwms,
            boolean[] delays)
    {
        //   Creates two pipe objects. These objects are connected by two ypipes,
        //   each to pass messages in one direction.

        YPipe<Msg> upipe1 = new YPipe<Msg>(Config.MESSAGE_PIPE_GRANULARITY.getValue());
        YPipe<Msg> upipe2 = new YPipe<Msg>(Config.MESSAGE_PIPE_GRANULARITY.getValue());

        pipes[0] = new Pipe(parents[0], upipe1, upipe2,
            hwms[1], hwms[0], delays[0]);
        pipes[1] = new Pipe(parents[1], upipe2, upipe1,
            hwms[0], hwms[1], delays[1]);

        pipes[0].setPeer(pipes[1]);
        pipes[1].setPeer(pipes[0]);

    }

    //  Pipepair uses this function to let us know about
    //  the peer pipe object.
    private void setPeer(Pipe peer)
    {
        //  Peer can be set once only.
        assert (peer != null);
        this.peer = peer;
    }

    //  Specifies the object to send events to.
    public void setEventSink(IPipeEvents sink)
    {
        assert (this.sink == null);
        this.sink = sink;
    }

    //  Pipe endpoint can store an opaque ID to be used by its clients.
    public void setIdentity(Blob identity)
    {
        this.identity = identity;
    }

    public Blob getIdentity()
    {
        return identity;
    }

    //  Returns true if there is at least one message to read in the pipe.
    public boolean checkRead()
    {
        if (!inActive || (state != State.ACTIVE && state != State.PENDING)) {
            return false;
        }

        //  Check if there's an item in the pipe.
        if (!inpipe.checkRead()) {
            inActive = false;
            return false;
        }

        //  If the next item in the pipe is message delimiter,
        //  initiate termination process.
        if (isDelimiter(inpipe.probe())) {
            Msg msg = inpipe.read();
            assert (msg != null);
            delimit();
            return false;
        }

        return true;
    }

    //  Reads a message to the underlying pipe.
    public Msg read()
    {
        if (!inActive || (state != State.ACTIVE && state != State.PENDING)) {
            return null;
        }

        Msg msg = inpipe.read();

        if (msg == null) {
            inActive = false;
            return null;
        }

        //  If delimiter was read, start termination process of the pipe.
        if (msg.isDelimiter()) {
            delimit();
            return null;
        }

        if (!msg.hasMore()) {
            msgsRead++;
        }

        if (lwm > 0 && msgsRead % lwm == 0) {
            sendActivateWrite(peer, msgsRead);
        }

        return msg;
    }

    //  Checks whether messages can be written to the pipe. If writing
    //  the message would cause high watermark the function returns false.
    public boolean checkWrite()
    {
        if (!outActive || state != State.ACTIVE) {
            return false;
        }

        boolean full = hwm > 0 && msgsWritten - peersMsgsRead == (long) (hwm);

        if (full) {
            outActive = false;
            return false;
        }

        return true;
    }

    //  Writes a message to the underlying pipe. Returns false if the
    //  message cannot be written because high watermark was reached.
    public boolean write(Msg msg)
    {
        if (!checkWrite()) {
            return false;
        }

        boolean more = msg.hasMore();
        outpipe.write(msg, more);

        if (!more) {
            msgsWritten++;
        }

        return true;
    }

    //  Remove unfinished parts of the outbound message from the pipe.
    public void rollback()
    {
        //  Remove incomplete message from the outbound pipe.
        Msg msg;
        if (outpipe != null) {
            while ((msg = outpipe.unwrite()) != null) {
                assert ((msg.flags() & Msg.MORE) > 0);
            }
        }
    }

    //  Flush the messages downsteam.
    public void flush()
    {
        //  The peer does not exist anymore at this point.
        if (state == State.TERMINATING) {
            return;
        }

        if (outpipe != null && !outpipe.flush()) {
            sendActivateRead(peer);
        }
    }

    @Override
    protected void processActivateRead()
    {
        if (!inActive && (state == State.ACTIVE || state == State.PENDING)) {
            inActive = true;
            sink.readActivated(this);
        }
    }

    @Override
    protected void processActivateWrite(long msgsRead)
    {
        //  Remember the peers's message sequence number.
        peersMsgsRead = msgsRead;

        if (!outActive && state == State.ACTIVE) {
            outActive = true;
            sink.writeActivated(this);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void processHiccup(Object pipe)
    {
        //  Destroy old outpipe. Note that the read end of the pipe was already
        //  migrated to this thread.
        assert (outpipe != null);
        outpipe.flush();
        while (outpipe.read() != null) {
            // do nothing
        }

        //  Plug in the new outpipe.
        assert (pipe != null);
        outpipe = (YPipe<Msg>) pipe;
        outActive = true;

        //  If appropriate, notify the user about the hiccup.
        if (state == State.ACTIVE) {
            sink.hiccuped(this);
        }
    }

    @Override
    protected void processPipeTerm()
    {
        //  This is the simple case of peer-induced termination. If there are no
        //  more pending messages to read, or if the pipe was configured to drop
        //  pending messages, we can move directly to the terminating state.
        //  Otherwise we'll hang up in pending state till all the pending messages
        //  are sent.
        if (state == State.ACTIVE) {
            if (!delay) {
                state = State.TERMINATING;
                outpipe = null;
                sendPipeTermAck(peer);
            }
            else {
                state = State.PENDING;
            }
            return;
        }

        //  Delimiter happened to arrive before the term command. Now we have the
        //  term command as well, so we can move straight to terminating state.
        if (state == State.DELIMITED) {
            state = State.TERMINATING;
            outpipe = null;
            sendPipeTermAck(peer);
            return;
        }

        //  This is the case where both ends of the pipe are closed in parallel.
        //  We simply reply to the request by ack and continue waiting for our
        //  own ack.
        if (state == State.TERMINATED) {
            state = State.DOUBLE_TERMINATED;
            outpipe = null;
            sendPipeTermAck(peer);
            return;
        }

        //  pipe_term is invalid in other states.
        assert (false);
    }

    @Override
    protected void processPipeTermAck()
    {
        //  Notify the user that all the references to the pipe should be dropped.
        assert (sink != null);
        sink.pipeTerminated(this);

        //  In terminating and double_terminated states there's nothing to do.
        //  Simply deallocate the pipe. In terminated state we have to ack the
        //  peer before deallocating this side of the pipe. All the other states
        //  are invalid.
        if (state == State.TERMINATED) {
            outpipe = null;
            sendPipeTermAck(peer);
        }
        else {
            assert (state == State.TERMINATING || state == State.DOUBLE_TERMINATED);
        }

        // If the inbound pipe has already been deallocated, then we're done.
        if (inpipe == null) {
            return;
        }

        //  We'll deallocate the inbound pipe, the peer will deallocate the outbound
        //  pipe (which is an inbound pipe from its point of view).
        //  First, delete all the unread messages in the pipe. We have to do it by
        //  hand because msg_t doesn't have automatic destructor. Then deallocate
        //  the ypipe itself.
        while (inpipe.read() != null) {
            // do nothing
        }

        //  Deallocate the pipe object
        inpipe = null;
    }

    //  Ask pipe to terminate. The termination will happen asynchronously
    //  and user will be notified about actual deallocation by 'terminated'
    //  event. If delay is true, the pending messages will be processed
    //  before actual shutdown.
    public void terminate(boolean delay)
    {
        //  Overload the value specified at pipe creation.
        this.delay = delay;

        //  If terminate was already called, we can ignore the duplicit invocation.
        if (state == State.TERMINATED || state == State.DOUBLE_TERMINATED) {
            return;
        }
        //  If the pipe is in the final phase of async termination, it's going to
        //  closed anyway. No need to do anything special here.
        else if (state == State.TERMINATING) {
            return;
        }
        //  The simple sync termination case. Ask the peer to terminate and wait
        //  for the ack.
        else if (state == State.ACTIVE) {
            sendPipeTerm(peer);
            state = State.TERMINATED;
        }
        //  There are still pending messages available, but the user calls
        //  'terminate'. We can act as if all the pending messages were read.
        else if (state == State.PENDING && !this.delay) {
            outpipe = null;
            sendPipeTermAck(peer);
            state = State.TERMINATING;
        }
        //  If there are pending messages still available, do nothing.
        else if (state == State.PENDING) {
            // do nothing
        }
        //  We've already got delimiter, but not term command yet. We can ignore
        //  the delimiter and ack synchronously terminate as if we were in
        //  active state.
        else if (state == State.DELIMITED) {
            sendPipeTerm(peer);
            state = State.TERMINATED;
        }
        //  There are no other states.
        else {
            assert (false);
        }

        //  Stop outbound flow of messages.
        outActive = false;

        if (outpipe != null) {
            //  Drop any unfinished outbound messages.
            rollback();

            //  Write the delimiter into the pipe. Note that watermarks are not
            //  checked; thus the delimiter can be written even when the pipe is full.

            Msg msg = new Msg();
            msg.initDelimiter();
            outpipe.write(msg, false);
            flush();
        }
    }

    //  Returns true if the message is delimiter; false otherwise.
    private static boolean isDelimiter(Msg msg)
    {
        return msg.isDelimiter();
    }

    //  Computes appropriate low watermark from the given high watermark.
    private static int computeLwm(int hwm)
    {
        //  Compute the low water mark. Following point should be taken
        //  into consideration:
        //
        //  1. LWM has to be less than HWM.
        //  2. LWM cannot be set to very low value (such as zero) as after filling
        //     the queue it would start to refill only after all the messages are
        //     read from it and thus unnecessarily hold the progress back.
        //  3. LWM cannot be set to very high value (such as HWM-1) as it would
        //     result in lock-step filling of the queue - if a single message is
        //     read from a full queue, writer thread is resumed to write exactly one
        //     message to the queue and go back to sleep immediately. This would
        //     result in low performance.
        //
        //  Given the 3. it would be good to keep HWM and LWM as far apart as
        //  possible to reduce the thread switching overhead to almost zero,
        //  say HWM-LWM should be max_wm_delta.
        //
        //  That done, we still we have to account for the cases where
        //  HWM < max_wm_delta thus driving LWM to negative numbers.
        //  Let's make LWM 1/2 of HWM in such cases.

        return (hwm > Config.MAX_WM_DELTA.getValue() * 2) ?
            hwm - Config.MAX_WM_DELTA.getValue() : (hwm + 1) / 2;
    }

    //  Handler for delimiter read from the pipe.
    private void delimit()
    {
        if (state == State.ACTIVE) {
            state = State.DELIMITED;
            return;
        }

        if (state == State.PENDING) {
            outpipe = null;
            sendPipeTermAck(peer);
            state = State.TERMINATING;
            return;
        }

        //  Delimiter in any other state is invalid.
        assert (false);
    }

    //  Temporaraily disconnects the inbound message stream and drops
    //  all the messages on the fly. Causes 'hiccuped' event to be generated
    //  in the peer.
    public void hiccup()
    {
        //  If termination is already under way do nothing.
        if (state != State.ACTIVE) {
            return;
        }

        //  We'll drop the pointer to the inpipe. From now on, the peer is
        //  responsible for deallocating it.
        inpipe = null;

        //  Create new inpipe.
        inpipe = new YPipe<Msg>(Config.MESSAGE_PIPE_GRANULARITY.getValue());
        inActive = true;

        //  Notify the peer about the hiccup.
        sendHiccup(peer, inpipe);
    }

    public boolean checkHwm()
    {
        boolean full = hwm > 0 && (msgsWritten - peersMsgsRead) >= (hwm - 1);
        return !full;
    }

    @Override
    public String toString()
    {
        return super.toString() + "[" + parent + "]";
    }
}
