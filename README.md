# WatchUtil

A Wear OS 5 app that shows **free RAM** and **current CPU usage**, and can
**enable/disable services** and **reboot the watch**.

The UI is built with Compose for Wear Material 3 and renders correctly on both
**round and square** watch displays.

## Features

- **Dashboard** — live CPU percentage and RAM used/free/total, refreshed every
  2 seconds while the screen is visible. RAM comes from `/proc/meminfo`. CPU
  comes from `/proc/stat`, which SELinux denies to normal app UIDs on Wear OS 5;
  when the direct read is blocked the raw line is fetched through the bridge
  instead. Polling pauses when the app is backgrounded.
- **Services** — lists system packages from `pm`, showing whether each is
  enabled or disabled. Tap a package to disable it (`pm disable-user`) or
  re-enable it (`pm enable`).
- **Reboot** — restarts the watch, behind a confirmation screen.
- **Round + square layouts** — `ScreenScaffold` + `ScalingLazyColumn` center
  content and inset it for curved edges, so no shape-specific layout is needed.

## Privilege model: the "Shizuku-lite" ADB bridge

An unprivileged app cannot run `pm disable-user` or `reboot`. Rather than pull
in the full Shizuku stack, WatchUtil ships a small ADB bridge:

- `scripts/start-bridge.sh` launches `com.watchutil.bridge.BridgeServer` with
  `app_process` over ADB. Because the `shell` user spawns it, the process
  inherits shell privileges (uid 2000) — enough for `pm` and `reboot`.
- The bridge listens on loopback TCP (port `8778`) and speaks one JSON object
  per line. Every request carries an **argv array**, never a shell string, so
  there is no command-injection surface.
- Requests are authenticated with a per-install token generated on first run.
- `PrivilegedExecutor` tries the bridge first, then root (`su`), then degrades
  to read-only stats.

The bridge is **not persistent**: it dies on reboot. Restart it with
`scripts/start-bridge.sh`. Nothing runs with elevated privilege unless you
started it.

## Quick start

```sh
export JAVA_HOME=~/.local/share/jdk/jdk-21.0.12.1+1/Contents/Home
export ANDROID_HOME=~/Library/Android/sdk

# Build + install + launch + start the bridge
./scripts/install.sh

# Or step by step
./gradlew :app:assembleDebug
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
./scripts/start-bridge.sh
```

Connect the watch first, either over USB or with `adb connect <watch-ip>:5555`
(enable ADB debugging in the watch's developer options).

After a reboot, run `./scripts/start-bridge.sh` again.

## Verifying

```sh
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:assembleDebug        # build
```

## Project layout

```
app/src/main/java/com/watchutil/
  MainActivity.kt              Compose entry point
  MainViewModel.kt             UiState (StateFlow), stats loop, service actions
  bridge/                      ADB bridge protocol, server, and client
  core/                        privileged execution, /proc stats, pm parsing
  ui/                          theme and screens
app/src/test/                  JVM unit tests for pure logic
scripts/                       install and bridge-launch helpers
.opencode/agents/              orchestrator, android-wear-dev, qa-tester
```

See `AGENTS.md` for the pinned toolchain and conventions.

## Safety

Disabling the wrong system package can prevent the watch from booting or
connecting. Avoid disabling `com.android.systemui`, Bluetooth, or the companion
app unless you have a recovery path. Re-enable a package with the same screen
if something breaks.
