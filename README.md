#JeroMQ

Pure Java implementation of libzmq (http://zeromq.org)

[![Build Status](https://travis-ci.org/zeromq/jeromq.png)](https://travis-ci.org/zeromq/jeromq)

## Features

* based on libzmq 3.2.2
* ZMTP/2.0 (http://rfc.zeromq.org/spec:15)
* tcp:// protocol and inproc:// is compatible with zeromq
* ipc:// protocol works only between jeromq (uses tcp://127.0.0.1:port internally)
* not too bad performance compared to zeromq
 * 2M messages (100B) per sec
 * [Performance](https://github.com/zeromq/jeromq/wiki/Perfomance)
* exactly same develope experience with zeromq and jzmq

## Not supported Features

* ipc:// protocol with zeromq. Java doesn't support UNIX domain socket.
* pgm:// protocol. Cannot find a pgm Java implementation

## Extended Features

* build your own StreamEngine's Decoder/Encoder
 * [TestProxyTcp](https://github.com/zeromq/jeromq/blob/master/src/test/java/zmq/TestProxyTcp.java)
 * [Proxy](https://github.com/zeromq/jeromq/blob/master/src/main/java/org/jeromq/codec/Proxy.java)

## Contribution Process

This project uses the [C4 process](http://rfc.zeromq.org/spec:16) for all code changes. "Everyone,
without distinction or discrimination, SHALL have an equal right to become a Contributor under the
terms of this contract."

## Usage

Add it to your Maven project's `pom.xml`:

    <dependency>
      <groupId>org.zeromq</groupId>
      <artifactId>jeromq</artifactId>
      <version>0.3.2</version>
    </dependency>

    <!-- for the latest SNAPSHOT -->
    <dependency>
      <groupId>org.zeromq</groupId>
      <artifactId>jeromq</artifactId>
      <version>0.3.3-SNAPSHOT</version>
    </dependency>

    <!-- If you can't find the latest snapshot -->
    <repositories>
      <repository>
        <id>sonatype-nexus-snapshots</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <releases>
          <enabled>false</enabled>
        </releases>
        <snapshots>
          <enabled>true</enabled>
        </snapshots>
       </repository>
    </repositories>

If your are using the Ant 

    ant [target]


Also please refer the [Wiki](https://github.com/zeromq/jeromq/wiki)
