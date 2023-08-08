FROM quay.io/pypa/manylinux2014_x86_64@sha256:6b61fd1ad2cb7c0c409a037663b7289201ed2570db7076842f3a34459398c8ab

RUN yum install -y \
	git \
	java-1.8.0-openjdk-devel

RUN cd /usr/local/ && \
	curl -Lf https://github.com/Kitware/CMake/releases/download/v3.24.0/cmake-3.24.0-linux-x86_64.tar.gz | \
	tar --strip-components=1 -xzf -
