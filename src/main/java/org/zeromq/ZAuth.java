package org.zeromq;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQ.Socket.Mechanism;
import org.zeromq.util.ZMetadata;

/**
 * A ZAuth actor takes over authentication for all incoming connections in
 *  its context. You can whitelist or blacklist peers based on IP address,
 *  and define policies for securing PLAIN, CURVE, and GSSAPI connections.
 *  <br>
 * Note that libzmq provides four levels of security: default NULL (which ZAuth
 * does not see), and authenticated NULL, PLAIN, and CURVE, which ZAuth can see.
 *  <br>
 * Based on <a href="http://github.com/zeromq/czmq/blob/master/src/zauth.c">zauth.c</a> in czmq
 */
public class ZAuth implements Closeable
{
    public interface Auth
    {
        /**
         * Configures with ad-hoc message.
         * @param msg the configuration message.
         * @param verbose true if the actor is verbose.
         * @return true if correctly configured, otherwise false.
         */
        boolean configure(ZMsg msg, boolean verbose);

        /**
         * Callback for authorizing a connection.
         * @return true if the connection is authorized, false otherwise.
         */
        boolean authorize(ZapRequest request, boolean verbose);
    }

    public static class SimplePlainAuth implements Auth
    {
        private final Properties passwords = new Properties(); // PLAIN passwords, if loaded
        private File             passwordsFile;
        private long             passwordsModified;

        @Override
        public boolean configure(ZMsg msg, boolean verbose)
        {
            assert (msg.size() == 2);

            // For now we don't do anything with domains
            @SuppressWarnings("unused")
            String domain = msg.popString();
            // Get password file and load into HashMap
            // If the file doesn't exist we'll get an empty map
            String filename = msg.popString();
            passwordsFile = new File(filename);

            if (verbose) {
                System.out.printf(
                                  "ZAuth: activated plain-mechanism with password-file: %s%n",
                                  passwordsFile.getAbsolutePath());
            }

            try {
                loadPasswords(true);
            }
            catch (IOException e) {
                // Ignore the exception, just don't read the file
            }
            return true;
        }

        @Override
        public boolean authorize(ZapRequest request, boolean verbose)
        {
            // assert (request.username != null);
            // Refresh the passwords map if the file changed
            try {
                loadPasswords(false);
            }
            catch (IOException e) {
                // Ignore the exception, just don't read the file
            }

            String password = passwords.getProperty(request.username);
            if (password != null && password.equals(request.password)) {
                if (verbose) {
                    System.out.printf("ZAuth: Allowed (PLAIN) username=%s\n", request.username);
                }
                request.userId = request.username;
                return true;
            }
            else {
                if (verbose) {
                    System.out.printf("ZAuth: Denied (PLAIN) username=%s\n", request.username);
                }

                return false;
            }
        }

        private void loadPasswords(boolean initial) throws IOException
        {
            if (!initial) {
                final long lastModified = passwordsFile.lastModified();
                final long age = System.currentTimeMillis() - lastModified;
                if (lastModified > passwordsModified && age > 1000) {
                    // File has been modified and is stable, clear map
                    passwords.clear();
                }
                else {
                    return;
                }
            }

            passwordsModified = passwordsFile.lastModified();
            Reader br = new BufferedReader(new FileReader(passwordsFile));
            try {
                passwords.load(br);
            }
            catch (IOException | IllegalArgumentException ex) {
                // Ignore the exception, just don't read the file
            }
            finally {
                br.close();
            }
        }
    }

    public static class SimpleCurveAuth implements Auth
    {
        private final ZCertStore.Fingerprinter fingerprinter;

        private ZCertStore certStore = null;
        private boolean    allowAny;

        public SimpleCurveAuth()
        {
            this(new ZCertStore.Hasher());
        }

        public SimpleCurveAuth(ZCertStore.Fingerprinter fingerprinter)
        {
            this.fingerprinter = fingerprinter;
        }

