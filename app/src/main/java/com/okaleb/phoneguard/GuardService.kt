package com.okaleb.phoneguard

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sqrt

class GuardService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL = "phoneguard_guard"
        const val GRACE_MS = 30000L
        @Volatile var armed = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var challenge = false
    private var safeUntil = 0L
    private var sm: SensorManager? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_USER_PRESENT -> cancelChallenge()
                Intent.ACTION_SCREEN_OFF ->
                    safeUntil = System.currentTimeMillis() + 60000
            }
        }
    }

    private val fire = Runnable {
        challenge = false
        if (armed) ContextCompat.startForegroundService(
            this, Intent(this, AlarmService::class.java)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (armed) return START_STICKY
        armed = true
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Owner guard", NotificationManager.IMPORTANCE_LOW)
            )
        }
        ServiceCompat.startForeground(
            this, 2, buildNotif("Owner guard is on"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm?.registerListener(
            this, sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        return START_STICKY
    }

    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("PHONE SECURITY")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java).notify(2, buildNotif(text))
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (challenge || AlarmService.running) return
        if (System.currentTimeMillis() < safeUntil) return
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) return
        val g = sqrt(
            e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]
        )
        if (abs(g - 9.81f) > 3f) startChallenge()
    }

    private fun startChallenge() {
        challenge = true
        update("Movement detected. Unlock the phone within 30 seconds")
        handler.postDelayed(fire, GRACE_MS)
    }

    private fun cancelChallenge() {
        handler.removeCallbacks(fire)
        challenge = false
        safeUntil = System.currentTimeMillis() + 10000
        update("Owner verified. Guard is on")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        armed = false
        handler.removeCallbacksAndMessages(null)
        sm?.unregisterListener(this)
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
