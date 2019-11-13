#!/bin/bash
set -ev

ANDROID_ROOT=src/android/Jeromq/app/src/

rm -fr $ANDROID_ROOT/main/java/{org,zmq}
rm -fr $ANDROID_ROOT/androidTest/java/{org,zmq}

cp -R src/main/java/{org,zmq} $ANDROID_ROOT/main/java
cp -R src/test/java/{org,zmq} $ANDROID_ROOT/androidTest/java

cp $ANDROID_ROOT/TemporaryFolderFinder.java $ANDROID_ROOT/androidTest/java/org/zeromq

grep -rl zmq.util.AndroidProblematic $ANDROID_ROOT/androidTest/java | xargs -I @@ bash -c '{\
 sed -i 's/zmq.util.AndroidProblematic/org.junit.Ignore/g' @@ ;\
 sed -i 's/@AndroidProblematic/@Ignore/g' @@ ;\
}'

