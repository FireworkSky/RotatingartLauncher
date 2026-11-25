# Third-party notices — Bunny Evolution

## Terraria PE resource converter

The optional Android Terraria PE resource converter under `core/mobile-terraria/` and the Kotlin conversion pipeline under `app/src/bunnyTerraria/` are adapted from:

- **tML Pocket Launcher**
- Copyright and source: https://github.com/FireworkSky/tML-Pocket-Launcher
- License: GNU General Public License v3.0 (GPL-3.0)

The upstream notices and license terms must be preserved in source distributions and derivative builds. Bunny Evolution remains GPL-3.0-compatible and keeps its own application identity and integration code.

The native converter fetches and links these dependencies at build time using pinned versions/hashes in `core/mobile-terraria/CMakeLists.txt`:

- libogg 1.3.6 — Xiph.Org
- libvorbis 1.3.7 — Xiph.Org
- vgmstream — pinned Git revision
- LZ4 1.10.0 — BSD-2-Clause

Terraria and tModLoader game code, artwork, audio, fonts, shaders and other game assets remain the property of their respective rights holders. Bunny does not include or redistribute the user's Terraria APK or desktop game content. The optional converter profile is generated locally from game files the user is entitled to use.
