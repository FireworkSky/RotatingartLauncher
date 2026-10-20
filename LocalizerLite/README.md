# LocalizerLite（tModLoader 1.3 精简汉化 mod）

从 [Localizer](https://github.com/chi-rei-den/Localizer) 1.5.0.19（`!Localizer`）反编译、
裁剪出来的**无 GUI 版**：去掉 Squid 界面 / ModBrowser / 网络下载 / 导出打包 / Harmony 依赖，
只保留「读 `.locpack` 汉化包 → 导入」这一条链路。

原版在 Android 的 mono 上会在 `AssemblyManager.Instantiate` 阶段 native abort
（大量 Harmony 1.2 transpiler + 多线程初始化），精简后只保留：

- `BasicItem/NPC/Buff/Projectile/Prefix` 名称导入
- `CustomModTranslation`（mod 自带 Localization 的翻译）
- `Ldstr` IL 字符串替换（走 tML 自带 MonoMod 的 `HookEndpointManager.Modify`，与普通 mod 的 IL hook 同机制）

## 用法

1. 编译：

   ```
   RAL_GAME_DIR=<tModLoader 1.3 游戏目录> ./build.sh
   ```

   `build.sh` 会用 `mcs` 编译出 `out/Localizer.dll`，再用 `pack.py` 打成
   `out/Localizer.tmod`（tModLoader 0.11 的 TmodFile 格式，不压缩）。

2. 安装：
   - 把 `Localizer.tmod` 放进游戏的 `ModLoader/Mods/`
   - 汉化包（`*.locpack`）放进 **与 `Mods` 同级的** `ModLoader/Localizer/` 目录
     - 不再需要原版的 `Localizer/Source`、`Localizer/Download` 子目录
   - `enabled.json` 里确保有 `"!Localizer"`

3. 启动游戏，日志里应出现：
   ```
   [Localizer]: Auto import begin (all mods loaded).
   [Localizer]: Scanning packages in [...], found 1
   [Localizer]: Loaded package: [...] for mod [CalamityMod]
   ...
   [Localizer]: Auto import end.
   ```

## 与上游的差异

| 原版 | 本精简版 |
|---|---|
| Squid 即时模式 GUI（`MainWindow` / `UIHost` / `UIBindings`） | 全部删除 |
| ModBrowser 镜像 patch（Harmony transpiler） | 全部删除 |
| 网络下载 / GitHub 更新 / 包导出、打包 | 全部删除（不依赖网络） |
| Harmony 1.2 的 ctor hook（`AssemblyManager.Instantiate`） | 删除，改为 `PostSetupContent` 时统一导入 |
| 汉化包目录 `./Localizer/Source`、`./Localizer/Download`、`Mods/*.locpack` | 统一为与 `Mods` 同级的 `Localizer/*.locpack` |
| Ldstr 导入器可选 Cecil/Harmony/MonoMod | 只保留 MonoMod（最稳） |

`Info` 里仍保留原版的 `dllReferences: Ninject 0Harmony Squid`（tML 0.11 的 Info 是二进制编码，
为省事直接沿用原文件），因此 `libs/` 里也带着这三个 dll；实际代码只用到 Ninject。

## 目录

```
Localizer/                   Mod 入口（Localizer.cs、Configuration、Hooks、Utils、Lang）
Localizer.Package.*/         包加载/导入（JsonFileLoad、PackedPackageLoad、各 Importer）
Localizer.DataModel*/        数据模型（Package、Basic*File、LdstrFile 等）
Localizer.Helpers/           扩展方法
System.Reflection/           NoroHelper（反射/IL 工具）
libs/                        编译期引用（MonoMod/Cecil/Newtonsoft/Ninject/log4net/ReLogic + 运行时 Ninject/0Harmony/Squid）
build.sh                     编译脚本
pack.py                      tModFile 打包脚本
```
