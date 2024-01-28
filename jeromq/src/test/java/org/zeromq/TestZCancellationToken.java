package org.zeromq;

import org.junit.Assert;
import org.junit.Test;
import zmq.ZError;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestZCancellationToken
{
    @Test
    public void cancelReceiveThreadSafe()
    {
        try (ZContext context = new ZContext();
             ZMQ.Socket socket = context.createSocket(SocketType.CLIENT)) {
            ZMQ.CancellationToken cancellationToken = socket.createCancellationToken();

            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cancellationToken.cancel();
            });
            t.start();

            try {
                socket.recv(0, cancellationToken);
                Assert.fail();
            }
            catch (ZMQException ex) {
                assertThat(ex.getErrorCode(), is(ZError.ECANCELED));
            }
        }
    }

    @Test
    public void cancelSendThreadSafe()
    {
        try (ZContext context = new ZContext();
             ZMQ.Socket socket = context.createSocket(SocketType.CLIENT)) {
            ZMQ.CancellationToken cancellationToken = socket.createCancellationToken();

            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cancellationToken.cancel();
            });
            t.start();

            try {
                socket.send(new byte[1], 0, cancellationToken);
                Assert.fail();
            }
            catch (ZMQException ex) {
                assertThat(ex.getErrorCode(), is(ZError.ECANCELED));
            }
        }
    }

    @Test
    public void cancelReceive()
    {
        try (ZContext context = new ZContext();
             ZMQ.Socket socket = context.createSocket(SocketType.DEALER)) {
            ZMQ.CancellationToken cancellationToken = socket.createCancellationToken();

            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cancellationToken.cancel();
            });
            t.start();

            try {
                socket.recv(0, cancellationToken);
                Assert.fail();
            }
            catch (ZMQException ex) {
                assertThat(ex.getErrorCode(), is(ZError.ECANCELED));
            }
        }
    }

    @Test
    public void cancelSend()
    {
        try (ZContext context = new ZContext();
             ZMQ.Socket socket = context.createSocket(SocketType.DEALER)) {
            ZMQ.CancellationToken cancellationToken = socket.createCancellationToken();

            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cancellationToken.cancel();
            });
            t.start();

            try {
                socket.send(new byte[1], 0, cancellationToken);
                Assert.fail();
            }
            catch (ZMQException ex) {
                assertThat(ex.getErrorCode(), is(ZError.ECANCELED));
            }
        }
    }
}
