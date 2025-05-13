/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#ifndef COMMON_H
#define COMMON_H

#include <jni.h>
#include <ddwaf.h>
#include "java_call.h"

#define JNI(method, ...) (*env)->method(env, ##__VA_ARGS__)
#ifdef UNUSED
#undef UNUSED
#endif
#define UNUSED(var) (void) var

#define MIN(x, y) (((x) < (y)) ? (x) : (y))

extern jclass jcls_rte;
extern jclass jcls_iae;
extern jmethodID rte_constr_cause;

extern jclass charSequence_cls;
extern struct j_method charSequence_length;
extern struct j_method charSequence_subSequence;

extern jclass buffer_cls;
extern struct j_method buffer_position;
extern struct j_method buffer_limit;

extern jclass charBuffer_cls;
extern struct j_method charBuffer_hasArray;
extern struct j_method charBuffer_array;

extern jclass string_cls;
extern struct j_method to_string;

extern struct j_method number_longValue;
extern struct j_method number_doubleValue;
// weak, but assumed never to be gced
extern jclass *number_cls;

extern struct j_method map_entryset;
// weak, but assumed never to be gced
extern jclass *map_cls;
extern struct j_method map_size;
extern struct j_method entry_key;
extern struct j_method entry_value;

extern struct j_method iterable_iterator;
extern jclass *iterable_cls;
extern struct j_method iterator_next;
extern struct j_method iterator_hasNext;

extern struct j_method class_is_array;

ddwaf_handle get_pwaf_handle_checked(JNIEnv *env, jobject handle_obj);

#endif
