# Contributing to JeroMQ

## Contribution Process

This project uses the [C4 process](https://rfc.zeromq.org/spec:42/C4/) for all code changes. "Everyone, without distinction or discrimination, SHALL have an equal right to become a Contributor under the terms of this contract."

## General Information

These [slides](http://www.slideshare.net/dongminyu/zeromq-jeromq) (a visualization of the [Internal Architecture of libzmq](http://zeromq.org/whitepapers:architecture) page) may be helpful if you are interesting in contributing to JeroMQ.

## Running the Tests

To run the automated test battery:

```
mvn test
```

To run a single test class (e.g. PubSubTest):

```
mvn -Dtest=PubSubTest test
```

Before submitting a Pull Request, please be sure that the tests pass!

## Running the Examples

To run the [ZGuide examples](https://github.com/zeromq/jeromq/tree/master/src/test/java/guide):

```
mvn exec:java -Dexec.mainClass=guide.hwserver -Dexec.classpathScope=test
```

Or run this [helper script](scripts/run-example):

```
scripts/run-example hwserver
```

## JeroMQ wiki

For miscellaneous information that hasn't yet been pulled into this document, please see the [wiki](https://github.com/zeromq/jeromq/wiki).

