package com.okaleb.phoneguard

import android.Manifest
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PhoneGuardAdmin : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent) {
        val p = context.getSharedPreferences("pg", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val camOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (camOk && now - p.getLong("lastPhoto", 0L) > 60000L) {
            p.edit().putLong("lastPhoto", now).apply()
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, PhotoService::class.java)
                )
            } catch (_: Exception) {}
        }
        ContextCompat.startForegroundService(
            context, Intent(context, AlarmService::class.java)
        )
    }
}
