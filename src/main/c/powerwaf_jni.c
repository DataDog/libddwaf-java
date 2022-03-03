/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include "jni/io_sqreen_powerwaf_Powerwaf.h"
#include "jni/io_sqreen_powerwaf_Additive.h"
#include "common.h"
#include "java_call.h"
#include "utf16_utf8.h"
#include "logging.h"
#include "compat.h"
#include <ddwaf.h>
#include <assert.h>
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
static void _dispose_of_action_with_data_fields(JNIEnv *env);
static void _dispose_of_cache_references(JNIEnv *env);
static ddwaf_object _convert_checked(JNIEnv *env, jobject obj, struct _limits *limits, int rec_level);
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
static ddwaf_handle _get_pwaf_handle_checked(JNIEnv *env, jobject handle_obj);
static void _throw_pwaf_exception(JNIEnv *env, DDWAF_RET_CODE retcode);
static void _throw_pwaf_timeout_exception(JNIEnv *env);
static jobject _convert_rsi_checked(JNIEnv *env, const ddwaf_ruleset_info *rsi);

// don't use DDWAF_OBJ_INVALID, as that can't be added to arrays/maps
static const ddwaf_object _pwinput_invalid = { .type = DDWAF_OBJ_MAP };

// disable these checks. We also have our own limits given at rule run time
static const ddwaf_config _pw_config = {
    .maxArrayLength = 0ULL,
    .maxMapDepth = 0ULL,
};

jclass jcls_rte;
jclass jcls_iae;
jmethodID rte_constr_cause;

static struct j_method _create_exception;
static struct j_method _timeout_exception_init;
/* these three are weak global references
 * we don't need strong ones because they are static fields of a class that
 * won't be unloaded as long as the Powerwaf class is loaded */
static jobject _action_ok;
static jobject _action_monitor;
static jobject _action_block;
static jobject _action_with_data_ok_null;
static struct j_method _action_with_data_init;
static jfieldID _limit_max_depth;
static jfieldID _limit_max_elements;
static jfieldID _limit_max_string_size;
static jfieldID _limit_general_budget_in_us;
static jfieldID _limit_run_budget_in_us;

static jfieldID _additive_ptr;

static struct j_method _ruleset_info_init;

jclass charSequence_cls;
struct j_method charSequence_length;
struct j_method charSequence_subSequence;

jclass buffer_cls;
struct j_method buffer_position;
struct j_method buffer_limit;

jclass charBuffer_cls;
struct j_method charBuffer_hasArray;
struct j_method charBuffer_array;

jclass string_cls;
struct j_method to_string;

static struct j_method _pwaf_handle_init;
static jfieldID _pwaf_handle_native_handle;

struct j_method number_longValue;
struct j_method number_doubleValue;
// weak, but assumed never to be gced
jclass *number_cls = &number_longValue.class_glob;

static struct j_method _boolean_booleanValue;
static jclass *_boolean_cls = &_boolean_booleanValue.class_glob;

