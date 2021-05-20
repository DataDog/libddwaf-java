#include <jni.h>
#include <stdint.h>
#include "common.h"
#include "io_sqreen_powerwaf_ByteBufferSerializer.h"

JNIEXPORT jlong JNICALL
Java_io_sqreen_powerwaf_ByteBufferSerializer_getByteBufferAddress(JNIEnv *env,
                                                                  jclass clazz,
                                                                  jobject bb)
{
    return (jlong)(intptr_t) JNI(GetDirectBufferAddress, bb);
}
