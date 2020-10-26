#include "io_sqreen_detailed_metrics_RequestDataCollection.h"
#include "detailed_metrics.h"
#include "common.h"
#include "logging.h"
#include "java_call.h"
#include "utf16_utf8.h"
#include <stdbool.h>
#include <assert.h>
#include <limits.h>
#include <metrics_capi.h>

static struct j_method _ju_collection_size;
static jclass *_ju_collection_jclass = &_ju_collection_size.class_glob;
static jclass _double_jclass;
static jclass _float_jclass;

static struct j_method _ihm_containsKey;
static struct j_method _ihm_put;
static struct j_method _ihm_constructor;
static jclass *_ihm_jclass = &_ihm_containsKey.class_glob;

static struct j_method _class_getName;
static struct j_method _boolean_booleanValue;

static jfieldID _collection_requests;

static jclass _collection_jclass;
static jclass _request_jclass;
static jclass _meas_jclass;
static jclass _sc_jclass;

static jfieldID _req_route;
static jfieldID _req_overtime_cb;
static jfieldID _req_measurements;
static jfieldID _req_slow_calls;

static jfieldID _meas_callback;
static jfieldID _meas_timing;

static jfieldID _sc_callback;
static jfieldID _sc_timing;
static jfieldID _sc_arguments;

static jclass _find_class_checked(JNIEnv *env, const char *name)
{
    jclass class_local = JNI(FindClass, name);
    if (!class_local) {
        assert(JNI(ExceptionCheck));
        return NULL;
    }

    jclass class_global = JNI(NewWeakGlobalRef, class_local);
    JNI(DeleteLocalRef, class_local);
    if (!class_global) {
        if (!JNI(ExceptionCheck)) {
            JNI(ThrowNew, jcls_rte, "Failed creating global reference");
        }
    }
    return class_global;
}

