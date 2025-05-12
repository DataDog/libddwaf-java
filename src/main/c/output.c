/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2023 Datadog, Inc.
 */

#include "output.h"
#include <assert.h>
#include <ddwaf.h>
#include <stdint.h>
#include <string.h>
#include <inttypes.h>
#include <limits.h>
#include <zlib.h>

#include "common.h"
#include "java_call.h"
#include "jni.h"
#include "json.h"
#include "utf16_utf8.h"
#include "base64.h"
#include "logging.h"

#define LSTR(x) "" x, sizeof(x) - 1
#define MAX_JINT ((jint) 0x7FFFFFFF)

static struct j_method _rsi_init;
static struct j_method _sect_info_err_init;
static struct j_method _sect_info_normal_init;
static struct j_method _array_list_init;
static struct j_method _array_list_add;
static struct j_method _linked_hm_init;
static struct j_method _map_put;

static jstring _map_get_string_checked(JNIEnv *env, const ddwaf_object *obj,
                                       const char *str, size_t str_len);
static jobject _convert_section_checked(JNIEnv *env, const ddwaf_object *root,
                                        const char *sect_name, size_t sect_len);
static jobject _convert_strarr_checked(JNIEnv *env, const ddwaf_object *o);
static struct json_segment *_convert_json(const ddwaf_object *cur_obj,
                                          int depth,
                                          struct json_segment *cur_seg);

static bool _is_derivative(const ddwaf_object *entry, const char *prefix)
{
    size_t prefix_size = strlen(prefix);
    if (entry->parameterNameLength < prefix_size) {
        return false;
    }
    return strncmp(prefix, entry->parameterName, prefix_size) == 0;
}

jobject output_convert_diagnostics_checked(JNIEnv *env, const ddwaf_object *obj)
{
    jstring rulesetVersion =
            _map_get_string_checked(env, obj, LSTR("ruleset_version"));
    if (JNI(ExceptionCheck)) {
        return NULL;
    }

    jobject rules = NULL, custom_rules = NULL, rules_data = NULL,
            rules_override = NULL, exclusions = NULL, ret = NULL,
            exclusion_data = NULL;

    rules = _convert_section_checked(env, obj, LSTR("rules"));
    if (JNI(ExceptionCheck)) {
        goto err;
    }
    custom_rules = _convert_section_checked(env, obj, LSTR("custom_rules"));
    if (JNI(ExceptionCheck)) {
        goto err;
    }
    rules_data = _convert_section_checked(env, obj, LSTR("rules_data"));
    if (JNI(ExceptionCheck)) {
        goto err;
    }
    rules_override = _convert_section_checked(env, obj, LSTR("rules_override"));
    if (JNI(ExceptionCheck)) {
        goto err;
    }
    exclusions = _convert_section_checked(env, obj, LSTR("exclusions"));
    if (JNI(ExceptionCheck)) {
        goto err;
    }
    exclusion_data = _convert_section_checked(env, obj, LSTR("exclusion_data"));
    if (JNI(ExceptionCheck)) {
        goto err;
    }

    ret = java_meth_call(env, &_rsi_init, NULL, rulesetVersion, rules,
                         custom_rules, rules_data, rules_override, exclusions,
                         exclusion_data);

err:
    if (rulesetVersion) {
        JNI(DeleteLocalRef, rulesetVersion);
    }
    if (rules) {
        JNI(DeleteLocalRef, rules);
    }
    if (custom_rules) {
        JNI(DeleteLocalRef, custom_rules);
    }
    if (rules_data) {
        JNI(DeleteLocalRef, rules_data);
    }
    if (rules_override) {
        JNI(DeleteLocalRef, rules_override);
    }
    if (exclusions) {
        JNI(DeleteLocalRef, exclusions);
    }
    if (exclusion_data) {
        JNI(DeleteLocalRef, exclusion_data);
    }
    return ret;
}

