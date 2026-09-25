# WatchUtil — project instructions

Wear OS 5 app that shows **free RAM** and **current CPU usage** and can
**enable/disable services** and **reboot** the watch. Built with Kotlin and
Compose for Wear Material 3.

## Build environment

The toolchain is pinned; do not upgrade casually.

| Tool | Version | Path |
| --- | --- | --- |
| JDK | 21 (Temurin) | `~/.local/share/jdk/jdk-21.0.12.1+1/Contents/Home` |
| Gradle | 8.14.3 (wrapper) | `./gradlew` |
| Android Gradle Plugin | 8.13.2 | `build.gradle.kts` |
| Kotlin | 2.2.21 | `build.gradle.kts` |
| compileSdk / targetSdk | 36 | `app/build.gradle.kts` |
| minSdk | 30 | `app/build.gradle.kts` |
| Compose UI | 1.9.0 | `app/build.gradle.kts` |
| Wear Compose | 1.6.1 | `app/build.gradle.kts` |
| Android SDK | `~/Library/Android/sdk` | `local.properties` |

Wear Compose **1.7.0 requires AGP 9.1+ and compileSdk 37**. Stay on 1.6.1 until
the whole toolchain is upgraded together.

```sh
export JAVA_HOME=~/.local/share/jdk/jdk-21.0.12.1+1/Contents/Home
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests
```

## Architecture

```
app/src/main/java/com/watchutil/
  MainActivity.kt              Compose entry point
  MainViewModel.kt             UiState (StateFlow), stats loop, service actions
  bridge/
    BridgeProtocol.kt          JSON line protocol (pure, unit-tested)
    BridgeServer.kt            shell-uid process, launched via ADB app_process
    BridgeClient.kt            loopback client used by the app
  core/
    PrivilegedExecutor.kt      bridge -> root -> none, in that order
    SystemStatsReader.kt       /proc/stat and /proc/meminfo parsing
    PackageParser.kt           pm output parsing (pure, unit-tested)
  ui/
    Theme.kt                   Wear Material 3 color scheme
    WatchUtilApp.kt            screens and components
```

### Privilege model ("Shizuku-lite")

An unprivileged app cannot run `pm disable-user` or `reboot`. The app ships a
tiny **ADB bridge** instead of the full Shizuku stack:

- `scripts/start-bridge.sh` launches `com.watchutil.bridge.BridgeServer` with
  `app_process` over ADB. Because the `shell` user spawns it, it inherits the
  shell's privileges (uid 2000), which is enough for `pm` and `reboot`.
- The process listens on loopback TCP (`8778`) and accepts JSON requests. Each
  request carries an **argv array**, never a shell string, so there is no
  injection surface.
- Requests are authenticated with a per-install token, generated on first run
  and stored in the app's private preferences. The dashboard displays it.
- `PrivilegedExecutor` tries the bridge first, then `su`, then degrades to
  read-only stats.

**The bridge is not persistent.** It dies on reboot and must be restarted with
`scripts/start-bridge.sh`. This is intentional: nothing runs with elevated
privilege unless the user started it.

## Conventions

- Pure logic has no Android imports so it can be unit-tested on the JVM.
- UI state is a single immutable `UiState` in a `StateFlow`.
- Shell commands are always argument arrays. Never build a shell string from
  untrusted input.
- Layouts must work on **round and square** displays. Use `ScreenScaffold` +
  `ScalingLazyColumn`; no corner-anchored content, no circular assumptions.
- Match surrounding code style; add KDoc for public types.

## Agents

- `orchestrator` (primary) — plans and delegates.
- `android-wear-dev` (subagent) — implements app code.
- `qa-tester` (subagent) — verifies and reports pass/fail with evidence.

## Safety

- Do not run `reboot` during automated testing; it is disruptive and requires
  restarting the bridge.
- Do not disable packages that the watch needs to boot or connect (for example
  `com.android.systemui`, Bluetooth, or the companion app) without an explicit
  request and a recovery plan.
