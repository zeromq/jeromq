# JeroMQ

Pure Java implementation of libzmq (http://zeromq.org).

[![Build Status](https://travis-ci.org/zeromq/jeromq.png)](https://travis-ci.org/zeromq/jeromq)
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
 
* Not too bad performance compared to zeromq.
 * 4.5M messages (100B) per sec.
 * [Performance](https://github.com/zeromq/jeromq/wiki/Performance).
* Exactly same developer experience with zeromq and jzmq.

## Not supported Features

* ipc:// protocol with zeromq. Java doesn't support UNIX domain socket.
* pgm:// protocol. Cannot find a pgm Java implementation.
* norm:// protocol. Cannot find a Java implementation.
* tipc:// protocol. Cannot find a Java implementation.

* GSSAPI mechanism is not yet implemented.

* TCP KeepAlive Count, Idle, Interval cannot be set via Java but as OS level.

## Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for details about the contribution process and useful development tasks.

## Usage

### Maven

Add it to your Maven project's `pom.xml`:

```xml
    <dependency>
      <groupId>org.zeromq</groupId>
      <artifactId>jeromq</artifactId>
      <version>0.4.1</version>
    </dependency>

    <!-- for the latest SNAPSHOT -->
    <dependency>
      <groupId>org.zeromq</groupId>
      <artifactId>jeromq</artifactId>
      <version>0.4.2-SNAPSHOT</version>
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

## License

All source files are copyright © 2007-2017 contributors as noted in the AUTHORS file.

Free use of this software is granted under the terms of the Mozilla Public License 2.0. For details see the file `LICENSE` included with the JeroMQ distribution.
