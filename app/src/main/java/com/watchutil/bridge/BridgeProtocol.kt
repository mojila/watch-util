package com.watchutil.bridge

import org.json.JSONObject

/**
 * Wire format shared by [BridgeServer] and [BridgeClient].
 *
 * The protocol is intentionally tiny: one JSON object per line over a loopback
 * TCP socket. Each request carries an argv array (never a shell string), so no
 * quoting or shell-injection surface exists.
 *
 * Request:  {"id": 1, "token": "...", "args": ["pm", "list", "packages"]}
 * Response: {"id": 1, "code": 0, "out": "...", "err": "...", "ok": true}
 */
object BridgeProtocol {
    const val DEFAULT_PORT = 8778

    fun encodeRequest(id: Long, token: String?, args: List<String>): String {
        val json = JSONObject()
        json.put("id", id)
        if (token != null) json.put("token", token)
        json.put("args", org.json.JSONArray(args))
        return json.toString()
    }

    fun decodeRequest(line: String): BridgeRequest? = try {
        val json = JSONObject(line)
        val array = json.optJSONArray("args") ?: return null
        val args = ArrayList<String>(array.length())
        for (i in 0 until array.length()) args.add(array.getString(i))
        BridgeRequest(
            id = json.optLong("id", 0L),
            token = if (json.has("token")) json.optString("token") else null,
            args = args,
        )
    } catch (_: Exception) {
        null
    }

    fun encodeResponse(
        id: Long,
        code: Int,
        out: String,
        err: String,
        ok: Boolean,
    ): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("code", code)
        json.put("out", out)
        json.put("err", err)
        json.put("ok", ok)
        return json.toString()
    }

    fun decodeResponse(line: String): BridgeResponse? = try {
        val json = JSONObject(line)
        BridgeResponse(
            id = json.optLong("id", 0L),
            code = json.optInt("code", -1),
            out = json.optString("out", ""),
            err = json.optString("err", ""),
            ok = json.optBoolean("ok", false),
        )
    } catch (_: Exception) {
        null
    }
}

data class BridgeRequest(val id: Long, val token: String?, val args: List<String>)

data class BridgeResponse(
    val id: Long,
    val code: Int,
    val out: String,
    val err: String,
    val ok: Boolean,
) {
    val combined: String get() = if (err.isBlank()) out else "$out\n$err".trim()
}
