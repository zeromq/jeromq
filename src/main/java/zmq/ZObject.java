package zmq;

//  Base class for all objects that participate in inter-thread
//  communication.
public abstract class ZObject
{
    //  Context provides access to the global state.
    private final Ctx ctx;

    //  Thread ID of the thread the object belongs to.
    private final int tid;

    protected ZObject(Ctx ctx, int tid)
    {
        this.ctx = ctx;
        this.tid = tid;
    }

    protected ZObject(ZObject parent)
    {
        this(parent.ctx, parent.tid);
    }

    protected int getTid()
    {
        return tid;
    }

    protected Ctx getCtx()
    {
        return ctx;
    }

    protected void processCommand(Command cmd)
    {
        switch (cmd.type()) {
        case ACTIVATE_READ:
            processActivateRead();
            break;

        case ACTIVATE_WRITE:
            processActivateWrite((Long) cmd.arg);
            break;

        case STOP:
            processStop();
            break;

        case PLUG:
            processPlug();
            processSeqnum();
            break;

        case OWN:
            processOwn((Own) cmd.arg);
            processSeqnum();
            break;

        case ATTACH:
            processAttach((IEngine) cmd.arg);
            processSeqnum();
            break;

        case BIND:
            processBind((Pipe) cmd.arg);
            processSeqnum();
            break;

        case HICCUP:
            processHiccup(cmd.arg);
            break;

        case PIPE_TERM:
            processPipeTerm();
            break;

        case PIPE_TERM_ACK:
            processPipeTermAck();
            break;

        case TERM_REQ:
            processTermReq((Own) cmd.arg);
            break;

        case TERM:
            processTerm((Integer) cmd.arg);
            break;

        case TERM_ACK:
            processTermAck();
            break;

        case REAP:
            processReap((SocketBase) cmd.arg);
            break;

        case REAPED:
            processReaped();
            break;

        default:
            throw new IllegalArgumentException();
        }
    }

    protected boolean registerEndpoint(String addr, Ctx.Endpoint endpoint)
    {
        return ctx.registerEndpoint(addr, endpoint);
    }

    protected void unregisterEndpoints(SocketBase socket)
    {
        ctx.unregisterEndpoints(socket);
    }

    protected Ctx.Endpoint findEndpoint(String addr)
    {
        return ctx.findEndpoint(addr);
    }

    protected void destroySocket(SocketBase socket)
    {
        ctx.destroySocket(socket);
    }

    //  Chooses least loaded I/O thread.
    protected IOThread chooseIoThread(long affinity)
    {
        return ctx.chooseIoThread(affinity);
    }

    protected void sendStop()
    {
        //  'stop' command goes always from administrative thread to
        //  the current object.
        Command cmd = new Command(this, Command.Type.STOP);
        ctx.sendCommand(tid, cmd);
    }

    protected void sendPlug(Own destination)
    {
        sendPlug(destination, true);
    }

    protected void sendPlug(Own destination, boolean incSeqnum)
    {
        if (incSeqnum) {
            destination.incSeqnum();
        }

        Command cmd = new Command(destination, Command.Type.PLUG);
        sendCommand(cmd);
    }

    protected void sendOwn(Own destination, Own object)
    {
        destination.incSeqnum();
        Command cmd = new Command(destination, Command.Type.OWN, object);
        sendCommand(cmd);
    }

    protected void sendAttach(SessionBase destination, IEngine engine)
    {
        sendAttach(destination, engine, true);
    }

    protected void sendAttach(SessionBase destination, IEngine engine, boolean incSeqnum)
    {
        if (incSeqnum) {
            destination.incSeqnum();
        }

        Command cmd = new Command(destination, Command.Type.ATTACH, engine);
        sendCommand(cmd);
    }

    protected void sendBind(Own destination, Pipe pipe)
    {
        sendBind(destination, pipe, true);
    }

