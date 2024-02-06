/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include <jni.h>
#include "jni/io_sqreen_powerwaf_Powerwaf.h"
#include "jni/io_sqreen_powerwaf_Additive.h"
#include "common.h"
#include "java_call.h"
#include "json.h"
#include "utf16_utf8.h"
#include "output.h"
#include "logging.h"
#include "metrics.h"
#include "cs_wrapper.h"
#include "compat.h"
#include <ddwaf.h>
#include <assert.h>
#ifndef _MSC_VER
#include <dlfcn.h>
#endif
#include <math.h>
#include <stdint.h>
#include <string.h>
#include <inttypes.h>
#include <time.h>
#include <limits.h>

struct _limits {
    int64_t general_budget_in_us;
    int64_t run_budget_in_us;
    int max_depth;
    int max_elements;
    int max_string_size;
    char _padding[4];
};

// suffix _checked means if a function fails it leaves a pending exception
static bool _check_init(JNIEnv *env);
static void _deinitialize(JNIEnv *env);
static bool _cache_references(JNIEnv *env);
static void _dispose_of_action_enums(JNIEnv *env);
static void _dispose_of_result_with_data_fields(JNIEnv *env);
static void _dispose_of_cache_references(JNIEnv *env);
struct _init_or_update {
    bool is_update;
    union {
        jobject jconfig;
        jobject jold_handle;
    };
    jobject jspec;
    jobjectArray jrsi_arr;
};
static jobject _ddwaf_init_or_update_checked(JNIEnv *env, struct _init_or_update *s);
static ddwaf_object _convert_checked(JNIEnv *env, jobject obj, struct _limits *limits, int rec_level);
static ddwaf_object* _convert_buffer_checked(JNIEnv *env, jobject buffer);
static struct _limits _fetch_limits_checked(JNIEnv *env, jobject limits_obj);
struct char_buffer_info {
    jchar *nat_array;
    jcharArray javaArray;
    int start;
    int end;
    bool call_release;
};
static bool _get_char_buffer_data(JNIEnv *env, jobject obj, struct char_buffer_info *info);
static ddwaf_context _get_additive_context_checked(JNIEnv *env, jobject additive_obj);
static bool _set_additive_context_checked(JNIEnv *env, jobject additive_obj, ddwaf_context ctx);
static bool _get_time_checked(JNIEnv *env, struct timespec *time);
static inline int64_t _timespec_diff_ns(struct timespec a, struct timespec b);
static int64_t _get_pw_run_timeout_checked(JNIEnv *env);
static size_t get_run_budget(int64_t rem_gen_budget_in_us, struct _limits *limits);
static int64_t get_remaining_budget(struct timespec start, struct timespec end, struct _limits *limits);
static void _throw_pwaf_exception(JNIEnv *env, DDWAF_RET_CODE retcode);
static void _throw_pwaf_timeout_exception(JNIEnv *env);
static void _update_metrics(JNIEnv *env, jobject metrics_obj, bool is_byte_buffer, const ddwaf_result *ret, struct timespec start);
static bool _convert_ddwaf_config_checked(JNIEnv *env, jobject jconfig, ddwaf_config *out_config);
static void _dispose_of_ddwaf_config(ddwaf_config *cfg);
static jobject _create_result_checked(JNIEnv *env, DDWAF_RET_CODE code, const ddwaf_result *ret);
static inline bool _has_derivative(const ddwaf_result *res);

#define MAX_DEPTH_UPPER_LIMIT ((uint32_t)32)

// don't use DDWAF_OBJ_INVALID, as that can't be added to arrays/maps
static const ddwaf_object _pwinput_invalid = { .type = DDWAF_OBJ_MAP };

jclass jcls_rte;
jclass jcls_iae;
jmethodID rte_constr_cause;

static struct j_method _create_exception;
static struct j_method _timeout_exception_init;
/* these three are weak global references
 * we don't need strong ones because they are static fields of a class that
 * won't be unloaded as long as the Powerwaf class is loaded */
static jobject _action_ok;
static jobject _action_match;
static jobject _result_with_data_ok_null;
static jobject _result_with_data_empty_str_arr;
static struct j_method result_with_data_init;
static jfieldID _limit_max_depth;
static jfieldID _limit_max_elements;
static jfieldID _limit_max_string_size;
static jfieldID _limit_general_budget_in_us;
static jfieldID _limit_run_budget_in_us;

static jfieldID _config_key_regex;
static jfieldID _config_value_regex;
static jfieldID _config_use_byte_buffers;

static jfieldID _additive_ptr;

jclass charSequence_cls;
struct j_method charSequence_length;
struct j_method charSequence_subSequence;

jclass buffer_cls;
struct j_method buffer_position;
struct j_method buffer_limit;

jclass charBuffer_cls;
struct j_method charBuffer_hasArray;
struct j_method charBuffer_array;
static struct j_method _charBuffer_order;
// weak, but assumed never to be gced
static jobject _native_order;

jclass string_cls;
struct j_method to_string;

static struct j_method _pwaf_handle_init;
static jfieldID _pwaf_handle_native_handle;

struct j_method number_longValue;
struct j_method number_doubleValue;
// weak, but assumed never to be gced
jclass *number_cls = &number_longValue.class_glob;
static jclass double_cls;
static jclass float_cls;
static jclass bigdecimal_cls;

static struct j_method _boolean_booleanValue;
static jclass *_boolean_cls = &_boolean_booleanValue.class_glob;

struct j_method map_entryset;
struct j_method map_size;
// weak, but assumed never to be gced
jclass *map_cls = &map_entryset.class_glob;
struct j_method entry_key;
struct j_method entry_value;

struct j_method iterable_iterator;
// weak, but assumed never to be gced
jclass *iterable_cls = &iterable_iterator.class_glob;
struct j_method iterator_next;
struct j_method iterator_hasNext;

struct j_method class_is_array;
static struct j_method _class_get_name;

static int64_t pw_run_timeout;

static bool _init_ok;

#ifdef _MSC_VER
# include <crtdbg.h>
# define FULL_MEMORY_BARRIER _ReadWriteBarrier
_STATIC_ASSERT(sizeof(bool) == 1);
# define COMPARE_AND_SWAP(ptr, old, new) \
    _InterlockedCompareExchange8(ptr, new, old)
#else
# define FULL_MEMORY_BARRIER __sync_synchronize
# define COMPARE_AND_SWAP(ptr, old, new) \
    __sync_bool_compare_and_swap(ptr, old, new)
#endif

// TODO move global intialization/deinitialization to another file
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    UNUSED(reserved);

    JNIEnv *env;
    (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);

    bool cache_ref_ok = _cache_references(env);
    if (!cache_ref_ok) {
        if (!JNI(ExceptionCheck)) {
            JNI(ThrowNew, jcls_rte, "Library initialization failed "
                                    "(_cache_references)");
        }
        goto error;
    }

    // If the glibc version was loaded on musl, raise an exception at this
    // point (which is as soon as possible once jcls_rte is defined)
    // In general, it is unsafe to continue, as symbols may be missing.
    // In practice, the release glibc binary is currently compatible with musl,
    // but that compatibility relies on an old glibc version to compile the
    // binary against, as well as the glibc functionality used and the value of
    // compilation switches such as _FORTIFY_SOURCE.
#ifdef __GLIBC__
    void *glibc_version_f = dlsym(NULL, "gnu_get_libc_version");
    if (!glibc_version_f) {
        JNI(ThrowNew, jcls_rte,
            "JNI library was compiled against glibc, "
            "but at runtime the function gnu_get_libc_version was not found. "
            "Not a glibc process?");
        goto error;
    }
#endif

    bool log_ok = java_log_init(vm, env);
    if (!log_ok) {
        if (!JNI(ExceptionCheck)) {
            JNI(ThrowNew, jcls_rte, "Library initialization failed"
                                    "(java_log_init)");
        }
        goto error;
    }

    bool metrics_ok = metrics_init(env);
    if (!metrics_ok) {
        if (!JNI(ExceptionCheck)) {
            JNI(ThrowNew, jcls_rte, "Library initialization failed"
                                    "(metrics_init)");
        }
        goto error;
    }

    output_init_checked(env);
    if (JNI(ExceptionCheck)) {
        java_wrap_exc("Failed initializing references for diagnostics.c");
        goto error;
    }

    cs_wrapper_init(env);

    pw_run_timeout = _get_pw_run_timeout_checked(env);
    if (JNI(ExceptionCheck)) {
        goto error;
    }

    // probably not needed, as we piggyback on Java's synchronization
    FULL_MEMORY_BARRIER();
    _init_ok = true;

