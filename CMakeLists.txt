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

if(MSVC)
    foreach(flag_var
            CMAKE_C_FLAGS CMAKE_C_FLAGS_DEBUG CMAKE_C_FLAGS_RELEASE
            CMAKE_C_FLAGS_MINSIZEREL CMAKE_C_FLAGS_RELWITHDEBINFO
            CMAKE_CXX_FLAGS CMAKE_CXX_FLAGS_DEBUG CMAKE_CXX_FLAGS_RELEASE
            CMAKE_CXX_FLAGS_MINSIZEREL CMAKE_CXX_FLAGS_RELWITHDEBINFO)
        string(REPLACE "/MD" "-MT" ${flag_var} "${${flag_var}}")
        string(REPLACE "/W3" "/W4" ${flag_var} "${${flag_var}}")
    endforeach()
endif()

add_library(powerwaf_jni SHARED
    src/main/c/powerwaf_jni.c
    src/main/c/java_call.c
    src/main/c/utf16_utf8.c
    src/main/c/logging.c
    $<$<BOOL:MSVC>:src/main/c/windows_compat.c>)

target_compile_definitions(powerwaf_jni PRIVATE _GNU_SOURCE)

message(STATUS "CMAKE_C_COMPILER=${CMAKE_C_COMPILER}")
if(CMAKE_C_COMPILER_ID STREQUAL "Clang")
    target_compile_options(powerwaf_jni PRIVATE
        -Werror
        -Weverything -Wno-unused-macros  -Wno-gnu-zero-variadic-macro-arguments
        -Wno-documentation -Wno-nullability-extension -Wno-zero-length-array
        -Wno-gnu-anonymous-struct -Wno-nested-anon-types -Wno-switch
        -Wno-gnu-statement-expression -Wno-reserved-id-macro)
endif()

set_target_properties(powerwaf_jni PROPERTIES
    C_VISIBILITY_PRESET hidden)

target_include_directories(powerwaf_jni PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(powerwaf_jni PRIVATE Sqreen ${JAVA_JVM_LIBRARY})

if(NOT (CMAKE_BUILD_TYPE MATCHES Debug))
    set_target_properties(powerwaf_jni PROPERTIES
        INSTALL_RPATH_USE_LINK_PATH false
        BUILD_WITH_INSTALL_RPATH true
        INSTALL_RPATH "$ORIGIN")

    if(NOT WIN32)
        execute_process(COMMAND "bash" "-c" "cd '${powerwaf_jni_SOURCE_DIR}' && git rev-parse HEAD" OUTPUT_VARIABLE BUILD_ID)
        string(STRIP "${BUILD_ID}" BUILD_ID)
        message(STATUS "Build id is ${BUILD_ID}")
        string(SUBSTRING "${BUILD_ID}" 0 2 BUILD_ID_PREFIX)
        string(SUBSTRING "${BUILD_ID}" 2 39 BUILD_ID_SUFFIX)
    endif()

    if(NOT (CMAKE_CXX_COMPILER_ID MATCHES MSVC))
        target_link_libraries(powerwaf_jni PRIVATE -Wl,--build-id=0x${BUILD_ID})
    endif()

    if(NOT WIN32)
        set(SYMBOL_FILE $<TARGET_FILE:powerwaf_jni>.debug)
        add_custom_command(TARGET powerwaf_jni POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:powerwaf_jni> ${SYMBOL_FILE}
            COMMAND ${CMAKE_STRIP} --only-keep-debug ${SYMBOL_FILE}
            COMMAND ${CMAKE_STRIP} $<TARGET_FILE:powerwaf_jni>)
    endif()
endif()

# vim: set et:
