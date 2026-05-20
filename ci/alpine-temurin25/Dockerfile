FROM alpine:3.19
# JDK 8 retained for build toolchain compatibility (sourceCompatibility = 1.8)
RUN apk add --no-cache bash openjdk8
COPY --from=eclipse-temurin:25-jdk-alpine /opt/java/openjdk /usr/lib/jvm/25
ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV JAVA_8_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV JAVA_25_HOME=/usr/lib/jvm/25
ENV PATH=$JAVA_HOME/bin:$PATH
