#ifndef COMMON_H
#define COMMON_H

#include <jni.h>

#define JNI(method, ...) (*env)->method(env, ##__VA_ARGS__)
#ifdef UNUSED
#  undef UNUSED
#endif
#define UNUSED(var) (void) var

extern jclass jcls_rte;
extern jclass jcls_iae;
extern jmethodID rte_constr_cause;

#endif
