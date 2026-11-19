package com.okaleb.phoneguard

import android.content.Context
import android.util.Log
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object Mailer {
    fun send(ctx: Context, subject: String, body: String) {
        val p = ctx.getSharedPreferences("pg", Context.MODE_PRIVATE)
        val user = p.getString("gmail", "") ?: ""
        val pass = (p.getString("apppass", "") ?: "").replace(" ", "")
        val to = p.getString("to", "") ?: ""
        if (user.isBlank() || pass.isBlank() || to.isBlank()) return

        Thread {
            try {
                val props = Properties().apply {
                    put("mail.smtp.host", "smtp.gmail.com")
                    put("mail.smtp.port", "465")
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.ssl.enable", "true")
                }
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(user, pass)
                })
                val msg = MimeMessage(session).apply {
                    setFrom(InternetAddress(user))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                    setSubject(subject)
                    setText(body)
                }
                Transport.send(msg)
            } catch (e: Exception) {
                Log.e("Mailer", "Send failed", e)
            }
        }.start()
    }
}
