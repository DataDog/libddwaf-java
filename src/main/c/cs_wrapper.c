/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include <jni.h>
#include <stdbool.h>
#include "common.h"
#include "logging.h"
#include "cs_wrapper.h"
#include "jni/com_datadog_ddwaf_CharSequenceWrapper.h"

static bool _active;
static jfieldID _scb_str;
static jfieldID _cb_offset;
static jfieldID _b_mark;
static jfieldID _b_position;
static jfieldID _b_limit;
static jfieldID _b_capacity;
static jmethodID _cs_length;

void cs_wrapper_init(JNIEnv *env)
{
    jclass scb_cls, cb_cls = NULL, b_cls = NULL, cs_cls = NULL;
    scb_cls = JNI(FindClass, "java/nio/StringCharBuffer");
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    cb_cls = JNI(FindClass, "java/nio/CharBuffer");
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    b_cls = JNI(FindClass, "java/nio/Buffer");
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    cs_cls = JNI(FindClass, "java/lang/CharSequence");
    if (JNI(ExceptionCheck)) {
        goto error;
    }

#define GET_FIELD(var, cls, name, descr)                                       \
    do {                                                                       \
        var = JNI(GetFieldID, scb_cls, name, descr);                           \
        if (JNI(ExceptionCheck)) {                                             \
            goto error;                                                        \
        }                                                                      \
    } while (0)

    GET_FIELD(_scb_str, scb_cls, "str", "Ljava/lang/CharSequence;");
    GET_FIELD(_cb_offset, cb_cls, "offset", "I");
    GET_FIELD(_b_mark, b_cls, "mark", "I");
    GET_FIELD(_b_position, b_cls, "position", "I");
    GET_FIELD(_b_limit, b_cls, "limit", "I");
    GET_FIELD(_b_capacity, b_cls, "capacity", "I");

#undef GET_FIELD
    _cs_length = JNI(GetMethodID, cs_cls, "length", "()I");
    if (JNI(ExceptionOccurred)) {
        goto error;
    }

    _active = true;

error:
    if (!_active) {
        JAVA_LOG_THR(DDWAF_LOG_WARN, JNI(ExceptionOccurred),
                     "Error initializing CharSequenceWrapper native method");
        JNI(ExceptionClear);
    }
    if (scb_cls) {
        JNI(DeleteLocalRef, scb_cls);
    }
    if (cb_cls) {
        JNI(DeleteLocalRef, cb_cls);
    }
    if (b_cls) {
        JNI(DeleteLocalRef, b_cls);
    }
    if (_cs_length) {
        JNI(DeleteLocalRef, cs_cls);
    }
}

JNIEXPORT void JNICALL Java_com_datadog_ddwaf_CharSequenceWrapper_resetState(
        JNIEnv *env, jclass clazz, jobject scb, jobject cs)
{
    UNUSED(clazz);
    if (!_active) {
        JNI(ThrowNew, jcls_rte, "Initialization failed earlier");
        return;
    }

    JNI(SetObjectField, scb, _scb_str, cs);
    if (JNI(ExceptionOccurred)) {
        return;
    }
    JNI(SetIntField, scb, _cb_offset, 0);
    if (JNI(ExceptionOccurred)) {
        return;
    }
    JNI(SetIntField, scb, _b_mark, -1);
    if (JNI(ExceptionOccurred)) {
        return;
    }
    JNI(SetIntField, scb, _b_position, 0);
    if (JNI(ExceptionOccurred)) {
        return;
    }

    jint length = JNI(CallIntMethod, cs, _cs_length);
    if (JNI(ExceptionOccurred)) {
        return;
    }
    JNI(SetIntField, scb, _b_limit, length);
    if (JNI(ExceptionOccurred)) {
        return;
    }
    JNI(SetIntField, scb, _b_capacity, length);
    if (JNI(ExceptionOccurred)) {
        return;
    }
}
