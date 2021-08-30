#include <jni.h>
#include <stdint.h>
#include <string.h>
#include "common.h"
#include "jni/io_sqreen_powerwaf_ByteBufferSerializer.h"

JNIEXPORT jlong JNICALL
Java_io_sqreen_powerwaf_ByteBufferSerializer_getByteBufferAddress(JNIEnv *env,
                                                                  jclass clazz,
                                                                  jobject bb)
{
    (void) clazz;
    void *addr = JNI(GetDirectBufferAddress, bb);
    jlong ret;
    memcpy(&ret, &addr, sizeof ret);
    return ret;
}
