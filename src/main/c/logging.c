/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#include <ddwaf.h>

#include "logging.h"
#include "common.h"
#include "java_call.h"
#include "utf16_utf8.h"
#include "compat.h"

#ifdef _WIN32
#define DIR_SEP '\\'
#else
#define DIR_SEP '/'
#endif

#define LOGGER_NAME "ddwaf_native"
#define LOGGING_PATTERN "%s (%s on %s:%s)"
static jobject _trace, _debug, _info, _warn, _error;
static jobject _logger;
static jstring _log_pattern; // LOGGING_PATTERN
static struct j_method _log_meth;
static struct j_method _is_loggable;
static jclass _object_jcls;
static JavaVM *_vm;

static int file_strip_idx;

static bool _get_min_log_level(JNIEnv *env, DDWAF_LOG_LEVEL *level);
static void _waf_logging_c(DDWAF_LOG_LEVEL level, const char *function,
                           const char *file, unsigned line, const char *message,
                           uint64_t message_len);
static void _waf_logging_c_throwable(DDWAF_LOG_LEVEL level,
                                     const char *function, const char *file,
                                     int line, const char *message,
                                     uint64_t message_len,
                                     jthrowable throwable);
static const char *_remove_path(const char *path);
static JNIEnv *_attach_vm(bool *attached);
static void _detach_vm(void);

struct slf4j_strings {
    const char *level, *level_descr, *logger_factory, *get_logger_descr,
            *info_to_dbg_descr, *log_descr, *is_loggable_descr;
};
static const struct slf4j_strings slf4j_strings_org = {
        .level = "org/slf4j/event/Level",
        .level_descr = "Lorg/slf4j/event/Level;",
        .logger_factory = "org/slf4j/LoggerFactory",
        .get_logger_descr = "(Ljava/lang/String;)Lorg/slf4j/Logger;",
        .info_to_dbg_descr = "(Lorg/slf4j/Logger;)V",
        .log_descr = "(Lorg/slf4j/event/Level;Ljava/lang/Throwable;"
                     "Ljava/lang/String;[Ljava/lang/Object;)V",
        .is_loggable_descr = "(Lorg/slf4j/event/Level;)Z",
};
static const struct slf4j_strings slf4j_strings_ddog = {
        .level = "datadog/slf4j/event/Level",
        .level_descr = "Ldatadog/slf4j/event/Level;",
        .logger_factory = "datadog/slf4j/LoggerFactory",
        .get_logger_descr = "(Ljava/lang/String;)Ldatadog/slf4j/Logger;",
        .info_to_dbg_descr = "(Ldatadog/slf4j/Logger;)V",
        .log_descr = "(Ldatadog/slf4j/event/Level;Ljava/lang/Throwable;"
                     "Ljava/lang/String;[Ljava/lang/Object;)V",
        .is_loggable_descr = "(Ldatadog/slf4j/event/Level;)Z",
};
static const struct slf4j_strings *slf4j_active = &slf4j_strings_org;

DDWAF_LOG_LEVEL min_level;
extern inline bool log_level_enabled(DDWAF_LOG_LEVEL);

