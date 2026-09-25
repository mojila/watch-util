package com.watchutil.watchface

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Watch face editor entry point.
 *
 * The face needs `BODY_SENSORS` for live heart rate, but the complication
 * fallback works without it, so this activity only asks for the permission and
 * then finishes. It must never crash when the permission is denied.
 *
 * Uses the platform `requestPermissions` API rather than
 * `registerForActivityResult`, because the module deliberately carries no
 * dependency on `androidx.activity`.
 */
class WatchFaceConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already granted (or already denied once): nothing to ask, close
        // quietly. Re-asking on every edit would be noise.
        if (hasSensorPermission()) {
            finish()
            return
        }

        Toast.makeText(this, getString(R.string.permission_rationale), Toast.LENGTH_SHORT).show()
        requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), REQUEST_BODY_SENSORS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_BODY_SENSORS) return

        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        val message = if (granted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun hasSensorPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val REQUEST_BODY_SENSORS = 1
    }
}
