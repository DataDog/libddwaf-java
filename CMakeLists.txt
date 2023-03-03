project(libsqreen_java)
cmake_minimum_required(VERSION 2.8.12)

include(FindJNI)
if (JNI_FOUND)
    message(STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
    message(STATUS "JAVA_JVM_LIBRARY=${JAVA_JVM_LIBRARY}")
else()
    message(FATAL_ERROR "Found JNI: failed")
endif()

find_package(libddwaf REQUIRED)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

if(MSVC)
    foreach(flag_var
            CMAKE_C_FLAGS CMAKE_C_FLAGS_DEBUG CMAKE_C_FLAGS_RELEASE
            CMAKE_C_FLAGS_MINSIZEREL CMAKE_C_FLAGS_RELWITHDEBINFO
            CMAKE_CXX_FLAGS CMAKE_CXX_FLAGS_DEBUG CMAKE_CXX_FLAGS_RELEASE
            CMAKE_CXX_FLAGS_MINSIZEREL CMAKE_CXX_FLAGS_RELWITHDEBINFO)
        string(REPLACE "/MD" "-MT" ${flag_var} "${${flag_var}}")
        string(REPLACE "/W3" "/W4" ${flag_var} "${${flag_var}}")
    endforeach()
    add_definitions(-D_CRT_SECURE_NO_WARNINGS=1 -Dstrdup=_strdup -Dputenv=_putenv)
endif()

if(NOT(MSVC))
    add_compile_options(-Wall -Wno-padded -Wno-unknown-pragmas -Wno-switch -Werror)
endif()

set(SOURCE_FILES
    src/main/c/byte_buffer.c
    src/main/c/cs_wrapper.c
    src/main/c/debug_helpers.c
    src/main/c/powerwaf_jni.c
    src/main/c/java_call.c
    src/main/c/metrics.c
    src/main/c/utf16_utf8.c
    src/main/c/logging.c)
if(MSVC OR APPLE)
    set(SOURCE_FILES ${SOURCE_FILES} src/main/c/compat.c)
endif()
add_library(sqreen_jni SHARED ${SOURCE_FILES})

target_compile_definitions(sqreen_jni PRIVATE _GNU_SOURCE)
if(CMAKE_CXX_COMPILER_ID MATCHES APPLE)
    target_compile_definitions(sqreen_jni PRIVATE TARGET_RT_MAC_CFM=0)
endif()

message(STATUS "CMAKE_C_COMPILER=${CMAKE_C_COMPILER}")
if(CMAKE_C_COMPILER_ID STREQUAL "Clang")
    target_compile_options(sqreen_jni PRIVATE
        -Werror
        -Weverything -Wno-unused-macros  -Wno-gnu-zero-variadic-macro-arguments
        -Wno-documentation -Wno-nullability-extension -Wno-zero-length-array
        -Wno-gnu-anonymous-struct -Wno-nested-anon-types -Wno-switch
        -Wno-gnu-statement-expression -Wno-reserved-id-macro
        -Wno-atomic-implicit-seq-cst -Wno-covered-switch-default
        -Wno-switch-enum)
endif()

set_target_properties(sqreen_jni PROPERTIES
    C_VISIBILITY_PRESET hidden)

target_include_directories(sqreen_jni PRIVATE ${JNI_INCLUDE_DIRS})
if(MSVC)
    set(LIBDDWAF_TARGET libddwaf_static)
else()
    set(LIBDDWAF_TARGET libddwaf_shared)
endif()
if(NOT (CMAKE_CXX_COMPILER_ID MATCHES MSVC OR APPLE))
    target_link_libraries(sqreen_jni PRIVATE rt)
endif()
target_link_libraries(sqreen_jni PRIVATE ${LIBDDWAF_TARGET} ${JAVA_JVM_LIBRARY})

if(NOT (CMAKE_BUILD_TYPE MATCHES Debug))
    if(APPLE)
        set(RPATH_VAL "@loader_path")
    else()
        set(RPATH_VAL "$ORIGIN")
    endif()

    set_target_properties(sqreen_jni PROPERTIES
        INSTALL_RPATH_USE_LINK_PATH false
        BUILD_WITH_INSTALL_RPATH true
        INSTALL_RPATH ${RPATH_VAL})

    if(NOT WIN32)
        execute_process(COMMAND "bash" "-c" "cd '${PROJECT_SOURCE_DIR}' && git rev-parse HEAD" OUTPUT_VARIABLE BUILD_ID)
        string(STRIP "${BUILD_ID}" BUILD_ID)
        message(STATUS "Build id is ${BUILD_ID}")
        string(SUBSTRING "${BUILD_ID}" 0 2 BUILD_ID_PREFIX)
        string(SUBSTRING "${BUILD_ID}" 2 39 BUILD_ID_SUFFIX)
    endif()

    if(NOT (CMAKE_CXX_COMPILER_ID MATCHES MSVC OR APPLE))
        target_link_libraries(sqreen_jni PRIVATE -Wl,--build-id=0x${BUILD_ID})
    endif()

    if (CMAKE_SYSTEM_NAME STREQUAL Darwin)
        find_program(DSYMUTIL dsymutil)
        if (DSYMUTIL STREQUAL "DSYMUTIL-NOTFOUND")
            message(FATAL_ERROR "dsymutil not found")
        endif()
        find_program(STRIP strip)
        if (STRIP STREQUAL "STRIP-NOTFOUND")
            message(FATAL_ERROR "strip not found")
        endif()

        set(SYMBOL_FILE $<TARGET_FILE:sqreen_jni>.dwarf)
        add_custom_command(TARGET sqreen_jni POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:sqreen_jni> ${SYMBOL_FILE}
            COMMAND ${DSYMUTIL} --flat --minimize ${SYMBOL_FILE}
            COMMAND ${STRIP} -S -x $<TARGET_FILE:sqreen_jni>
            COMMAND mv ${SYMBOL_FILE}.dwarf ${SYMBOL_FILE})
    elseif(NOT WIN32)
        #        set(SYMBOL_FILE $<TARGET_FILE:sqreen_jni>.debug)
        #add_custom_command(TARGET sqreen_jni POST_BUILD
        #    COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:sqreen_jni> ${SYMBOL_FILE}
        #    COMMAND ${CMAKE_STRIP} --only-keep-debug ${SYMBOL_FILE}
        #    COMMAND ${CMAKE_STRIP} $<TARGET_FILE:sqreen_jni>)
    endif()
endif()

# vim: set et:
