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
#include "logging.h"
#include "metrics.h"
#include "common.h"
#include "jni/io_sqreen_powerwaf_PowerwafMetrics.h"

// enough for around 500 rules whose average rule id size is 20
// (maybe mmap a large amount instead?)
#define RESULT_BUFFER_SIZE ((size_t)(500 * (20 * 2 + 4 + 8)))

static jfieldID _ptr_field;
static jfieldID _total_ddwaf_run_time_micros_field;
static jfieldID _copied_results_field;

static ddwaf_metrics_collector
_get_metrics_collector_checked(JNIEnv *env, jobject metrics_obj);

bool metrics_init(JNIEnv *env)
{
    jclass clazz = JNI(FindClass, "io/sqreen/powerwaf/PowerwafMetrics");
    if (!clazz) {
        return false;
    }
    bool ret = false;

    _ptr_field = JNI(GetFieldID, clazz, "ptr", "J");
    if (!_ptr_field) {
        goto error;
    }

    _total_ddwaf_run_time_micros_field =
            JNI(GetFieldID, clazz, "totalDdwafRunTimeMicros", "J");
    if (!_total_ddwaf_run_time_micros_field) {
        goto error;
    }

    _copied_results_field =
            JNI(GetFieldID, clazz, "copiedResults", "Ljava/nio/ByteBuffer;");
    if (!_copied_results_field) {
        goto error;
    }

    ret = true;
error:
    JNI(DeleteLocalRef, clazz);
    if (!ret) {
        metrics_dispose();
    }
    return ret;
}

void metrics_dispose() {

}

JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_PowerwafMetrics_init(
    JNIEnv * env, jobject thiz, jobject handle_obj)
{
    ddwaf_handle handle = get_pwaf_handle_checked(env, handle_obj);
    if (!handle) {
        return;
    }

    ddwaf_metrics_collector coll = ddwaf_metrics_collector_init(handle);
    if (!coll) {
        JNI(ThrowNew, jcls_rte, "Failure calling ddwaf_metrics_collector_init");
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
        JNI(ThrowNew, jcls_rte, "Could not acquire monitor");
        return;
    }

    jobject cur_results = JNI(GetObjectField, thiz, _copied_results_field);
    if (cur_results) {
        JNI(ThrowNew, jcls_rte, "Tried iterating results more than once");
    }

    ddwaf_metrics_collector coll = _get_metrics_collector_checked(env, thiz);
    if (!coll) {
        goto error;
    }

    ddwaf_metrics metrics;
    ddwaf_get_metrics(coll, &metrics);

    JNI(SetLongField, thiz, _total_ddwaf_run_time_micros_field,
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
        size_t left = RESULT_BUFFER_SIZE - (uintptr_t)(p - buffer);
        size_t needed = 4 + (size_t)rule_id_len * 2 + 8;
        if (needed > left) {
            num_rules = i;
            break;
        }

        memcpy(p, &(jint){rule_id_len}, sizeof(jint));
        p += sizeof(jint);
        const char *end = p + rule_id_len * 2;
        for (int j = 0; p < end; j++, p += 2) {
            p[0] = '\0';
            p[1] = e->parameterName[j];
        }
    }
    ddwaf_metrics_free(&metrics);

    memcpy(buffer, &(jint){num_rules}, sizeof(jint));

error:
    JNI(MonitorExit, thiz);
}

JNIEXPORT void JNICALL
Java_io_sqreen_powerwaf_PowerwafMetrics_close(JNIEnv *env, jobject thiz)
{
    if (JNI(MonitorEnter, thiz) < 0) {
        JNI(ThrowNew, jcls_rte, "Could not acquire monitor");
        return;
    }

    jobject cur_results = JNI(GetObjectField, thiz, _copied_results_field);
    if (cur_results) {
        JNI(SetObjectField, thiz, _copied_results_field, NULL);
        if (JNI(ExceptionCheck)) {
            goto error;
        }

        void *buffer = JNI(GetDirectBufferAddress, cur_results);
        if (buffer) {
            free(buffer);
        }
    }

    ddwaf_metrics_collector coll = _get_metrics_collector_checked(env, thiz);
    if (coll) {
        ddwaf_metrics_collector_destroy(coll);
        JNI(SetLongField, thiz, _ptr_field, 0L);
    }

error:
    JNI(MonitorExit, thiz);
}

static ddwaf_metrics_collector
_get_metrics_collector_checked(JNIEnv *env, jobject metrics_obj)
{
    if (JNI(IsSameObject, metrics_obj, NULL)) {
        JNI(ThrowNew, jcls_iae, "Passed null PowerwafMetrics");
        return NULL;
    }
    ddwaf_metrics_collector coll = (ddwaf_metrics_collector) (intptr_t) JNI(
            GetLongField, metrics_obj, _ptr_field);
    if (!coll) {
        JNI(ThrowNew, jcls_iae, "Passed invalid (NULL) PowerwafMetrics");
        return NULL;
    }
    return coll;
}
