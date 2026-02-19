# Subcomponent Implementation Details (RotatingartLauncher)

Load this file before editing any subcomponent.

## App Module (`app/src/main/java/com/app/ralaunch`)

### `core/common`

- Responsibility:
  app-wide managers and utilities (theme, permissions, launch/deletion helpers, logging, archive/file helpers, dialogs).
- Key files:
  `ThemeManager.kt`, `PermissionManager.kt`, `GameLaunchManager.kt`, `GameDeletionManager.kt`, `SettingsAccess.kt`, `ErrorHandler.kt`.
- Implementation details:
  contains operational managers used by feature layers; many features call these directly.
- Change coupling:
  changes propagate broadly to `feature/main`, `feature/game`, and settings flows.

### `core/di`

- Responsibility:
  initialize Koin and register app-level dependencies.
- Key files:
  `KoinInitializer.kt`, `AppModule.kt`.
- Implementation details:
  merges `shared` DI modules with app-specific managers; wrong bindings break app startup.
- Change coupling:
  affects construction of services/managers across the entire app process.

### `core/platform/android`

- Responsibility:
  Android platform service/provider integration.
- Key files:
  `ProcessLauncherService.kt`, `provider/RaLaunchDocumentsProvider.kt`, `provider/RaLaunchFileProvider.kt`.
- Implementation details:
  `ProcessLauncherService` launches .NET assemblies in `:launcher`, sets stdin pipe, applies patches, and delegates launch to runtime layer.
- Change coupling:
  service/authority/action changes require manifest updates and inter-process call updates.

### `core/platform/install`

- Responsibility:
  game import/install pipeline using plugin selection.
- Key files:
  `GameInstaller.kt`, plugin files under `install/plugins`, extractor files under `install/extractors`.
- Implementation details:
  detects game/mod loader type, creates storage roots via `GameListStorage`, delegates extraction/install to selected plugin.
- Change coupling:
  impacts import UX, storage layout, and game metadata persistence.

### `core/platform/network/easytier`

- Responsibility:
  EasyTier multiplayer and VPN/TUN orchestration.
- Key files:
  `EasyTierVpnService.kt`, `EasyTierManager.kt`, `EasyTierConfigBuilder.kt`.
- Implementation details:
  `EasyTierVpnService` creates TUN fd, runs as foreground service, broadcasts readiness/errors, and coordinates JNI calls.
- Change coupling:
  touches permissions, process boundaries (`:game`), notification behavior, and JNI interop.

### `core/platform/runtime`

- Responsibility:
  runtime preparation and .NET game launch execution.
- Key files:
  `GameLauncher.kt`, `RuntimeLibraryLoader.kt`, `AssemblyPatcher.kt`, `EnvVarsManager.kt`, `dotnet/DotNetLauncher.kt`, `renderer/RendererLoader.kt`.
- Implementation details:
  `GameLauncher` sets env vars, data dirs, startup hooks, renderer/thread settings, then calls `DotNetLauncher.hostfxrLaunch`.
  `DotNetLauncher` sets `DOTNET_ROOT`, applies CoreCLR config/hooking, loads native libs, and invokes native hostfxr launch bridge.
- Change coupling:
  highest-risk area; can break all game launch paths if env/native ordering changes.

### `core/ui`

- Responsibility:
  app-level UI base classes and shared dialogs.
- Key files:
  `base/BaseActivity.kt`, `base/BasePresenter.kt`, dialog compose files.
- Implementation details:
  shared foundations used by multiple activities.
- Change coupling:
  cross-feature visual or lifecycle behavior impact.

## Feature Module (`app/src/main/java/com/app/ralaunch/feature`)

### `feature/init`

- Responsibility:
  first-run initialization/extraction screen and flow.
- Key files:
  `InitializationActivity.kt`, `InitializationScreen.kt`.
- Implementation details:
  checks extracted flags, requests permissions, extracts component archives and runtime libs, then routes to main activity.
- Change coupling:
  affects first-launch reliability and runtime readiness.

### `feature/main`

- Responsibility:
  launcher home orchestration and state management.
- Key files:
  `MainActivityCompose.kt`, `MainViewModel.kt`, `MainUseCases.kt`, `MainContracts.kt`, screen wrappers under `screens/`.
