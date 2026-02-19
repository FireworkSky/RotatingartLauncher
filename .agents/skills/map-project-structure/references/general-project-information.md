# General Project Information (RotatingartLauncher)

Load this file before planning or modifying code in this repository.

## Project Identity

- Type: Android launcher for .NET/FNA-style games.
- Primary modules: `:app` and `:shared`.
- Root name: `Rotating-art-Launcher`.
- Main package namespace: `com.app.ralaunch`.
- Shared package namespace: `com.app.ralaunch.shared`.

## Tech and Build Stack

- Android app module with Kotlin + Compose + CMake native integration.
- Kotlin Multiplatform shared module for common contracts, data, navigation, and UI state.
- DI framework: Koin.
- Coroutines + Flow for async/state.
- Native runtime integrations under `app/src/main/cpp`.
- Patch system with C# patch projects under `patches`.

## Build Constraints and Runtime Assumptions

- ABI split: `arm64-v8a` only.
- Java/Kotlin target: JVM 21.
- `app` compile SDK 36, target SDK 35, min SDK 28.
- `.NET` and runtime libraries are partly bundled and partly extracted/loaded at runtime.
- APK packaging excludes several large runtime `.so` files and expects runtime extraction/loading logic to provide them.

## Startup and Runtime Flow

1. Launcher activity from manifest: `.feature.init.InitializationActivity`.
2. App process bootstrap in `RaLaunchApp`:
   - density adapter
   - Koin startup
   - theme setup
   - crash capture init
   - patch extraction/install background work
3. Initialization flow extracts required runtime/game components.
4. Main compose UI hosted by `.feature.main.MainActivityCompose`.
5. Game execution hosted by `.feature.game.legacy.GameActivity` in `:game` process.

## Process Model

- Main app process: launcher UI and most feature logic.
- `:game` process: SDL game runtime + game-specific services.
- `:launcher` process: `ProcessLauncherService` for background assembly launch.
- VPN multiplayer service: `EasyTierVpnService` runs in `:game` process and uses foreground service + TUN.

## Module Responsibilities

- `app`:
  Android-specific app shell, platform services, runtime launchers, feature UIs, native bridge usage.
- `shared`:
  KMP contracts/repositories/models/theme/navigation and shared settings/file-browser/control logic.
- `patches`:
  Patch projects and manifests used by patch installation/activation.

## Directory Landmarks

- `app/src/main/java/com/app/ralaunch/core`:
  infrastructure and platform integration.
- `app/src/main/java/com/app/ralaunch/feature`:
  feature verticals (`init`, `main`, `game`, `controls`, `gog`, `patch`, `crash`, `sponsor`).
- `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core`:
  shared core architecture.
- `shared/src/commonMain/kotlin/com/app/ralaunch/shared/feature`:
  shared feature-level viewmodels/screens.
- `shared/src/androidMain/kotlin/com/app/ralaunch/shared/core`:
  Android implementations for shared contracts.
- `app/src/main/cpp/main`, `app/src/main/cpp/dotnethost`:
  first-party native runtime bridges.
- `app/src/main/cpp/SDL`, `FNA3D`, `gl4es`, `FAudio`:
  large vendored native source trees.

## Dependency Injection Map

- App bootstrap DI:
  `app/src/main/java/com/app/ralaunch/core/di/KoinInitializer.kt`.
- App Android module registrations:
  `app/src/main/java/com/app/ralaunch/core/di/AppModule.kt`.
- Shared/common registrations:
  `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/di/SharedModule.kt`.
- Shared Android bindings:
  `shared/src/androidMain/kotlin/com/app/ralaunch/shared/core/di/AndroidModule.kt`.

## High-Coupling Areas

- `feature/main` orchestration and wrappers.
- `core/platform/runtime` launch/runtime environment.
- `feature/game` + SDL integration.
- `feature/controls` editor/render/input paths.
- `feature/patch` + `patches` synchronization.
- shared contracts/repositories consumed by app-side features.

## Pre-Modification Read Order

1. Confirm module and package boundaries with:
   `skills/map-project-structure/scripts/project_map.sh .`
2. Load architecture notes:
   `skills/map-project-structure/references/rotatingart-architecture.md`
3. For any specific target folder, load:
   `skills/analyze-subcomponents/references/subcomponent-implementation-details.md`
4. Run component profiling:
   `skills/analyze-subcomponents/scripts/component_profile.sh <target> .`
