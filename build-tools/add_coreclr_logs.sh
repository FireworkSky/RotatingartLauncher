#!/bin/bash
# 修改 coreclr 源码添加 Android logcat 日志

RUNTIME_PATH="/mnt/d/runtime-10.0.0-rc.2/src/coreclr"

echo "📝 添加 Android logcat 日志到 coreclr 源码..."

# 1. 修改 ceemain.cpp - CoreCLR 主入口
CEEMAIN_FILE="$RUNTIME_PATH/vm/ceemain.cpp"
if [ -f "$CEEMAIN_FILE" ]; then
    echo "✓ 修改 ceemain.cpp"
    
    # 在文件开头添加 Android log 头文件
    if ! grep -q "#include <android/log.h>" "$CEEMAIN_FILE"; then
        sed -i '1i #ifdef __ANDROID__\n#include <android/log.h>\n#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "CoreCLR", __VA_ARGS__)\n#else\n#define LOGD(...)\n#endif\n' "$CEEMAIN_FILE"
    fi
    
    # 在 EEStartup 函数开头添加日志
    sed -i '/HRESULT EEStartup(/a\    LOGD("[CORECLR-1] EEStartup called");' "$CEEMAIN_FILE"
    
    # 在 InitializeEE 函数开头添加日志
    sed -i '/void InitializeEE(/a\    LOGD("[CORECLR-2] InitializeEE called");' "$CEEMAIN_FILE"
fi

# 2. 修改 eehost.cpp - Host 接口
EEHOST_FILE="$RUNTIME_PATH/vm/eehost.cpp"
if [ -f "$EEHOST_FILE" ]; then
    echo "✓ 修改 eehost.cpp"
    
    # 添加 Android log 头文件
    if ! grep -q "#include <android/log.h>" "$EEHOST_FILE"; then
        sed -i '1i #ifdef __ANDROID__\n#include <android/log.h>\n#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "CoreCLR", __VA_ARGS__)\n#else\n#define LOGD(...)\n#endif\n' "$EEHOST_FILE"
    fi
fi

# 3. 修改 appdomain.cpp - AppDomain 初始化
APPDOMAIN_FILE="$RUNTIME_PATH/vm/appdomain.cpp"
if [ -f "$APPDOMAIN_FILE" ]; then
    echo "✓ 修改 appdomain.cpp"
    
    # 添加 Android log 头文件
    if ! grep -q "#include <android/log.h>" "$APPDOMAIN_FILE"; then
        sed -i '1i #ifdef __ANDROID__\n#include <android/log.h>\n#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "CoreCLR", __VA_ARGS__)\n#else\n#define LOGD(...)\n#endif\n' "$APPDOMAIN_FILE"
    fi
    
    # 在 AppDomain::Create 开头添加日志
    sed -i '/void AppDomain::Create(/a\    LOGD("[CORECLR-3] AppDomain::Create called");' "$APPDOMAIN_FILE"
fi

# 4. 修改 assemblyspec.cpp - Assembly 加载
ASSEMBLYSPEC_FILE="$RUNTIME_PATH/vm/assemblyspec.cpp"
if [ -f "$ASSEMBLYSPEC_FILE" ]; then
    echo "✓ 修改 assemblyspec.cpp"
    
    # 添加 Android log 头文件
    if ! grep -q "#include <android/log.h>" "$ASSEMBLYSPEC_FILE"; then
        sed -i '1i #ifdef __ANDROID__\n#include <android/log.h>\n#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "CoreCLR", __VA_ARGS__)\n#else\n#define LOGD(...)\n#endif\n' "$ASSEMBLYSPEC_FILE"
    fi
fi

# 5. 修改 ceeload.cpp - 加载逻辑
CEELOAD_FILE="$RUNTIME_PATH/vm/ceeload.cpp"
if [ -f "$CEELOAD_FILE" ]; then
    echo "✓ 修改 ceeload.cpp"
    
    # 添加 Android log 头文件
    if ! grep -q "#include <android/log.h>" "$CEELOAD_FILE"; then
        sed -i '1i #ifdef __ANDROID__\n#include <android/log.h>\n#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "CoreCLR", __VA_ARGS__)\n#else\n#define LOGD(...)\n#endif\n' "$CEELOAD_FILE"
    fi
    
    # 在 Module::DoInit 开头添加日志
    sed -i '/void Module::DoInit(/a\    LOGD("[CORECLR-4] Module::DoInit called");' "$CEELOAD_FILE"
fi

echo "✅ 日志添加完成！"

