/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class io_sqreen_powerwaf_Powerwaf */

#ifndef _Included_io_sqreen_powerwaf_Powerwaf
#define _Included_io_sqreen_powerwaf_Powerwaf
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    addRules
 * Signature: (Ljava/util/Map;[Lio/sqreen/powerwaf/PowerwafConfig;[Lio/sqreen/powerwaf/RuleSetInfo;)Lio/sqreen/powerwaf/PowerwafHandle;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Powerwaf_addRules
  (JNIEnv *, jclass, jobject, jobject, jobject);

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    clearRules
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;)V
 */
JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_Powerwaf_clearRules
  (JNIEnv *, jclass, jobject);

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    getRequiredAddresses
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_io_sqreen_powerwaf_Powerwaf_getRequiredAddresses
  (JNIEnv *, jclass, jobject);

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    runRules
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;Ljava/nio/ByteBuffer;Lio/sqreen/powerwaf/Powerwaf$Limits;Lio/sqreen/powerwaf/PowerwafMetrics;)Lio/sqreen/powerwaf/Powerwaf$ResultWithData;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Powerwaf_runRules__Lio_sqreen_powerwaf_PowerwafHandle_2Ljava_nio_ByteBuffer_2Lio_sqreen_powerwaf_Powerwaf_00024Limits_2Lio_sqreen_powerwaf_PowerwafMetrics_2
  (JNIEnv *, jclass, jobject, jobject, jobject, jobject);

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    runRules
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;Ljava/util/Map;Lio/sqreen/powerwaf/Powerwaf$Limits;Lio/sqreen/powerwaf/PowerwafMetrics;)Lio/sqreen/powerwaf/Powerwaf$ResultWithData;
 */
JNIEXPORT jobject JNICALL Java_io_sqreen_powerwaf_Powerwaf_runRules__Lio_sqreen_powerwaf_PowerwafHandle_2Ljava_util_Map_2Lio_sqreen_powerwaf_Powerwaf_00024Limits_2Lio_sqreen_powerwaf_PowerwafMetrics_2
  (JNIEnv *, jclass, jobject, jobject, jobject, jobject);

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    pwArgsBufferToString
 * Signature: (Ljava/nio/ByteBuffer;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_io_sqreen_powerwaf_Powerwaf_pwArgsBufferToString
  (JNIEnv *, jclass, jobject);

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    updateData
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;Ljava/util/List;)V
 */
JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_Powerwaf_updateData
  (JNIEnv *, jclass, jobject, jobject);

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    toggleRules
 * Signature: (Lio/sqreen/powerwaf/PowerwafHandle;Ljava/util/Map;)V
 */
JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_Powerwaf_toggleRules
  (JNIEnv *, jclass, jobject, jobject);

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    getVersion
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_io_sqreen_powerwaf_Powerwaf_getVersion
  (JNIEnv *, jclass);

/*
 * Class:     io_sqreen_powerwaf_Powerwaf
 * Method:    deinitialize
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_io_sqreen_powerwaf_Powerwaf_deinitialize
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif
