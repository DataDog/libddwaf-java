/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include "utf16_utf8.h"
#include "common.h"
#include "json.h"
#include "logging.h"
#include <stdbool.h>
#include <limits.h>
#include <assert.h>

/* adapted from PHP code in ext/standard/html.c
 * (that I wrote back in the day, so no copyright problems) */

#define MB_FAILURE(pos, advance) do { \
    *cursor = pos + (advance); \
    *status = false; \
    return 0; \
} while (0)

#define CHECK_LEN(pos, chars_need) ((str_len - (pos)) >= (chars_need))

#define UTF16_LEAD(c) ((c) >= 0xD800 && (c) <= 0xDBFF)
#define UTF16_TRAIL(c) ((c) >= 0xDC00 && (c) <= 0xDFFF)

/* valid as single byte character or leading byte */
#define UTF8_LEAD(c)  ((c) < 0x80 || ((c) >= 0xC2 && (c) <= 0xF4))
/* whether it's actually valid depends on other stuff;
 * this macro cannot check for non-shortest forms, surrogates or
 * code points above 0x10FFFF */
#define UTF8_TRAIL(c) ((c) >= 0x80 && (c) <= 0xBF)

#define REPL_CHAR 0xFFFDU

static inline uint32_t _get_next_codepoint_utf16(
        const jchar *str, // jchar is unsigned
        size_t str_len /* in code units */,
        size_t *cursor,
        bool *status)
{
    size_t pos = *cursor;
    uint32_t codepoint;

    *status = true;
    assert(pos <= str_len);
    if (!CHECK_LEN(pos, 1)) {
        MB_FAILURE(pos, 1);
    }

    jchar c = str[pos];
    if (!UTF16_LEAD(c)) {
        if (UTF16_TRAIL(c)) {
            MB_FAILURE(pos, 1);
        }
        codepoint = c;
        pos++;
    } else { // leading code unit
        if (!CHECK_LEN(pos, 2)) {
            // leading code unit without pair (strings ends)
            MB_FAILURE(pos, 1);
        }

        jchar c2 = str[pos + 1];
        if (!UTF16_TRAIL(c2)) {
            MB_FAILURE(pos, 1);
        }

        codepoint = 0x10000 + ((c - 0xD800) * 0x400 + (c2 - 0xDC00));
        pos += 2;
    }

    *cursor = pos;
    return codepoint;
}

static inline uint32_t _get_next_codepoint_utf8(
        const uint8_t *str,
        size_t str_len,
        size_t *cursor,
        bool *status)
{
    /* We'll follow strategy 2. from section 3.6.1 of UTR #36:
     * "In a reported illegal byte sequence, do not include any
     *  non-initial byte that encodes a valid character or is a leading
     *  byte for a valid sequence." */
    size_t pos = *cursor;
    uint32_t codepoint;
    uint8_t c;

    *status = true;

    c = str[pos];
    if (c < 0x80) {
        codepoint = c;
        pos++;
    } else if (c < 0xc2) {
        MB_FAILURE(pos, 1);
    } else if (c < 0xe0) {
        if (!CHECK_LEN(pos, 2))
            MB_FAILURE(pos, 1);

        if (!UTF8_TRAIL(str[pos + 1])) {
            MB_FAILURE(pos, UTF8_LEAD(str[pos + 1]) ? 1 : 2);
        }
        codepoint = ((c & 0x1fU) << 6) | (str[pos + 1] & 0x3f);
        if (codepoint < 0x80) { /* non-shortest form */
            MB_FAILURE(pos, 2);
        }
        pos += 2;
    } else if (c < 0xf0) {
        size_t avail = str_len - pos;

        if (avail < 3 ||
                !UTF8_TRAIL(str[pos + 1]) || !UTF8_TRAIL(str[pos + 2])) {
            if (avail < 2 || UTF8_LEAD(str[pos + 1]))
                MB_FAILURE(pos, 1);
            else if (avail < 3 || UTF8_LEAD(str[pos + 2]))
                MB_FAILURE(pos, 2);
            else
                MB_FAILURE(pos, 3);
        }

        codepoint = ((c & 0x0fU) << 12) | ((str[pos + 1] & 0x3fU) << 6) | (str[pos + 2] & 0x3f);
        if (codepoint < 0x800) { /* non-shortest form */
            MB_FAILURE(pos, 3);
        } else if (codepoint >= 0xd800 && codepoint <= 0xdfff) { /* surrogate */
            MB_FAILURE(pos, 3);
        }
        pos += 3;
    } else if (c < 0xf5) {
        size_t avail = str_len - pos;

        if (avail < 4 ||
                !UTF8_TRAIL(str[pos + 1]) || !UTF8_TRAIL(str[pos + 2]) ||
                !UTF8_TRAIL(str[pos + 3])) {
            if (avail < 2 || UTF8_LEAD(str[pos + 1]))
                MB_FAILURE(pos, 1);
            else if (avail < 3 || UTF8_LEAD(str[pos + 2]))
                MB_FAILURE(pos, 2);
            else if (avail < 4 || UTF8_LEAD(str[pos + 3]))
                MB_FAILURE(pos, 3);
            else
                MB_FAILURE(pos, 4);
        }

        codepoint = ((c & 0x07U) << 18) | ((str[pos + 1] & 0x3fU) << 12) |
                ((str[pos + 2] & 0x3fU) << 6) | (str[pos + 3] & 0x3f);
        if (codepoint < 0x10000 || codepoint > 0x10FFFF) {
            /* non-shortest form or outside range */
            MB_FAILURE(pos, 4);
        }
        pos += 4;
    } else {
        MB_FAILURE(pos, 1);
    }

    *cursor = pos;
    return codepoint;
}

