<img width="100%" src="https://capsule-render.vercel.app/api?type=waving&color=0:6C3483,50:2874A6,100:1ABC9C&height=220&section=header&text=Rotating%20Art%20Launcher&fontSize=42&fontColor=ffffff&animation=fadeIn&fontAlignY=38&desc=在%20Android%20上运行%20.NET%20桌面游戏&descSize=18&descAlignY=55&descAlign=50"/>

<div align="center">

<img src="icons/ral_app.svg" alt="Logo" width="100" height="100">

<br/>

<a href="README.md">English</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href="README_ZH.md">中文</a>

<br/><br/>

[![Android](https://img.shields.io/badge/Android_7.1+-34A853?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com)
[![.NET](https://img.shields.io/badge/.NET_10.0-512BD4?style=for-the-badge&logo=dotnet&logoColor=white)](https://dotnet.microsoft.com)
[![Kotlin](https://img.shields.io/badge/Kotlin_2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

[![License](https://img.shields.io/badge/License-GPL_3.0-2ea44f?style=for-the-badge)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/FireworkSky/RotatingartLauncher?style=for-the-badge&logo=github&color=yellow)](https://github.com/FireworkSky/RotatingartLauncher/stargazers)
[![Discord](https://img.shields.io/discord/724163890803638273?style=for-the-badge&logo=discord&logoColor=white&label=Discord&color=5865F2)](https://discord.gg/cVkrRdffGp)
[![Patreon](https://img.shields.io/badge/Patreon-支持我们-FF424D?style=for-the-badge&logo=patreon&logoColor=white)](https://www.patreon.com/c/RotatingArtLauncher)

<br/>

**Rotating Art Launcher** 是一款 Android 应用，让你在移动设备上运行基于 .NET 的桌面游戏。<br/>
支持 FNA/XNA 框架游戏、tModLoader、SMAPI、Everest 等模组加载器。

⚠️警告⚠️:这是针对Android 7.1+的修改版本 

</div>

---

<details>
<summary><h2>📖 目录</h2></summary>

- [支持的游戏](#-支持的游戏)
- [特性一览](#-特性一览)
- [快速开始](#-快速开始)
- [从源码构建](#-从源码构建)
- [项目架构](#-项目架构)
- [贡献指南](#-贡献指南)
- [许可证](#-许可证)
- [致谢](#-致谢)
- [联系我们](#-联系我们)

</details>

---

## 🎮 支持的游戏

<div align="center">

| 游戏 | 模组加载器 | 状态 |
|:----:|:----------:|:----:|
| **Terraria** (泰拉瑞亚) | tModLoader | ✅ 支持 |
| **Stardew Valley** (星露谷物语) | SMAPI | ✅ 支持 |
| **Celeste** (蔚蓝) | Everest | ✅ 支持 |
| 其他 FNA/XNA .NET 游戏 | — | ✅ 支持 |

</div>

## ✨ 特性一览

<table>
<tr>
<td width="50%" valign="top">

### 🧩 .NET 运行时
- 集成完整的 **.NET 10.0 Runtime**
- 原生运行 .NET 程序集
- 支持 **FNA / XNA** 游戏框架
- 内置 **MonoMod 补丁系统**

</td>
<td width="50%" valign="top">

### 🖥️ 多渲染后端
| 渲染器 | 说明 |
|:------:|:----:|
| Native OpenGL ES 3 | 最快，GPU 直接加速 |
| GL4ES | 兼容性最佳 |
| GL4ES + ANGLE | Vulkan 转译，推荐骁龙 |
| MobileGlues | GL 4.6 → GLES 3.2 |

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🎛️ 控制系统
- **虚拟摇杆** — 自定义触摸摇杆
- **虚拟按钮** — 支持不规则形状
- **虚拟键盘** — 拖动 & 透明度调整
- **Xbox 手柄** — 蓝牙 / USB
- **鼠标键盘模拟** — 触控映射
- **控件布局编辑器** — 可视化编辑
- **控件包** — 可分享的布局包

</td>
<td width="50%" valign="top">

### 🌐 更多特性
- **GOG 集成** — 登录 GOG 下载已购游戏
- **EasyTier 联机** — P2P VPN 多人组网
- **补丁系统** — 自动游戏兼容性修复
- **多进程隔离** — 游戏独立进程运行
- **动态库加载** — 按需解压原生库
- **Compose UI** — Material 3 现代界面

</td>
</tr>
</table>

## 🚀 快速开始

### 系统要求

> - 📱 Android 7.1 (API 25) 或更高
> - 🏗️ ARM64-v8a 架构设备
> - 💾 至少 2GB 可用存储

### 安装

```
1. 从 Releases 页面下载最新 APK
2. 启用「允许安装未知来源应用」
3. 安装并启动应用
4. 按照引导完成初始化
```

<div align="center">

[![Download](https://img.shields.io/badge/⬇_下载最新版本-28a745?style=for-the-badge)](https://github.com/FireworkSky/RotatingartLauncher/releases)

</div>

### 使用方法

1. 打开 Rotating Art Launcher
2. 点击 **「+」** 添加游戏（或通过 GOG 下载）
3. 选择游戏可执行文件
4. 配置渲染器和控件布局
5. 启动游戏 🎮

## 🛠️ 从源码构建

<details>
<summary><b>展开查看构建指南</b></summary>

### 前置条件

| 工具 | 版本 |
|:----:|:----:|
| Android Studio | 最新稳定版 |
| Android NDK | r28 |
| CMake | 3.22.1+ |
| JDK | 21 |
| Git | 含 LFS 支持 |

### 构建步骤

```bash
# 克隆仓库
git clone --recursive https://github.com/FireworkSky/RotatingartLauncher.git
cd RotatingartLauncher

# 构建 Debug APK
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/`

</details>

## 📁 项目架构

<details>
<summary><b>展开查看目录结构</b></summary>

```
RotatingartLauncher/
├── app/                          # Android 应用主模块
│   └── src/main/
│       ├── java/.../ralaunch/    # Kotlin/Java 源码
│       │   ├── core/             #   游戏启动核心逻辑
│       │   ├── dotnet/           #   .NET 运行时集成
│       │   ├── renderer/         #   渲染器配置与加载
│       │   ├── controls/         #   虚拟控件系统
│       │   ├── gog/              #   GOG Galaxy 集成
│       │   ├── easytier/         #   EasyTier 联机服务
│       │   ├── patch/            #   补丁管理系统
│       │   └── ui/               #   Compose UI 界面
│       ├── cpp/                  #   原生 C/C++ (SDL2, GL4ES ...)
│       └── assets/               #   运行时资源
├── shared/                       # Kotlin Multiplatform 共享模块
│   └── src/
│       ├── commonMain/           #   通用 UI、领域模型、数据层
│       └── androidMain/          #   Android 平台实现
└── patches/                      # C# 游戏补丁文件
```

</details>

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

<details>
<summary><b>展开查看贡献步骤</b></summary>

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

### 代码规范

- 遵循 Android Kotlin 编码规范
- 使用有意义的变量和函数命名
- 为复杂逻辑添加注释

### 报告问题

1. 先查看已有 [Issues](https://github.com/FireworkSky/RotatingartLauncher/issues) 避免重复
2. 创建新 Issue 时请提供：
   - 问题的清晰描述
   - 复现步骤
   - 设备信息（型号、Android 版本）
   - 日志（如有）

</details>

## 📄 许可证

本项目基于 **GNU General Public License v3.0 (GPLv3)** 开源。详见 [LICENSE](LICENSE) 文件。

<details>
<summary><b>第三方库许可</b></summary>

| 库 | 许可证 |
|:--:|:------:|
| [SDL2](https://www.libsdl.org/) | [Zlib License](https://www.libsdl.org/license.php) |
| [GL4ES](https://github.com/ptitSeb/gl4es) | [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE) |
| [.NET Runtime](https://github.com/dotnet/runtime) | [MIT License](https://github.com/dotnet/runtime/blob/main/LICENSE.TXT) |
| [FNA3D](https://github.com/FNA-XNA/FNA3D) | [Microsoft Public License](https://github.com/FNA-XNA/FNA3D/blob/master/LICENSE) |

</details>

## 🙏 致谢

<div align="center">

感谢以下开源项目和社区

</div>

| 项目 | 说明 |
|:----:|:----:|
| [SDL Project](https://www.libsdl.org/) | 跨平台媒体库 |
| [GL4ES](https://github.com/ptitSeb/gl4es) | OpenGL 兼容层 |
| [.NET Runtime](https://github.com/dotnet/runtime) | .NET 运行时 |
| [FNA](https://github.com/FNA-XNA/FNA) | XNA 兼容框架 |
| [ANGLE](https://chromium.googlesource.com/angle/angle) | OpenGL ES over Vulkan |
| [EasyTier](https://github.com/EasyTier/EasyTier) | P2P 组网 |
| [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) | 启动器灵感来源 |

<div align="center">

感谢所有贡献者和 [Patreon 支持者](https://www.patreon.com/c/RotatingArtLauncher) ！

</div>

## 📬 联系我们

<div align="center">

[![Issue](https://img.shields.io/badge/提交_Issue-171515?style=for-the-badge&logo=github&logoColor=white)](https://github.com/FireworkSky/RotatingartLauncher/issues)
[![Discussions](https://img.shields.io/badge/参与讨论-171515?style=for-the-badge&logo=github&logoColor=white)](https://github.com/FireworkSky/RotatingartLauncher/discussions)
[![Discord](https://img.shields.io/badge/加入_Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/cVkrRdffGp)
[![Patreon](https://img.shields.io/badge/支持我们-FF424D?style=for-the-badge&logo=patreon&logoColor=white)](https://www.patreon.com/c/RotatingArtLauncher)

</div>

<img width="100%" src="https://capsule-render.vercel.app/api?type=waving&color=0:6C3483,50:2874A6,100:1ABC9C&height=120&section=footer"/>
