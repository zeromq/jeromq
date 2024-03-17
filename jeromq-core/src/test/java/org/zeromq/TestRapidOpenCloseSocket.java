package org.zeromq;

import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;

// For issue: https://github.com/zeromq/jeromq/issues/198
public class TestRapidOpenCloseSocket
{
  @Test
  @Ignore
  public void testRapidOpenCloseSocket() throws Exception
  {
    for (int i = 0; i < 50; i++) {
      performTest();
    }
  }

  private void performTest() throws Exception
  {
    ZContext ctx = new ZContext();

    Socket recvMsgSock = ctx.createSocket(SocketType.PULL);
    recvMsgSock.bind("tcp://*:" + Utils.findOpenPort());
    Socket processMsgSock = ctx.createSocket(SocketType.PUSH);
    processMsgSock.bind("inproc://process-msg");

    List<Socket> workerSocks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      Socket workerSock = ctx.createSocket(SocketType.PULL);
      workerSock.connect("inproc://process-msg");
      workerSocks.add(workerSock);
    }

    ZMQQueue queue = new ZMQQueue(ctx.getContext(), recvMsgSock, processMsgSock);
    Thread proxyThr = new Thread(queue);
    proxyThr.setName("Proxy thr");
    proxyThr.start();

    for (final Socket workerSock : workerSocks) {
      Thread workerThr = new Thread(() -> {
        try {
          while (true) {
            byte[] msg = workerSock.recv();
            // Process the msg!
          }
        }
        catch (Exception e) {
        }
      });
      workerThr.setName("A worker thread");
      workerThr.start();
    }

    try {
      Thread.sleep(100);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }

    System.out.println("Closing now");

    ctx.destroy();
    System.out.println("Successfully closed");
  }
}
