/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include <assert.h>
#include <ddwaf.h>
#include <jni.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include "java_call.h"
#include "logging.h"
#include "metrics.h"
#include "common.h"
#include "jni/io_sqreen_powerwaf_PowerwafMetrics.h"

// enough for around 500 rules whose average rule id size is 20
// (maybe mmap a large amount instead?)
#define RESULT_BUFFER_SIZE ((size_t) (500 * (20 * 2 + 4 + 8)))

static jfieldID _ptr_field;
static jfieldID _total_ddwaf_run_time_ns_field;
static jfieldID _copied_results_field;
static struct j_method _buffer_limit;

bool metrics_init(JNIEnv *env)
{
    jclass pwaf_metrics_cls =
            JNI(FindClass, "io/sqreen/powerwaf/PowerwafMetrics");
    if (!pwaf_metrics_cls) {
        return false;
    }

    bool ret = false;

    _ptr_field = JNI(GetFieldID, pwaf_metrics_cls, "ptr", "J");
    if (!_ptr_field) {
        goto error;
    }

    _total_ddwaf_run_time_ns_field =
            JNI(GetFieldID, pwaf_metrics_cls, "totalDdwafRunTimeNs", "J");
    if (!_total_ddwaf_run_time_ns_field) {
        goto error;
    }

    _copied_results_field = JNI(GetFieldID, pwaf_metrics_cls, "copiedResults",
                                "Ljava/nio/ByteBuffer;");
    if (!_copied_results_field) {
        goto error;
    }

    if (!java_meth_init_checked(env, &_buffer_limit, "java/nio/Buffer", "limit",
                                "(I)Ljava/nio/Buffer;", JMETHOD_VIRTUAL)) {
        goto error;
    }

    ret = true;
error:
    JNI(DeleteLocalRef, pwaf_metrics_cls);
    return ret;
}

JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_PowerwafMetrics_init(
        JNIEnv *env, jobject thiz, jobject handle_obj)
{
    ddwaf_handle handle = get_pwaf_handle_checked(env, handle_obj);
    if (!handle) {
        return;
    }

    ddwaf_metrics_collector coll = ddwaf_metrics_collector_init(handle);
    if (!coll) {
        JNI(ThrowNew, jcls_rte, "Failure calling ddwaf_metrics_collector_init");
        return;
    }

    JNI(SetLongField, thiz, _ptr_field, (jlong) (intptr_t) coll);
    if (JNI(ExceptionCheck)) {
        java_wrap_exc("Error setting ptr field in PowerwafMetrics");
    }
}

JNIEXPORT void JNICALL
Java_io_sqreen_powerwaf_PowerwafMetrics_copyResults(JNIEnv *env, jobject thiz)
{
    if (JNI(MonitorEnter, thiz) < 0) {
        JNI(ThrowNew, jcls_rte, "Could not enter monitor");
        return;
    }

    jobject cur_results = JNI(GetObjectField, thiz, _copied_results_field);
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    if (cur_results) {
        JNI(ThrowNew, jcls_rte, "Tried iterating results more than once");
        goto error;
    }

    ddwaf_metrics_collector coll = get_metrics_collector_checked(env, thiz);
    if (!coll) {
        goto error;
    }

    ddwaf_metrics metrics;
    ddwaf_get_metrics(coll, &metrics);

    JNI(SetLongField, thiz, _total_ddwaf_run_time_ns_field,
        (jlong) metrics.total_runtime);
    if (JNI(ExceptionCheck)) {
        goto error;
    }

    char *buffer = malloc(RESULT_BUFFER_SIZE);
    if (!buffer) {
        JNI(ThrowNew, jcls_rte, "malloc failed");
        goto error;
    }
    jobject bb = JNI(NewDirectByteBuffer, buffer, RESULT_BUFFER_SIZE);
    if (!bb) {
        free(buffer);
        if (!JNI(ExceptionCheck)) {
            JNI(ThrowNew, jcls_rte, "NewDirectByteBuffer failed");
        }
        goto error;
    }
    JNI(SetObjectField, thiz, _copied_results_field, bb);
    if (JNI(ExceptionCheck)) {
        free(buffer);
        java_wrap_exc("Error setting copied_results_field");
        goto error;
    }

    assert(metrics.rule_runtime.type == DDWAF_OBJ_MAP);

    char *p = buffer + sizeof(jint); // reserve space for an int

    jint num_rules = (jint) MIN(INT_MAX, metrics.rule_runtime.nbEntries);
    for (jint i = 0; i < num_rules; i++) {
        ddwaf_object *e = &metrics.rule_runtime.array[i];
        assert(e->type == DDWAF_OBJ_UNSIGNED);
        jint rule_id_len = (int) MIN(INT_MAX / 2, e->parameterNameLength);
        size_t left = RESULT_BUFFER_SIZE - (uintptr_t) (p - buffer);
        size_t needed = sizeof(jint) + (size_t) rule_id_len * sizeof(jchar) +
                        sizeof(jlong);
        if (needed > left) {
            num_rules = i;
            break;
        }

        memcpy(p, &(jint){rule_id_len}, sizeof(jint));
        p += sizeof(jint);
        const char *end = p + (size_t) rule_id_len * sizeof(jchar);
        for (int j = 0; p < end; j++, p += sizeof(jchar)) {
            // Assuming only ASCII characters
            memcpy(p, &(jchar){(jchar) e->parameterName[j]}, sizeof(jchar));
        }
        memcpy(p, &(jlong){(jlong) e->uintValue}, sizeof(jlong));
        p += sizeof(jlong);
    }
    ddwaf_metrics_free(&metrics);
    java_meth_call(env, &_buffer_limit, bb, (jint) (intptr_t) (p - buffer));
    if (JNI(ExceptionCheck)) {
        free(buffer);
        goto error;
    }

    memcpy(buffer, &(jint){num_rules}, sizeof(jint));

error:
    JNI(MonitorExit, thiz);
}

