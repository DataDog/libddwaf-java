#ifndef JAVA_CALL_H
#define JAVA_CALL_H

#include <jni.h>
#include <stdbool.h>

enum j_method_type {
    JMETHOD_UNINITIALIZED,
    JMETHOD_STATIC,
    JMETHOD_NON_VIRTUAL,
    JMETHOD_CONSTRUCTOR,
    JMETHOD_VIRTUAL,
};

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wpadded"
struct j_method {
    enum j_method_type type;
    jclass class_glob;
    jmethodID meth_id;
};
#pragma clang diagnostic pop

bool java_meth_init_checked(
        JNIEnv *env,
        struct j_method *jmeth,
        const char *class_name,
        const char *method_name, const char *sig,
        enum j_method_type type);

void java_meth_destroy(JNIEnv *env, struct j_method *jmeth);

jobject java_meth_call(JNIEnv *env,
                       const struct j_method *jmeth,
                       jobject receiver,
                       ...);

#endif
