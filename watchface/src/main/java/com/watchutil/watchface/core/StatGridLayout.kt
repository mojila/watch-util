package com.watchutil.watchface.core

/**
 * Geometry for the face's 2x3 stat grid.
 *
 * Everything here is pure arithmetic over plain [Float]s so it can be unit
 * tested on the JVM. Coordinates come in two flavours:
 *
 * - [columnX], [labelY] and [valueY] are **pixels**, relative to the face's
 *   centre and scaled by the bezel radius.
 * - the `slot*` functions return **unit-square display fractions** in `0..1`,
 *   which is the form the complication slot system uses.
 *
 * ### Round-bezel reasoning
 *
 * The lowest row's value baseline sits at unit-square `y ~ 0.834`. At that
 * height the circle's half-chord is `sqrt(0.5^2 - 0.334^2) ~= 0.37`, so the
 * usable horizontal band is roughly `0.13..0.87`. The two columns span
 * `0.24..0.76`, comfortably inside it, so no column is clipped on a round
 * bezel. The same bounds also leave margin on a square display.
 */

/** x offset from centre, as a fraction of radius. */
const val COLUMN_OFFSET_RATIO = 0.26f

/** First row label baseline below centre, as a fraction of radius. */
const val FIRST_LABEL_Y_RATIO = 0.14f

/** Vertical gap between row label baselines, as a fraction of radius. */
const val ROW_PITCH_RATIO = 0.20f

/** Value text size, as a fraction of radius. */
const val STAT_TEXT_RATIO = 0.085f

/** Label size relative to value size. */
const val LABEL_SIZE_FACTOR = 0.7f

/** Value baseline below label baseline, in multiples of the value text size. */
const val VALUE_OFFSET_FACTOR = 1.25f

// Slot bounds: three vertically tiled rows, two horizontally separated columns,
// in unit-square display fractions. Tiles touch but never overlap.

/** Unit-square `y` of the top edge of the first row. */
const val SLOT_TOP_FIRST = 0.535f

/** Unit-square height of a single row. */
const val SLOT_ROW_HEIGHT = 0.10f

/** Unit-square `x` of the left column's left edge. */
const val SLOT_LEFT_EDGE = 0.24f

/** Unit-square `x` of the right column's left edge. */
const val SLOT_RIGHT_EDGE = 0.50f

/** Unit-square width of a single column. */
const val SLOT_WIDTH = 0.26f

/** Value text size in pixels for a bezel of [radius]. */
fun statTextSize(radius: Float): Float = radius * STAT_TEXT_RATIO

/** Label text size in pixels for a bezel of [radius]. */
fun labelTextSize(radius: Float): Float = statTextSize(radius) * LABEL_SIZE_FACTOR

/**
 * Pixel x of a column's centre.
 *
 * @param column `0` = left, `1` = right.
 */
fun columnX(centerX: Float, radius: Float, column: Int): Float =
    centerX + (if (column == 0) -1f else 1f) * radius * COLUMN_OFFSET_RATIO

/**
 * Pixel y of a row's label baseline.
 *
 * @param row `0..2`, top to bottom.
 */
fun labelY(centerY: Float, radius: Float, row: Int): Float =
    centerY + radius * (FIRST_LABEL_Y_RATIO + row * ROW_PITCH_RATIO)

/**
 * Pixel y of a row's value baseline, one [VALUE_OFFSET_FACTOR] below the label.
 *
 * @param row `0..2`, top to bottom.
 */
fun valueY(centerY: Float, radius: Float, row: Int): Float =
    labelY(centerY, radius, row) + statTextSize(radius) * VALUE_OFFSET_FACTOR

/** Unit-square `y` of a row's top edge. */
fun slotTop(row: Int): Float = SLOT_TOP_FIRST + row * SLOT_ROW_HEIGHT

/** Unit-square `y` of a row's bottom edge; equals the next row's top. */
fun slotBottom(row: Int): Float = slotTop(row) + SLOT_ROW_HEIGHT

/** Unit-square `x` of a column's left edge; `0` = left, `1` = right. */
fun slotLeft(column: Int): Float =
    if (column == 0) SLOT_LEFT_EDGE else SLOT_RIGHT_EDGE

/** Unit-square `x` of a column's right edge; `0` = left, `1` = right. */
fun slotRight(column: Int): Float = slotLeft(column) + SLOT_WIDTH
