FROM alpine@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a as build_env
RUN apk --no-cache add alpine-sdk coreutils cmake sudo \
  && adduser -G abuild -g "Alpine Package Builder" -s /bin/ash -D builder \
  && echo "builder ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers \
  && mkdir /packages \
  && chown builder:abuild /packages \
  && mkdir -p /var/cache/apk \
  && ln -s /var/cache/apk /etc/apk/cache
COPY /abuilder /bin/
USER builder
RUN mkdir /home/builder/package && chown builder /home/builder/package
WORKDIR /home/builder/package
ENV REPODEST /packages

FROM build_env AS build_apk
ADD APKBUILD /home/builder/package/
ADD avoid-strtoll_l.patch /home/builder/package/
RUN abuild-keygen -ain
RUN sudo apk update
RUN abuild -r

FROM scratch
COPY --from=build_apk /packages/builder/x86_64/libc++-dev-11.1.0-r1.apk /libc++-dev-11.1.0-r1.x86_64.apk
