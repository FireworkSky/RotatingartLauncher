# tModLoader 1.3 支持（mono + FNA）

RA 内置的 tModLoader 补丁体系是给 **CoreCLR（DOTNET_STARTUP_HOOKS）** 用的，1.3 跑在 **mono**
上，没有 startup hook 机制，所以 1.3 的兼容修复全部收在一个「包装 exe」里。

## 组成

| 组件 | 位置 | 作用 |
|---|---|---|
| 安装逻辑 | `TerrariaInstallPlugin` | 识别 1.3 的 `tModLoader.exe` 发行包，平铺覆盖到 Terraria 目录 |
| 包装 exe | `tools/tml13-launcher` → `assets/prebuilt/tml13/TML13Launcher.exe` | 入口，加载 `tModLoader.exe` 并打运行时补丁 |
| FNA | `assets/prebuilt/tml13/FNA.dll`（FNA 源码编译，net40） | 新版 FNA3D 版，兼容 1.3 的 XNA4 API |
| libgdiplus / libfontconfig | `jniLibs/arm64-v8a` | mono 的 System.Drawing；tML 1.3 用 Bitmap 读模组贴图 |
| BCL 补充 | `assets/bcl-extra` | mono-bcl 里缺的 System.Xaml / TypeConverter 等 |

## 包装 exe 做了哪些修复

1. **GLCallLocker.Init**：1.3 会 hook `OpenGLDevice.ForceToMainThread`，新版 FNA 只有 FNA3D
   驱动，类型不存在 → 改为「只记录主线程 ID」。
2. **InstallVerifier.InstallCheck**：1.3 会校验 Terraria.exe 的 MD5（要求 1.3.5.3 的 GOG 版），
   Android 上不可能满足，校验失败会 `Environment.Exit(1)` → 改为视为合法 GOG 安装。
3. **GL 调用回主线程**：`ImageIO.PngToTexture2DAsync / RawToTexture2DAsync` 在后台线程加载贴图，
   原实现把 GL 部分塞进旧 FNA 的 `OpenGLDevice.actions`；FNA3D 没有这个队列。包装 exe 接管这
   两个方法：CPU 侧解码留在原线程，`Texture2D` 创建 + `SetData` 排进队列，钩住 `Game.Tick`
   每帧在主线程执行。
4. **嵌入程序集解析**：ReLogic / MonoMod(20.11) / Steamworks.NET / Mono.Cecil 等都在
   `tModLoader.exe` 的 manifest resource 里，包装 exe 按同样规则解析，便于在 EntryPoint 前反射打补丁。

## FNA 侧的两处补丁（FNA 源码）

- `SDL2.SDL_RWFromMem(byte[], int)` 重载（旧 FNA 有，新绑定只剩 IntPtr 版本）；
- `Texture.Name` setter 在 GraphicsDevice 尚未赋值时不再调用 `FNA3D_SetTextureName`
  （`GraphicsResource.GraphicsDevice` setter 会先写 Name 再赋设备，旧 FNA 不做这件事）。

## 已知限制

- `!Localizer` 这类深度 patch WPF/WinForms 的模组需要完整的 System.Xaml/WindowsBase 链，
  Android mono 上仍不稳定，建议关闭。
- 1.3 的模组贴图加载依赖上面的主线程队列；`Game.Tick` 不跑（例如把游戏切到后台）时
  贴图加载会挂起。
- 需要 mono 运行时（`mono:4.5`），安装时会写入 `game_info.json` 的
  `dotNetRuntimeVersionOverride`。

## 运行时依赖（2026-10 实测）

| 依赖 | 位置 | 说明 |
|---|---|---|
| `libMonoPosixHelper.so` | `jniLibs/arm64-v8a` | mono 的 `System.IO.Compression` 走它的 zlib 封装；缺它 CalamityMod 读 `.csc` 结构图时 `DllNotFoundException` 被自动禁用。现为 NDK aarch64 编译的精简版（导出 5 个 zlib 函数，NEEDED 仅系统 libz） |
| `libgdiplus.so` / `libfontconfig.so` | `jniLibs/arm64-v8a` | System.Drawing；tModLoader 1.3 用 Bitmap 读模组贴图 |
| `assets/bcl-extra` | 补进 mono `bcl/` | mono-bcl 缺的 System.Xaml / System.ComponentModel.TypeConverter 等（模组配置反序列化要用） |
| `assets/prebuilt/tml13` | 安装时拷进游戏目录 | `TML13Launcher.exe` + 交叉编译的 `FNA.dll`（含 SDL_RWFromMem 数组重载、Texture.Name 空设备保护、Android 建窗后按需 `SDL_StartTextInput`） |

## mono 性能参数（MonoLauncher 统一设置）

```
MONO_GC_PARAMS = major=marksweep-conc-par,nursery-size=128M
```

- 进世界/保存世界时的卡顿主要是 minor GC：默认 nursery 4MB 太小，放大到 128MB 后明显变好；
- `minor=split`、`concurrent-sweep`、`MONO_THREADS_SUSPEND=coop` 在这份 mono（嵌入式 + 大量 detour）上会偶发 native abort（`FORTIFY: pthread_mutex_lock called on a destroyed mutex`），**不要**默认打开；
- 用户/游戏的 `gameEnvVars` 里指定 `MONO_GC_PARAMS` 时以用户为准；
- RAL 这份 mono 带 LLVM（有 `mono_llvm_*` 符号，无 "--llvm not enabled" 提示），设置里开 Mono LLVM 实测可进世界，但内存占用明显更高，默认关。

## 已知坑

- tModLoader 1.3 的音乐走 XACT，音量在游戏内 config（`config.json` 的 Music/Sound 音量）里，默认可能是 0；先检查游戏内设置，不要先怀疑资源。
- tModLoader 的 mdb 符号化在 mono 上会二次抛 `FieldAccessException`，把真正的异常信息吞掉；包装 exe 会申请 `NativeDetouringGranted` 后把 `MdbManager.Symbolize` 打成 no-op，异常信息才能正常打印。
- `!Localizer` 这类深度 patch WPF/WinForms 的模组在 mono/Android 上仍会崩，建议关闭。