static inline size_t _write_utf8_codeunits(uint8_t *buf, uint32_t k)
{
    size_t retval = 0;

    /* assert(0x0 <= k <= 0x10FFFF); */

    if (k < 0x80) {
        buf[0] = (uint8_t)k;
        retval = 1;
    } else if (k < 0x800) {
        buf[0] = (uint8_t)(0xc0 | (k >> 6));
        buf[1] = 0x80 | (k & 0x3f);
        retval = 2;
    } else if (k < 0x10000) {
        buf[0] = (uint8_t)(0xe0 | (k >> 12));
        buf[1] = 0x80 | ((k >> 6) & 0x3f);
        buf[2] = 0x80 | (k & 0x3f);
        retval = 3;
    } else {
        buf[0] = (uint8_t)(0xf0 | (k >> 18));
        buf[1] = 0x80 | ((k >> 12) & 0x3f);
        buf[2] = 0x80 | ((k >> 6) & 0x3f);
        buf[3] = 0x80 | (k & 0x3f);
        retval = 4;
    }
    /* UTF-8 has been restricted to max 4 bytes since RFC 3629 */

    return retval;
}

static inline size_t _write_utf16_codeunits(jchar *buf, uint32_t k)
{
    if (k < 0x10000) {
        buf[0] = (jchar)k;
        return 1;
    } else {
        buf[0] = 0xD800 | ((k & 0xFFFF) >> 10); // high 10 bits excl over bit 20
        buf[1] = 0xDC00 | (k & 0x3FF); // low 10 bits
        return 2;
    }
}

void java_utf16_to_utf8_checked(JNIEnv *env,
                                const jchar *in,
                                jsize length /* in code units*/,
                                uint8_t **out_p,
                                size_t *out_len_p)
{
    assert(sizeof(jsize) < sizeof(size_t));

    // let's be optimistic and assume the input string is all ascii
    // +3 because we always require 4 bytes before writing
    size_t out_cap = (size_t)length + 3; // not including NUL
    size_t out_len = 0;

    // Cannot overflow (jsize is an int)
    uint8_t *out = malloc(out_cap + 1);
    if (!out) {
        JNI(ThrowNew, jcls_rte, "out of memory");
        return;
    }

    size_t in_cursor = 0;
    while (in_cursor < (size_t)length) {
        size_t out_left = out_cap - out_len;
        if (out_left < 4) {
            out_cap *= 2;
            uint8_t *new_out = realloc(out, out_cap + 1);
            if (!new_out) {
                free(out);
                JNI(ThrowNew, jcls_rte, "out of memory");
                return;
            }
            out = new_out;
        }

        bool status;
        uint32_t cp = _get_next_codepoint_utf16(
                    in, (size_t)length, &in_cursor, &status);
        if (!status) {
            cp = REPL_CHAR;
        }

        out_len += _write_utf8_codeunits(&out[out_len], cp);
    }

#ifndef __clang_analyzer__
    // this is safe. _write_utf8_codeunits can only write 4 chars, which
    // would make at most out_cap == out_len. And we always allocate out_cap +1
    out[out_len] = '\0';
#endif
    *out_p = out;
    if (out_len_p) {
        *out_len_p = out_len;
    }
}

jstring java_utf8_to_jstring_checked(JNIEnv *env,
                                     const char *in_signed,
                                     size_t in_len)
{
    const uint8_t *in = (const uint8_t*)in_signed;
    // at most we'll have as many UTF-16 code units as UTF-8 code units
    // (in case of only ASCII characters)
    size_t out_cap = in_len; // not including NUL
    size_t out_len = 0;

    jchar *out;
    if (out_cap > ((size_t)-1) / sizeof(*out) - 1) {
        JNI(ThrowNew, jcls_rte, "string is too large");
        return NULL;
    }
    out = malloc((out_cap + 1) * sizeof(*out));
    if (!out) {
        JNI(ThrowNew, jcls_rte, "out of memory");
        return NULL;
    }

    size_t in_cursor = 0;
    while (in_cursor < in_len) {
        bool status;
        uint32_t cp = _get_next_codepoint_utf8(
                    in, in_len, &in_cursor, &status);

        if (!status) {
            cp = REPL_CHAR;
        }

        if (out_cap < out_len + 1 ||
                (cp > 0xFFFF && out_cap < out_len + 2)) {
            JNI(ThrowNew, jcls_rte, "output string is unexpectedly too short");
            free(out);
            return NULL;
        }
        out_len += _write_utf16_codeunits(&out[out_len], cp);
    }

    out[out_len] = '\0';
    if (out_len > INT_MAX) {
        JNI(ThrowNew, jcls_rte, "string is too long");
        free(out);
        return NULL;
    }

    jstring ret = JNI(NewString, out, (jint)out_len);
    free(out);
    return ret;
}