        @Override
        public boolean configure(ZMsg configuration, boolean verbose)
        {
            //  If location is CURVE_ALLOW_ANY, allow all clients. Otherwise
            //  treat location as a directory that holds the certificates.
            final String location = configuration.popString();
            allowAny = location.equals(CURVE_ALLOW_ANY);
            if (allowAny) {
                if (verbose) {
                    System.out.println("ZAuth: Allowing all clients");
                }
            }
            else {
                if (verbose) {
                    System.out.printf("ZAuth: Using %s as certificates directory%n", location);
                }
                certStore = new ZCertStore(location, fingerprinter);
            }
            return true;
        }

        @Override
        public boolean authorize(ZapRequest request, boolean verbose)
        {
            if (allowAny) {
                if (verbose) {
                    System.out.println("ZAuth: allowed (CURVE allow any client)");
                }
                return true;
            }
            else {
                if (certStore != null) {
                    if (certStore.containsPublicKey(request.clientKey)) {
                        // login allowed
                        if (verbose) {
                            System.out.printf("ZAuth: Allowed (CURVE) client_key=%s\n", request.clientKey);
                        }
                        request.userId = request.clientKey;
                        request.metadata = certStore.getMetadata(request.clientKey);
                        return true;
                    }
                    else {
                        // login not allowed. couldn't find certificate
                        if (verbose) {
                            System.out.printf("ZAuth: Denied (CURVE) client_key=%s\n", request.clientKey);
                        }
                        return false;
                    }
                }
            }
            return false;
        }
    }

    public static class SimpleNullAuth implements Auth
    {
        @Override
        public boolean configure(ZMsg configuration, boolean verbose)
        {
            return true;
        }

        @Override
        public boolean authorize(ZapRequest request, boolean verbose)
        {
            return true;
        }
    }

    private static final String ZAP_VERSION = "1.0";

    public static class ZapReply
    {
        public final String    version;    //  Version number, must be "1.0"
        public final String    sequence;   //  Sequence number of request
        public final int       statusCode; //  numeric status code
        public final String    statusText; //  readable status
        public final String    userId;     //  User-Id
        public final ZMetadata metadata;   //  optional metadata
        public final String    address;    // not part of the ZAP protocol, but handy information for user
        public final String    identity;   // not part of the ZAP protocol, but handy information for user

        private ZapReply(String version, String sequence, int statusCode, String statusText, String userId,
                ZMetadata metadata)
        {
            this(version, sequence, statusCode, statusText, userId, metadata, null, null);
        }

        private ZapReply(String version, String sequence, int statusCode, String statusText, String userId,
                ZMetadata metadata, String address, String identity)
        {
            assert (ZAP_VERSION.equals(version));
            this.version = version;
            this.sequence = sequence;
            this.statusCode = statusCode;
            this.statusText = statusText;
            this.userId = userId;
            this.metadata = metadata;
            this.address = address;
            this.identity = identity;
        }

        private ZMsg msg()
        {
            ZMsg msg = new ZMsg();
            msg.add(version);
            msg.add(sequence);
            msg.add(Integer.toString(statusCode));
            msg.add(statusText);
            msg.add(userId == null ? "" : userId);
            msg.add(metadata == null ? new byte[0] : metadata.bytes());
            return msg;
        }

        @Override
        public String toString()
        {
            return "ZapReply [" + (version != null ? "version=" + version + ", " : "")
                    + (sequence != null ? "sequence=" + sequence + ", " : "") + "statusCode=" + statusCode + ", "
                    + (statusText != null ? "statusText=" + statusText + ", " : "")
                    + (userId != null ? "userId=" + userId + ", " : "")
                    + (metadata != null ? "metadata=" + metadata : "") + "]";
        }

        private static ZapReply recv(ZAgent agent, boolean wait)
        {
            return received(agent.recv(wait));
        }

