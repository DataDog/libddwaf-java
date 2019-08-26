#include "utf16_utf8.h"
#include "common.h"
#include <stdbool.h>
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

#define REPL_CHAR 0xFFFDU

static inline uint32_t _get_next_codepoint(
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

void java_utf16_to_utf8_checked(JNIEnv *env,
                                const jchar *in,
                                jsize length /* in code units*/,
                                uint8_t **out_p,
                                size_t *out_len_p)
{
    assert(sizeof(jsize) < sizeof(size_t));

    // let's be optimistic and assume the input string is all ascii
    // +3 because we always require 4 bytes before writing
    size_t out_cap = (size_t)length + 3;
    size_t out_len = 0;

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
        uint32_t cp = _get_next_codepoint(in, (size_t)length, &in_cursor,
                                          &status);
        if (!status) {
            cp = REPL_CHAR;
        }

        out_len += _write_utf8_codeunits(&out[out_len], cp);
    }

    out[out_len] = '\0';
    *out_p = out;
    if (out_len_p) {
        *out_len_p = out_len;
    }
}