error:
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved)
{
    UNUSED(reserved);

    JNIEnv *env;
    (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
    _deinitialize(env);
}

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    deinitialize
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_Powerwaf_deinitialize(
        JNIEnv *env, jclass clazz)
{
    UNUSED(clazz);

    _deinitialize(env);
}

/*
 * Class:     io.sqreen.powerwaf.Powerwaf
 * Method:    addRules
 * Signature: (Ljava/util/Map;[Lio/sqreen/powerwaf/PowerwafConfig;[Lio/sqreen/powerwaf/RuleSetInfo;)Lio/sqreen/powerwaf/PowerwafHandle;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Powerwaf_addRules(
        JNIEnv *env, jclass clazz,
        jobject rule_def, jobject jconfig, jobject rule_set_info_arr)
{
    UNUSED(clazz);

    return _ddwaf_init_or_update_checked(env, &(struct _init_or_update){
        .jconfig = jconfig,
        .jspec = rule_def,
        .jrsi_arr = rule_set_info_arr,
    });
}

static jobject _ddwaf_init_or_update_checked(JNIEnv *env,
                                             struct _init_or_update *s)
{
    if (!_check_init(env)) {
        return NULL;
    }

    ddwaf_handle new_handle = NULL;

    struct _limits limits = {
            .max_depth = 20,
            .max_elements = 1000000,
            .max_string_size = 1000000,
    };
    ddwaf_object input = _convert_checked(env, s->jspec, &limits, 0);
    jthrowable thr = JNI(ExceptionOccurred);
    if (thr) {
        JAVA_LOG_THR(DDWAF_LOG_ERROR, thr,
                     "Exception encoding init/update rule specifications");
        java_wrap_exc("%s",
                      "Exception encoding init/update rule specification");
        JNI(DeleteLocalRef, thr);
        return NULL;
    }

    ddwaf_object *diagnostics = NULL;
    // jump to error henceforth

    bool has_rule_set_info = !JNI(IsSameObject, s->jrsi_arr, NULL) &&
                             JNI(GetArrayLength, s->jrsi_arr) == 1;
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    diagnostics = has_rule_set_info ? &(ddwaf_object){0} : NULL;

    if (s->is_update) {
        ddwaf_handle nat_handle;
        if (!(nat_handle = get_pwaf_handle_checked(env, s->jold_handle))) {
            goto error;
        }
        new_handle = ddwaf_update(nat_handle, &input, diagnostics);
    } else {
        ddwaf_config config;
        if (!_convert_ddwaf_config_checked(env, s->jconfig, &config)) {
            java_wrap_exc("%s", "Error converting PowerwafConfig");
            goto error;
        }
        new_handle = ddwaf_init(&input, &config, diagnostics);
        _dispose_of_ddwaf_config(&config);
    }

    // even if ddwaf_update/init failed, we try to report ruleset info
    if (diagnostics && memcmp(diagnostics, &(ddwaf_object){0},
                                sizeof(*diagnostics)) != 0) {
        jobject jrsi = output_convert_diagnostics_checked(env, diagnostics);

        if (JNI(ExceptionCheck)) {
            java_wrap_exc("Error converting rule info structure");
            goto error;
        }
        JNI(SetObjectArrayElement, s->jrsi_arr, 0, jrsi);
        JNI(DeleteLocalRef, jrsi);
        if (JNI(ExceptionCheck)) {
            java_wrap_exc("Error setting reference for RuleSetInfo");
            goto error;
        }
    }

    if (!new_handle) {
        if (s->is_update) {
            JAVA_LOG(DDWAF_LOG_WARN, "call to ddwaf_update failed");
            JNI(ThrowNew, jcls_iae, "Call to ddwaf_update failed");
        } else {
            JAVA_LOG(DDWAF_LOG_WARN,
                     "call to ddwaf_init failed (or no update)");
            JNI(ThrowNew, jcls_iae, "Call to ddwaf_init failed (or no update)");
        }
        goto error;
    } else {
        JAVA_LOG(DDWAF_LOG_DEBUG, "Successfully created ddwaf_handle");
    }

    ddwaf_object_free(&input);
    if (diagnostics) {
        ddwaf_object_free(diagnostics);
    }
    return java_meth_call(env, &_pwaf_handle_init, NULL,
                          (jlong) (intptr_t) new_handle);

error:
    ddwaf_object_free(&input);
    if (diagnostics) {
        ddwaf_object_free(diagnostics);
    }
    if (new_handle) {
        ddwaf_destroy(new_handle);
    }
    return NULL;
}

/*
 * Class:     io.sqreen.powerwaf.Powerwaf
 * Method:    clearRules
 * Signature: (Lio/sqreen/powerwaf/PowerWAFHandle;)V
 */
JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_Powerwaf_clearRules(
        JNIEnv *env, jclass clazz, jobject handle_obj)
{
    UNUSED(clazz);

    if (!_check_init(env)) {
        return;
    }

    ddwaf_handle nat_handle;
    if (!(nat_handle = get_pwaf_handle_checked(env, handle_obj))) {
        return;
    }

    ddwaf_destroy(nat_handle);
}

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    getKnownAddresses
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_io_sqreen_powerwaf_Powerwaf_getKnownAddresses
  (JNIEnv *env, jclass clazz, jobject handle_obj)
{
    UNUSED(clazz);

    if (!_check_init(env)) {
        return NULL;
    }

    ddwaf_handle nat_handle;
    if (!(nat_handle = get_pwaf_handle_checked(env, handle_obj))) {
        return NULL;
    }

    uint32_t size;
    const char* const* addresses = ddwaf_known_addresses(nat_handle, &size);
    if (!addresses || size == 0 || size > INT_MAX /* jsize == int */) {
        JAVA_LOG(DDWAF_LOG_DEBUG, "Found no addresses in ruleset");
        jobject ret_jarr = JNI(NewObjectArray, 0, string_cls, NULL);
        UNUSED(JNI(ExceptionCheck));
        return ret_jarr;
    }

    JAVA_LOG(DDWAF_LOG_DEBUG, "Found %u addresses in ruleset", size);

    jobject ret_jarr = JNI(NewObjectArray, (jsize) size, string_cls, NULL);
    if (JNI(ExceptionCheck)) {
        return NULL;
    }

    for (jsize i = 0; i < (jsize) size; i++) {
        const char *addr = addresses[i];
        if (!addr) {
            JNI(ThrowNew, jcls_rte,
                "Unexpected NULL ptr in returned list of addresses");
            return NULL; // should not happen
        }
        jstring addr_jstr =
                java_utf8_to_jstring_checked(env, addr, strlen(addr));
        if (!addr_jstr) {
            return NULL;
        }
        JNI(SetObjectArrayElement, ret_jarr, i, addr_jstr);
        if (JNI(ExceptionCheck)) {
            return NULL;
        }
        JNI(DeleteLocalRef, addr_jstr);
    }

    return ret_jarr;
}

// runRule overloads
static jobject _run_rule_common(bool is_byte_buffer, JNIEnv *env, jclass clazz,
                                jobject handle_obj, jobject persistent_data, jobject ephemeral_data,
                                jobject limits_obj, jobject metrics_obj)
{
    UNUSED(clazz);

    jobject result = NULL;

    ddwaf_object persistent_input = _pwinput_invalid;
    ddwaf_object ephemeral_input = _pwinput_invalid;

    ddwaf_object * persistent_input_ptr = NULL;
    ddwaf_object * ephemeral_input_ptr = NULL;
    ddwaf_context ctx = NULL;
    struct _limits limits;
    ddwaf_result ret;
    struct timespec start;

    if (!_get_time_checked(env, &start)) {
        return NULL;
    }

    if (!_check_init(env)) {
        return NULL;
    }

    limits = _fetch_limits_checked(env, limits_obj);
    if (JNI(ExceptionCheck)) {
        return NULL;
    }

    ddwaf_handle pwhandle;
    if (!(pwhandle = get_pwaf_handle_checked(env, handle_obj))) {
        return NULL;
    }

    int64_t rem_gen_budget_in_us;
    if (is_byte_buffer) {
        persistent_input_ptr = _convert_buffer_checked(env, persistent_data);
        jthrowable thr = JNI(ExceptionOccurred);
        if (thr) {
            JAVA_LOG_THR(DDWAF_LOG_INFO, thr,
                         "Exception converting 'persistent' ByteBuffer into ddwaf_object");
            java_wrap_exc("%s", "Exception converting 'persistent' ByteBuffer into ddwaf_object");
            JNI(DeleteLocalRef, thr);
            goto end;
        }

        ephemeral_input_ptr = _convert_buffer_checked(env, ephemeral_data);
        thr = JNI(ExceptionOccurred);
        if (thr) {
            JAVA_LOG_THR(DDWAF_LOG_INFO, thr,
                         "Exception converting 'ephemeral' ByteBuffer into ddwaf_object");
            java_wrap_exc("%s", "Exception converting 'ephemeral' ByteBuffer into ddwaf_object");
            JNI(DeleteLocalRef, thr);
            goto end;
        }

        // let's pretend nothing we did till now took time
        rem_gen_budget_in_us = limits.general_budget_in_us;

        if (persistent_input_ptr == NULL && ephemeral_input_ptr == NULL) {
            JAVA_LOG(DDWAF_LOG_WARN, "Both persistent and ephemeral data are null");
            _throw_pwaf_exception(env, DDWAF_ERR_INVALID_ARGUMENT);
            goto end;
        }
    } else {
        struct timespec conv_end;
        persistent_input = _convert_checked(env, persistent_data, &limits, 0);
        jthrowable thr = JNI(ExceptionOccurred);
        if (thr) {
            ddwaf_object_free(&persistent_input);
            JAVA_LOG_THR(DDWAF_LOG_INFO, thr,
                         "Exception encoding parameters");
            java_wrap_exc("%s", "Exception encoding parameters");
            JNI(DeleteLocalRef, thr);
            goto err;
        }
        if (persistent_input.type != DDWAF_OBJ_NULL) {
            persistent_input_ptr = &persistent_input;
        }

        ephemeral_input = _convert_checked(env, ephemeral_data, &limits, 0);
        thr = JNI(ExceptionOccurred);
        if (thr) {
            JAVA_LOG_THR(DDWAF_LOG_INFO, thr,
                         "Exception encoding parameters");
            java_wrap_exc("%s", "Exception encoding parameters");
            JNI(DeleteLocalRef, thr);
            goto err;
        }
        if (ephemeral_input.type != DDWAF_OBJ_NULL) {
            ephemeral_input_ptr = &ephemeral_input;
        }

        if (!_get_time_checked(env, &conv_end)) {
            goto err;
        }
        rem_gen_budget_in_us = get_remaining_budget(start, conv_end, &limits);
        if (rem_gen_budget_in_us == 0) {
            JAVA_LOG(DDWAF_LOG_INFO,
                     "General budget of %" PRId64 " us exhausted after "
                     "native conversion",
                     limits.general_budget_in_us);
            _throw_pwaf_timeout_exception(env);
            goto err;
        }
    }

    size_t run_budget = get_run_budget(rem_gen_budget_in_us, &limits);

    ctx = ddwaf_context_init(pwhandle);
    if (!ctx) {
        JAVA_LOG(DDWAF_LOG_WARN, "Call to ddwaf_context_init failed");
        _throw_pwaf_exception(env, DDWAF_ERR_INTERNAL);
        goto end;
    }

    DDWAF_RET_CODE ret_code = ddwaf_run(ctx, persistent_input_ptr, ephemeral_input_ptr, &ret, run_budget);

    if (log_level_enabled(DDWAF_LOG_DEBUG)) {
            JAVA_LOG(DDWAF_LOG_DEBUG,
                     "ddwaf_run ran in %" PRId64 " microseconds. "
                     "Result code: %d",
                     ret.total_runtime, ret_code);
    }

    if (ret.timeout) {
        _throw_pwaf_timeout_exception(env);
        goto freeRet;
    }

    switch (ret_code) {
        case DDWAF_OK:
        case DDWAF_MATCH: {
            result = _create_result_checked(env, ret_code, &ret);
            goto freeRet;
        }
        case DDWAF_ERR_INTERNAL: {
            JAVA_LOG(DDWAF_LOG_ERROR, "libddwaf returned DDWAF_ERR_INTERNAL. "
                "Data may have leaked");
            _throw_pwaf_exception(env, DDWAF_ERR_INTERNAL);
            goto freeRet;
        }
        case DDWAF_ERR_INVALID_ARGUMENT:
            // break intentionally missing
        default: {
            // any errors or unknown statuses
            _throw_pwaf_exception(env, (jint) ret_code);
            goto freeRet;
        }
    }

freeRet:
    _update_metrics(env, metrics_obj, is_byte_buffer, &ret, start);
    ddwaf_result_free(&ret);
end:
    if (ctx) {
        ddwaf_context_destroy(ctx);
    }

    return result;
err:
    if (ctx) {
        ddwaf_context_destroy(ctx);
    }
    if (!is_byte_buffer) {
        ddwaf_object_free(&persistent_input);
        ddwaf_object_free(&ephemeral_input);
    }
    return NULL;
}

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    runRules
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;Ljava/util/Map;Lio/sqreen/powerwaf/Powerwaf$Limits;)Lio/sqreen/powerwaf/Powerwaf$ResultWithData;
 */
