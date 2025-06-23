FROM alpine:3.11

RUN apk add --update-cache --repository http://dl-cdn.alpinelinux.org/alpine/edge/testing cmake bash make git g++ wget openjdk8

ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
