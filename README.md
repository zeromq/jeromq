
#JeroMQ

Java POJO zeromq (http://zeromq.org) implementation

## Features

* based on zeromq-3
* tcp:// protocol is compatible with zeromq
* not too bad performance compared to zeromq
 * 2M messages (100B) per sec
 * [Performance][https://github.com/miniway/jeromq/wiki/Perfomance] 
* exactly same develope experience with zeromq

## Not supported Features
* ipc:// protocol. Java doesn't support UNIX domain socket.
* pgm:// protocol. Cannot find a pgm Java implementation

## Usage

Add it to your Maven project's `pom.xml`:

    <dependency>
      <groupId>org.jeromq</groupId>
      <artifactId>jeromq</artifactId>
      <version>0.1.0-SNAPSHOT</version>
    </dependency>

