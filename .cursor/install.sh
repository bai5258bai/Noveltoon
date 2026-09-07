#!/usr/bin/env bash
# Idempotent Cloud Agent bootstrap for the NovelToon Android app.
# Installs the Android SDK components the build needs (if missing), points
# Gradle at them, and warms the Gradle cache by assembling the debug APK.
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"

CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
PLATFORM="platforms;android-36"
BUILD_TOOLS="build-tools;36.0.0"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

ensure_sdk_dir() {
  if [ ! -d "$ANDROID_SDK_ROOT" ]; then
    if mkdir -p "$ANDROID_SDK_ROOT" 2>/dev/null; then :; else
      sudo mkdir -p "$ANDROID_SDK_ROOT"
      sudo chown -R "$(id -u):$(id -g)" "$ANDROID_SDK_ROOT"
    fi
  fi
  if [ ! -w "$ANDROID_SDK_ROOT" ]; then
    sudo chown -R "$(id -u):$(id -g)" "$ANDROID_SDK_ROOT"
  fi
}

install_cmdline_tools() {
  local sdkmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  if [ -x "$sdkmanager" ]; then
    return
  fi
  echo "Installing Android command-line tools..."
  local tmp
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/cmdline-tools.zip" "$CMDLINE_TOOLS_URL"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  unzip -q "$tmp/cmdline-tools.zip" -d "$ANDROID_SDK_ROOT/cmdline-tools"
  # The archive extracts to cmdline-tools/cmdline-tools; move it to .../latest.
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
}

install_sdk_packages() {
  local sdkmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  yes | "$sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true
  "$sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" \
    "platform-tools" "$PLATFORM" "$BUILD_TOOLS" >/dev/null
}

ensure_sdk_dir
install_cmdline_tools
install_sdk_packages

# Gradle reads sdk.dir from local.properties (git-ignored, per-machine).
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties

echo "Warming Gradle cache and building the debug APK..."
./gradlew --no-daemon assembleDebug

echo "NovelToon environment ready. Debug APK:"
ls -1 app/build/outputs/apk/debug/app-debug.apk
