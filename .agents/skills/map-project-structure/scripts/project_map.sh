#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"

if [ ! -d "$ROOT" ]; then
  echo "error: repo root not found: $ROOT" >&2
  exit 1
fi

ROOT="$(cd "$ROOT" && pwd)"
cd "$ROOT"

print_section() {
  printf '== %s ==\n' "$1"
}

SETTINGS_FILE=""
if [ -f settings.gradle ]; then
  SETTINGS_FILE="settings.gradle"
elif [ -f settings.gradle.kts ]; then
  SETTINGS_FILE="settings.gradle.kts"
fi

print_section "Repository"
echo "root: $ROOT"
if [ -n "$SETTINGS_FILE" ]; then
  echo "settings: $SETTINGS_FILE"
else
  echo "settings: not found"
fi
echo

print_section "Top-Level Directories"
TOP_DIRS="$(
  find . -mindepth 1 -maxdepth 1 -type d \
    | sed 's#^\./##' \
    | grep -Ev '^(\.git|\.idea|\.gradle|\.kotlin|build)$' \
    | sort \
    || true
)"
if [ -n "$TOP_DIRS" ]; then
  echo "$TOP_DIRS"
else
  echo "(none)"
fi
echo

print_section "Gradle Modules"
if [ -n "$SETTINGS_FILE" ]; then
  MODULES="$(
    grep -E '^[[:space:]]*include' "$SETTINGS_FILE" \
      | grep -oE "['\"][^'\"]+['\"]" \
      | tr -d "'\"" \
      | sort -u \
      || true
  )"
  if [ -n "$MODULES" ]; then
    echo "$MODULES"
  else
    echo "(no include directives found)"
  fi
else
  echo "(settings file missing)"
fi
echo

print_section "Entry Files"
ENTRY_FILES=(
  "app/src/main/AndroidManifest.xml"
  "app/src/main/java/com/app/ralaunch/RaLaunchApp.kt"
  "app/src/main/java/com/app/ralaunch/core/di/KoinInitializer.kt"
  "app/src/main/java/com/app/ralaunch/core/di/AppModule.kt"
  "app/src/main/java/com/app/ralaunch/feature/init/InitializationActivity.kt"
  "app/src/main/java/com/app/ralaunch/feature/main/MainActivityCompose.kt"
  "shared/src/commonMain/kotlin/com/app/ralaunch/shared/core/di/SharedModule.kt"
  "shared/src/androidMain/kotlin/com/app/ralaunch/shared/core/di/AndroidModule.kt"
)
for path in "${ENTRY_FILES[@]}"; do
  if [ -e "$path" ]; then
    printf 'present  %s\n' "$path"
  else
    printf 'missing  %s\n' "$path"
  fi
done
echo

print_section "Manifest Components"
MANIFEST="app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST" ]; then
  if command -v perl >/dev/null 2>&1; then
    COMPONENTS="$(
      perl -0777 -ne 'while (/<(activity|service|provider)\b[^>]*?\bandroid:name="([^"]+)"/sg) { print "$1 $2\n"; }' "$MANIFEST"
    )"
  else
    COMPONENTS="$(
      awk '
        BEGIN { component = "" }
        /<(activity|service|provider)\b/ {
          if (match($0, /<(activity|service|provider)\b/, m)) {
            component = m[1]
          }
        }
        component != "" && /android:name="/ {
          if (match($0, /android:name="[^"]+"/)) {
            name = substr($0, RSTART + 14, RLENGTH - 15)
            printf "%s %s\n", component, name
            component = ""
          }
        }
        /\/>/ { component = "" }
      ' "$MANIFEST"
    )"
  fi

  if [ -n "$COMPONENTS" ]; then
    echo "$COMPONENTS"
  else
    echo "(no activity/service/provider declarations parsed)"
  fi
else
  echo "(manifest missing)"
fi
echo

print_section "App Java/Kotlin Layout"
APP_ROOT="app/src/main/java/com/app/ralaunch"
if [ -d "$APP_ROOT" ]; then
  TOP_PACKAGES="$(
    find "$APP_ROOT" -mindepth 1 -maxdepth 1 -type d \
      | sed 's#.*/##' \
      | sort \
      || true
  )"
  if [ -n "$TOP_PACKAGES" ]; then
    echo "$TOP_PACKAGES"
  else
    echo "(no subpackages)"
  fi

  if [ -d "$APP_ROOT/feature" ]; then
    echo
    echo "feature/*:"
    find "$APP_ROOT/feature" -mindepth 1 -maxdepth 1 -type d \
      | sed 's#.*/##' \
      | sort \
      || true
  fi
fi
echo

print_section "Shared KMP Layout"
SHARED_COMMON="shared/src/commonMain/kotlin/com/app/ralaunch/shared"
SHARED_ANDROID="shared/src/androidMain/kotlin/com/app/ralaunch/shared"

if [ -d "$SHARED_COMMON" ]; then
  echo "commonMain:"
  find "$SHARED_COMMON" -mindepth 1 -maxdepth 1 -type d \
    | sed 's#.*/##' \
    | sort \
    || true
else
  echo "commonMain: (missing)"
fi

if [ -d "$SHARED_ANDROID" ]; then
  echo
  echo "androidMain:"
  find "$SHARED_ANDROID" -mindepth 1 -maxdepth 1 -type d \
    | sed 's#.*/##' \
    | sort \
    || true
else
  echo
  echo "androidMain: (missing)"
fi
echo

print_section "Native C++ Tree (Top Level)"
CPP_ROOT="app/src/main/cpp"
if [ -d "$CPP_ROOT" ]; then
  NATIVE_DIRS="$(
    find "$CPP_ROOT" -mindepth 1 -maxdepth 1 -type d \
      | sed 's#.*/##' \
      | sort \
      || true
  )"
  if [ -n "$NATIVE_DIRS" ]; then
    echo "$NATIVE_DIRS"
  else
    echo "(no native subdirectories)"
  fi
else
  echo "(native tree missing)"
fi
