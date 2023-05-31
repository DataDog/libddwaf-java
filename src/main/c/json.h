/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2023 Datadog, Inc.
 */

#pragma once

#include <inttypes.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#define MIN_JSON_SEGMENT_SIZE ((uint32_t) 216)
#define MAX_JSON_DEPTH 10

struct json_segment {
    uint32_t size, len;
    struct json_segment *next;
    char data[];
};

struct json_iterator {
    const struct json_segment *seg;
    uint32_t pos;
};

static inline size_t json_length(const struct json_segment *seg)
{
    size_t res = 0;
    for (const struct json_segment *p = seg; p; p = p->next) {
        res += p->len;
    }
    return res;
}

static inline size_t json_it_read(struct json_iterator *it, char *out,
                                  size_t out_len)
{
    size_t written = 0;
    while (it->seg != NULL && written < out_len) {
        uint32_t avail_in_seg = it->seg->len - it->pos;
        if (avail_in_seg == 0) {
            it->seg = it->seg->next;
            it->pos = 0;
            continue;
        }
        size_t left_in_out = out_len - written;
        size_t to_copy =
                avail_in_seg < left_in_out ? avail_in_seg : left_in_out;
        memcpy(out + written, it->seg->data + it->pos, to_copy);
        it->pos += to_copy;
        written += to_copy;
    }
    return written;
}

static inline struct json_segment *json_seg_new(size_t min_size)
{
    if (min_size > UINT32_MAX) {
        return NULL;
    }

    uint32_t size = ((uint32_t) min_size) < MIN_JSON_SEGMENT_SIZE
                            ? MIN_JSON_SEGMENT_SIZE
                            : (uint32_t) min_size;

    _Static_assert(sizeof(size_t) > sizeof(size), "size_t is more than 32 bit");
    struct json_segment *initial_seg = malloc(sizeof *initial_seg + size);
    if (!initial_seg) {
        return NULL;
    }
    initial_seg->size = size;
    initial_seg->len = 0;
    initial_seg->next = 0;
    return initial_seg;
}

static inline struct json_segment *_json_seg_ensure(struct json_segment *seg,
                                                    size_t data_len)
{
    if (seg == NULL) {
        return NULL;
    }
    if (data_len > UINT32_MAX) {
        return NULL;
    }

    uint32_t avail = seg->size - seg->len;
    if (avail >= data_len) {
        return seg;
    } else {
        struct json_segment *new_seg = json_seg_new(data_len);
        if (!new_seg) {
            return NULL;
        }
        seg->next = new_seg;
        return new_seg;
    }
}

static inline struct json_segment *
json_append(struct json_segment *seg, const char *data, size_t data_len)
{
    struct json_segment *actual_seg = _json_seg_ensure(seg, data_len);
    if (actual_seg == NULL) {
        return NULL;
    }

    memcpy(actual_seg->data + actual_seg->len, data, data_len);
    actual_seg->len += data_len;
    return actual_seg;
}

static inline void json_seg_free(struct json_segment *seg)
{
    struct json_segment *cur = seg;
    struct json_segment *next;
    while (cur) {
        next = cur->next;
        free(cur);
        cur = next;
    }
}
static inline size_t _json_str_encoded_size(const char *str, size_t str_len)
{
    size_t res = 0;
    for (const char *c = str; c < str + str_len; c++) {
        if (*c == '\\' || *c == '"') {
            res += 2;
        } else if (*c >= 0 && *c < 0x20) {
            res += 6; /* \uXXXX */
        } else {
            res += 1;
        }
    }
    return res;
}
static inline void _json_encode_str(const char *str, size_t str_len, char *out)
{
    char *o = out;
    for (const char *c = str; c < str + str_len; c++) {
        if (*c == '\\' || *c == '"') {
            o[0] = '\\';
            o[1] = *c;
            o += 2;
        } else if (*c >= 0 && *c < 0x20) {
            char s[3];
            sprintf(s, "%02X", *c);
            o[0] = '\\';
            o[1] = 'u';
            o[2] = '0';
            o[3] = '0';
            o[4] = s[0];
            o[5] = s[1];
            o += 6; /* \uXXXX */
        } else {
            *o = *c;
            o += 1;
        }
    }
}

static inline struct json_segment *json_encode_str(struct json_segment *seg,
                                                   const char *raw_str,
                                                   size_t raw_str_len)
{
    size_t encoded_len = _json_str_encoded_size(raw_str, raw_str_len);
    struct json_segment *cur_seg = _json_seg_ensure(seg, encoded_len);
    if (!cur_seg) {
        return NULL;
    }
    if (encoded_len == raw_str_len) {
        // can be copied as is
        memcpy(cur_seg->data + cur_seg->len, raw_str, raw_str_len);
    } else {
        _json_encode_str(raw_str, raw_str_len, cur_seg->data + cur_seg->len);
    }
    cur_seg->len += encoded_len;
    return cur_seg;
}
