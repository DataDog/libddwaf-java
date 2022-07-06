/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#pragma once

#include <ddwaf.h>
#include <jni.h>
#include <stdbool.h>

bool metrics_init(JNIEnv *env);
void metrics_update_checked(JNIEnv *env, jobject metrics_obj, jlong run_time_ns,
                            jlong ddwaf_run_time_ns);
