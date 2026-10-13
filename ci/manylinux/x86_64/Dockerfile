FROM quay.io/pypa/manylinux2010_x86_64@sha256:3b5eb5ab9bc73b93740ae4eda9962078951cd3bb7efe837b770360e78c7366fb

RUN yum install -y \
	git \
	java-1.8.0-openjdk-devel

RUN cd /usr/local/ && \
	curl -Lf https://github.com/Kitware/CMake/releases/download/v3.24.0/cmake-3.24.0-linux-x86_64.tar.gz | \
	tar --strip-components=1 -xzf -
