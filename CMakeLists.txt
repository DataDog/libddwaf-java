project(powerwaf_java)
cmake_minimum_required(VERSION 2.8.8)

include(FindJNI)
if (JNI_FOUND)
    message (STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
    message (STATUS "JAVA_JVM_LIBRARY=${JAVA_JVM_LIBRARY}")
else()
    message(FATAL_ERROR "Found JNI: failed")
endif()

find_package(powerwaf REQUIRED)

add_library(powerwaf_jni SHARED
    src/main/c/powerwaf_jni.c
    src/main/c/java_call.c
    src/main/c/utf16_utf8.c
    src/main/c/logging.c)

target_compile_definitions(powerwaf_jni PRIVATE _GNU_SOURCE)

message(STATUS "CMAKE_C_COMPILER=${CMAKE_C_COMPILER}")
if(CMAKE_C_COMPILER_ID STREQUAL "Clang")
    target_compile_options(powerwaf_jni PRIVATE
        -Werror
        -Weverything -Wno-unused-macros  -Wno-gnu-zero-variadic-macro-arguments
        -Wno-documentation -Wno-nullability-extension -Wno-zero-length-array
        -Wno-gnu-anonymous-struct -Wno-nested-anon-types
        -Wno-gnu-statement-expression -Wno-reserved-id-macro)
endif()

set_target_properties(powerwaf_jni PROPERTIES
	C_VISIBILITY_PRESET hidden)

target_include_directories(powerwaf_jni PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(powerwaf_jni Sqreen ${JAVA_JVM_LIBRARY})

if(CMAKE_BUILD_TYPE MATCHES Release)
	set_target_properties(powerwaf_jni PROPERTIES
		INSTALL_RPATH_USE_LINK_PATH false
		BUILD_WITH_INSTALL_RPATH true
		INSTALL_RPATH "$ORIGIN")
endif()
