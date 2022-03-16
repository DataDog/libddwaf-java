#include <ddwaf.h>
#include <jni.h>
#include <stdbool.h>

bool metrics_init(JNIEnv *env);
void metrics_update_checked(JNIEnv *env, jobject metrics_obj, jlong run_time_ns,
                            jlong ddwaf_run_time_ns);
