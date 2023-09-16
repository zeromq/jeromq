# How to Release JeroMQ on Central

## Prerequisites

The minimum supported version of Java JeroMQ uses is Version 8. So
when publishing builds to the Maven Central be sure to use the latest
stable release of (open)JDK 8.


## OSS Sonatype

In order to publish builds on [OSS Sonatype](https://oss.sonatype.org), you must
first have permission to upload on behalf of the `org.zeromq` groupId.

You will need to create a JIRA issue on Open Source Project Repository Hosting
(OSSRH) project requesting access. Here is an
[example](https://issues.sonatype.org/browse/OSSRH-46351) of such a request.

A current maintainer must approve the request before you gain
permission.

## Sonatype Configuration

You will need to add the following configuration into the
`~/.m2/settings.xml` file.

```
<servers>
  <server>
    <id>ossrh</id>
    <username>...</username>
    <password>...</password>
  </server>
</servers>
```

The username and password are the same as your OSS Sonatype
credentials.

## Release a Snapshot to Central

You are not required to sign SNAPSHOT builds. Issue the following
command to deploy a SNAPSHOT.

```
$ mvn clean deploy
```

## Release Commands

```
$ mvn release:clean release:prepare
```

You will be asked a series of questions regarding version numbers. It
is safe to hit `<enter>` 3 times.

Example output:

```
[INFO] Checking dependencies and plugins for snapshots ...
What is the release version for "JeroMQ"? (org.zeromq:jeromq) 0.5.0: :
What is SCM release tag or label for "JeroMQ"? (org.zeromq:jeromq) v0.5.0: :
What is the new development version for "JeroMQ"? (org.zeromq:jeromq) 0.5.1-SNAPSHOT: :
```

The Maven Release Plugin will take care of bumping version numbers and
tagging the release build. It will also push those changes to your
chosen SCM.

When you commit this changes, CircleCI will pick them and publish in the stagging
repositories at [OSS Sonatype](https://oss.sonatype.org), with a valid GPG signature.
There is a list of known keys that have been used to sign tagged JeroMQ releases [here](public-keys.md).

To publish it to the Central Repository, one can follow the procedure at 
[Publish my artifact](https://central.sonatype.org/publish/release/).


# Making an Announcement on the ZeroMQ Mailing list when it has been successfully synced.

TODO: more info?
