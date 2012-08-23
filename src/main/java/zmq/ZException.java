package zmq;


public class ZException  {


    public static class AddrInUse extends RuntimeException {

        private static final long serialVersionUID = 8574207129098333986L;

    }
    public static class NoFreeSlot extends RuntimeException {
        private static final long serialVersionUID = -8599234879594535002L;
    }

    public static class ConnectionRefused extends RuntimeException {
        private static final long serialVersionUID = 2962118056167320315L;
    }
    
    public static class CtxTerminated extends RuntimeException {

        private static final long serialVersionUID = 7074545575003946832L;
        
    }

    public static class InstantiationException extends RuntimeException {
        private static final long serialVersionUID = -4404921838608052955L;
        
        public InstantiationException(Throwable cause) {
            super(cause);
        }
    }

    public static class IOException extends RuntimeException {
        private static final long serialVersionUID = 9202470691157986262L;

        public IOException(java.io.IOException e) {
            super(e);
        }
    }

}