JNIEXPORT jobject JNICALL
Java_io_sqreen_powerwaf_Powerwaf_runRules__Lio_sqreen_powerwaf_PowerwafHandle_2Ljava_util_Map_2Ljava_util_Map_2Lio_sqreen_powerwaf_Powerwaf_00024Limits_2Lio_sqreen_powerwaf_PowerwafMetrics_2(
        JNIEnv *env, jclass clazz, jobject handle_obj, jobject persistent_data, jobject ephemeral_data,
        jobject limits_obj, jobject metrics_obj)
{
    return _run_rule_common(false, env, clazz, handle_obj, persistent_data, ephemeral_data,
                            limits_obj, metrics_obj);
}

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    runRules
 * Signature: (Lio/sqreen/powerwaf/PowerWAFHandle;Ljava/nio/ByteBuffer;Lio/sqreen/powerwaf/Powerwaf/Limits;)Lio/sqreen/powerwaf/Powerwaf/ResultWithData;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Powerwaf_runRules__Lio_sqreen_powerwaf_PowerwafHandle_2Ljava_nio_ByteBuffer_2Ljava_nio_ByteBuffer_2Lio_sqreen_powerwaf_Powerwaf_00024Limits_2Lio_sqreen_powerwaf_PowerwafMetrics_2
  (JNIEnv *env, jclass clazz, jobject handle_obj, jobject persistent_buffer, jobject ephemeral_buffer, jobject limits_obj, jobject metrics_obj)
{
    return _run_rule_common(true, env, clazz, handle_obj, persistent_buffer, ephemeral_buffer,
                            limits_obj, metrics_obj);
}

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    update
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;Ljava/util/Map;[Lio/sqreen/powerwaf/RuleSetInfo;)Lio/sqreen/powerwaf/PowerwafHandle;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Powerwaf_update
  (JNIEnv *env, jclass clazz, jobject jhandle, jobject jspec, jobjectArray jrsi_arr)
{
    UNUSED(clazz);

    return _ddwaf_init_or_update_checked(env, &(struct _init_or_update){
        .is_update = true,
        .jold_handle = jhandle,
        .jspec = jspec,
        .jrsi_arr = jrsi_arr,
    });
}

/*
 * Class:     io.sqreen.powerwaf.Powerwaf
 * Method:    getVersion
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_io_sqreen_powerwaf_Powerwaf_getVersion(
        JNIEnv *env, jclass clazz)
{
    UNUSED(env);
    UNUSED(clazz);

    const char *version = ddwaf_get_version();
    jstring ret = java_utf8_to_jstring_checked(
                env, version, strlen(version));
    return ret;
}

/*
 * Class:     io.sqreen.powerwaf.Additive
 * Method:    initAdditive
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;)J
 */
JNIEXPORT jlong JNICALL Java_io_sqreen_powerwaf_Additive_initAdditive(
        JNIEnv *env, jclass clazz, jobject handle_obj)
{
    UNUSED(clazz);

    ddwaf_handle nat_handle;
    if (!(nat_handle = get_pwaf_handle_checked(env, handle_obj))) {
        return 0L;
    }

    ddwaf_context context = ddwaf_context_init(nat_handle);
    if (!context) {
        JNI(ThrowNew, jcls_rte, "ddwaf_context_init failed");
        return 0L;
    }

    return (jlong) (intptr_t) context;
}

static jobject _run_additive_common(JNIEnv *env, jobject this,
                                    jobject persistent_data, jobject ephemeral_data,
                                    jobject limits_obj, jobject metrics_obj,
                                    const bool is_byte_buffer)
{
    jobject result = NULL;
    ddwaf_context context = NULL;
    ddwaf_object persistent_input = _pwinput_invalid;
    ddwaf_object ephemeral_input = _pwinput_invalid;

    ddwaf_object * persistent_input_ptr = NULL;
    ddwaf_object * ephemeral_input_ptr = NULL;

    struct _limits limits;
    ddwaf_result ret;
    struct timespec start;

    if (!_get_time_checked(env, &start)) {
        return NULL;
    }

    if (!_check_init(env)) {
        return NULL;
    }

    if (limits_obj == NULL) {
        JNI(ThrowNew, jcls_iae, "limits should not be null");
        return NULL;
    }

    limits = _fetch_limits_checked(env, limits_obj);
    if (JNI(ExceptionCheck)) {
        return NULL;
    }

    context = _get_additive_context_checked(env, this);
    if (!context) {
        return NULL;
    }

    if (context == 0) {
        JNI(ThrowNew, jcls_rte, "The Additive has already been cleared");
        return NULL;
    }

    if (is_byte_buffer) {
        persistent_input_ptr = _convert_buffer_checked(env, persistent_data);
        jthrowable thr = JNI(ExceptionOccurred);
        if (thr) {
            JAVA_LOG_THR(DDWAF_LOG_INFO, thr,
                         "Exception converting 'persistent' ByteBuffer into ddwaf_object");
            java_wrap_exc("%s", "Exception converting 'persistent' ByteBuffer into ddwaf_object");
            JNI(DeleteLocalRef, thr);
            return NULL;
        }

        ephemeral_input_ptr = _convert_buffer_checked(env, ephemeral_data);
        thr = JNI(ExceptionOccurred);
        if (thr) {
            JAVA_LOG_THR(DDWAF_LOG_INFO, thr,
                         "Exception converting 'ephemeral' ByteBuffer into ddwaf_object");
            java_wrap_exc("%s", "Exception converting 'ephemeral' ByteBuffer into ddwaf_object");
            JNI(DeleteLocalRef, thr);
            return NULL;
        }

        if (persistent_input_ptr == NULL && ephemeral_input_ptr == NULL) {
            JAVA_LOG(DDWAF_LOG_WARN, "Both persistent and ephemeral data are null");
            _throw_pwaf_exception(env, DDWAF_ERR_INVALID_ARGUMENT);
            return NULL;
        }
    } else {
        persistent_input = _convert_checked(env, persistent_data, &limits, 0);
        jthrowable thr = JNI(ExceptionOccurred);
        if (thr) {
            ddwaf_object_free(&persistent_input);
            JAVA_LOG_THR(DDWAF_LOG_WARN, thr,
                         "Exception encoding parameters");
            java_wrap_exc("%s", "Exception encoding parameters");
            JNI(DeleteLocalRef, thr);
            return NULL;
        }
        if (persistent_input.type != DDWAF_OBJ_NULL) {
            persistent_input_ptr = &persistent_input;
        }

        ephemeral_input = _convert_checked(env, ephemeral_data, &limits, 0);
        thr = JNI(ExceptionOccurred);
        if (thr) {
            ddwaf_object_free(&persistent_input);
            ddwaf_object_free(&ephemeral_input);
            JAVA_LOG_THR(DDWAF_LOG_WARN, thr,
                         "Exception encoding parameters");
            java_wrap_exc("%s", "Exception encoding parameters");
            JNI(DeleteLocalRef, thr);
            return NULL;
        }
        if (ephemeral_input.type != DDWAF_OBJ_NULL) {
            ephemeral_input_ptr = &ephemeral_input;
        }
    }

    struct timespec conv_end;
    if (!_get_time_checked(env, &conv_end)) {
        goto err;
    }

    int64_t rem_gen_budget_in_us =
            get_remaining_budget(start, conv_end, &limits);
    if (rem_gen_budget_in_us == 0) {
        JAVA_LOG(DDWAF_LOG_INFO,
                 "General budget of %" PRId64
                 " us exhausted after native conversion",
                 limits.general_budget_in_us);
        _throw_pwaf_timeout_exception(env);
        goto err;
    }

    size_t run_budget = get_run_budget(rem_gen_budget_in_us, &limits);

    DDWAF_RET_CODE ret_code =
            ddwaf_run(context, persistent_input_ptr, ephemeral_input_ptr, &ret, run_budget);

    if (ret.timeout) {
        _throw_pwaf_timeout_exception(env);
        goto freeRet;
    }

    switch (ret_code) {
        case DDWAF_OK:
        case DDWAF_MATCH:
            result = _create_result_checked(env, ret_code, &ret);
            break;
        case DDWAF_ERR_INTERNAL: {
            JAVA_LOG(DDWAF_LOG_ERROR, "libddwaf returned DDWAF_ERR_INTERNAL. "
                "Data may have leaked");
            _throw_pwaf_exception(env, DDWAF_ERR_INTERNAL);
            goto freeRet;
        }
        case DDWAF_ERR_INVALID_ARGUMENT:
            if (!is_byte_buffer) {
                ddwaf_object_free(&persistent_input);
                ddwaf_object_free(&ephemeral_input);
            }
            // break intentionally missing
        default: {
            // any errors or unknown statuses
            _throw_pwaf_exception(env, (jint) ret_code);
            goto freeRet;
        }
    }

freeRet:
    _update_metrics(env, metrics_obj, is_byte_buffer, &ret, start);
    ddwaf_result_free(&ret);

    return result;

err:
    if (!is_byte_buffer) {
        ddwaf_object_free(&persistent_input);
        ddwaf_object_free(&ephemeral_input);
    }
    return NULL;
}

