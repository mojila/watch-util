#!/usr/bin/env bash
#
# Build and install WatchUtil on a connected watch, then start the ADB bridge.
#
# Usage:
#   scripts/install.sh            # build debug, install, start bridge
#   scripts/install.sh --no-bridge
#
set -euo pipefail

cd "$(dirname "$0")/.."

# Prefer adb on PATH, otherwise fall back to the standard SDK location.
ADB="${ADB:-$(command -v adb || true)}"
if [ -z "$ADB" ] && [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB="$HOME/Library/Android/sdk/platform-tools/adb"
fi
if [ -z "$ADB" ]; then
    echo "error: adb not found. Add platform-tools to PATH or set \$ADB." >&2
    exit 1
fi

PKG="com.watchutil"
PORT="${BRIDGE_PORT:-8778}"
START_BRIDGE=1

for arg in "$@"; do
    case "$arg" in
        --no-bridge) START_BRIDGE=0 ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

if ! adb get-state >/dev/null 2>&1; then
    echo "error: no device. Connect the watch with 'adb connect <ip>:5555' or USB." >&2
    exit 1
fi

echo "==> Building debug APK"
./gradlew :app:assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
echo "==> Installing $APK"
"$ADB" install -r -g "$APK"

echo "==> Launching app"
"$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null

if [ "$START_BRIDGE" -eq 1 ]; then
    echo "==> Starting bridge"
    exec "$(dirname "$0")/start-bridge.sh" "$PORT"
fi
