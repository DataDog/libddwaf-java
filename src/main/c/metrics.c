/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include <ddwaf.h>
#include <jni.h>
#include "logging.h"
#include "metrics.h"

static jfieldID _total_ddwaf_run_time_ns_field;
static jfieldID _total_run_time_ns_field;
static jclass _atomic_long_cls;
static jmethodID _add_and_get;

bool metrics_init(JNIEnv *env)
{
    jclass pwaf_metrics_cls =
            JNI(FindClass, "io/sqreen/powerwaf/PowerwafMetrics");
    if (!pwaf_metrics_cls) {
        return false;
    }

    bool ret = false;

    _total_ddwaf_run_time_ns_field =
            JNI(GetFieldID, pwaf_metrics_cls, "totalDdwafRunTimeNs", "Ljava/util/concurrent/atomic/AtomicLong;");
    if (!_total_ddwaf_run_time_ns_field) {
        goto error;
    }

    _total_run_time_ns_field =
            JNI(GetFieldID, pwaf_metrics_cls, "totalRunTimeNs", "Ljava/util/concurrent/atomic/AtomicLong;");
    if (!_total_run_time_ns_field) {
        goto error;
    }

    _atomic_long_cls = JNI(FindClass, "java/util/concurrent/atomic/AtomicLong");
    if (!_atomic_long_cls) {
        goto error;
    }

    _add_and_get = JNI(GetMethodID, _atomic_long_cls, "addAndGet", "(J)J");
    if (!_add_and_get) {
        goto error;
    }

    ret = true;
error:
    JNI(DeleteLocalRef, pwaf_metrics_cls);
    return ret;
}

void metrics_update_checked(JNIEnv *env, jobject metrics_obj, jlong run_time_ns,
                            jlong ddwaf_run_time_ns)
{
    jobject rt_obj = NULL;
    jobject ddrt_obj = NULL;

    if (JNI(MonitorEnter, metrics_obj) < 0) {
        JNI(ThrowNew, jcls_rte, "Error entering monitor on the metrics object");
        goto error;
    }

    if (run_time_ns > 0) {
        rt_obj = JNI(GetObjectField, metrics_obj, _total_run_time_ns_field);
        if (JNI(ExceptionCheck)) {
            goto error;
        }
        JNI(CallLongMethod, rt_obj, _add_and_get, run_time_ns);
        if (JNI(ExceptionCheck)) {
            goto error;
        }
    }

    ddrt_obj = JNI(GetObjectField, metrics_obj, _total_ddwaf_run_time_ns_field);
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    JNI(CallLongMethod, ddrt_obj, _add_and_get, ddwaf_run_time_ns);
    if (JNI(ExceptionCheck)) {
        goto error;
    }

error:
    if (rt_obj) {
        JNI(DeleteLocalRef, rt_obj);
    }
    if (ddrt_obj) {
        JNI(DeleteLocalRef, ddrt_obj);
    }
    JNI(MonitorExit, metrics_obj);
}
