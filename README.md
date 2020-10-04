
# JeroMQ

Pure Java implementation of libzmq (http://zeromq.org).

[![CircleCI](https://circleci.com/gh/zeromq/jeromq.svg?style=svg)](https://circleci.com/gh/zeromq/jeromq)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=zeromq_jeromq&metric=alert_status)](https://sonarcloud.io/dashboard?id=zeromq_jeromq)
[![Coverage Status](https://coveralls.io/repos/github/zeromq/jeromq/badge.svg?branch=master)](https://coveralls.io/github/zeromq/jeromq?branch=master)
[![Maven Central](https://img.shields.io/maven-central/v/org.zeromq/jeromq.svg)](https://maven-badges.herokuapp.com/maven-central/org.zeromq/jeromq)
[![Javadocs](http://www.javadoc.io/badge/org.zeromq/jeromq.svg)](http://www.javadoc.io/doc/org.zeromq/jeromq)

## Features

* Based on libzmq 4.1.7.
* ZMTP/3.0 (http://rfc.zeromq.org/spec:23).
* tcp:// protocol and inproc:// is compatible with zeromq.
* ipc:// protocol works only between jeromq (uses tcp://127.0.0.1:port internally).

* Securities
  * [PLAIN](http://rfc.zeromq.org/spec:24).
  * [CURVE](http://rfc.zeromq.org/spec:25).

* Performance that's not too bad, compared to native libzmq.
  * 4.5M messages (100B) per sec.
  * [Performance](https://github.com/zeromq/jeromq/wiki/Performance).
* Exactly same developer experience with zeromq and jzmq.

## Unsupported

* ipc:// protocol with zeromq. Java doesn't support UNIX domain socket.
* pgm:// protocol. Cannot find a pgm Java implementation.
* norm:// protocol. Cannot find a Java implementation.
* tipc:// protocol. Cannot find a Java implementation.

* GSSAPI mechanism is not yet implemented.

* TCP KeepAlive Count, Idle, Interval cannot be set via Java but as OS level.

* Interrupting threads is still unsupported: library is NOT Thread.interrupt safe.

## Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for details about the contribution process and useful development tasks.

## Usage

### Maven

Add it to your Maven project's `pom.xml`:

```xml
    <dependency>
      <groupId>org.zeromq</groupId>
      <artifactId>jeromq</artifactId>
      <version>0.5.2</version>
    </dependency>

    <!-- for the latest SNAPSHOT -->
    <dependency>
      <groupId>org.zeromq</groupId>
      <artifactId>jeromq</artifactId>
      <version>0.5.3-SNAPSHOT</version>
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
```

### Ant

To generate an ant build file from `pom.xml`, issue the following command:

```bash
mvn ant:ant
```

## Getting started

### Simple example

Here is how you might implement a server that prints the messages it receives
and responds to them with "Hello, world!":

```java
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

public class hwserver
{
    public static void main(String[] args) throws Exception
    {
        try (ZContext context = new ZContext()) {
            // Socket to talk to clients
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5555");

            while (!Thread.currentThread().isInterrupted()) {
                // Block until a message is received
                byte[] reply = socket.recv(0);

                // Print the message
                System.out.println(
                    "Received: [" + new String(reply, ZMQ.CHARSET) + "]"
                );

                // Send a response
                String response = "Hello, world!";
                socket.send(response.getBytes(ZMQ.CHARSET), 0);
            }
        }
    }
}
```

### More examples

The JeroMQ [translations of the zguide examples](src/test/java/guide) are a good
reference for recommended usage.

### Documentation

For API-level documentation, see the
[Javadocs](http://www.javadoc.io/doc/org.zeromq/jeromq).

This repo also has a [doc](doc/) folder, which contains assorted "how to do X"
guides and other useful information about various topics related to using
JeroMQ.

## License

All source files are copyright © 2007-2020 contributors as noted in the AUTHORS file.

Free use of this software is granted under the terms of the Mozilla Public License 2.0. For details see the file `LICENSE` included with the JeroMQ distribution.

