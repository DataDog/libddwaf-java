FROM alpine:3.24.3
RUN apk add --no-cache bash openjdk8 p11-kit-trust && \
    for module in /usr/lib/libnssckbi.so*; do \
        ln -sf /usr/lib/pkcs11/p11-kit-trust.so "$module"; \
    done
ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV JAVA_8_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