/*
 * Class:     io_sqreen_powerwaf_Additive
 * Method:    runAdditive
 * Signature:
 * (Ljava/util/Map;Lio/sqreen/powerwaf/Powerwaf$Limits;)Lio/sqreen/powerwaf/Powerwaf$ResultWithData;
 */
JNIEXPORT jobject JNICALL
Java_io_sqreen_powerwaf_Additive_runAdditive__Ljava_util_Map_2Ljava_util_Map_2Lio_sqreen_powerwaf_Powerwaf_00024Limits_2Lio_sqreen_powerwaf_PowerwafMetrics_2(
        JNIEnv *env, jobject this, jobject persistent_data, jobject ephemeral_data, jobject limits_obj,
        jobject metrics_obj)
{
    return _run_additive_common(env, this, persistent_data, ephemeral_data, limits_obj, metrics_obj,
                                false);
}

/*
 * Class:     io_sqreen_powerwaf_Additive
 * Method:    runAdditive
 * Signature:
 * (Ljava/nio/ByteBuffer;Lio/sqreen/powerwaf/Powerwaf$Limits;)Lio/sqreen/powerwaf/Powerwaf$ResultWithData;
 */
JNIEXPORT jobject JNICALL
Java_io_sqreen_powerwaf_Additive_runAdditive__Ljava_nio_ByteBuffer_2Ljava_nio_ByteBuffer_2Lio_sqreen_powerwaf_Powerwaf_00024Limits_2Lio_sqreen_powerwaf_PowerwafMetrics_2(
        JNIEnv *env, jobject this, jobject persistent_buffer, jobject ephemeral_buffer, jobject limits_obj,
        jobject metrics_obj)
{
    return _run_additive_common(env, this, persistent_buffer, ephemeral_buffer, limits_obj, metrics_obj, true);
}

