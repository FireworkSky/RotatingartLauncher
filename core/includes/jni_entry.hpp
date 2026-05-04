#pragma once

#include <jni.h>

JNIEnv* JniEntry_GetEnv(void);
void JniEntry_SafeDetachEnv(void);
JavaVM* JniEntry_GetJavaVM(void);