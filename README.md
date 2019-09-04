# Build instructions

Gradle is not currently used to build the JNI library, with exception to the
debug version used for testing. The reason is that these have to be built for
several environments.

libSqreen.so and powerwaf\_jni.so have to be copied to the `native_libs`'
subdirectories prior to the final release build (built with `gradle build`).

For each environment, a release build of PowerWAF is needed:

```sh
cd PowerWAF
mkdir Release && cd Release
cmake .. -DCMAKE_BUILD_TYPE=Release
make -j
DESTDIR=out make install
cp libSqreen.so ../../native_libs/linux_64/  # example
cd ../..
```

Then the jni lib can be built with:

```sh
mkdir Release && cd Release
cmake .. -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DCMAKE_PREFIX_PATH=$(realpath ../PowerWAF/Release/out/usr/local/share/cmake/powerwaf/)
make -j
cp libpowerwaf_jni.so ../native_libs/linux_64/  # example
cd ..
```
