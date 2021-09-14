/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#ifndef LOGGING_H
#define LOGGING_H

#include <jni.h>
#include "common.h"
#include <ddwaf.h>

#ifdef _MSC_VER
# define __attribute__(...)
#endif

bool java_log_init(JavaVM *vm, JNIEnv *env);
void java_log_shutdown(JNIEnv *env);
void _java_wrap_exc_relay(JNIEnv *env,
        const char *format,
        const char *file, const char *function, int line, ...)
        __attribute__((format (printf, 2, 6)));

#define JAVA_LOG(level, fmt, ...) \
    java_log(level, __FUNCTION__, __FILE__, __LINE__, NULL, fmt, ##__VA_ARGS__)
#define JAVA_LOG_THR(level, thr, fmt, ...) \
    java_log(level, __FUNCTION__, __FILE__, __LINE__, thr, fmt, ##__VA_ARGS__)
void java_log(DDWAF_LOG_LEVEL level, const char *function, const char *file,
              int line, jthrowable throwable, const char *fmt, ...)
__attribute__((format (printf, 6, 7)));

#ifdef _MSC_VER
# undef __attribute__
#endif

/* wraps the pending exception in a RuntimeException (as a cause) with
 * a custom message */
#define java_wrap_exc(format, ...)  \
    _java_wrap_exc_relay(env, format, \
    (const char *) __FILE__, \
    __FUNCTION__, __LINE__, ##__VA_ARGS__)

#endif
