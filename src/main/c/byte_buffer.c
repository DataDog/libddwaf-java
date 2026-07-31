/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include <jni.h>
#include <ddwaf.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include "common.h"
#include "ddwaf_layout.h"
#include "jni/com_datadog_ddwaf_ByteBufferSerializer.h"

JNIEXPORT jlong JNICALL
Java_com_datadog_ddwaf_ByteBufferSerializer_getByteBufferAddress(JNIEnv *env,
                                                                 jclass clazz,
                                                                 jobject bb)
{
    (void) clazz;
    void *addr = JNI(GetDirectBufferAddress, bb);
    jlong ret;
    memcpy(&ret, &addr, sizeof ret);
    return ret;
}

/*
 * Class:     com_datadog_ddwaf_ByteBufferSerializer
 * Method:    getNativeObjectLayout
 * Signature: ()[I
 *
 * Reports the real layout of ddwaf_object / ddwaf_object_kv so that
 * ByteBufferSerializer, which hand-rolls the very same layout in Java in order
 * to serialize with no copies, can validate its constants at initialization
 * time instead of trusting them blindly. Keep in sync with
 * ByteBufferSerializer.checkNativeLayout().
 */
JNIEXPORT jintArray JNICALL
Java_com_datadog_ddwaf_ByteBufferSerializer_getNativeObjectLayout(JNIEnv *env,
                                                                  jclass clazz)
{
    (void) clazz;

    const jint layout[] = {
            (jint) sizeof(ddwaf_object),
            (jint) sizeof(ddwaf_object_kv),
            (jint) offsetof(ddwaf_object_kv, key),
            (jint) offsetof(ddwaf_object_kv, val),
            (jint) DDWAF_OBJ_OFF_TYPE,
            (jint) DDWAF_OBJ_OFF_BOOL_VAL,
            (jint) DDWAF_OBJ_OFF_NUM_VAL,
            (jint) DDWAF_OBJ_OFF_STR_SIZE,
            (jint) DDWAF_OBJ_OFF_STR_PTR,
            (jint) DDWAF_OBJ_OFF_SSTR_SIZE,
            (jint) DDWAF_OBJ_OFF_SSTR_DATA,
            (jint) DDWAF_OBJ_SSTR_SIZE,
            (jint) DDWAF_OBJ_OFF_CONTAINER_SIZE,
            (jint) DDWAF_OBJ_OFF_CONTAINER_CAPACITY,
            (jint) DDWAF_OBJ_OFF_CONTAINER_PTR,
            (jint) DDWAF_OBJ_INVALID,
            (jint) DDWAF_OBJ_NULL,
            (jint) DDWAF_OBJ_BOOL,
            (jint) DDWAF_OBJ_SIGNED,
            (jint) DDWAF_OBJ_UNSIGNED,
            (jint) DDWAF_OBJ_FLOAT,
            (jint) DDWAF_OBJ_STRING,
            (jint) DDWAF_OBJ_LITERAL_STRING,
            (jint) DDWAF_OBJ_SMALL_STRING,
            (jint) DDWAF_OBJ_ARRAY,
            (jint) DDWAF_OBJ_MAP,
    };
    const jsize count = (jsize) (sizeof(layout) / sizeof(layout[0]));

    jintArray ret = JNI(NewIntArray, count);
    if (!ret) {
        return NULL;
    }
    JNI(SetIntArrayRegion, ret, 0, count, layout);
    if (JNI(ExceptionCheck)) {
        return NULL;
    }
    return ret;
}
