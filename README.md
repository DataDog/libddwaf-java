# libddwaf-java

## Overview

This project provides Java bindings for [libddwaf](https://github.com/DataDog/libddwaf) through JNI. There are 3 components:

* libddwaf itself
* libsqreen\_jni, the JNI binding (native side), uses cmake
* a jar with the Java API, uses gradle

## Build

### Simple build

To build the JNI bindings, you can run:

```sh
# Build JNI bindings for local testing, uses cmake under the hood
./gradlew buildNativeLib --rerun-tasks

# Or, in recent macOS you might need to specify the architecture (x86_64, arm64):
./gradlew buildNativeLib --rerun-tasks -PmacArch=arm64
```

Then all tests can be run as follows:

```sh
./gradlew check
```

### Build with custom libddwaf

By default, cmake will download the required libddwaf build for the current sysem and use it. In some cases, you may
want to use a custom libddwaf build. This is required to use an unreleased version of libddwaf or to use custom build
arguments (e.g. ASAN).

So, here's an example building a debug version of libddwaf from master:

```sh
git clone git@github.com:DataDog/libddwaf.git
cd libddwaf
mkdir Debug && cd Debug
cmake .. -DCMAKE_BUILD_TYPE=Debug
make -j
DESTDIR=out make install
cd ../..
```

Then you can build the JNI bindings with the following command:

```sh
./gradlew buildNativeLib -PlibddwafPath=libddwaf/Debug/out/usr/local --rerun-tasks
```

## Release build

A jar for release purposes needs to have both the libddwaf and libsqreen
binaries for all the supported runtime environments. These are included inside
the JAR. For Linux, a shared library build of libddwaf (libddwaf.so) is included
in JAR; this DSO is used in both musl and glibc systems. On Windows, there is
only one binary, which is linked against libddwaf built as a static library.

The binaries for inclusion in the final release jar have to be copied into the
`build/native_libs` directory. Then the jar can be built with `gradle build`.

To build native libraries, recommended use `--rerun-tasks` option to enforce rebuild task, because of gradle can skip building tasks due caches and lead to unexpected results.

Tests in reease mode are run with `./gradlew check -Prelease`.

