/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

/*
 * Single source of truth for the ddwaf_object binary layout that
 * ByteBufferSerializer replicates in Java in order to serialize WAF input
 * without copies.
 *
 * libddwaf explicitly reserves the right to break the low-level C API on minor
 * versions, and the layout is not part of any stability contract, so:
 *
 *  - the _Static_asserts below make a layout change a compile-time error here;
 *  - byte_buffer.c exposes these very same offsets to Java through
 *    ByteBufferSerializer.getNativeObjectLayout(), which is validated against
 *    the Java-side constants when the native library is loaded.
 */

#ifndef DDWAF_LAYOUT_H
#define DDWAF_LAYOUT_H

#include <ddwaf.h>
#include <stddef.h>

#define DDWAF_OBJ_OFF_TYPE offsetof(ddwaf_object, type)
#define DDWAF_OBJ_OFF_BOOL_VAL offsetof(ddwaf_object, via.b8.val)
#define DDWAF_OBJ_OFF_NUM_VAL offsetof(ddwaf_object, via.i64.val)
#define DDWAF_OBJ_OFF_STR_SIZE offsetof(ddwaf_object, via.str.size)
#define DDWAF_OBJ_OFF_STR_PTR offsetof(ddwaf_object, via.str.ptr)
#define DDWAF_OBJ_OFF_SSTR_SIZE offsetof(ddwaf_object, via.sstr.size)
#define DDWAF_OBJ_OFF_SSTR_DATA offsetof(ddwaf_object, via.sstr.data)
#define DDWAF_OBJ_OFF_CONTAINER_SIZE offsetof(ddwaf_object, via.array.size)
#define DDWAF_OBJ_OFF_CONTAINER_CAPACITY                                       \
    offsetof(ddwaf_object, via.array.capacity)
#define DDWAF_OBJ_OFF_CONTAINER_PTR offsetof(ddwaf_object, via.array.ptr)

/* The Java serializer writes both arrays and maps through the same offsets, so
 * the two container structs must agree. */
_Static_assert(offsetof(ddwaf_object, via.map.size) ==
                               DDWAF_OBJ_OFF_CONTAINER_SIZE &&
                       offsetof(ddwaf_object, via.map.capacity) ==
                               DDWAF_OBJ_OFF_CONTAINER_CAPACITY &&
                       offsetof(ddwaf_object, via.map.ptr) ==
                               DDWAF_OBJ_OFF_CONTAINER_PTR,
               "ddwaf_object map and array layouts diverged");

/* Likewise for the integral/float scalars, all written at the same offset. */
_Static_assert(offsetof(ddwaf_object, via.u64.val) == DDWAF_OBJ_OFF_NUM_VAL &&
                       offsetof(ddwaf_object, via.f64.val) ==
                               DDWAF_OBJ_OFF_NUM_VAL,
               "ddwaf_object scalar layouts diverged");

_Static_assert(sizeof(ddwaf_object) == 16, "sizeof(ddwaf_object) changed");
_Static_assert(sizeof(ddwaf_object_kv) == 32,
               "sizeof(ddwaf_object_kv) changed");
_Static_assert(offsetof(ddwaf_object_kv, key) == 0,
               "ddwaf_object_kv.key is no longer the first member");
_Static_assert(offsetof(ddwaf_object_kv, val) == 16,
               "ddwaf_object_kv.val moved");

_Static_assert(DDWAF_OBJ_OFF_TYPE == 0, "ddwaf_object.type moved");
_Static_assert(DDWAF_OBJ_OFF_BOOL_VAL == 1, "ddwaf_object bool value moved");
_Static_assert(DDWAF_OBJ_OFF_NUM_VAL == 8, "ddwaf_object scalar value moved");
_Static_assert(DDWAF_OBJ_OFF_STR_SIZE == 4, "ddwaf_object string size moved");
_Static_assert(DDWAF_OBJ_OFF_STR_PTR == 8, "ddwaf_object string ptr moved");
_Static_assert(DDWAF_OBJ_OFF_SSTR_SIZE == 1, "small string size moved");
_Static_assert(DDWAF_OBJ_OFF_SSTR_DATA == 2, "small string data moved");
_Static_assert(DDWAF_OBJ_SSTR_SIZE == 14, "small string capacity changed");
_Static_assert(DDWAF_OBJ_OFF_CONTAINER_SIZE == 2, "container size moved");
_Static_assert(DDWAF_OBJ_OFF_CONTAINER_CAPACITY == 4,
               "container capacity moved");
_Static_assert(DDWAF_OBJ_OFF_CONTAINER_PTR == 8, "container ptr moved");

/* DDWAF_OBJ_TYPE values are a bitmask, mirrored verbatim in
 * ByteBufferSerializer.PWInputType. */
_Static_assert(DDWAF_OBJ_INVALID == 0x00 && DDWAF_OBJ_NULL == 0x01 &&
                       DDWAF_OBJ_BOOL == 0x02 && DDWAF_OBJ_SIGNED == 0x04 &&
                       DDWAF_OBJ_UNSIGNED == 0x06 && DDWAF_OBJ_FLOAT == 0x08 &&
                       DDWAF_OBJ_STRING == 0x10 &&
                       DDWAF_OBJ_LITERAL_STRING == 0x12 &&
                       DDWAF_OBJ_SMALL_STRING == 0x14 &&
                       DDWAF_OBJ_ARRAY == 0x20 && DDWAF_OBJ_MAP == 0x40,
               "DDWAF_OBJ_TYPE values changed");

#endif // DDWAF_LAYOUT_H
