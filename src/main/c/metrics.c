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

    ret = true;
error:
    JNI(DeleteLocalRef, pwaf_metrics_cls);
    return ret;
}

void metrics_update_checked(JNIEnv *env, jobject metrics_obj, jlong run_time_ns,
                            jlong ddwaf_run_time_ns)
{
    if (JNI(MonitorEnter, metrics_obj) < 0) {
        JNI(ThrowNew, jcls_rte, "Error entering monitor on the metrics object");
        goto error;
    }

    if (run_time_ns > 0) {
        jobject rt_obj = JNI(GetObjectField, metrics_obj, _total_run_time_ns_field);
        if (JNI(ExceptionCheck)) {
            goto error;
        }
        jclass atomic_long_cls = JNI(FindClass, "java/util/concurrent/atomic/AtomicLong");
        jmethodID add_and_get = JNI(GetMethodID, atomic_long_cls, "addAndGet", "(J)J");
        JNI(CallLongMethod, rt_obj, add_and_get, run_time_ns);
    }

    jobject ddrt_obj = JNI(GetObjectField, metrics_obj, _total_ddwaf_run_time_ns_field);
    if (JNI(ExceptionCheck)) {
        goto error;
    }
    jclass atomic_long_cls = JNI(FindClass, "java/util/concurrent/atomic/AtomicLong");
    jmethodID add_and_get = JNI(GetMethodID, atomic_long_cls, "addAndGet", "(J)J");
    JNI(CallLongMethod, ddrt_obj, add_and_get, ddwaf_run_time_ns);
    if (JNI(ExceptionCheck)) {
        goto error;
    }

error:
    JNI(MonitorExit, metrics_obj);
}
