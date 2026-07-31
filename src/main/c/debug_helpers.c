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

#define INITIAL_CAPACITY ((size_t) 16)

JNIEXPORT jstring JNICALL
Java_com_datadog_ddwaf_Waf_pwArgsBufferToString(JNIEnv *, jclass, jobject);

JNIEXPORT jstring JNICALL
Java_com_datadog_ddwaf_Waf_referenceObjectTreeToString(JNIEnv *, jclass);

static void _hstring_write_pwargs(hstring *str, size_t depth,
                                  const ddwaf_object *key,
                                  const ddwaf_object *pwargs);
static jstring _pwargs_to_jstring(JNIEnv *env, const ddwaf_object *root);

/*
 * Class:     com.datadog.ddwaf.Waf
 * Method:    pwArgsBufferToString
 * Signature: (Ljava/nio/ByteBuffer;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_datadog_ddwaf_Waf_pwArgsBufferToString(
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
    return _pwargs_to_jstring(env, &root);
}

/*
 * Class:     com.datadog.ddwaf.Waf
 * Method:    referenceObjectTreeToString
 * Signature: ()Ljava/lang/String;
 *
 * (FOR TESTING PURPOSES ONLY) Builds a fixed object tree using libddwaf's
 * official ddwaf_object_set_* and ddwaf_object_insert_* API and renders it with
 * the same printer used for pwArgsBufferToString. The Java side builds the
 * equivalent tree with ByteBufferSerializer's hand-rolled layout and compares
 * the two renderings, which detects any divergence between the two
 * construction paths.
 */
JNIEXPORT jstring JNICALL
Java_com_datadog_ddwaf_Waf_referenceObjectTreeToString(JNIEnv *env,
                                                       jclass clazz)
{
    (void) clazz;
    ddwaf_allocator alloc = ddwaf_get_default_allocator();

    ddwaf_object root;
    ddwaf_object_set_map(&root, 8, alloc);

    ddwaf_object_set_string(ddwaf_object_insert_key(&root, LSTR("sstr"), alloc),
                            LSTR("small"), alloc);
    ddwaf_object_set_string(ddwaf_object_insert_key(&root, LSTR("str"), alloc),
                            LSTR("a string longer than 14 bytes"), alloc);
    ddwaf_object_set_signed(
            ddwaf_object_insert_key(&root, LSTR("signed"), alloc), -42);
    ddwaf_object_set_bool(ddwaf_object_insert_key(&root, LSTR("bool"), alloc),
                          true);
    ddwaf_object_set_float(ddwaf_object_insert_key(&root, LSTR("float"), alloc),
                           8.5);
    ddwaf_object_set_null(ddwaf_object_insert_key(&root, LSTR("null"), alloc));

    ddwaf_object *arr = ddwaf_object_insert_key(&root, LSTR("array"), alloc);
    ddwaf_object_set_array(arr, 2, alloc);
    ddwaf_object_set_signed(ddwaf_object_insert(arr, alloc), 1);
    ddwaf_object_set_string(ddwaf_object_insert(arr, alloc), LSTR("two"),
                            alloc);

    ddwaf_object *nested = ddwaf_object_insert_key(&root, LSTR("map"), alloc);
    ddwaf_object_set_map(nested, 1, alloc);
    ddwaf_object_set_string(
            ddwaf_object_insert_key(nested, LSTR("inner"), alloc),
            LSTR("value"), alloc);

    jstring jstr = _pwargs_to_jstring(env, &root);
    ddwaf_object_destroy(&root, alloc);
    return jstr;
}

