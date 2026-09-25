package com.watchutil.watchface.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatGridLayoutTest {

    // -------------------------------------------------------------- text sizes

    @Test
    fun `label text is smaller than value text`() {
        val radius = 200f
        assertTrue(labelTextSize(radius) < statTextSize(radius))
    }

    @Test
    fun `text sizes scale with the radius`() {
        assertEquals(0f, statTextSize(radius = 0f), 0f)
        assertEquals(
            statTextSize(200f) * 2f,
            statTextSize(400f),
            0.0001f,
        )
    }

    // ------------------------------------------------------------------ column

    @Test
    fun `column zero is left of centre and column one is right`() {
        val centerX = 200f
        val radius = 200f
        assertTrue(columnX(centerX, radius, 0) < centerX)
        assertTrue(columnX(centerX, radius, 1) > centerX)
    }

    @Test
    fun `columns are symmetric about the centre`() {
        val centerX = 123f
        val radius = 180f
        val left = columnX(centerX, radius, 0)
        val right = columnX(centerX, radius, 1)
        assertEquals(centerX - left, right - centerX, 0.0001f)
        assertEquals(centerX * 2f, left + right, 0.0001f)
    }

    // ------------------------------------------------------------------- rows

    @Test
    fun `label y increases strictly with the row`() {
        val centerY = 200f
        val radius = 200f
        val y0 = labelY(centerY, radius, 0)
        val y1 = labelY(centerY, radius, 1)
        val y2 = labelY(centerY, radius, 2)
        assertTrue(y0 < y1)
        assertTrue(y1 < y2)
    }

    @Test
    fun `value y sits below the label for the same row`() {
        val centerY = 200f
        val radius = 200f
        for (row in 0..2) {
            assertTrue(valueY(centerY, radius, row) > labelY(centerY, radius, row))
        }
    }

    // ------------------------------------------------------------------ slots

    @Test
    fun `every row has top above bottom and all bounds are in range`() {
        for (row in 0..2) {
            assertTrue(slotTop(row) < slotBottom(row))
            assertTrue(slotTop(row) in 0f..1f)
            assertTrue(slotBottom(row) in 0f..1f)
        }
        for (column in 0..1) {
            assertTrue(slotLeft(column) < slotRight(column))
            assertTrue(slotLeft(column) in 0f..1f)
            assertTrue(slotRight(column) in 0f..1f)
        }
    }

    @Test
    fun `rows are vertically tiled without gaps or overlap`() {
        // A tight tolerance, not 0: `slotTop(row)` computes `row * height`, so
        // row 2 can land one float ULP away from the previous row's bottom.
        // The point is that the edges coincide to within rounding, not that
        // they are bit-identical.
        assertEquals(slotBottom(0), slotTop(1), 1e-6f)
        assertEquals(slotBottom(1), slotTop(2), 1e-6f)
    }

    @Test
    fun `left column never reaches into the right column`() {
        assertTrue(slotRight(0) <= slotLeft(1))
    }

    @Test
    fun `columns are equal width and rows are equal height`() {
        val width = slotRight(0) - slotLeft(0)
        assertEquals(width, slotRight(1) - slotLeft(1), 0f)
        val height = slotBottom(0) - slotTop(0)
        assertEquals(height, slotBottom(1) - slotTop(1), 0f)
        assertEquals(height, slotBottom(2) - slotTop(2), 0f)
    }
}
