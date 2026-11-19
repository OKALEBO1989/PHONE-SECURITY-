package com.okaleb.phoneguard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Date
import java.util.Locale

class AlarmService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val MESSAGE = "Take me back to the owner. I do not belong to you."
        const val CHANNEL = "phoneguard_alarm"
        @Volatile var running = false
    }

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())

    private val speakLoop = object : Runnable {
        override fun run() {
            tts?.speak(MESSAGE, TextToSpeech.QUEUE_FLUSH, null, "pg")
            handler.postDelayed(this, 8000)
        }
    }

    private val alertLoop = object : Runnable {
        override fun run() {
            sendAlert()
            handler.postDelayed(this, 120000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        running = true
        startAsForeground()
        setMaxVolume()
        startDrum()
        tts = TextToSpeech(this, this)
        handler.post(alertLoop)
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Alarm", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("PHONE SECURITY ALARM")
            .setContentText("Enter the PIN to stop the alarm")
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        val hasLoc = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasLoc) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        ServiceCompat.startForeground(this, 1, notif, type)
    }

    private fun setMaxVolume() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(
            AudioManager.STREAM_ALARM,
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
        )
    }

    private fun alarmAttrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun startDrum() {
        val id = resources.getIdentifier("african_drum", "raw", packageName)
        if (id == 0) { DrumSynth.start(); return }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(alarmAttrs())
                setDataSource(
                    this@AlarmService,
                    Uri.parse("android.resource://$packageName/$id")
                )
                isLooping = true
                setVolume(0.7f, 0.7f)
                prepare()
                start()
            }
        } catch (e: Exception) { DrumSynth.start() }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val r = tts?.setLanguage(Locale.forLanguageTag("en-NG"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.UK)
        }
        tts?.setAudioAttributes(alarmAttrs())
        tts?.setSpeechRate(0.9f)
        handler.post(speakLoop)
    }

    private fun sendAlert() {
        Locator.getLocation(this) { loc ->
            val where = if (loc != null)
                "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            else "Location not available"
            Mailer.send(
                this,
                "PHONE SECURITY ALARM",
                "Your phone alarm is ringing.\nTime: ${Date()}\nLocation: $where"
            )
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        DrumSynth.stop()
        player?.release()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
