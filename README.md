# Introduction

This project provides Java bindings for [libddwaf][libddwaf_repos] through JNI.
Currently supported runtimes are Linux (GNU and musl) and Windows amd64.

# Build instructions

Gradle is used to build the Java component, and CMake to build the native
components. Three components need to be built separately:

* libddwaf needs to be built and installed.
* Then the JNI binding (named libsqreen\_jni, for historical reasons).
* Finally the jar can be built with Gradle.

A jar for release purposes needs to have both the libddwaf and libsqreen
binaries for all the supported runtime environments. These are included inside
the JAR. For Linux, a shared library build of libddwaf (libddwaf.so) is included
in JAR; this DSO is used in both musl and glibc systems. On Windows, there is
only one binary, which is linked against libddwaf built as a static library.

For testing purposes, Gradle can invoke a debug build of libsqreen\_jni. By
default, libddwaf CMake config files are searched in
`libddwaf/Debug/out/usr/local/share/cmake/libddwaf`.

The binaries for inclusion in the final release jar have to be copied into the
`native_libs` directory. Then the jar can be built with `gradle build`.

For development purposes, a debug build of libddwaf can be obtained with:

```sh
cd libddwaf
mkdir Debug && cd Debug
cmake .. -DCMAKE_BUILD_TYPE=Debug
make -j
DESTDIR=out make install
cd ../..
```

Then the jni lib can be built with `./gradlew buildNativeLibDebug --rerun-tasks`.

To build native libraries, recommended use `--rerun-tasks` option to enforce rebuild task, because of gradle can skip building tasks due caches and lead to unexpected results. 

Tests are run with `./gradlew check`. This implicitly invokes
`buildNativeLibDebug` if needed.

On Windows libddwaf can be built with:

```sh
cd libddwaf
mkdir Debug && cd Debug
cmake .. -DCMAKE_INSTALL_PREFIX=out\usr\local -A x64 -DCMAKE_BUILD_TYPE=Debug
cmake --build . --target install -j --config Debug

```

  [libddwaf_repos]: https://github.com/DataDog/libddwaf
