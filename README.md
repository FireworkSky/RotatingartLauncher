# Rotating Art Launcher

<div align="center">
  <img src="icons/ral_app.svg" alt="Rotating Art Launcher Logo" width="128" height="128">
  
  **Rotating Art Launcher在 Android 设备上运行 .NET 游戏的强大启动器**
  
  [![Android](https://img.shields.io/badge/Android-7.0+-green?logo=android)](https://www.android.com)
  [![.NET](https://img.shields.io/badge/.NET-8.0-blue?logo=dotnet)](https://dotnet.microsoft.com)
  [![License](https://img.shields.io/badge/License-LGPL--3.0-green)](LICENSE)
  [![Stars](https://img.shields.io/github/stars/Fireworkshh/Rotating-art-Launcher?style=social)](https://github.com/Fireworkshh/Rotating-art-Launcher/stargazers)
  
  [![Patreon](https://img.shields.io/badge/Patreon-支持我们-FF424D?style=for-the-badge&logo=patreon&logoColor=white)](https://www.patreon.com/c/RotatingArtLauncher)
</div>

---

## ✨ 特性

- 🎮 **原生 .NET 支持** - 集成完整的 .NET 8.0 Runtime，支持运行 .NET 程序集
- 🚀 **FNA/XNA 框架兼容** - 完美支持 FNA 和 XNA 游戏框架
- 🔧 **灵活配置** - 支持多种游戏配置和控制布局
- 🎨 **现代 UI** - Material Design 3 风格的用户界面
- 🌐 **多语言支持** - 中文和英文界面
- 🎯 **多种渲染器** - 支持 GL4ES、OSMesa + Zink、Angle 等多种渲染方案
- 🎮 **完整手柄支持** - Xbox 手柄模式、虚拟手柄控制器
- ⚡ **高性能优化** - 线程亲和性绑定、性能优化

## 🎮 支持的游戏

- **tModLoader** - Terraria 模组加载器
- **Stardew Valley** - 星露谷物语
- 其他基于 FNA/XNA 的游戏


## 📋 系统要求

| 项目 | 要求 |
|------|------|
| **Android 版本** | 7.0 (API 24) 或更高 |
| **架构支持** | ARM64-v8a (64位) |
| **存储空间** | 至少 500MB 可用空间 |
| **RAM** | 建议 4GB 或以上 |

## 🚀 快速开始

### 下载安装

1. 前往 [Releases](https://github.com/Fireworkshh/Rotating-art-Launcher/releases) 页面下载最新版本
2. 安装 APK 文件
3. 授予必要的存储权限
4. 导入游戏文件并开始游玩

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/Fireworkshh/Rotating-art-Launcher.git
cd Rotating-art-Launcher

# 使用 Gradle 构建
./gradlew assembleDebug
```

## 🔧 技术栈

### Android 层
- **语言**: Java 17
- **最小 SDK**: API 24 (Android 7.0)
- **目标 SDK**: API 34 (Android 14)
- **构建工具**: Gradle 8.2
- **UI 框架**: Material Design 3

### 原生层
- **语言**: C/C++
- **核心框架**: 
  - SDL2 - 跨平台媒体层
  - GL4ES - OpenGL 到 OpenGL ES 转换层
  - Mesa 3D - 软件渲染支持
  - Virglrenderer - 虚拟化 GPU 渲染
- **运行时**: .NET 8.0 CoreCLR

### 核心组件
| 组件 | 说明 |
|------|------|
| **GameLauncher** | 游戏启动管理 |
| **rustcorehost** | .NET Runtime 宿主 |
| **SDL_android_main** | 原生入口点 |
| **FNA3D** | FNA 3D 渲染引擎 |
| **FAudio** | 音频引擎 |

## 💝 赞助支持

如果这个项目对您有帮助，欢迎通过 Patreon 支持我们的开发工作！

<div align="center">
  <a href="https://www.patreon.com/c/RotatingArtLauncher">
    <img src="https://img.shields.io/badge/成为赞助者-FF424D?style=for-the-badge&logo=patreon&logoColor=white" alt="成为赞助者" />
  </a>
</div>

### 赞助层级

| 层级 | 价格 | 权益 |
|------|------|------|
| 🎁 **Supporter** | $3/月 | 开发日志访问 · 项目更新通知 · 社区访问 · 早期功能预览 |
| 🚀 **Early Access** | $5/月 | Supporter 所有权益 + 新版本抢先体验 · 月度进展报告 · 优先技术支持 · 功能投票权 |
| ⭐ **Premium** | $10/月 | Early Access 所有权益 + 定制功能建议 · 专属 Discord 频道 · 致谢名单 · 年度项目回顾 |

您的支持将帮助我们：
- ✨ 添加更多游戏支持
- 🐛 修复 Bug 和性能优化
- 📚 完善文档和教程
- 🎨 改进用户界面
- 🔧 开发新功能

## 🐛 已知问题

- [ ] 某些游戏可能需要额外的库文件
- [ ] 性能在低端设备上可能受限
- [ ] 部分游戏模组可能不兼容

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 如何贡献

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📝 更新日志

### v1.0.0 (2024-10-26)
- ✨ 初始版本发布
- 🎮 支持 tModLoader 和 FNA 游戏
- 🖥️ 全屏和刘海屏支持
- 📦 自动资源解压
- 🌐 中英文双语支持

## 📚 文档

详细文档请查看 [docs](docs/) 目录：

- [代码结构](docs/CODE_STRUCTURE.md)
- [渲染器使用指南](docs/RENDERER_USAGE_GUIDE.md)
- [补丁系统](docs/PATCH_SYSTEM.md)
- [Xbox 手柄架构](docs/XBOX_CONTROLLER_ARCHITECTURE.md)
- [更多文档...](docs/)

## 📄 许可证

本项目基于 **GNU Lesser General Public License v3.0 (LGPLv3)** 开源。

详见 [LICENSE](LICENSE) 文件。

### 第三方库许可

- **SDL2** - [Zlib License](https://www.libsdl.org/license.php)
- **GL4ES** - [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE)
- **.NET Runtime** - [MIT License](https://github.com/dotnet/runtime/blob/main/LICENSE.TXT)
- **FNA** - [Ms-PL License](https://github.com/FNA-XNA/FNA/blob/master/LICENSE)
- **Mesa 3D** - [MIT License](https://docs.mesa3d.org/license.html)
- **LWJGL3** - [BSD-3 License](https://www.lwjgl.org/license)

## 👥 贡献者

<div align="center">
  
### 主要作者

**FireworkSky** - [GitHub](https://github.com/FireworkSky)
  
项目主要开发者 · 触摸控制系统 · 渲染器集成 · 多进程架构

### 核心贡献者

**LaoSparrow (佬麻雀)** - [GitHub](https://github.com/LaoSparrow)

手柄支持 · 渲染器优化 · 性能改进 · Bug 修复

**EternalFuture**

文档维护 · 代码审查

</div>

## 🙏 特别致谢

感谢以下开源项目和社区：

- [SDL Project](https://www.libsdl.org/) - 跨平台媒体库
- [GL4ES](https://github.com/ptitSeb/gl4es) - OpenGL 兼容层
- [.NET Runtime](https://github.com/dotnet/runtime) - .NET 运行时
- [FNA](https://github.com/FNA-XNA/FNA) - XNA 兼容框架
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) - Minecraft 启动器灵感
- 所有贡献者和用户
- 特别感谢所有 [Patreon 支持者](https://www.patreon.com/c/RotatingArtLauncher)！

## 📞 联系方式

如有问题或建议，请：
- 💬 提交 [Issue](https://github.com/Fireworkshh/Rotating-art-Launcher/issues)
- 🗣️ 访问 [Discussions](https://github.com/Fireworkshh/Rotating-art-Launcher/discussions)
- 💝 支持我们 [Patreon](https://www.patreon.com/c/RotatingArtLauncher)

---

<div align="center">
  
**Made with ❤️ by the Rotating Art Launcher Team**

⭐ 如果这个项目对你有帮助，请给个 Star！

</div>
