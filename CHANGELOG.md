# Changelog

## v0.4.1 (unreleased)

### Changed

* [#413](https://github.com/zeromq/jeromq/pull/413): fixed a NullPointerException when ZMQ.ZMQ_TCP_ACCEPT_FILTER is used

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
