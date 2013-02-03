package guide.newapi;

import org.jeromq.api.ErrorCode;
import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;
import org.zeromq.ZMQException;

/**
 * Interrupt in Java
 * Shows how to handle Ctrl-C
 */
public class interrupt {

    public static void main(String[] args) {
        //  Prepare our context and socket
        final ZeroMQContext context = new ZeroMQContext();

        final Thread zmqThread = new Thread() {
            @Override
            public void run() {
                Socket socket = context.createSocket(SocketType.REP);
                socket.bind("tcp://*:5555");

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        socket.receive();
                    } catch (ZMQException e) {
                        ErrorCode errorCode = ErrorCode.findByCode(e.getErrorCode());
                        if (ErrorCode.TERMINATE == errorCode) {
                            break;
                        }
                    }
                }
            }
        };

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("W: interrupt received, killing server...");
                context.terminate();
                try {
                    zmqThread.interrupt();
                    zmqThread.join();
                } catch (InterruptedException e) {
                    //ignore this?
                }
            }
        });

        zmqThread.start();
    }
}
