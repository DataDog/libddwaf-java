FROM alpine:3.24.3
# JDK 8 retained for build toolchain compatibility (sourceCompatibility = 1.8)
RUN apk add --no-cache bash openjdk8 p11-kit-trust && \
    for module in /usr/lib/libnssckbi.so*; do \
        ln -sf /usr/lib/pkcs11/p11-kit-trust.so "$module"; \
    done && \
    rm -f /usr/lib/libnssckbi-testlib.so*
COPY --from=eclipse-temurin:21-jdk-alpine /opt/java/openjdk /usr/lib/jvm/21
# Delete the emSign ECC G3 root only while the copied JDK still ships it (the Oct-21 JDK update removes it).
RUN KS=/usr/lib/jvm/21/lib/security/cacerts; A="cn_emsign_ecc_root_ca__g3,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]"; \
    if /usr/lib/jvm/21/bin/keytool -list -keystore "$KS" -storepass changeit -alias "$A" >/dev/null 2>&1; then \
      /usr/lib/jvm/21/bin/keytool -delete -keystore "$KS" -storepass changeit -alias "$A"; \
    fi
ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV JAVA_8_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV JAVA_21_HOME=/usr/lib/jvm/21
ENV PATH=$JAVA_HOME/bin:$PATH
