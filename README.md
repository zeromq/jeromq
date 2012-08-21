
#JeroMQ

Java POJO zeromq (http://zeromq.org) implementation

## Features

* based on zeromq-3
* 99% compatible with zeromq
 * cannot support ipc://
 * cannot support pgm
* not too bad performance compared to zeromq
 * 2M messages (100B) per sec
* exactly same develope experience with zeromq

## Usage

Add it to your Maven project's `pom.xml`:

    <dependency>
      <groupId>org.jeromq</groupId>
      <artifactId>jeromq</artifactId>
      <version>0.1.0-SNAPSHOT</version>
    </dependency>

