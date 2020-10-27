#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

void java_utf16_to_utf8_checked(JNIEnv *env, const jchar *in, jsize length, uint8_t **out_p, size_t *out_len_p);
jstring java_utf8_to_jstring_checked(JNIEnv *env, const char *in, size_t in_len);
char *java_to_utf8_checked(JNIEnv *env, jstring str, size_t *utf8_out_len);
char *java_to_utf8_limited_checked( JNIEnv *env, jstring str, size_t *len, int max_len);
