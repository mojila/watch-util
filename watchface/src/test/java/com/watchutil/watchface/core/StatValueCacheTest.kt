package com.watchutil.watchface.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatValueCacheTest {

    @Test
    fun `usable current wins over cached`() {
        assertEquals("1234", StatValueCache.resolve(current = "1234", cached = "900"))
    }

    @Test
    fun `unusable current falls back to cached`() {
        assertEquals("900", StatValueCache.resolve(current = null, cached = "900"))
        assertEquals("900", StatValueCache.resolve(current = "", cached = "900"))
        assertEquals("900", StatValueCache.resolve(current = "   ", cached = "900"))
        assertEquals(
            StatFormat.PLACEHOLDER,
            StatValueCache.resolve(
                current = StatFormat.PLACEHOLDER,
                cached = StatFormat.PLACEHOLDER,
            ),
        )
        assertEquals("900", StatValueCache.resolve(current = StatFormat.PLACEHOLDER, cached = "900"))
    }

    @Test
    fun `both unusable yields the placeholder`() {
        assertEquals(
            StatFormat.PLACEHOLDER,
            StatValueCache.resolve(current = null, cached = null),
        )
        assertEquals(
            StatFormat.PLACEHOLDER,
            StatValueCache.resolve(current = "   ", cached = ""),
        )
    }

    @Test
    fun `usable current is reported as persistable and trimmed`() {
        assertEquals("1234", StatValueCache.persistable("1234"))
        assertEquals("1234", StatValueCache.persistable("  1234  "))
    }

    @Test
    fun `placeholder and null are not persistable`() {
        assertNull(StatValueCache.persistable(null))
        assertNull(StatValueCache.persistable(""))
        assertNull(StatValueCache.persistable("   "))
        assertNull(StatValueCache.persistable(StatFormat.PLACEHOLDER))
    }
}
