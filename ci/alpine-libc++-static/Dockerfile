FROM alpine@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a as build_env
RUN apk --no-cache add clang compiler-rt compiler-rt-static wget cmake make binutils musl-dev git patchelf
RUN mkdir /tmp/apk && \
	echo '509615086ee6d7fee24c71ed9e07bf5e8f9f82348266966dd9904974f58629cb0166e0cc72198e6e019c1a1c2498499093bc8d1b22ae6b69ef97a59c83264eb4  libc++-dev-11.1.0-r1.apk' > CHECKSUM && \
	wget -q https://sqreen-ci-java.s3.amazonaws.com/apk/libc%2B%2B-dev-11.1.0-r1.apk && \
	sha512sum -c CHECKSUM && \
	apk add --allow-untrusted libc++-dev-11.1.0-r1.apk && \
	rm -rf /tmp/apk

FROM build_env AS libddwaf_build
COPY . /AgentJavaNative
# NOTES:
#    CMAKE_CXX_FLAGS (except -stdlib=libc++) and CMAKE_C_FLAGS are only used to
#    pass the cmake compiler sanity checks. They have no effect otherwise due to
#    the usage of -nodefaultlibs
#    -static-libgcc is used to force libunwind to be compiled statically
#    (-l:libwind.a instead of -lunwind), it has nothing to do with libgcc.
#    Again, this is only needed for the cmake sanity checks.
RUN mkdir /build && cd /build && cmake /AgentJavaNative/libddwaf \
	-DCMAKE_BUILD_TYPE=RelWithDebInfo \
	-DCMAKE_INSTALL_PREFIX=/usr \
	-DCMAKE_C_COMPILER=clang \
	-DCMAKE_CXX_COMPILER=clang++ \
	-DCMAKE_CXX_FLAGS="-stdlib=libc++ -Qunused-arguments -rtlib=compiler-rt -unwindlib=libunwind -static-libgcc" \
	-DCMAKE_C_FLAGS="-Qunused-arguments -rtlib=compiler-rt -unwindlib=libunwind -static-libgcc" \
	-DLIBDDWAF_BUILD_STATIC=OFF \
	-DLIBDDWAF_SHARED_LINKER_FLAGS='-v -nodefaultlibs -Wl,-Bstatic -lc++ /usr/lib/clang/11.1.0/lib/linux/libclang_rt.builtins-x86_64.a -lunwind -Wl,-Bdynamic -lc /usr/lib/clang/11.1.0/lib/linux/libclang_rt.builtins-x86_64.a'
# Link line will be:
# "/usr/bin/ld" -z now -z relro --hash-style=gnu --eh-frame-hdr -m elf_x86_64 -shared -o libddwaf.so /usr/bin/../lib/crti.o /usr/lib/clang/11.1.0/lib/linux/clang_rt.crtbegin-x86_64.o -L/usr/bin/../lib -L/lib -L/usr/lib -soname libddwaf.so CMakeFiles/libddwaf_objects.dir/src/Clock.cpp.o CMakeFiles/libddwaf_objects.dir/src/parameter.cpp.o CMakeFiles/libddwaf_objects.dir/src/PowerWAF.cpp.o CMakeFiles/libddwaf_objects.dir/src/PowerWAFInterface.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWAdditive.cpp.o CMakeFiles/libddwaf_objects.dir/src/object.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWManifest.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWProcessor.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWRet.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWRetriever.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWRule.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWRuleManager.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWTransformer.cpp.o CMakeFiles/libddwaf_objects.dir/src/radixlib.c.o CMakeFiles/libddwaf_objects.dir/src/utils.cpp.o CMakeFiles/libddwaf_objects.dir/src/log.cpp.o CMakeFiles/libddwaf_objects.dir/src/rule_processor/IPWRuleProcessor.cpp.o CMakeFiles/libddwaf_objects.dir/src/rule_processor/perf_match.cpp.o CMakeFiles/libddwaf_objects.dir/src/rule_processor/re2.cpp.o -lrt -ldl -lm --no-undefined -version-script=/libddwaf/libddwaf.version --build-id=0xa75fbdf483c3016d8ae0acccea972956fe1121fe third_party/lib/libre2.a third_party/liblib_ac.a third_party/liblib_injection.a -Bstatic -lc++ /usr/lib/clang/11.1.0/lib/linux/libclang_rt.builtins-x86_64.a -lunwind -Bdynamic -lc /usr/lib/clang/11.1.0/lib/linux/libclang_rt.builtins-x86_64.a -lpthread /usr/lib/clang/11.1.0/lib/linux/clang_rt.crtend-x86_64.o /usr/bin/../lib/crtn.o
# The apparent dynamic linking to librt, libm, libpthread doesn't happen
# because in musl these are all empty libraries. The symbols live in libc, and
# will be imported. When linking to the shared library in a glibc system,
# -lm, -ldl, -lpthread need to be provided though
RUN cd /build && VERBOSE=1 make -j
RUN patchelf --remove-needed libc.musl-x86_64.so.1 /build/libddwaf.so
RUN cd /build && make package && mv libddwaf-*.tar.gz libddwaf.tar.gz

FROM scratch
COPY --from=libddwaf_build /build/libddwaf.tar.gz /
