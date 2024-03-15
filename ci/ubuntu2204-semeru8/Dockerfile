FROM ubuntu:22.04
COPY --from=eclipse-temurin:8-jdk-jammy /opt/java/openjdk /usr/lib/jvm/8
ENV JAVA_HOME=/usr/lib/jvm/8
ENV JAVA_8_HOME=/usr/lib/jvm/8
COPY --from=ibm-semeru-runtimes:open-8-jdk-jammy /opt/java/openjdk /usr/lib/jvm/semeru8
ENV JAVA_SEMERU8_HOME=/usr/lib/jvm/semeru8