static jstring _pwargs_to_jstring(JNIEnv *env, const ddwaf_object *root)
{
    hstring str = {.buffer = malloc(INITIAL_CAPACITY),
                   .capacity = INITIAL_CAPACITY};
    if (!str.buffer) {
        return NULL;
    }
    _hstring_write_pwargs(&str, 0, NULL, root);
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
#define max(a, b)                                                              \
    ({                                                                         \
        __typeof__(a) _a = (a);                                                \
        __typeof__(b) _b = (b);                                                \
        _a > _b ? _a : _b;                                                     \
    })
#else
// this evaluates a and b twice though
#define max(a, b) (((a) > (b)) ? (a) : (b))
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
#define HSTRING_APPEND_CONST(str, constant)                                    \
    do {                                                                       \
        _hstring_append(str, constant "", sizeof(constant) - 1);               \
    } while (0)

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
                                  const ddwaf_object *key,
                                  const ddwaf_object *pwargs)
{
    if (depth > 25 || pwargs == NULL) { // arbitrary cutoff to avoid
                                        // stackoverflows
        return;
    }
    _hstring_repeat(str, ' ', depth * 2);
    if (key != NULL && ddwaf_object_is_string(key)) {
        size_t key_len;
        const char *key_str = ddwaf_object_get_string(key, &key_len);
        if (key_str) {
            _hstring_append(str, key_str, key_len);
        }
        HSTRING_APPEND_CONST(str, ": ");
    }

    // small and literal strings are printed like regular strings; the
    // distinction is a storage detail
    if (ddwaf_object_is_string(pwargs)) {
        HSTRING_APPEND_CONST(str, "<STRING> ");
        size_t len;
        const char *value = ddwaf_object_get_string(pwargs, &len);
        if (value) {
            _hstring_append(str, value, len);
        }
        HSTRING_APPEND_CONST(str, "\n");
        return;
    }

    switch (ddwaf_object_get_type(pwargs)) {
    case DDWAF_OBJ_INVALID:
        HSTRING_APPEND_CONST(str, "<INVALID>\n");
        break;
    case DDWAF_OBJ_NULL:
        HSTRING_APPEND_CONST(str, "<NULL>\n");
        break;
    case DDWAF_OBJ_SIGNED: {
        HSTRING_APPEND_CONST(str, "<SIGNED> ");
        char scratch[sizeof("-9223372036854775808")];
        int len = snprintf(scratch, sizeof(scratch), "%" PRId64,
                           ddwaf_object_get_signed(pwargs));
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
                           ddwaf_object_get_unsigned(pwargs));
        if ((size_t) len < sizeof scratch) {
            _hstring_append(str, scratch, (size_t) len);
        } // else should never happen
        HSTRING_APPEND_CONST(str, "\n");
        break;
    }
    case DDWAF_OBJ_FLOAT: {
        HSTRING_APPEND_CONST(str, "<FLOAT> ");
        char scratch[sizeof("6.324040266767955765e-322")];
        int len = snprintf(scratch, sizeof(scratch), "%.18e",
                           ddwaf_object_get_float(pwargs));
        if ((size_t) len < sizeof scratch) {
            _hstring_append(str, scratch, (size_t) len);
        } // else should never happen
        HSTRING_APPEND_CONST(str, "\n");
        break;
    }
    case DDWAF_OBJ_BOOL:
        HSTRING_APPEND_CONST(str, "<BOOL> ");
        if (ddwaf_object_get_bool(pwargs)) {
            HSTRING_APPEND_CONST(str, "true\n");
        } else {
            HSTRING_APPEND_CONST(str, "false\n");
        }
        break;
    case DDWAF_OBJ_ARRAY: {
        HSTRING_APPEND_CONST(str, "<ARRAY>\n");
        size_t size = ddwaf_object_get_size(pwargs);
        for (size_t i = 0; i < size; i++) {
            _hstring_write_pwargs(str, depth + 1, NULL,
                                  ddwaf_object_at_value(pwargs, i));
        }
        break;
    }
    case DDWAF_OBJ_MAP: {
        HSTRING_APPEND_CONST(str, "<MAP>\n");
        size_t size = ddwaf_object_get_size(pwargs);
        for (size_t i = 0; i < size; i++) {
            _hstring_write_pwargs(str, depth + 1,
                                  ddwaf_object_at_key(pwargs, i),
                                  ddwaf_object_at_value(pwargs, i));
        }
        break;
    }
    default:
        HSTRING_APPEND_CONST(str, "<UNKNOWN>\n");
        break;
    }
}
