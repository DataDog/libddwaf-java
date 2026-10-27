FROM alpine:3.24.3
# JDK 8 retained for build toolchain compatibility (sourceCompatibility = 1.8)
RUN apk add --no-cache bash openjdk8 p11-kit-trust && \
    for module in /usr/lib/libnssckbi.so*; do \
        ln -sf /usr/lib/pkcs11/p11-kit-trust.so "$module"; \
    done && \
    rm -f /usr/lib/libnssckbi-testlib.so*
COPY --from=eclipse-temurin:21-jdk-alpine /opt/java/openjdk /usr/lib/jvm/21
# Delete both compromised roots from the copied JDK cacerts by subject (not alias), then fail the
# build if either is still there. The current eclipse-temurin:21-jdk-alpine tag still ships emSign
# ECC G3; the Oct-21 Temurin release drops it, and then this step deletes nothing.
RUN set -e; KT=/usr/lib/jvm/21/bin/keytool; KS=/usr/lib/jvm/21/lib/security/cacerts; \
    BAD='^Owner: CN=(emSign ECC Root CA - G3|AAA Certificate Services),'; \
    LIST=$("$KT" -list -v -keystore "$KS" -storepass changeit); \
    printf '%s\n' "$LIST" | awk -v bad="$BAD" '/^Alias name: /{a=substr($0,13)} $0 ~ bad {print a}' | \
    while IFS= read -r alias; do "$KT" -delete -keystore "$KS" -storepass changeit -alias "$alias"; done; \
    LIST=$("$KT" -list -v -keystore "$KS" -storepass changeit); \
    if printf '%s\n' "$LIST" | grep -Eq "$BAD"; then echo "compromised root still in $KS" >&2; exit 1; fi
ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV JAVA_8_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV JAVA_21_HOME=/usr/lib/jvm/21
ENV PATH=$JAVA_HOME/bin:$PATH
