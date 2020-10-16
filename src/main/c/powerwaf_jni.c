#include "io_sqreen_powerwaf_Powerwaf.h"
#include "common.h"
#include "java_call.h"
#include "utf16_utf8.h"
#include "logging.h"
#include "compat.h"
#include "detailed_metrics.h"
#include <PowerWAF.h>
#include <assert.h>
#include <string.h>
#include <inttypes.h>
#include <time.h>

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
static void _dispose_of_cache_references(JNIEnv *env);
static PWArgs _convert_checked(JNIEnv *env, jobject obj, struct _limits *limits, int rec_level);
static struct _limits _fetch_limits_checked(JNIEnv *env, jobject limits_obj);
static PWAddContext _get_additive_context(JNIEnv *env, jobject additive_obj);
static bool _set_additive_context(JNIEnv *env, jobject additive_obj, jlong value);
static bool _get_time_checked(JNIEnv *env, struct timespec *time);
static inline int64_t _timespec_diff_ns(struct timespec a, struct timespec b);
static int64_t _get_pw_run_timeout_checked(JNIEnv *env);
static size_t get_run_budget(int64_t rem_gen_budget_in_us, struct _limits *limits);
static int64_t get_remaining_budget(struct timespec start, struct timespec end, struct _limits *limits);

static const PWArgs _pwinput_invalid = { .type = PWI_INVALID };

// disable these checks. Our limits are given at rule run time
static const PWConfig _pw_config = {
    .maxArrayLength = 0,
    .maxMapDepth = 0,
};

jclass jcls_rte;
jclass jcls_iae;
jmethodID rte_constr_cause;

static jmethodID _create_exception_mid;
/* these three are weak global references
 * we don't need strong ones because they are static fields of a class that
 * won't be unloaded as long as the Powerwaf class is loaded */
static jobject _action_ok;
static jobject _action_monitor;
static jobject _action_block;
static struct j_method _action_with_data_init;
static jfieldID _limit_max_depth;
static jfieldID _limit_max_elements;
static jfieldID _limit_max_string_size;
static jfieldID _limit_general_budget_in_us;
static jfieldID _limit_run_budget_in_us;

static jfieldID _additive_ptr;

jclass string_cls;
struct j_method to_string;

static struct j_method _additive_init;

