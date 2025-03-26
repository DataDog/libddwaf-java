# libddwaf-java

## Overview

This project provides Java bindings for [libddwaf](https://github.com/DataDog/libddwaf) through JNI.
Currently supported runtimes are Linux (GNU and musl), Windows amd64 and macOS x86\_64 and arm64.

The project has 3 components:

* libddwaf, the core WAF library.
* libsqreen\_jni, the JNI bindings.
* libsqreen.jar, the Java component, which embeds libddwaf and JNI bindings builds for all supported platforms.

## Build and test

### Prerequisites

* cmake 3.15 or later
* JDK 8 or later

### Quick start

Just run:

```sh
./gradlew check
```

This will build debug versions of libddwaf, the JNI bindings, the final jar,
and run tests and other checks

### Release mode build

If you want to test as closely as possible to a release, you have to place
all built binaries in the `native_libs` directory. Then you can run:

```sh
./gradlew check -PuseReleaseBinaries
```

This will skip the build of the native libraries and use the ones it finds in
`native_libs`. For more on the release build process, you can go ahead and check
the GitHub Actions workflow file at `.github/workflows/actions.yml`.

### Advanced

#### Custom libddwaf build

If you need to build the JNI bindings against a custom build of libddwaf, you can use
the `libddwafDir` property to specify the path to the libddwaf build directory:

```sh
./gradlew buildNativeLibDebug -PlibddwafDir=/path/to/dir/of/libddwaf-config-debug.cmake
```

This will avoid building libddwaf and use the one found in the specified directory.

#### ASAN

You can run tests with ASAN with the `withASAN` property:

```sh
./gradlew check -PwithASAN
```

#### Local testing

To deploy the current bindings to your local Maven repository, use the following command:

```sh
./gradlew publishToMavenLocal
```

This will publish the artifact with the `-SNAPSHOT` suffix, allowing for convenient local testing.
