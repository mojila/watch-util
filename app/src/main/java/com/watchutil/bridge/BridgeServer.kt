package com.watchutil.bridge

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * WatchUtil's ADB bridge: a deliberately small stand-in for Shizuku.
 *
 * It is not launched by the app (an unprivileged app cannot spawn a process
 * that runs as `shell`). Instead the user starts it once over ADB:
 *
 *   adb shell CLASSPATH=/data/app/.../base.apk app_process /system/bin \
 *       com.watchutil.bridge.BridgeServer 8778 <token>
 *
 * Because `app_process` is spawned by the `shell` user (uid 2000), the process
 * inherits the shell's privileges, which is exactly what `pm disable-user` and
 * `reboot` require. The app then talks to it over loopback TCP.
 *
 * The process is intentionally ephemeral: it dies on reboot, and that is fine.
 * Re-running the ADB command after a reboot restores it.
 */
object BridgeServer {
    private const val MAX_LINE = 1 shl 16
    private const val COMMAND_TIMEOUT_SECONDS = 20L
    private const val LOOPBACK = "127.0.0.1"
    private val pool = Executors.newCachedThreadPool { r ->
        Thread(r, "bridge-client").apply { isDaemon = true }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val port = args.getOrNull(0)?.toIntOrNull() ?: BridgeProtocol.DEFAULT_PORT
        val token = args.getOrNull(1)?.takeIf { it.isNotBlank() }

        val server = try {
            // Bind explicitly to IPv4 loopback. InetAddress.getLoopbackAddress()
            // can resolve to IPv6 (::1) inside app_process, which the app's
            // IPv4 client would then fail to reach.
            ServerSocket(port, 8, InetAddress.getByName(LOOPBACK))
        } catch (e: Exception) {
            System.err.println("WatchUtil bridge: cannot bind port $port: ${e.message}")
            return
        }

        // The app watches stdout for this line when it launches the bridge
        // itself; when started manually it is simply a human-readable marker.
        println("WATCHUTIL_BRIDGE_READY ${server.localPort}")
        System.out.flush()

        while (!Thread.currentThread().isInterrupted) {
            val socket = try {
                server.accept()
            } catch (_: Exception) {
                break
            }
            pool.execute { handle(socket, token) }
        }
    }

    private fun handle(socket: Socket, expectedToken: String?) {
        socket.use { s ->
            s.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))

            while (true) {
                val line = try {
                    reader.readLine()
                } catch (_: Exception) {
                    null
                } ?: break

                if (line.isBlank()) continue
                if (line.length > MAX_LINE) break

                val request = BridgeProtocol.decodeRequest(line) ?: run {
                    writeLine(writer, BridgeProtocol.encodeResponse(0, -1, "", "bad request", false))
                    continue
                }

                if (expectedToken != null && request.token != expectedToken) {
                    writeLine(
                        writer,
                        BridgeProtocol.encodeResponse(request.id, -1, "", "unauthorized", false),
                    )
                    continue
                }

                if (request.args.isEmpty()) {
                    writeLine(writer, BridgeProtocol.encodeResponse(request.id, 0, "", "", true))
                    continue
                }

                val result = runCommand(request.args)
                writeLine(
                    writer,
                    BridgeProtocol.encodeResponse(
                        request.id,
                        result.code,
                        result.out,
                        result.err,
                        result.code == 0,
                    ),
                )
            }
        }
    }

    private fun writeLine(writer: BufferedWriter, line: String) {
        try {
            writer.write(line)
            writer.newLine()
            writer.flush()
        } catch (_: Exception) {
            // Peer went away; the read loop will terminate on its own.
        }
    }

    private data class CommandResult(val code: Int, val out: String, val err: String)

    private fun runCommand(args: List<String>): CommandResult {
        return try {
            val process = ProcessBuilder(args).start()
            process.outputStream.close()

            val out = StringBuilder()
            val err = StringBuilder()
            val outReader = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.inputStream)).forEachLine {
                        out.append(it).append('\n')
                    }
                }
            }
            val errReader = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.errorStream)).forEachLine {
                        err.append(it).append('\n')
                    }
                }
            }
            outReader.start()
            errReader.start()

            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CommandResult(-1, "", "command timed out: ${args.first()}")
            }
            outReader.join(1_000)
            errReader.join(1_000)
            CommandResult(process.exitValue(), out.toString().trim(), err.toString().trim())
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "failed to run ${args.first()}")
        }
    }
}