void output_init_checked(JNIEnv *env)
{
    if (!java_meth_init_checked(env, &_rsi_init,
                                "com/datadog/ddwaf/RuleSetInfo", "<init>",
                                "(Ljava/lang/String;Lcom/datadog/ddwaf/"
                                "RuleSetInfo$SectionInfo;Lcom/datadog/ddwaf/"
                                "RuleSetInfo$SectionInfo;Lcom/datadog/ddwaf/"
                                "RuleSetInfo$SectionInfo;Lcom/datadog/ddwaf/"
                                "RuleSetInfo$SectionInfo;Lcom/datadog/ddwaf/"
                                "RuleSetInfo$SectionInfo;Lcom/datadog/ddwaf/"
                                "RuleSetInfo$SectionInfo;)V",
                                JMETHOD_CONSTRUCTOR)) {
        goto err;
    }
    if (!java_meth_init_checked(env, &_sect_info_err_init,
                                "com/datadog/ddwaf/RuleSetInfo$SectionInfo",
                                "<init>", "(Ljava/lang/String;)V",
                                JMETHOD_CONSTRUCTOR)) {
        goto err;
    }
    if (!java_meth_init_checked(
                env, &_sect_info_normal_init,
                "com/datadog/ddwaf/RuleSetInfo$SectionInfo", "<init>",
                "(Ljava/util/List;Ljava/util/List;Ljava/util/Map;)V",
                JMETHOD_CONSTRUCTOR)) {
        goto err;
    }
    if (!java_meth_init_checked(env, &_array_list_init, "java/util/ArrayList",
                                "<init>", "(I)V", JMETHOD_CONSTRUCTOR)) {
        goto err;
    }
    if (!java_meth_init_checked(env, &_array_list_add, "java/util/ArrayList",
                                "add", "(Ljava/lang/Object;)Z",
                                JMETHOD_NON_VIRTUAL)) {
        goto err;
    }
    if (!java_meth_init_checked(env, &_linked_hm_init,
                                "java/util/LinkedHashMap", "<init>", "()V",
                                JMETHOD_CONSTRUCTOR)) {
        goto err;
    }
    if (!java_meth_init_checked(
                env, &_map_put, "java/util/Map", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                JMETHOD_VIRTUAL)) {
        goto err;
    }

    return;
err:
    output_shutdown(env);
}

void output_shutdown(JNIEnv *env)
{
    java_meth_destroy(env, &_rsi_init);
    java_meth_destroy(env, &_sect_info_err_init);
    java_meth_destroy(env, &_sect_info_normal_init);
    java_meth_destroy(env, &_array_list_init);
    java_meth_destroy(env, &_array_list_add);
    java_meth_destroy(env, &_linked_hm_init);
    java_meth_destroy(env, &_map_put);
}

static const ddwaf_object *_map_get_object_checked(JNIEnv *env,
                                                   const ddwaf_object *obj,
                                                   const char *str,
                                                   size_t str_len)
{
    if (!obj || obj->type != DDWAF_OBJ_MAP) {
        JNI(ThrowNew, jcls_rte, "ddwaf map expected");
        return NULL;
    }

    for (uint64_t i = 0; i < obj->nbEntries; i++) {
        const ddwaf_object *o = &obj->array[i];
        if (o->parameterNameLength == str_len &&
            memcmp(o->parameterName, str, str_len) == 0) {
            return o;
        }
    }
    return NULL;
}

static jstring _map_get_string_checked(JNIEnv *env, const ddwaf_object *obj,
                                       const char *str, size_t str_len)
{
    const ddwaf_object *o = _map_get_object_checked(env, obj, str, str_len);
    if (JNI(ExceptionCheck) || o == NULL) {
        return NULL;
    }

    size_t length;
    const char *value = ddwaf_object_get_string(o, &length);
    if (!value) {
        JNI(ThrowNew, jcls_rte, "ddwaf string expected");
        return NULL;
    }

    return java_utf8_to_jstring_checked(env, value, length);
}

