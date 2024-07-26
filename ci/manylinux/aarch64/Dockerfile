FROM quay.io/pypa/manylinux2014_aarch64:2024-02-04-ea37246@sha256:d4cb8ad0418d7dfd25012720e3021c2d07e3bb839d395d585668e1a498e5b6ed

# Centos7 is EOL starting from June 30, 2024, we need to use vault mirrors
RUN sed -i 's|mirror.centos.org/centos|vault.centos.org/altarch|g' /etc/yum.repos.d/*.repo
RUN sed -i 's|mirror.centos.org/altarch|vault.centos.org/altarch|g' /etc/yum.repos.d/*.repo
RUN sed -i s/^#.*baseurl=http/baseurl=http/g /etc/yum.repos.d/*.repo
RUN sed -i s/^mirrorlist=http/#mirrorlist=http/g /etc/yum.repos.d/*.repo

RUN yum install -y \
	git \
	java-1.8.0-openjdk-devel

RUN cd /usr/local/ && \
	curl -Lf https://github.com/Kitware/CMake/releases/download/v3.24.0/cmake-3.24.0-linux-aarch64.tar.gz | \
	tar --strip-components=1 -xzf -
