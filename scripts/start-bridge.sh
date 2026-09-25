#!/usr/bin/env bash
#
# Start the WatchUtil ADB bridge: a process running as the `shell` user that
# executes privileged commands (pm disable-user, reboot) on behalf of the app.
#
# This is the "Shizuku-lite" mechanism. It must be re-run after every reboot,
# because the process is not persistent.
#
# Usage:
#   scripts/start-bridge.sh [port] [token]
#
# If no token is given, it is read from the app's private storage (debug builds
# are debuggable, so `run-as` works). If that fails you are prompted for it; the
# token is shown on the app's dashboard.
#
set -euo pipefail

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
PORT="${1:-8778}"
TOKEN="${2:-}"

if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "error: no device connected." >&2
    exit 1
fi

APK_PATH="$("$ADB" shell pm path "$PKG" | sed 's/package://' | tr -d '\r' | head -1)"
if [ -z "$APK_PATH" ]; then
    echo "error: $PKG is not installed. Run scripts/install.sh first." >&2
    exit 1
fi

if [ -z "$TOKEN" ]; then
    TOKEN="$("$ADB" shell run-as "$PKG" cat "/data/data/$PKG/shared_prefs/watchutil.xml" 2>/dev/null \
        | grep -o 'bridge_token">[A-Za-z0-9]*' | sed 's/.*>//' | tr -d '\r' || true)"
fi

if [ -z "$TOKEN" ]; then
    echo "Could not read the bridge token automatically."
    echo "Open WatchUtil on the watch and copy the token from the dashboard."
    read -r -p "Bridge token: " TOKEN
fi

if [ -z "$TOKEN" ]; then
    echo "error: a token is required." >&2
    exit 1
fi

# Kill any previous bridge so the port is free.
"$ADB" shell "pkill -f com.watchutil.bridge.BridgeServer" >/dev/null 2>&1 || true

echo "==> Starting bridge on port $PORT"
echo "    APK: $APK_PATH"

# Run the bridge in the background on the device. `app_process` is spawned by
# the shell user (uid 2000), which is the privilege level we need.
"$ADB" shell "CLASSPATH='$APK_PATH' nohup app_process /system/bin com.watchutil.bridge.BridgeServer '$PORT' '$TOKEN' >/data/local/tmp/watchutil-bridge.log 2>&1 &" >/dev/null

sleep 1
ready=0
for _ in $(seq 1 15); do
    if "$ADB" shell "cat /data/local/tmp/watchutil-bridge.log" 2>/dev/null | grep -q "WATCHUTIL_BRIDGE_READY"; then
        ready=1
        break
    fi
    sleep 1
done

if [ "$ready" -eq 1 ]; then
    echo "==> Bridge ready. Open WatchUtil and tap 'No privileges' to refresh."
else
    echo "warning: bridge did not report ready. Log:" >&2
    "$ADB" shell "cat /data/local/tmp/watchutil-bridge.log" >&2 || true
    exit 1
fi