static jobject _convert_strarr_checked(JNIEnv *env, const ddwaf_object *o)
{
    if (!o) {
        return NULL;
    }

    if (o->type != DDWAF_OBJ_ARRAY) {
        JNI(ThrowNew, jcls_rte, "ddwaf array expected");
        return NULL;
    }

    if (o->nbEntries == 0) {
        return NULL;
    }

    if (o->nbEntries > MAX_JINT) {
        JNI(ThrowNew, jcls_rte, "too many elements in ddwaf array");
        return NULL;
    }

    jobject ret =
            java_meth_call(env, &_array_list_init, NULL, (jint) o->nbEntries);
    if (ret == NULL) {
        return NULL;
    }

    for (uint64_t i = 0; i < o->nbEntries; i++) {
        const ddwaf_object *elem = &o->array[i];
        size_t str_len;
        const char *str = ddwaf_object_get_string(elem, &str_len);
        if (!str) {
            JNI(ThrowNew, jcls_rte, "expected array to contain only strings");
            goto err;
        }

        jstring jstr = java_utf8_to_jstring_checked(env, str, str_len);
        if (JNI(ExceptionCheck)) {
            goto err;
        }
        java_meth_call(env, &_array_list_add, ret, jstr);
        JNI(DeleteLocalRef, jstr);
        if (JNI(ExceptionCheck)) {
            goto err;
        }
    }

    return ret;

err:
    JNI(DeleteLocalRef, ret);
    return NULL;
}

static jobject _map_get_object_strarr_checked(JNIEnv *env,
                                              const ddwaf_object *obj,
                                              const char *str, size_t str_len)
{

    const ddwaf_object *o = _map_get_object_checked(env, obj, str, str_len);
    if (JNI(ExceptionCheck) || o == NULL) {
        return NULL;
    }

    return _convert_strarr_checked(env, o);
}

static jobject _map_get_object_errmap_checked(JNIEnv *env,
                                              const ddwaf_object *obj,
                                              const char *str, size_t str_len)
{
    const ddwaf_object *o = _map_get_object_checked(env, obj, str, str_len);
    if (JNI(ExceptionCheck) || o == NULL) {
        return NULL;
    }

    if (o->type != DDWAF_OBJ_MAP) {
        JNI(ThrowNew, jcls_rte, "ddwaf map expected");
        return NULL;
    }

    if (o->nbEntries == 0) {
        return NULL;
    }

    jobject ret = java_meth_call(env, &_linked_hm_init, NULL);
    if (JNI(ExceptionCheck)) {
        return NULL;
    }

    for (uint64_t i = 0; i < o->nbEntries; i++) {
        ddwaf_object *elem = &o->array[i];
        if (elem->type != DDWAF_OBJ_ARRAY) {
            JNI(ThrowNew, jcls_rte, "ddwaf array expected inside map");
            goto err;
        }

        jstring jkey = java_utf8_to_jstring_checked(env, elem->parameterName,
                                                    elem->parameterNameLength);
        if (JNI(ExceptionCheck)) {
            goto err;
        }
        jobject str_list = _convert_strarr_checked(env, elem);
        if (JNI(ExceptionCheck)) {
            JNI(DeleteLocalRef, jkey);
            goto err;
        }

        java_meth_call(env, &_map_put, ret, jkey, str_list);
        JNI(DeleteLocalRef, jkey);
        JNI(DeleteLocalRef, str_list);
        if (JNI(ExceptionCheck)) {
            goto err;
        }
    }

    return ret;
err:
    JNI(DeleteLocalRef, ret);
    return NULL;
}

