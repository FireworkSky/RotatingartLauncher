# RotatingartLauncher Architecture Notes

Use this reference after running `scripts/project_map.sh`.

## Module Graph

- `settings.gradle` includes `:app` and `:shared`.
- `app` is the Android application module (`com.app.ralaunch` namespace).
- `shared` is the Kotlin Multiplatform shared module (`com.app.ralaunch.shared` namespace).

## Startup Chain

1. `app/src/main/AndroidManifest.xml` declares `.feature.init.InitializationActivity` as launcher entry.
2. `app/src/main/java/com/app/ralaunch/RaLaunchApp.kt` initializes:
   - density adapter
   - Koin DI
   - theme defaults
   - crash logging
   - patch extraction/install
3. `app/src/main/java/com/app/ralaunch/feature/main/MainActivityCompose.kt` hosts the main Compose experience.

## Dependency Injection Boundaries

- `app/src/main/java/com/app/ralaunch/core/di/KoinInitializer.kt` starts Koin.
- `app/src/main/java/com/app/ralaunch/core/di/AppModule.kt` registers Android app-specific managers.
- `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/di/SharedModule.kt` registers shared repositories/viewmodels.
- `shared/src/androidMain/kotlin/com/app/ralaunch/shared/core/di/AndroidModule.kt` binds Android implementations for shared contracts.

## High-Level Code Zones

- `app/src/main/java/com/app/ralaunch/core`:
  platform/runtime internals, process/service bridges, utility managers.
- `app/src/main/java/com/app/ralaunch/feature`:
  product feature verticals (`main`, `game`, `controls`, `gog`, `patch`, `init`, `crash`, `sponsor`).
- `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core`:
  shared contracts, repositories, models, navigation, theme/config.
- `shared/src/commonMain/kotlin/com/app/ralaunch/shared/feature`:
  shared UI logic and viewmodels (`settings`, `filebrowser`, `controls`).
- `shared/src/androidMain/kotlin/com/app/ralaunch/shared/core`:
  Android adapters for shared storage/services.
- `patches`:
  patch assets used by patch-management logic.

## Native and Vendored Surface

- `app/src/main/cpp/main` and `app/src/main/cpp/dotnethost` are first-party runtime/native integration layers.
- `app/src/main/cpp/SDL`, `app/src/main/cpp/FNA3D`, `app/src/main/cpp/gl4es`, `app/src/main/cpp/FAudio` are large external trees.
- Default architecture summaries should separate these vendored trees from first-party code.

## Practical Mapping Heuristics

- Prefer on-disk structure over README tree snippets if they differ.
- Ignore generated directories (`build/`, `.gradle/`, `.kotlin/`) during architecture analysis.
- Confirm process boundaries from manifest declarations (`:game`, `:launcher`) before describing runtime flows.
- For change placement questions, identify the module boundary first, then the package boundary.

## Fast Commands

```bash
# Snapshot architecture
skills/map-project-structure/scripts/project_map.sh .

# List top-level feature folders
find app/src/main/java/com/app/ralaunch/feature -mindepth 1 -maxdepth 1 -type d | sort

# List shared feature folders
find shared/src/commonMain/kotlin/com/app/ralaunch/shared/feature -mindepth 1 -maxdepth 1 -type d | sort
```