JNIEXPORT void JNICALL
Java_io_sqreen_powerwaf_PowerwafMetrics_doClose(JNIEnv *env, jobject thiz)
{
    if (JNI(MonitorEnter, thiz) < 0) {
        JNI(ThrowNew, jcls_rte, "Could not enter monitor");
        return;
    }

    jobject cur_results = JNI(GetObjectField, thiz, _copied_results_field);
    jthrowable exc_bb = JNI(ExceptionOccurred);
    if (exc_bb) {
        JAVA_LOG_THR(DDWAF_LOG_ERROR, exc_bb, "Error reading ByteBuffer field");

        // continue; still try to free the collector behind the ptr field
        JNI(ExceptionClear);
    } else if (cur_results) {
        JNI(SetObjectField, thiz, _copied_results_field, NULL);
        jthrowable thr = JNI(ExceptionOccurred);
        if (thr) {
            JAVA_LOG_THR(DDWAF_LOG_WARN, thr,
                         "Error setting copiedResults to null; calling close "
                         "again may cause crash");
            JNI(ExceptionClear);
        }

        void *buffer = JNI(GetDirectBufferAddress, cur_results);
        thr = JNI(ExceptionOccurred);
        if (thr) {
            JAVA_LOG_THR(DDWAF_LOG_WARN, thr,
                         "Error calling GetDirectBufferAddress");
            JNI(ExceptionClear);
        } else {
            free(buffer);
        }
    }

    ddwaf_metrics_collector coll = get_metrics_collector_checked(env, thiz);
    jthrowable exc_coll = NULL;
    if (coll) {
        ddwaf_metrics_collector_destroy(coll);
        JNI(SetLongField, thiz, _ptr_field, 0L);
        jthrowable thr = JNI(ExceptionOccurred);
        if (thr) {
            JAVA_LOG_THR(DDWAF_LOG_WARN, thr,
                         "Error setting ptr to null; calling close again may "
                         "cause crash");
            JNI(ExceptionClear);
        }
    } else if ((exc_coll = JNI(ExceptionOccurred))) {
        JAVA_LOG_THR(DDWAF_LOG_ERROR, exc_coll,
                     "Error reading metrics collector from pointer");
        JNI(ExceptionClear);
    }

    if (exc_bb) {
        JNI(Throw, exc_bb);
    } else if (exc_coll) {
        JNI(Throw, exc_coll);
    }

    JNI(MonitorExit, thiz);
}

ddwaf_metrics_collector get_metrics_collector_checked(JNIEnv *env,
                                                      jobject metrics_obj)
{
    if (JNI(IsSameObject, metrics_obj, NULL)) {
        JNI(ThrowNew, jcls_iae, "Passed null PowerwafMetrics");
        return NULL;
    }
    ddwaf_metrics_collector coll = (ddwaf_metrics_collector) (intptr_t) JNI(
            GetLongField, metrics_obj, _ptr_field);
    if (JNI(ExceptionCheck)) {
        java_wrap_exc("Error fetching ptr field in PowerwafMetrics");
        return NULL;
    }
    if (!coll) {
        JNI(ThrowNew, jcls_iae,
            "Passed invalid (NULL collector) PowerwafMetrics");
        return NULL;
    }
    return coll;
}
