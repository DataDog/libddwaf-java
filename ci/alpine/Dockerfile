FROM alpine:3.11

# Alpine 3.11 is kept on purpose: the musl native binaries built here must run on old musl.
# It gets no fixed ca-certificates package, so remove both compromised eMudhra roots in place.
RUN apk add --no-cache ca-certificates \
 && sed -i -e 's|^mozilla/emSign_ECC_Root_CA_-_G3.crt|!&|' -e 's|^mozilla/Comodo_AAA_Services_root.crt|!&|' /etc/ca-certificates.conf \
 && rm -f /usr/share/ca-certificates/mozilla/emSign_ECC_Root_CA_-_G3.crt /usr/share/ca-certificates/mozilla/Comodo_AAA_Services_root.crt \
 && update-ca-certificates \
 && ln -sf /etc/ssl/certs/ca-certificates.crt /etc/ssl/cert.pem

# openjdk8 pulls in nss, whose builtin roots module still trusts both roots: point it at the
# p11-kit trust module (system bundle) and drop the NSS test copy of the builtins.
RUN apk add --update-cache --repository http://dl-cdn.alpinelinux.org/alpine/edge/testing cmake bash make git g++ wget openjdk8 p11-kit-trust \
 && for module in /usr/lib/libnssckbi.so*; do \
        ln -sf /usr/lib/pkcs11/p11-kit-trust.so "$module"; \
    done \
 && rm -f /usr/lib/libnssckbi-testlib.so*

ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
