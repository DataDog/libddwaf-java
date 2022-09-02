FROM quay.io/pypa/manylinux2010_aarch64@sha256:4fa870a37a1d488246847d3d0a1de9f65ce086d8ccf78b2c40c697fa27fcfc9c

RUN yum install -y \
	git \
	java-1.8.0-openjdk-devel

RUN cd /usr/local/ && \
	curl -Lf https://github.com/Kitware/CMake/releases/download/v3.24.0/cmake-3.24.0-linux-aarch64.tar.gz | \
	tar --strip-components=1 -xzf -
