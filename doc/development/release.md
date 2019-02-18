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
$ mvn deploy
```

## Release Commands

```
$ mvn release:prepare
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

To perform a release, issue the following command.

```
$ mvn release:perform
```

This will upload the artifacts to OSS Sonatype and will require you to sign the
build. There is a list of known keys that have been used to sign tagged JeroMQ
releases [here](public-keys.md).

## Build Publishing

Log into the OSS Nexus Repository located at: [OSS
NEXUS](https://oss.sonatype.org/).

Click `Staging Repositories` link located on the left
navigation.

 (show picture)

Locate the latest entry with the profile `org.zeromq`. Ensure it's
from JeroMQ. In order to release the project to the Maven Central, you
first must close the staging repository.

(show picture)

It will take a minute to reflect that it has been closed. Once you can
see the status change, you may now queue the build to be mirrored to
the Maven Central.

Making an Announcement on the ZeroMQ Mailing list when it has been
successfully synced.
