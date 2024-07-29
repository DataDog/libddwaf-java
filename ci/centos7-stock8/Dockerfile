FROM centos:7

# Centos7 is EOL starting from June 30, 2024, we need to use vault mirrors
RUN sed -i 's|mirror.centos.org|vault.centos.org|g' /etc/yum.repos.d/*.repo
RUN sed -i s/^#.*baseurl=http/baseurl=http/g /etc/yum.repos.d/*.repo
RUN sed -i s/^mirrorlist=http/#mirrorlist=http/g /etc/yum.repos.d/*.repo

RUN yum install -y java-1.8.0-openjdk-devel
ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk
ENV JAVA_8_HOME=/usr/lib/jvm/java-1.8.0-openjdk
