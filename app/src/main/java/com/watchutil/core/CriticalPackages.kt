package com.watchutil.core

/**
 * Packages that must never be disabled.
 *
 * Disabling any of these can leave the watch unable to boot, draw its UI,
 * maintain Bluetooth connectivity, or reach the companion app — and on a watch
 * there is often no other way to recover. The app refuses to disable them; they
 * can still be *enabled* so a user can undo a mistake made elsewhere.
 *
 * Kept free of Android imports so it can be unit-tested on the JVM.
 */
object CriticalPackages {

    /**
     * Exact package names that are never safe to disable.
     *
     * The launcher entries cover AOSP Home and the common OEM/GMS homes; extra
     * names are harmless when a package is absent from the watch.
     */
    private val EXACT = setOf(
        "com.android.systemui",
        "com.android.bluetooth",
        "com.google.android.wearable.app",
        // Launcher / home packages.
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.wearable.launcher",
        "com.google.android.wearable.home",
        // WatchUtil itself.
        "com.watchutil",
    )

    /**
     * Package-name prefixes that are never safe to disable. Matched with
     * [String.startsWith] so variants such as `com.android.bluetooth.audio`
     * are covered by a single entry.
     */
    private val PREFIXES = listOf(
        "com.android.bluetooth",
        "com.android.systemui",
        "com.google.android.wearable.app",
    )

    /**
     * True when [packageName] is too critical to disable.
     *
     * Matching is by exact name or by [PREFIXES], whichever applies.
     */
    fun isProtected(packageName: String): Boolean =
        packageName in EXACT || PREFIXES.any { packageName.startsWith(it) }
}
