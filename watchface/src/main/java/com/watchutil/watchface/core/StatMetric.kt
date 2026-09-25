package com.watchutil.watchface.core

// Full class names of Xiaomi's complication data sources. Each is a *default*
// only: the slot stays user-changeable, so another watch can use a different
// provider.
//
// These literals are declared at file scope, not in [StatMetric]'s companion,
// because an enum entry argument cannot touch its own companion object (it is
// still uninitialized while the entries are being constructed). The companion
// re-exposes them under the public names callers use.
private const val XIAOMI_PACKAGE = "com.xiaomi.wear.fitness.whs_service"
private const val XIAOMI_STEPS =
    "$XIAOMI_PACKAGE.complication.StepComplicationService"
private const val XIAOMI_CALORIES =
    "$XIAOMI_PACKAGE.complication.CaloriesComplicationService"
private const val XIAOMI_HEART_RATE =
    "$XIAOMI_PACKAGE.complication.HeartRateComplicationService"
private const val XIAOMI_STAND =
    "$XIAOMI_PACKAGE.complication.StandComplicationService"
private const val XIAOMI_SPO2 =
    "$XIAOMI_PACKAGE.complication.Spo2ComplicationService"
private const val XIAOMI_VITALITY =
    "$XIAOMI_PACKAGE.complication.VitalityComplicationService"

/**
 * One of the six health metrics shown in the face's 2x3 stat grid.
 *
 * The enum is declared in grid order, so [ordinal] is also the reading order:
 * left-to-right within a row, rows top to bottom. Each metric names the
 * complication data source that *defaults* to the Xiaomi Fitness provider; the
 * slot stays user-changeable, so another watch may bind a different provider to
 * the same slot.
 *
 * Pure description only — no Android imports, so it is unit-testable on the JVM.
 *
 * @property label short uppercase caption drawn above the value.
 * @property providerService fully qualified class name of the default
 *   complication service for this slot.
 * @property column grid column, `0` = left, `1` = right.
 * @property row grid row, `0..2` top to bottom.
 */
enum class StatMetric(
    val label: String,
    val providerService: String,
    val column: Int,
    val row: Int,
) {
    /** Steps taken reported by the Xiaomi Fitness step source. */
    STEPS("STEPS", XIAOMI_STEPS, 0, 0),

    /** Active calories burned reported by the Xiaomi Fitness source. */
    CALORIES("CALORIES", XIAOMI_CALORIES, 1, 0),

    /** Current heart rate in bpm. */
    HEART_RATE("HEART RATE", XIAOMI_HEART_RATE, 0, 1),

    /** Stand goal time, i.e. hours with movement in the current hour. */
    STAND("STAND", XIAOMI_STAND, 1, 1),

    /** Blood oxygen saturation percentage. */
    SPO2("SPO2", XIAOMI_SPO2, 0, 2),

    /** Xiaomi vitality / activity score. */
    VITALITY("VITALITY", XIAOMI_VITALITY, 1, 2);

    /** Complication slot id: stable, 1-based, unique per metric. */
    val slotId: Int get() = ordinal + 1

    companion object {
        /** Xiaomi Fitness (Wear Health Service) package. */
        const val PROVIDER_PACKAGE = XIAOMI_PACKAGE

        // Public aliases of the file-scope literals above, so callers can
        // still name a provider as `StatMetric.PROVIDER_STEPS`.
        const val PROVIDER_STEPS = XIAOMI_STEPS
        const val PROVIDER_CALORIES = XIAOMI_CALORIES
        const val PROVIDER_HEART_RATE = XIAOMI_HEART_RATE
        const val PROVIDER_STAND = XIAOMI_STAND
        const val PROVIDER_SPO2 = XIAOMI_SPO2
        const val PROVIDER_VITALITY = XIAOMI_VITALITY
    }
}
