#!/bin/bash -e

PROJ_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")"/.. >/dev/null 2>&1 && pwd)"

set -x
set -o pipefail

function main {
  local readonly arg=$1

  if [[ $arg = 'build_docker_image' ]]; then
    build_docker_image
  elif [[ $arg = 'build_powerwaf' ]]; then
    build_powerwaf
  elif [[ $arg = 'build_powerwaf_docker' ]]; then
    build_powerwaf_docker
  elif [[ $arg = 'test_java' ]]; then
    test_java
  elif [[ $arg = 'test_java_docker' ]]; then
    test_java_docker
  else
    echo "Invalid argument: $arg" >&2
    exit 1
  fi
}

# function must be used inline
function docker_prepare {
  # save original stdout in fd 3 then dup stderr in stdout
  # we're saving stdout for returning the modified dir contents
  exec 3>&1
  exec >&2

  rm -rf /tmp/AgentJavaNative
  mkdir -p /tmp/AgentJavaNative
  cd /tmp/AgentJavaNative
  tar -C /tmp/AgentJavaNative -xzf -
}

function docker_epilogue {
  local readonly dir_to_copy=$1
  # restore original stdout
  exec >&3
  cd /tmp/AgentJavaNative
  chown -R "$OWNER" "$dir_to_copy"
  tar -czf - "$dir_to_copy"
}

function build_docker_image {
  cd "$PROJ_DIR/ci/manylinux"
  docker build -t manylinux-cmake .
}

function run_in_docker {
  local readonly cmd="$*"
  local new_cmd
  cd "$PROJ_DIR"
  new_cmd="exec 3>&1; exec >&2; mkdir -p /tmp/AgentJavaNative && tar -C /tmp/AgentJavaNative -xzf - && $cmd"
  tar -czf - . | docker run -i --init --rm -e OWNER=$(id -u):$(id -g) manylinux-cmake \
    bash -e -c "$new_cmd" | tar xzf -
}

function run_in_docker_no_copy {
  local readonly cmd="$*"
  cd "$PROJ_DIR"
  new_cmd="mkdir -p /tmp/AgentJavaNative && tar -C /tmp/AgentJavaNative -xzf - && $cmd"
  tar -czf - . | docker run -i --init --rm manylinux-cmake \
    bash -e -c "$new_cmd"
}

function build_powerwaf {
  cd "$PROJ_DIR"
  rm -rf PowerWAF/Debug
  run_in_docker /tmp/AgentJavaNative/ci/jenkins_run.sh build_powerwaf_docker
}
function build_powerwaf_docker {
  cd "/tmp/AgentJavaNative/PowerWAF"
  rm -rf Debug
  mkdir Debug
  cd Debug
  cmake .. -DCMAKE_BUILD_TYPE=Debug
  make -j Sqreen VERBOSE=1
  DESTDIR=out make install
  docker_epilogue PowerWAF/Debug
}

function test_java {
  run_in_docker_no_copy /tmp/AgentJavaNative/ci/jenkins_run.sh test_java_docker
}
function test_java_docker {
  cd "/tmp/AgentJavaNative"
  ./gradlew --info check jacocoTestReport || {
    find /tmp/AgentJavaNative/ -name '*.log' -exec cat '{}' \;
    return 1;
  }
  CODECOV_TOKEN='19632923-518b-4125-8b3a-d400365a1d83' bash \
    <(curl -fL --connect-timeout 10 -m 20 --retry 3 https://codecov.io/bash)
}

main "$@"