static struct j_method _hashmap_init;
static struct j_method _map_put;
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
    bool log_ok = java_log_init(vm, env);
    if (!log_ok) {
        if (!JNI(ExceptionCheck)) {
            JNI(ThrowNew, jcls_rte, "Library initialization failed"
                                    "(java_log_init)");
        }
        goto error;
    }

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
 * Signature: (Ljava/util/Map;)Lio/sqreen/powerwaf/PowerWAFHandle
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Powerwaf_addRules(
        JNIEnv *env, jclass clazz,
        jobject rule_def, jobject rule_set_info_arr)
{
    UNUSED(clazz);

    if (!_check_init(env)) {
        return NULL;
    }

    struct _limits limits = {
        .max_depth = 20,
        .max_elements = 1000000,
        .max_string_size = 1000000,
    };
    ddwaf_object input = _convert_checked(env, rule_def, &limits, 0);
    jthrowable thr = JNI(ExceptionOccurred);
    if (thr) {
        JAVA_LOG_THR(DDWAF_LOG_ERROR, thr,
                     "Exception encoding rule definitions");
        java_wrap_exc("%s", "Exception encoding rule definitions");
        JNI(DeleteLocalRef, thr);
        return NULL;
    }

    bool has_rule_set_info = !JNI(IsSameObject, rule_set_info_arr, NULL) &&
                             JNI(GetArrayLength, rule_set_info_arr) == 1;
    ddwaf_ruleset_info *rule_set_info =
            has_rule_set_info ? &(ddwaf_ruleset_info){0} : NULL;
    ddwaf_handle nativeHandle = ddwaf_init(&input, &_pw_config, rule_set_info);
    ddwaf_object_free(&input);

    if (rule_set_info && memcmp(rule_set_info, &(ddwaf_ruleset_info){0},
                                sizeof(*rule_set_info)) != 0) {
        jobject rsi_java = _convert_rsi_checked(env, rule_set_info);
        ddwaf_ruleset_info_free(rule_set_info);

        if (JNI(ExceptionOccurred)) {
            java_wrap_exc("Error converting rule info structure");
            if (nativeHandle) {
                ddwaf_destroy(nativeHandle);
            }
            return NULL;
        }
        JNI(SetObjectArrayElement, rule_set_info_arr, 0, rsi_java);
        if (JNI(ExceptionOccurred)) {
            java_wrap_exc("Error setting reference for RuleSetInfo");
            if (nativeHandle) {
                ddwaf_destroy(nativeHandle);
            }
            return NULL;
        }
    }

    if (!nativeHandle) {
        JAVA_LOG(DDWAF_LOG_WARN, "call to pw_initH failed");
        JNI(ThrowNew, jcls_iae, "Call to pw_initH failed");
        return NULL;
    }

    return java_meth_call(env, &_pwaf_handle_init, NULL,
                          (jlong)(intptr_t) nativeHandle);
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
    if (!(nat_handle = _get_pwaf_handle_checked(env, handle_obj))) {
        return;
    }

    ddwaf_destroy(nat_handle);
}

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    getRequiredAddresses
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_io_sqreen_powerwaf_Powerwaf_getRequiredAddresses
  (JNIEnv *env, jclass clazz, jobject handle_obj)
{
    UNUSED(clazz);

    if (!_check_init(env)) {
        return NULL;
    }

    ddwaf_handle nat_handle;
    if (!(nat_handle = _get_pwaf_handle_checked(env, handle_obj))) {
        return NULL;
    }

    uint32_t size;
    const char* const* addresses = ddwaf_required_addresses(nat_handle, &size);
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
                                jobject handle_obj, jobject parameters,
                                jobject limits_obj)
{
    UNUSED(clazz);

    jobject result = NULL;
    ddwaf_object input = _pwinput_invalid;
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
    if (!(pwhandle = _get_pwaf_handle_checked(env, handle_obj))) {
        return NULL;
    }

    int64_t rem_gen_budget_in_us;
    if (is_byte_buffer) {
        void *input_p = JNI(GetDirectBufferAddress, parameters);
        if (!input_p) {
            JNI(ThrowNew, jcls_iae, "Not a DirectBuffer passed");
            goto end;
        }
        jlong capacity = JNI(GetDirectBufferCapacity, parameters);
        if (capacity < (jlong) sizeof(input)) {
            JNI(ThrowNew, jcls_iae, "Capacity of DirectBuffer is insufficient");
            goto end;
        }
        memcpy(&input, input_p, sizeof input);
        // let's pretend nothing we did till now took time
        rem_gen_budget_in_us = limits.general_budget_in_us;
    } else {
        struct timespec conv_end;
        input = _convert_checked(env, parameters, &limits, 0);
        jthrowable thr = JNI(ExceptionOccurred);
        if (thr) {
            JAVA_LOG_THR(DDWAF_LOG_INFO, thr,
                         "Exception encoding parameters");
            java_wrap_exc("%s", "Exception encoding parameters");
            JNI(DeleteLocalRef, thr);
            goto end;
        }
        if (!_get_time_checked(env, &conv_end)) {
            goto end;
        }
        rem_gen_budget_in_us = get_remaining_budget(start, conv_end, &limits);
        if (rem_gen_budget_in_us == 0) {
            JAVA_LOG(DDWAF_LOG_INFO,
                     "General budget of %" PRId64 " us exhausted after "
                     "native conversion",
                     limits.general_budget_in_us);
            _throw_pwaf_timeout_exception(env);
            goto end;
        }
    }

    size_t run_budget = get_run_budget(rem_gen_budget_in_us, &limits);

    ctx = ddwaf_context_init(pwhandle,
                             is_byte_buffer ? NULL : ddwaf_object_free);
    if (!ctx) {
        JAVA_LOG(DDWAF_LOG_WARN, "Call to ddwaf_context_init failed");
        _throw_pwaf_exception(env, DDWAF_ERR_INTERNAL);
        goto end;
    }
    DDWAF_RET_CODE ret_code = ddwaf_run(ctx, &input, &ret, run_budget);

    JAVA_LOG(DDWAF_LOG_DEBUG,
             "ddwaf_run ran in %" PRId32 " microseconds. "
             "Result code: %d",
             ret.perfTotalRuntime, ret_code);

    if (ret.timeout) {
        _throw_pwaf_timeout_exception(env);
        goto freeRet;
    }

    jobject action_obj;
    switch (ret_code) {
        case DDWAF_GOOD:
            action_obj = _action_ok;
            if (!ret.data) {
                result = _action_with_data_ok_null;
                goto freeRet;
            }
            break;
        case DDWAF_MONITOR:
            action_obj = _action_monitor;
            break;
        case DDWAF_BLOCK:
            action_obj = _action_block;
            break;
        case DDWAF_ERR_INTERNAL: {
            JAVA_LOG(DDWAF_LOG_ERROR, "libddwaf returned DDWAF_ERR_INTERNAL. "
                "Data may have leaked");
            _throw_pwaf_exception(env, DDWAF_ERR_INTERNAL);
            goto freeRet;
        }
        case DDWAF_ERR_INVALID_ARGUMENT:
            if (!is_byte_buffer) {
                ddwaf_object_free(&input);
            }
            // break intentionally missing
        default: {
            // any errors or unknown statuses
            _throw_pwaf_exception(env, (jint) ret_code);
            goto freeRet;
        }
    }

    jstring data_obj = NULL;
    if (ret.data) {
        // no length, so the string must be NUL-terminated
        data_obj = java_utf8_to_jstring_checked(
                    env, ret.data, strlen(ret.data));
        if (!data_obj) {
            goto freeRet;
        }
    }

    result = java_meth_call(env, &_action_with_data_init, NULL,
                            action_obj, data_obj);

    JNI(DeleteLocalRef, data_obj);

freeRet:
    ddwaf_result_free(&ret);
end:
    if (ctx) {
        ddwaf_context_destroy(ctx);
    }

    return result;
}

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    runRules
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;Ljava/util/Map;Lio/sqreen/powerwaf/Powerwaf$Limits;)Lio/sqreen/powerwaf/Powerwaf$ActionWithData;
 */