/*
 * Class:     io.sqreen.powerwaf.Additive
 * Method:    clearAdditive
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_Additive_clearAdditive
  (JNIEnv *env, jobject this) {

    ddwaf_context context = _get_additive_context_checked(env, this);
    if (!context) {
        return;
    }

    ddwaf_context_destroy(context);

    _set_additive_context_checked(env, this, NULL);
}

static bool _check_init(JNIEnv *env)
{
    if (!_init_ok) {
        JNI(ThrowNew, jcls_rte, "Library has not been properly initialized");
        return false;
    }
    return true;
}

static void _deinitialize(JNIEnv *env)
{
    // ensure no race between Powerwaf::deinitialize() and JNI's onUnload
    bool inited = COMPARE_AND_SWAP(&_init_ok, true, false);
    if (!inited) {
        return;
    }

    JAVA_LOG(DDWAF_LOG_DEBUG, "Deinitializing JNI library");
    _dispose_of_cache_references(env);

    // do not delete reference to jcls_rte, as _check_init uses it
//    if (jcls_rte) {
//        JNI(DeleteGlobalRef, jcls_rte);
//        jcls_rte = NULL;
//    }

    output_shutdown(env);

    ddwaf_set_log_cb(NULL, DDWAF_LOG_ERROR);

    java_log_shutdown(env);
}

#define RESULT_ENUM_DESCR "Lio/sqreen/powerwaf/Powerwaf$Result;"
static bool _fetch_action_enums(JNIEnv *env)
{
    bool ret = false;

    jclass action_jclass = JNI(FindClass, "io/sqreen/powerwaf/Powerwaf$Result");
    if (!action_jclass) {
        goto error;
    }

    _action_ok = java_static_field_checked(env, action_jclass, "OK",
                                           RESULT_ENUM_DESCR);
    if (!_action_ok) {
        goto error;
    }
    _action_match = java_static_field_checked(env, action_jclass, "MATCH",
                                              RESULT_ENUM_DESCR);
    if (!_action_match) {
        goto error;
    }

    ret = true;

error:
    JNI(DeleteLocalRef, action_jclass);
    if (!ret) {
        _dispose_of_action_enums(env);
    }
    return ret;
}

#define RESULT_WITH_DATA_DESCR "Lio/sqreen/powerwaf/Powerwaf$ResultWithData;"
static bool _fetch_result_with_data_fields(JNIEnv *env)
{
    bool ret = false;

    jclass result_with_data_jclass =
            JNI(FindClass, "io/sqreen/powerwaf/Powerwaf$ResultWithData");
    if (!result_with_data_jclass) {
        goto error;
    }

    _result_with_data_ok_null = java_static_field_checked(
            env, result_with_data_jclass, "OK_NULL", RESULT_WITH_DATA_DESCR);
    if (!_result_with_data_ok_null) {
        goto error;
    }

    _result_with_data_empty_str_arr =
            java_static_field_checked(env, result_with_data_jclass,
                                      "EMPTY_ACTIONS", "[Ljava/lang/String;");
    if (!_result_with_data_empty_str_arr) {
        goto error;
    }

    ret = true;

error:
    JNI(DeleteLocalRef, result_with_data_jclass);
    if (!ret) {
        _dispose_of_result_with_data_fields(env);
    }
    return ret;
}

static bool _fetch_additive_fields(JNIEnv *env)
{
    bool ret = false;

    jclass additive_jclass = JNI(FindClass, "io/sqreen/powerwaf/Additive");
    if (!additive_jclass) {
        goto error;
    }

    _additive_ptr = JNI(GetFieldID, additive_jclass, "ptr", "J");
    if (!_additive_ptr) {
        goto error;
    }

    ret = true;
    error:
    JNI(DeleteLocalRef, additive_jclass);
    return ret;
}

static bool _fetch_limit_fields(JNIEnv *env)
{
    bool ret = false;

    jclass limits_jclass = JNI(FindClass, "io/sqreen/powerwaf/Powerwaf$Limits");
    if (!limits_jclass) {
        goto error;
    }

    _limit_max_depth = JNI(GetFieldID, limits_jclass, "maxDepth", "I");
    if (!_limit_max_depth) {
        goto error;
    }
    _limit_max_elements = JNI(GetFieldID, limits_jclass, "maxElements", "I");
    if (!_limit_max_elements) {
        goto error;
    }
    _limit_max_string_size = JNI(GetFieldID, limits_jclass, "maxStringSize", "I");
    if (!_limit_max_string_size) {
        goto error;
    }
    _limit_general_budget_in_us =
            JNI(GetFieldID, limits_jclass, "generalBudgetInUs", "J");
    if (!_limit_general_budget_in_us) {
        goto error;
    }
    _limit_run_budget_in_us =
            JNI(GetFieldID, limits_jclass, "runBudgetInUs", "J");
    if (!_limit_run_budget_in_us) {
        goto error;
    }

    ret = true;
error:
    JNI(DeleteLocalRef, limits_jclass);
    return ret;
}

static bool _fetch_config_fields(JNIEnv *env)
{
    bool ret = false;

    jclass config_jclass = JNI(FindClass, "io/sqreen/powerwaf/PowerwafConfig");
    if (!config_jclass) {
        goto error;
    }

    _config_key_regex = JNI(GetFieldID, config_jclass, "obfuscatorKeyRegex",
                           "Ljava/lang/String;");
    if (!_config_key_regex) {
        goto error;
    }
    _config_value_regex = JNI(GetFieldID, config_jclass, "obfuscatorValueRegex",
                           "Ljava/lang/String;");
    if (!_config_value_regex) {
        goto error;
    }

    _config_use_byte_buffers =
            JNI(GetFieldID, config_jclass, "useByteBuffers", "Z");
    if (!_config_use_byte_buffers) {
        goto error;
    }

    ret = true;
error:
    JNI(DeleteLocalRef, config_jclass);
    return ret;
}

static bool _fetch_native_handle_field(JNIEnv *env)
{
    jclass cls = JNI(FindClass, "io/sqreen/powerwaf/PowerwafHandle");
    if (!cls) {
        java_wrap_exc("Could not find class io.sqreen.powerwaf.PowerWAFHandle");
        return false;
    }

    _pwaf_handle_native_handle = JNI(GetFieldID, cls, "nativeHandle", "J");
    bool ret = _pwaf_handle_native_handle != 0;

    JNI(DeleteLocalRef, cls);
    return ret;
}

static bool _fetch_native_order_obj(JNIEnv *env) {
    struct j_method meth;
    if (!java_meth_init_checked(env, &meth, "java/nio/ByteOrder", "nativeOrder",
                                "()Ljava/nio/ByteOrder;", JMETHOD_STATIC)) {
        return false;
    }

    jobject nat_ord_obj = java_meth_call(env, &meth, NULL);
    java_meth_destroy(env, &meth);
    if (JNI(ExceptionCheck)) {
        return false;
    }

    _native_order = JNI(NewWeakGlobalRef, nat_ord_obj);

    JNI(DeleteLocalRef, nat_ord_obj);
    return true;
}

static void _dispose_of_action_enums(JNIEnv *env)
{
    if (_action_ok) {
        JNI(DeleteWeakGlobalRef, _action_ok);
        _action_ok = NULL;
    }
    if (_action_match) {
        JNI(DeleteWeakGlobalRef, _action_match);
        _action_match = NULL;
    }
}

static void _dispose_of_result_with_data_fields(JNIEnv *env)
{
    if (_result_with_data_ok_null) {
        JNI(DeleteWeakGlobalRef, _result_with_data_ok_null);
        _result_with_data_ok_null = NULL;
    }
    if (_result_with_data_empty_str_arr) {
        JNI(DeleteWeakGlobalRef, _result_with_data_empty_str_arr);
        _result_with_data_empty_str_arr = NULL;
    }
}

static bool _cache_single_class_weak(JNIEnv *env,
                                const char *class_name,
                                jclass *out)
{
    jclass cls_local = JNI(FindClass, class_name);
    if (!cls_local) {
        java_wrap_exc("Could not find class %s", class_name);
        return false;
    }

    *out = JNI(NewWeakGlobalRef, cls_local);
    JNI(DeleteLocalRef, cls_local);

    if (!*out) {
        // out of memory
        return false;
    }

    return true;
}

static bool _cache_classes(JNIEnv *env)
{
  return _cache_single_class_weak(env, "java/lang/RuntimeException",
                                  &jcls_rte) &&
         _cache_single_class_weak(env, "java/lang/IllegalArgumentException",
                                  &jcls_iae) &&
         _cache_single_class_weak(env, "java/lang/CharSequence",
                                  &charSequence_cls) &&
         _cache_single_class_weak(env, "java/nio/Buffer",
                                  &buffer_cls) &&
         _cache_single_class_weak(env, "java/nio/CharBuffer",
                                  &charBuffer_cls) &&
         _cache_single_class_weak(env, "java/lang/String", &string_cls) &&
         _cache_single_class_weak(env, "java/lang/Double", &double_cls) &&
         _cache_single_class_weak(env, "java/lang/Float", &float_cls) &&
         _cache_single_class_weak(env, "java/math/BigDecimal", &bigdecimal_cls);
}

static void _dispose_of_weak_classes(JNIEnv *env)
{
#define DESTROY_CLASS_REF(jcls)                                                \
    if (jcls) {                                                                \
        JNI(DeleteWeakGlobalRef, jcls);                                        \
        jcls = NULL;                                                           \
    }

    DESTROY_CLASS_REF(jcls_iae)
    DESTROY_CLASS_REF(charSequence_cls)
    DESTROY_CLASS_REF(buffer_cls)
    DESTROY_CLASS_REF(charBuffer_cls)
    DESTROY_CLASS_REF(string_cls)
    DESTROY_CLASS_REF(double_cls)
    DESTROY_CLASS_REF(float_cls)
    DESTROY_CLASS_REF(bigdecimal_cls)
    // leave jcls_rte for last in OnUnload; we might still need it
}

static void _dispose_of_native_order_obj(JNIEnv *env)
{
    if (_native_order) {
        JNI(DeleteWeakGlobalRef, _native_order);
        _native_order = NULL;
    }
}

static bool _cache_methods(JNIEnv *env)
{
    if (!java_meth_init_checked(
                env, &_create_exception, "io/sqreen/powerwaf/Powerwaf",
                "createException",
                "(I)Lio/sqreen/powerwaf/exception/AbstractPowerwafException;",
                JMETHOD_STATIC)) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &_timeout_exception_init,
                "io/sqreen/powerwaf/exception/TimeoutPowerwafException",
                "<init>", "()V", JMETHOD_CONSTRUCTOR)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &to_string, "java/lang/Object", "toString",
                                "()Ljava/lang/String;", JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &charSequence_length, "java/lang/CharSequence", "length",
                                "()I", JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &charSequence_subSequence, "java/lang/CharSequence", "subSequence",
                                "(II)Ljava/lang/CharSequence;", JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &buffer_position, "java/nio/Buffer", "position",
                                "()I", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &buffer_limit, "java/nio/Buffer", "limit",
                                "()I", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &charBuffer_hasArray, "java/nio/CharBuffer", "hasArray",
                                "()Z", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &charBuffer_array, "java/nio/CharBuffer", "array",
                                "()[C", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &_charBuffer_order, "java/nio/CharBuffer",
                                "order", "()Ljava/nio/ByteOrder;",
                                JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &number_longValue,
                "java/lang/Number", "longValue",
                "()J",
                JMETHOD_VIRTUAL_RETRIEVE_CLASS)) {
        goto error;
    }
    if (!java_meth_init_checked(
                env, &number_doubleValue,
                "java/lang/Number", "doubleValue",
                "()D",
                JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &_boolean_booleanValue,
                                "java/lang/Boolean", "booleanValue", "()Z",
                                JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &result_with_data_init,
                "io/sqreen/powerwaf/Powerwaf$ResultWithData", "<init>",
                "(Lio/sqreen/powerwaf/Powerwaf$Result;Ljava/lang/String;"
                "[Ljava/lang/String;Ljava/util/Map;)V",
                JMETHOD_CONSTRUCTOR)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &_pwaf_handle_init,
                                "io/sqreen/powerwaf/PowerwafHandle", "<init>",
                                "(J)V", JMETHOD_CONSTRUCTOR)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &map_entryset, "java/util/Map", "entrySet",
                "()Ljava/util/Set;", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }
    map_entryset.type = JMETHOD_VIRTUAL;

    if (!java_meth_init_checked(env, &map_size, "java/util/Map", "size",
                                "()I", JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &entry_key, "java/util/Map$Entry",
                                "getKey", "()Ljava/lang/Object;",
                                JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &entry_value, "java/util/Map$Entry",
                                "getValue", "()Ljava/lang/Object;",
                                JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &iterable_iterator,
                "java/lang/Iterable", "iterator",
                "()Ljava/util/Iterator;", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }
    iterable_iterator.type = JMETHOD_VIRTUAL;

    if (!java_meth_init_checked(
                env, &iterator_next,
                "java/util/Iterator", "next",
                "()Ljava/lang/Object;", JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &iterator_hasNext,
                "java/util/Iterator", "hasNext",
                "()Z", JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(
            env, &class_is_array,
            "java/lang/Class", "isArray",
            "()Z",
            JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &_class_get_name, "java/lang/Class",
                                "getName", "()Ljava/lang/String;",
                                JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    return true;
error:
    return false;
}

static void _dispose_of_cached_methods(JNIEnv *env)
{
#define DESTROY_METH(var) \
    if ((var).type != JMETHOD_UNINITIALIZED) { \
        java_meth_destroy(env, &(var)); \
    }

    DESTROY_METH(_create_exception)
    DESTROY_METH(_timeout_exception_init)
    DESTROY_METH(charSequence_length)
    DESTROY_METH(charSequence_subSequence)
    DESTROY_METH(charBuffer_hasArray)
    DESTROY_METH(charBuffer_array)
    DESTROY_METH(_charBuffer_order)
    DESTROY_METH(buffer_position)
    DESTROY_METH(buffer_limit)
    DESTROY_METH(to_string)
    DESTROY_METH(number_longValue)
    DESTROY_METH(_boolean_booleanValue)
    DESTROY_METH(result_with_data_init)
    DESTROY_METH(_pwaf_handle_init)
    DESTROY_METH(map_entryset)
    DESTROY_METH(map_size)
    DESTROY_METH(entry_key)
    DESTROY_METH(entry_value)
    DESTROY_METH(iterable_iterator)
    DESTROY_METH(iterator_next)
    DESTROY_METH(iterator_hasNext)
    DESTROY_METH(class_is_array)
    DESTROY_METH(_class_get_name)
}


static bool _cache_references(JNIEnv *env)
{
    if (!_cache_classes(env)) {
        goto error;
    }

    rte_constr_cause = JNI(GetMethodID, jcls_rte, "<init>",
        "(Ljava/lang/String;Ljava/lang/Throwable;)V");
    if (!rte_constr_cause) {
        goto error;
    }

    if (!_fetch_action_enums(env)) {
        goto error;
    }

    if (!_fetch_result_with_data_fields(env)) {
        goto error;
    }

    if (!_fetch_additive_fields(env)) {
        goto error;
    }

    if (!_fetch_limit_fields(env)) {
        goto error;
    }

    if (!_fetch_config_fields(env)) {
        goto error;
    }

    if (!_fetch_native_handle_field(env)) {
        goto error;
    }

    if (!_fetch_native_order_obj(env)) {
        goto error;
    }

    if (!_cache_methods(env)) {
        goto error;
    }

    return true;
error:
    _dispose_of_cache_references(env);
    return false;
}

static void _dispose_of_cache_references(JNIEnv * env)
{
    _dispose_of_action_enums(env);
    _dispose_of_result_with_data_fields(env);
    _dispose_of_weak_classes(env);
    _dispose_of_native_order_obj(env);
    _dispose_of_cached_methods(env);
}

static ddwaf_object _convert_checked(JNIEnv *env, jobject obj,
                                     struct _limits *lims, int rec_level)
{
#define RET_IF_EXC() do { if (JNI(ExceptionCheck)) { goto error; } } while (0)
#define JAVA_CALL(var, meth, recv) \
    do { \
        var = java_meth_call(env, &(meth), (recv)); \
        RET_IF_EXC(); \
    } while (0)
#define JAVA_CALL_ERR_MSG(var, meth, recv, err_msg, ...) \
    do { \
        var = java_meth_call(env, &(meth), (recv)); \
        if (JNI(ExceptionCheck)) { \
            java_wrap_exc(err_msg, ##__VA_ARGS__); \
            goto error; \
        } \
    } while (0)

    lims->max_elements--;

    /* this function can only fail in two situations:
     * 1) maximum depth exceeded or
     * 2) a java method call or another JNI calls throws.
     *
     * It particular, it doesn't "fail" if any of the powerwaf_* functions
     * fail. It never checks their return values. The assumption is that
     * any failure will be an extraordinary circumstance and that the
     * implementation is designed in such a way that passing NULL pointers
     * or PWI_INVALID objects doesn't cause crashes */

    if (rec_level > lims->max_depth) {
        JNI(ThrowNew, jcls_rte, "Maximum recursion level exceeded");
        goto error;
    }

    ddwaf_object result = _pwinput_invalid;

    jboolean is_array = JNI_FALSE;
    if (obj != NULL) {
        jclass clazz = JNI(GetObjectClass, obj);
        is_array = JNI(CallBooleanMethod, clazz, class_is_array.meth_id);
        if (JNI(ExceptionCheck)) {
            goto error;
        }
        JNI(DeleteLocalRef, clazz);
    }

    if (JNI(IsSameObject, obj, NULL)) {
        ddwaf_object_null(&result); // can't fail
    } else if (is_array == JNI_TRUE) {
        ddwaf_object_array(&result); // can't fail

        if (rec_level >= lims->max_depth) {
            JAVA_LOG(DDWAF_LOG_INFO,
                     "Leaving array empty because max depth of %d "
                     "has been reached",
                     lims->max_depth);
            goto early_return;
        }

        jsize len = JNI(GetArrayLength, obj);

        for (int i=0; i<len; i++) {

            if (lims->max_elements <= 0) {
                JAVA_LOG(DDWAF_LOG_INFO, "Interrupting iterating array due to "
                                    "the max of elements being reached");
                break;
            }

            jobject element = JNI(GetObjectArrayElement, obj, i);

            ddwaf_object value =
                    _convert_checked(env, element, lims, rec_level + 1);
            if (JNI(ExceptionCheck)) {
                goto error;
            }
            bool success = ddwaf_object_array_add(&result, &value);
            JNI(DeleteLocalRef, element);
            if (!success) {
                JNI(ThrowNew, jcls_rte, "ddwaf_object_array_add failed (OOM?)");
                goto error;
            }
        }
    } else if (JNI(IsInstanceOf, obj, *map_cls)) {
        ddwaf_object_map(&result); // can't fail
        if (rec_level >= lims->max_depth) {
            JAVA_LOG(DDWAF_LOG_DEBUG,
                     "Leaving map empty because max depth of %d "
                     "has been reached",
                     lims->max_depth);
            goto early_return;
        }

        jobject entry_set, entry_set_it;
        JAVA_CALL(entry_set, map_entryset, obj);
        JAVA_CALL(entry_set_it, iterable_iterator, entry_set);

        while (JNI(CallBooleanMethod, entry_set_it, iterator_hasNext.meth_id)) {
            if (JNI(ExceptionCheck)) {
                goto error;
            }
            if (lims->max_elements <= 0) {
                JAVA_LOG(DDWAF_LOG_DEBUG,
                         "Interrupting map iteration due to the max "
                         "number of elements being reached");
                break;
            }

            jobject entry, key_obj, value_obj;
            jstring key_jstr;

            JAVA_CALL(entry, iterator_next, entry_set_it);
            JAVA_CALL(key_obj, entry_key, entry);
            JAVA_CALL_ERR_MSG(key_jstr, to_string, key_obj,
                              "Error calling toString() on map key");
            JAVA_CALL(value_obj, entry_value, entry);

            JNI(DeleteLocalRef, key_obj);
            JNI(DeleteLocalRef, entry);

            ddwaf_object value =
                    _convert_checked(env, value_obj, lims, rec_level + 1);
            if (JNI(ExceptionCheck)) {
                goto error;
            }

            size_t key_len;
            char *key_cstr = java_to_utf8_limited_checked(
                    env, key_jstr, &key_len, lims->max_string_size);
            if (!key_cstr) {
                goto error;
            }

            bool success =
                    ddwaf_object_map_addl(&result, key_cstr, key_len, &value);
            free(key_cstr);

            /* doesn't matter if these leak in case of error
             * we do need to delete them in normal circumstances because we're
             * on a loop and we don't want to run out of local refs */
            JNI(DeleteLocalRef, value_obj);
            JNI(DeleteLocalRef, key_jstr);
            if (!success) {
                JNI(ThrowNew, jcls_rte, "ddwaf_object_map_add failed (OOM?)");
                goto error;
            }
        }

        JNI(DeleteLocalRef, entry_set_it);
        JNI(DeleteLocalRef, entry_set);

    } else if (JNI(IsInstanceOf, obj, *iterable_cls)) {
        ddwaf_object_array(&result);
        if (rec_level >= lims->max_depth) {
            JAVA_LOG(DDWAF_LOG_DEBUG,
                     "Leaving array empty because max depth of %d "
                     "has been reached",
                     lims->max_depth);
            goto early_return;
        }

        jobject it;
        JAVA_CALL(it, iterable_iterator, obj);
        while (JNI(CallBooleanMethod, it, iterator_hasNext.meth_id)) {
            if (JNI(ExceptionCheck)) {
                goto error;
            }
            if (lims->max_elements <= 0) {
                JAVA_LOG(DDWAF_LOG_DEBUG,
                         "Interrupting iterable iteration due to "
                         "the max of elements being reached");
                break;
            }

            jobject element;
            JAVA_CALL(element, iterator_next, it);

            ddwaf_object value =
                    _convert_checked(env, element, lims, rec_level + 1);
            if (JNI(ExceptionCheck)) {
                goto error;
            }

            bool success = ddwaf_object_array_add(&result, &value);
            JNI(DeleteLocalRef, element);
            if (!success) {
                JNI(ThrowNew, jcls_rte, "ddwaf_object_array_add failed (OOM?)");
                goto error;
            }
        }

        JNI(DeleteLocalRef, it);

    } else if (JNI(IsInstanceOf, obj, string_cls)) {
        size_t len;
        char *str_c = java_to_utf8_limited_checked(env, obj, &len,
                                                   lims->max_string_size);
        if (!str_c) {
            goto error;
        }

        bool success = !!ddwaf_object_stringl(&result, str_c, len);
        free(str_c);
        if (!success) {
            JNI(ThrowNew, jcls_rte, "ddwaf_object_stringl failed (OOM?)");
            goto error;
        }

    } else if (JNI(IsInstanceOf, obj, charSequence_cls)) {
        int utf16_len;
        const int max_utf16_len = lims->max_string_size;

        struct char_buffer_info cbi;
        bool has_nat_arr = _get_char_buffer_data(env, obj, &cbi);
        if (JNI(ExceptionCheck)) {
            goto error;
        }

        if (has_nat_arr) {
            utf16_len = cbi.end - cbi.start;
            if (utf16_len > max_utf16_len) {
                utf16_len = max_utf16_len;
            }

            uint8_t *utf8_out;
            size_t utf8_len;
            java_utf16_to_utf8_checked(env, cbi.nat_array + cbi.start,
                                       utf16_len, &utf8_out, &utf8_len);

            if (cbi.call_release) {
                JNI(ReleaseCharArrayElements, cbi.javaArray, cbi.nat_array,
                    JNI_ABORT);
                JNI(DeleteLocalRef, cbi.javaArray);
            }

            if (JNI(ExceptionCheck)) {
                goto error;
            }

            bool success = !!ddwaf_object_stringl(&result, (char *) utf8_out,
                                                  utf8_len);
            free(utf8_out);
            if (!success) {
                JNI(ThrowNew, jcls_rte, "ddwaf_object_stringl failed (OOM?)");
                goto error;
            }
        } else { // regular char sequence or non-direct CharBuffer w/out array
            // Try to read data from CharSequence
            utf16_len = JNI(CallIntMethod, obj, charSequence_length.meth_id);
            if (JNI(ExceptionCheck)) {
                goto error;
            }

            bool delete_obj = false;
            if (utf16_len > max_utf16_len) {
                jobject new_obj = java_meth_call(env, &charSequence_subSequence,
                                                 obj, 0, max_utf16_len);
                if (JNI(ExceptionCheck)) {
                    goto error;
                }
                obj = new_obj;
                delete_obj = true;
            }

            jstring str = java_meth_call(env, &to_string, obj);
            if (delete_obj) {
                JNI(DeleteLocalRef, obj);
            }
            if (JNI(ExceptionCheck)) {
                goto error;
            }

            size_t utf8_len;
            char *utf8_out = java_to_utf8_checked(env, str, &utf8_len);
            if (!utf8_out) {
                goto error;
            }

            bool success = !!ddwaf_object_stringl(&result, utf8_out, utf8_len);
            free(utf8_out);
            if (!success) {
                JNI(ThrowNew, jcls_rte, "ddwaf_object_stringl failed (OOM?)");
                goto error;
            }
        }
    } else if (JNI(IsInstanceOf, obj, *number_cls)) {
        bool success;
        if (JNI(IsInstanceOf, obj, double_cls) ||
            JNI(IsInstanceOf, obj, float_cls) ||
            JNI(IsInstanceOf, obj, bigdecimal_cls)) {
            jdouble dval =
                    JNI(CallDoubleMethod, obj, number_doubleValue.meth_id);
            if (JNI(ExceptionCheck)) {
                goto error;
            }
            success = !!ddwaf_object_float(&result, dval);
        } else {
            jlong lval = JNI(CallLongMethod, obj, number_longValue.meth_id);
            if (JNI(ExceptionCheck)) {
                goto error;
            }
            success = !!ddwaf_object_signed(&result, lval);
        }

        if (!success) {
            JNI(ThrowNew, jcls_rte, "ddwaf_object_signed failed");
            goto error;
        }
    } else if (JNI(IsInstanceOf, obj, *_boolean_cls)) {
        jboolean bval = JNI(CallNonvirtualBooleanMethod, obj,
                            _boolean_booleanValue.class_glob,
                            _boolean_booleanValue.meth_id);
        if (JNI(ExceptionCheck)) {
            goto error;
        }

        if (!ddwaf_object_bool(&result, (bool) bval)) {
            JNI(ThrowNew, jcls_rte, "ddwaf_object_bool failed (OOM?)");
            goto error;
        }
    } else if (log_level_enabled(DDWAF_LOG_DEBUG)) {
        jclass cls = JNI(GetObjectClass, obj);
        jobject name = java_meth_call(env, &_class_get_name, cls);
        static const char unknown[] = "<unknown class>";
        static const size_t unknown_len = sizeof(unknown) - 1;
        const char *name_c;
        size_t name_len;
        if (JNI(ExceptionCheck)) {
            JNI(ExceptionClear);
            name_c = unknown;
            name_len = unknown_len;
        } else {
            name_c = java_to_utf8_checked(env, (jstring) name, &name_len);
            if (JNI(ExceptionCheck)) {
                JNI(ExceptionClear);
                name_c = unknown;
                name_len = unknown_len;
            }
        }

        JAVA_LOG(DDWAF_LOG_DEBUG,
                 "Could not convert object of type %.*s; "
                 "encoding as invalid",
                 (int) name_len /* should be safe */, name_c);
        if (name_c != unknown) {
            free((void *) (uintptr_t) name_c);
        }
    }

    // having lael here so if we add cleanup in the future we don't forget
