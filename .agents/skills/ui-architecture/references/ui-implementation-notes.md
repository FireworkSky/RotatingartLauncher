# UI Implementation Notes

Concise project snapshot for landscape UI, routing, and MVVM.

## 1) Landscape Baseline

- Launcher activities are locked to landscape in `app/src/main/AndroidManifest.xml`:
  - `InitializationActivity`
  - `MainActivityCompose`
  - `GameActivity`
  - `ControlEditorActivity`
  - `CrashReportActivity`
  - `SponsorsActivity`
- `BaseActivity` also enforces `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` in `app/src/main/java/com/app/ralaunch/core/ui/base/BaseActivity.kt`.
- Compose screens are implemented as landscape-first split panes (rail + content, list + detail).

## 2) Routing Snapshot

- Route model: `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/navigation/NavRoutes.kt`
  - `Screen` sealed class defines route strings and parameterized screens.
  - `NavDestination` enum defines main rail tabs.
- Route state: `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/navigation/AppNavHost.kt`
  - `NavState` stores `currentScreen`, back stack, and navigation direction.
- Route helpers: `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/navigation/NavigationExtensions.kt`
  - typed helpers (`navigateToGames`, `navigateToSettings`, etc.)
  - `handleBackPress()` fallback policy.
- Route rendering:
  - `app/src/main/java/com/app/ralaunch/feature/main/MainApp.kt` (`PageContent`)
  - `app/src/main/java/com/app/ralaunch/feature/main/MainActivityCompose.kt` (slot wiring + wrapper logic).

## 3) MVVM Snapshot

- Main feature contract:
  - `app/src/main/java/com/app/ralaunch/feature/main/contracts/MainContracts.kt`
  - `MainUiState`, `MainUiEvent`, `MainUiEffect`.
- Main orchestration VM:
  - `app/src/main/java/com/app/ralaunch/feature/main/MainViewModel.kt`
  - state via `MutableStateFlow`, effects via `MutableSharedFlow`, event reducer via `onEvent`.
- Shared reusable VM example:
  - `shared/src/commonMain/kotlin/com/app/ralaunch/shared/feature/settings/SettingsViewModel.kt`
  - same state/event/effect pattern in KMP shared layer.
- Android-specific side effects stay in wrapper composables (for example `SettingsScreenWrapper` using activity result APIs and file pickers).

## 4) Practical Placement Rules

- Put reusable UI logic in `shared`.
- Put Android/runtime integration in `app` wrappers.
- Route contract changes happen in shared navigation files first; main page rendering and dependency wiring happen in app layer.
- Prefer existing component families before adding new top-level UI patterns.

## 5) MD3 + Glass Design Baseline

- Theme root:
  - `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/theme/Theme.kt` (`RaLaunchTheme`)
  - dynamic light/dark color scheme generated from seed color.
- Tokens:
  - colors: `MaterialTheme.colorScheme.*` and `RaLaunchTheme.extendedColors`
  - typography: `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/theme/Typography.kt`
  - shape system: `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/theme/Shape.kt`
- Glass primitives:
  - `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/component/GlassComponents.kt`
  - prefer `GlassSurface` / `GlassSurfaceRegular` for blur-backed panels.
- Existing visual language references:
  - `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/component/AppNavigationRail.kt`
  - `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/component/game/GameCard.kt`
  - `shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/component/game/GameDetailPanel.kt`
  - `app/src/main/java/com/app/ralaunch/feature/main/SplashOverlay.kt`

## 6) Good UI Checklist (Landscape Launcher)

- Information architecture:
  - clear primary action and grouped secondary actions.
  - split panes for dense management tasks.
- Visual hierarchy:
  - use surface container levels and typography scale, not random font/alpha changes.
- Interaction states:
  - implement loading, empty, error, pressed, selected, and destructive-confirm states.
- Accessibility and readability:
  - ensure contrast over image/video backgrounds.
  - provide content descriptions and maintain practical touch target size.
- Performance:
  - keep file/network/storage operations out of Composables.
  - use stable item keys in lazy containers and avoid unnecessary recomposition churn.
