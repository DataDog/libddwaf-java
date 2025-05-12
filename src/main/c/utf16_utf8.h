/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#ifndef UTF16_UTF8_H
#define UTF16_UTF8_H

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include "json.h"

void java_utf16_to_utf8_checked(JNIEnv *env, const jchar *in, jsize length,
                                uint8_t **out_p, size_t *out_len_p);
jstring java_utf8_to_jstring_checked(JNIEnv *env, const char *in,
                                     size_t in_len);
char *java_to_utf8_checked(JNIEnv *env, jstring str, size_t *utf8_out_len);
char *java_to_utf8_limited_checked(JNIEnv *env, jstring str, size_t *len,
                                   int max_len);
jstring java_json_to_jstring_checked(JNIEnv *env,
                                     const struct json_segment *seg);

#endif
