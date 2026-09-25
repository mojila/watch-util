package com.watchutil.watchface.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatMetricTest {

    @Test
    fun `there are exactly six metrics`() {
        assertEquals(6, StatMetric.entries.size)
    }

    @Test
    fun `slot ids are 1 through 6 and unique`() {
        val slotIds = StatMetric.entries.map { it.slotId }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), slotIds)
        assertEquals(slotIds.size, slotIds.toSet().size)
    }

    @Test
    fun `column and row pairs cover the whole 2x3 grid exactly once`() {
        val cells = StatMetric.entries.map { it.column to it.row }.toSet()
        val expected = buildSet {
            for (column in 0..1) for (row in 0..2) add(column to row)
        }
        assertEquals(expected, cells)
        assertEquals(6, StatMetric.entries.size)
    }

    @Test
    fun `declaration order is left to right, top to bottom`() {
        assertEquals(
            listOf(
                StatMetric.STEPS,
                StatMetric.CALORIES,
                StatMetric.HEART_RATE,
                StatMetric.STAND,
                StatMetric.SPO2,
                StatMetric.VITALITY,
            ),
            StatMetric.entries,
        )
        StatMetric.entries.forEachIndexed { index, metric ->
            assertEquals(index, metric.row * 2 + metric.column)
        }
    }

    @Test
    fun `provider services are non-blank, distinct and scoped to the package`() {
        val services = StatMetric.entries.map { it.providerService }
        assertTrue(services.all { it.isNotBlank() })
        assertEquals(services.size, services.toSet().size)
        services.forEach { service ->
            assertTrue(service.startsWith(StatMetric.PROVIDER_PACKAGE + "."))
        }
    }

    @Test
    fun `labels are non-blank and unique`() {
        val labels = StatMetric.entries.map { it.label }
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(labels.size, labels.toSet().size)
    }
}