    protected void sendBind(Own destination, Pipe pipe, boolean incSeqnum)
    {
        if (incSeqnum) {
            destination.incSeqnum();
        }

        Command cmd = new Command(destination, Command.Type.BIND, pipe);
        sendCommand(cmd);
    }

    protected void sendActivateRead(Pipe destination)
    {
        Command cmd = new Command(destination, Command.Type.ACTIVATE_READ);
        sendCommand(cmd);
    }

    protected void sendActivateWrite(Pipe destination, long msgsRead)
    {
        Command cmd = new Command(destination, Command.Type.ACTIVATE_WRITE, msgsRead);
        sendCommand(cmd);
    }

    protected void sendHiccup(Pipe destination, Object pipe)
    {
        Command cmd = new Command(destination, Command.Type.HICCUP, pipe);
        sendCommand(cmd);
    }

    protected void sendPipeTerm(Pipe destination)
    {
        Command cmd = new Command(destination, Command.Type.PIPE_TERM);
        sendCommand(cmd);
    }

    protected void sendPipeTermAck(Pipe destination)
    {
        Command cmd = new Command(destination, Command.Type.PIPE_TERM_ACK);
        sendCommand(cmd);
    }

    protected void sendTermReq(Own destination, Own object)
    {
        Command cmd = new Command(destination, Command.Type.TERM_REQ, object);
        sendCommand(cmd);
    }

    protected void sendTerm(Own destination, int linger)
    {
        Command cmd = new Command(destination, Command.Type.TERM, linger);
        sendCommand(cmd);
    }

    protected void sendTermAck(Own destination)
    {
        Command cmd = new Command(destination, Command.Type.TERM_ACK);
        sendCommand(cmd);
    }

    protected void sendReap(SocketBase socket)
    {
        Command cmd = new Command(ctx.getReaper(), Command.Type.REAP, socket);
        sendCommand(cmd);
    }

    protected void sendReaped()
    {
        Command cmd = new Command(ctx.getReaper(), Command.Type.REAPED);
        sendCommand(cmd);
    }

    protected void sendDone()
    {
        Command cmd = new Command(null, Command.Type.DONE);
        ctx.sendCommand(Ctx.TERM_TID, cmd);
    }

    protected void processStop()
    {
        throw new UnsupportedOperationException();
    }

    protected void processPlug()
    {
        throw new UnsupportedOperationException();
    }

    protected void processOwn(Own object)
    {
        throw new UnsupportedOperationException();
    }

    protected void processAttach(IEngine engine)
    {
        throw new UnsupportedOperationException();
    }

    protected void processBind(Pipe pipe)
    {
        throw new UnsupportedOperationException();
    }

    protected void processActivateRead()
    {
        throw new UnsupportedOperationException();
    }

    protected void processActivateWrite(long msgsRead)
    {
        throw new UnsupportedOperationException();
    }

    protected void processHiccup(Object hiccupPipe)
    {
        throw new UnsupportedOperationException();
    }

    protected void processPipeTerm()
    {
        throw new UnsupportedOperationException();
    }

    protected void processPipeTermAck()
    {
        throw new UnsupportedOperationException();
    }

    protected void processTermReq(Own object)
    {
        throw new UnsupportedOperationException();
    }

    protected void processTerm(int linger)
    {
        throw new UnsupportedOperationException();
    }

    protected void processTermAck()
    {
        throw new UnsupportedOperationException();
    }

    protected void processReap(SocketBase socket)
    {
        throw new UnsupportedOperationException();
    }

    protected void processReaped()
    {
        throw new UnsupportedOperationException();
    }

    //  Special handler called after a command that requires a seqnum
    //  was processed. The implementation should catch up with its counter
    //  of processed commands here.
    protected void processSeqnum()
    {
        throw new UnsupportedOperationException();
    }

    private void sendCommand(Command cmd)
    {
        ctx.sendCommand(cmd.destination().getTid(), cmd);
    }
}
