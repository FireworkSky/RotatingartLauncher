<img width="100%" src="https://capsule-render.vercel.app/api?type=waving&color=0:6C3483,50:2874A6,100:1ABC9C&height=220&section=header&text=Rotating%20Art%20Launcher&fontSize=42&fontColor=ffffff&animation=fadeIn&fontAlignY=38&desc=Run%20.NET%20Desktop%20Games%20on%20Android&descSize=18&descAlignY=55&descAlign=50"/>

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
[![Patreon](https://img.shields.io/badge/Patreon-Support_Us-FF424D?style=for-the-badge&logo=patreon&logoColor=white)](https://www.patreon.com/c/RotatingArtLauncher)

<br/>

**Rotating Art Launcher** is an Android application that lets you run .NET-based desktop games on mobile devices.<br/>
Supports FNA/XNA framework games and mod loaders like tModLoader, SMAPI, and Everest.

⚠️Warning⚠️: This is a modified version for Android 7.1+

</div>

---

<details>
<summary><h2>📖 Table of Contents</h2></summary>

- [Supported Games](#-supported-games)
- [Features](#-features)
- [Getting Started](#-getting-started)
- [Building from Source](#-building-from-source)
- [Project Architecture](#-project-architecture)
- [Contributing](#-contributing)
- [License](#-license)
- [Acknowledgments](#-acknowledgments)
- [Contact](#-contact)

</details>

---

## 🎮 Supported Games

<div align="center">

| Game | Mod Loader | Status |
|:----:|:----------:|:------:|
| **Terraria** | tModLoader | ✅ Supported |
| **Stardew Valley** | SMAPI | ✅ Supported |
| **Celeste** | Everest | ✅ Supported |
| Other FNA/XNA .NET Games | — | ✅ Supported |

</div>

## ✨ Features

<table>
<tr>
<td width="50%" valign="top">

### 🧩 .NET Runtime
- Full **.NET 10.0 Runtime** integrated
- Native .NET assembly execution
- **FNA / XNA** game framework support
- Built-in **MonoMod patch system**

</td>
<td width="50%" valign="top">

### 🖥️ Multiple Renderers
| Renderer | Description |
|:--------:|:-----------:|
| Native OpenGL ES 3 | Fastest, direct GPU |
| GL4ES | Best compatibility |
| GL4ES + ANGLE | Vulkan, for Snapdragon |
| MobileGlues | GL 4.6 → GLES 3.2 |

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🎛️ Control System
- **Virtual Joysticks** — Custom touch controls
- **Virtual Buttons** — Irregular shapes
- **Virtual Keyboard** — Drag & transparency
- **Xbox Controller** — Bluetooth / USB
- **Mouse + Keyboard** — Touch mapping
- **Layout Editor** — Visual customization
- **Control Packs** — Shareable layouts

</td>
<td width="50%" valign="top">

### 🌐 More Features
- **GOG Integration** — Download purchased games
- **EasyTier Multiplayer** — P2P VPN networking
- **Patch System** — Auto game compatibility
- **Multi-Process** — Isolated game process
- **Dynamic Loading** — On-demand native libs
- **Compose UI** — Material 3 modern design

</td>
</tr>
</table>

## 🚀 Getting Started

### Requirements

> - 📱 Android 7.1(API 25) or higher
> - 🏗️ ARM64-v8a architecture device
> - 💾 At least 2GB free storage

### Installation

```
1. Download the latest APK from the Releases page
2. Enable "Install from Unknown Sources"
3. Install the APK and launch the app
4. Follow the setup wizard
```

<div align="center">

[![Download](https://img.shields.io/badge/⬇_Download_Latest-28a745?style=for-the-badge)](https://github.com/FireworkSky/RotatingartLauncher/releases)

</div>

### Usage

1. Open Rotating Art Launcher
2. Tap **"+"** to add a game (or download via GOG)
3. Select the game executable
4. Configure renderer and controls
5. Launch and enjoy 🎮

## 🛠️ Building from Source

<details>
<summary><b>Click to expand build guide</b></summary>

### Prerequisites

| Tool | Version |
|:----:|:-------:|
| Android Studio | Latest stable |
| Android NDK | r28 |
| CMake | 3.22.1+ |
| JDK | 21 |
| Git | With LFS support |

### Build Steps

```bash
# Clone the repository
git clone --recursive https://github.com/FireworkSky/RotatingartLauncher.git
cd RotatingartLauncher

# Build the Debug APK
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/`

</details>

## 📁 Project Architecture

<details>
<summary><b>Click to expand directory structure</b></summary>

```
RotatingartLauncher/
├── app/                          # Main Android application module
│   └── src/main/
│       ├── java/.../ralaunch/    # Kotlin/Java source code
│       │   ├── core/             #   Game launch core logic
│       │   ├── dotnet/           #   .NET runtime integration
│       │   ├── renderer/         #   Renderer config & loading
│       │   ├── controls/         #   Virtual control system
│       │   ├── gog/              #   GOG Galaxy integration
│       │   ├── easytier/         #   EasyTier multiplayer service
│       │   ├── patch/            #   Patch management system
│       │   └── ui/               #   Compose UI screens
│       ├── cpp/                  #   Native C/C++ (SDL2, GL4ES ...)
│       └── assets/               #   Runtime resources
├── shared/                       # Kotlin Multiplatform shared module
│   └── src/
│       ├── commonMain/           #   Shared UI, domain, data layer
│       └── androidMain/          #   Android-specific implementations
└── patches/                      # C# game patch files
```

</details>

## 🤝 Contributing

Contributions are welcome! Feel free to submit Issues and Pull Requests.

<details>
<summary><b>Click to expand contributing guide</b></summary>

1. Fork this repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Code Style

- Follow Android Kotlin coding guidelines
- Use meaningful variable and function names
- Add comments for complex logic

### Reporting Issues

1. Check existing [Issues](https://github.com/FireworkSky/RotatingartLauncher/issues) to avoid duplicates
2. Create a new issue with:
   - Clear description of the problem
   - Steps to reproduce
   - Device info (model, Android version)
   - Logs (if applicable)

</details>

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](LICENSE) file for details.

<details>
<summary><b>Third-Party Licenses</b></summary>

| Library | License |
|:-------:|:-------:|
| [SDL2](https://www.libsdl.org/) | [Zlib License](https://www.libsdl.org/license.php) |
| [GL4ES](https://github.com/ptitSeb/gl4es) | [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE) |
| [.NET Runtime](https://github.com/dotnet/runtime) | [MIT License](https://github.com/dotnet/runtime/blob/main/LICENSE.TXT) |
| [FNA3D](https://github.com/FNA-XNA/FNA3D) | [Microsoft Public License](https://github.com/FNA-XNA/FNA3D/blob/master/LICENSE) |

</details>

## 🙏 Acknowledgments

<div align="center">

Special thanks to the following open-source projects and communities

</div>

| Project | Description |
|:-------:|:-----------:|
| [SDL Project](https://www.libsdl.org/) | Cross-platform media library |
| [GL4ES](https://github.com/ptitSeb/gl4es) | OpenGL compatibility layer |
| [.NET Runtime](https://github.com/dotnet/runtime) | .NET runtime |
| [FNA](https://github.com/FNA-XNA/FNA) | XNA compatibility framework |
| [ANGLE](https://chromium.googlesource.com/angle/angle) | OpenGL ES over Vulkan |
| [EasyTier](https://github.com/EasyTier/EasyTier) | P2P networking |
| [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) | Launcher inspiration |

<div align="center">

Thanks to all contributors and [Patreon supporters](https://www.patreon.com/c/RotatingArtLauncher)!

</div>

## 📬 Contact

<div align="center">

[![Issue](https://img.shields.io/badge/Submit_Issue-171515?style=for-the-badge&logo=github&logoColor=white)](https://github.com/FireworkSky/RotatingartLauncher/issues)
[![Discussions](https://img.shields.io/badge/Discussions-171515?style=for-the-badge&logo=github&logoColor=white)](https://github.com/FireworkSky/RotatingartLauncher/discussions)
[![Discord](https://img.shields.io/badge/Join_Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/cVkrRdffGp)
[![Patreon](https://img.shields.io/badge/Support_Us-FF424D?style=for-the-badge&logo=patreon&logoColor=white)](https://www.patreon.com/c/RotatingArtLauncher)

</div>

<img width="100%" src="https://capsule-render.vercel.app/api?type=waving&color=0:6C3483,50:2874A6,100:1ABC9C&height=120&section=footer"/>