bool java_log_init(JavaVM *vm, JNIEnv *env)
{
    char *loc = memrchr(__FILE__, DIR_SEP, strlen(__FILE__));
    file_strip_idx = loc ? (int) (loc - __FILE__ + 1) : 0;

    bool retval = false;
    struct j_method fact_get = {0};
    struct j_method wrapper_log_init = {0};
    jstring logger_name = NULL;
    jstring str_pattern_local = NULL;
    jobject logger_local = NULL;
    jobject wrapper_local = NULL;
    jclass level_cls = NULL;
    jclass object_cls_local = NULL;

    _vm = vm;

    level_cls = JNI(FindClass, slf4j_active->level);
    if (!level_cls) {
        JNI(ExceptionClear);
        slf4j_active = &slf4j_strings_ddog;
        level_cls = JNI(FindClass, slf4j_active->level);
        if (!level_cls) {
            java_wrap_exc("Could find slf4j Level neither at %s, nor at %s",
                          slf4j_strings_org.level, slf4j_strings_ddog.level);
            goto error;
        }
    }

    object_cls_local = JNI(FindClass, "java/lang/Object");
    if (!object_cls_local) {
        goto error;
    }
    _object_jcls = JNI(NewGlobalRef, object_cls_local);
    if (!_object_jcls) {
        goto error;
    }

#define FETCH_FIELD(var, name)                                                 \
    do {                                                                       \
        var = java_static_field_checked(env, level_cls, name,                  \
                                        slf4j_active->level_descr);            \
        if (!var) {                                                            \
            goto error;                                                        \
        }                                                                      \
    } while (0)

    FETCH_FIELD(_trace, "TRACE");
    FETCH_FIELD(_debug, "DEBUG");
    FETCH_FIELD(_info, "INFO");
    FETCH_FIELD(_warn, "WARN");
    FETCH_FIELD(_error, "ERROR");

    if (!java_meth_init_checked(env, &fact_get, slf4j_active->logger_factory,
                                "getLogger", slf4j_active->get_logger_descr,
                                JMETHOD_STATIC)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &wrapper_log_init,
                                "com/datadog/ddwaf/logging/InfoToDebugLogger",
                                "<init>", slf4j_active->info_to_dbg_descr,
                                JMETHOD_CONSTRUCTOR)) {
        goto error;
    }

    logger_name = JNI(NewStringUTF, LOGGER_NAME);
    if (!logger_name) {
        goto error;
    }
    logger_local = java_meth_call(env, &fact_get, NULL, logger_name);
    if (JNI(ExceptionCheck)) {
        goto error;
    }

    wrapper_local = java_meth_call(env, &wrapper_log_init, NULL, logger_local);
    if (JNI(ExceptionCheck)) {
        goto error;
    }

    /* This will prevent garbage collection of the classloader without
     * calling Waf.deinitialize(). This is because the InfoToDebugLogger
     * class will likely have been loaded by the same classloader that is
     * loading this JNI library */
    _logger = JNI(NewGlobalRef, wrapper_local);
    if (!_logger) {
        goto error;
    }

    str_pattern_local = JNI(NewStringUTF, LOGGING_PATTERN);
    if (!str_pattern_local) {
        goto error;
    }
    _log_pattern = JNI(NewGlobalRef, str_pattern_local);
    if (!_log_pattern) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &_log_meth, "com/datadog/ddwaf/logging/InfoToDebugLogger",
                "log", slf4j_active->log_descr, JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &_is_loggable,
                                "com/datadog/ddwaf/logging/InfoToDebugLogger",
                                "isLoggable", slf4j_active->is_loggable_descr,
                                JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    if (!_get_min_log_level(env, &min_level)) {
        java_wrap_exc("Could not determine minimum log level");
        goto error;
    }

    ddwaf_set_log_cb(_waf_logging_c, min_level);

    retval = true;

error:
    if (level_cls) {
        JNI(DeleteLocalRef, level_cls);
    }
    if (object_cls_local) {
        JNI(DeleteLocalRef, object_cls_local);
    }
    if (logger_name) {
        JNI(DeleteLocalRef, logger_name);
    }
    if (str_pattern_local) {
        JNI(DeleteLocalRef, str_pattern_local);
    }
    if (logger_local) {
        JNI(DeleteLocalRef, logger_local);
    }
    if (wrapper_local) {
        JNI(DeleteLocalRef, wrapper_local);
    }
    java_meth_destroy(env, &fact_get);
    java_meth_destroy(env, &wrapper_log_init);
    if (!retval) {
        java_log_shutdown(env);
    }
    return retval;
}

void java_log_shutdown(JNIEnv *env)
{
    if (_object_jcls) {
        JNI(DeleteGlobalRef, _object_jcls);
    }
    if (_trace) {
        JNI(DeleteWeakGlobalRef, _trace);
    }
    if (_debug) {
        JNI(DeleteWeakGlobalRef, _debug);
    }
    if (_info) {
        JNI(DeleteWeakGlobalRef, _info);
    }
    if (_warn) {
        JNI(DeleteWeakGlobalRef, _warn);
    }
    if (_error) {
        JNI(DeleteWeakGlobalRef, _error);
    }
    if (_logger) {
        JNI(DeleteGlobalRef, _logger);
    }
    if (_log_pattern) {
        JNI(DeleteGlobalRef, _log_pattern);
    }

    // actually not needed, these are virtual so don't store the class
    java_meth_destroy(env, &_log_meth);
    java_meth_destroy(env, &_is_loggable);
}

