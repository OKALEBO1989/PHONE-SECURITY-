package com.okaleb.phoneguard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class PhoneGuardAdmin : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent) {
        ContextCompat.startForegroundService(
            context, Intent(context, AlarmService::class.java)
        )
    }
}
