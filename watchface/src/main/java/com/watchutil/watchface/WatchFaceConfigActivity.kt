package com.watchutil.watchface

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Watch face editor entry point.
 *
 * Asks for the one runtime permission the face needs, reports the outcome, and
 * finishes. It is optional — the face degrades gracefully when denied.
 *
 * `RECEIVE_COMPLICATION_DATA` is what the system's `ComplicationController`
 * checks before handing complication data to a watch face. Without it every
 * slot is replaced with "no permission" data, so all six metrics stay at their
 * `--` placeholder. The platform only ever fires its automatic request once
 * (when the face is first added), so a user who missed that prompt has no other
 * route to grant it — hence asking here.
 *
 * The face reads every metric from a complication slot, so it needs no sensor
 * permission of its own.
 *
 * This activity must never crash when the permission is denied.
 */
class WatchFaceConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val missing = REQUIRED_PERMISSIONS.filterNot(::hasPermission)
        if (missing.isEmpty()) {
            // Nothing to ask. Re-prompting on every edit would be noise.
            finish()
            return
        }

        Toast.makeText(this, getString(R.string.permission_rationale), Toast.LENGTH_SHORT).show()
        requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return

        val message = if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val REQUEST_PERMISSIONS = 1

        /**
         * Declared in the manifest. The complication permission is defined by
         * the Wear OS system app, so it is referenced by its literal name.
         */
        val REQUIRED_PERMISSIONS = listOf(
            "com.google.android.wearable.permission.RECEIVE_COMPLICATION_DATA",
        )
    }
}
