package org.zeromq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.zeromq.ZMQ.Socket;
import org.zeromq.util.ZMetadata;

import zmq.io.mechanism.Mechanisms;
import zmq.io.mechanism.curve.Curve;

/**
 * A ZAuth actor takes over authentication for all incoming connections in
 *  its context. You can whitelist or blacklist peers based on IP address,
 *  and define policies for securing PLAIN, CURVE, and GSSAPI connections.
 *  <br>
 * Note that libzmq provides four levels of security: default NULL (which ZAuth
 * does not see), and authenticated NULL, PLAIN, and CURVE, which ZAuth can see.
 *  <br>
 * Based on <a href="http://github.com/zeromq/czmq/blob/master/src/zauth.c">zauth.c</a> in czmq
 *
 * @author cbusbey (at) connamara (dot) com
 */
public class ZAuth
{
    public interface Authentifier
    {
        /**
         * Callback for authorizing an authenticated GSS connection.  Returns true
         * if the connection is authorized, false otherwise.  Default implementation
         * authorizes all authenticated connections.
         */
        boolean authenticate(ZapRequest request, boolean verbose);
    }

    public static class GSSAuthentifier implements Authentifier
    {
        @Override
        public boolean authenticate(ZapRequest request, boolean verbose)
        {
            if (verbose) {
                System.out.printf(
                                  "I: ALLOWED (GSSAPI allow any client) principal = %s identity = %s%n",
                                  request.principal,
                                  request.identity);
            }
            request.userId = request.principal;

            return true;
        }
    }

    /**
     * A small class for working with ZAP requests and replies.
     */
    public static class ZapRequest
    {
        private static final String ZAP_VERSION = "1.0";

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
            assert (version.equals(ZAP_VERSION));

            // Get mechanism-specific frames
            if (Mechanisms.PLAIN.name().equals(mechanism)) {
                username = request.popString();
                password = request.popString();
                clientKey = null;
                principal = null;
            }
            else if (Mechanisms.CURVE.name().equals(mechanism)) {
                ZFrame frame = request.pop();
                byte[] clientPublicKey = frame.getData();
                username = null;
                password = null;
                clientKey = Curve.z85EncodePublic(clientPublicKey);
                principal = null;
            }
            else if (Mechanisms.GSSAPI.name().equals(mechanism)) {
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

        static ZapRequest recvRequest(Socket handler)
        {
            ZMsg request = ZMsg.recvMsg(handler);
            if (request == null) {
                return null;
            }

            ZapRequest self = new ZapRequest(handler, request);

            //  If the version is wrong, we're linked with a bogus libzmq, so die
            assert (self.version.equals(ZAP_VERSION));

            // Get mechanism-specific frames
            if (!(Mechanisms.PLAIN.name().equals(self.mechanism) || Mechanisms.CURVE.name().equals(self.mechanism)
                    || Mechanisms.GSSAPI.name().equals(self.mechanism))) {
                return null;
            }

            request.destroy();
            return self;
        }

        /**
         * Send a zap reply to the handler socket
         *
         * @param request
         */
        public void reply(int statusCode, String statusText)
        {
            ZMsg msg = new ZMsg();
            msg.add(ZAP_VERSION);
            msg.add(sequence);
            msg.add(Integer.toString(statusCode));
            msg.add(statusText);
            msg.add(userId == null ? "" : userId);
            msg.add(metadata == null ? new byte[0] : metadata.bytes());
            msg.send(handler);
        }
    }

    public static final String CURVE_ALLOW_ANY = "*";

    private static final String VERBOSE   = "VERBOSE";
    private static final String ALLOW     = "ALLOW";
    private static final String DENY      = "DENY";
    private static final String TERMINATE = "TERMINATE";

    private final ZAgent     agent;
    private final ZStar.Exit exit;

    /**
     * Install authentication for the specified context. Note that until you add
     * policies, all incoming NULL connections are allowed (classic ZeroMQ
     * behavior), and all PLAIN and CURVE connections are denied.
     */
    public ZAuth(ZContext ctx)
    {
        this(ctx, new GSSAuthentifier(), "ZAuth");
    }

    public ZAuth(ZContext ctx, String actorName)
    {
        this(ctx, new GSSAuthentifier(), actorName);
    }

