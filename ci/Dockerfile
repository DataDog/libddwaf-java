FROM quay.io/pypa/manylinux2010_x86_64

RUN yum install -y centos-release-scl
RUN yum install -y \
	git \
	pkgconfig \
	libcurl-devel \
	bzip2-devel \
	shared-mime-info \
	zlib-devel \
	expat-devel \
	xz-devel \
	python27-python-sphinx \
	java-1.8.0-openjdk-devel

RUN mkdir -p /tmp/librhash && \
	cd /tmp/librhash && \
	{ curl -L -f https://github.com/rhash/RHash/archive/657825709a7711538d9a19120a990ba5120780fb.tar.gz | \
		tar --strip-components=1 -xzf -; } && \
	./configure --enable-lib-static && make -j && make install && \
	rm -rf /tmp/librhash

RUN mkdir -p /tmp/cmake && \
	cd /tmp/cmake && \
	{ curl -L -f https://www.cmake.org/files/v3.15/cmake-3.15.1.tar.gz | \
		tar --strip-components=1 -xzf -; } && \
	LD_LIBRARY_PATH=/opt/rh/python27/root/usr/lib64 \
	LDFLAGS="-static-libstdc++" \
	./bootstrap --prefix=/usr \
			--mandir=/share/man \
			--docdir=/share/doc/cmake \
			--sphinx-man \
			--system-libs \
			--no-system-libarchive \
			--no-system-jsoncpp \
			--no-system-libuv \
			--parallel=4 \
			--sphinx-build=/opt/rh/python27/root/usr/bin/sphinx-build \
			--verbose && \
	LD_LIBRARY_PATH=/opt/rh/python27/root/usr/lib64 make -j VERBOSE=1 && \
	LD_LIBRARY_PATH=/opt/rh/python27/root/usr/lib64 make install && \
	rm -rf /tmp/cmake
