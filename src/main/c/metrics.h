#include <ddwaf.h>
#include <jni.h>
#include <stdbool.h>

bool metrics_init(JNIEnv *env);
ddwaf_metrics_collector get_metrics_collector_checked(JNIEnv *env,
                                                      jobject metrics_obj);
