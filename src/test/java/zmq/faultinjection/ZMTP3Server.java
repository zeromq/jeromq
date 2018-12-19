package zmq.faultinjection;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ZMTP3Server {

    private static Charset ASCII = Charset.forName("ascii");
    private AcceptThread worker;
    private ISocketEvents delegate;


    public interface ISocketEvents {
        public void onConnectionReceived(SocketChannel channel);

        public void onGreetingReceived(SocketChannel channel);

        public void onHandshakeReceived(SocketChannel channel);

        public void onCommandReceived(SocketChannel channel, String command);

        public void onMessageReceived(SocketChannel channel, String message);
    }

    public void setDelegate(ISocketEvents delegate) {
        this.delegate = delegate;
    }

    public void initialize(SocketAddress localAddress) throws IOException {
        LOG.debug("initialize()");
        worker = new AcceptThread();
        worker.initialize(localAddress);

    }

    public void initialize() throws IOException {
        initialize(null);
    }

    public void stop() throws IOException {
        this.worker.stop();
    }

    class AcceptThread implements Runnable {
        private ServerSocketChannel channel;
        private Thread thread;
        SocketAddress localAddress;
        Selector selector = null;
        private ExecutorService executorService;

        public AcceptThread() {
        }

        @Override
        public void run() {
            try {
                this.selector = Selector.open();
                channel.register(selector, SelectionKey.OP_ACCEPT);

                while (!Thread.currentThread().isInterrupted()) {
                    // wait for a connection
                    selector.select();

                    // get the list of selection keys with pending events
                    java.util.Iterator<SelectionKey> it = selector.selectedKeys().iterator();

                    while (it.hasNext()) {
                        // Get the selection key SelectionKey
                        SelectionKey selKey = (SelectionKey) it.next();

                        // Remove it from the list to indicate that it is being processed
                        it.remove();

                        // Check if it's a connection request
                        if (selKey.isAcceptable()) {
                            // Get channel with connection request
                            ServerSocketChannel ssChannel = (ServerSocketChannel) selKey.channel();

                            CharsetDecoder decoder = ASCII.newDecoder();

                            // See Accepting a Connection on a ServerSocketChannel
                            // for an example of accepting a connection request
                            SocketChannel client = ssChannel.accept();

                            InetSocketAddress inets = (InetSocketAddress) client.socket().getRemoteSocketAddress();
                            LOG.info("Remote Endpoint: " + inets.getHostName() + ":" + inets.getPort());

                            if (delegate != null) {
                                delegate.onConnectionReceived(client);
                            }

                            RequestHandler handler = new RequestHandler(client, delegate);
                            executorService.submit(handler);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void initialize(SocketAddress localAddress) throws IOException {
            LOG.debug("initialize()");

            this.localAddress = localAddress;

            this.channel = ServerSocketChannel.open();
            this.channel.configureBlocking(false);

            this.channel.socket().bind(localAddress);
            this.channel.accept();

            final AcceptThread acceptThread = this;
            executorService = Executors.newCachedThreadPool(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "AcceptorThread");
                    return t;
                }
            });

            this.thread = new Thread(this, "ZMTP3ServerAcceptor");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        public void stop() throws IOException {
            LOG.debug("stop()");

            if (this.channel != null) {

                this.channel.close();
                this.channel.socket().close();

                this.channel = null;
            }

            if (this.thread != null) {

                try {
                    this.thread.interrupt();
                    this.thread.join(5000);
                    this.thread = null;
                } catch (InterruptedException e) {
                    LOG.warn(e.toString());
                }
            }
        }
    }


    class RequestHandler implements Runnable
    {
        private static final int TAB_CODE = 9;
        private boolean stopped;
        SocketChannel client = null;
        ISocketEvents delegate;

        private Object notifier = new Object();

        public RequestHandler(SocketChannel client, ISocketEvents delegate) {
            this.client = client;
            this.delegate = delegate;
        }

        public void stop()
        {
            LOG.debug("RequestHandler::stop()");

            try {
                // there might not be a current connection
                if (this.client != null)
                {
                    this.client.close();
                    this.client = null;
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            this.stopped = true;
        }


        public void run()
        {
            try
            {
                byte [] CLIENT_SIG = { (byte)0xff, (byte)0x0, (byte)0x0, (byte)0x0, (byte)0x0, (byte)0x0, (byte)0x0, (byte)0x0, (byte)0x0, (byte)0x7f  };
                byte [] VERSION = { (byte)0x03, (byte)0x00 };
                byte [] MECHANISM = { 'N', 'U', 'L', 'L', (byte)0x00 };

                ByteBuffer greeting = ByteBuffer.allocate(64);
                greeting.put(CLIENT_SIG);
                greeting.put(VERSION);
                greeting.put(MECHANISM);
                greeting.put((byte)0); // as server
                greeting.rewind();

                //PushbackInputStream readStream = new PushbackInputStream(client.socket().getInputStream());
                InputStream inputStream = client.socket().getInputStream();
                DataInputStream readStream = new DataInputStream(inputStream);

                OutputStream rawOutputStream = client.socket().getOutputStream();
                DataOutputStream outputStream = new DataOutputStream(rawOutputStream);

                LOG.info("Send greeting");

                outputStream.write(greeting.array());

                int offset = 0;
                byte [] requestBytes = new byte[1024];
                byte [] greetings = new byte[64];

                LOG.info("Reading greetings");
                int read = readStream.read(greetings, 0, greetings.length);
                LOG.info(String.format("Read: %d Total: %d", read, offset));

                LOG.info("\n" + print_hex(greetings, 0, greetings.length));

                assert (read == 64);

                if (delegate != null) {
                    delegate.onGreetingReceived(client);
                }

                // handshake.
                        /*
                        ;   The handshake consists of at least one command
                        ;   The actual grammar depends on the security mechanism
                        handshake = 1*command

                        ;   A command is a single long or short frame
                        command = command-size command-body
                        command-size = %x04 short-size | %x06 long-size
                        short-size = OCTET          ; Body is 0 to 255 octets
                        long-size = 8OCTET          ; Body is 0 to 2^63-1 octets
                        command-body = command-name command-data
                        command-name = OCTET 1*255command-name-char
                        command-name-char = ALPHA
                        command-data = *OCTET

                         */
                long command_size = 0;
                read = readStream.read(requestBytes, 0, 1);
                assert (read == 1);
                if (requestBytes[0] == 0x04) {
                    command_size = readStream.readByte();
                } else if (requestBytes[0] == 0x06) {
                    // long size. 8 bytes.

                    read = readStream.read(requestBytes, 0, 8);
                    assert (read == 8);
                    command_size = readStream.readLong();
                }

                byte [] command_bytes = new byte[(int)command_size];
                read = readStream.read(command_bytes, 0, command_bytes.length);
                assert (read == command_bytes.length);

                LOG.info("ComamndBytes: ");
                LOG.info("\n" + print_hex(command_bytes, 0, command_bytes.length));

                /*
                null = ready *message | error
                ready = command-size %d5 "READY" metadata
                metadata = *property
                property = name value
                name = OCTET 1*255name-char
                name-char = ALPHA | DIGIT | "-" | "_" | "." | "+"
                value = 4OCTET *OCTET       ; Size in network byte order
                error = command-size %d5 "ERROR" error-reason
                error-reason = OCTET 0*255VCHAR
                 */

                ByteArrayInputStream bis = new ByteArrayInputStream(command_bytes);
                DataInputStream dis = new DataInputStream(bis);
                int command_name_len = dis.readByte();
                byte [] command_name_bytes = new byte[command_name_len];
                dis.read(command_name_bytes);
                String commandName = new String(command_name_bytes,"utf-8");

                assert(Objects.equals(commandName, "READY"));

                if (delegate != null) {
                    delegate.onHandshakeReceived(client);
                }

                // we dont care about rest of the READY message from client as part of NULL handshake.

                // this is response to NULL handshake from client.
                // we will wrap this in a command later.
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                dos.writeByte(5);
                dos.write("READY".getBytes("utf-8"));

                // metdata for NULL handshake
                String [][] NULL_HANDSHAKE_METADATA = {
                        { "Socket-Type", "ROUTER" },
                        { "Identity", "+tcp://localhost:4300"}

                };

                for(int i = 0; i < NULL_HANDSHAKE_METADATA.length; i++) {
                    String key= NULL_HANDSHAKE_METADATA[i][0];
                    String value = NULL_HANDSHAKE_METADATA[i][1];

                    dos.writeByte(key.length());
                    dos.write(key.getBytes("utf-8"));
                    dos.writeInt(value.length());
                    dos.write(value.getBytes("utf-8"));
                }

                byte [] null_response = bos.toByteArray();
                if (null_response.length <= 255) {
                    outputStream.writeByte(0x04);
                    outputStream.writeByte(null_response.length);
                } else {
                    outputStream.writeByte(0x06);
                    outputStream.writeLong(null_response.length);
                }

                outputStream.write(null_response);


                int max = 10;


                while(max > 0)
                {
                    // now we are getting traffic
                            /*
                            traffic = *command
                             */
                    read_frame(readStream);

                    // delegate to the caller.
                    if (delegate != null)
                    {
                        delegate.onConnectionReceived(client);
                    }
                    //--max;
                }
            }
            catch(IOException e)
            {
                e.printStackTrace();
            }
            catch(ClosedSelectorException e)
            {
                e.printStackTrace();
            } finally {
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            LOG.debug("**** ZMTP3 TEST SERVER EXITING *****");
        }

        String print_hex(byte [] buffer, int offset, int length) {
            StringBuilder sb = new StringBuilder(128);
            StringBuilder hex = new StringBuilder(128);
            StringBuilder ascii = new StringBuilder(128);
            for (int i = 0; i < buffer.length; i++) {
                hex.append(String.format("%02x", buffer[i]));
                hex.append(" ");

                if (buffer[i] >= 32 && buffer[i] < 128) {
                    ascii.append(String.format("%c", buffer[i]));
                } else {
                    ascii.append("-");
                }
                ascii.append(" ");

                if ((i +1) % 8 == 0) {
                    sb.append(hex.toString()).append(" ").append(ascii.toString()).append("\n");
                    hex.setLength(0);
                    ascii.setLength(0);
                }
            }

            return sb.toString();
        }

        void read_command(DataInputStream readStream, byte command_size_type) throws IOException {
            /*
            ;   A command is a single long or short frame
            command = command-size command-body
            command-size = %x04 short-size | %x06 long-size
            short-size = OCTET          ; Body is 0 to 255 octets
            long-size = 8OCTET          ; Body is 0 to 2^63-1 octets
            command-body = command-name command-data
            command-name = OCTET 1*255command-name-char
            command-name-char = ALPHA
            command-data = *OCTET
            */

            long command_size = 0;

            if (command_size_type == 0x04) {
                command_size = readStream.readByte();
            } else if (command_size_type == 0x06) {
                // long size. 8 bytes.
                command_size = readStream.readLong();
            }

            byte [] command_body_bytes = new byte[(int)command_size];
            int read = readStream.read(command_body_bytes, 0, command_body_bytes.length);
            assert (read == command_body_bytes.length);

            LOG.info("COMMAND-BODY: \n" + print_hex(command_body_bytes, 0, command_body_bytes.length));

            // get command name
            String commandName = readString(readStream);

            if (delegate != null) {
                delegate.onCommandReceived(client, commandName);
            }


        }

        boolean read_frame(DataInputStream inputStream) throws IOException {
            /*
            Following the greeting, which has a fixed size of 64 octets, all further data is sent as frames, which carry commands or messages. Frames consist of a size field, a flags fields, and a body. The frame design is meant to be efficient for small frames but capable of handling extremely large data as well.

            A frame consists of a flags field (1 octet), followed by a size field (one octet or eight octets) and a frame body of size octets. The size does not include the flags field, nor itself, so an empty frame has a size of zero.

            A short frame has a body of 0 to 255 octets. A long frame has a body of 0 to 2^63-1 octets.

            The flags field consists of a single octet containing various control flags. Bit 0 is the least significant bit (rightmost bit):

            Bits 7-3: Reserved. Bits 7-3 are reserved for future use and MUST be zero.

            Bit 2 (COMMAND): Command frame. A value of 1 indicates that the frame is a command frame. A value of 0 indicates that the frame is a message frame.

            Bit 1 (LONG): Long frame. A value of 0 indicates that the frame size is encoded as a single octet. A value of 1 indicates that the frame size is encoded as a 64-bit unsigned integer in network byte order.

            Bit 0 (MORE): More frames to follow. A value of 0 indicates that there are no more frames to follow. A value of 1 indicates that more frames will follow. This bit SHALL be zero on command frames.
            */

            byte flag = inputStream.readByte();

            boolean more = (flag & 0x01) == 0x01;

            boolean long_frame = (flag & 0x02) == 0x02;

            boolean command_frame = (flag & 0x04) == 0x04;

            if (command_frame) {
                read_command(inputStream, flag);

            } else {
                read_message(inputStream, long_frame);
            }

            return more;
        }

        void read_message(DataInputStream readStream, boolean long_frame) throws IOException {
            /*
                ;   A message is one or more frames
                message = *message-more message-last
                message-more = ( %x01 short-size | %x03 long-size ) message-body
                message-last = ( %x00 short-size | %x02 long-size ) message-body
                message-body = *OCTET
            */

            long message_size = 0;

            if (long_frame) {
                // long size. 8 bytes.
                message_size = readStream.readLong();
            } else {
                message_size = readStream.readByte();
            }

            byte [] message_body_bytes = new byte[(int)message_size];
            int read = readStream.read(message_body_bytes, 0, message_body_bytes.length);
            assert (read == message_body_bytes.length);

            LOG.info(String.format("Read %d message bytes", read));
            //message body is string from logging system.
            LOG.info("COMMAND-BODY: \n" + print_hex(message_body_bytes, 0, message_body_bytes.length));

            if (delegate != null) {
                delegate.onMessageReceived(client, null);
            }

        }

        String readString(DataInputStream inputStream) throws IOException {
            // read a size prepended string value
            byte size = inputStream.readByte();
            byte [] buffer = new byte[size];
            int read = inputStream.read(buffer);
            assert (read == size);
            String s = new String(buffer, "utf-8");
            LOG.info("Got command: " + s);
            return s;
        }
    }

    static class LOG {
        public static void debug(String format, String... args) {
            print("DEBUG", format, args);
        }

        public static void info(String format, String... args) {
            print("INFO", format, args);
        }

        public static void warn(String format, String... args) {
            print("WARN", format, args);
        }

        static void print(String level, String format, String... args) {
            if (args.length == 0) {
                System.out.printf("%s %s\n", level, format);
            } else {
                System.out.printf(level + " " + format +"\n", args);
            }

        }
    }
}
