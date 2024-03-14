FROM ubuntu:22.04
COPY --from=eclipse-temurin:8-jdk-jammy /opt/java/openjdk /usr/lib/jvm/8
ENV JAVA_HOME=/usr/lib/jvm/8
ENV JAVA_8_HOME=/usr/lib/jvm/8
COPY --from=eclipse-temurin:11-jdk-jammy /opt/java/openjdk /usr/lib/jvm/11
ENV JAVA_11_HOME=/usr/lib/jvm/11
