FROM quay.io/pypa/manylinux2010_x86_64@sha256:4e92ea10ee57db150ccc9ab264f9cfd889db788adb10b3478f77b1c6c75db684

RUN yum install -y \
	git \
	java-1.8.0-openjdk-devel

RUN cd /usr/local/ && \
	curl -Lf https://github.com/Kitware/CMake/releases/download/v3.15.3/cmake-3.15.3-Linux-x86_64.tar.gz | \
	tar --strip-components=1 -xzf -
