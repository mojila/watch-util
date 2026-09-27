package com.watchutil.core

/**
 * Outcome of a single command, whether it ran through the bridge or root.
 *
 * Lives in `core` because it is the shared domain type for command results;
 * [com.watchutil.bridge.BridgeClient] returns it as well.
 */
data class ExecResult(
    val code: Int,
    val out: String,
    val err: String,
) {
    /** True when the command exited successfully. */
    val ok: Boolean get() = code == 0

    /** [out] and [err] joined for display; [err] is dropped when blank. */
    val combined: String get() = if (err.isBlank()) out else "$out\n$err".trim()
}
