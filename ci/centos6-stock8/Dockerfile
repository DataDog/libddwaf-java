FROM centos:6
RUN set -eux; \
    curl https://www.getpagespeed.com/files/centos6-eol.repo --output /etc/yum.repos.d/CentOS-Base.repo; \
    yum install -y java-1.8.0-openjdk-devel
ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk.x86_64
ENV JAVA_8_HOME=/usr/lib/jvm/java-1.8.0-openjdk.x86_64
# CentOS 6 reached EOL in 2020; its JDK cacerts no longer include the CA chains
# used by modern HTTPS endpoints (Gradle distribution servers, Maven Central).
# Replace the stale cacerts with those from Eclipse Temurin 8, which are current.
COPY --from=eclipse-temurin:8-jdk /opt/java/openjdk/jre/lib/security/cacerts /tmp/temurin-cacerts
RUN keytool -importkeystore \
      -srckeystore  /tmp/temurin-cacerts \
      -destkeystore "$JAVA_HOME/jre/lib/security/cacerts" \
      -srcstorepass  changeit \
      -deststorepass changeit \
      -noprompt 2>/dev/null && \
    rm /tmp/temurin-cacerts
