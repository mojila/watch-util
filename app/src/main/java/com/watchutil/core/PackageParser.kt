package com.watchutil.core

/** How a service package should be presented and acted on. */
enum class ServiceState { ENABLED, DISABLED, UNKNOWN }

data class ServiceEntry(
    val packageName: String,
    val label: String,
    val state: ServiceState,
)

/**
 * Parser for `pm` output. Kept pure so the QA agent can unit-test it without a
 * device.
 */
object PackageParser {

    /**
     * Parses `pm list packages -s -d --user 0`.
     *
     * Lines look like `package:com.example.thing`. The optional
     * `--user <id>` suffix is stripped when present.
     */
    fun parsePackages(raw: String): List<String> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { line ->
                line.removePrefix("package:")
                    .substringBefore(" ")
                    .substringBefore("=")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()

    /** Parses `pm list packages -s -e --user 0` (enabled) and `-d` (disabled). */
    fun parseState(
        enabledRaw: String,
        disabledRaw: String,
    ): Map<String, ServiceState> {
        val enabled = parsePackages(enabledRaw).associateWith { ServiceState.ENABLED }
        val disabled = parsePackages(disabledRaw).associateWith { ServiceState.DISABLED }
        return enabled + disabled
    }

    /** True when `pm` reported a successful disable/enable. */
    fun isSuccess(output: String): Boolean {
        val lower = output.lowercase()
        return lower.contains("new state") ||
            lower.contains("packagename") ||
            lower.isBlank()
    }
}
