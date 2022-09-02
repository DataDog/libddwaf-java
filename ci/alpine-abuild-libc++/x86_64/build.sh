#!/bin/sh
if uname -m | grep -q 'x86_64\|arm64'; then
    docker build --pull --platform linux/amd64 -f Dockerfile -o build .
else
    echo FAIL: This build script requires x86_64 or arm64 host platform
fi