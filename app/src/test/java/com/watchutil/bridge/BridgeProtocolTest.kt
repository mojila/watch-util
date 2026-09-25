package com.watchutil.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeProtocolTest {

    @Test
    fun `request round trips with args`() {
        val line = BridgeProtocol.encodeRequest(7L, "secret", listOf("pm", "list", "packages"))
        val request = BridgeProtocol.decodeRequest(line)

        assertEquals(7L, request?.id)
        assertEquals("secret", request?.token)
        assertEquals(listOf("pm", "list", "packages"), request?.args)
    }

    @Test
    fun `request without token omits it`() {
        val line = BridgeProtocol.encodeRequest(1L, null, listOf("id"))
        assertTrue(!line.contains("token"))
        assertNull(BridgeProtocol.decodeRequest(line)?.token)
    }

    @Test
    fun `response round trips`() {
        val line = BridgeProtocol.encodeResponse(3L, 0, "out", "err", true)
        val response = BridgeProtocol.decodeResponse(line)

        assertEquals(3L, response?.id)
        assertEquals(0, response?.code)
        assertEquals("out", response?.out)
        assertEquals("err", response?.err)
        assertEquals(true, response?.ok)
    }

    @Test
    fun `malformed input decodes to null`() {
        assertNull(BridgeProtocol.decodeRequest("not json"))
        assertNull(BridgeProtocol.decodeRequest("""{"id":1}"""))
        assertNull(BridgeProtocol.decodeResponse("{"))
    }

    @Test
    fun `args are not shell-split`() {
        // A package name with shell metacharacters must survive as one argument.
        val nasty = "com.a;rm -rf /"
        val line = BridgeProtocol.encodeRequest(1L, "t", listOf("pm", "disable-user", nasty))
        assertEquals(nasty, BridgeProtocol.decodeRequest(line)?.args?.last())
    }
}
