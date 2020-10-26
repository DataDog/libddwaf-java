# Build instructions

Gradle is not currently used to build the JNI library, with exception to the
debug version used for testing. The reason is that these have to be built for
several environments.

libSqreen.so and libsqreen\_jni.so have to be copied to the `native_libs`'
subdirectories prior to the final release build (built with `gradle build`).

For each environment, a release build of libsqreen is needed:

```sh
cd libsqreen
mkdir Release && cd Release
cmake .. -DCMAKE_BUILD_TYPE=RelWithDebInfo
make -j
DESTDIR=out make install
cp libSqreen.so{,.debug} ../../native_libs/linux_64_glibc/  # example
cd ../..
```

Then the jni lib can be built with:

```sh
mkdir Release && cd Release
cmake .. -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DCMAKE_PREFIX_PATH=$(realpath ../libsqreen/Release/out/usr/local/share/cmake/libsqreen/)
make -j
cp libsqreen_jni.so{,.debug} ../native_libs/linux_64_glibc/  # example
cd ..
```

On Windows:

```sh
cd libsqreen
mkdir Release && cd Release
cmake .. -DCMAKE_INSTALL_PREFIX=out\usr\local -A x64 -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build . --target Sqreen -j --config RelWithDebInfo
cmake --build . --target install -j --config RelWithDebInfo

cd ..\..
mkdir Release && cd Release
cmake .. -DCMAKE_PREFIX_PATH=...\libsqreen\Release\out\usr\local -A x64 -DCMAKE_BUILD_TYPE=RelWithDebInfo

```