static jobject _convert_section_checked(JNIEnv *env, const ddwaf_object *root,
                                        const char *sect_name, size_t sect_len)
{
    const ddwaf_object *section =
            _map_get_object_checked(env, root, sect_name, sect_len);
    if (JNI(ExceptionCheck) || section == NULL) {
        return NULL;
    }

    {
        jstring error = _map_get_string_checked(env, section, LSTR("error"));
        if (JNI(ExceptionCheck)) {
            return NULL;
        }

        if (error) {
            jobject res =
                    java_meth_call(env, &_sect_info_err_init, NULL, error);
            JNI(DeleteLocalRef, error);
            return res;
        }
    }

    jobject loaded = NULL, failed = NULL, errors = NULL, ret = NULL;

    loaded = _map_get_object_strarr_checked(env, section, LSTR("loaded"));
    if (JNI(ExceptionCheck)) {
        goto err;
    }
    failed = _map_get_object_strarr_checked(env, section, LSTR("failed"));
    if (JNI(ExceptionCheck)) {
        goto err;
    }
    errors = _map_get_object_errmap_checked(env, section, LSTR("errors"));
    if (JNI(ExceptionCheck)) {
        goto err;
    }

    ret = java_meth_call(env, &_sect_info_normal_init, NULL, loaded, failed,
                         errors);

err:
    if (loaded) {
        JNI(DeleteLocalRef, loaded);
    }
    if (failed) {
        JNI(DeleteLocalRef, failed);
    }
    if (errors) {
        JNI(DeleteLocalRef, errors);
    }
    return ret;
}

struct json_segment *output_convert_json(const ddwaf_object *obj)
{
    struct json_segment *initial_seg = json_seg_new(0);
    if (!initial_seg) {
        return NULL;
    }
    bool ok = _convert_json(obj, 0, initial_seg) != NULL;
    if (ok) {
        return initial_seg;
    } else {
        json_seg_free(initial_seg);
        return NULL;
    }
}

static struct json_segment *_convert_json(const ddwaf_object *cur_obj,
                                          int depth,
                                          struct json_segment *cur_seg)
{
    if (depth > MAX_JSON_DEPTH || !cur_obj || !cur_seg) {
        return NULL;
    }

    switch (cur_obj->type) {
    case DDWAF_OBJ_INVALID:
        return false;
    case DDWAF_OBJ_SIGNED: {
        int64_t val = cur_obj->intValue;
        char str[21];
        int len = sprintf(str, "%" PRId64, val);
        assert(len > 0); // can't fail
        cur_seg = json_append(cur_seg, str, (size_t) len);
    } break;
    case DDWAF_OBJ_UNSIGNED: {
        uint64_t val = cur_obj->uintValue;
        char str[21];
        int len = sprintf(str, "%" PRIu64, val);
        assert(len > 0); // can't fail
        cur_seg = json_append(cur_seg, str, (size_t) len);
    } break;
    case DDWAF_OBJ_STRING: {
        cur_seg = json_append(cur_seg, "\"", 1);
        cur_seg = json_encode_str(cur_seg, cur_obj->stringValue,
                                  cur_obj->nbEntries);
        cur_seg = json_append(cur_seg, "\"", 1);
    } break;
    case DDWAF_OBJ_ARRAY: {
        cur_seg = json_append(cur_seg, "[", 1);
        for (uint64_t i = 0; i < cur_obj->nbEntries; i++) {
            const ddwaf_object *o = &cur_obj->array[i];
            cur_seg = _convert_json(o, depth + 1, cur_seg);
            if (i != cur_obj->nbEntries - 1) {
                cur_seg = json_append(cur_seg, ",", 1);
            }
        }
        cur_seg = json_append(cur_seg, "]", 1);
    } break;
    case DDWAF_OBJ_MAP: {
        cur_seg = json_append(cur_seg, "{", 1);
        for (uint64_t i = 0; i < cur_obj->nbEntries; i++) {
            const ddwaf_object *o = &cur_obj->array[i];

            cur_seg = json_append(cur_seg, "\"", 1);
            cur_seg = json_encode_str(cur_seg, o->parameterName,
                                      o->parameterNameLength);
            cur_seg = json_append(cur_seg, "\":", 2);

            cur_seg = _convert_json(o, depth + 1, cur_seg);

            if (i != cur_obj->nbEntries - 1) {
                cur_seg = json_append(cur_seg, ",", 1);
            }
        }
        cur_seg = json_append(cur_seg, "}", 1);
    } break;
    case DDWAF_OBJ_BOOL: {
        if (cur_obj->boolean) {
            cur_seg = json_append(cur_seg, "true", 4);
        } else {
            cur_seg = json_append(cur_seg, "false", 5);
        }
    } break;
    }

