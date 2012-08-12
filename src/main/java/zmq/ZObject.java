package zmq;

public class ZObject {


    //  Context provides access to the global state.
    final private Ctx ctx;

    //  Thread ID of the thread the object belongs to.
    final private int tid;
    
    protected ZObject(Ctx ctx, int tid) {
    	this.ctx = ctx;
    	this.tid = tid;
    }
    
    protected ZObject (ZObject parent_) {
    	this(parent_.ctx, parent_.tid);
    }    
    
    protected int get_tid ()
    {
        return tid;
    }
    
    protected Ctx get_ctx ()
    {
        return ctx;
    }
    
    protected Ctx.Endpoint find_endpoint (String addr_)
    {
        return ctx.find_endpoint (addr_);
    }
    
    protected void destroy_socket (SocketBase socket_)
    {
        ctx.destroy_socket (socket_);
    }

    void register_endpoint (String addr_, Ctx.Endpoint endpoint_)
    {
        ctx.register_endpoints (addr_, endpoint_);
    }
    
    void unregister_endpoints (SocketBase socket_)
    {
        ctx.unregister_endpoints (socket_);
    }

    
    private void send_command(Pipe destination_, Command.Type type) {
        Command cmd = new Command(destination_, type);
        send_command (cmd);
    }
    
    protected void send_command (Command cmd_)
    {
        ctx.send_command (cmd_.destination().get_tid (), cmd_);
    }

    protected void send_activate_write (Pipe destination_,
            long msgs_read_)
    {
        Command cmd = new Command(destination_, Command.Type.activate_write, msgs_read_);
        send_command (cmd);
    }

    protected void send_pipe_term_ack (Pipe destination_)
    {
        send_command(destination_, Command.Type.pipe_term_ack);
    }
    
    protected void send_pipe_term (Pipe destination_)
    {
        send_command(destination_, Command.Type.pipe_term);
    }

    protected void send_activate_read(Pipe destination_)
    {
        send_command(destination_, Command.Type.activate_read);
    }
    
    protected void send_bind (Own destination_, Pipe pipe_)
    {
        send_bind(destination_, pipe_, true);
    }
    
    protected void send_bind (Own destination_, Pipe pipe_,
            boolean inc_seqnum_)
    {
        if (inc_seqnum_)
            destination_.inc_seqnum ();

        Command cmd = new Command(destination_, Command.Type.bind, pipe_);
        send_command (cmd);
    }
    
    protected IOThread choose_io_thread (long affinity_)
    {
        return ctx.choose_io_thread (affinity_);
    }
    
    protected void send_plug (Own destination_) {
        send_plug(destination_, true);
    }

    protected void send_plug (Own destination_, boolean inc_seqnum_)
    {
        if (inc_seqnum_)
            destination_.inc_seqnum ();

        Command cmd = new Command(destination_, Command.Type.plug);
        send_command (cmd);
    }
    
    protected void send_own (Own destination_, Own object_)
    {
        destination_.inc_seqnum ();
        Command cmd = new Command(destination_, Command.Type.own, object_);
        send_command (cmd);
    }
    
    protected void send_reap (SocketBase socket_)
    {
        Command cmd = new Command(ctx.get_reaper (), Command.Type.reap, socket_);
        send_command (cmd);
    }
    
    
    protected void send_reaped ()
    {
        Command cmd = new Command(ctx.get_reaper (), Command.Type.reaped);
        send_command (cmd);
    }

    protected void send_stop ()
    {
        //  'stop' command goes always from administrative thread to
        //  the current object. 
        Command cmd = new Command(this, Command.Type.stop);
        ctx.send_command (tid, cmd);
    }

    protected void send_term (Own destination_, int linger_)
    {
        Command cmd = new Command(destination_, Command.Type.term, linger_);
        send_command (cmd);
    }

    protected void send_term_ack (Own destination_)
    {   
        Command cmd = new Command(destination_, Command.Type.term_ack);
        send_command (cmd);
    }   

    protected void send_term_req (Own destination_,
            Own object_)
    {
        Command cmd = new Command(destination_, Command.Type.term_req, object_);
        send_command (cmd);
    }
    
    protected void send_attach (SessionBase destination_, IEngine engine_) {
        send_attach(destination_, engine_, true);
    }
    
    protected void send_attach (SessionBase destination_,
            IEngine engine_, boolean inc_seqnum_)
    {
        if (inc_seqnum_)
            destination_.inc_seqnum ();

        Command cmd = new Command(destination_, Command.Type.attach, engine_);
        send_command (cmd);
    }

    protected void send_done ()
    {
        Command cmd = new Command(null, Command.Type.done);
        ctx.send_command(Ctx.term_tid, cmd);
    }

    protected void send_hiccup (Pipe destination_, Object pipe_)
    {
        Command cmd = new Command(destination_, Command.Type.hiccup, pipe_);
        send_command (cmd);
    }



    protected void process_command(Command cmd_) {
        switch (cmd_.type()) {

        case activate_read:
            process_activate_read ();
            break;

        case activate_write:
            process_activate_write ((Long)cmd_.arg);
            break;

        case stop:
            process_stop ();
            break;

        case plug:
            process_plug ();
            process_seqnum ();
            break;

        case own:
            process_own ((Own)cmd_.arg);
            process_seqnum ();
            break;

        case attach:
            process_attach ((IEngine)cmd_.arg);
            process_seqnum ();
            break;

        case bind:
            process_bind ((Pipe)cmd_.arg);
            process_seqnum ();
            break;

        case hiccup:
            process_hiccup (cmd_.arg);
            break;
    
        case pipe_term:
            process_pipe_term ();
            break;
    
        case pipe_term_ack:
            process_pipe_term_ack ();
            break;
    
        case term_req:
            process_term_req ((Own)cmd_.arg);
            break;
    
        case term:
            process_term ((Integer)cmd_.arg);
            break;
    
        case term_ack:
            process_term_ack ();
            break;
    
        case reap:
            process_reap ((SocketBase)cmd_.arg);
            break;
            
        case reaped:
            process_reaped ();
            break;
    
        default:
            throw new UnsupportedOperationException();
        }

    }

    protected void process_reap(SocketBase socket) {
        throw new UnsupportedOperationException();
    }

    protected void process_term(int linger) {
        throw new UnsupportedOperationException();
    }

    protected void process_term_req(Own object) {
        throw new UnsupportedOperationException();
    }

    protected void process_hiccup(Object hiccup_pipe) {
        throw new UnsupportedOperationException();
    }

    protected void process_bind(Pipe pipe) {
        throw new UnsupportedOperationException();
    }

    protected void process_attach(IEngine engine) {
        throw new UnsupportedOperationException();
    }

    protected void process_own(Own object) {
        throw new UnsupportedOperationException();
    }

    protected void process_activate_read() {
        throw new UnsupportedOperationException();
    }

    protected void process_reaped() {
        throw new UnsupportedOperationException();
    }

    protected void process_term_ack() {
        throw new UnsupportedOperationException();
    }
    
    protected void process_pipe_term_ack() {
        throw new UnsupportedOperationException();
    }
    
    protected void process_pipe_term() {
        throw new UnsupportedOperationException();
    }
    
    protected void process_stop() {
        throw new UnsupportedOperationException();
    }
    
    protected void process_seqnum() {
        throw new UnsupportedOperationException();
    }
    
    protected void process_plug() {
        throw new UnsupportedOperationException();
    }
    
    protected void process_activate_write(long msgs_read_) {
        throw new UnsupportedOperationException();
    }
    
    //@Override
    //public String toString() {
    //    return super.toString() + "[" + tid + "]";
    //}
}
