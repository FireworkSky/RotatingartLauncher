# 如何修复 Android .NET 网络连接问题

## 问题现象

```
System.Net.Sockets.SocketException: Connection timed out [::ffff:192.168.1.105]:7777
   at System.Net.Sockets.Socket.Connect(EndPoint remoteEP)
   at System.Net.Sockets.TcpClient.Connect(IPEndPoint remoteEP)
```

## 根本原因

Android 上的 .NET Runtime 需要手动加载以下 native 库才能使用网络功能：

1. **`libSystem.Native.so`** - 包含所有 Unix 系统调用封装（socket, connect, bind 等）
2. **`libSystem.Security.Cryptography.Native.Android.so`** - TLS/SSL 加密库

如果这些库没有加载或加载顺序错误，就会导致网络连接失败。

## 解决方案

### 已实现的修复

我们创建了 `DotNetNativeLibraryLoader` 类来自动处理所有库的加载：

```java
import com.app.ralaunch.netcore.DotNetNativeLibraryLoader;

// 在启动 .NET 应用之前调用
String dotnetRoot = RuntimePreference.getDotnetRootPath();
boolean success = DotNetNativeLibraryLoader.loadAllLibraries(dotnetRoot);

if (!success) {
    Log.e(TAG, "Failed to load .NET native libraries!");
}
```

### 加载的库列表

`DotNetNativeLibraryLoader` 会按以下顺序加载库：

1. ✅ **libSystem.Native.so** (必需)
   - 包含 socket, connect, bind, listen, accept 等系统调用
   - 必须最先加载

2. ⚠️ **libSystem.Globalization.Native.so** (可选)
   - 本地化和字符编码

3. ⚠️ **libSystem.IO.Compression.Native.so** (可选)
   - 数据压缩

4. ✅ **libSystem.Security.Cryptography.Native.Android.so** (必需)
   - TLS/SSL 加密
   - 通过 JNI 调用 Android Java 加密 API
   - 自动调用 `JNI_OnLoad` 进行初始化

5. ⚠️ **libSystem.Net.Security.Native.so** (可选，通常不存在)
   - 网络安全层

### 修改的文件

1. **新增文件**:
   - `app/src/main/java/com/app/ralaunch/netcore/DotNetNativeLibraryLoader.java`

2. **修改文件**:
   - `app/src/main/java/com/app/ralaunch/game/GameLauncher.java`

   修改内容：
   ```java
   // 之前：只加载 Cryptography 库
   System.load(Paths.get(dotnetRoot,
       "shared/Microsoft.NETCore.App/.../libSystem.Security.Cryptography.Native.Android.so")
       .toString());

   // 现在：加载所有必需的库
   DotNetNativeLibraryLoader.loadAllLibraries(dotnetRoot);
   ```

## 测试验证

### 测试步骤

1. 启动应用，查看日志：
   ```
   [DotNetNativeLibLoader] ========================================
   [DotNetNativeLibLoader] 开始加载 .NET Native 库...
   [DotNetNativeLibLoader] Runtime 路径: /data/user/0/.../dotnet/shared/...
   [DotNetNativeLibLoader] ========================================
   [DotNetNativeLibLoader] 正在加载: libSystem.Native.so
   [DotNetNativeLibLoader]   ✓ libSystem.Native.so 加载成功
   [DotNetNativeLibLoader] 正在加载: libSystem.Globalization.Native.so
   [DotNetNativeLibLoader]   ✓ libSystem.Globalization.Native.so 加载成功
   [DotNetNativeLibLoader] 正在加载: libSystem.IO.Compression.Native.so
   [DotNetNativeLibLoader]   ✓ libSystem.IO.Compression.Native.so 加载成功
   [DotNetNativeLibLoader] 正在加载: libSystem.Security.Cryptography.Native.Android.so
   [DotNetNativeLibLoader]   ✓ libSystem.Security.Cryptography.Native.Android.so 加载成功
   [DotNetNativeLibLoader] ========================================
   [DotNetNativeLibLoader] ✅ .NET Native 库加载完成
   [DotNetNativeLibLoader] ========================================
   ```

2. 测试网络连接：
   - 尝试连接到 PC 上的 tModLoader 服务器
   - 应该能够成功建立 TCP 连接
   - TLS/SSL 握手应该正常工作

### 预期结果

- ✅ Socket 创建成功
- ✅ TCP 连接成功（不再 timeout）
- ✅ TLS/SSL 加密正常工作
- ✅ 可以连接到局域网服务器

## 常见问题

### Q1: 库加载失败怎么办？

**错误信息**:
```
[DotNetNativeLibLoader] ✗ libSystem.Native.so 加载失败 (必需库)
java.lang.UnsatisfiedLinkError: dlopen failed: library "libSystem.Native.so" not found
```

**解决方法**:
1. 检查 runtime 路径是否正确
2. 确认版本号匹配（`10.0.0-rc.2.25502.107`）
3. 检查文件是否存在于 assets 或设备上

### Q2: 为什么要按顺序加载？

因为库之间有依赖关系：
- `Cryptography.Native.Android` 依赖 `System.Native`
- 如果顺序错误，会导致符号解析失败

### Q3: 可选库加载失败有影响吗？

可选库（Globalization, IO.Compression 等）加载失败不会影响网络功能，只会在日志中显示警告。

## 参考

- `.NET Runtime 源码`: `D:/runtime-10.0.0-rc.2/src/native/libs/`
- `Xamarin 源码`: `D:/new/android-main/src/java-runtime/`
- `详细文档`: `DOTNET_NATIVE_LIBS_LOADING.md`
