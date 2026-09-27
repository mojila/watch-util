package com.watchutil.core

import com.watchutil.bridge.BridgeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

enum class Backend { SHIZUKU_LITE, ROOT, NONE }

/**
 * The single place privileged commands are executed.
 *
 * Order of preference:
 *  1. Shizuku-lite bridge — commands run as the `shell` user, which is the same
 *     privilege level ADB grants and enough for `pm disable-user` and `reboot`.
 *  2. Root (`su -c`) — used automatically when the watch is rooted.
 *  3. Nothing — the app stays useful for reading stats but cannot mutate state.
 *
 * Commands are always passed as argument arrays so the shell is never asked to
 * re-parse untrusted text.
 */
class PrivilegedExecutor(
    private val bridge: BridgeClient,
) {
    @Volatile
    var backend: Backend = Backend.NONE
        private set

    suspend fun refreshBackend(): Backend {
        backend = when {
            bridge.ping() -> Backend.SHIZUKU_LITE
            hasRoot() -> Backend.ROOT
            else -> Backend.NONE
        }
        return backend
    }

    private suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext false
            }
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.exitValue() == 0 && out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    suspend fun exec(vararg args: String): ExecResult = exec(args.toList())

    suspend fun exec(args: List<String>): ExecResult {
        if (args.isEmpty()) return ExecResult(0, "", "")

        return when (backend) {
            Backend.SHIZUKU_LITE -> {
                // The bridge reports the command outcome directly; only a dead
                // bridge (not a command failure) should degrade the backend.
                val result = bridge.exec(args)
                if (result.code == -1 && result.err.contains("bridge", ignoreCase = true)) {
                    backend = Backend.NONE
                }
                result
            }

            Backend.ROOT -> withContext(Dispatchers.IO) {
                runCatching {
                    // Quote each argument for the root shell. Arguments are
                    // package names and fixed flags, never free-form user text.
                    val quoted = args.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
                    val process = ProcessBuilder("su", "-c", quoted).start()
                    val out = process.inputStream.bufferedReader().readText().trim()
                    val err = process.errorStream.bufferedReader().readText().trim()
                    process.waitFor()
                    ExecResult(process.exitValue(), out, err)
                }.getOrElse { ExecResult(-1, "", it.message ?: "root failed") }
            }

            Backend.NONE -> ExecResult(
                -1,
                "",
                "No privileged backend. Start the ADB bridge or root the watch.",
            )
        }
    }
}
