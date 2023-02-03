FROM alpine@sha256:8914eb54f968791faf6a8638949e480fef81e697984fba772b3976835194c6d4 as build_env
RUN apk --no-cache add clang compiler-rt compiler-rt wget cmake make binutils musl-dev git patchelf
RUN mkdir /tmp/apk && cd /tmp/apk && \
	echo '73d064712462cc4728bb4c617d84dbea10af43d7384f7f08a64bafd8255e49fb0f80dd7c7ad57f502c483c995ff29919e384c8ec78daf136c29faf13aa2a30ee  libc++-15.0.6-r1.x86_64.apk' > CHECKSUM && \
	wget -q https://sqreen-ci-java.s3.amazonaws.com/apk/libc%2B%2B-15.0.6-r1.x86_64.apk -O libc++-15.0.6-r1.x86_64.apk && \
	sha512sum -c CHECKSUM && \
	apk add --allow-untrusted libc++-15.0.6-r1.x86_64.apk && \
	rm -rf /tmp/apk

FROM build_env AS libddwaf_build
COPY .. /AgentJavaNative
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
	-DLIBDDWAF_SHARED_LINKER_FLAGS='-v -nodefaultlibs -Wl,-Bstatic -lc++ /usr/lib/clang/15.0.7/lib/linux/libclang_rt.builtins-x86_64.a -lunwind -Wl,-Bdynamic -lc /usr/lib/clang/15.0.7/lib/linux/libclang_rt.builtins-x86_64.a'
# Link line will be:
# "/usr/bin/ld" -z now -z relro --hash-style=gnu --eh-frame-hdr -m elf_x86_64 -shared -o libddwaf.so /usr/bin/../lib/crti.o /usr/lib/clang/15.0.6/lib/linux/clang_rt.crtbegin-x86_64.o -L/usr/bin/../lib -L/lib -L/usr/lib -soname libddwaf.so CMakeFiles/libddwaf_objects.dir/src/Clock.cpp.o CMakeFiles/libddwaf_objects.dir/src/parameter.cpp.o CMakeFiles/libddwaf_objects.dir/src/PowerWAF.cpp.o CMakeFiles/libddwaf_objects.dir/src/PowerWAFInterface.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWAdditive.cpp.o CMakeFiles/libddwaf_objects.dir/src/object.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWManifest.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWProcessor.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWRet.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWRetriever.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWRule.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWRuleManager.cpp.o CMakeFiles/libddwaf_objects.dir/src/PWTransformer.cpp.o CMakeFiles/libddwaf_objects.dir/src/radixlib.c.o CMakeFiles/libddwaf_objects.dir/src/utils.cpp.o CMakeFiles/libddwaf_objects.dir/src/log.cpp.o CMakeFiles/libddwaf_objects.dir/src/rule_processor/IPWRuleProcessor.cpp.o CMakeFiles/libddwaf_objects.dir/src/rule_processor/perf_match.cpp.o CMakeFiles/libddwaf_objects.dir/src/rule_processor/re2.cpp.o -lrt -ldl -lm --no-undefined -version-script=/libddwaf/libddwaf.version --build-id=0xa75fbdf483c3016d8ae0acccea972956fe1121fe third_party/lib/libre2.a third_party/liblib_ac.a third_party/liblib_injection.a -Bstatic -lc++ /usr/lib/clang/15.0.6/lib/linux/libclang_rt.builtins-x86_64.a -lunwind -Bdynamic -lc /usr/lib/clang/15.0.6/lib/linux/libclang_rt.builtins-x86_64.a -lpthread /usr/lib/clang/15.0.6/lib/linux/clang_rt.crtend-x86_64.o /usr/bin/../lib/crtn.o
# The apparent dynamic linking to librt, libm, libpthread doesn't happen
# because in musl these are all empty libraries. The symbols live in libc, and
# will be imported. When linking to the shared library in a glibc system,
# -lm, -ldl, -lpthread need to be provided though
RUN cd /build && VERBOSE=1 make -j
RUN patchelf --remove-needed libc.musl-x86_64.so.1 /build/libddwaf.so
RUN cd /build && make package && mv libddwaf-*.tar.gz libddwaf-x86_64.tar.gz

FROM scratch
COPY --from=libddwaf_build /build/libddwaf-x86_64.tar.gz /
