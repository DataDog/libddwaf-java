#!/bin/sh

docker buildx build --progress=plain --platform linux/x86_64 --build-arg ARCH=x86_64 -f Dockerfile -o build .