static char *_to_utf8_checked_utf16_len(JNIEnv *env,
                                        jstring str, jint utf16_len,
                                        size_t *utf8_out_len)
{
    const jchar *utf16_str = JNI(GetStringChars, str, NULL);

    if (!utf16_str) {
        if (!JNI(ExceptionCheck)) {
            JNI(ThrowNew, jcls_rte, "Error calling GetStringChars");
        } else {
            java_wrap_exc("Error calling GetStringChars on string of size %d",
                          utf16_len);
        }
        return NULL;
    }

    uint8_t *out = NULL;
    java_utf16_to_utf8_checked(env, utf16_str, utf16_len, &out, utf8_out_len);

    JNI(ReleaseStringChars, str, utf16_str);

    return (char *)out;
}

char *java_to_utf8_checked(JNIEnv *env, jstring str, size_t *utf8_out_len)
{
    const jint utf16_len = JNI(GetStringLength, str);
    if (JNI(ExceptionCheck)) {
        java_wrap_exc("Error getting the length of putative string");
        return NULL;
    }

    return _to_utf8_checked_utf16_len(env, str, utf16_len, utf8_out_len);
}

// sets a pending exception in case of failure
char *java_to_utf8_limited_checked(
        JNIEnv *env, jstring str, size_t *len, int max_len)
{
    const jint utf16_len = JNI(GetStringLength, str);
    if (JNI(ExceptionCheck)) {
        java_wrap_exc("Error getting the length of putative string");
        return NULL;
    }

    if (max_len < 0 || utf16_len <= max_len) {
        return _to_utf8_checked_utf16_len(env, str, utf16_len, len);
    }

    jchar *utf16_str = calloc((size_t)max_len, sizeof *utf16_str);
    if (!utf16_str) {
            JNI(ThrowNew, jcls_rte, "malloc failed allocated jchar array");
            return NULL;
    }

    JNI(GetStringRegion, str, 0, max_len, utf16_str);

    if (JNI(ExceptionCheck)) {
        java_wrap_exc("Error calling GetStringRegion for substring of size %d",
                      max_len);
        free(utf16_str);
        return NULL;
    }

    uint8_t *out = NULL;
    java_utf16_to_utf8_checked(env, utf16_str, max_len, &out, len);

    free(utf16_str);

    return (char *)out;
}

jstring java_json_to_jstring_checked(JNIEnv *env, const struct json_segment *seg)
{
    struct json_iterator it = { .seg = seg };
    // at most we'll have as many UTF-16 code units as UTF-8 code units
    // (in case of only ASCII characters)
    size_t out_cap = json_length(seg); // not including NUL
    size_t out_len = 0;

    jchar *out;
    if (out_cap > ((size_t)-1) / sizeof(*out) - 1) {
        JNI(ThrowNew, jcls_rte, "string is too large");
        return NULL;
    }
    out = malloc((out_cap + 1) * sizeof(*out));
    if (!out) {
        JNI(ThrowNew, jcls_rte, "out of memory");
        return NULL;
    }

    uint8_t buffer[256];
    size_t buffer_len = 0;
    size_t buffer_cursor = 0;
    bool eof = false;
    while (true) {
        size_t left_in_buffer = buffer_len - buffer_cursor;
        if (left_in_buffer < 4 && !eof) {
            memcpy(buffer, buffer + buffer_cursor, left_in_buffer); // compact
            size_t max_read = sizeof(buffer) - left_in_buffer;
            size_t read = json_it_read(&it, (char *) (buffer + left_in_buffer),
                                       max_read);
            buffer_cursor = 0;
            buffer_len = left_in_buffer + read;
            if (read < max_read) {
                eof = true;
            }
            left_in_buffer = buffer_len;
        }
        if (left_in_buffer == 0) {
            break;
        }

        bool status;
        uint32_t cp = _get_next_codepoint_utf8(
                    buffer, buffer_len, &buffer_cursor, &status);

        if (!status) {
            cp = REPL_CHAR;
        }

        if (out_cap < out_len + 1 ||
                (cp > 0xFFFF && out_cap < out_len + 2)) {
            JNI(ThrowNew, jcls_rte, "output string is unexpectedly too short");
            free(out);
            return NULL;
        }
        out_len += _write_utf16_codeunits(&out[out_len], cp);
    }

    out[out_len] = '\0';
    if (out_len > INT_MAX) {
        JNI(ThrowNew, jcls_rte, "string is too long");
        free(out);
        return NULL;
    }

    jstring ret = JNI(NewString, out, (jint)out_len);
    free(out);
    return ret;
}