early_return:
    return result;
error:
    ddwaf_object_free(&result);

    return _pwinput_invalid;
}

static ddwaf_object * _convert_buffer_checked(JNIEnv *env, jobject buffer)
{
    if (buffer == NULL) {
        return NULL;
    }

    void *input_p = JNI(GetDirectBufferAddress, buffer);
    if (!input_p) {
        JNI(ThrowNew, jcls_iae, "Not a DirectBuffer passed");
        return NULL;
    }
    jlong capacity = JNI(GetDirectBufferCapacity, buffer);
    if (capacity < (jlong) sizeof(ddwaf_object)) {
        JNI(ThrowNew, jcls_iae, "Capacity of DirectBuffer is insufficient");
        return NULL;
    }
    return input_p;
}

// can return false with and without exception
static bool _get_char_buffer_data(JNIEnv *env, jobject obj,
                                   struct char_buffer_info *info)
{
    info->call_release = false;
    jboolean is_char_buffer = JNI(IsInstanceOf, obj, charBuffer_cls);
    if (!is_char_buffer) {
        return false;
    }

    jint pos = JNI(CallNonvirtualIntMethod, obj, buffer_position.class_glob,
                   buffer_position.meth_id);
    if (JNI(ExceptionCheck)) {
        return false;
    }

