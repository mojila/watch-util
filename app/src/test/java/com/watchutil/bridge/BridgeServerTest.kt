package com.watchutil.bridge

import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BridgeServerTest {

    /** A port the binder is very unlikely to hold. */
    private val port = 18778

    private fun listening(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300) }
        true
    } catch (_: Exception) {
        false
    }

    @Test
    fun `refuses to start without a token`() {
        val done = Executors.newSingleThreadExecutor().submit {
            // Must return promptly instead of blocking in accept().
            BridgeServer.main(arrayOf(port.toString()))
        }
        done.get(5, TimeUnit.SECONDS)
        assertFalse("bridge must not bind without a token", listening())
    }

    @Test
    fun `refuses to start with a blank token`() {
        val done = Executors.newSingleThreadExecutor().submit {
            BridgeServer.main(arrayOf(port.toString(), "   "))
        }
        done.get(5, TimeUnit.SECONDS)
        assertFalse("bridge must not bind with a blank token", listening())
    }
}