        private static ZapReply recv(ZAgent agent, int timeout)
        {
            return received(agent.recv(timeout));
        }

        private static ZapReply received(ZMsg msg)
        {
            if (msg == null) {
                return null;
            }
            assert (msg.size() == 8);
            String version = msg.popString();
            String sequence = msg.popString();
            int statusCode = Integer.parseInt(msg.popString());
            String statusText = msg.popString();
            String userId = msg.popString();
            ZMetadata metadata = ZMetadata.read(msg.popString());
            String address = msg.popString();
            String identity = msg.popString();

            return new ZapReply(version, sequence, statusCode, statusText, userId, metadata, address, identity);
        }
    }

    /**
     * A small class for working with ZAP requests and replies.
     */
    public static class ZapRequest
    {
        private final Socket handler; //  socket we're talking to

        public final String version;   //  Version number, must be "1.0"
        public final String sequence;  //  Sequence number of request
        public final String domain;    //  Server socket domain
        public final String address;   //  Client IP address
        public final String identity;  //  Server socket identity
        public final String mechanism; //  Security mechanism
        public final String username;  //  PLAIN user name
        public final String password;  //  PLAIN password, in clear text
        public final String clientKey; //  CURVE client public key in ASCII
        public final String principal; //  GSSAPI principal
        public String       userId;    //  User-Id to return in the ZAP Response
        public ZMetadata    metadata;  // metadata to eventually return

        private ZapRequest(Socket handler, ZMsg request)
        {
            //  Store handler socket so we can send a reply easily
            this.handler = handler;
            //  Get all standard frames off the handler socket
            version = request.popString();
            sequence = request.popString();
            domain = request.popString();
            address = request.popString();
            identity = request.popString();
            mechanism = request.popString();

            //  If the version is wrong, we're linked with a bogus libzmq, so die
            assert (ZAP_VERSION.equals(version));

            // Get mechanism-specific frames
            if (Mechanism.PLAIN.name().equals(mechanism)) {
                username = request.popString();
                password = request.popString();
                clientKey = null;
                principal = null;
            }
            else if (Mechanism.CURVE.name().equals(mechanism)) {
                ZFrame frame = request.pop();
                byte[] clientPublicKey = frame.getData();
                username = null;
                password = null;
                clientKey = ZMQ.Curve.z85Encode(clientPublicKey);
                principal = null;
            }
            else if (zmq.io.mechanism.Mechanisms.GSSAPI.name().equals(mechanism)) {
                // TOD handle GSSAPI as well
                username = null;
                password = null;
                clientKey = null;
                principal = request.popString();
            }
            else {
                username = null;
                password = null;
                clientKey = null;
                principal = null;
            }
        }

        private static ZapRequest recvRequest(Socket handler, boolean wait)
        {
            ZMsg request = ZMsg.recvMsg(handler, wait);
            if (request == null) {
                return null;
            }

            ZapRequest self = new ZapRequest(handler, request);

            //  If the version is wrong, we're linked with a bogus libzmq, so die
            assert (ZAP_VERSION.equals(self.version));

            request.destroy();
            return self;
        }

        /**
         * Send a zap reply to the handler socket
         */
        private void reply(int statusCode, String statusText, Socket replies)
        {
            ZapReply reply = new ZapReply(ZAP_VERSION, sequence, statusCode, statusText, userId, metadata);
            ZMsg msg = reply.msg();
            boolean destroy = replies == null;
            msg.send(handler, destroy);
            if (replies != null) {
                // let's add other fields for convenience of listener
                msg.add(address);
                msg.add(identity);
                msg.send(replies);
            }
        }
    }

    public static final String CURVE_ALLOW_ANY = "*";

    private static final String VERBOSE   = "VERBOSE";
    private static final String REPLIES   = "REPLIES";
    private static final String ALLOW     = "ALLOW";
    private static final String DENY      = "DENY";
    private static final String TERMINATE = "TERMINATE";