    return cur_seg;
}

static uint8_t *_encode_gzip(const struct json_segment *json, size_t json_len,
                             size_t *out_size)
{
    z_stream strm = {0};
    if (deflateInit2(&strm, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 31, 8,
                     Z_DEFAULT_STRATEGY) != Z_OK) {
        return NULL;
    }

    size_t deflated_size = json_len < 1024 ? 1024 : json_len;
    uint8_t *ret = malloc(deflated_size);
    if (ret == NULL) {
        deflateEnd(&strm);
        return NULL;
    }

    // Compress each string
    struct json_iterator it = {0};
    for (it.seg = json; it.seg != NULL; it.seg = it.seg->next) {
        strm.avail_in = it.seg->len;
        strm.next_in = (const uint8_t *) it.seg->data;

        do {
            if (strm.total_out >= deflated_size) {
                deflated_size *= 2;
                uint8_t *new_ret = realloc(ret, deflated_size);
                if (new_ret == NULL) {
                    free(ret);
                    deflateEnd(&strm);
                    return NULL;
                }
                ret = new_ret;
            }

            size_t avail_out = deflated_size - strm.total_out;
            if (avail_out > (unsigned) -1) {
                avail_out = (unsigned) -1;
            }
            strm.avail_out = (unsigned) avail_out;
            strm.next_out = ret + strm.total_out;

            if (deflate(&strm, it.seg->next == NULL ? Z_FINISH : Z_NO_FLUSH) ==
                Z_STREAM_ERROR) {
                free(ret);
                deflateEnd(&strm);
                return NULL;
            }
        } while (strm.avail_out == 0);
    }

    *out_size = strm.total_out;
    if (deflateEnd(&strm) == Z_OK) {
        return ret;
    }
    free(ret);
    return NULL;
}

#define MAX_SIZE_OF_SCHEMA 2500
static jstring _encode_json_gzip_base64_checked(JNIEnv *env,
                                                const ddwaf_object *obj)
{
    struct json_segment *json = output_convert_json(obj);
    if (json == NULL) {
        // too deep (or OOM)
        return NULL;
    }

    size_t json_len = json_length(json);
    if (json_len > MAX_SIZE_OF_SCHEMA) {
        json_seg_free(json);
        return NULL;
    }

    // deflate
    size_t gzip_size;
    uint8_t *gzip = _encode_gzip(json, json_len, &gzip_size);
    json_seg_free(json);
    if (gzip == NULL) {
        JAVA_LOG(DDWAF_LOG_DEBUG, "%s", "gzip encoding of derivative failed");
        return NULL;
    }

    // base64 encode
    size_t base64_capacity = ((4 * gzip_size / 3) + 3) & ~3UL;
    char *base64 = malloc(base64_capacity);
    if (base64 == NULL) {
        free(gzip);
        return NULL;
    }
    size_t base64_len;
    base64_encode((const char *) gzip, gzip_size, base64, &base64_len);
    free(gzip);

    // finally, java string
    jstring ret = java_utf8_to_jstring_checked(env, base64, base64_len);
    free(base64);
    return ret;
}

