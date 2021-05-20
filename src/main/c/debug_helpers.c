#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <inttypes.h>

#include <PowerWAF.h>
#include "common.h"
#include "utf16_utf8.h"

typedef struct {
    char *buffer;
    size_t capacity;
    size_t offset;
} hstring;

static void _hstring_write_pwargs(hstring *str, size_t depth,
                                  const PWArgs *pwargs);

/*
 * Class:     io.sqreen.powerwaf.Powerwaf
 * Method:    pwArgsBufferToJson
 * Signature: (Ljava/nio/ByteBuffer;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_io_sqreen_powerwaf_Powerwaf_pwArgsBufferToJson(
    JNIEnv *env, jclass clazz, jobject byte_buffer)
{
    void *input_p = JNI(GetDirectBufferAddress, byte_buffer);
    if (!input_p) {
        JNI(ThrowNew, jcls_rte, "Not a DirectBuffer passed");
        return NULL;
    }

    PWArgs root;
    memcpy(&root, input_p, sizeof root);
    hstring str = {0};
    _hstring_write_pwargs(&str, 0, &root);
    jstring jstr = java_utf8_to_jstring_checked(env, str.buffer, str.offset);
    free(str.buffer);
    return jstr;
}

#define max(a, b)                                                              \
    ({                                                                         \
        __typeof__(a) _a = (a);                                                \
        __typeof__(b) _b = (b);                                                \
        _a > _b ? _a : _b;                                                     \
    })

static bool _hstring_ensure_extra_capacity(hstring *str, size_t data_size)
{
    size_t left = str->capacity - str->offset;
    if (left < data_size) {
        size_t new_capacity = max(str->capacity * 2, str->capacity + data_size);
        if (new_capacity < str->capacity) { // wrap-around
            return false;
        }
        str->buffer = realloc(str->buffer, new_capacity);
        str->capacity = new_capacity;
    }
    return true;

}
static void _hstring_append(hstring *str, const char *data, size_t data_size)
{
    if (!_hstring_ensure_extra_capacity(str, data_size)) {
        return;
    }
    memcpy(str->buffer + str->offset, data, data_size);
    str->offset += data_size;
}
#define HSTRING_APPEND_CONST(str, constant) \
    do { _hstring_append(str, constant "", sizeof(constant) - 1); } while (0)

static void _hstring_repeat(hstring *str, char c, size_t repeat_times)
{
    if (!_hstring_ensure_extra_capacity(str, repeat_times)) {
        return;
    }
    for (size_t i = 0; i < repeat_times; i++) {
        str->buffer[str->offset + i] = c;
    }
    str->offset += repeat_times;
}

static void _hstring_write_pwargs(hstring *str, size_t depth,
                                  const PWArgs *pwargs)
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
    case PWI_INVALID:
        HSTRING_APPEND_CONST(str, "<INVALID>\n");
        break;
    case PWI_SIGNED_NUMBER: {
        HSTRING_APPEND_CONST(str, "<SIGNED> ");
        char scratch[sizeof("-9223372036854775808")];
        int len = sprintf(scratch, "%" PRId64, pwargs->intValue);
        _hstring_append(str, scratch, (size_t) len);
        HSTRING_APPEND_CONST(str, "\n");
        break;
    }
    case PWI_UNSIGNED_NUMBER: {
        HSTRING_APPEND_CONST(str, "<UNSIGNED> ");
        char scratch[sizeof("18446744073709551615")];
        int len = sprintf(scratch, "%" PRIu64, pwargs->uintValue);
        _hstring_append(str, scratch, (size_t) len);
        HSTRING_APPEND_CONST(str, "\n");
        break;
    }
    case PWI_STRING:
        HSTRING_APPEND_CONST(str, "<STRING> ");
        _hstring_append(str, pwargs->stringValue, pwargs->nbEntries);
        HSTRING_APPEND_CONST(str, "\n");
        break;
    case PWI_ARRAY: {
        HSTRING_APPEND_CONST(str, "<ARRAY>\n");
        for (size_t i = 0; i < pwargs->nbEntries; i++) {
            _hstring_write_pwargs(str, depth + 1, pwargs->array + i);
        }
        break;
    case PWI_MAP: {
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