    private final ZAgent     agent;
    private final ZStar.Exit exit;
    private final ZAgent     replies;
    private boolean          repliesEnabled; // are replies enabled?

    /**
     * Install authentication for the specified context. Note that until you add
     * policies, all incoming NULL connections are allowed (classic ZeroMQ
     * behavior), and all PLAIN and CURVE connections are denied.
     */
    public ZAuth(ZContext ctx)
    {
        this(ctx, "ZAuth");
    }

    public ZAuth(ZContext ctx, ZCertStore.Fingerprinter fingerprinter)
    {
        this(ctx, "ZAuth", curveVariant(fingerprinter));
    }

    public ZAuth(ZContext ctx, String actorName)
    {
        this(ctx, actorName, makeSimpleAuths());
    }

    private static Map<String, Auth> makeSimpleAuths()
    {
        Map<String, Auth> auths = new HashMap<>();

        auths.put(Mechanism.PLAIN.name(), new SimplePlainAuth());
        auths.put(Mechanism.CURVE.name(), new SimpleCurveAuth());
        auths.put(Mechanism.NULL.name(), new SimpleNullAuth());
        // TODO add GSSAPI once it is implemented
        return auths;
    }

    private static Map<String, Auth> curveVariant(ZCertStore.Fingerprinter fingerprinter)
    {
        Map<String, Auth> auths = makeSimpleAuths();
        auths.put(Mechanism.CURVE.name(), new SimpleCurveAuth(fingerprinter));
        return auths;
    }

    public ZAuth(final ZContext ctx, String actorName, Map<String, Auth> auths)
    {
        assert (ctx != null); // works only for connections within the same context
        assert (actorName != null);
        assert (auths != null);
        final AuthActor actor = new AuthActor(actorName, auths);
        final ZActor zactor = new ZActor(ctx, null, actor, UUID.randomUUID().toString());
        agent = zactor.agent();
        exit = zactor.exit();

        // wait for the start of the actor
        agent.recv().destroy();
        replies = actor.createAgent(ctx);
    }

    /**
     * Enable verbose tracing of commands and activity
     */
    public ZAuth setVerbose(boolean verbose)
    {
        return verbose(verbose);
    }

    public ZAuth verbose(boolean verbose)
    {
        return send(VERBOSE, String.format("%b", verbose));
    }

    /**
     * Allow (whitelist) a single IP address. For NULL, all clients from this
     * address will be accepted. For PLAIN and CURVE, they will be allowed to
     * continue with authentication. You can call this method multiple times to
     * whitelist multiple IP addresses. If you whitelist a single address, any
     * non-whitelisted addresses are treated as blacklisted.
     */
    public ZAuth allow(String address)
    {
        assert (address != null);
        return send(ALLOW, address);
    }

    /**
     * Deny (blacklist) a single IP address. For all security mechanisms, this
     * rejects the connection without any further authentication. Use either a
     * whitelist, or a blacklist, not not both. If you define both a whitelist
     * and a blacklist, only the whitelist takes effect.
     */
    public ZAuth deny(String address)
    {
        assert (address != null);
        return send(DENY, address);
    }

    /**
     * Configure PLAIN authentication for a given domain. PLAIN authentication
     * uses a plain-text password file. To cover all domains, use "*". You can
     * modify the password file at any time; it is reloaded automatically.
     */
    public ZAuth configurePlain(String domain, String filename)
    {
        assert (domain != null);
        assert (filename != null);
        return send(Mechanism.PLAIN.name(), domain, filename);
    }

    /**
     * Configure CURVE authentication
     *
     * @param location Can be ZAuth.CURVE_ALLOW_ANY or a directory with public-keys that will be accepted
     */
    public ZAuth configureCurve(String location)
    {
        assert (location != null);
        return send(Mechanism.CURVE.name(), location);
    }

    public ZAuth replies(boolean enable)
    {
        repliesEnabled = enable;
        return send(REPLIES, String.format("%b", enable));
    }

