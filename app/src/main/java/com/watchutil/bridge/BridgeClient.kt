package com.watchutil.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/** Outcome of a single remote command. */
data class ExecResult(
    val code: Int,
    val out: String,
    val err: String,
) {
    val ok: Boolean get() = code == 0
    val combined: String get() = if (err.isBlank()) out else "$out\n$err".trim()
}

/**
 * App-side client for [BridgeServer]. Opens a short-lived loopback connection
 * per request; our request volume is tiny and this avoids stale-socket bugs
 * across reboots.
 */
class BridgeClient(
    private val port: Int = BridgeProtocol.DEFAULT_PORT,
    private val token: String? = null,
    private val timeoutMillis: Int = 8_000,
) {
    private val ids = AtomicLong(0)

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }

    suspend fun ping(): Boolean = exec(listOf("id")).ok

    suspend fun exec(args: List<String>): ExecResult = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        // Capture as a local: inside Socket().apply { } an unqualified `port`
        // resolves to Socket.getPort(), which is 0 before connect().
        val targetPort = port
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(InetAddress.getByName(LOOPBACK), targetPort), timeoutMillis)
                soTimeout = timeoutMillis
                tcpNoDelay = true
            }
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            val id = ids.incrementAndGet()
            writer.write(BridgeProtocol.encodeRequest(id, token, args))
            writer.newLine()
            writer.flush()

            val line = reader.readLine()
                ?: return@withContext ExecResult(-1, "", "bridge closed the connection")

            val response = BridgeProtocol.decodeResponse(line)
                ?: return@withContext ExecResult(-1, "", "malformed bridge response")

            if (response.err == "unauthorized") {
                return@withContext ExecResult(-1, "", "bridge token mismatch — restart the bridge")
            }
            ExecResult(response.code, response.out, response.err)
        } catch (e: Exception) {
            android.util.Log.w("WatchUtil", "bridge exec failed: ${e.message}")
            ExecResult(-1, "", e.message ?: "bridge unavailable")
        } finally {
            runCatching { socket?.close() }
        }
    }
}