- Implementation details:
  central state holder for game list, selection, import/deletion/launch events; wraps shared screens and app-specific dialogs/background systems.
- Change coupling:
  high; navigation, import, deletion, launch, and settings entry points converge here.

### `feature/game`

- Responsibility:
  in-game activity integration with SDL runtime.
- Key files:
  `legacy/GameActivity.kt`, `legacy/GamePresenter.kt`, `GameVirtualControlsManager.kt`, input helpers.
- Implementation details:
  `GameActivity` extends `SDLActivity`, handles fullscreen/IME/touch bridges, and terminates process on destroy to avoid multi-init runtime issues.
- Change coupling:
  affects game process lifecycle, controls overlay, and crash reporting behavior.

### `feature/controls`

- Responsibility:
  virtual control data model, runtime overlays, pack management, and editor tooling.
- Key files:
  `ControlData.kt`, `ControlEditorViewModel.kt`, editor UI/manager/helper folders, `packs/ControlPackManager.kt`, view bridge files.
- Implementation details:
  `ControlEditorViewModel` manages control editing state via flows, supports add/update/delete/duplicate, tracks unsaved changes, and persists layout through `ControlPackManager`.
- Change coupling:
  large surface; touches in-game input bridge, editor UI, pack storage, and rendering assets.

### `feature/gog`

- Responsibility:
  GOG auth, catalog display, and download workflow.
- Key files:
  API/auth clients in `data/api`, models in `data/model`, `GogDownloader.kt`, and UI in `ui/`.
- Implementation details:
  screen toggles between embedded web login and logged-in dual-pane catalog.
  downloader supports auth headers, retries, progress, and resume semantics.
- Change coupling:
  affects network/auth behavior, file download correctness, and import entry flow.

### `feature/patch`

- Responsibility:
  patch discovery/install/enable pipeline.
- Key files:
  `data/PatchManager.kt`, `PatchManifest.kt`, `PatchManagerConfig.kt`.
- Implementation details:
  stores patch configs by game assembly path, installs patch archives, and prepares startup-hook data consumed during launch.
- Change coupling:
  tightly coupled with runtime launch (`GameLauncher`) and patch assets under `patches`.

### `feature/crash`

- Responsibility:
  crash report activity/screen.
- Key files:
  `CrashReportActivity.kt`, `CrashReportScreen.kt`.
- Implementation details:
  dedicated UI flow for rendering runtime error/crash diagnostics.
- Change coupling:
  interacts with error handler and game/main activity exception paths.

### `feature/sponsor`

- Responsibility:
  sponsors wall data + UI.
- Key files:
  `SponsorRepository.kt`, `SponsorsActivity.kt`, `SponsorsScreen.kt`, `SponsorWallView.kt`.
- Implementation details:
  isolated presentation feature with low runtime coupling.
- Change coupling:
  mostly local unless routing/manifest changes.

## Shared Common Module (`shared/src/commonMain/kotlin/com/app/ralaunch/shared`)

### `core/contract`

- Responsibility:
  repository/service interfaces used across platforms.
- Key files:
  `contract/repository/*.kt`, `contract/service/*.kt`.
- Implementation details:
  defines boundaries consumed by viewmodels/use cases and implemented by common/android layers.
- Change coupling:
  interface changes require updates in both implementations and consumers.

### `core/data`

- Responsibility:
  common repositories, storage abstractions, and data models.
- Key files:
  `data/repository/GameRepositoryImpl.kt`, `SettingsRepositoryImpl.kt`, `ControlLayoutRepositoryImpl.kt`, `data/local/*.kt`.
- Implementation details:
  repository layer wraps storage adapters and exposes state flows/mutation methods.
- Change coupling:
  affects persistence semantics and feature state behavior.

### `core/model`

- Responsibility:
  domain and UI models.
- Key files:
  `model/domain/*.kt`, `model/ui/*.kt`.
- Implementation details:
  shared types used by app and shared feature UIs/viewmodels.
- Change coupling:
  broad compile-time impact due to model usage breadth.

### `core/navigation`

- Responsibility:
  shared navigation state and route model.
- Key files:
  `AppNavHost.kt`, `NavRoutes.kt`, `NavigationExtensions.kt`.
