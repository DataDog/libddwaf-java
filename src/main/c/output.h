/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2023 Datadog, Inc.
 */

#pragma once

#include <jni.h>
#include <ddwaf.h>

#include "json.h"

// Schemas: https://github.com/DataDog/libddwaf/tree/master/schema

void output_init_checked(JNIEnv *env);
void output_shutdown(JNIEnv *env);
jobject output_convert_diagnostics_checked(JNIEnv *env,
                                           const ddwaf_object *obj);

struct json_segment *output_convert_json(const ddwaf_object *obj);
struct json_segment *output_convert_json(const ddwaf_object *obj);
jobject output_convert_attributes_checked(JNIEnv *env, const ddwaf_object *obj);
jobject convert_ddwaf_object_to_jobject(JNIEnv *env, const ddwaf_object *obj);