struct j_method number_longValue;
struct j_method number_doubleValue;
// weak, but assumed never to be gced
jclass *number_cls = &number_longValue.class_glob;

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
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
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

    bool detailed_metrics_ok = detailed_metrics_cache_global_references(env);
    if (!detailed_metrics_ok) {
        if (!JNI(ExceptionCheck)) {
            JNI(ThrowNew, jcls_rte, "Library initialization failed"
                                    "(detailed_metrics_cache_global_references)");
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

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved)
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
 * Method:    addRule
 * Signature: (Ljava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_io_sqreen_powerwaf_Powerwaf_addRule(
        JNIEnv *env, jclass clazz,
        jstring rule_name, jstring rule_def)
{
    UNUSED(clazz);

    if (!_check_init(env)) {
        return JNI_FALSE;
    }

    jboolean result = JNI_FALSE;
    char *rule_name_c = NULL;
    char *rule_def_c = NULL;

    rule_name_c = java_to_utf8_checked(env, rule_name, NULL);
    if (!rule_name_c) {
        goto end;
    }
    rule_def_c = java_to_utf8_checked(env, rule_def, NULL);
    if (!rule_def_c) {
        goto end;
    }

    char * errors = NULL;
    result = pw_init(rule_name_c, rule_def_c, &_pw_config, &errors);

    if (!result && errors != NULL)
        JAVA_LOG(PWL_WARN, "PowerWAF init error: '%s'", errors);

end:
    free(rule_name_c);
    free(rule_def_c);

    return result;
}

/*
 * Class:     io.sqreen.powerwaf.Powerwaf
 * Method:    clearRule
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_Powerwaf_clearRule(
        JNIEnv *env, jclass clazz, jstring rule_name)
{
    UNUSED(clazz);

    if (!_check_init(env)) {
        return;
    }

    char *rule_name_c = java_to_utf8_checked(env, rule_name, NULL);
    if (!rule_name_c) {
        return;
    }

    pw_clearRule(rule_name_c);
    free(rule_name_c);
}

/*
 * Class:     io.sqreen.powerwaf.Powerwaf
 * Method:    runRule
 * Signature: (Ljava/lang/String;Ljava/util/Map;Lio/sqreen/powerwaf/Powerwaf$Limits;)Lio/sqreen/powerwaf/Powerwaf$ActionWithData;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Powerwaf_runRule(
        JNIEnv *env, jclass clazz,
        jstring rule_name, jobject parameters, jobject limits_obj)
{
    jobject result = NULL;
    char *rule_name_c = NULL;
    PWArgs input = { .type = PWI_INVALID };
    struct _limits limits;
    PWRet ret;
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

    rule_name_c = java_to_utf8_checked(env, rule_name, NULL);
    if (!rule_name_c) {
        goto end;
    }

    input = _convert_checked(env, parameters, &limits, 0);
    if (JNI(ExceptionCheck)) {
        goto end;
    }
    struct timespec conv_end;
    if (!_get_time_checked(env, &conv_end)) {
        goto end;
    }

    int64_t rem_gen_budget_in_us = get_remaining_budget(start, conv_end, &limits);
    if (rem_gen_budget_in_us == 0) {
        JAVA_LOG(PWL_INFO, "General budget of %" PRId64 " us exhausted after "
                           "native conversion", limits.general_budget_in_us);
        jobject exc = JNI(CallStaticObjectMethod, clazz,
                          _create_exception_mid, PW_ERR_TIMEOUT);
        if (!JNI(ExceptionOccurred)) {
            JNI(Throw, exc);
        }
        goto end;
    }

    size_t run_budget = get_run_budget(rem_gen_budget_in_us, &limits);

    ret = pw_run(rule_name_c, input, run_budget);

    jobject action_obj;
    switch (ret.action) {
        case PW_GOOD:
            action_obj = _action_ok;
            break;
        case PW_MONITOR:
            action_obj = _action_monitor;
            break;
        case PW_BLOCK:
            action_obj = _action_block;
            break;
        case PW_ERR_TIMEOUT:
            goto freeRet;
        default: {
            // any errors or unknown statuses
            jobject exc = JNI(CallStaticObjectMethod,
                      clazz, _create_exception_mid, (jint) ret.action);
            if (!JNI(ExceptionOccurred)) {
                JNI(Throw, exc);
            } // if an exception occurred calling createException, let it propagate
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
    pw_freeReturn(ret);
end:
    free(rule_name_c);
    pw_freeArg(&input);

    return result;
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

    PWVersion iversion = pw_getVersion();
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
 * Signature: (Ljava/lang/String;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Additive_initAdditive
        (JNIEnv *env, jobject clazz, jstring rule_name)
{
    UNUSED(env);
    UNUSED(clazz);

    PWAddContext *context = NULL;
    char *rule_name_c = NULL;

    if (rule_name == NULL) {
            JNI(ThrowNew, jcls_iae, "ruleName should not be null");
        return NULL;
    }

    rule_name_c = java_to_utf8_checked(env, rule_name, NULL);
    if (rule_name_c == NULL) {
        return NULL;
    }

    context = pw_initAdditive(rule_name_c);
    if (context == NULL) {
        return NULL;
    }

    return java_meth_call(env, &_additive_init, NULL, (jlong) context, rule_name);
}

/*
 * Class:     io.sqreen.powerwaf.Additive
 * Method:    runAdditive
 * Signature: (Ljava/util/Map;Lio/sqreen/powerwaf/Powerwaf/Limits;)Lio/sqreen/powerwaf/Powerwaf/ActionWithData;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Additive_runAdditive
        (JNIEnv *env, jobject this, jobject parameters, jobject limits_obj) {

    jobject result = NULL;
    PWAddContext context = NULL;
    PWArgs input;
    struct _limits limits;
    PWRet ret;
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

    context = _get_additive_context(env, this);
    if (JNI(ExceptionCheck)) {
        return NULL;
    }

    if (context == 0) {
        JNI(ThrowNew, jcls_rte, "The Additive has already been cleared");
        return NULL;
    }

    input = _convert_checked(env, parameters, &limits, 0);
    if (JNI(ExceptionCheck)) {
        goto end;
    }

    struct timespec conv_end;
    if (!_get_time_checked(env, &conv_end)) {
        goto end;
    }

    int64_t rem_gen_budget_in_us = get_remaining_budget(start, conv_end, &limits);
    if (rem_gen_budget_in_us == 0) {
        JAVA_LOG(PWL_INFO, "General budget of %" PRId64 " us exhausted after "
                           "native conversion", limits.general_budget_in_us);
        jobject exc = JNI(CallStaticObjectMethod, this,
                          _create_exception_mid, PW_ERR_TIMEOUT);
        if (!JNI(ExceptionOccurred)) {
            JNI(Throw, exc);
        }
        goto end;
    }

    size_t run_budget = get_run_budget(rem_gen_budget_in_us, &limits);

    ret = pw_runAdditive(context, input, run_budget);

    jobject action_obj;
    switch (ret.action) {
        case PW_GOOD:
            action_obj = _action_ok;
            break;
        case PW_MONITOR:
            action_obj = _action_monitor;
            break;
        case PW_BLOCK:
            action_obj = _action_block;
            break;
        case PW_ERR_TIMEOUT:
            goto freeRet;
        default: {
            // any errors or unknown statuses
            jobject exc = JNI(CallStaticObjectMethod,
                      this, _create_exception_mid, (jint) ret.action);
            if (!JNI(ExceptionOccurred)) {
                JNI(Throw, exc);
            } // if an exception occurred calling createException, let it propagate
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
    pw_freeReturn(ret);
end:
    //free(add_context);
    //pw_freeArg(&input);

    return result;
}

/*
 * Class:     io.sqreen.powerwaf.Additive
 * Method:    clearAdditive
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_Additive_clearAdditive
  (JNIEnv *env, jobject this) {

    PWAddContext context = _get_additive_context(env, this);
    if (JNI(ExceptionCheck)) {
        return;
    }

    if (context == 0) {
        JNI(ThrowNew, jcls_rte, "Double free detected. The Additive has already been cleared");
        return;
    }

    pw_clearAdditive(context);

    _set_additive_context(env, this, 0);
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

    JAVA_LOG(PWL_DEBUG, "Deinitializing JNI library");
    _dispose_of_cache_references(env);

    // do not delete reference to jcls_rte, as _check_init uses it
//    if (jcls_rte) {
//        JNI(DeleteGlobalRef, jcls_rte);
//        jcls_rte = NULL;
//    }

    detailed_metrics_deinitialize(env);

    pw_clearAll();
    pw_setupLogging(NULL, PWL_ERROR);

    java_log_shutdown(env);
}

static bool _cache_create_exception(JNIEnv *env)
{
    jclass powerwaf_jclass = JNI(FindClass, "io/sqreen/powerwaf/Powerwaf");
    if (!powerwaf_jclass) {
        return false;
    }

    _create_exception_mid =
            JNI(GetStaticMethodID, powerwaf_jclass, "createException",
                "(I)Lio/sqreen/powerwaf/exception/AbstractPowerwafException;");
    JNI(DeleteLocalRef, powerwaf_jclass);

    return !!_create_exception_mid;
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
         _cache_single_class_weak(env, "java/lang/String", &string_cls);
}

static void _dispose_of_weak_classes(JNIEnv *env)
{
#define DESTROY_CLASS_REF(jcls) \
    if (jcls) { \
        JNI(DeleteWeakGlobalRef, jcls); \
        jcls = NULL; \
    }

  DESTROY_CLASS_REF(string_cls)
    // leave jcls_rte for last in OnUnload; we might still need it
}

static bool _cache_methods(JNIEnv *env)
{
    if (!java_meth_init_checked(
            env, &_additive_init,
            "io/sqreen/powerwaf/Additive", "<init>",
            "(JLjava/lang/String;)V",
            JMETHOD_CONSTRUCTOR)) {
        goto error;
    }

    if (!java_meth_init_checked(env, &to_string, "java/lang/Object", "toString",
                                "()Ljava/lang/String;", JMETHOD_VIRTUAL)) {
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

    if (!java_meth_init_checked(
                env, &_action_with_data_init,
                "io/sqreen/powerwaf/Powerwaf$ActionWithData", "<init>",
                "(Lio/sqreen/powerwaf/Powerwaf$Action;Ljava/lang/String;)V",
                JMETHOD_CONSTRUCTOR)) {
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

    DESTROY_METH(_additive_init)
    DESTROY_METH(to_string)
    DESTROY_METH(number_longValue)
    DESTROY_METH(_action_with_data_init)
    DESTROY_METH(map_entryset)
    DESTROY_METH(map_size)
    DESTROY_METH(entry_key)
    DESTROY_METH(entry_value)
    DESTROY_METH(iterable_iterator)
    DESTROY_METH(iterator_next)
    DESTROY_METH(iterator_hasNext)
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

    if (!_cache_create_exception(env)) {
        goto error;
    }

    if (!_fetch_action_enums(env)) {
        goto error;
    }

    if (!_fetch_additive_fields(env)) {
        goto error;
    }

    if (!_fetch_limit_fields(env)) {
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
    _dispose_of_weak_classes(env);
    _dispose_of_cached_methods(env);
}

static PWArgs _convert_checked(JNIEnv *env, jobject obj,
                               struct _limits *lims,
                               int rec_level)
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

    PWArgs result = _pwinput_invalid;

    if (JNI(IsSameObject, obj, NULL)) {
        result = pw_createMap(); // replace NULLs with empty maps
    } else if (JNI(IsInstanceOf, obj, *map_cls)) {
        result = pw_createMap();
        if (rec_level >= lims->max_depth) {
        JAVA_LOG(PWL_DEBUG,
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
                JAVA_LOG(PWL_DEBUG, "Interrupting map iteration due to the max "
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

            PWArgs value =
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

            pw_addMap(&result, key_cstr, key_len, value);

            free(key_cstr);

            /* doesn't matter if these leak in case of error
             * we do need to delete them in normal circumstances because we're
             * on a loop and we don't want to run out of local refs */
            JNI(DeleteLocalRef, value_obj);
            JNI(DeleteLocalRef, key_jstr);
            JNI(DeleteLocalRef, key_obj);
            JNI(DeleteLocalRef, entry);
        }

        JNI(DeleteLocalRef, entry_set_it);
        JNI(DeleteLocalRef, entry_set);

    } else if (JNI(IsInstanceOf, obj, *iterable_cls)) {
        result = pw_createArray();
        if (rec_level >= lims->max_depth) {
            JAVA_LOG(PWL_DEBUG,
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
                JAVA_LOG(PWL_DEBUG, "Interrupting iterable iteration due to "
                                    "the max of elements being reached");
                break;
            }

            jobject element;
            JAVA_CALL(element, iterator_next, it);

            PWArgs value = _convert_checked(env, element, lims, rec_level + 1);
            if (JNI(ExceptionCheck)) {
                goto error;
            }

            pw_addArray(&result, value);

            JNI(DeleteLocalRef, element);
        }

        JNI(DeleteLocalRef, it);

    } else if (JNI(IsInstanceOf, obj, string_cls)) {
        size_t len;
        char *str_c = java_to_utf8_limited_checked(env, obj, &len,
                                                   lims->max_string_size);
        if (!str_c) {
            goto error;
        }

        result = pw_createStringWithLength(str_c, len);

        free(str_c);

    } else if (JNI(IsInstanceOf, obj, *number_cls)) {
        jlong lval = JNI(CallLongMethod, obj, number_longValue.meth_id);
        if (JNI(ExceptionCheck)) {
            goto error;
        }

        result = pw_createInt(lval);
    }

    // having lael here so if we add cleanup in the future we don't forget
