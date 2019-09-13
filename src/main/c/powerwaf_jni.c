#include "io_sqreen_powerwaf_Powerwaf.h"
#include "common.h"
#include "java_call.h"
#include "utf16_utf8.h"
#include "logging.h"
#include "windows_compat.h"
#include <PowerWAF.h>
#include <assert.h>
#include <string.h>

#define MAX_CONVERT_REC_LEVEL 10

// suffix _checked means if a function fails it leaves a pending exception
static bool _check_init(JNIEnv *env);
static void _deinitialize(JNIEnv *env);
static char *_to_utf8_checked(JNIEnv *env, jstring str, size_t *len);
static bool _cache_references(JNIEnv *env);
static void _dispose_of_action_enums(JNIEnv *env);
static void _dispose_of_cache_references(JNIEnv *env);
static PWArgs _convert_checked(JNIEnv *env, jobject obj, int rec_level);

static const PWArgs _pwinput_invalid = { .type = PWI_INVALID };

jclass jcls_rte;
jmethodID rte_constr_cause;

static jmethodID _create_exception_mid;
static jobject _action_ok;
static jobject _action_monitor;
static jobject _action_block;
static struct j_method _action_with_data_init;

static jclass _string_cls;
static struct j_method _to_string;

static struct j_method _number_longValue;
static jclass *_number_cls = &_number_longValue.class_glob;

static struct j_method _map_entryset;
static jclass *_map_cls = &_map_entryset.class_glob;
static struct j_method _entry_key;
static struct j_method _entry_value;

static struct j_method _iterable_iterator;
static jclass *_iterable_cls = &_iterable_iterator.class_glob;
static struct j_method _iterator_next;
static struct j_method _iterator_hasNext;

static bool _init_ok;

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

    rule_name_c = _to_utf8_checked(env, rule_name, NULL);
    if (!rule_name_c) {
        goto end;
    }
    rule_def_c = _to_utf8_checked(env, rule_def, NULL);
    if (!rule_def_c) {
        goto end;
    }

    result = powerwaf_initializePowerWAF(rule_name_c, rule_def_c);

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

    char *rule_name_c = _to_utf8_checked(env, rule_name, NULL);
    if (!rule_name_c) {
        return;
    }

    powerwaf_clearRule(rule_name_c);
    free(rule_name_c);
}