JNIEXPORT jobject JNICALL
Java_io_sqreen_powerwaf_Powerwaf_runRules__Lio_sqreen_powerwaf_PowerwafHandle_2Ljava_util_Map_2Lio_sqreen_powerwaf_Powerwaf_00024Limits_2(
        JNIEnv *env, jclass clazz, jobject handle_obj, jobject parameters,
        jobject limits_obj)
{
    return _run_rule_common(false, env, clazz, handle_obj, parameters,
                            limits_obj);
}

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    runRules
 * Signature: (Lio/sqreen/powerwaf/PowerWAFHandle;Ljava/nio/ByteBuffer;Lio/sqreen/powerwaf/Powerwaf/Limits;)Lio/sqreen/powerwaf/Powerwaf/ActionWithData;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Powerwaf_runRules__Lio_sqreen_powerwaf_PowerwafHandle_2Ljava_nio_ByteBuffer_2Lio_sqreen_powerwaf_Powerwaf_00024Limits_2
  (JNIEnv *env, jclass clazz, jobject handle_obj, jobject main_byte_buffer, jobject limits_obj)
{
    return _run_rule_common(true, env, clazz, handle_obj, main_byte_buffer,
                            limits_obj);
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

    ddwaf_version iversion;
    ddwaf_get_version(&iversion);
    char *version;
    int size_version = asprintf(&version, "%d.%d.%d",
                                iversion.major, iversion.minor, iversion.patch);
    if (size_version < 0) {
        JNI(ThrowNew, jcls_rte, "Could not allocate memory for the version");
        return NULL;
    }

    jstring ret = java_utf8_to_jstring_checked(
                env, version, (size_t)size_version);
    free(version);
    return ret;
}