    public ZAuth(ZContext ctx, Authentifier gssAuthentifier, String actorName)
    {
        final ZActor.Actor actor = new AuthActor(gssAuthentifier, actorName);
        final ZActor zactor = new ZActor(ctx, null, actor, UUID.randomUUID().toString());
        agent = zactor.agent();
        exit = zactor.exit();

        // wait for the start of the actor
        agent.recv().destroy();
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
        return send(Mechanisms.PLAIN.name(), domain, filename);
    }

    /**
     * Configure CURVE authentication
     *
     * @param location Can be ZAuth.CURVE_ALLOW_ANY or a directory with public-keys that will be accepted
     */
    public ZAuth configureCurve(String location)
    {
        assert (location != null);
        return send(Mechanisms.CURVE.name(), location);
    }

    /**
     * Configure GSSAPI authentication for a given domain. GSSAPI authentication
     * uses an underlying mechanism (usually Kerberos) to establish a secure
     * context and perform mutual authentication.  To cover all domains, use "*".
     */
    public ZAuth configureGSSAPI(String domain)
    {
        assert (domain != null);
        return send(Mechanisms.GSSAPI.name(), domain);
    }

    /**
     * Destructor.
     */
    public void close()
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
    }

    private ZAuth send(String command, String... datas)
    {
        ZMsg msg = new ZMsg();
        msg.add(command);
        for (String data : datas) {
            msg.add(data);
        }
        agent.send(msg);
        msg.destroy();
        agent.recv().destroy();
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

        private boolean          verbose;                      // trace output to stdout
        private final Properties whitelist = new Properties(); // whitelisted addresses
        private final Properties blacklist = new Properties(); // blacklisted addresses
        private final Properties passwords = new Properties(); // PLAIN passwords, if loaded
        private File             passwordsFile;
        private long             passwordsModified;
        private boolean          allowAny;
        private ZCertStore       certStore = null;

        private final String       actorName;
        private final Authentifier gssAuthentifier;

        private AuthActor(Authentifier gssAuthentifier, String actorName)
        {
            this.gssAuthentifier = gssAuthentifier;
            this.actorName = actorName;
        }

        @Override
        public String premiere(Socket pipe)
        {
            return actorName;
        }

        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            //create ZAP handler and get ready for requests
            return Arrays.asList(ctx.createSocket(ZMQ.REP));
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            Socket handler = sockets.get(0);
            try {
                handler.bind("inproc://zeromq.zap.01");
                poller.register(handler, ZPoller.POLLIN);
                pipe.send(OK);
            }
            catch (ZMQException e) {
                System.out.printf("ZAuth: Error\n");
                e.printStackTrace();
                pipe.send("ERROR");
            }
        }