/*
 * Class:     io.sqreen.powerwaf.Powerwaf
 * Method:    runRule
 * Signature: (Ljava/lang/String;Ljava/util/Map;J)Lio/sqreen/powerwaf/Powerwaf/ActionWithData;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Powerwaf_runRule(
        JNIEnv *env, jclass clazz,
        jstring rule_name, jobject parameters, jlong budget_in_us)
{
    jobject result = NULL;
    char *rule_name_c = NULL;
    PWArgs input = { .type = PWI_INVALID };
    PWRet *ret = NULL;

    if (!_check_init(env)) {
        return NULL;
    }

    rule_name_c = _to_utf8_checked(env, rule_name, NULL);
    if (!rule_name_c) {
        goto end;
    }

    input = _convert_checked(env, parameters, 0);
    if (JNI(ExceptionCheck)) {
        goto end;
    }

    ret = powerwaf_runPowerWAF(
                rule_name_c, &input, (size_t)budget_in_us);

    if (ret->action < 0 || ret->action > 2) {
        jobject exc = JNI(CallStaticObjectMethod,
                          clazz, _create_exception_mid, (jint) ret->action);
        if (!JNI(ExceptionOccurred)) {
            JNI(Throw, exc);
        } // if an exception occurred calling createException, let it propagate
        goto end;
    }

    jobject action_obj;
    if (ret->action == PW_GOOD) {
        action_obj = _action_ok;
    } else if (ret->action == PW_MONITOR) {
        action_obj = _action_monitor;
    } else {
        assert(ret->action == PW_BLOCK);
        action_obj = _action_block;
    }
    jstring data_obj = NULL;
    if (ret->data) {
        // no length, so the string must be NUL-terminated
        data_obj = java_utf8_to_jstring_checked(
                    env, ret->data, strlen(ret->data));
        if (!data_obj) {
            if (!JNI(ExceptionCheck)) {
                JNI(ThrowNew, jcls_rte, "Could not create result data string");
            }
            goto end;
        }
    }

    result = java_meth_call(env, &_action_with_data_init, NULL,
                            action_obj, data_obj);

    JNI(DeleteLocalRef, data_obj);

end:
    free(rule_name_c);
    powerwaf_freeInput(&input, false);
    if (ret) {
        powerwaf_freeReturn(ret);
    }
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

    PWVersion iversion = powerwaf_getVersion();
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
    if (_init_ok) {
        JAVA_LOG(PWL_DEBUG, "Deinitializing JNI library");
        _dispose_of_cache_references(env);
    }

    // do not delete reference to jcls_rte, as _check_init uses it
//    if (jcls_rte) {
//        JNI(DeleteGlobalRef, jcls_rte);
//        jcls_rte = NULL;
//    }

    java_log_shutdown(env);

    _init_ok = false;
    powerwaf_clearAll();
    powerwaf_setupLogging(NULL, PWL_ERROR);
}

// sets a pending exception in case of failure
static char *_to_utf8_checked(JNIEnv *env, jstring str, size_t *len)
{
    const jint utf16_len = JNI(GetStringLength, str);
    if (JNI(ExceptionCheck)) {
        java_wrap_exc("Error getting the length of putative string");
        return NULL;
    }

    const jchar *utf16_str = JNI(GetStringChars, str, NULL);
    if (!utf16_str) {
        if (!JNI(ExceptionCheck)) {
            JNI(ThrowNew, jcls_rte, "Error calling GetStringChars");
        } else {
            java_wrap_exc("Error calling GetStringChars on string of size %d",
                          utf16_len);
        }
        return NULL;
    }

    uint8_t *out = NULL;
    java_utf16_to_utf8_checked(env, utf16_str, utf16_len, &out, len);

    JNI(ReleaseStringChars, str, utf16_str);

    return (char *)out;
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

    return true;

error:
    _dispose_of_action_enums(env);
    return false;
}

static void _dispose_of_action_enums(JNIEnv *env)
{
    if (_action_ok) {
        JNI(DeleteGlobalRef, _action_ok);
        _action_ok = NULL;
    }
    if (_action_monitor) {
        JNI(DeleteGlobalRef, _action_monitor);
        _action_monitor = NULL;
    }
    if (_action_block) {
        JNI(DeleteGlobalRef, _action_block);
        _action_block = NULL;
    }
}

static bool _cache_single_class(JNIEnv *env,
                                const char *class_name,
                                jclass *out)
{
    jclass cls_local = JNI(FindClass, class_name);
    if (!cls_local) {
        java_wrap_exc("Could not find class %s", class_name);
        return false;
    }

    *out = JNI(NewGlobalRef, cls_local);
    JNI(DeleteLocalRef, cls_local);

    if (!*out) {
        // out of memory
        return false;
    }

    return true;
}

static bool _cache_classes(JNIEnv *env)
{
    return _cache_single_class(env, "java/lang/RuntimeException", &jcls_rte) &&
            _cache_single_class(env, "java/lang/String", &_string_cls);
}

static void _dispose_of_classes(JNIEnv *env)
{
#define DESTROY_CLASS_REF(jcls) \
    if (jcls) { \
        JNI(DeleteGlobalRef, jcls); \
        jcls = NULL; \
    }

    DESTROY_CLASS_REF(_string_cls)
    // leave jcls_rte for last in OnUnload; we might still need it
}

static bool _cache_methods(JNIEnv *env)
{
    if (!java_meth_init_checked(
                env, &_to_string,
                "java/lang/Object", "toString",
                "()Ljava/lang/String;",
                JMETHOD_VIRTUAL)) {
        goto error;
    }

    // we use non_virtual so a global reference to the class is stored
    if (!java_meth_init_checked(
                env, &_number_longValue,
                "java/lang/Number", "longValue",
                "()J",
                JMETHOD_NON_VIRTUAL)) {
        goto error;
    }
    _number_longValue.type = JMETHOD_VIRTUAL;

    if (!java_meth_init_checked(
                env, &_action_with_data_init,
                "io/sqreen/powerwaf/Powerwaf$ActionWithData", "<init>",
                "(Lio/sqreen/powerwaf/Powerwaf$Action;Ljava/lang/String;)V",
                JMETHOD_CONSTRUCTOR)) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &_map_entryset,
                "java/util/Map", "entrySet",
                "()Ljava/util/Set;", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }
    _map_entryset.type = JMETHOD_VIRTUAL;

    if (!java_meth_init_checked(
                env, &_entry_key,
                "java/util/Map$Entry", "getKey",
                "()Ljava/lang/Object;", JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &_entry_value,
                "java/util/Map$Entry", "getValue",
                "()Ljava/lang/Object;", JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &_iterable_iterator,
                "java/lang/Iterable", "iterator",
                "()Ljava/util/Iterator;", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }
    _iterable_iterator.type = JMETHOD_VIRTUAL;

    if (!java_meth_init_checked(
                env, &_iterator_next,
                "java/util/Iterator", "next",
                "()Ljava/lang/Object;", JMETHOD_VIRTUAL)) {
        goto error;
    }

    if (!java_meth_init_checked(
                env, &_iterator_hasNext,
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

    DESTROY_METH(_to_string)
    DESTROY_METH(_number_longValue)
    DESTROY_METH(_action_with_data_init)
    DESTROY_METH(_map_entryset)
    DESTROY_METH(_entry_key)
    DESTROY_METH(_entry_value)
    DESTROY_METH(_iterable_iterator)
    DESTROY_METH(_iterator_next)
    DESTROY_METH(_iterator_hasNext)
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
    _dispose_of_classes(env);
    _dispose_of_cached_methods(env);
}

static PWArgs _convert_checked(JNIEnv *env, jobject obj, int rec_level)
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

    /* this function can only fail in two situations:
     * 1) maximum depth exceeded or
     * 2) a java method call or another JNI calls throws.
     *
     * It particular, it doesn't "fail" if any of the powerwaf_* functions
     * fail. It never checks their return values. The assumption is that
     * any failure will be an extraordinary circumstance and that the
     * implementation is designed in such a way that passing NULL pointers
     * or PWI_INVALID objects doesn't cause crashes */

    if (rec_level > MAX_CONVERT_REC_LEVEL) {
        JNI(ThrowNew, jcls_rte, "Maximum recursion level exceeded");
        goto error;
    }

    PWArgs result = _pwinput_invalid;

    if (JNI(IsSameObject, obj, NULL)) {
        result = powerwaf_createMap(); // replace NULLs with empty maps
    } else if (JNI(IsInstanceOf, obj, *_map_cls)) {
        result = powerwaf_createMap();

        jobject entry_set, entry_set_it;
        JAVA_CALL(entry_set,    _map_entryset, obj);
        JAVA_CALL(entry_set_it, _iterable_iterator, entry_set);

        while (JNI(CallBooleanMethod, entry_set_it,
                   _iterator_hasNext.meth_id)) {
            if (JNI(ExceptionCheck)) {
                goto error;
            }
            jobject entry, key_obj, value_obj;
            jstring key_jstr;

            JAVA_CALL(entry, _iterator_next, entry_set_it);
            JAVA_CALL(key_obj, _entry_key, entry);
            JAVA_CALL_ERR_MSG(key_jstr, _to_string, key_obj,
                        "Error calling toString() on map key");
            JAVA_CALL(value_obj, _entry_value, entry);

            PWArgs value = _convert_checked(env, value_obj, rec_level + 1);
            if (JNI(ExceptionCheck)) {
                goto error;
            }

            size_t key_len;
            char *key_cstr = _to_utf8_checked(env, key_jstr, &key_len);
            if (JNI(ExceptionCheck)) {
                goto error;
            }

            powerwaf_addToPWArgsMap(&result, key_cstr, key_len, value);

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

    } else if (JNI(IsInstanceOf, obj, *_iterable_cls)) {
        result = powerwaf_createArray();

        jobject it;
        JAVA_CALL(it, _iterable_iterator, obj);
        while (JNI(CallBooleanMethod, it, _iterator_hasNext.meth_id)) {
            if (JNI(ExceptionCheck)) {
                goto error;
            }
            jobject element;
            JAVA_CALL(element, _iterator_next, it);

            PWArgs value = _convert_checked(env, element, rec_level + 1);
            if (JNI(ExceptionCheck)) {
                goto error;
            }

            powerwaf_addToPWArgsArray(&result, value);

            JNI(DeleteLocalRef, element);
        }

        JNI(DeleteLocalRef, it);

    } else if (JNI(IsInstanceOf, obj, _string_cls)) {
        size_t len;
        char *str_c = _to_utf8_checked(env, obj, &len);
        if (JNI(ExceptionCheck)) {
            goto error;
        }

        result = powerwaf_createStringWithLength(str_c, len);

        free(str_c);

    } else if (JNI(IsInstanceOf, obj, *_number_cls)) {
        jlong lval = JNI(CallLongMethod, obj, _number_longValue.meth_id);
        if (JNI(ExceptionCheck)) {
            goto error;
        }

        result = powerwaf_createInt(lval);
    }

    return result;
error:
    powerwaf_freeInput(&result, false);

    return _pwinput_invalid;
}