/*
 * Class:     io.sqreen.powerwaf.Additive
 * Method:    initAdditive
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;Z)J
 */
JNIEXPORT jlong JNICALL Java_io_sqreen_powerwaf_Additive_initAdditive(
        JNIEnv *env, jclass clazz, jobject handle_obj, jboolean is_byte_buffers)
{
    UNUSED(clazz);

    ddwaf_handle nat_handle;
    if (!(nat_handle = _get_pwaf_handle_checked(env, handle_obj))) {
        return 0L;
    }

    ddwaf_context context = ddwaf_context_init(
            nat_handle, is_byte_buffers ? NULL : ddwaf_object_free);
    if (!context) {
        JNI(ThrowNew, jcls_rte, "ddwaf_context_init failed");
        return 0L;
    }

    return (jlong)(intptr_t) context;
}

static jobject _run_additive_common(JNIEnv *env, jobject this,
                                    jobject parameters, jobject limits_obj,
                                    const bool is_byte_buffer)
{
    jobject result = NULL;
    ddwaf_context context = NULL;
    ddwaf_object input = _pwinput_invalid;
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
        void *input_p = JNI(GetDirectBufferAddress, parameters);
        if (!input_p) {
            JNI(ThrowNew, jcls_iae, "Not a DirectBuffer passed");
            return NULL;
        }
        jlong capacity = JNI(GetDirectBufferCapacity, parameters);
        if (capacity < (jlong) sizeof(input)) {
            JNI(ThrowNew, jcls_iae, "Capacity of DirectBuffer is insufficient");
            return NULL;
        }
        memcpy(&input, input_p, sizeof input);
    } else {
        input = _convert_checked(env, parameters, &limits, 0);
        jthrowable thr = JNI(ExceptionOccurred);
        if (thr) {
            JAVA_LOG_THR(DDWAF_LOG_WARN, thr,
                         "Exception encoding parameters");
            java_wrap_exc("%s", "Exception encoding parameters");
            JNI(DeleteLocalRef, thr);
            return NULL;
        }
        // after this point input must be accounted for
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

    DDWAF_RET_CODE ret_code = ddwaf_run(context, &input, &ret, run_budget);

    if (ret.timeout) {
        _throw_pwaf_timeout_exception(env);
        goto freeRet;
    }

    jobject action_obj;
    switch (ret_code) {
        case DDWAF_GOOD:
            action_obj = _action_ok;
            if (!ret.data) {
                result = _action_with_data_ok_null;
                goto freeRet;
            }
            break;
        case DDWAF_MONITOR:
            action_obj = _action_monitor;
            break;
        case DDWAF_BLOCK:
            action_obj = _action_block;
            break;
        case DDWAF_ERR_INTERNAL: {
            JAVA_LOG(DDWAF_LOG_ERROR, "libddwaf returned DDWAF_ERR_INTERNAL. "
                "Data may have leaked");
            _throw_pwaf_exception(env, DDWAF_ERR_INTERNAL);
            goto freeRet;
        }
        case DDWAF_ERR_INVALID_ARGUMENT:
            if (!is_byte_buffer) {
                ddwaf_object_free(&input);
            }
            // break intentionally missing
        default: {
            // any errors or unknown statuses
            _throw_pwaf_exception(env, (jint) ret_code);
            goto freeRet;
        }
    }

    jstring data_obj = NULL;
    if (ret.data) {
        // no length, so the string must be NUL-terminated
        data_obj = java_utf8_to_jstring_checked(
            env, ret.data, strlen(ret.data));
        if (!data_obj) {
            if (!JNI(ExceptionCheck)) {
                JNI(ThrowNew, jcls_rte, "Could not create result data string");
            }
            goto freeRet;
        }
    }

    result = java_meth_call(env, &_action_with_data_init, NULL,
                            action_obj, data_obj);

    JNI(DeleteLocalRef, data_obj);

freeRet:
    ddwaf_result_free(&ret);

    return result;

err:
    if (!is_byte_buffer) {
        ddwaf_object_free(&input);
    }
    return NULL;
}

