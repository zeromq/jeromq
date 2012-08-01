package zmq;

public class ZObject {

    //  Context provides access to the global state.
    final Ctx ctx;

    //  Thread ID of the thread the object belongs to.
    final int tid;
    
    protected ZObject(Ctx ctx, int tid) {
    	this.ctx = ctx;
    	this.tid = tid;
    }
    
    protected ZObject (ZObject parent_) {
    	this(parent_.ctx, parent_.tid);
    }    
    
    int get_tid ()
    {
        return tid;
    }
    
    Ctx.Endpoint find_endpoint (String addr_)
    {
        return ctx.find_endpoint (addr_);
    }
    
    private void send_command(Pipe destination_, Command.Type type) {
        Command cmd = new Command();
        cmd.destination = destination_;
        cmd.type = type;
        send_command (cmd);
    }
    
    void send_command (Command cmd_)
    {
        ctx.send_command (cmd_.destination.get_tid (), cmd_);
    }

    void send_activate_write (Pipe destination_,
            long msgs_read_)
    {
        Command cmd = new Command();
        cmd.destination = destination_;
        cmd.type = Command.Type.activate_write;
        cmd.msgs_read = msgs_read_;
        send_command (cmd);
    }

    void send_pipe_term_ack (Pipe destination_)
    {
        send_command(destination_, Command.Type.pipe_term_ack);
    }
    
    void send_pipe_term (Pipe destination_)
    {
        send_command(destination_, Command.Type.pipe_term);
    }

    void send_activate_read(Pipe destination_)
    {
        send_command(destination_, Command.Type.activate_read);
    }
    
    void send_bind (Own destination_, Pipe pipe_,
            boolean inc_seqnum_)
    {
        if (inc_seqnum_)
            destination_.inc_seqnum ();

        Command cmd = new Command();
        cmd.destination = destination_;
        cmd.type = Command.Type.bind;
        cmd.pipe = pipe_;
        send_command (cmd);
    }
    
    IOThread choose_io_thread (long affinity_)
    {
        return ctx.choose_io_thread (affinity_);
    }
    
    void send_plug (Own destination_) {
        send_plug(destination_, true);
    }

    void send_plug (Own destination_, boolean inc_seqnum_)
    {
        if (inc_seqnum_)
            destination_.inc_seqnum ();

        Command cmd = new Command();
        cmd.destination = destination_;
        cmd.type = Command.Type.plug;
        send_command (cmd);
    }
    
    void send_own (Own destination_, Own object_)
    {
        destination_.inc_seqnum ();
        Command cmd = new Command();
        cmd.destination = destination_;
        cmd.type = Command.Type.own;
        cmd.object = object_;
        send_command (cmd);
    }
    
    void send_reap (SocketBase socket_)
    {
        Command cmd = new Command();
        cmd.destination = ctx.get_reaper ();
        cmd.type = Command.Type.reap;
        cmd.socket = socket_;
        send_command (cmd);
    }
    
    void send_stop ()
    {
        //  'stop' command goes always from administrative thread to
        //  the current object. 
        Command cmd = new Command();
        cmd.destination = this;
        cmd.type = Command.Type.stop;
        ctx.send_command (tid, cmd);
    }



    public void process_command(Command cmd_) {
        switch (cmd_.type) {

        case activate_read:
            process_activate_read ();
            break;

        case activate_write:
            process_activate_write (cmd_.msgs_read);
            break;

        case stop:
            process_stop ();
            break;

        case plug:
            process_plug ();
            process_seqnum ();
            break;

        case own:
            process_own (cmd_.object);
            process_seqnum ();
            break;

        case attach:
            process_attach (cmd_.engine);
            process_seqnum ();
            break;

        case bind:
            process_bind (cmd_.pipe);
            process_seqnum ();
            break;

        case hiccup:
            process_hiccup (cmd_.hiccup_pipe);
            break;
    
        case pipe_term:
            process_pipe_term ();
            break;
    
        case pipe_term_ack:
            process_pipe_term_ack ();
            break;
    
        case term_req:
            process_term_req (cmd_.object);
            break;
    
        case term:
            process_term (cmd_.linger);
            break;
    
        case term_ack:
            process_term_ack ();
            break;
    
        case reap:
            process_reap (cmd_.socket);
            break;
            
        case reaped:
            process_reaped ();
            break;
    
        default:
            assert (false);
        }

    }

    private void process_reap(SocketBase socket) {
        assert (false);
    }

    private void process_term(int linger) {
        assert (false);
    }

    private void process_term_req(Own object) {
        assert (false);
    }

    private void process_hiccup(Object hiccup_pipe) {
        assert (false);
    }

    private void process_bind(Pipe pipe) {
        assert (false);
    }

    private void process_attach(IEngine engine) {
        assert (false);
    }

    void process_own(Own object) {
        assert (false);
    }

    void process_activate_read() {
        assert (false);
    }

    void process_reaped() {
        assert (false);
    }

    void process_term_ack() {
        assert (false);
    }
    
    void process_pipe_term_ack() {
        assert (false);
    }
    
    void process_pipe_term() {
        assert (false);
    }
    
    void process_stop() {
        assert (false);
    }
    
    void process_seqnum() {
        assert (false);
    }
    
    void process_plug() {
        assert (false);
    }
    
    void process_activate_write(long msgs_read_) {
        assert (false);
    }
}
