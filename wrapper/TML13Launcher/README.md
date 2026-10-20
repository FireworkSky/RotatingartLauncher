# TML13Launcher（tModLoader 1.3 包装启动器）

tModLoader 1.3 跑在 **mono** 上，没有 CoreCLR 的 `DOTNET_STARTUP_HOOKS`，
所以 RAL 的「补丁」体系（`patches/`，基于 Harmony + startup hook）用不了。
这个包装 exe 就是 1.3 的补丁载体：安装时由安装器拷进游戏目录，
`game_info.json` 的启动入口指向它，它再加载真正的 `tModLoader.exe`。

目录和 `patches/` 一样**只放源码**，Gradle 构建不会编译它；
产物 `TML13Launcher.exe` 走 LFS，由构建脚本同步到
`app/src/main/assets/prebuilt/tml13/`，安装时再拷进游戏目录。

## 它做了什么

入口 `Program.Main`：切换工作目录 → 注册嵌入程序集解析 → 反射打补丁 → 调
`tModLoader.exe` 的 EntryPoint（`MonoLaunch.Main`）。

具体补丁（用 MonoMod 19 的 `Hook`）：

| 位置 | 问题 | 处理 |
|---|---|---|
| `Terraria.ModLoader.Engine.GLCallLocker.Init` | 会 hook 旧 FNA 的 `OpenGLDevice.ForceToMainThread`，新版 FNA 走 FNA3D 没有这个类型，`GetMethod` 返回 null → `NullReferenceException`，卡在 `EngineInit` | 换成「只记录主线程 ID」 |
| `Terraria.ModLoader.Engine.InstallVerifier.InstallCheck` | 校验 Terraria.exe 的 MD5（只认 1.3.5.3 GOG 版），失败直接 `Environment.Exit(1)` | 视为合法 GOG 安装 |
| `Terraria.ModLoader.Core.MdbManager.Symbolize` | mono 上 Cecil 版本对不上会二次抛 `FieldAccessException`，把真正的异常信息吞掉（log4net 只打 "Exception rendering object type [...]"） | 打成 no-op，异常信息才能正常打印 |
| `Terraria.ModLoader.IO.ImageIO.PngToTexture2DAsync` / `RawToTexture2DAsync` | 工作线程加载模组贴图，原实现把 GL 调用塞进旧 FNA 的 `OpenGLDevice.actions` 队列；FNA3D 没有这个队列 → `NullReferenceException`，CalamityMod 等直接加载失败 | CPU 侧解码留在原线程，`Texture2D` 创建 + `SetData` 排进主线程队列，钩住 `Game.Tick` 每帧排空（见 `GlCompat.cs`） |

另外通过 `AssemblyResolve` 从 `tModLoader.exe` 的 manifest resource 里解出
ReLogic / MonoMod 20.11 / Steamworks.NET / Mono.Cecil 等内嵌程序集——包装器要在
EntryPoint 之前反射这些类型，那时 tModLoader 自己的加载器还没注册。

> 注意：tModLoader 1.3 的 `MonoModHooks` 会拦截「未申请原生调转权限」的 detour，
> 而它自带的 `RequestNativeAccess()` 用 `StackTrace.GetFrame(1)` 认调用方，
> 反射调用会认成 mscorlib。包装器直接把自身塞进它的私有
> `NativeDetouringGranted` 集合（见 `Program.ApplyLateFixes`）。

## 编译

```
./build.sh
```

需要 `mcs`。`libs/` 里的三个程序集是 MonoMod 19.12.19 / Cecil 0.11.6
（Android 可用的版本，mono 4.5 profile 能加载）；不要换成 RAL `assets/MonoMod.zip`
里的 MonoMod 25——那份是 net8 的，mono 加载会炸。

## 配套的 FNA 修改

包装器需要新版 FNA（FNA3D），但 tModLoader 1.3 的代码是按旧 FNA 写的，
FNA 源码侧做了三处兼容（编译好的 `FNA.dll` 在 `app/src/main/assets/prebuilt/tml13/`）：

1. `SDL2.SDL_RWFromMem(byte[], int)` 重载（旧绑定有，新绑定只剩 `IntPtr` 版本）；
2. `Texture.Name` setter 在 `GraphicsDevice` 还没赋值时不再调 `FNA3D_SetTextureName`；
3. Android 建窗后按需 `SDL_StartTextInput()`（`FNA_TEXTINPUT_ALWAYS_ON=1` 才自动弹，
   默认由 RAL 的「键盘」虚拟按键触发）。