/*
 * Class:     io_sqreen_powerwaf_Additive
 * Method:    runAdditive
 * Signature:
 * (Ljava/util/Map;Lio/sqreen/powerwaf/Powerwaf$Limits;)Lio/sqreen/powerwaf/Powerwaf$ActionWithData;
 */
JNIEXPORT jobject JNICALL
Java_io_sqreen_powerwaf_Additive_runAdditive__Ljava_util_Map_2Lio_sqreen_powerwaf_Powerwaf_00024Limits_2(
        JNIEnv *env, jobject this, jobject parameters, jobject limits_obj)
{
    return _run_additive_common(env, this, parameters, limits_obj, false);
}

/*
 * Class:     io_sqreen_powerwaf_Additive
 * Method:    runAdditive
 * Signature:
 * (Ljava/nio/ByteBuffer;Lio/sqreen/powerwaf/Powerwaf$Limits;)Lio/sqreen/powerwaf/Powerwaf$ActionWithData;
 */
JNIEXPORT jobject JNICALL
Java_io_sqreen_powerwaf_Additive_runAdditive__Ljava_nio_ByteBuffer_2Lio_sqreen_powerwaf_Powerwaf_00024Limits_2(
        JNIEnv *env, jobject this, jobject bb, jobject limits_obj)
{
    return _run_additive_common(env, this, bb, limits_obj, true);
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

    ddwaf_set_log_cb(NULL, DDWAF_LOG_ERROR);

    java_log_shutdown(env);
}

