cmake_minimum_required(VERSION 3.14.0)
include(FetchContent)

set(ZLIB_VERSION v1.3)
FetchContent_Declare(
  zlib
  GIT_REPOSITORY https://github.com/madler/zlib.git
  GIT_TAG        ${ZLIB_VERSION}
)
FetchContent_MakeAvailable(zlib)
if(NOT(MSVC))
    set_property(TARGET zlibstatic PROPERTY POSITION_INDEPENDENT_CODE ON)
endif()

target_compile_definitions(zlibstatic PUBLIC ZLIB_CONST=1)
target_include_directories(zlibstatic INTERFACE ${zlib_SOURCE_DIR} ${zlib_BINARY_DIR})

set(LIBDDWAF_VERSION 1.16.0)
set(LIBDDWAF_VERSION ${LIBDDWAF_VERSION} PARENT_SCOPE)
if(CMAKE_SYSTEM_NAME STREQUAL Linux)
    if(CMAKE_SYSTEM_PROCESSOR STREQUAL x86_64)
        set(LIBDDWAF_VARIANT x86_64-linux-musl)
        # i386?
    elseif (CMAKE_SYSTEM_PROCESSOR STREQUAL i386)
        set(LIBDDWAF_VARIANT i386-linux-musl)
    elseif (CMAKE_SYSTEM_PROCESSOR STREQUAL aarch64)
        set(LIBDDWAF_VARIANT aarch64-linux-musl)
    else()
        message(FATAL_ERROR "Unsupported architecture: ${CMAKE_SYSTEM_PROCESSOR}")
    endif()
elseif (CMAKE_SYSTEM_NAME STREQUAL Darwin)
    if (CMAKE_SYSTEM_PROCESSOR STREQUAL arm64 OR CMAKE_OSX_ARCHITECTURES STREQUAL arm64)
        set(LIBDDWAF_VARIANT darwin-arm64)
    elseif (CMAKE_SYSTEM_PROCESSOR STREQUAL x86_64)
        set(LIBDDWAF_VARIANT darwin-x86_64)
    else()
        message(FATAL_ERROR "Unsupported architecture: ${CMAKE_SYSTEM_PROCESSOR}")
    endif()
elseif (CMAKE_SYSTEM_NAME STREQUAL Windows)
    if(CMAKE_SYSTEM_PROCESSOR STREQUAL AMD64)
        set(LIBDDWAF_VARIANT windows-x64)
    else()
        message(FATAL_ERROR "Unsupported architecture: ${CMAKE_SYSTEM_PROCESSOR}")
    endif()
else()
    message(FATAL_ERROR "Unsupported platform: ${CMAKE_SYSTEM_NAME}")
endif()
FetchContent_Declare(
        libddwaf
        # TODO: gen proper url
        URL https://github.com/DataDog/libddwaf/releases/download/${LIBDDWAF_VERSION}/libddwaf-${LIBDDWAF_VERSION}-${LIBDDWAF_VARIANT}.tar.gz
)
FetchContent_MakeAvailable(libddwaf)
set(libddwaf_DIR ${libddwaf_SOURCE_DIR}/share/cmake/libddwaf PARENT_SCOPE)

# vim: set et:
