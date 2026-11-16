# MobileServerPatch 0.1.0

把 MobileServer 0.2 的同进程局域网开房逻辑迁移为启动器补丁。房主保留画面、输入和一份权威世界，不另起服务器进程。原模组源码和现有存档不会被修改。

## 使用

- 在启动器补丁管理中启用“tModLoader 同进程开房补丁”，重新启动游戏。
- 使用“多人游戏 → 主持并游玩”，选择人物、世界、端口和密码。
- 单人世界也可输入 `/mobilehost [密码]`。`/mobilehost stop` 保存人物与世界、关闭房间并继续单人游戏。
- 加入者需要兼容的 tML 和相同游戏内容模组；不需要安装这个补丁或 MobileServer 模组。补丁不加入 tML 模组网络清单。
- 如果实际加载的模组列表包含原 MobileServer，补丁自动避让，由原模组负责开房。要使用补丁实现，请在游戏模组管理中手动停用原模组并重载。补丁不修改 enabled.json。

首版严格适配 Terraria 1.4.4.9 / tML **2026.9.3.1**，MVID `7b8a9f2a-b51f-4957-bcbc-cf6609b474c8`。IL 指令按原实现审计的偏移检查；不同构建或有冲突的 IL 修改会回滚安装，主持并游玩和命令会说明不可用原因。

补丁 ID 为 `com.app.ralaunch.tmodloader.mobileserver`，与已移除的多进程 `hostandplaypatch` 不同。补丁管理的开关在下一次启动游戏生效。此功能保留原实现的实验性质，不承诺所有内容模组与所有游戏交互兼容。

## 实现边界

`HostSession`、`HostTransport` 与七个 Host*Patches 沿用原模组实现，仅迁移命名空间、日志和注册入口。`Patcher` 在内容加载前安装 Hook，模组卸载前清理网络状态与本轮 Hook，失败的内容加载也执行回滚。`HostCommandPatch` 接入现有命令处理与帮助入口，检查本机身份并保留密码大小写，不创建伪造 Mod。

日志写入现有 Console，在 Android logcat 中查找 `tag:DOTNET` 与 `[MobileServerPatch]`。记录生命周期就绪、是否避让原模组、适配 MVID、Hook 数、端口、关闭与完整异常堆栈；不记录房间密码。

## 构建与测试

在仓库根目录运行：

```powershell
./patches/MobileServerPatch/build.ps1
./patches/MobileServerPatch/tests/Run.ps1 -TmlRoot 'D:/Steam/steamapps/common/tModLoader'
```

生产构建复用 `patches/external/tModLoader` 引用和 MonoMod.RuntimeDetour 25.3.2 编译依赖。ZIP 只含 `patch.json` 和 `MobileServerPatch.dll`，运行时复用启动器通过 MONOMOD_PATH 提供的 MonoMod 库。

HostHarness 使用真实适配版本 DLL，测试临时数据与 TCP 连接，不使用真实人物和世界。覆盖 Hook 安装、握手、玩家槽位、聊天、箱子互斥/改名/关闭、装备实体广播、端口冲突与释放、退出恢复、命令权限、大小写、反复重载、原模组避让及失败回滚。保存测试替换底层写盘入口以检查顺序，不写真实世界。

另用真正的 DOTNET_STARTUP_HOOKS 加载生产 DLL，验证内容生命周期、命令、开房与关闭；再替换为仓库旧 tML 引用 DLL，验证不支持版本的保护。测试不等同于双端图形游戏、完整区块加载、长期战斗建造和所有模组兼容性验证。

2026-11-16 验证：HostHarness 110 项、生产 DLL 启动测试 7 项、不支持版本保护 5 项断言通过；Android Release 构建及 39 项相关单元测试通过。Redmi 23113RKC6C / Android 16 上，在独立测试存档且未加载 MobileServer 模组时成功主持世界，电脑经 ADB 转发完成 5 次密码/模组握手，房主保留槽位 0。保存退出后重新开房，测试方块与人物背包物品均保留。完整双端图形联机与直接局域网连接尚未验证。

真机使用 `tests/RuntimeProbe`，它仅在传入 `-mobiletest` 参数时运行，且不依赖原 MobileServer 模组。测试前务必单独设置 `-tmlsavedirectory` 和 `-modpath`；测试过程会生成测试人物和世界。测试指令通过临时文件写完后原子重命名为 `probe/host.command`，可执行 `marker`、`save`、`reload`、`inspect`、`quit`。握手客户端通过 `HostHarness.dll --remote-handshake <地址> <端口>` 运行，允许握手响应前出现 tML 原生网络模块广播包。

该设备的原生 UDP 房间发现广播出现 `SocketException: Permission denied`，未影响已验证的 TCP 握手。自动发现和直接局域网连接仍需后续验证；本次没有修改原有发现实现。

参考：[MonoMod RuntimeDetour](https://monomod.dev/docs/RuntimeDetour/Usage.html)、[.NET startup hook 设计](https://github.com/dotnet/runtime/blob/main/docs/design/features/host-startup-hook.md)。