jobject output_convert_derivatives_checked(JNIEnv *env, const ddwaf_object *obj)
{
    assert(obj->type == DDWAF_OBJ_MAP);

    jobject ret = java_meth_call(env, &_linked_hm_init, NULL);
    if (JNI(ExceptionCheck)) {
        return NULL;
    }

    for (size_t i = 0; i < obj->nbEntries; i++) {
        ddwaf_object *entry = &obj->array[i];
        jstring key = java_utf8_to_jstring_checked(env, entry->parameterName,
                                                   entry->parameterNameLength);
        if (JNI(ExceptionCheck)) {
            goto error;
        }

        jstring value = NULL;
        if (_is_derivative(entry, "_dd.appsec.s.")) {
            // json schemas are json that has to be gzipped and encoded in
            // base64
            value = _encode_json_gzip_base64_checked(env, entry);
        } else if (_is_derivative(entry, "_dd.appsec.fp.")) {
            // fingerprints are simple strings
            value = java_utf8_to_jstring_checked(env, entry->stringValue,
                                                 entry->nbEntries);
        }

        if (JNI(ExceptionCheck)) {
            JNI(DeleteLocalRef, key);
            goto error;
        } else if (value == NULL) {
            JAVA_LOG(DDWAF_LOG_DEBUG,
                     "Failed serializing derivative entry for %*.s",
                     (int) entry->parameterNameLength, entry->parameterName);
            JNI(DeleteLocalRef, key);
            continue;
        }

        java_meth_call(env, &_map_put, ret, key, value);
        JNI(DeleteLocalRef, key);
        JNI(DeleteLocalRef, value);
        if (JNI(ExceptionCheck)) {
            goto error;
        }
    }

    return ret;

error:
    JNI(DeleteLocalRef, ret);
    return NULL;
}

jobject convert_ddwaf_object_to_jobject(JNIEnv *env, const ddwaf_object *obj)
{
    switch (obj->type) {
    // TODO: Implement missing types, when needed
    case DDWAF_OBJ_INVALID:
    case DDWAF_OBJ_SIGNED:
    case DDWAF_OBJ_UNSIGNED:
    case DDWAF_OBJ_FLOAT:
    case DDWAF_OBJ_BOOL:
        JNI(ThrowNew, jcls_rte, "Unsupported ddwaf object type");
        return NULL;
    case DDWAF_OBJ_STRING:
        return java_utf8_to_jstring_checked(env, obj->stringValue,
                                            obj->nbEntries);
    case DDWAF_OBJ_ARRAY: {
        if (obj->nbEntries > MAX_JINT) {
            JNI(ThrowNew, jcls_rte, "Too many elements in ddwaf array");
            return NULL;
        }
        jobject ret = java_meth_call(env, &_array_list_init, NULL,
                                     (jint) obj->nbEntries);
        if (ret == NULL) {
            return NULL;
        }

        for (uint64_t i = 0; i < obj->nbEntries; i++) {
            jobject elem = convert_ddwaf_object_to_jobject(env, &obj->array[i]);
            if (JNI(ExceptionCheck)) {
                JNI(DeleteLocalRef, ret);
                return NULL;
            }

            java_meth_call(env, &_array_list_add, ret, elem);
            JNI(DeleteLocalRef, elem);
            if (JNI(ExceptionCheck)) {
                JNI(DeleteLocalRef, ret);
                return NULL;
            }
        }

        return ret;
    }
    case DDWAF_OBJ_MAP: {
        jobject ret = java_meth_call(env, &_linked_hm_init, NULL);
        if (ret == NULL) {
            return NULL;
        }

        for (uint64_t i = 0; i < obj->nbEntries; i++) {
            ddwaf_object *elem = &obj->array[i];
            jstring key = java_utf8_to_jstring_checked(
                    env, elem->parameterName, elem->parameterNameLength);
            if (JNI(ExceptionCheck)) {
                JNI(DeleteLocalRef, ret);
                return NULL;
            }

            jobject value = convert_ddwaf_object_to_jobject(env, elem);
            if (JNI(ExceptionCheck)) {
                JNI(DeleteLocalRef, key);
                return NULL;
            }

            java_meth_call(env, &_map_put, ret, key, value);
            JNI(DeleteLocalRef, key);
            JNI(DeleteLocalRef, value);
            if (JNI(ExceptionCheck)) {
                JNI(DeleteLocalRef, ret);
                return NULL;
            }
        }

        return ret;
    }
    }
    JNI(ThrowNew, jcls_rte, "Unknown ddwaf object type");
    return NULL;
}
