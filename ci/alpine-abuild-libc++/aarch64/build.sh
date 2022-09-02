#!/bin/sh
if uname -m | grep -q 'arm64'; then
    docker build --pull --platform linux/arm64 -f Dockerfile -o build .
else
    echo FAIL: This build script requires arm64 host platform
fi