- Implementation details:
  `NavState` stores current screen and back stack; main app composes this state into screen wrappers.
- Change coupling:
  affects screen transitions and route contracts.

### `core/theme` and `core/config`

- Responsibility:
  shared theming state and config constants/interfaces.
- Key files:
  `theme/AppThemeState.kt`, `theme/Theme.kt`, `config/*.kt`.
- Implementation details:
  central theme/background config states consumed by compose screens.
- Change coupling:
  UI-wide visual/state side effects.

### `core/component`

- Responsibility:
  reusable compose components and dialogs.
- Key files:
  `component/game/*.kt`, `component/dialogs/*.kt`, `AppNavigationRail.kt`.
- Implementation details:
  presentational units used by wrappers and feature screens.
- Change coupling:
  shared UI behavior impact.

### `feature/settings`

- Responsibility:
  shared settings screen/viewmodel/state/effects.
- Key files:
  `SettingsViewModel.kt`, `SettingsScreen.kt`, category files.
- Implementation details:
  event-driven state updates through `SettingsRepositoryV2`, emits effects for host app actions (dialogs, pickers, patch management).
- Change coupling:
  touches repositories plus app-side wrappers/effect handling.

### `feature/filebrowser`

- Responsibility:
  shared file browser UI models and screen.
- Key files:
  `FileBrowserScreen.kt`, `FileBrowserModels.kt`.
- Implementation details:
  reusable file browsing primitives consumed by app wrappers.
- Change coupling:
  mostly UI and data-source integration points.

### `feature/controls`

- Responsibility:
  shared control-layout viewmodel.
- Key files:
  `ControlLayoutViewModel.kt`.
- Implementation details:
  bridges shared contract repository to UI-level control layout state.
- Change coupling:
  links app control features to shared contracts/repository implementations.

## Shared Android Module (`shared/src/androidMain/kotlin/com/app/ralaunch/shared/core`)

### `data/local`

- Responsibility:
  Android storage implementations for shared abstractions.
- Key files:
  `AndroidGameListStorage.kt`, `AndroidControlLayoutStorage.kt`, `StoragePathsProvider.android.kt`.
- Implementation details:
  binds filesystem and DataStore paths to shared repository/storage contracts.
- Change coupling:
  affects persistence location and migration assumptions.

### `data/service`

- Responsibility:
  Android service implementations for shared service contracts.
- Key files:
  `AndroidGameLaunchService.kt`, `AndroidControlLayoutService.kt`, `AndroidPatchService.kt`.
- Implementation details:
  adapter layer from shared service interfaces to app/runtime operations.
- Change coupling:
  service behavior changes bubble into settings/main/control workflows.

### `di`

- Responsibility:
  Android shared-module bindings.
- Key files:
  `AndroidModule.kt`.
- Implementation details:
  registers storage/service implementations and combines shared/common modules.
- Change coupling:
  boot failures or missing dependency injections when altered incorrectly.

## Native and Patch Assets

### `app/src/main/cpp/main` and `app/src/main/cpp/dotnethost`

- Responsibility:
  first-party native bridge and .NET host support.
- Change coupling:
  impacts runtime launch and JNI boundary behavior.

### `app/src/main/cpp/SDL`, `FNA3D`, `gl4es`, `FAudio`

- Responsibility:
  vendored third-party source trees.
- Change coupling:
  avoid direct modifications unless task explicitly requires vendor-level changes.

### `patches`

- Responsibility:
  C# patch projects and manifests bundled as built-in patches.
- Key structure:
  each patch folder includes `.csproj`, `Patcher.cs`, `StartupHook.cs`, and `patch.json`.
- Change coupling:
  patch identifier/schema changes must stay aligned with patch manager logic and runtime startup-hook wiring.

## Mandatory Before-Edit Checklist

1. Confirm target section in this file and list exact touched paths.
2. Run:
   `skills/analyze-subcomponents/scripts/component_profile.sh <target> .`
3. Check DI registration files if dependencies are added/removed.
4. Check manifest for component/process/permission implications.
5. Check shared contract/data model impacts if public types/signatures change.
6. For runtime/patch changes, verify launch-path coupling:
   `feature/main` -> `core/platform/runtime` -> `feature/game` -> `patches`.
