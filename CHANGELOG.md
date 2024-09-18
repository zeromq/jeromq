# Changelog

## v0.7.0 (2024-xx-xx)

### Added

* [#998] (https://github.com/zeromq/jeromq/pull/998)
  Add IPC support for Java 16 and above

* [#974] (https://github.com/zeromq/jeromq/pull/974)
  Implement `NetworkProtocols` as `ServiceProviders` to decouple protocol from implementation

* [#971] (https://github.com/zeromq/jeromq/pull/971)
  Attribute core developers in `pom.xml`

### Changed

* [#985] (https://github.com/zeromq/jeromq/pull/985)
  Correct set option implementation for `IP_TOS` as it was previously set to modify `SO_SNDBUF`. Update various dependency versions

* [#978] (https://github.com/zeromq/jeromq/pull/978)
  Use multi-module project organization and release

* [#976] (https://github.com/zeromq/jeromq/pull/976)
  Correct handling of unsupported protocols

* [#973] (https://github.com/zeromq/jeromq/pull/973)
  Eliminate wrapping of `SO_KEEPALIVE` settings with Java 11

* [#972] (https://github.com/zeromq/jeromq/pull/972)
  Change the minimum supported Java android version to 11

## v0.6.0 (2024-02-04)

### Added

* [#964] (https://github.com/zeromq/jeromq/pull/964)
  Exports all options default values as constant in ZMQ class. This will allow client libraries to use them.

* [#963] (https://github.com/zeromq/jeromq/pull/963)
  Replaces maven-bundle-plugin with the more up-to-date bnd-maven-plugin and configures it to generate a JPMS module 
  descriptor besides the OSGi descriptor.

* [#949] (https://github.com/zeromq/jeromq/pull/949)
  Added a way to specify a custom monitor, that don’t need to communicate through a ZMQ socket. It allows to use monitor
  for logging without hitting hard on the CPU with poller overload.
  Improved in [#950] (https://github.com/zeromq/jeromq/pull/950)

* [#942] (https://github.com/zeromq/jeromq/pull/942)
  `zmq.Ctx` can now be provided a thread factory, that can used to tweaks threads. The main purpose of this is to be able to 
  bind thread to CPU using external libraries like https://github.com/OpenHFT/Java-Thread-Affinity. The idea was 
  suggested by issue #910.

* Also adding a check that prevent modifications of some settings once they have been used by `zmq.Ctx` initialisation.

### Changed

* Many code smell removed using SonarLint.

* [#941] (https://github.com/zeromq/jeromq/issues/940)
  Fix to issue [#940](https://github.com/zeromq/jeromq/issues/940) by setting `key=null on setKey, added test

* [#939] (https://github.com/zeromq/jeromq/issues/939)
  Simplified Mailbox, always thread safe, so `MailboxSafe usages are removed and the class is marked at deprecated.

* [#936] (https://github.com/zeromq/jeromq/pull/936)

  `org.zeromq.ZSocket` and `org.zeromq.ZMQ.Socket` can now read and write `zmq.Msg` directly.

  Updating `Metada` class to provide more data access and functions. `ZMetadata` is updated too. It internally now uses
  `ConcurrentHashMap` instead of the old `Properties class`. The `Properties` class ignore null values, `ConcurrentHashMap`
  reject them but ZMTP protocol allows empty values. So null values are transformed to empty strings.

  `Metada#set` is deprecated and replaced by `Metada#put`, to be more consistent with Map API.

  Lots of hostile final method declaration removed.

## v0.5.4 (2023-09-26)

### Changed

* new GPG signature for artifact, id 9C925EE1.

* [#935](https://github.com/zeromq/jeromq/pull/935): With
  org.zeromq.ZMQ.Event.recv(socket, DONTWAIT), if there is no more,
  event to receive, it throws an NPE although the javadoc says it should
  return null. Fixed.

  Also don’t resolve event value for ZMQ_EVENT_MONITOR_STOPPED, it’s a constant.

* [#937] (https://github.com/zeromq/jeromq/pull/937): When doing PLAIN or CURVE
  authentication, the logic can be exchanged. The client is doing the bind, and
  the server doing the connect. That was not handled, a connect socket was not
  expected to reuse the session, and so found an already configured zapPipe. It’s
  now handled.

  Some error message was not returning any message, the specification says that an empty error should be returned instead.

  Big rewrite of the tests for mechanisms, the big test function is split and more code is shared.

## v0.5.3 (2022-12-03)

### Added

* [#921](https://github.com/zeromq/jeromq/pull/921): Add peer support
  disconnect

* [#906](https://github.com/zeromq/jeromq/pull/906): Fix issue where
  socket identity was failing with overflow when identity was bigger
  than 127

* [#903](https://github.com/zeromq/jeromq/pull/903): Make JeroMQ
  compatiable with Android API Level 19

* [#902](https://github.com/zeromq/jeromq/pull/902): Add tests and
  build on Java 17. Helping dependecies resolution by activating more
  profiles and don't try to publish on forks

* [#775](https://github.com/zeromq/jeromq/pull/775): ZMQ_HEARTBEAT is not
  useful without sending an hello message.To solve that, the majordomo worker
  still has to implement heartbeat. With this new option, whenever the
  connection drops and reconnects the hello message will be sent, greatly
  simplify the majordomo protocol, as now READY and HEARTBEAT can be handled by
  zeromq.

* [#783](https://github.com/zeromq/jeromq/pull/777): Jeromq is not thread-safe,
  so port CLIENT and SERVER sockets from libzmq, which are thread-safe sockets.

* [#808](https://github.com/zeromq/jeromq/pull/808): Add Client/Server support
  to ZFrame.

* [#837](https://github.com/zeromq/jeromq/pull/837): Radio-Dish implementation.

* [#880](https://github.com/zeromq/jeromq/pull/880): Port of
  https://github.com/zeromq/libzmq/pull/3871, router can handle peer
  disconnected.

* [#898](https://github.com/zeromq/jeromq/pull/898): Adding critical and
  notification exceptions handlers in zmq.poll.Poller.

### Changed

* [#919](https://github.com/zeromq/jeromq/pull/919): Fix deadlock
  issue on socket close

* [#906](https://github.com/zeromq/jeromq/pull/906): Fix issue where
  socket identity was failing with overflow when identity was bigger
  than 127

* [#903](https://github.com/zeromq/jeromq/pull/903): Make JeroMQ
  compatiable with Android API Level 19

* [#777](https://github.com/zeromq/jeromq/pull/777): ZMQ.Socket now remember
  the ZContext that created it and remove from it when closed.

* Many improvement to error handling, with more error messages.

* [#772](https://github.com/zeromq/jeromq/pull/772): Fix ZMQ_REQ_CORRELATE.

* [#797](https://github.com/zeromq/jeromq/pull/797): A new ZBeacon implementation.

* [#814](https://github.com/zeromq/jeromq/pull/814): IPC protocol now comply to
  java.net.preferIPv4Stack or java.net.preferIPv6Addresses for the choice of
  the TCP stack to use.

* [#825](https://github.com/zeromq/jeromq/pull/825): Improved monitor, with
  added events in some mechanisms.

## v0.5.2 (2020-01-31)

### Added

* [#715](https://github.com/zeromq/jeromq/pull/715): Added a ZCert constructor
  that takes a Writer as an argument, in order to support writing to the
  Writer instead of to a file.

* [#716](https://github.com/zeromq/jeromq/pull/716): Added a ZTicket API, as
  well as a ZTicker API, which combines ZTimer and ZTicket.

* [#724](https://github.com/zeromq/jeromq/pull/724): Added support for the XPUB
  options `ZMQ_XPUB_MANUAL` and `ZMQ_XPUB_VERBOSER`.

* [#727](https://github.com/zeromq/jeromq/pull/727): Added a ZSocket constructor
  that takes a SocketType enum value as an argument.

* [#747](https://github.com/zeromq/jeromq/pull/747): Improvements to
  ZBeacon:
  * Added `startClient` and `startServer` methods, to support restarting the
    client or server individually.
  * You can now specify the interface address when constructing a
    BroadcastClient.

* [#755](https://github.com/zeromq/jeromq/pull/755): Added ZCert constructors
  that take (mandatory) public and (optional) secret keys as arguments.

### Changed

* Fixes for Android compatibility:
  * [#710](https://github.com/zeromq/jeromq/pull/710): Use traditional loops
    instead of streams.
  * [#717](https://github.com/zeromq/jeromq/pull/717): Don't use
    `Map.computeIfAbsent`.
  * [#736](https://github.com/zeromq/jeromq/pull/736): Use java.util.Iterator
    instead of lambdas.
  * [#752](https://github.com/zeromq/jeromq/pull/752): Various fixes discovered
    by creating an Android project within the JeroMQ repo for testing purposes.

* [#720](https://github.com/zeromq/jeromq/pull/720): Removed a println debug
  statement in `Poller.rebuildSelector`.

* [#733](https://github.com/zeromq/jeromq/pull/733): Fixed a bug introduced in
  JeroMQ 0.5.1 where `ZPoller.poll` was returning -1 instead of 1.

* [#735](https://github.com/zeromq/jeromq/pull/735): Fixed bugs related to
  the handling of bytes in the Msg class.

* [#759](https://github.com/zeromq/jeromq/pull/759): Fixed an
  IndexOutOfBoundsException that occurs when the number of subscriptions exceeds
  the HWM.

## v0.5.1 (2019-04-03)

### Added

* [#677](https://github.com/zeromq/jeromq/pull/677): ZPoller now supports
  registering multiple event handlers on a single socket or channel.

* [#685](https://github.com/zeromq/jeromq/pull/685),
  [#687](https://github.com/zeromq/jeromq/pull/687): ZMQ.Socket has new methods
  that encode and decode messages based on a picture pattern which is compatible
  to ZProto: `sendPicture`, `recvPicture`, `sendBinaryPicture` and
  `recvBinaryPicture`.

* [#692](https://github.com/zeromq/jeromq/pull/692): Added an overload of the
  ZBeacon that has an additional `serverAddress` option so that the broadcast
  address can be specified. The default value is still `255.255.255.255`.

* [#694](https://github.com/zeromq/jeromq/pull/694): Added a draft ZNeedle
  helper class for serialization and deserialization within a frame.

* [#697](https://github.com/zeromq/jeromq/pull/697): Added encoding/decoding of
  the `COMMAND` flag when using CURVE encryption.

* [#698](https://github.com/zeromq/jeromq/pull/698): Added a
  `Msg.putShortString` method.

### Changed

* [#671](https://github.com/zeromq/jeromq/pull/671),
  [#672](https://github.com/zeromq/jeromq/pull/672): In the internal
  `zmq.io.StreamEngine` class, a `ZError.InstantiationException` is now thrown
  when a decoder or encoder cannot be instantiated.  Previously, a stacktrace
  would be printed and `null` would be returned instead of a decoder/encoder
  instance.

* [#673](https://github.com/zeromq/jeromq/pull/673): `zmq.Mailbox.recv` now
  handles `EINTR` by returning `null`. This can happen, for example, if the
  channel is closed.

* [#679](https://github.com/zeromq/jeromq/pull/679): Fixed a file descriptor
  leak when opening a TCP connection.

* [#680](https://github.com/zeromq/jeromq/pull/680): Various improvements to
  support for IPv6 and name resolution.

  IPv6 is now enabled if the properties `java.net.preferIPv4Stack=false` or
  `java.net.preferIPv6Addresses=true` are set.

* [#684](https://github.com/zeromq/jeromq/pull/684): Fixed a bug where
  `zmq.Msg.getBytes` was writing to an internal buffer instead of the given
  buffer.

* [#688](https://github.com/zeromq/jeromq/pull/688): Javadoc fixes.

* [#691](https://github.com/zeromq/jeromq/pull/691): Fixed a bug where timers
  would accumulate in the PollerBase when failed connections were retried,
  causing a memory leak.

* [#693](https://github.com/zeromq/jeromq/pull/693): Fixed a Java 8-related
  compilation error.

* [#702](https://github.com/zeromq/jeromq/pull/702): Removed all usage of
  `java.util.function`, `java.util.stream`, `java.util.Objects` and
  `java.util.Optional`, which are known to cause problems for some versions of
  Android. Replaced their usage with internal implementations.

## v0.5.0 (2019-02-18)

### Added

* [#539](https://github.com/zeromq/jeromq/pull/539),
  [#552](https://github.com/zeromq/jeromq/pull/552),
  [#573](https://github.com/zeromq/jeromq/pull/573): Implemented heartbeating
  between sockets as specified in
  [https://rfc.zeromq.org/spec:37/ZMTP](https://rfc.zeromq.org/spec:37/ZMTP).

* [#556](https://github.com/zeromq/jeromq/pull/556): There is now a `SocketType`
  enum that can be used when creating sockets. This is recommended over the old
  way of using integer values, which is error-prone. The overload of
  `ZContext.createSocket` that takes an integer is still supported, but is now
  marked deprecated.

* [#559](https://github.com/zeromq/jeromq/pull/559): Added `recvStream` and
  `recvStrStream` instance methods to the `ZMQ.Socket` class. These expose a
  Stream of incoming messages, each of which is a `byte[]` or `String`,
  respectively.

* [#560](https://github.com/zeromq/jeromq/pull/560): ZMsg instance methods can
  now be chained.

* [#576](https://github.com/zeromq/jeromq/pull/576): Added an overload of
  `ZMsg.recvMsg` that takes a `Consumer<ZMsg> handler` and a
  `Consumer<ZMQException> exceptionHandler` for handling the result of
  attempting to receive a message on a socket.

* [#586](https://github.com/zeromq/jeromq/pull/586): Implemented a Timer API
  based on the one added to libzmq in version 4.2.

* [#590](https://github.com/zeromq/jeromq/pull/590): Added a `closeSelector`
  method to the ZContext class, to expose a way for selectors created by
  `createSelector` to be closed. Note that both of these methods are also
  deprecated; it is not recommended to manage selectors directly, as these are
  managed for you by pollers.

* [#614](https://github.com/zeromq/jeromq/pull/614): Added a
  `ZMQ_SELECTOR_PROVIDERCHOOSER` socket option that allows you to define a
  custom
  [SelectorProvider](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/spi/SelectorProvider.html).

### Changed

* [**JeroMQ no longer supports Java
  7.**](https://github.com/zeromq/jeromq/pull/557) Dropping support for Java 7
  allows us to leverage the new features of Java 8, including the use of lambda
  syntax to create IAttachedRunnable and IDetachedRunnable.

* Refactored code to use Java 8 features like lambdas and streams in various
  places. See [#570](https://github.com/zeromq/jeromq/pull/571) and
  [#650](https://github.com/zeromq/jeromq/pull/650), for example.

* [#510](https://github.com/zeromq/jeromq/pull/510): Polling now measures time
  in nanoseconds instead of microseconds, ensuring a higher degree of precision.

* [#523](https://github.com/zeromq/jeromq/pull/523): Fixed a bug where ZLoop was
  closing the context.

* [#525](https://github.com/zeromq/jeromq/pull/525): Fixed a bug in
  `zmq.io.StreamEngine` that was causing an infinite loop in the IO thread,
  blocking the application with 100% CPU usage.

* [#527](https://github.com/zeromq/jeromq/pull/527): Fixed a bug in
  `zmq.io.StreamEngine` where data could still be present inside the handshake
  buffer that was not decoded.

* [#535](https://github.com/zeromq/jeromq/pull/535): Fixed an edge case where,
  once in a blue moon, after a socket fails to connect, and is subsequently
  closed, it would still try to reconnect as if it weren't closed.

* [#546](https://github.com/zeromq/jeromq/pull/546): Prior to this release, when
  a ZContext was initialized, its internal `ZMQ.Context` would be null
  initially, and the ZContext `isClosed` instance method would misleadingly
  return `true`; the `ZMQ.Context` was being created lazily when `getContext`
  was called on the ZContext. Worse, the internal context would be reset to
  `null` after closing a ZContext, which could lead to problems if another
  thread happened to try to use the ZContext after it was closed, resulting in a
  new `ZMQ.Context` secretly being created. Now, the internal `ZMQ.Context` is
  created upon initialization of the ZContext, and you can rely on it never
  being null.

* [#548](https://github.com/zeromq/jeromq/pull/550): Various javadoc updates and
  improvements. There is now a `@Draft` annotation to identify work-in-progress
  APIs that are unstable or experimental.

* [#552](https://github.com/zeromq/jeromq/pull/552): Fixed a bug where internal
  command messages (e.g. HEARTBEAT commands) were disrupting the REQ state
  machine.

* [#564](https://github.com/zeromq/jeromq/pull/564): Implemented the ability to
  bind a socket via IPC or TCP with a dynamic ("wildcard") port and retrieve it
  via `ZMQ.ZMQ_LAST_ENDPOINT`. Leveraged this to make our test suite more
  reliable.

* [#569](https://github.com/zeromq/jeromq/pull/569): Fixed an issue where an
  overridable method was being used in the ZStar constructor.

* [#578](https://github.com/zeromq/jeromq/pull/578): Fixed an issue where an
  errno of 48 ("address already in use") would persist longer than intended
  in the process of binding to a random port. Now, errno is reset to 0 after a
  port is found.

* [#581](https://github.com/zeromq/jeromq/pull/581): Fixed a bug where, if
  you're polling in one thread and you close the context in another thread, it
  would result in an uncaught ClosedSelectorException.

* [#583](https://github.com/zeromq/jeromq/pull/583): Fixed a race condition
  causing `ZMQ_CONNECT_RID` to sometimes be assigned to the wrong peer socket.

* [#597](https://github.com/zeromq/jeromq/pull/597): Fixed a bug causing the
  context to hang indefinitely after calling `destroy()`, if multiple sockets
  had connected to the same socket.

* [#609](https://github.com/zeromq/jeromq/pull/609): For numerous methods, when
  invalid arguments are passed to the method, an InvalidArgumentException with a
  friendly error message will now be thrown, instead of an assertion error.

* [#610](https://github.com/zeromq/jeromq/pull/610): Added some asserts in
  places where there could potentially be NullPointerExceptions.

* [#623](https://github.com/zeromq/jeromq/pull/623): `Options.rcvbuf` and
  `Options.sndbuf` will now adjust `Config.IN_BATCH_SIZE` and
  `Config.OUT_BATCH_SIZE` accordingly.

* [#634](https://github.com/zeromq/jeromq/pull/634): We are now using a 64-bit
  long, instead of a 32-bit integer, as a cursor in the internal
  `java.zmq.Signaler` class. This change should not affect the library user,
  except that it will now take longer for the value to overflow. Previously,
  with the 32-bit integer cursor, the Signaler could overflow within a month or
  so under heavy load, causing serious problems such as a server being unable to
  accept new client connections.

* [#642](https://github.com/zeromq/jeromq/pull/642),
  [#646](https://github.com/zeromq/jeromq/pull/646),
  [#652](https://github.com/zeromq/jeromq/pull/652): Removed debug printing
  intended for development use only.

* [#643](https://github.com/zeromq/jeromq/pull/643): Added some checks in parts
  of the codebase related to encryption and authentication mechanisms.

* [#652](https://github.com/zeromq/jeromq/pull/652): IOExceptions that occur
  during polling will now set `errno` more accurately depending on the
  exception. Previously, the `errno` would always be set to `EINTR` when an
  IOException occurs during polling.

* [#653](https://github.com/zeromq/jeromq/pull/653): `ZError.toString` now
  defaults to `"errno " + Integer.toString(code)` if a string version of that
  error code hasn't been implemented.

* [#654](https://github.com/zeromq/jeromq/pull/654): In a low-level place where
	an `IllegalStateException` was thrown with no arguments before, the string
  value of the `errno` is now included to provide some context.

* [#655](https://github.com/zeromq/jeromq/pull/655): In a low-level place in
  the polling code, `EINTR` is now correctly reported to indicate that polling
  was interrupted, whereas we used to miss it and try to poll again.

* [#657](https://github.com/zeromq/jeromq/pull/657): When destroying a ZPoller,
  we will no longer close the poller's Selector, as that is handled by the
  context.

* [#659](https://github.com/zeromq/jeromq/pull/659): Made internal
  optimizations to ZContext. The only visible change should be that the order of
  the sockets when you call `getSockets()` is no longer deterministic, as we are
  now storing them internally in a Set rather than a List.

* [#660](https://github.com/zeromq/jeromq/pull/660): When creating a socket and
  the `maxSockets` limit is reached, a ZMQException is now thrown instead of the
  more generic IllegalStateException.

## v0.4.3 (2017-11-17)

### Added

* [#470](https://github.com/zeromq/jeromq/pull/470): Added an argument to the
  ZBeacon constructor to configure datagram socket blocking behavior. The
  default behavior (non-blocking) is preserved when the argument is omitted.

* [#474](https://github.com/zeromq/jeromq/pull/474),
  [#475](https://github.com/zeromq/jeromq/pull/475),
  [#477](https://github.com/zeromq/jeromq/pull/477),
  [#479](https://github.com/zeromq/jeromq/pull/479) Added features:
  * ZAuth, an actor that manages authentication and handles ZAP requests.
  * ZCert, an abstraction for CURVE certificates.
  * ZCertStore, a sub-optimal store for certificates.
  * ZConfig, to manage the ZPL file format.
  * ZMonitor, for simplified socket monitoring.
  * Reinstated support for the `ZMQ_MSG_ALLOCATOR` option. Added a
    `setMsgAllocator` method in the ZMQ class for setting a custom message
    allocator.

* [#477](https://github.com/zeromq/jeromq/pull/477): Added an overload of
  `ZAgent.recv` that takes a timeout argument.

* [#498](https://github.com/zeromq/jeromq/pull/498): Implemented `Closable` for
  `ZMQ.Poller`, providing a way to call `.close()` on a poller when you're done
  with it and free the selector resource to avoid memory leaks.

  It is recommended that you either close a poller or terminate the context when
  you are done polling.

### Changed

* Miscellaneous Javadoc documentation tweaks/fixes.

* [#453](https://github.com/zeromq/jeromq/pull/453),
  [#462](https://github.com/zeromq/jeromq/pull/462),
  [#471](https://github.com/zeromq/jeromq/pull/471): Fixed Android-specific
  compilation issues.

* [#454](https://github.com/zeromq/jeromq/pull/454): Fixed an issue where the
  router was interpreting peers' socket identities as UTF-8 strings instead of
  raw bytes.

* [#460](https://github.com/zeromq/jeromq/pull/460): Fixed an issue where CURVE
  keys were being parsed as strings.

* [#461](https://github.com/zeromq/jeromq/pull/461),
  [#501](https://github.com/zeromq/jeromq/pull/501): Fixed protocol handshake
  issues that were causing interoperability problems between applications using
  different versions of ZeroMQ/JeroMQ.

* [#465](https://github.com/zeromq/jeromq/pull/465) Various small fixes:
  * Fixed an uncaught divide by zero exception
    ([#447](https://github.com/zeromq/jeromq/issues/447)).
  * ZMQ.Socket class is no longer final.
  * Handle interrupt caused by close in ZBeacon.

* [#468](https://github.com/zeromq/jeromq/pull/468): Fix an issue where sockets
  would disconnect when network connection was lost.

* [#469](https://github.com/zeromq/jeromq/pull/469) Various small fixes:
  * Fixed an error in comparison of byte arrays in the Mechanism class.
  * Handled the possibility of receiving a null message in ZSocket by returning
    null instead of throwing an uncaught NullPointerException.
  * Fixed the return value of ZMQ.setHWM, which indicates the status of the
    lower-level calls to set the send and receive HWM, but was doing so
    incorrectly.

* [#478](https://github.com/zeromq/jeromq/pull/478): Fixed an issue where, when
  using an XPUB/XSUB proxy, the PUB socket was throwing an error when attempting
  to send a message if all of the subscriptions have been removed.

* [#479](https://github.com/zeromq/jeromq/pull/479): Various internal
  improvements.

* [#486](https://github.com/zeromq/jeromq/pull/486): Fixed an issue where it was
  not possible to send two messages in a row without a successful receive in
  between, even with the RELAXED option set on the REQ socket.

* [#487](https://github.com/zeromq/jeromq/pull/487) Various improvements:
  * Added some method name aliases for compatibility with the jzmq API, in
    places where the JeroMQ method names differed.
  * Miscellaneous internal refactoring to make JeroMQ code more similar to that
    of jzmq.
  * It is not possible to get the values of the ZMQ options `ZMQ_REQ_CORRELATE`
    and `ZMQ_REQ_RELAXED`, so `getReqCorrelated` and `getReqRelaxed` are now
    deprecated and will throw an UnsupportedOperationException when called.

* [#492](https://github.com/zeromq/jeromq/pull/492): Fixed an issue where a
  NullPointerException was thrown when trying to bind on an already used port,
  for example when the socket has a monitor.

* [#502](https://github.com/zeromq/jeromq/pull/502): Use explicit mutex locks to
  help prevent problems caused by concurrent access to a ZContext. This makes
  ZContext behave more like libzmq's zctx.

## v0.4.2 (2017-06-29)

* [#443](https://github.com/zeromq/jeromq/pull/443): Fix issue where JeroMQ was
broken on Android. Security no longer depends on libsodium and is now pure Java

## v0.4.1 (2017-06-28)

### Added

JeroMQ is now based off of 4.1.7 of libzmq which means it now supports additional security features.

### Changed

* [#413](https://github.com/zeromq/jeromq/pull/413): fixed a NullPointerException when ZMQ.ZMQ_TCP_ACCEPT_FILTER is used

* [#412](https://github.com/zeromq/jeromq/pull/412): tcp accept filter null pointer exception fix

## v0.4.0 (2017-03-22)

### Added

* [#366](https://github.com/zeromq/jeromq/pull/366): support for `ZMQ_REQ_RELAXED` and `ZMQ_REQ_CORRELATE` socket options

* [#375](https://github.com/zeromq/jeromq/pull/375): re-added `ZMQ.Socket.disconnect`, which had been removed in 0.3.6 because the contributor who originally added it did not agree to the license change from LGPL to MPLv2

### Changed

* [#374](https://github.com/zeromq/jeromq/pull/374):
  * fixed a NullPointerException and mangling of existing indexes in ZMQ.Poller
  * fixed a Windows bug in Signaler
  * other small changes to keep JeroMQ in sync with jzmq

* [#386](https://github.com/zeromq/jeromq/pull/386): improved deallocation of polling Selector resources. When creating a poller via `ZMQ.Context.poller` or `ZContext.createPoller`, the context will manage the Selector resources and ensure that they are deallocated when the context is terminated.

* [#387](https://github.com/zeromq/jeromq/pull/387): (**BREAKING CHANGE**) It is no longer possible to create a ZMQ.Poller in any way except via a context.  This is to ensure that all Selector resources are deallocated when a context is terminated.

* [#388](https://github.com/zeromq/jeromq/pull/388) `ZMQ.Socket.setLinger` can now be called safely after a context is terminated.

* [#390](https://github.com/zeromq/jeromq/pull/390): fixed a bug where terminating a context while polling would sometimes cause a ClosedChannelException.

* [#399](https://github.com/zeromq/jeromq/pull/399): fixed a NullPointerException that would sometimes occur when terminating a context

* [#400](https://github.com/zeromq/jeromq/pull/400): (**BREAKING CHANGE**)
  * deprecated the setters `setIoThreads`, `setMain` and `setContext` in `ZContext`. These parameters are set in the constructor and `final`. Because it is no longer possible to set these values after constructing a ZContext, the setters are now no-ops.

* [#402](https://github.com/zeromq/jeromq/pull/402): added constructors for ZPoller that take a ZContext argument, thus making it possible to create a ZPoller whose Selector resources are managed by the context.

## v0.3.6 (2016-09-27)

### Added

* [#292](https://github.com/zeromq/jeromq/pull/292/commits/12befcb27f13572a5a49669e433a399c3e5a72ac): support for `ZMQ_XPUB_NODROP` and `ZMQ_XPUB_VERBOSE_UNSUBSCRIBE` options

* [#299](https://github.com/zeromq/jeromq/pull/299): a setter for UncaughtExceptionHandlers in ZBeacon threads

* [#309](https://github.com/zeromq/jeromq/pull/309): MsgAllocator allows you to define how Msgs are allocated.

* [#316](https://github.com/zeromq/jeromq/pull/316): ZSocket high-level API allows you to work with sockets directly without having to manage the ZMQ context.

### Changed

* [**JeroMQ no longer supports Java 6.**](https://github.com/zeromq/jeromq/pull/316/commits/3cafb3babdb7509ec7adb705e1dacb6a804294a7)

* Changed from LGPL to [MPLv2](https://www.mozilla.org/en-US/MPL/2.0/) license.

* Related to changing license, the following changes were made as a result of reverting pre-0.3.6 commits by contributors who did not agree to the license change:
  * `ZMQ.Socket.disconnect` method removed
  * [Slight changes to the way ephemeral ports are handled](https://github.com/zeromq/jeromq/pull/354/commits/f455c740be4950ea7973276c33141008dadd97e7).

* [#266](https://github.com/zeromq/jeromq/pull/266): fixed a NullPointerException bug in `ZMsg.dump` when attempting to dump a ZMsg after its frames have been cleared

* [#271](https://github.com/zeromq/jeromq/pull/271), [#272](https://github.com/zeromq/jeromq/pull/272): misc fixes and improvements to ZAgent, ZActor, ZProxy, and ZStar

* [#295](https://github.com/zeromq/jeromq/pull/295): renamed `ZMQ.Socket.setRouterHandlover` to `ZMQ.Socket.setRouterHandover` (typo fix)

* [#301](https://github.com/zeromq/jeromq/pull/301): fixed [a bug](https://github.com/zeromq/jeromq/issues/280) where if a frame failed to send, it would still try to send the next frame

* [#306](https://github.com/zeromq/jeromq/pull/306), [#308](https://github.com/zeromq/jeromq/pull/308), [#311](https://github.com/zeromq/jeromq/pull/311): misc byte buffer performance improvements and bugfixes

* [#324](https://github.com/zeromq/jeromq/pull/324): implementation changes to avoid extra bytes being copied in PUB/SUB

## v0.3.5 (2015-07-15)

 * Capitalize constants
 * Use for each style
 * Issue #152 - Add unit test to test ZContext.close
 * Fix mislabeling issue
 * remove me from the AUTHORS file
 * Sometimes hostname resolution will fail.  Make sure that, if it does, we don't break the ioloop.
 * Narrowed exception handling a bit.  Makes me kind of nervous because the exceptions thrown are not documented.
 * remove email addresses
 * Fix issue #166 - Able to build using java 8
 * Fix issue #166 - Fix building project in a backwards compatible way
 * Replace home grown Multimap with guava
 * Update README.md
 * Normalize license header preamble that mirrors libzmq
 * Add Trevor Bernard as a contributor and sort authors
 * typo readme
 * Fix issue #176 - Remove auto-generated ant build files
 * Overload Socket send
 * Fix style violation of unittests
 * fix bug where poll does not accept -1 as argument
 * Issue #176 - Remove build.xml ant file
 * Fix all style violations
 * Remove superfluous limit
 * Move the wcursor increment after the assert
 * Improve imports
 * Issue #191 - Generates excess garbage on close
 * Port JeroMQ to be based on libzmq 3.2.5
 * Remove public method declaration in interfaces
 * Revert "Remove public method declaration in interfaces"
 * Change Chunk<T> to be a static inner class
 * Fix raw type parameterized warnings
 * Change constructor and method declarations to be public
 * Update plugins
 * Fix issue where project wasn't correctly importing using new m2eclipse plugin
 * Remove redundant if
 * Revert checkstyle plugin update to fix build error
 * Add ZBeacon implementation
 * Fix checkstyle errors
 * Problem: beacon messages are not always filtered out for local addresses
 * Problem: current ZBeacon tests are not testing whether messages are received.
 * Fix typo
 * Revert "Replace home grown Multimap with guava"
 * Remove redundant static modifier
 * Remove redundant encoding entry
 * Fix java6 build problem where req was failing with BOTTOM illegalstateexception
 * Remove redundant method
 * Fix #209 - Set errno on SocketBase instead of throwing IllegalArgumentException
 * Fix issue #197 - Don't call setReuseAddress on windows
 * Change version to 3.2.5
 * Make Mailbox,Thread and Reaper closeable
 * Router Handover
 * Rename xterminated into xpipeTerminated to follow libzmq
 * Fix exception for inproc bind fail
 * Fix issue #200
 * Remove redundant nested static modifer from interfaces
 * Ignore .checkstyle file
 * Fixed two bugs in test path. In flserver3.java ZFrame.equals(string) will always return false. and in cloneserv6.java equals method is called on an Array.
 * Revert "Remove redundant method"
 * Test receiving correctly a prefetched message when using a poller
 * Fix issue #228 - Add ZMQ_BLOCKY to Context to get a less surprising behaviour on context termination
 * Implementation proposal for Z-Components: ZPoller, ZAgent, ZStar, ZActor, ZProxy
 * Fixed typo in Features section.
 * Aligned punctuation and capitalized first letter in sentences.
 * pom.xml: missing bracket
 * Change ZMQ.bind() method to return void.
 * Fixed minor issues - documentation (javadoc links, ..) - possible NPEs - simplified some statements, removed unnecessary variables, ...
 * Break loop on finding the first non-printable character
 * Fix issue #243 - Add a copy section in the README specifying the license
 * Fix issue #245 - Double socket close no longer hangs
 * Set daemon flag on poller threads.
 * Set daemon flag on beacon and zthread threads.
 * Fix Spinning in Reaper Thread
 * Added constructors to ZMQException
 * Changed ZFrame.recvFrame to return null in non-overloaded method
 * Added ENOTSOCK error code
 * Added EAGAIN error (code already present)
 * Fix resource leak at socket close
 * Fix c-style method name

## v0.3.4 (2014-05-15)

* Various code improvements
* Add unbind method to org.zeromq.ZMQ.Socket
* Added double checked locking for shared variable context. getContext() and createSocket() should now be thread safe.
* Extend support for ZMQ monitors to inline with jzmq
* Apply checkstyle and sample changes
* Fixed recvFrame to return null on no data. Added Test cases.
* Corrected ZMsg documentation.
* Adds lazy create context to getContext() method
* Fix wrong Router xwrite_activated assert
* Raise exception when bind fails
* Fix issue #80
* throw an exception if the ByteBuffer provided to Msg is not flipped
* re-resolve tcp addresses on reconnections
* add convenience methods to set TCP keep alive options
* Refactor Msg to better handle memory and Java idiomatic
* Force StreamEngine to use big endian
* Remove org.jeromq.* namespace and associated tests
* Revert back to use currentTimeMillis because it's less expensive than nanoTime
* Fix issue #122 - handshake now uses ByteBuffer accessor methods directly

## v0.3.2 (2013-12-10)

* Various code improvements
* Update junit to version 4.11
* Fix issue #115 - Expose all Context options
* Fix issue #58 - XPUB can receive multipart messages
* Fix issue #109 - Make ZMQ.Context and ZMQ.Socket implement java.io.Closeable
* Use UTF-8 as default charset
* Use monotonic source for time
* Use try finally idiom on locks
* Backport fix for race condition on shutdown
* sendByteBuffer should return number of sent bytes

## v0.3.0 (2013-11-03)

* [maven-release-plugin] prepare release v0.3.0
* Prepare for release
* Update maven plugins
* Change groupId to zeromq
* Use the org.zeromq groupId
* Add build status icon
* Fix issue #95 - Add travis-ci support
* remove usage of bytebuffer just for the sake of a byte array
* use configurable Charset in every String.getBytes() and new String()
* support DirectByteBuffer on socket.sendByteBuffer()
* ignore whole target and also ignore Eclipse's .settings folder
* fixes zeromq/jeromq#86
* Improved handling of ephemeral ports
* Possible fix for a memory leak in Poller.fd_table.
* subscriber should ignore HUGZ
* support ZMQ_DELAY_ATTACH_ON_CONNECT socket option
* Close inproc socket pairs on zmq_disconnect
* Rewrite TestConnectDelay
* Backport for LIBZMQ-541 fix
* Fix issue when building with Ant and system default encoding is not UTF-8
* Update clonesrv6.java
* Ignore CtxTerminatedException at ZContext.destroy
* Fix issue #76, #77 but at topic remove at trie
* Remove global errno
* expose special purpose raw zmq.SocketBase
* Work around for LIBZMQ-496 The problem is that other threads might still be in mailbox::send() when it is destroyed. So as a workaround, we just acquire the mutex in the destructor. Therefore the running send will finish before the mailbox is destroyed.
* patch for issue 456 Do not filter out duplicate subscriptions on the XSUB side of XSUB/XPUB, so that ZMQ_XPUB_VERBOSE doesn't get blocked by forwarding devices (as long as the devices all use ZMQ_XPUB_VERBOSE)
* Issue #72 resource leak at Reaper
* Issue #70 Remove thread local at errno
* Fix IPv6 address parsing.
* added osgi manifest headers with maven-bundle-plugin
* osgi manifest
* Fix issue #66 - Add ByteBuffer API to Sockets for sending and receiving messages
* Fix a bug that socket disconnect didn't terminate properly
* add setTCPKeepAlive socket option
* add a pom helper for the latest sonatype snapshot
* fix missing frame at monitoring
* Add chapter 5 guide
* ZMsg.recv documentation of flag options
* new timer during handling timer_event doesn't set correctly
* chapter 4 java guide
* fix a bug which unsubscribe doesn't work correctly
* Set the compiler version to 1.6
* Suppress platform dependent encoding warning
* ZContext.close doesn't have to throw an exception
* implement Closeable on ZContext
* user friendly error at bind failure
* change jeromq package namespace and cleanup guide
* Add set method for sockopt ZMQ_XPUB_VERBOSE
* Add disconnect method
* Ignore eclipse workspace files
* rewrite poller as it compatile with jzmq
* converted asyncsrv guide example to use the org.zeromq packaged code, and updated for the slightly different API.
* fix constant collision between jzmq and czmq
* simplify the ZMQ mayRaise logic
* fix typo
* make jzmq compatible and update examples
* LIBZMQ-497 send unsent data in encoder buffer at termination
* moving namespace from org.jeromq to org.zeromq
* Issue#34 inproc connect should raise ZMQException
* fix POLLOUT polling causes InvalidArgumentException
* jdk epoll bug workaround
* ZMsg.send returns boolean value
* handle ConcurrentModification Exception
* returns -1 with EAGAIN when mandatory is set and pipe is full
* enhance device code
* update README about 0.2.0 release
* remove persistence related code
* persistence helper encoder
* start 0.3.0-SNAPSHOT
