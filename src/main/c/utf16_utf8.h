#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

void java_utf16_to_utf8_checked(JNIEnv *env, const jchar *in, jsize length, uint8_t **out_p, size_t *out_len_p);
jstring java_utf8_to_jstring_checked(JNIEnv *env, const char *in, size_t in_len);
