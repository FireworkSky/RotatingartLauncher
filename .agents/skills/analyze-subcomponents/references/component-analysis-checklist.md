# RotatingartLauncher Subcomponent Analysis Checklist

Use this reference after running `scripts/component_profile.sh`.

## Component Classes in This Repo

- `app/src/main/java/com/app/ralaunch/feature/*`:
  feature verticals (UI flows, feature-specific domain logic, user interactions).
- `app/src/main/java/com/app/ralaunch/core/*`:
  platform/runtime infrastructure, shared managers, process/service integrations.
- `shared/src/commonMain/kotlin/com/app/ralaunch/shared/*`:
  KMP-shared contracts, models, repositories, navigation, theming, shared screens.
- `shared/src/androidMain/kotlin/com/app/ralaunch/shared/*`:
  Android implementations for shared interfaces/services.
- `app/src/main/cpp/*`:
  native bridge code plus vendored third-party source trees.

## Coupling Checkpoints

1. DI registration:
   - app-level: `app/src/main/java/com/app/ralaunch/core/di/AppModule.kt`
   - shared/common: `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/di/SharedModule.kt`
   - shared/android: `shared/src/androidMain/kotlin/com/app/ralaunch/shared/core/di/AndroidModule.kt`
2. App entry/runtime path:
   - `app/src/main/java/com/app/ralaunch/RaLaunchApp.kt`
   - `app/src/main/AndroidManifest.xml`
3. Navigation and screen composition:
   - `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/navigation`
   - `app/src/main/java/com/app/ralaunch/feature/main/screens`
4. Storage and persistence contracts:
   - `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/contract`
   - `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/data`

## Change-Impact Questions

- Which symbols in this component are referenced outside its own folder?
- Does this component expose interfaces consumed across module boundaries?
- Does this component participate in process boundaries (`:game`, `:launcher`)?
- Does this component require manifest updates (activity/service/provider)?
- Does this component depend on native/JNI or external SDK APIs?
- Does this component alter persisted data formats or preference keys?

## High-Risk Areas

- Runtime/bootstrap:
  `app/src/main/java/com/app/ralaunch/core/platform/runtime`
- Main navigation orchestration:
  `app/src/main/java/com/app/ralaunch/feature/main`
- Virtual controls stack:
  `app/src/main/java/com/app/ralaunch/feature/controls`
  and
  `shared/src/commonMain/kotlin/com/app/ralaunch/shared/feature/controls`
- Patch flow:
  `app/src/main/java/com/app/ralaunch/feature/patch`
  and
  `patches`
- Game launch path:
  `app/src/main/java/com/app/ralaunch/feature/game`
  plus runtime loader classes under `core/platform/runtime`

## Reporting Format

Produce component findings in this order:

1. Responsibility summary (1-2 lines).
2. Key files and declarations.
3. Dependency profile (internal vs external imports).
4. Outward references and likely blast radius.
5. Required companion updates (DI, navigation, manifest, storage, tests).
