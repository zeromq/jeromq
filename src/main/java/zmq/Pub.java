package zmq;

public class Pub extends XPub {

	public static class PubSession extends XPub.XPubSession {

        public PubSession(IOThread io_thread_, boolean connect_,
                SocketBase socket_, Options options_, Address addr_) {
            super(io_thread_, connect_, socket_, options_, addr_);
        }

    }

    Pub(Ctx parent_, int tid_, int sid_) {
		super(parent_, tid_, sid_);
		options.type = ZMQ.ZMQ_PUB;
	}

}