void java_log(DDWAF_LOG_LEVEL level, const char *function, const char *file,
              int line, jthrowable throwable, const char *fmt, ...)
{
    if (!log_level_enabled(level)) {
        // don't even create the Java String if we won't log it anyway
        return;
    }

    char *message = NULL;
    va_list ap;
    va_start(ap, fmt);
    int message_len = vasprintf(&message, fmt, ap);
    va_end(ap);

    if (!message) {
        return;
    }
    _waf_logging_c_throwable(level, function, file + file_strip_idx, line,
                             message, (uint64_t) message_len, throwable);
    free(message);
}

static bool _get_min_log_level(JNIEnv *env, DDWAF_LOG_LEVEL *level)
{
#define TEST_LEVEL(jobj, pwl_level)                                            \
    do {                                                                       \
        if (JNI(CallBooleanMethod, _logger, _is_loggable.meth_id, jobj)) {     \
            *level = pwl_level;                                                \
            return true;                                                       \
        }                                                                      \
        if (JNI(ExceptionCheck)) {                                             \
            return false;                                                      \
        }                                                                      \
    } while (0)

    TEST_LEVEL(_trace, DDWAF_LOG_TRACE);
    TEST_LEVEL(_debug, DDWAF_LOG_DEBUG);
    TEST_LEVEL(_info, DDWAF_LOG_INFO);
    TEST_LEVEL(_warn, DDWAF_LOG_WARN);
    *level = DDWAF_LOG_ERROR;
    return true;
}
static jobject _lvl_api_to_java(DDWAF_LOG_LEVEL api_lvl)
{
    switch (api_lvl) {
    case DDWAF_LOG_TRACE:
        return _trace;
    case DDWAF_LOG_DEBUG:
        return _debug;
    case DDWAF_LOG_INFO:
        return _info;
    case DDWAF_LOG_WARN:
        return _warn;
    case DDWAF_LOG_ERROR:
        return _error;
    }
    // should not be reached
    return _debug;
}
static void _waf_logging_c(DDWAF_LOG_LEVEL level, const char *function,
                           const char *file, unsigned line, const char *message,
                           uint64_t message_len)
{
    _waf_logging_c_throwable(level, function, file, (int) line, message,
                             message_len, NULL);
}
static void _waf_logging_c_throwable(DDWAF_LOG_LEVEL level,
                                     const char *function, const char *file,
                                     int line, const char *message,
                                     uint64_t message_len, jthrowable throwable)
{
    UNUSED(message_len);

    bool attached = false;
    JNIEnv *env = _attach_vm(&attached);
    if (!env) {
        return;
    }

    jthrowable prev_thr = JNI(ExceptionOccurred);
    if (prev_thr) {
        JNI(ExceptionClear);
    }

    jstring message_jstr = NULL;
    jstring file_jstr = NULL;
    jstring function_jstr = NULL;
    jstring line_jstr = NULL;
    jobjectArray args_arr = NULL;

    message_jstr = java_utf8_to_jstring_checked(env, message, strlen(message));
    if (!message_jstr) {
        goto error;
    }

    file_jstr = JNI(NewStringUTF, file);
    if (!file_jstr) {
        goto error;
    }

    function_jstr = JNI(NewStringUTF, function);
    if (!function_jstr) {
        goto error;
    }

    char int_cstr[sizeof("-9223372036854775808")] = ""; // in case int is 64-bit
    sprintf(int_cstr, "%d", line);
    line_jstr = JNI(NewStringUTF, int_cstr);
    if (!line_jstr) {
        goto error;
    }

    args_arr = JNI(NewObjectArray, 4, _object_jcls, NULL);
    if (!args_arr) {
        goto error;
    }
#define ADD_ARR_ELEM(idx, var)                                                 \
    do {                                                                       \
        JNI(SetObjectArrayElement, args_arr, idx, var);                        \
        if (JNI(ExceptionCheck)) {                                             \
            goto error;                                                        \
        }                                                                      \
    } while (0)
    ADD_ARR_ELEM(0, message_jstr);
    ADD_ARR_ELEM(1, function_jstr);
    ADD_ARR_ELEM(2, file_jstr);
    ADD_ARR_ELEM(3, line_jstr);

    jobject java_level = _lvl_api_to_java(level);
    JNI(CallVoidMethod, _logger, _log_meth.meth_id, java_level, throwable,
        _log_pattern, args_arr);

error:
    if (JNI(ExceptionCheck)) {
        JNI(ExceptionClear);
    }

    if (message_jstr) {
        JNI(DeleteLocalRef, message_jstr);
    }
    if (file_jstr) {
        JNI(DeleteLocalRef, file_jstr);
    }
    if (function_jstr) {
        JNI(DeleteLocalRef, function_jstr);
    }
    if (line_jstr) {
        JNI(DeleteLocalRef, line_jstr);
    }
    if (args_arr) {
        JNI(DeleteLocalRef, args_arr);
    }

    if (prev_thr) {
        JNI(Throw, prev_thr);
        JNI(DeleteLocalRef, prev_thr);
    }

    if (attached) {
        _detach_vm();
    }
}