    jint limit = JNI(CallNonvirtualIntMethod, obj, buffer_limit.class_glob,
                     buffer_limit.meth_id);
    if (JNI(ExceptionCheck)) {
        return false;
    }

    info->start = pos;
    info->end = limit;

    jboolean has_array =
            JNI(CallNonvirtualBooleanMethod, obj,
                charBuffer_hasArray.class_glob, charBuffer_hasArray.meth_id);
    if (JNI(ExceptionCheck)) {
        return false;
    }

    if (has_array) {
        info->javaArray =
                JNI(CallNonvirtualObjectMethod, obj,
                    charBuffer_array.class_glob, charBuffer_array.meth_id);
        if (JNI(ExceptionCheck)) {
            return false;
        }

        jchar *elems = JNI(GetCharArrayElements, info->javaArray, NULL);
        if (JNI(ExceptionCheck)) {
            JNI(DeleteLocalRef, info->javaArray);
            return false;
        }

        info->nat_array = elems;
        info->call_release = true;
        return true;
    } else {
        jobject order = java_meth_call(env, &_charBuffer_order, obj);
        if (JNI(ExceptionCheck)) {
            return false;
        }

        bool has_nat_order = JNI(IsSameObject, order, _native_order);
        JNI(DeleteLocalRef, order);
        if (has_nat_order) {
            void *addr = JNI(GetDirectBufferAddress, obj);
            if (addr) {
                info->javaArray = NULL;
                info->nat_array = addr;
                info->call_release = false;
                return true;
            }
        }

        return false;
    }
}

static ddwaf_context _get_additive_context_checked(JNIEnv *env,
                                                   jobject additive_obj)
{
    ddwaf_context context = NULL;
    context = (ddwaf_context)(intptr_t) JNI(GetLongField, additive_obj,
                                            _additive_ptr);
    if (JNI(ExceptionCheck)) {
        return NULL;
    }

    if (!context) {
        JNI(ThrowNew, jcls_rte, "The Additive has already been cleared");
    }

    return context;
}

static bool _set_additive_context_checked(JNIEnv *env, jobject additive_obj,
                                          ddwaf_context ctx)
{
    jlong ctx_long;
    memcpy(&ctx_long, &ctx, sizeof ctx_long);

    JNI(SetLongField, additive_obj, _additive_ptr, ctx_long);
    if (JNI(ExceptionCheck)) {
        return false;
    }
    return true;
}

static struct _limits _fetch_limits_checked(JNIEnv *env, jobject limits_obj)
{
    struct _limits l = {0};
    l.max_depth = JNI(GetIntField, limits_obj, _limit_max_depth);
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    l.max_elements = JNI(GetIntField, limits_obj, _limit_max_elements);
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    l.max_string_size = JNI(GetIntField, limits_obj, _limit_max_string_size);
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    l.general_budget_in_us =
            JNI(GetLongField, limits_obj, _limit_general_budget_in_us);
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    jlong run_budget =
            JNI(GetLongField, limits_obj, _limit_run_budget_in_us);
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    // PW_RUN_TIMEOUT is in us
    l.run_budget_in_us = run_budget > 0
            ? (int64_t)run_budget
            : pw_run_timeout;

    return l;
error:
    return (struct _limits) {0};
}

static bool _get_time_checked(JNIEnv *env, struct timespec *time)
{
    int res = clock_gettime(CLOCK_MONOTONIC, time);
    if (res) {
        JNI(ThrowNew, jcls_rte, "Error getting time");
        return false;
    }
    return true;
}

static inline int64_t _timespec_diff_ns(struct timespec a, struct timespec b)
{
    return ((int64_t)a.tv_sec - (int64_t)b.tv_sec) * 1000000000L +
            ((int64_t)a.tv_nsec - (int64_t)b.tv_nsec);
}

static int64_t _get_pw_run_timeout_checked(JNIEnv *env)
{
    struct j_method get_prop = {0};
    jstring env_key = NULL;
    jstring val_jstr = NULL;
    char *val_cstr = NULL;
    long long val = DDWAF_RUN_TIMEOUT;

    if (!java_meth_init_checked(
                env, &get_prop, "java/lang/System", "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;", JMETHOD_STATIC)) {
        goto end;
    }

    env_key = java_utf8_to_jstring_checked(
                env, "PW_RUN_TIMEOUT", strlen("PW_RUN_TIMEOUT"));
    if (!env_key) {
        goto end;
    }

    val_jstr = java_meth_call(env, &get_prop, NULL, env_key);
    if (JNI(ExceptionCheck)) {
        goto end;
    }

    if (JNI(IsSameObject, val_jstr, NULL)) {
        JAVA_LOG(DDWAF_LOG_DEBUG,
                 "No property PW_RUN_TIMEOUT; using default %lld", val);
        goto end;
    }

    size_t len;
    // java_to_utf8_checked gives out a NUL-terminated string
    if ((val_cstr = java_to_utf8_checked(env, val_jstr, &len)) == NULL) {
        goto end;
    }

    char *end;
    val = strtoll(val_cstr, &end, 10);
    if (*end != '\0') {
        JAVA_LOG(DDWAF_LOG_WARN, "Invalid value of system property "
                           "PW_RUN_TIMEOUT: '%s'", val_cstr);
        goto end;
    }

    JAVA_LOG(DDWAF_LOG_INFO, "Using value %lld us for PW_RUN_TIMEOUT", val);

end:
    if (get_prop.class_glob) {
        java_meth_destroy(env, &get_prop);
    }
    if (env_key) {
        JNI(DeleteLocalRef, env_key);
    }
    if (val_jstr) {
        JNI(DeleteLocalRef, val_jstr);
    }
    free(val_cstr);
    return val;
}

