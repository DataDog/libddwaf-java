FROM alpine@sha256:8914eb54f968791faf6a8638949e480fef81e697984fba772b3976835194c6d4 AS build_env
RUN apk --no-cache add alpine-sdk coreutils cmake sudo bash samurai python3 linux-headers compiler-rt \
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
RUN sudo rm /bin/ash && sudo ln -s /bin/bash /bin/ash
RUN abuild -r
RUN find /packages

FROM scratch
ARG ARCH
COPY --from=build_apk /packages/builder/$ARCH/libc++-15.0.6-r1.apk /libc++-15.0.6-r1.$ARCH.apk
