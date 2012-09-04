package guide;

import org.jeromq.ZMQ;

public class identity {

  public static void main(String[] args) throws InterruptedException {
    ZMQ.Context context = ZMQ.context(1);
    ZMQ.Socket sink = context.socket(ZMQ.ROUTER);
    sink.bind("inproc://example");
    
    //  First allow 0MQ to set the identity, [00] + random 4byte
    ZMQ.Socket anonymous = context.socket(ZMQ.REQ);

    anonymous.connect("inproc://example");
    anonymous.send ("ROUTER uses a generated UUID",0);
    sink.dump ();

    //  Then set the identity ourself
    ZMQ.Socket identified = context.socket(ZMQ.REQ);
    identified.setIdentity("Hello");
    identified.connect ("inproc://example");
    identified.send("ROUTER socket uses REQ's socket identity", 0);
    sink.dump ();

    sink.close ();
    anonymous.close ();
    identified.close();
    context.term();

  }
}
