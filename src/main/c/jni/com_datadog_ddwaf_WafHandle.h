/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_datadog_ddwaf_WafHandle */

#ifndef _Included_com_datadog_ddwaf_WafHandle
#define _Included_com_datadog_ddwaf_WafHandle
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_datadog_ddwaf_Waf
 * Method:    getKnownAddresses
 * Signature: (Lcom/datadog/ddwaf/WafHandle;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_com_datadog_ddwaf_WafHandle_getKnownAddresses
  (JNIEnv *, jclass, jobject);

/*
 * Class:     com_datadog_ddwaf_Waf
 * Method:    getKnownActions
 * Signature: (Lcom/datadog/ddwaf/WafHandle;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_com_datadog_ddwaf_WafHandle_getKnownActions
  (JNIEnv *, jclass, jobject);

JNIEXPORT void JNICALL Java_com_datadog_ddwaf_WafHandle_destroyWafHandle(JNIEnv *, jclass, jlong);


#ifdef __cplusplus
}
#endif
#endif