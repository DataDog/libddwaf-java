/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <inttypes.h>

#include <ddwaf.h>
#include "common.h"
#include "utf16_utf8.h"

typedef struct {
    char *buffer;
    size_t capacity;
    size_t offset;
} hstring;

#define INITIAL_CAPACITY ((size_t)16)

JNIEXPORT jstring JNICALL Java_io_sqreen_powerwaf_Powerwaf_pwArgsBufferToString(
        JNIEnv *, jclass, jobject);

static void _hstring_write_pwargs(hstring *str, size_t depth,
                                  const ddwaf_object *pwargs);

/*
 * Class:     io.sqreen.powerwaf.Powerwaf
 * Method:    pwArgsBufferToString
 * Signature: (Ljava/nio/ByteBuffer;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_io_sqreen_powerwaf_Powerwaf_pwArgsBufferToString(
        JNIEnv *env, jclass clazz, jobject byte_buffer)
{
    (void) clazz;
    void *input_p = JNI(GetDirectBufferAddress, byte_buffer);
    if (!input_p) {
        JNI(ThrowNew, jcls_rte, "Not a DirectBuffer passed");
        return NULL;
    }

    ddwaf_object root;
    memcpy(&root, input_p, sizeof root);
    hstring str = {
        .buffer = malloc(INITIAL_CAPACITY),
        .capacity = INITIAL_CAPACITY
    };
    if (!str.buffer) {
        return NULL;
    }
    _hstring_write_pwargs(&str, 0, &root);
#ifdef __clang_analyzer__
    // due to other exclusions, analyzer doesn't know str.buffer was written
    jstring jstr = NULL;
#else
    jstring jstr = java_utf8_to_jstring_checked(env, str.buffer, str.offset);
#endif
    free(str.buffer);
    return jstr;
}

#if defined(__GNUC__) || defined(__clang__)
# define max(a, b)                                                              \
    ({                                                                         \
        __typeof__(a) _a = (a);                                                \
        __typeof__(b) _b = (b);                                                \
        _a > _b ? _a : _b;                                                     \
    })
#else
// this evaluates a and b twice though
# define max(a, b)  (((a) > (b)) ? (a) : (b))
#endif

static bool _hstring_ensure_extra_capacity(hstring *str, size_t data_size)
{
    size_t left = str->capacity - str->offset;
    if (left < data_size) {
        size_t new_capacity = max(str->capacity * 2, str->capacity + data_size);
        if (new_capacity < str->capacity) { // wrap-around
            return false;
        }
        char *new_buffer = realloc(str->buffer, new_capacity);
        if (!new_buffer) {
            return false;
        }
        str->buffer = new_buffer;
        str->capacity = new_capacity;
    }
    return true;

}
static void _hstring_append(hstring *str, const char *data, size_t data_size)
{
    if (data_size == 0) {
        return;
    }
    if (!_hstring_ensure_extra_capacity(str, data_size)) {
        return;
    }
#ifndef __clang_analyzer__
    // clang analyzer doesn't seem to look into ensure_extra_capacity
    memcpy(str->buffer + str->offset, data, data_size);
#endif
    str->offset += data_size;
}
#define HSTRING_APPEND_CONST(str, constant) \
    do { _hstring_append(str, constant "", sizeof(constant) - 1); } while (0)

static void _hstring_repeat(hstring *str, char c, size_t repeat_times)
{
    if (repeat_times == 0) {
        return;
    }
    if (!_hstring_ensure_extra_capacity(str, repeat_times)) {
        return;
    }
    for (size_t i = 0; i < repeat_times; i++) {
#ifndef __clang_analyzer__
    // clang analyzer doesn't seem to look into ensure_extra_capacity
        str->buffer[str->offset + i] = c;
#endif
    }
    str->offset += repeat_times;
}

static void _hstring_write_pwargs(hstring *str, size_t depth,
                                  const ddwaf_object *pwargs)
{
    if (depth > 25) { // arbitrary cutoff to avoid stackoverflows
        return;
    }
    _hstring_repeat(str, ' ', depth * 2);
    if (pwargs->parameterName) {
        _hstring_append(str, pwargs->parameterName,
                        pwargs->parameterNameLength);
        HSTRING_APPEND_CONST(str, ": ");
    }
    switch (pwargs->type) {
    case DDWAF_OBJ_INVALID:
        HSTRING_APPEND_CONST(str, "<INVALID>\n");
        break;
    case DDWAF_OBJ_SIGNED: {
        HSTRING_APPEND_CONST(str, "<SIGNED> ");
        char scratch[sizeof("-9223372036854775808")];
        int len = snprintf(scratch, sizeof(scratch), "%" PRId64,
                           pwargs->intValue);
        if ((size_t) len < sizeof scratch) {
            _hstring_append(str, scratch, (size_t) len);
        } // else should never happen
        HSTRING_APPEND_CONST(str, "\n");
        break;
    }
    case DDWAF_OBJ_UNSIGNED: {
        HSTRING_APPEND_CONST(str, "<UNSIGNED> ");
        char scratch[sizeof("18446744073709551615")];
        int len = snprintf(scratch, sizeof(scratch), "%" PRIu64,
                           pwargs->uintValue);
        if ((size_t) len < sizeof scratch) {
            _hstring_append(str, scratch, (size_t) len);
        } // else should never happen
        HSTRING_APPEND_CONST(str, "\n");
        break;
    }
    case DDWAF_OBJ_STRING:
        HSTRING_APPEND_CONST(str, "<STRING> ");
        _hstring_append(str, pwargs->stringValue, pwargs->nbEntries);
        HSTRING_APPEND_CONST(str, "\n");
        break;
    case DDWAF_OBJ_ARRAY: {
        HSTRING_APPEND_CONST(str, "<ARRAY>\n");
        for (size_t i = 0; i < pwargs->nbEntries; i++) {
            _hstring_write_pwargs(str, depth + 1, pwargs->array + i);
        }
        break;
    case DDWAF_OBJ_MAP: {
        HSTRING_APPEND_CONST(str, "<MAP>\n");
        for (size_t i = 0; i < pwargs->nbEntries; i++) {
            _hstring_write_pwargs(str, depth + 1, pwargs->array + i);
        }
        break;
    }
    default:
        HSTRING_APPEND_CONST(str, "<UNKNOWN>\n");
        break;
    }
    }
}