early_return:
    return result;
error:
    pw_freeArg(&result);

    return _pwinput_invalid;
}

static PWAddContext _get_additive_context(JNIEnv *env, jobject additive_obj)
{
    PWAddContext context = NULL;
    context = (PWAddContext)(long)JNI(GetLongField, additive_obj, _additive_ptr);
    if (JNI(ExceptionCheck)) {
        return NULL;
    }
    return context;
}

static bool _set_additive_context(JNIEnv *env, jobject additive_obj, jlong value)
{
    JNI(SetLongField, additive_obj, _additive_ptr, value);
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
    long long val = PW_RUN_TIMEOUT;

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
        JAVA_LOG(PWL_DEBUG, "No property PW_RUN_TIMEOUT; using default %lld",
                 val);
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
        JAVA_LOG(PWL_WARN, "Invalid valid of system property "
                           "PW_RUN_TIMEOUT: '%s'", val_cstr);
        goto end;
    }

    JAVA_LOG(PWL_INFO, "Using value %lld us for PW_RUN_TIMEOUT", val);

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
    JAVA_LOG(PWL_DEBUG, "Conversion of WAF arguments took %" PRId64
            " us; remaining general budget is %" PRId64 " us",
             diff_us, rem_gen_budget_in_us);
    return rem_gen_budget_in_us;
}

static size_t get_run_budget(int64_t rem_gen_budget_in_us, struct _limits *limits) {

    size_t run_budget;
    if (rem_gen_budget_in_us > limits->run_budget_in_us) {
        JAVA_LOG(PWL_DEBUG, "Using run budget of % " PRId64 " us instead of "
                                                            "remaining general budget of %" PRId64 " us",
                 limits->run_budget_in_us, rem_gen_budget_in_us);
        run_budget = (size_t)limits->run_budget_in_us;
    } else {
        run_budget = (size_t)rem_gen_budget_in_us;
    }

    return run_budget;
}
