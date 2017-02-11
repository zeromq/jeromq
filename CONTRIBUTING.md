# Contributing to JeroMQ

## Contribution Process

This project uses the [C4 process](http://rfc.zeromq.org/spec:16) for all code changes. "Everyone, without distinction or discrimination, SHALL have an equal right to become a Contributor under the terms of this contract."

If you are interested in contributing to JeroMQ, leave us a comment on [this issue](https://github.com/zeromq/jeromq/issues/4) and let us know that you have read and understand the C4 process.

## General Information

These [slides](http://www.slideshare.net/dongminyu/zeromq-jeromq) (a visualization of the [Internal Architecture of libzmq](http://zeromq.org/whitepapers:architecture) page) may be helpful if you are interesting in contributing to JeroMQ.

## Running the Tests

To run the automated test battery:

```
mvn test
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

