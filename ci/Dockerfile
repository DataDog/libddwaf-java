FROM quay.io/pypa/manylinux2010_x86_64

RUN yum install -y centos-release-scl
RUN yum install -y \
	git \
	java-1.8.0-openjdk-devel

RUN cd /usr/local/ && \
	curl -Lf https://github.com/Kitware/CMake/releases/download/v3.15.3/cmake-3.15.3-Linux-x86_64.tar.gz | \
	tar --strip-components=1 -xzf -
