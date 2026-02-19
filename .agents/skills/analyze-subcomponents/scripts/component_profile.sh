#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "usage: $(basename "$0") <component-path-or-token> [repo-root]" >&2
  exit 1
fi

TARGET_INPUT="$1"
ROOT="${2:-.}"

if [ ! -d "$ROOT" ]; then
  echo "error: repo root not found: $ROOT" >&2
  exit 1
fi

ROOT="$(cd "$ROOT" && pwd)"
cd "$ROOT"

SEARCH_BASES=(
  "app/src/main/java/com/app/ralaunch"
  "shared/src/commonMain/kotlin/com/app/ralaunch/shared"
  "shared/src/androidMain/kotlin/com/app/ralaunch/shared"
  "app/src/main/cpp"
)

resolve_target() {
  local input="$1"
  local -a candidates=()
  local base

  if [ -e "$input" ]; then
    printf '%s\n' "${input#./}"
    return 0
  fi

  for base in "${SEARCH_BASES[@]}"; do
    [ -d "$base" ] || continue
    while IFS= read -r path; do
      [ -n "$path" ] && candidates+=("${path#./}")
    done < <(
      find "$base" \( -type d -o -type f \) -path "*${input}*" | sort || true
    )
  done

  if [ "${#candidates[@]}" -eq 0 ]; then
    echo "error: no component matched '$input'" >&2
    exit 1
  fi

  mapfile -t candidates < <(printf '%s\n' "${candidates[@]}" | awk '!seen[$0]++')

  if [ "${#candidates[@]}" -gt 1 ]; then
    echo "error: ambiguous component '$input'; refine the selector" >&2
    printf '  %s\n' "${candidates[@]}" >&2
    exit 2
  fi

  printf '%s\n' "${candidates[0]}"
}

classify_target() {
  case "$1" in
    app/src/main/java/com/app/ralaunch/feature/*) echo "android-feature" ;;
    app/src/main/java/com/app/ralaunch/core/*) echo "android-core" ;;
    shared/src/commonMain/*) echo "shared-common" ;;
    shared/src/androidMain/*) echo "shared-android" ;;
    app/src/main/cpp/*) echo "native-cpp" ;;
    *) echo "other" ;;
  esac
}

TARGET="$(resolve_target "$TARGET_INPUT")"
if [ ! -e "$TARGET" ]; then
  echo "error: resolved target does not exist: $TARGET" >&2
  exit 1
fi

TARGET_TYPE="file"
if [ -d "$TARGET" ]; then
  TARGET_TYPE="directory"
fi

mapfile -t SCOPE_FILES < <(
  if [ "$TARGET_TYPE" = "directory" ]; then
    find "$TARGET" -type f \
      \( -name '*.kt' -o -name '*.java' -o -name '*.xml' -o -name '*.kts' -o -name '*.gradle' -o -name '*.c' -o -name '*.cpp' -o -name '*.h' -o -name '*.hpp' \) \
      | sort
  else
    printf '%s\n' "$TARGET"
  fi
)

mapfile -t SOURCE_FILES < <(
  printf '%s\n' "${SCOPE_FILES[@]}" | grep -E '\.(kt|java)$' || true
)

printf '== Target ==\n'
echo "input: $TARGET_INPUT"
echo "resolved: $TARGET"
echo "type: $TARGET_TYPE"
echo "classification: $(classify_target "$TARGET")"
echo

printf '== Scope Summary ==\n'
echo "file_count: ${#SCOPE_FILES[@]}"
if [ "$TARGET_TYPE" = "directory" ]; then
  echo "direct_subdirs:"
  SUBDIRS="$(
    find "$TARGET" -mindepth 1 -maxdepth 1 -type d \
      | sed "s#^$TARGET/##" \
      | sort \
      || true
  )"
  if [ -n "$SUBDIRS" ]; then
    echo "$SUBDIRS"
  else
    echo "(none)"
  fi
fi
echo

printf '== File Types ==\n'
if [ "${#SCOPE_FILES[@]}" -gt 0 ]; then
  printf '%s\n' "${SCOPE_FILES[@]}" \
    | awk -F. '
      {
        ext = $NF
        if ($0 !~ /\./) ext = "[no-ext]"
        counts[ext]++
      }
      END {
        for (e in counts) {
          printf "%d %s\n", counts[e], e
        }
      }
    ' \
    | sort -nr
else
  echo "(no files)"
fi
echo

printf '== Key Declarations ==\n'
DECL_PATTERN='(class|interface|object)[[:space:]]+[A-Za-z_][A-Za-z0-9_]*'
if [ "${#SOURCE_FILES[@]}" -gt 0 ]; then
  rg -n --no-heading "$DECL_PATTERN" "${SOURCE_FILES[@]}" | head -n 40 || true
else
  echo "(no Kotlin/Java files in scope)"
fi
echo

printf '== Import Surface ==\n'
if [ "${#SOURCE_FILES[@]}" -gt 0 ]; then
  IMPORTS="$(
    rg --no-heading --no-filename '^import[[:space:]]+[^[:space:]]+' "${SOURCE_FILES[@]}" \
      | sed -E 's/^import[[:space:]]+//' \
      | sort -u \
      || true
  )"
  if [ -n "$IMPORTS" ]; then
    echo "internal_imports:"
    printf '%s\n' "$IMPORTS" | grep '^com\.app\.ralaunch' | head -n 30 || true
    echo
    echo "external_imports:"
    printf '%s\n' "$IMPORTS" | grep -v '^com\.app\.ralaunch' | head -n 30 || true
  else
    echo "(no import lines found)"
  fi
else
  echo "(no Kotlin/Java files in scope)"
fi
echo

printf '== Outward References ==\n'
if [ "${#SOURCE_FILES[@]}" -gt 0 ]; then
  mapfile -t SYMBOLS < <(
    rg --no-heading --no-filename "$DECL_PATTERN" "${SOURCE_FILES[@]}" \
      | sed -E 's/.*(class|interface|object)[[:space:]]+([A-Za-z_][A-Za-z0-9_]*).*/\2/' \
      | sort -u \
      | head -n 8
  )

  if [ "${#SYMBOLS[@]}" -eq 0 ]; then
    echo "(no symbols extracted)"
  else
    SEARCH_INPUTS=()
    [ -d app/src/main/java ] && SEARCH_INPUTS+=("app/src/main/java")
    [ -d shared/src ] && SEARCH_INPUTS+=("shared/src")

    if [ "${#SEARCH_INPUTS[@]}" -eq 0 ]; then
      echo "(no search roots found)"
    else
      for symbol in "${SYMBOLS[@]}"; do
        if [ "$TARGET_TYPE" = "directory" ]; then
          ref_count="$(
            rg -n --glob '!**/build/**' "\\b${symbol}\\b" "${SEARCH_INPUTS[@]}" 2>/dev/null \
              | awk -F: -v p="$TARGET/" 'index($1, p) != 1 { c++ } END { print c + 0 }'
          )"
        else
          ref_count="$(
            rg -n --glob '!**/build/**' "\\b${symbol}\\b" "${SEARCH_INPUTS[@]}" 2>/dev/null \
              | awk -F: -v p="$TARGET" '$1 != p { c++ } END { print c + 0 }'
          )"
        fi
        printf '%s %s\n' "$symbol" "$ref_count"
      done
    fi
  fi
else
  echo "(no Kotlin/Java files in scope)"
fi
