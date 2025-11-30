# 在 WSL 中编译修改后的 libhostpolicy.so 以绕过 deps.json 验证

## 问题描述

当启动 .NET 应用时，如果 `deps.json` 缺少 `runtime.linux-bionic-arm64.Microsoft.NETCore.DotNetHostPolicy` 条目，hostpolicy 会报错并阻止应用启动。

## 解决方案

修改 hostpolicy 源代码，使其在缺少特定运行时依赖项时不报错，而是使用默认的运行时依赖项。

## 前提条件

1. WSL 2 中的 Ubuntu 20.04
2. .NET 运行时源代码位于 `/runtime-10.0.0-rc.2` 或 `\\wsl.localhost\Ubuntu-20.04\runtime-10.0.0-rc.2`
3. 已安装 Android NDK（用于交叉编译）

## 步骤 1: 准备编译环境

在 WSL 中运行以下命令安装依赖：

```bash
sudo apt-get update
sudo apt-get install -y \
    build-essential \
    clang \
    cmake \
    git \
    python3 \
    ninja-build \
    pkg-config \
    libunwind-dev \
    libicu-dev \
    libssl-dev \
    zlib1g-dev
```

## 步骤 2: 修改 hostpolicy 源代码

hostpolicy 的依赖验证逻辑位于以下文件：
- `src/native/corehost/hostpolicy/deps_resolver.cpp` - 依赖项解析
- `src/native/corehost/hostpolicy/deps_format.h` - 依赖项格式定义

### 需要修改的位置

找到检查 `Microsoft.NETCore.DotNetHostPolicy` 的代码，通常类似：

```cpp
// 原始代码（需要修改）
if (!has_hostpolicy_dep) {
    trace::error(_X("Dependency manifest does not contain an entry for runtime.%s.Microsoft.NETCore.DotNetHostPolicy"), rid);
    return StatusCode::ResolverInitFailure;
}
```

修改为：

```cpp
// 修改后的代码（跳过验证）
if (!has_hostpolicy_dep) {
    trace::warning(_X("Dependency manifest does not contain an entry for runtime.%s.Microsoft.NETCore.DotNetHostPolicy, using default"), rid);
    // 继续执行，不返回错误
}
```

## 步骤 3: 编译脚本

创建编译脚本 `build-hostpolicy.sh`：

```bash
#!/bin/bash
set -e

RUNTIME_DIR="/runtime-10.0.0-rc.2"
BUILD_DIR="$RUNTIME_DIR/artifacts/bin/hostpolicy/android.arm64.Release"
OUTPUT_DIR="./build/hostpolicy"

echo "=== 编译修改后的 libhostpolicy.so ==="

# 检查运行时目录
if [ ! -d "$RUNTIME_DIR" ]; then
    echo "错误: 运行时目录不存在: $RUNTIME_DIR"
    exit 1
fi

cd "$RUNTIME_DIR"

# 构建配置
export DOTNET_BUILD_SKIP_SUBMODULE_CHECK=1
export DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1
export DOTNET_CLI_TELEMETRY_OPTOUT=1

# 设置 Android NDK 路径（根据实际情况修改）
export ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT:-$HOME/android-ndk-r21e}"
if [ ! -d "$ANDROID_NDK_ROOT" ]; then
    echo "警告: ANDROID_NDK_ROOT 未设置或不存在，请设置 Android NDK 路径"
    echo "export ANDROID_NDK_ROOT=/path/to/android-ndk"
    exit 1
fi

# 仅构建 hostpolicy
./build.sh \
    -subset corehost \
    -configuration Release \
    -arch arm64 \
    -os Android \
    -runtimeidentifier android-arm64 \
    /p:PublishAot=false \
    /p:TargetOS=Android \
    /p:TargetArchitecture=arm64

# 复制编译后的文件
mkdir -p "$OUTPUT_DIR"
cp "$BUILD_DIR/libhostpolicy.so" "$OUTPUT_DIR/" || {
    # 如果路径不同，尝试查找
    find "$RUNTIME_DIR/artifacts" -name "libhostpolicy.so" -type f | head -1 | xargs -I {} cp {} "$OUTPUT_DIR/"
}

echo "=== 编译完成 ==="
echo "输出文件: $OUTPUT_DIR/libhostpolicy.so"
```

## 步骤 4: 应用补丁

由于直接修改源代码可能比较复杂，我们可以创建一个补丁文件。首先创建补丁：

```bash
# 在 WSL 中
cd /runtime-10.0.0-rc.2
git apply /path/to/hostpolicy-deps-bypass.patch
```

## 步骤 5: 替换 Android 设备上的文件

编译完成后，将新的 `libhostpolicy.so` 复制到 Android 设备：

```bash
# 从 Windows 执行
adb push build/hostpolicy/libhostpolicy.so /sdcard/

# 然后在 Android 设备上替换（需要 root 或使用应用内部存储）
adb shell su -c "cp /sdcard/libhostpolicy.so /data/data/com.app.ralaunch/files/dotnet/shared/Microsoft.NETCore.App/10.0.0/libhostpolicy.so"
```

或者如果应用有写权限，可以直接：

```bash
adb push build/hostpolicy/libhostpolicy.so /storage/emulated/0/Android/data/com.app.ralaunch/files/dotnet/shared/Microsoft.NETCore.App/10.0.0/
```

## 替代方案：使用环境变量绕过

如果不想重新编译，可以尝试设置环境变量来绕过验证（需要修改 hostpolicy 源代码支持）。

## 注意事项

1. 修改 hostpolicy 可能导致其他依赖项问题，请测试完整功能
2. 每次更新 .NET 运行时后可能需要重新编译
3. 建议保留原始文件的备份

