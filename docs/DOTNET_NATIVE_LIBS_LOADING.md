# .NET Native 库加载指南

## 问题总结

Android 上的 .NET 应用需要手动加载 native 库才能使用网络和加密功能。

## 关键发现

### 1. 必需的 Native 库

根据 .NET Runtime 源码分析：

| 库名 | 是否必需 | 需要 JNI 初始化 | 用途 |
|------|---------|-----------------|------|
| `libSystem.Native.so` | ✅ 是 | ❌ 否 | **最重要**！包含所有 Unix 系统调用封装（socket, connect, bind, listen 等）|
| `libSystem.Security.Cryptography.Native.Android.so` | ✅ 是 | ✅ 是 | TLS/SSL 加密，通过 JNI 调用 Android 的 Java 加密 API |
| `libSystem.Globalization.Native.so` | ⚠️ 可选 | ❌ 否 | 本地化和字符编码 |
| `libSystem.IO.Compression.Native.so` | ⚠️ 可选 | ❌ 否 | 数据压缩 |
| `libSystem.Net.Security.Native.so` | ⚠️ 可选 | ❌ 否 | 通常不存在，功能已集成到 Cryptography 中 |

### 2. 加载顺序至关重要

**正确的加载顺序**：
1. `System.Native.so` - 必须最先加载（其他库依赖它）
2. `Globalization.Native.so` - 可选
3. `IO.Compression.Native.so` - 可选
4. `Cryptography.Native.Android.so` - 依赖 System.Native
5. `Net.Security.Native.so` - 可选

### 3. JNI 初始化

只有 `libSystem.Security.Cryptography.Native.Android.so` 需要 JNI 初始化：

- 它的 `JNI_OnLoad` 函数会缓存 Java 类和方法引用
- 必须通过 `System.load()` 加载（不能用 `System.loadLibrary()`）
- 加载后会自动调用 `JNI_OnLoad`

**其他库不需要 JNI 初始化**，它们是纯 native 代码。

## 使用方法

### 在应用启动时加载

```java
import com.app.ralaunch.netcore.DotNetNativeLibraryLoader;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 在启动 .NET 之前加载所有 native 库
        String dotnetRoot = getFilesDir().getAbsolutePath() + "/dotnet";
        boolean success = DotNetNativeLibraryLoader.loadAllLibraries(dotnetRoot);

        if (!success) {
            Log.e("MainActivity", "Failed to load .NET native libraries!");
            // 处理错误...
        }

        // 现在可以安全地启动 .NET Runtime
        // ...
    }
}
```

## 常见错误

### 错误 1: Socket 连接失败
```
System.Net.Sockets.SocketException: Connection timed out
```
**原因**: 没有加载 `libSystem.Native.so`

**解决**: 确保按正确顺序加载，`System.Native.so` 必须最先加载

### 错误 2: TLS/SSL 失败
```
System.IO.IOException: The read operation failed, see inner exception
```
**原因**: `libSystem.Security.Cryptography.Native.Android.so` 未正确加载或初始化

**解决**:
1. 使用 `System.load(fullPath)` 而不是 `System.loadLibrary()`
2. 确保在 `System.Native.so` 之后加载

### 错误 3: UnsatisfiedLinkError
```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libSystem.Native.so" not found
```
**原因**: 库文件不存在或路径不正确

**解决**:
1. 检查文件路径是否正确
2. 确认 runtime 版本号匹配（例如 `10.0.0-rc.2.25502.107`）

## Xamarin 的做法

Xamarin Android 也是类似的方式：

```java
// Xamarin 加载 Mono runtime
System.loadLibrary("monosgen-2.0");
System.loadLibrary("xamarin-app");
System.loadLibrary("mono-native");
System.loadLibrary("monodroid");
```

对于 .NET 6+，Xamarin 注释说明：
```java
if (!BuildConfig.DotNetRuntime) {
    // .net5+ APKs don't contain `libmono-native.so`
    System.loadLibrary("mono-native");
}
```

这表明 .NET 6+ 使用不同的 native 库结构，需要手动加载 `System.Native.so` 和 `Cryptography.Native.Android.so`。

## 参考

- .NET Runtime 源码: `D:/runtime-10.0.0-rc.2/src/native/libs/`
- Cryptography JNI 初始化: `System.Security.Cryptography.Native.Android/pal_jni_onload.c`
- Socket 实现: `System.Native/pal_networking.c`
- Xamarin 源码: `D:/new/android-main/src/java-runtime/`
