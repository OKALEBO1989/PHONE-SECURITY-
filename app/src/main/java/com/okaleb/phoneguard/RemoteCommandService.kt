package com.okaleb.phoneguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Session
import javax.mail.search.AndTerm
import javax.mail.search.FlagTerm
import javax.mail.search.OrTerm
import javax.mail.search.SubjectTerm

class RemoteCommandService : Service() {

    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(77, buildNotification())
        if (!running) {
            running = true
            worker = Thread { loop() }.also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("remote", "Remote control", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, "remote")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Remote alarm active")
            .setContentText("Listening for your email command")
            .setOngoing(true)
            .build()
    }

    private fun loop() {
        while (running) {
            try { check() } catch (e: Exception) { /* no internet or login error: retry */ }
            try { Thread.sleep(60_000) } catch (e: InterruptedException) { break }
        }
    }

    private fun check() {
        val p = getSharedPreferences("pg", MODE_PRIVATE)
        val gmail = p.getString("gmail", "") ?: ""
        val pass = p.getString("apppass", "") ?: ""
        val pin = p.getString("pin", "") ?: ""
        val owner = (p.getString("to", "") ?: "").lowercase()
        if (gmail.isBlank() || pass.isBlank() || pin.isBlank() || owner.isBlank()) return

        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "15000")
        }
        val store = Session.getInstance(props).getStore("imaps")
        try {
            store.connect("imap.gmail.com", 993, gmail, pass)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)

            // Only unread messages whose subject starts a PG command
            val term = AndTerm(
                FlagTerm(Flags(Flags.Flag.SEEN), false),
                OrTerm(SubjectTerm("PGALARM"), SubjectTerm("PGSTOP"))
            )
            for (m in inbox.search(term)) {
                val subject = (m.subject ?: "").trim()
                val from = (m.from?.joinToString { it.toString() } ?: "").lowercase()
                m.setFlag(Flags.Flag.SEEN, true)
                if (!from.contains(owner)) continue   // only your own address

                when (subject) {
                    "PGALARM $pin" -> ContextCompat.startForegroundService(
                        this, Intent(this, AlarmService::class.java)
                    )
                    "PGSTOP $pin" -> stopService(Intent(this, AlarmService::class.java))
                }
            }
            inbox.close(false)
        } finally {
            try { store.close() } catch (e: Exception) { }
        }
    }
}
