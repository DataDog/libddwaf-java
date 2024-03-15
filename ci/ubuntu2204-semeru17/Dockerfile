FROM ubuntu:22.04
COPY --from=eclipse-temurin:8-jdk-jammy /opt/java/openjdk /usr/lib/jvm/8
ENV JAVA_HOME=/usr/lib/jvm/8
ENV JAVA_8_HOME=/usr/lib/jvm/8
COPY --from=ibm-semeru-runtimes:open-17-jdk-jammy /opt/java/openjdk /usr/lib/jvm/semeru17
ENV JAVA_SEMERU17_HOME=/usr/lib/jvm/semeru17