static int64_t get_remaining_budget(struct timespec start, struct timespec end, struct _limits *limits) {
    int64_t diff_us = _timespec_diff_ns(end, start) / 1000LL;
    int64_t rem_gen_budget_in_us = limits->general_budget_in_us - diff_us;
    if (rem_gen_budget_in_us < 0) {
        rem_gen_budget_in_us = 0;
    }
    JAVA_LOG(DDWAF_LOG_DEBUG, "Conversion of WAF arguments took %" PRId64
            " us; remaining general budget is %" PRId64 " us",
             diff_us, rem_gen_budget_in_us);
    return rem_gen_budget_in_us;
}

static size_t get_run_budget(int64_t rem_gen_budget_in_us, struct _limits *limits) {

    size_t run_budget;
    if (rem_gen_budget_in_us > limits->run_budget_in_us) {
        JAVA_LOG(DDWAF_LOG_DEBUG,
                 "Using run budget of % " PRId64 " us instead of "
                 "remaining general budget of %" PRId64 " us",
                 limits->run_budget_in_us, rem_gen_budget_in_us);
        run_budget = (size_t)limits->run_budget_in_us;
    } else {
        run_budget = (size_t)rem_gen_budget_in_us;
    }

    return run_budget;
}

ddwaf_handle get_pwaf_handle_checked(JNIEnv *env, jobject handle_obj)
{
    if (JNI(IsSameObject, handle_obj, NULL)) {
        JNI(ThrowNew, jcls_iae, "Passed null PowerwafHandle");
        return NULL;
    }

    ddwaf_handle handle = (ddwaf_handle)(intptr_t) JNI(
            GetLongField, handle_obj, _pwaf_handle_native_handle);
    if (!handle) {
        JNI(ThrowNew, jcls_iae, "Passed invalid (NULL) PowerwafHandle");
        return NULL;
    }

    return handle;
}

static void _throw_pwaf_exception(JNIEnv *env, DDWAF_RET_CODE retcode)
{
    jobject exc = java_meth_call(env, &_create_exception, NULL, retcode);
    if (!JNI(ExceptionCheck)) {
        JNI(Throw, exc);
    }
}

static void _throw_pwaf_timeout_exception(JNIEnv *env)
{
    jobject exc = java_meth_call(env, &_timeout_exception_init, NULL);
    if (!JNI(ExceptionCheck)) {
        JNI(Throw, exc);
    }
}

static void _update_metrics(JNIEnv *env, jobject metrics_obj,
                            bool is_byte_buffer, const ddwaf_result *ret,
                            struct timespec start)
{
    // save exception if any
    jthrowable earlier_exc = JNI(ExceptionOccurred);
    if (earlier_exc) {
        JNI(ExceptionClear);
    }

    // metrics update
    if (!JNI(IsSameObject, metrics_obj, NULL)) {
        if (is_byte_buffer) {
            // we don't know the total time then
            metrics_update_checked(env, metrics_obj, 0,
                                   (jlong) ret->total_runtime);
        } else {
            struct timespec end_time;
            if (_get_time_checked(env, &end_time)) {
                int64_t run_time_ns = _timespec_diff_ns(end_time, start);
                metrics_update_checked(env, metrics_obj, (jlong) run_time_ns,
                                       (jlong) ret->total_runtime);
            }
        }
    }

    if (earlier_exc) {
        {
            jthrowable subseq_exc = JNI(ExceptionOccurred);
            if (subseq_exc) {
                JAVA_LOG_THR(
                        DDWAF_LOG_ERROR, subseq_exc, "%s",
                        "Exception updating metrics will be suppressed due to "
                        "earlier exception");
                JNI(DeleteLocalRef, subseq_exc);
                JNI(ExceptionClear);
            }
        }
        JNI(Throw, earlier_exc);
        JNI(DeleteLocalRef, earlier_exc);
    }
}

static bool _convert_ddwaf_config_checked(JNIEnv *env, jobject jconfig,
                                          ddwaf_config *out_config)
{

    if (JNI(IsSameObject, jconfig, NULL)) {
        JNI(ThrowNew, jcls_iae, "powerwaf config cannot be null");
        return false;
    }

    char *key_regex = NULL, *value_regex = NULL;

    jobject key_regex_jstr = JNI(GetObjectField, jconfig, _config_key_regex);
    if (JNI(ExceptionCheck)) {
        return false;
    }
    if (!JNI(IsSameObject, key_regex_jstr, NULL)) {
        key_regex = java_to_utf8_checked(env, (jstring) key_regex_jstr,
                                         &(size_t){0});
        if (!key_regex) {
            return false;
        }
        if (key_regex[0] == '\0') {
            free(key_regex);
            key_regex = NULL;
        }
    }
    jobject value_regex_jstr =
            JNI(GetObjectField, jconfig, _config_value_regex);
    if (JNI(ExceptionCheck)) {
        return false;
    }
    if (!JNI(IsSameObject, value_regex_jstr, NULL)) {
        value_regex = java_to_utf8_checked(env, (jstring) value_regex_jstr,
                                           &(size_t){0});
        if (!value_regex) {
            free(key_regex);
            return false;
        }
        if (value_regex[0] == '\0') {
            free(value_regex);
            value_regex = NULL;
        }
    }

    jboolean use_byte_buffers =
            JNI(GetBooleanField, jconfig, _config_use_byte_buffers);
    if (JNI(ExceptionCheck)) {
        free(key_regex);
        free(value_regex);
        return false;
    }

    *out_config = (ddwaf_config){
            // disable these checks. We also have our own
            // limits given at rule run time
            .limits =
                    {// libddwaf allocates a vector with this
                     // size, so this can't be too large
                     .max_container_depth = MAX_DEPTH_UPPER_LIMIT,
                     .max_container_size = (uint32_t) -1,
                     .max_string_length = (uint32_t) -1},

            .obfuscator =
                    {
                            .key_regex = key_regex,
                            .value_regex = value_regex,
                    },

            .free_fn = use_byte_buffers ? NULL : ddwaf_object_free};

    return true;
}
static void _dispose_of_ddwaf_config(ddwaf_config *cfg)
{
    free((void *) (uintptr_t) cfg->obfuscator.key_regex);
    free((void *) (uintptr_t) cfg->obfuscator.value_regex);
}

static jobject _create_result_checked(JNIEnv *env, DDWAF_RET_CODE code,
                                      const ddwaf_result *ret)
{
    if (code == DDWAF_OK && !_has_derivative(ret)) {
        return _result_with_data_ok_null;
    }

    jobject actions_jarr;
    bool del_actions_jarr = false;
    if (ret->actions.type != DDWAF_OBJ_ARRAY || ret->actions.nbEntries == 0) {
        actions_jarr = _result_with_data_empty_str_arr;
    } else {
        actions_jarr = JNI(NewObjectArray, (jsize) ret->actions.nbEntries,
                           string_cls, NULL);
        if (!actions_jarr) {
            java_wrap_exc("%s", "Error creating actions array");
            return NULL;
        }
        del_actions_jarr = true;

        for (uint32_t i = 0; i < ret->actions.nbEntries; i++) {
            const ddwaf_object *action_obj = &ret->actions.array[i];
            if (action_obj->type != DDWAF_OBJ_STRING) {
                JNI(ThrowNew, jcls_iae, "non-string action found");
                goto err;
            }
            jstring action_jstr = java_utf8_to_jstring_checked(
                    env, action_obj->stringValue, action_obj->nbEntries);
            if (!action_jstr) {
                java_wrap_exc("%s", "Error creating actions array");
                goto err;
            }
            JNI(SetObjectArrayElement, actions_jarr, (jsize) i, action_jstr);
            JNI(DeleteLocalRef, action_jstr);
            if (JNI(ExceptionCheck)) {
                java_wrap_exc("%s", "Error setting element in actions array");
                goto err;
            }
        }
    }

    jstring data_obj = NULL;
    if (ret->events.type == DDWAF_OBJ_ARRAY && ret->events.nbEntries > 0) {
        struct json_segment *seg = output_convert_json(&ret->events);
        if (!seg) {
            JNI(ThrowNew, jcls_iae, "failed converting events array to json");
            goto err;
        }

        data_obj = java_json_to_jstring_checked(env, seg);
        json_seg_free(seg);
        if (JNI(ExceptionCheck)) {
            java_wrap_exc("%s", "Failed converting json to Java string");
            goto err;
        }
    }

    jobject schema = NULL;
    if (ret->derivatives.type == DDWAF_OBJ_MAP &&
        ret->derivatives.nbEntries > 0) {
        schema = output_convert_schema_checked(env, &ret->derivatives);
        if (!schema) {
            java_wrap_exc("%s", "Failed encoding inferred schema");
            goto err;
        }
    }

    jobject result =
            java_meth_call(env, &result_with_data_init, NULL,
                           code == DDWAF_OK ? _action_ok : _action_match,
                           data_obj, actions_jarr, schema);
    if (del_actions_jarr) {
        JNI(DeleteLocalRef, actions_jarr);
    }
    JNI(DeleteLocalRef, data_obj);
    return result;

err:
    if (del_actions_jarr) {
        JNI(DeleteLocalRef, actions_jarr);
    }
    return NULL;
}

static inline bool _has_derivative(const ddwaf_result *res) {
    return res->derivatives.type == DDWAF_OBJ_MAP 
    && res->derivatives.nbEntries > 0;
}
