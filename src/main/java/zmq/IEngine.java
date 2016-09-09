package zmq;

//  Abstract interface to be implemented by various engines.
public interface IEngine
{
    //  Plug the engine to the session.
    void plug(IOThread ioThread, SessionBase session);

    //  Terminate and deallocate the engine. Note that 'detached'
    //  events are not fired on termination.
    void terminate();

    //  This method is called by the session to signalise that more
    //  messages can be written to the pipe.
    void activateIn();

    //  This method is called by the session to signalise that there
    //  are messages to send available.
    void activateOut();
}
