package com.watchutil

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watchutil.bridge.BridgeClient
import com.watchutil.core.Backend
import com.watchutil.core.CacheCleaner
import com.watchutil.core.PackageParser
import com.watchutil.core.PrivilegedExecutor
import com.watchutil.core.ServiceEntry
import com.watchutil.core.ServiceState
import com.watchutil.core.SystemStats
import com.watchutil.core.SystemStatsReader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen { DASHBOARD, SERVICES, CONFIRM_REBOOT }

data class UiState(
    val screen: Screen = Screen.DASHBOARD,
    val stats: SystemStats = SystemStats.EMPTY,
    val backend: Backend = Backend.NONE,
    val services: List<ServiceEntry> = emptyList(),
    val servicesLoading: Boolean = false,
    val clearingCaches: Boolean = false,
    val busyPackage: String? = null,
    val message: String? = null,
    val bridgeToken: String = "",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val packageManager: PackageManager = application.packageManager
    private val bridgeToken: String = loadOrCreateToken(application)
    private val bridge = BridgeClient(token = bridgeToken)
    private val executor = PrivilegedExecutor(bridge)
    private val statsReader = SystemStatsReader()

    private val _state = MutableStateFlow(UiState(bridgeToken = bridgeToken))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var statsJob: Job? = null

    init {
        statsReader.prime()
        startStatsLoop()
        viewModelScope.launch { refreshBackend() }
    }

    // ---------------------------------------------------------------- stats

    /** Starts polling while the UI is visible; stops it when backgrounded. */
    fun setVisible(visible: Boolean) {
        if (visible) startStatsLoop() else stopStatsLoop()
    }

    private fun startStatsLoop() {
        if (statsJob?.isActive == true) return
        statsJob = viewModelScope.launch {
            while (isActive) {
                val stats = sampleStats()
                _state.update { it.copy(stats = stats) }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopStatsLoop() {
        statsJob?.cancel()
        statsJob = null
    }

    /**
     * Reads resource usage. RAM is always direct. CPU prefers a direct
     * /proc/stat read; when SELinux denies it (the common case on Wear OS 5),
     * the raw line is fetched through the privileged bridge instead.
     */
    private suspend fun sampleStats(): SystemStats {
        if (statsReader.isDirectCpuReadable()) {
            return statsReader.read()
        }

        val external = if (executor.backend != Backend.NONE) {
            val result = executor.exec("cat", "/proc/stat")
            if (result.ok) result.out.lineSequence().firstOrNull() else null
        } else {
            null
        }
        return statsReader.read(external)
    }

    // -------------------------------------------------------------- backend

    fun refreshBackend() {
        viewModelScope.launch {
            val backend = executor.refreshBackend()
            _state.update { it.copy(backend = backend) }
            if (backend != Backend.NONE && _state.value.services.isEmpty()) {
                loadServices()
            }
        }
    }

    // ------------------------------------------------------------- services

    fun loadServices() {
        if (_state.value.servicesLoading) return
        _state.update { it.copy(servicesLoading = true) }
        viewModelScope.launch {
            val enabled = executor.exec("pm", "list", "packages", "-s", "-e", "--user", "0")
            val disabled = executor.exec("pm", "list", "packages", "-s", "-d", "--user", "0")

            if (!enabled.ok && !disabled.ok) {
                _state.update {
                    it.copy(
                        servicesLoading = false,
                        message = enabled.err.ifBlank { "Could not list packages" },
                    )
                }
                return@launch
            }

            val states = PackageParser.parseState(enabled.out, disabled.out)
            val entries = states.map { (pkg, state) ->
                ServiceEntry(
                    packageName = pkg,
                    label = resolveLabel(pkg),
                    state = state,
                )
            }.sortedWith(compareBy({ it.state != ServiceState.DISABLED }, { it.label.lowercase() }))

            _state.update { it.copy(services = entries, servicesLoading = false) }
        }
    }

    private fun resolveLabel(pkg: String): String = try {
        val info: ApplicationInfo = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    fun toggleService(entry: ServiceEntry) {
        if (_state.value.busyPackage != null) return
        val target = if (entry.state == ServiceState.DISABLED) "enable" else "disable-user"
        _state.update { it.copy(busyPackage = entry.packageName) }

        viewModelScope.launch {
            val result = executor.exec(
                "pm", target, "--user", "0", entry.packageName,
            )
            val success = result.ok || PackageParser.isSuccess(result.combined)

            if (success) {
                val newState =
                    if (target == "enable") ServiceState.ENABLED else ServiceState.DISABLED
                _state.update { current ->
                    current.copy(
                        busyPackage = null,
                        services = current.services.map {
                            if (it.packageName == entry.packageName) it.copy(state = newState) else it
                        },
                        message = "${entry.label}: ${
                            if (newState == ServiceState.DISABLED) "disabled" else "enabled"
                        }",
                    )
                }
            } else {
                _state.update {
                    it.copy(busyPackage = null, message = "Failed: ${result.combined.ifBlank { "unknown error" }}")
                }
            }
        }
    }

    // ---------------------------------------------------------------- caches

    /**
     * Trims every app cache via `pm trim-caches`. Non-destructive: only cache
     * files are deleted, never package data, logins or settings.
     *
     * The requested free-space target is derived from `df -k /data` so it is
     * strictly larger than the volume, which makes every cache eligible. When
     * `df` cannot be parsed, a fixed 1 TiB fallback is used.
     */
    fun clearCaches() {
        if (_state.value.clearingCaches) return
        _state.update { it.copy(clearingCaches = true, message = null) }

        viewModelScope.launch {
            if (executor.backend == Backend.NONE) {
                _state.update {
                    it.copy(
                        clearingCaches = false,
                        message = "No privileges. Start the ADB bridge first.",
                    )
                }
                return@launch
            }

            val before = executor.exec("df", "-k", "/data")
            val spaceBefore = CacheCleaner.parseDf(before.out)
            val args = if (spaceBefore != null) {
                CacheCleaner.trimAllArgs(spaceBefore)
            } else {
                CacheCleaner.trimAllArgs()
            }

            val result = executor.exec(args)
            if (!result.ok) {
                _state.update {
                    it.copy(
                        clearingCaches = false,
                        message = "Clear caches failed: ${
                            result.combined.ifBlank { "unknown error" }
                        }",
                    )
                }
                return@launch
            }

            val after = executor.exec("df", "-k", "/data")
            val spaceAfter = CacheCleaner.parseDf(after.out)
            val freed = if (spaceBefore != null && spaceAfter != null) {
                (spaceAfter.freeBytes - spaceBefore.freeBytes).coerceAtLeast(0L)
            } else {
                0L
            }

            _state.update {
                it.copy(
                    clearingCaches = false,
                    message = "Caches cleared · ${CacheCleaner.formatBytes(freed)} freed",
                )
            }
        }
    }

    // ------------------------------------------------------------ navigation

    fun navigate(screen: Screen) {
        _state.update { it.copy(screen = screen, message = null) }
    }

    // --------------------------------------------------------------- reboot

    fun reboot() {
        viewModelScope.launch {
            val result = executor.exec("reboot")
            if (!result.ok) {
                _state.update {
                    it.copy(message = "Reboot failed: ${result.combined.ifBlank { "no permission" }}")
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        statsJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val PREFS = "watchutil"
        const val KEY_TOKEN = "bridge_token"

        /**
         * The bridge requires a shared secret so other apps on the watch cannot
         * drive privileged commands through it. Generated once and persisted.
         */
        fun loadOrCreateToken(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(KEY_TOKEN, null)?.let { return it }
            val token = java.util.UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_TOKEN, token).apply()
            return token
        }
    }
}
