/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include "java_call.h"
#include "common.h"
#include "logging.h"
#include <assert.h>

bool java_meth_init_checked(JNIEnv *env, struct j_method *jmeth,
                            const char *class_name, const char *method_name,
                            const char *sig, enum j_method_type type)
{
    bool result = false;
    jmethodID meth_id;
    jclass class_global = NULL;

    jclass class_local = JNI(FindClass, class_name);
    if (!class_local) {
        assert(JNI(ExceptionCheck));
        java_wrap_exc("Could not find class %s", class_name);
        goto end;
    }

    if (type == JMETHOD_CONSTRUCTOR) {
        method_name = "<init>";
    }

    if (type != JMETHOD_VIRTUAL) {
        class_global = JNI(NewWeakGlobalRef, class_local);
        if (!class_global) {
            if (!JNI(ExceptionCheck)) {
                JNI(ThrowNew, jcls_rte, "Failed creating global reference");
            }
            goto end;
        }
    }

    if (type == JMETHOD_STATIC) {
        meth_id = JNI(GetStaticMethodID, class_local, method_name, sig);
    } else {
        meth_id = JNI(GetMethodID, class_local, method_name, sig);
    }

    if (!meth_id) {
        assert(JNI(ExceptionCheck));
        java_wrap_exc("Could not find method %s.%s%s", class_name, method_name,
                      sig);
        goto end;
    }

    *jmeth = (struct j_method){
            .type = type != JMETHOD_VIRTUAL_RETRIEVE_CLASS ? type
                                                           : JMETHOD_VIRTUAL,
            .class_glob = class_global,
            .meth_id = meth_id,
    };
    result = true;

end:
    if (class_local) {
        JNI(DeleteLocalRef, class_local);
    }

    return result;
}

jobject java_meth_call(JNIEnv *env, const struct j_method *jmeth,
                       jobject receiver, ...)
{
    if (jmeth->type == JMETHOD_UNINITIALIZED) {
        JNI(ThrowNew, jcls_rte, "j_method unitialized");
        return NULL;
    }

    jobject ret = NULL;
    jclass class_strongref = NULL;
    if (jmeth->type != JMETHOD_VIRTUAL) {
        class_strongref = JNI(NewLocalRef, jmeth->class_glob);
        if (!class_strongref) {
            if (!JNI(ExceptionCheck)) {
                JNI(ThrowNew, jcls_rte, "Cached class was garbage collected");
            }
            return NULL;
        }
    }

    va_list args;
    va_start(args, receiver);
    if (jmeth->type == JMETHOD_CONSTRUCTOR) {
        ret = JNI(NewObjectV, class_strongref, jmeth->meth_id, args);
    } else if (jmeth->type == JMETHOD_STATIC) {
        ret = JNI(CallStaticObjectMethodV, class_strongref, jmeth->meth_id,
                  args);
    } else if (jmeth->type == JMETHOD_NON_VIRTUAL) {
        ret = JNI(CallNonvirtualObjectMethodV, receiver, class_strongref,
                  jmeth->meth_id, args);
    } else { // JMETHOD_VIRTUAL
        ret = JNI(CallObjectMethodV, receiver, jmeth->meth_id, args);
    }

    va_end(args);

    if (class_strongref) {
        JNI(DeleteLocalRef, class_strongref);
    }

    return ret;
}

void java_meth_destroy(JNIEnv *env, struct j_method *jmeth)
{
    if (jmeth->class_glob) {
        JNI(DeleteWeakGlobalRef, jmeth->class_glob);
        jmeth->class_glob = NULL;
    }
}

// returns a global reference
jobject java_static_field_checked(JNIEnv *env, jclass clazz, const char *name,
                                  const char *sig)
{
    jobject local_obj = NULL, global_obj = NULL;

    jfieldID id = JNI(GetStaticFieldID, clazz, name, sig);
    if (!id) {
        goto error;
    }

    local_obj = JNI(GetStaticObjectField, clazz, id);
    if (JNI(ExceptionCheck)) {
        goto error;
    }

    global_obj = JNI(NewWeakGlobalRef, local_obj);
    if (!global_obj) {
        // out of memory
        goto error;
    }

error:
    if (local_obj) {
        JNI(DeleteLocalRef, local_obj);
    }
    if (!global_obj) {
        if (JNI(ExceptionCheck)) {
            java_wrap_exc("Error fetching static field %s%s", name, sig);
        } else {
            JNI(ThrowNew, jcls_rte, "Could not fetch field");
        }
    }
    return global_obj;
}
