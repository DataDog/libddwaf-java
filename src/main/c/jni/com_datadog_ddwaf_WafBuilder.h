/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_datadog_ddwaf_WafBuilder */

#ifndef _Included_com_datadog_ddwaf_WafBuilder
#define _Included_com_datadog_ddwaf_WafBuilder
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_datadog_ddwaf_WafBuilder
 * Method:    buildInstance
 * Signature: (Lcom/datadog/ddwaf/WafBuilder;)Lcom/datadog/ddwaf/WafHandle;
 */
JNIEXPORT jobject JNICALL
Java_com_datadog_ddwaf_WafBuilder_buildInstance(JNIEnv *, jclass, jobject);

/*
 * Class:     com_datadog_ddwaf_WafBuilder
 * Method:    initBuilder
 * Signature: (Lcom/datadog/ddwaf/WafConfig;)J
 */
JNIEXPORT jlong JNICALL Java_com_datadog_ddwaf_WafBuilder_initBuilder(JNIEnv *,
                                                                      jclass,
                                                                      jobject);

/*
 * Class:     com_datadog_ddwaf_WafBuilder
 * Method:    addOrUpdateConfigNative
 * Signature:
 * (Lcom/datadog/ddwaf/WafBuilder;Ljava/lang/String;Ljava/util/Map;[Lcom/datadog/ddwaf/WafDiagnostics;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_datadog_ddwaf_WafBuilder_addOrUpdateConfigNative(JNIEnv *, jclass,
                                                          jobject, jstring,
                                                          jobject,
                                                          jobjectArray);

/*
 * Class:     com_datadog_ddwaf_WafBuilder
 * Method:    removeConfigNative
 * Signature: (Lcom/datadog/ddwaf/WafBuilder;Ljava/lang/String;)V
 */
JNIEXPORT jboolean JNICALL Java_com_datadog_ddwaf_WafBuilder_removeConfigNative(
        JNIEnv *, jclass, jobject, jstring);

/*
 * Class:     com_datadog_ddwaf_WafBuilder
 * Method:    destroyBuilder
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_datadog_ddwaf_WafBuilder_destroyBuilder(JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
