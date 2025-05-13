FROM centos:6
RUN set -eux; \
    curl https://www.getpagespeed.com/files/centos6-eol.repo --output /etc/yum.repos.d/CentOS-Base.repo; \
    yum install -y java-1.8.0-openjdk-devel
ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk.x86_64
ENV JAVA_8_HOME=/usr/lib/jvm/java-1.8.0-openjdk.x86_64
