# Rotating Art Launcher

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="Logo" width="120"/>
</p>

<p align="center">
  <strong>一个强大的 Android 平台 .NET 游戏启动器</strong>
</p>

<p align="center">
  <a href="#特性">特性</a> •
  <a href="#支持的游戏">支持的游戏</a> •
  <a href="#系统要求">系统要求</a> •
  <a href="#构建">构建</a> •
  <a href="#技术栈">技术栈</a> •
  <a href="#许可证">许可证</a>
</p>

---

## 📖 简介

Rotating Art Launcher 是一个专为 Android 平台设计的游戏启动器，能够运行使用 FNA/XNA 框架开发的 .NET 游戏。本项目通过集成 .NET Core Runtime 和 SDL2，实现了在 Android 设备上原生运行 Windows PC 游戏的能力。

## ✨ 特性

- 🎮 **原生 .NET 支持** - 集成完整的 .NET 8.0 Runtime，支持运行 .NET 程序集
- 🚀 **FNA/XNA 框架兼容** - 完美支持 FNA 和 XNA 游戏框架
- 🔧 **灵活配置** - 支持多种游戏配置和控制布局
- 🌐 **多语言支持** - 中文和英文界面
- 🎨 **现代 UI** - Material Design 风格的用户界面

## 🎮 支持的游戏

- **tModLoader** - Terraria 模组加载器
- **Stardew Valley** - 星露谷物语
- 其他基于 FNA/XNA 的游戏

## 📋 系统要求

- **Android 版本**: 7.0 (API 24) 或更高
- **架构支持**: ARM64-v8a (64位)
- **存储空间**: 至少 500MB 可用空间
- **RAM**: 建议 4GB 或以上


## 🔧 技术栈

### Android 层
- **语言**: Java 17
- **最小 SDK**: API 24 (Android 7.0)
- **目标 SDK**: API 34 (Android 14)
- **构建工具**: Gradle 8.2

### 原生层
- **语言**: C/C++
- **框架**:
  - SDL2 - 跨平台媒体层
  - GL4ES - OpenGL 到 OpenGL ES 转换层
- **运行时**: .NET 8.0 CoreCLR

### 核心组件
- **GameLauncher** - 游戏启动管理
- **rustcorehost** - .NET Runtime 宿主
- **SDL_android_main** - 原生入口点
- **FNA3D** - FNA 3D 渲染引擎
- **FAudio** - 音频引擎



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

## 📄 许可证

本项目基于 **GNU Lesser General Public License v3.0 (LGPLv3)** 开源。

详见 [LICENSE](LICENSE) 文件。

### 第三方库许可

- **SDL2** - [Zlib License](https://www.libsdl.org/license.php)
- **GL4ES** - [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE)
- **.NET Runtime** - [MIT License](https://github.com/dotnet/runtime/blob/main/LICENSE.TXT)
- **FNA** - [Ms-PL License](https://github.com/FNA-XNA/FNA/blob/master/LICENSE)

## 👥 作者

**FireworkSky** - [GitHub](https://github.com/FireworkSky)
**LaoSparrow** - [GitHub](https://github.com/LaoSparrow)

## 🙏 致谢

- [SDL Project](https://www.libsdl.org/)
- [GL4ES](https://github.com/ptitSeb/gl4es)
- [.NET Runtime](https://github.com/dotnet/runtime)
- [FNA](https://github.com/FNA-XNA/FNA)
- 所有贡献者和用户

## 📞 联系方式

如有问题或建议，请：
- 提交 [Issue](https://github.com/Fireworkshh/Rotating-art-Launcher/issues)
- 访问 [Discussions](https://github.com/Fireworkshh/Rotating-art-Launcher/discussions)

---

<p align="center">
  Made with ❤️ by Fireworkshh
</p>

<p align="center">
  ⭐ 如果这个项目对你有帮助，请给个 Star！
</p>
