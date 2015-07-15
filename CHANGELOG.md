# Changelog

## v0.3.6

## v0.3.5

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

## v0.3.4

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

## v0.3.2

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

## v0.3.1

* [maven-release-plugin] prepare release v0.3.1
* Update README.md
* [maven-release-plugin] prepare for next development iteration

## v0.3.0

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