    /**
     * Retrieves the next ZAP reply.
     * @return the next reply or null if the actor is closed.
     */
    public ZapReply nextReply()
    {
        return nextReply(true);
    }

    /**
     * Retrieves the next ZAP reply.
     * @param wait true to wait for the next reply, false to immediately return if there is no next reply.
     * @return the next reply or null if the actor is closed or if there is no next reply yet.
     */
    public ZapReply nextReply(boolean wait)
    {
        if (!repliesEnabled) {
            System.out.println("ZAuth: replies are disabled. Please use replies(true);");
            return null;
        }
        return ZapReply.recv(replies, wait);
    }

    /**
     * Retrieves the next ZAP reply.
     * @param timeout the timeout in milliseconds to wait for a reply before giving up and returning null.
     * @return the next reply or null if the actor is closed or if there is no next reply after the elapsed timeout.
     */
    public ZapReply nextReply(int timeout)
    {
        if (!repliesEnabled) {
            System.out.println("ZAuth: replies are disabled. Please use replies(true);");
            return null;
        }
        return ZapReply.recv(replies, timeout);
    }

    /**
     * Destructor.
     */
    @Override
    public void close() throws IOException
    {
        destroy();
    }

    /**
     * Destructor.
     */
    public void destroy()
    {
        send(TERMINATE);
        exit.awaitSilent();
        agent.close();
        replies.close();
    }

    protected ZAuth send(String command, String... datas)
    {
        ZMsg msg = new ZMsg();
        msg.add(command);
        for (String data : datas) {
            msg.add(data);
        }
        agent.send(msg);
        msg.destroy();
        agent.recv();
        return this;
    }

    /**
     * AuthActor is the backend actor which we talk to over a pipe. This lets
     * the actor do work asynchronously in the background while our application
     * does other things. This is invisible to the caller, who sees a classic
     * API.
     */
    private static class AuthActor extends ZActor.SimpleActor
    {
        private static final String OK = "OK";

        private final String actorName;

        private final Properties        whitelist = new Properties(); // whitelisted addresses
        private final Properties        blacklist = new Properties(); // blacklisted addresses
        private final Map<String, Auth> auths     = new HashMap<>();

        private final String repliesAddress; // address of replies pipe AND safeguard lock for connected agent
        private boolean      repliesEnabled; // are replies enabled?
        private Socket       replies;        // replies pipe
        private boolean      verbose;        // trace behavior

        private AuthActor(String actorName, Map<String, Auth> auths)
        {
            assert (auths != null);
            assert (actorName != null);
            this.actorName = actorName;
            this.auths.putAll(auths);
            this.repliesAddress = "inproc://zauth-replies-" + UUID.randomUUID().toString();
        }

        private ZAgent createAgent(ZContext ctx)
        {
            Socket pipe = ctx.createSocket(ZMQ.PAIR);
            boolean rc = pipe.connect(repliesAddress);
            assert (rc);
            return new ZAgent.SimpleAgent(pipe, repliesAddress);
        }

        @Override
        public String premiere(Socket pipe)
        {
            return actorName;
        }

        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            //create replies pipe that will forward replies to user
            replies = ctx.createSocket(ZMQ.PAIR);
            assert (replies != null);

            //create ZAP handler and get ready for requests
            Socket handler = ctx.createSocket(ZMQ.REP);
            assert (handler != null);
            return Arrays.asList(handler, replies);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            boolean rc;
            try {
                rc = replies.bind(repliesAddress);
                assert (rc);
                Socket handler = sockets.get(0);
                rc = handler.bind("inproc://zeromq.zap.01");
                assert (rc);
                rc = poller.register(handler, ZPoller.POLLIN);
                assert (rc);
                rc = pipe.send(OK);
                assert (rc);
            }
            catch (ZMQException e) {
                System.out.println("ZAuth: Error");
                e.printStackTrace();
                rc = pipe.send("ERROR");
                assert (rc);
            }
        }

