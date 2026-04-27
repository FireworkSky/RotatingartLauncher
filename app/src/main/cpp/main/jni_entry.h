#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEnv* JniEntry_GetEnv(void);
void JniEntry_SafeDetachEnv(void);
JavaVM* JniEntry_GetJavaVM(void);

#ifdef __cplusplus
}
#endif