#define ACTION_ENUM_DESCR "Lio/sqreen/powerwaf/Powerwaf$Action;"
static bool _fetch_action_enums(JNIEnv *env)
{
    bool ret = false;

    jclass action_jclass = JNI(FindClass, "io/sqreen/powerwaf/Powerwaf$Action");
    if (!action_jclass) {
        goto error;
    }

    _action_ok = java_static_field_checked(env, action_jclass,
                                           "OK", ACTION_ENUM_DESCR);
    if (!_action_ok) {
        goto error;
    }
    _action_monitor = java_static_field_checked(env, action_jclass,
                                                "MONITOR", ACTION_ENUM_DESCR);
    if (!_action_monitor) {
        goto error;
    }
    _action_block = java_static_field_checked(env, action_jclass,
                                              "BLOCK", ACTION_ENUM_DESCR);
    if (!_action_block) {
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

#define ACTION_WITH_DATA_DESCR "Lio/sqreen/powerwaf/Powerwaf$ActionWithData;"
static bool _fetch_action_with_data_fields(JNIEnv *env)
{
    bool ret = false;

    jclass action_with_data_jclass = JNI(FindClass, "io/sqreen/powerwaf/Powerwaf$ActionWithData");
    if (!action_with_data_jclass) {
        goto error;
    }

    _action_with_data_ok_null = java_static_field_checked(env, action_with_data_jclass,
                                           "OK_NULL", ACTION_WITH_DATA_DESCR);
    if (!_action_with_data_ok_null) {
        goto error;
    }

    ret = true;

error:
    JNI(DeleteLocalRef, action_with_data_jclass);
    if (!ret) {
        _dispose_of_action_with_data_fields(env);
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

static bool _fetch_ruleset_info_init(JNIEnv *env)
{
    return java_meth_init_checked(
            env, &_ruleset_info_init, "io/sqreen/powerwaf/RuleSetInfo", NULL,
            "(Ljava/lang/String;IILjava/util/Map;)V", JMETHOD_CONSTRUCTOR);
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

static void _dispose_of_action_enums(JNIEnv *env)
{
    if (_action_ok) {
        JNI(DeleteWeakGlobalRef, _action_ok);
        _action_ok = NULL;
    }
    if (_action_monitor) {
        JNI(DeleteWeakGlobalRef, _action_monitor);
        _action_monitor = NULL;
    }
    if (_action_block) {
        JNI(DeleteWeakGlobalRef, _action_block);
        _action_block = NULL;
    }
}

static void _dispose_of_action_with_data_fields(JNIEnv *env)
{
    if (_action_with_data_ok_null) {
        JNI(DeleteWeakGlobalRef, _action_with_data_ok_null);
        _action_with_data_ok_null = NULL;
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
         _cache_single_class_weak(env, "java/lang/String", &string_cls);
}

static void _dispose_of_weak_classes(JNIEnv *env)
{
#define DESTROY_CLASS_REF(jcls)                                                \
    if (jcls) {                                                                \
        JNI(DeleteWeakGlobalRef, jcls);                                        \
        jcls = NULL;                                                           \
    }

    DESTROY_CLASS_REF(jcls_iae)
    DESTROY_CLASS_REF(string_cls)
    DESTROY_CLASS_REF(charSequence_cls)
    DESTROY_CLASS_REF(buffer_cls)
    DESTROY_CLASS_REF(charBuffer_cls)
    // leave jcls_rte for last in OnUnload; we might still need it
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
                env, &_timeout_exception_init, "io/sqreen/powerwaf/exception/TimeoutPowerwafException",
                "<init>",
                "()V",
                JMETHOD_CONSTRUCTOR)) {
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
                env, &_action_with_data_init,
                "io/sqreen/powerwaf/Powerwaf$ActionWithData", "<init>",
                "(Lio/sqreen/powerwaf/Powerwaf$Action;Ljava/lang/String;)V",
                JMETHOD_CONSTRUCTOR)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &_pwaf_handle_init,
                                "io/sqreen/powerwaf/PowerwafHandle", "<init>",
                                "(J)V", JMETHOD_CONSTRUCTOR)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &_hashmap_init, "java/util/HashMap", NULL,
                                "(I)V", JMETHOD_CONSTRUCTOR)) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &_map_put, "java/util/Map", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                JMETHOD_VIRTUAL)) {
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
    DESTROY_METH(buffer_position)
    DESTROY_METH(buffer_limit)
    DESTROY_METH(to_string)
    DESTROY_METH(number_longValue)
    DESTROY_METH(_boolean_booleanValue)
    DESTROY_METH(_action_with_data_init)
    DESTROY_METH(_ruleset_info_init)
    DESTROY_METH(_pwaf_handle_init)
    DESTROY_METH(_hashmap_init)
    DESTROY_METH(_map_put)
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

    if (!_fetch_action_with_data_fields(env)) {
        goto error;
    }

    if (!_fetch_additive_fields(env)) {
        goto error;
    }

    if (!_fetch_limit_fields(env)) {
        goto error;
    }

    if (!_fetch_ruleset_info_init(env)) {
        goto error;
    }

    if (!_fetch_native_handle_field(env)) {
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
    _dispose_of_action_with_data_fields(env);
    _dispose_of_weak_classes(env);
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
        // replace NULLs with empty maps.
        // DDWAF_OBJ_NULL is actually invalid; it can't be added to containers
        ddwaf_object_map(&result); // can't fail
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

            if (utf16_len > max_utf16_len) {
                jobject new_obj = java_meth_call(env, &charSequence_subSequence,
                                                 obj, 0, max_utf16_len);
                if (JNI(ExceptionCheck)) {
                    goto error;
                }
                JNI(DeleteLocalRef, obj);
                obj = new_obj;
            }

            jstring str = java_meth_call(env, &to_string, obj);
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
        jlong lval = JNI(CallLongMethod, obj, number_longValue.meth_id);
        if (JNI(ExceptionCheck)) {
            goto error;
        }

        bool success = !!ddwaf_object_signed(&result, lval);
        if (!success) {
            JNI(ThrowNew, jcls_rte, "ddwaf_object_signed failed (OOM?)");
            goto error;
        }
    } else if (JNI(IsInstanceOf, obj, *_boolean_cls)) {
        jboolean bval = JNI(CallNonvirtualBooleanMethod, obj,
                            _boolean_booleanValue.class_glob,
                            _boolean_booleanValue.meth_id);
        if (JNI(ExceptionCheck)) {
            goto error;
        }

        // PWArgs has no boolean type
        // PowerWAF expects this to be a string for match_regex > case_sensitive
        bool success;
        if (bval) {
            success =
                    !!ddwaf_object_stringl(&result, "true", sizeof("true") - 1);
        } else {
            success = !!ddwaf_object_stringl(&result, "false",
                                             sizeof("false") - 1);
        }
        if (!success) {
            JNI(ThrowNew, jcls_rte, "ddwaf_object_stringl failed (OOM?)");
            goto error;
        }
    } else {
        jclass cls = JNI(GetObjectClass, obj);
        jobject name = java_meth_call(env, &_class_get_name, cls);
        static char unknown[] = "<unknown class>";
        static const size_t unknown_len = sizeof(unknown) - 1;
        char *name_c;
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
            free(name_c);
        }
    }

    // having lael here so if we add cleanup in the future we don't forget
