#!/bin/sh

docker buildx build --progress=plain --platform linux/arm64 --build-arg ARCH=aarch64 -f Dockerfile -o build .
