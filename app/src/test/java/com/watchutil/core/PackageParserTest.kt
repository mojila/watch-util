package com.watchutil.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageParserTest {

    @Test
    fun `parses package lines and strips user suffix`() {
        val raw = """
            package:com.google.android.wearable.app
            package:com.android.systemui
            package:com.example.thing uid:10123
        """.trimIndent()

        assertEquals(
            listOf(
                "com.android.systemui",
                "com.example.thing",
                "com.google.android.wearable.app",
            ),
            PackageParser.parsePackages(raw),
        )
    }

    @Test
    fun `ignores blank and non-package lines`() {
        val raw = "\n  \nError: no packages\npackage:com.a\n"
        assertEquals(listOf("com.a"), PackageParser.parsePackages(raw))
    }

    @Test
    fun `deduplicates repeated packages`() {
        val raw = "package:com.a\npackage:com.a\npackage:com.b"
        assertEquals(listOf("com.a", "com.b"), PackageParser.parsePackages(raw))
    }

    @Test
    fun `state map marks enabled and disabled`() {
        val enabled = "package:com.on"
        val disabled = "package:com.off"

        val state = PackageParser.parseState(enabled, disabled)

        assertEquals(ServiceState.ENABLED, state["com.on"])
        assertEquals(ServiceState.DISABLED, state["com.off"])
    }

    @Test
    fun `disabled wins when a package appears in both lists`() {
        // pm can list a package in both sets during a transition; treat the
        // disabled listing as authoritative because it is the rarer state.
        val state = PackageParser.parseState("package:com.x", "package:com.x")
        assertEquals(ServiceState.DISABLED, state["com.x"])
    }

    @Test
    fun `isSuccess recognises pm output`() {
        assertTrue(PackageParser.isSuccess("Package com.x new state: disabled-user"))
        assertTrue(PackageParser.isSuccess("Package com.x new state: enabled"))
        assertTrue(PackageParser.isSuccess(""))
        assertFalse(PackageParser.isSuccess("Error: java.lang.SecurityException"))
    }
}