static JNIEnv *_attach_vm(bool *attached)
{
    JNIEnv *env;
    *attached = false;
    jint res = (*_vm)->GetEnv(_vm, (void **) &env, JNI_VERSION_1_6);
    if (res == JNI_OK) {
        return env;
    } else if (res == JNI_EDETACHED) {
        if ((*_vm)->AttachCurrentThread(_vm, (void **) &env, NULL) == JNI_OK) {
            *attached = true;
            return env;
        } else {
            return NULL;
        }
    } else { // res == JNI_EVERSION
        return NULL;
    }
}
static void _detach_vm(void)
{
    (*_vm)->DetachCurrentThread(_vm); // error ignored, nothing we can do
}

void _java_wrap_exc_relay(JNIEnv *env, const char *format, const char *file,
                          const char *function, int line, ...)
{
    char *epilog = NULL;
    char *user_msg = NULL;
    char *final_msg = NULL;
    jstring message_obj = NULL;

    if (!rte_constr_cause || !jcls_rte) {
        return;
    }

    jthrowable prev_throwable = JNI(ExceptionOccurred);
    jthrowable new_throwable = NULL;
    if (!prev_throwable) {
        return;
    }

    int size_epilog =
            asprintf(&epilog, "during native function %s, file %s, line %d",
                     function, _remove_path(file), line);
    if (size_epilog < 0) {
        goto error;
    }

    va_list ap;
    va_start(ap, line);
    int size_user_msg = vasprintf(&user_msg, format, ap);
    va_end(ap);
    if (size_user_msg < 0) {
        goto error;
    }

    final_msg = malloc((size_t) size_epilog + (size_t) size_user_msg + 2);
    if (!final_msg) {
        goto error;
    }

    char *msg_write = final_msg;
    memcpy(msg_write, user_msg, (size_t) size_user_msg);
    msg_write += size_user_msg;
    *(msg_write++) = ' ';
    memcpy(msg_write, epilog, (size_t) size_epilog);
    msg_write += size_epilog;
    *msg_write = '\0';

    JNI(ExceptionClear);

    message_obj = java_utf8_to_jstring_checked(
            env, final_msg, (size_t) (msg_write - final_msg));
    if (JNI(ExceptionCheck)) { // error in jstring creation; abort wrapping
        JNI(ExceptionClear);
        JNI(Throw, prev_throwable);
        goto error;
    }

    new_throwable = JNI(NewObject, jcls_rte, rte_constr_cause, message_obj,
                        prev_throwable);
    if (JNI(ExceptionCheck)) {
        JNI(ExceptionClear);
        JNI(Throw, prev_throwable);
        goto error;
    }

    JNI(Throw, new_throwable);

error:
    if (prev_throwable) {
        JNI(DeleteLocalRef, prev_throwable);
    }
    if (message_obj) {
        JNI(DeleteLocalRef, message_obj);
    }
    if (new_throwable) {
        JNI(DeleteLocalRef, new_throwable);
    }
    free(epilog);
    free(user_msg);
    free(final_msg);
}

static const char *_remove_path(const char *path)
{
    const char *res = path;
    while (*path) {
        if (*path == '/') {
            res = path + 1;
        }
        path++;
    }
    return res;
}
