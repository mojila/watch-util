package com.watchutil.watchface.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatFormatTest {

    @Test
    fun `placeholder is two dashes`() {
        assertEquals("--", StatFormat.PLACEHOLDER)
    }

    @Test
    fun `null becomes the placeholder`() {
        assertEquals(StatFormat.PLACEHOLDER, StatFormat.value(null))
    }

    @Test
    fun `blank and whitespace become the placeholder`() {
        assertEquals(StatFormat.PLACEHOLDER, StatFormat.value(""))
        assertEquals(StatFormat.PLACEHOLDER, StatFormat.value("   "))
        assertEquals(StatFormat.PLACEHOLDER, StatFormat.value("\t\n"))
    }

    @Test
    fun `normal text is returned trimmed`() {
        assertEquals("1234", StatFormat.value("1234"))
        assertEquals("1234", StatFormat.value("  1234  "))
        assertEquals("HEART RATE", StatFormat.value("\tHEART RATE\n"))
    }
}
