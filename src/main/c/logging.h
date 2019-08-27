#ifndef LOGGING_H
#define LOGGING_H

#include <jni.h>
#include "common.h"

bool java_log_init(JavaVM *vm, JNIEnv *env);
void java_log_shutdown(JNIEnv *env);
void _java_wrap_exc_relay(JNIEnv *env,
        const char *format,
        const char *file, const char *function, int line, ...)
        __attribute__((format (printf, 2, 6)));

/* wraps the pending exception in a RuntimeException (as a cause) with
 * a custom message */
#define java_wrap_exc(format, ...)  \
    _java_wrap_exc_relay(env, format, \
    (const char *) __FILE__, \
    __FUNCTION__, __LINE__, ##__VA_ARGS__)

#endif