        @Override
        public boolean backstage(Socket pipe, ZPoller poller, int events)
        {
            ZMsg msg = ZMsg.recvMsg(pipe);

            String command = msg.popString();
            if (command == null) {
                System.out.printf("ZAuth: Closing auth: No command%n");
                return false; //interrupted
            }
            boolean rc;
            if (ALLOW.equals(command)) {
                String address = msg.popString();
                if (verbose) {
                    System.out.printf("ZAuth: Whitelisting IP address=%s\n", address);
                }
                whitelist.put(address, OK);
                rc = pipe.send(OK);
            }
            else if (DENY.equals(command)) {
                String address = msg.popString();
                if (verbose) {
                    System.out.printf("ZAuth: Blacklisting IP address=%s\n", address);
                }
                blacklist.put(address, OK);
                rc = pipe.send(OK);
            }
            else if (VERBOSE.equals(command)) {
                String verboseStr = msg.popString();
                this.verbose = Boolean.parseBoolean(verboseStr);
                rc = pipe.send(OK);
            }
            else if (REPLIES.equals(command)) {
                repliesEnabled = Boolean.parseBoolean(msg.popString());
                if (verbose) {
                    if (repliesEnabled) {
                        System.out.println("ZAuth: Enabled replies");
                    }
                    else {
                        System.out.println("ZAuth: Disabled replies");
                    }
                }
                rc = pipe.send(OK);
            }
            else if (TERMINATE.equals(command)) {
                if (repliesEnabled) {
                    replies.send(repliesAddress); // lock replies agent
                }
                if (verbose) {
                    System.out.println("ZAuth: Terminated");
                }
                pipe.send(OK);
                return false;
            }
            else {
                final Auth authenticator = auths.get(command);
                if (authenticator != null) {
                    if (authenticator.configure(msg, verbose)) {
                        rc = pipe.send(OK);
                    }
                    else {
                        rc = pipe.send("ERROR");
                    }
                }
                else {
                    System.out.printf("ZAuth: Invalid command %s%n", command);
                    rc = true;
                }
            }

            msg.destroy();
            if (!rc) {
                System.out.printf("ZAuth: Command in error %s%n", command);
            }
            return rc;
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            ZapRequest request = ZapRequest.recvRequest(socket, true);
            if (request == null) {
                return false;
            }

            //is the address explicitly whitelisted or blacklisted?
            boolean allowed = false;
            boolean denied = false;

            if (!whitelist.isEmpty()) {
                if (whitelist.containsKey(request.address)) {
                    allowed = true;
                    if (verbose) {
                        System.out.printf("ZAuth: Passed (whitelist) address = %s\n", request.address);
                    }
                }
                else {
                    denied = true;
                    if (verbose) {
                        System.out.printf("ZAuth: Denied (not in whitelist) address = %s\n", request.address);
                    }
                }
            }
            else if (!blacklist.isEmpty()) {
                if (blacklist.containsKey(request.address)) {
                    denied = true;
                    if (verbose) {
                        System.out.printf("ZAuth: Denied (blacklist) address = %s\n", request.address);
                    }
                }
                else {
                    allowed = true;
                    if (verbose) {
                        System.out.printf("ZAuth: Passed (not in blacklist) address = %s\n", request.address);
                    }
                }
            }

            //mechanism specific check
            if (!denied) {
                final Auth auth = auths.get(request.mechanism);
                if (auth == null) {
                    System.out.printf("ZAuth E: Skipping unhandled mechanism %s%n", request.mechanism);
                    return false;
                }
                else {
                    allowed = auth.authorize(request, verbose);
                }
            }

            final Socket reply = repliesEnabled ? replies : null;
            if (allowed) {
                request.reply(200, OK, reply);
            }
            else {
                request.metadata = null;
                request.reply(400, "NO ACCESS", reply);
            }
            return true;
        }
    }
}
