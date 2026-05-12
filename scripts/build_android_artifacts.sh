#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILDS_DIR="${ROOT_DIR}/builds"
OLD_DIR="${BUILDS_DIR}/old"
DATE_LABEL="${BUILD_LABEL:-$(date -u +%Y%m%d-%H%M%S)}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-20}"
GRADLE_ARGS=()

usage() {
  cat <<USAGE
Usage: $(basename "$0") [--debug-only|--release-only] [--no-clean] [--attempts N] [--label LABEL]

Build Android Scribble APK artifacts and place the newest files in builds/.
Existing APKs/checksums are moved to builds/old/ with a timestamp label first.

Environment:
  JAVA_HOME        Optional JDK path. JDK 17+ is recommended.
  ANDROID_HOME     Optional Android SDK path.
  ANDROID_SDK_ROOT Optional Android SDK path.
  BUILD_LABEL      Optional label used for archived old artifacts.
  MAX_ATTEMPTS     Optional retry count. Defaults to 20.
USAGE
}

BUILD_DEBUG=true
BUILD_RELEASE=true
RUN_CLEAN=true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug-only)
      BUILD_RELEASE=false
      ;;
    --release-only)
      BUILD_DEBUG=false
      ;;
    --no-clean)
      RUN_CLEAN=false
      ;;
    --attempts)
      shift
      MAX_ATTEMPTS="${1:?missing attempt count}"
      ;;
    --label)
      shift
      DATE_LABEL="${1:?missing label}"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

if ! [[ "$MAX_ATTEMPTS" =~ ^[0-9]+$ ]] || [[ "$MAX_ATTEMPTS" -lt 1 ]]; then
  echo "MAX_ATTEMPTS must be a positive integer." >&2
  exit 2
fi

if [[ "$BUILD_DEBUG" == false && "$BUILD_RELEASE" == false ]]; then
  echo "Nothing to build; choose at least one variant." >&2
  exit 2
fi

if command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD=(gradle)
elif [[ -x "${ROOT_DIR}/gradlew" ]]; then
  GRADLE_CMD=("${ROOT_DIR}/gradlew")
else
  echo "Could not find gradle on PATH or an executable ./gradlew." >&2
  exit 127
fi

if [[ -n "${ANDROID_HOME:-}" && ! -d "${ANDROID_HOME}" ]]; then
  echo "ANDROID_HOME is set but does not exist: ${ANDROID_HOME}" >&2
  exit 2
fi

if [[ -n "${ANDROID_SDK_ROOT:-}" && ! -d "${ANDROID_SDK_ROOT}" ]]; then
  echo "ANDROID_SDK_ROOT is set but does not exist: ${ANDROID_SDK_ROOT}" >&2
  exit 2
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && ! -f "${ROOT_DIR}/local.properties" ]]; then
  for sdk_candidate in "$HOME/Android/Sdk" "$HOME/android-sdk" /opt/android-sdk; do
    if [[ -d "$sdk_candidate/platforms" ]]; then
      export ANDROID_HOME="$sdk_candidate"
      export ANDROID_SDK_ROOT="$sdk_candidate"
      echo "Using detected Android SDK: $sdk_candidate"
      break
    fi
  done
fi

TASKS=()
if [[ "$RUN_CLEAN" == true ]]; then
  TASKS+=(clean)
fi
if [[ "$BUILD_DEBUG" == true ]]; then
  TASKS+=(assembleDebug)
fi
if [[ "$BUILD_RELEASE" == true ]]; then
  TASKS+=(assembleRelease)
fi

mkdir -p "$BUILDS_DIR" "$OLD_DIR"

archive_existing_artifact() {
  local path="$1"
  [[ -e "$path" ]] || return 0
  local base ext stem target
  base="$(basename "$path")"
  ext="${base##*.}"
  if [[ "$base" == "$ext" ]]; then
    stem="$base"
    target="${OLD_DIR}/${stem}-${DATE_LABEL}"
  else
    stem="${base%.*}"
    target="${OLD_DIR}/${stem}-${DATE_LABEL}.${ext}"
  fi
  mv "$path" "$target"
  echo "Archived ${base} -> builds/old/$(basename "$target")"
}

archive_current_outputs() {
  archive_existing_artifact "${BUILDS_DIR}/android-scribble-android13-15-debug.apk"
  archive_existing_artifact "${BUILDS_DIR}/android-scribble-android13-15-debug.apk.sha256"
  archive_existing_artifact "${BUILDS_DIR}/android-scribble-android13-15-release-unsigned.apk"
  archive_existing_artifact "${BUILDS_DIR}/android-scribble-android13-15-release-unsigned.apk.sha256"
}

run_build() {
  local attempt=1
  while [[ "$attempt" -le "$MAX_ATTEMPTS" ]]; do
    echo "==> Build attempt ${attempt}/${MAX_ATTEMPTS}: ${GRADLE_CMD[*]} ${TASKS[*]} ${GRADLE_ARGS[*]}"
    if (cd "$ROOT_DIR" && "${GRADLE_CMD[@]}" "${TASKS[@]}" "${GRADLE_ARGS[@]}"); then
      return 0
    fi

    echo "Build attempt ${attempt} failed." >&2
    if [[ "$attempt" -eq "$MAX_ATTEMPTS" ]]; then
      break
    fi

    echo "Collecting diagnostics before retry..." >&2
    (cd "$ROOT_DIR" && "${GRADLE_CMD[@]}" --version) || true
    find "$ROOT_DIR/app/build/reports" -maxdepth 3 -type f 2>/dev/null | sort | tail -20 || true
    GRADLE_ARGS=(--stacktrace --info)
    attempt=$((attempt + 1))
  done
  return 1
}

copy_outputs() {
  if [[ "$BUILD_DEBUG" == true ]]; then
    local debug_apk="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
    if [[ ! -f "$debug_apk" ]]; then
      echo "Expected debug APK not found: $debug_apk" >&2
      return 1
    fi
    cp "$debug_apk" "${BUILDS_DIR}/android-scribble-android13-15-debug.apk"
    sha256sum "${BUILDS_DIR}/android-scribble-android13-15-debug.apk" > "${BUILDS_DIR}/android-scribble-android13-15-debug.apk.sha256"
  fi

  if [[ "$BUILD_RELEASE" == true ]]; then
    local release_apk="${ROOT_DIR}/app/build/outputs/apk/release/app-release-unsigned.apk"
    if [[ ! -f "$release_apk" ]]; then
      echo "Expected unsigned release APK not found: $release_apk" >&2
      return 1
    fi
    cp "$release_apk" "${BUILDS_DIR}/android-scribble-android13-15-release-unsigned.apk"
    sha256sum "${BUILDS_DIR}/android-scribble-android13-15-release-unsigned.apk" > "${BUILDS_DIR}/android-scribble-android13-15-release-unsigned.apk.sha256"
  fi
}

archive_current_outputs
run_build
copy_outputs

echo "Build artifacts are ready:"
find "$BUILDS_DIR" -maxdepth 1 -type f \( -name '*.apk' -o -name '*.sha256' \) -print | sort
