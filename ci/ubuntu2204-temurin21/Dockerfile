FROM ubuntu:22.04
COPY --from=eclipse-temurin:8-jdk-jammy /opt/java/openjdk /usr/lib/jvm/8
ENV JAVA_HOME=/usr/lib/jvm/8
ENV JAVA_8_HOME=/usr/lib/jvm/8
COPY --from=eclipse-temurin:21-jdk-jammy /opt/java/openjdk /usr/lib/jvm/21
ENV JAVA_21_HOME=/usr/lib/jvm/21