bool detailed_metrics_cache_global_references(JNIEnv *env)
{
    bool ret = false;

    // java.util.Collection
    if (!java_meth_init_checked(env, &_ju_collection_size,
                                "java/util/Collection", "size", "()I",
                                JMETHOD_VIRTUAL_RETRIEVE_CLASS)) {
        goto error;
    }

    _double_jclass = _find_class_checked(env, "java/lang/Double");
    if (!_double_jclass) {
        goto error;
    }
    _float_jclass = _find_class_checked(env, "java/lang/Float");
    if (!_float_jclass) {
        goto error;
    }

    // java.util.IdentityHashMap
    if (!java_meth_init_checked(env, &_ihm_containsKey,
                                "java/util/IdentityHashMap", "containsKey",
                                "(Ljava/lang/Object;)Z", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }
    if (!java_meth_init_checked(env, &_ihm_constructor,
                                "java/util/IdentityHashMap", "<init>",
                                "()V", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }
    if (!java_meth_init_checked(env, &_ihm_put,
                                "java/util/IdentityHashMap", "put",
                                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                                JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    // java.lang.Class
    if (!java_meth_init_checked(env, &_class_getName,
                                "java/lang/Class", "getName",
                                "()Ljava/lang/String;", JMETHOD_VIRTUAL)) {
        goto error;
    }

    // java.lang.Boolean
    if (!java_meth_init_checked(env, &_boolean_booleanValue,
                                "java/lang/Boolean", "booleanValue",
                                "()Z", JMETHOD_NON_VIRTUAL)) {
        goto error;
    }

    // request collection
    _collection_jclass = _find_class_checked(
            env, "io/sqreen/detailed_metrics/RequestDataCollection");
    if (!_collection_jclass) {
        goto error;
    }

    _collection_requests = JNI(GetFieldID, _collection_jclass, "requests", "Ljava/util/List;");
    if (!_collection_requests) {
        goto error;
    }

    // request
    _request_jclass =
            _find_class_checked(env, "io/sqreen/detailed_metrics/RequestData");
    if (!_request_jclass) {
        goto error;
    }

    _req_route = JNI(GetFieldID, _request_jclass, "route", "Ljava/lang/String;");
    if (!_req_route) {
        goto error;
    }
    _req_overtime_cb = JNI(GetFieldID, _request_jclass, "overtimeCallback", "Ljava/lang/String;");
    if (!_req_overtime_cb) {
        goto error;
    }
    _req_measurements = JNI(GetFieldID, _request_jclass, "measurements", "Ljava/util/List;");
    if (!_req_measurements) {
        goto error;
    }
    _req_slow_calls = JNI(GetFieldID, _request_jclass, "slowCalls", "Ljava/util/List;");
    if (!_req_slow_calls) {
        goto error;
    }

    // measurement
    _meas_jclass = _find_class_checked(
            env, "io/sqreen/detailed_metrics/RequestData$Measurement");
    if (!_meas_jclass) {
        goto error;
    }

    _meas_callback = JNI(GetFieldID, _meas_jclass, "callback", "Ljava/lang/String;");
    if (!_meas_callback) {
        goto error;
    }
    _meas_timing = JNI(GetFieldID, _meas_jclass, "timing", "F");
    if (!_meas_timing) {
        goto error;
    }

    // slow call
    _sc_jclass = _find_class_checked(
            env, "io/sqreen/detailed_metrics/RequestData$SlowCall");
    if (!_sc_jclass) {
        goto error;
    }
    _sc_callback = JNI(GetFieldID, _sc_jclass, "callback", "Ljava/lang/String;");
    if (!_sc_callback) {
        goto error;
    }
    _sc_timing = JNI(GetFieldID, _sc_jclass, "timing", "F");
    if (!_sc_timing) {
        goto error;
    }
    _sc_arguments = JNI(GetFieldID, _sc_jclass, "arguments", "[Ljava/lang/Object;");
    if (!_sc_arguments) {
        goto error;
    }

    ret = true;

error:
    return ret;
}

void detailed_metrics_deinitialize(JNIEnv *env)
{
    java_meth_destroy(env, &_ju_collection_size);
    java_meth_destroy(env, &_class_getName);
    java_meth_destroy(env, &_boolean_booleanValue);
    java_meth_destroy(env, &_ihm_containsKey);
    java_meth_destroy(env, &_ihm_constructor);
    java_meth_destroy(env, &_ihm_put);

    if (_double_jclass) {
        JNI(DeleteWeakGlobalRef, _double_jclass);
    }
    if (_float_jclass) {
        JNI(DeleteWeakGlobalRef, _float_jclass);
    }
    if (_collection_jclass) {
        JNI(DeleteWeakGlobalRef, _collection_jclass);
    }
    if (_request_jclass) {
        JNI(DeleteWeakGlobalRef, _request_jclass);
    }
    if (_meas_jclass) {
        JNI(DeleteWeakGlobalRef, _meas_jclass);
    }
    if (_sc_jclass) {
        JNI(DeleteWeakGlobalRef, _sc_jclass);
    }
}
#define RET_IF_EXC() do { if (JNI(ExceptionCheck)) { goto error; } } while (0)
#define RET_IF_EXC_L(label) do { if (JNI(ExceptionCheck)) { goto label; } } while (0)
#define JAVA_CALL(var, meth, recv) \
    do { \
        var = java_meth_call(env, &(meth), (recv)); \
        RET_IF_EXC(); \
    } while (0)

#define JAVA_CALL_L(label, var, meth, recv) \
    do { \
        var = java_meth_call(env, &(meth), (recv)); \
        RET_IF_EXC_L(label); \
    } while (0)

static bool _saw_object(JNIEnv *env, jobject id_hm, jobject needle)
{
    jboolean res = JNI(CallNonvirtualBooleanMethod, id_hm,
                       *_ihm_jclass, _ihm_containsKey.meth_id, needle);
    if (JNI(ExceptionCheck)) {
        jthrowable thr = JNI(ExceptionOccurred);
        JNI(ExceptionClear);
        JAVA_LOG_THR(PWL_WARN, thr, "Error looking up IdentityHashMap");
        return false;
    }

    return res;
}

static void _mark_object_seen(JNIEnv *env, jobject id_hm, jobject obj)
{
    JNI(CallNonvirtualVoidMethod, id_hm, *_ihm_jclass, _ihm_put.meth_id,
        obj, NULL);
    if (JNI(ExceptionCheck)) {
        jthrowable thr = JNI(ExceptionOccurred);
        JNI(ExceptionClear);
        JAVA_LOG_THR(PWL_WARN, thr, "Error adding value to IdentityHashMap");
    }
}

#define MAX_DEPTH 10
#define MAX_DEPTH_EXCEEDED_MSG "<max depth exceeded>"
#define MAX_ELEMENTS_EXCEEDED_MSG "<max elements exceeded>"
#define SELF_REFERENCE_COLL_MSG "<self-reference to collection>"
#define SELF_REFERENCE_MAP_MSG "<self-reference to map>"
// this function must delete all local references even in case of error
static void _convert_checked(JNIEnv *env, jobject obj,
                             perf2_argb_t *arg,
                             jobject id_hm,
                             int64_t *max_elements,
                             int rec_level)
{
    (*max_elements)--;

    if (rec_level > MAX_DEPTH) {
        perf2_argb_add_string(arg, MAX_DEPTH_EXCEEDED_MSG,
                              sizeof(MAX_DEPTH_EXCEEDED_MSG) - 1);
        return;
    }

    if (*max_elements <= 0) {
        perf2_argb_add_string(arg, MAX_ELEMENTS_EXCEEDED_MSG,
                              sizeof(MAX_ELEMENTS_EXCEEDED_MSG) - 1);
        return;
    }

    if (JNI(IsSameObject, obj, NULL)) {
        perf2_argb_add_null(arg);
    } else if (JNI(IsInstanceOf, obj, *map_cls)) {
        if (_saw_object(env, id_hm, obj)) {
            perf2_argb_add_string(arg, SELF_REFERENCE_MAP_MSG,
                                sizeof(SELF_REFERENCE_MAP_MSG) - 1);
            return;
        }
        _mark_object_seen(env, id_hm, obj);

        jint size = JNI(CallIntMethod, obj, map_size.meth_id);
        RET_IF_EXC();
        perf2_argb_start_map(arg, (size_t)size);

        jobject entry_set, entry_set_it;
        JAVA_CALL(entry_set,    map_entryset, obj);
        entry_set_it = java_meth_call(env, &iterable_iterator, entry_set);
        JNI(DeleteLocalRef, entry_set);
        RET_IF_EXC();

        while (JNI(CallBooleanMethod, entry_set_it, iterator_hasNext.meth_id)) {
            RET_IF_EXC_L(del_map_it);

            jobject entry = NULL,
                    key_obj = NULL,
                    value_obj = NULL;

            JAVA_CALL_L(del_map_loop_vars, entry, iterator_next, entry_set_it);
            JAVA_CALL_L(del_map_loop_vars, key_obj, entry_key, entry);
            JAVA_CALL_L(del_map_loop_vars, value_obj, entry_value, entry);

            _convert_checked(
                    env, key_obj, arg, id_hm, max_elements, rec_level + 1);
            RET_IF_EXC_L(del_map_loop_vars);

            _convert_checked(
                    env, value_obj, arg, id_hm, max_elements, rec_level + 1);
            RET_IF_EXC_L(del_map_loop_vars);

del_map_loop_vars:
            if (entry) {
                JNI(DeleteLocalRef, entry);
            }
            if (value_obj) {
                JNI(DeleteLocalRef, value_obj);
            }
            if (key_obj) {
                JNI(DeleteLocalRef, key_obj);
            }

            if (JNI(ExceptionCheck)) {
                goto del_map_it;
            }
        }
        JNI(ExceptionCheck); // avoid JNI check warnings

del_map_it:
        JNI(DeleteLocalRef, entry_set_it);

    } else if (JNI(IsInstanceOf, obj, *_ju_collection_jclass)) {
        if (_saw_object(env, id_hm, obj)) {
            perf2_argb_add_string(arg, SELF_REFERENCE_COLL_MSG,
                                sizeof(SELF_REFERENCE_COLL_MSG) - 1);
            return;
        }
        _mark_object_seen(env, id_hm, obj);

        jint size = JNI(CallIntMethod, obj, _ju_collection_size.meth_id);
        RET_IF_EXC();
        perf2_argb_start_array(arg, (size_t)size);

        jobject it;
        JAVA_CALL(it, iterable_iterator, obj);
        while (JNI(CallBooleanMethod, it, iterator_hasNext.meth_id)) {
            RET_IF_EXC_L(del_coll_it);

            jobject element;
            JAVA_CALL_L(del_coll_it, element, iterator_next, it);

            _convert_checked(
                    env, element, arg, id_hm, max_elements, rec_level + 1);
            JNI(DeleteLocalRef, element);
            RET_IF_EXC_L(del_coll_it);
        }
        JNI(ExceptionCheck); // avoid JNI check warnings

del_coll_it:
        JNI(DeleteLocalRef, it);

    } else if (JNI(IsInstanceOf, obj, string_cls)) {
        size_t len;
        char *str_c = java_to_utf8_checked(env, obj, &len);
        if (!str_c) {
            goto error;
        }

        perf2_argb_add_string(arg, str_c, len);

        free(str_c);
    } else if (JNI(IsInstanceOf, obj, _boolean_booleanValue.class_glob)) {
        jboolean bval = JNI(CallNonvirtualBooleanMethod, obj,
                            _boolean_booleanValue.class_glob,
                            _boolean_booleanValue.meth_id);
        RET_IF_EXC();

        perf2_argb_add_bool(arg, bval);
    } else if (JNI(IsInstanceOf, obj, _float_jclass) ||
            JNI(IsInstanceOf, obj, _double_jclass)) {
        jdouble dval = JNI(CallDoubleMethod, obj, number_doubleValue.meth_id);
        RET_IF_EXC();

        perf2_argb_add_double(arg, dval);
    } else if (JNI(IsInstanceOf, obj, *number_cls)) {
        jlong lval = JNI(CallLongMethod, obj, number_longValue.meth_id);
        RET_IF_EXC();

        perf2_argb_add_int64(arg, lval);
    } else {
        jclass obj_class = JNI(GetObjectClass, obj);
        if (!obj_class) {
            if (!JNI(ExceptionCheck)) {
                JNI(ThrowNew, jcls_rte, "Could not get class of object");
                goto error;
            }
        }

        jstring name_jstr = java_meth_call(env, &_class_getName, obj_class);
        if (JNI(ExceptionCheck)) {
            java_wrap_exc("Could not get name of class");
            goto error;
        }

        char *name = java_to_utf8_checked(env, name_jstr, &(size_t) {0});
        JNI(DeleteLocalRef, name_jstr);
        if (!name) {
            JNI(ThrowNew, jcls_rte,
                "Could not convert class name to utf-8 string");
            goto error;
        }

        char *final_str;
        int final_str_len = asprintf(
            &final_str, "object of type %s", name);
        free(name);
        if (final_str_len < 0) {
            JNI(ThrowNew, jcls_rte, "Could not format string");
            goto error;
        }

        perf2_argb_add_string(arg, final_str, (size_t)final_str_len);
        free(final_str);
    }
error:
    ;
}

static void _convert_meas_checked(JNIEnv *env, jobject jmeas, perf2_req_t *req)
{
    if (!JNI(IsInstanceOf, jmeas, _meas_jclass)) {
        JNI(ThrowNew, jcls_rte, "List has a non-Measurement element");
        return;
    }

    char *callback_cstr;
    size_t callback_len;

    jstring jcallback = JNI(GetObjectField, jmeas, _meas_callback);
    if (!jcallback) {
        JNI(ThrowNew, jcls_rte, "Measurement without callback");
        return;
    } else {
        if ((callback_cstr =
                 java_to_utf8_checked(env, jcallback, &callback_len)) == NULL) {
            return;
        }
        JNI(DeleteLocalRef, jcallback);
    }

    jfloat timing = JNI(GetFloatField, jmeas, _meas_timing);
    perf2_req_add_measurement(req, callback_cstr, callback_len,
                              (double)timing, false);
    free(callback_cstr);
}

static void _convert_slow_call_checked(JNIEnv *env, jobject jsc,
                                       perf2_req_t *req)
{
    if (!JNI(IsInstanceOf, jsc, _sc_jclass)) {
        JNI(ThrowNew, jcls_rte, "List has a non-SlowCall element");
        return;
    }

    perf2_argb_t **args = NULL;
    jsize args_size = 0;
    char *callback_cstr;
    size_t callback_len;

    jstring jcallback = JNI(GetObjectField, jsc, _sc_callback);
    if (!jcallback) {
        JNI(ThrowNew, jcls_rte, "SlowCall without callback");
        return;
    } else {
        if ((callback_cstr =
                java_to_utf8_checked(env, jcallback, &callback_len)) == NULL) {
            return;
        }
        JNI(DeleteLocalRef, jcallback);
    }

    jfloat timing = JNI(GetFloatField, jsc, _sc_timing);

    jobjectArray jargs = JNI(GetObjectField, jsc, _sc_arguments);
    if (!jargs) {
        JNI(ThrowNew, jcls_rte, "SlowCall without arguments");
        goto error;
    }

    args_size = JNI(GetArrayLength, jargs);
    if (args_size > (jsize)UINT8_MAX) {
        args_size = 255;
    }
    args = calloc((size_t)args_size, sizeof *args);
    for (jsize i = 0; i < args_size; i++) {
        jobject a = JNI(GetObjectArrayElement, jargs, i);
        RET_IF_EXC();

        jobject seen = JNI(NewObject, *_ihm_jclass, _ihm_constructor.meth_id);
        if (JNI(ExceptionCheck)) {
            java_wrap_exc("Error creating IdentityHashMap");
            goto error;
        }

        args[i] = perf2_argb_new();
        int64_t max_elements = 300;
        _convert_checked(env, a, args[i], seen, &max_elements, 1);

        JNI(DeleteLocalRef, seen);
        JNI(DeleteLocalRef, a);
        if (JNI(ExceptionCheck)) { // _convert_checked failed
            jthrowable thr = JNI(ExceptionOccurred);
            JNI(ExceptionClear);
            JAVA_LOG_THR(PWL_WARN, thr,
                         "Error converting argument #%d of slow call",
                         (int)i + 1);
            perf2_argb_free(args[i]);
            args[i] = perf2_argb_new();
            perf2_argb_add_string(args[i], "<error during conversion>",
                                  sizeof("<error during conversion>") - 1);
        }
    }
    JNI(DeleteLocalRef, jargs);

    perf2_req_add_slow_call(req, callback_cstr, callback_len,
                            (double)timing, false, args, (uint8_t)args_size);
error:
    for (jsize i = 0; i < args_size; i++) {
        perf2_argb_free(args[i]);
    }
    free(args);
    free(callback_cstr);
}

static void _convert_req(JNIEnv *env, jobject jreq, perf2_req_t *req)
{
    if (!JNI(IsInstanceOf, jreq, _request_jclass)) {
        JNI(ThrowNew, jcls_rte, "List has a non-Request element");
        return;
    }

    jstring jroute = JNI(GetObjectField, jreq, _req_route);
    if (jroute) {
        char *route_cstr;
        size_t len;

        if ((route_cstr = java_to_utf8_checked(env, jroute, &len)) == NULL) {
            return;
        }
        perf2_req_set_route(req, route_cstr, len);
        free(route_cstr);
        JNI(DeleteLocalRef, jroute);
    } // else leave route blank

    jstring jovertime_cb = JNI(GetObjectField, jreq, _req_overtime_cb);
    if (jovertime_cb) {
        char *overtime_cb_cstr;
        size_t len;

        if ((overtime_cb_cstr = java_to_utf8_checked(env, jovertime_cb, &len))
                == NULL) {
            return;
        }
        perf2_req_set_overtime_cb(req, overtime_cb_cstr, len);
        free(overtime_cb_cstr);
        JNI(DeleteLocalRef, jovertime_cb);
    }

    {
        jobject jmeasurements = JNI(GetObjectField, jreq, _req_measurements);
        jobject it = java_meth_call(env, &iterable_iterator, jmeasurements);
        RET_IF_EXC();
        while (JNI(CallBooleanMethod, it, iterator_hasNext.meth_id)) {
            RET_IF_EXC();

            jobject jmeas = java_meth_call(env, &iterator_next, it);
            if (JNI(ExceptionCheck)) {
                return;
            }

            _convert_meas_checked(env, jmeas, req);
            JNI(DeleteLocalRef, jmeas);
            RET_IF_EXC();
        }
        RET_IF_EXC();
        JNI(DeleteLocalRef, it);
    }

    {
        jobject jslow_calls = JNI(GetObjectField, jreq, _req_slow_calls);
        jobject it = java_meth_call(env, &iterable_iterator, jslow_calls);
        RET_IF_EXC();
        while (JNI(CallBooleanMethod, it, iterator_hasNext.meth_id)) {
            RET_IF_EXC();
            
            jobject jsc = java_meth_call(env, &iterator_next, it);
            RET_IF_EXC();

            _convert_slow_call_checked(env, jsc, req);

            JNI(DeleteLocalRef, jsc);
            RET_IF_EXC();
        }
        RET_IF_EXC();
        JNI(DeleteLocalRef, it);
    }
error:
    ;
}

JNIEXPORT jbyteArray JNICALL Java_io_sqreen_detailed_1metrics_RequestDataCollection_serialize(
        JNIEnv *env, jobject thiz)
{
    jbyteArray ret = NULL;
    perf2_coll_t *coll = perf2_coll_new();
    perf2_req_t *req = perf2_req_new();

    jobject requests = JNI(GetObjectField, thiz, _collection_requests);
    RET_IF_EXC();

    jobject it = java_meth_call(env, &iterable_iterator, requests);
    RET_IF_EXC();
    
    while (JNI(CallBooleanMethod, it, iterator_hasNext.meth_id)) {
        RET_IF_EXC();

        jobject jreq = java_meth_call(env, &iterator_next, it);
        RET_IF_EXC();

        _convert_req(env, jreq, req);

        JNI(DeleteLocalRef, jreq);
        RET_IF_EXC();

        perf2_coll_add_and_clear(coll, req);
    }
    RET_IF_EXC();
    JNI(DeleteLocalRef, it);

    perf2_data_t data = perf2_coll_flush(coll);
    if (data.len > (size_t)INT_MAX) { // jsize is int
        perf2_data_destroy(data);
        JNI(ThrowNew, jcls_rte, "data is too long");
        goto error;
    }

    ret = JNI(NewByteArray, (jsize)data.len);
    if (!ret) {
        goto error;
    }

    JNI(SetByteArrayRegion, ret, 0, (jsize)data.len, (const jbyte *)data.data);
    perf2_data_destroy(data);

error:
    perf2_coll_free(coll);
    perf2_req_free(req);
    return ret;
}