early_return:
    return result;
error:
    ddwaf_object_free(&result);

    return _pwinput_invalid;
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
    if (!JNI(ExceptionCheck)) {
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
        void *addr = JNI(GetDirectBufferAddress, obj);
        if (addr) {
            info->nat_array = addr;
            return true;
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

static ddwaf_handle _get_pwaf_handle_checked(JNIEnv *env, jobject handle_obj)
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

static jobject _convert_rsi_checked(JNIEnv *env, const ddwaf_ruleset_info *rsi)
{
    jstring version = NULL;
    if (rsi->version) {
        version = java_utf8_to_jstring_checked(env, rsi->version,
                                               strlen(rsi->version));
        if (JNI(ExceptionOccurred)) {
            return NULL;
        }
    }

    jobject errors_map = NULL;
    jint num_errors = (jint) MIN(rsi->errors.nbEntries, (INT_MAX / 4) * 3);
    jint map_cap = num_errors + (num_errors + 2) / 3; // load factor is .75
    errors_map = java_meth_call(env, &_hashmap_init, NULL, map_cap);
    if (JNI(ExceptionOccurred)) {
        return NULL;
    }
    assert(rsi->errors.type == DDWAF_OBJ_MAP);
    for (jint i = 0; i < num_errors; i++) {
        ddwaf_object *entry = &rsi->errors.array[i];
        jstring error_jstr = java_utf8_to_jstring_checked(
                env, entry->parameterName, entry->parameterNameLength);
        if (!error_jstr) {
            return NULL;
        }

        assert(entry->type == DDWAF_OBJ_ARRAY);

        jint num_rule_ids = (jint) MIN(entry->nbEntries, INT_MAX);
        jobjectArray rule_id_jarray =
                JNI(NewObjectArray, num_rule_ids, string_cls, NULL);
        if (JNI(ExceptionOccurred)) {
            return NULL;
        }

        for (jint j = 0; j < num_rule_ids; j++) {
            ddwaf_object *rule_id_obj = &entry->array[j];
            assert(rule_id_obj->type == DDWAF_OBJ_STRING);
            jstring rule_id = java_utf8_to_jstring_checked(env,
                rule_id_obj->stringValue, rule_id_obj->nbEntries);
            if (!rule_id) {
                return NULL;
            }

            JNI(SetObjectArrayElement, rule_id_jarray, j, rule_id);
            if (JNI(ExceptionOccurred)) {
                return NULL;
            }

            JNI(DeleteLocalRef, rule_id);
        }

        java_meth_call(env, &_map_put, errors_map, error_jstr, rule_id_jarray);
        if (JNI(ExceptionOccurred)) {
            return NULL;
        }

        JNI(DeleteLocalRef, error_jstr);
    }
    

    return java_meth_call(env, &_ruleset_info_init, NULL, version,
                          (jint) rsi->loaded, (jint) rsi->failed, errors_map);
}