        @Override
        public boolean backstage(Socket pipe, ZPoller poller, int events)
        {
            ZMsg msg = ZMsg.recvMsg(pipe);

            String command = msg.popString();
            if (verbose) {
                System.out.printf("ZAuth: API command=%s\n", command);
            }
            if (command == null) {
                return false; //interrupted
            }
            boolean rc;
            if (ALLOW.equals(command)) {
                String address = msg.popString();
                if (verbose) {
                    System.out.printf("ZAuth: - whitelisting ipaddress=%s\n", address);
                }
                whitelist.put(address, OK);
                rc = pipe.send(OK);
            }
            else if (DENY.equals(command)) {
                String address = msg.popString();
                if (verbose) {
                    System.out.printf("ZAuth: - blacklisting ipaddress=%s\n", address);
                }
                blacklist.put(address, OK);
                rc = pipe.send(OK);
            }
            else if (Mechanisms.PLAIN.name().equals(command)) {
                // For now we don't do anything with domains
                @SuppressWarnings("unused")
                String domain = msg.popString();
                // Get password file and load into HashMap
                // If the file doesn't exist we'll get an empty map
                String filename = msg.popString();
                passwordsFile = new File(filename);

                if (verbose) {
                    System.out.printf(
                                      "ZAuth: - activated plain-mechanism with password-file: %s%n",
                                      passwordsFile.getAbsolutePath());
                }

                try {
                    loadPasswords(true);
                }
                catch (IOException e) {
                    // Ignore the exception, just don't read the file
                }

                rc = pipe.send(OK);
            }
            else if (Mechanisms.CURVE.name().equals(command)) {
                //  If location is CURVE_ALLOW_ANY, allow all clients. Otherwise
                //  treat location as a directory that holds the certificates.
                String location = msg.popString();
                if (location.equals(CURVE_ALLOW_ANY)) {
                    allowAny = true;
                }
                else {
                    this.certStore = new ZCertStore(location);
                    this.allowAny = false;
                }
                rc = pipe.send(OK);
            }
            else if (Mechanisms.GSSAPI.name().equals(command)) {
                // GSSAPI authentication is not yet implemented here
                @SuppressWarnings("unused")
                String domain = msg.popString();
                rc = pipe.send(OK);
            }
            else if (VERBOSE.equals(command)) {
                String verboseStr = msg.popString();
                this.verbose = Boolean.parseBoolean(verboseStr);
                rc = pipe.send(OK);
            }
            else if (TERMINATE.equals(command)) {
                rc = pipe.send(OK);
                return false;
            }
            else {
                System.out.printf("ZAuth: - Invalid command %s%n", command);
                rc = true;
            }

            msg.destroy();

            return rc;
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            ZapRequest request = ZapRequest.recvRequest(socket);
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
                        System.out.printf("I: PASSED (whitelist) address = %s\n", request.address);
                    }
                }
                else {
                    denied = true;
                    if (verbose) {
                        System.out.printf("I: DENIED (not in whitelist) address = %s\n", request.address);
                    }
                }
            }
            else if (!blacklist.isEmpty()) {
                if (blacklist.containsKey(request.address)) {
                    denied = true;
                    if (verbose) {
                        System.out.printf("I: DENIED (blacklist) address = %s\n", request.address);
                    }
                }
                else {
                    allowed = true;
                    if (verbose) {
                        System.out.printf("I: PASSED (not in blacklist) address = %s\n", request.address);
                    }
                }
            }

            //mechanism specific check
            if (!denied) {
                if (Mechanisms.NULL.name().equals(request.mechanism) && !allowed) {
                    //  For NULL, we allow if the address wasn't blacklisted
                    if (verbose) {
                        System.out.printf("I: ALLOWED (NULL)\n");
                    }
                    allowed = true;
                }
                else if (Mechanisms.PLAIN.name().equals(request.mechanism)) {
                    // For PLAIN, even a whitelisted address must authenticate
                    allowed = authenticatePlain(request);
                }
                else if (Mechanisms.CURVE.name().equals(request.mechanism)) {
                    // For CURVE, even a whitelisted address must authenticate
                    allowed = authenticateCurve(request);
                }
                else if (Mechanisms.GSSAPI.name().equals(request.mechanism)) {
                    // At this point, the request is authenticated, send to
                    //zauth callback for complete authorization
                    request.userId = request.principal;
                    allowed = gssAuthentifier.authenticate(request, verbose);
                }
                else {
                    System.out.printf("E: Skipping unknown mechanism %s%n", request.mechanism);
                }
            }

            if (allowed) {
                request.reply(200, OK);
            }
            else {
                request.metadata = null;
                request.reply(400, "NO ACCESS");
            }

            return true;
        }

        private boolean authenticatePlain(ZapRequest request)
        {
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
                    System.out.printf(
                                      "ZAUTH I: ALLOWED (PLAIN) username=%s password=%s\n",
                                      request.username,
                                      request.password);
                }
                request.userId = request.username;
                return true;
            }
            else {
                if (verbose) {
                    System.out.printf(
                                      "ZAUTH I: DENIED (PLAIN) username=%s password=%s\n",
                                      request.username,
                                      request.password);
                }

                return false;
            }
        }

        private boolean authenticateCurve(ZapRequest request)
        {
            if (allowAny) {
                if (verbose) {
                    System.out.println("zauth: - allowed (CURVE allow any client)");
                }
                return true;
            }
            else {
                if (certStore != null) {
                    if (certStore.containsPublicKey(request.clientKey)) {
                        // login allowed
                        if (verbose) {
                            System.out.printf("zauth: - allowed (CURVE) client_key=%s\n", request.clientKey);
                        }
                        request.userId = request.clientKey;
                        request.metadata = certStore.getMetadata(request.clientKey);
                        return true;
                    }
                    else {
                        // login not allowed. couldn't find certificate
                        if (verbose) {
                            System.out.printf("zauth: - denied (CURVE) client_key=%s\n", request.clientKey);
                        }
                        return false;
                    }
                }
            }
            return false;
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
}
