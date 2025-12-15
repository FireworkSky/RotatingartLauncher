<h1 align="center">Rotating Art Launcher</h1>

<div align="center">
  <a href="README.md">中文</a> | <a href="README_EN.md">English</a>
</div>

<div align="center">
  <img src="icons/ral_app.svg" alt="Rotating Art Launcher Logo" width="128" height="128">
  
  
  
  [![Android](https://img.shields.io/badge/Android-7.0+-green?logo=android)](https://www.android.com)
  [![.NET](https://img.shields.io/badge/.NET-8.0-blue?logo=dotnet)](https://dotnet.microsoft.com)
  [![License](https://img.shields.io/badge/License-LGPL--3.0-green)](LICENSE)
  [![Stars](https://img.shields.io/badge/Stars-Give%20us%20a%20star-yellow?style=social&logo=github)](https://github.com/Fireworkshh/Rotating-art-Launcher/stargazers)
  [![Discord](https://img.shields.io/discord/724163890803638273.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/cVkrRdffGp)
  [![Patreon](https://img.shields.io/badge/Patreon-Support%20Us-FF424D?style=for-the-badge&logo=patreon&logoColor=white)](https://www.patreon.com/c/RotatingArtLauncher)
</div>

Rotating Art Launcher is an Android application that allows you to run .NET Core-based games, tModLoader, SMAPI, and more on mobile devices.

## ✨ Features

- **Native .NET Support** - Integrated full .NET 10.0 Runtime, supporting .NET assemblies
- **FNA/XNA Framework Compatibility** - Support for FNA and XNA game frameworks
- **Multiple Renderers** - Support for GL4ES, OSMesa + Zink, Angle, and other rendering solutions
- **Customizable Controls** - Xbox controller mode, virtual joystick controller, mouse + keyboard controls, custom game control mapping
- **Virtual Keyboard** - Built-in virtual keyboard with drag support and transparency adjustment
- **Dynamic Renderer Selection** - Choose between Native OpenGL ES 3, GL4ES, and GL4ES + ANGLE renderers

## 🚀 Getting Started

### Requirements

- Android 7.0 (API level 24) or higher
- ARM64-v8a architecture device
- At least 2GB of free storage space

### Installation

1. Download the latest APK from the [Releases](https://github.com/Fireworkshh/Rotating-art-Launcher/releases) page
2. Enable "Install from Unknown Sources" in your Android settings
3. Install the APK on your device
4. Launch the app and follow the setup wizard

## 🎮 Usage

### Adding Games

1. Open Rotating Art Launcher
2. Tap the "+" button to add a new game
3. Select your game's executable file
4. Configure game settings (renderer, controls, etc.)
5. Launch and enjoy!

### Configuring Controls

- **Virtual Joystick**: Tap and drag to move
- **Virtual Buttons**: Tap to trigger actions
- **Virtual Keyboard**: Toggle via the keyboard button in controls
- **Xbox Controller**: Connect a compatible controller via Bluetooth or USB

### Renderer Selection

Choose the best renderer for your device:

- **Native OpenGL ES 3**: Fastest with GPU acceleration, but may have rendering errors
- **GL4ES**: Most compatible with games, slightly slower frame rate
- **GL4ES + ANGLE**: Translated to Vulkan, best balance of speed and compatibility (recommended for Qualcomm Snapdragon)

## 🛠️ Building from Source

### Prerequisites

- Android Studio Arctic Fox or later
- Android NDK r21e or later
- CMake 3.18 or later
- Git with LFS support

### Build Steps

1. Clone the repository:
```bash
git clone --recursive https://github.com/Fireworkshh/Rotating-art-Launcher.git
cd Rotating-art-Launcher
```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. Build the APK:
```bash
./gradlew assembleDebug
```

The APK will be generated in `app/build/outputs/apk/debug/`

## 📚 Documentation

For detailed documentation, please refer to the [docs](docs/) directory:

- [Code Structure](docs/CODE_STRUCTURE.md)
- [Renderer Usage Guide](docs/RENDERER_USAGE_GUIDE.md)
- [Xbox Controller Architecture](docs/XBOX_CONTROLLER_ARCHITECTURE.md)
- [Virtual Joystick SDL Mode](docs/VIRTUAL_JOYSTICK_SDL_MODE.md)
- [Patch System](docs/PATCH_SYSTEM.md)

## 🤝 Contributing

We welcome contributions! Please feel free to submit Issues and Pull Requests.

### How to Contribute

1. Fork this repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Code Style

- Follow Android Kotlin/Java style guidelines
- Use meaningful variable and function names
- Add comments for complex logic
- Write unit tests for new features when possible

## 🐛 Reporting Issues

If you encounter any bugs or have feature requests, please:

1. Check existing [Issues](https://github.com/Fireworkshh/Rotating-art-Launcher/issues) to avoid duplicates
2. Create a new issue with:
   - Clear description of the problem
   - Steps to reproduce
   - Device information (model, Android version)
   - Logs (if applicable)

## 📄 License

This project is licensed under the **GNU Lesser General Public License v3.0 (LGPLv3)**.

See the [LICENSE](LICENSE) file for details.

### Third-Party Licenses

- **SDL2** - [Zlib License](https://www.libsdl.org/license.php)
- **GL4ES** - [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE)
- **.NET Runtime** - [MIT License](https://github.com/dotnet/runtime/blob/main/LICENSE.TXT)
- **FNA3D** - [Microsoft Public License](https://github.com/FNA-XNA/FNA3D/blob/master/LICENSE)

## 🙏 Acknowledgments

Special thanks to the following open-source projects and communities:

- [SDL Project](https://www.libsdl.org/) - Cross-platform media library
- [GL4ES](https://github.com/ptitSeb/gl4es) - OpenGL compatibility layer
- [.NET Runtime](https://github.com/dotnet/runtime) - .NET runtime
- [FNA](https://github.com/FNA-XNA/FNA) - XNA compatibility framework
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) - Inspiration from Minecraft launcher
- All contributors and users
- Special thanks to all [Patreon supporters](https://www.patreon.com/c/RotatingArtLauncher)!

## 📞 Contact

For questions or suggestions, please:

- 💬 Submit an [Issue](https://github.com/Fireworkshh/Rotating-art-Launcher/issues)
- 🗣️ Visit [Discussions](https://github.com/Fireworkshh/Rotating-art-Launcher/discussions)
- 💝 Support us on [Patreon](https://www.patreon.com/c/RotatingArtLauncher)
- 💬 Join our [Discord](https://discord.gg/cVkrRdffGp)

---

<div align="center">
  
**Made with ❤️ by the Rotating Art Launcher Team**

⭐ If this project helps you, please give it a Star!

</div>

