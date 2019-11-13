#!/bin/bash
set -ev

ANDROID_ROOT=src/android/Jeromq
ANDROID_SRC=$ANDROID_ROOT/app/src

mkdir -p $ANDROID_SRC/{main,androidTest}/java
rm -fr $ANDROID_SRC/{main,androidTest}/java/{org,zmq}

cp -R src/main/java/{org,zmq} $ANDROID_SRC/main/java
cp -R src/test/java/{org,zmq} $ANDROID_SRC/androidTest/java

cp $ANDROID_SRC/TemporaryFolderFinder.java $ANDROID_SRC/androidTest/java/org/zeromq

grep -rl zmq.util.AndroidIgnore $ANDROID_SRC/androidTest/java | xargs -I @@ bash -c '{\
 sed -i 's/zmq.util.AndroidIgnore/org.junit.Ignore/g' @@ ;\
 sed -i 's/@AndroidIgnore/@Ignore/g' @@ ;\
}'

