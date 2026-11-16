# tModLoader 120 FPS 补丁 0.1.1

启动器补丁管理中可独立开关。前台绘制最高 120 FPS，使用 tML 原有的 60 Hz 世界逻辑计时，不启用 `superFast`，不改游戏文件或保存的 FrameSkipMode 设置。关闭补丁后下次启动恢复原行为。

补丁在 `Main.DoUpdate` 中将前台的跳帧模式读取映射为 Off，保留原有 1/60 秒累加、扣减和积压限制；通过 FNA `Game.Tick` 安排每帧截止时间；设备创建与重置请求 `PresentInterval.Immediate`。背景、专用服务器不改变其计时读取和节流行为。启动时验证 IL 的跳帧读取、60 Hz 阈值和 SuppressDraw 结构，不匹配则记录完整错误并回滚安装，不盲目改偏移。

计时 IL 在 `ModContent.Load` 返回后安装，在内容加载/卸载前移除，避免提前修改 DoUpdate 破坏 MobileServer 原有的偏移校验。加载生命周期 Hook 使用明确优先级，在开房补丁外层运行；内容加载失败不重新安装计时。第一次内容加载成功后在主线程重置呈现参数，让创建于加载前的设备也能解除垂直同步等待。0.1.1 修正 0.1.0 的开房兼容问题，并通过版本升级触发已安装补丁的自动更新。

这不是完整的运动插值方案：实体位置仍按逻辑帧更新，重复绘制不会凭空生成实体中间位置。部分原生 UI、视觉效果和模组在 Draw 内更新动画，可能随绘制频率变化。120 FPS 是上限，设备性能、刷新率、驱动和系统呈现节流均可能降低实际帧率；没有修改系统刷新率设置。与其他修改游戏循环、跳帧或垂直同步的模组同时使用需要单独验证。

## 构建与验证

```powershell
./patches/TModLoaderFrameRatePatch/build.ps1
./patches/TModLoaderFrameRatePatch/tests/Run.ps1 -TmlRoot 'D:/Steam/steamapps/common/tModLoader'
```

ZIP 仅包含 `patch.json` 和 `TModLoaderFrameRatePatch.dll`，复用启动器现有的 MonoMod 依赖与 DOTNET_STARTUP_HOOKS 加载路径。没有新增用户配置格式或配置迁移。

RuntimeHarness 使用 Terraria 1.4.4.9 / tML 2026.9.3.1 的真实 DLL 和生产 ILHook。只截取实际 DoUpdate 的计时路径执行，绕过内容、音频和世界加载，并以计数器代替计时路径之后的世界模拟。以 60/120/144/240 FPS 输入十秒时，世界逻辑计数应为 599–600；另验证三个跳帧设置均保留、后台/专用服务器读取、即时呈现、实际 Tick Hook 的 120 FPS 节流、失焦恢复、卸载、非法布局回滚及重新安装。第二轮通过 DOTNET_STARTUP_HOOKS 加载独立生产 DLL 执行相同测试。

测试不会完整渲染游戏，不等同于真机持续 120 FPS、所有 Draw 动画或模组兼容性验证。真机验证应在独立测试人物/世界中检查帧率、移动速度、菜单触摸、切后台及渲染重置，并与关闭补丁时对照。

2026-11-16：53 项真实 DLL 断言及通过生产启动钩子的 54 项断言通过。与 MobileServer 的组合测试通过原有 110 项断言，确认内容加载、失败重试与卸载均能共存（人为破坏 IL 的独立测试在加载生命周期测试前执行，以免错误地绕过补丁的卸载入口）。

Redmi 23113RKC6C / Android 16 / SDL3 + sdl2-compat 真机上，早期 0.1.0 补丁已加载，但 MIUI `PRIORITY_MIUI_REFRESH_RATE` 将实际呈现限制为 60 Hz。仅临时提高标准 peak_refresh_rate 没有效果；同步调整 MIUI 的用户刷新率后，显示模式切到 120 Hz，游戏 SurfaceFlinger 时间戳的中位呈现帧率约 120.3 FPS。相关记录保存在 `build/adb-diagnostics/tml-120fps-display-miui-temporary-120hz.log` 与 `tml-120fps-surface-latency.log`。后续按用户所选 120 FPS 将该测试设备保持为 120 Hz。这些设备设置通过 ADB 诊断修改，补丁本身不会写入 Android 系统设置。0.1.1 的新生命周期另经真实 DLL 测试，尚未在运行中的手机游戏上热替换。

参考：[FNA Game.Tick](https://github.com/FNA-XNA/FNA/blob/master/src/Game.cs)、[MonoMod RuntimeDetour](https://monomod.dev/docs/RuntimeDetour/Usage.html)、[Terraria 高帧率方案](https://github.com/Pavlikmeow/terraria-high-fps)。参考代码不直接复制；补丁围绕本地 tML 的现有计时路径实现。
