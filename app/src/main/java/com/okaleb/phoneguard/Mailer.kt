package com.okaleb.phoneguard

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

object Mailer {
    fun send(
        ctx: Context,
        subject: String,
        body: String,
        attachment: File? = null,
        onDone: ((String) -> Unit)? = null
    ) {
        val p = ctx.getSharedPreferences("pg", Context.MODE_PRIVATE)
        val user = (p.getString("gmail", "") ?: "").trim()
        val pass = (p.getString("apppass", "") ?: "").replace(" ", "")
        val to = (p.getString("to", "") ?: "").trim()
        if (user.isBlank() || pass.isBlank() || to.isBlank()) {
            onDone?.invoke("Fill in all 3 email fields, then tap Save settings")
            return
        }

        Thread {
            try {
                val props = Properties().apply {
                    put("mail.smtp.host", "smtp.gmail.com")
                    put("mail.smtp.port", "465")
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.connectiontimeout", "15000")
                    put("mail.smtp.timeout", "15000")
                }
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(user, pass)
                })
                val msg = MimeMessage(session)
                msg.setFrom(InternetAddress(user))
                msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                msg.setSubject(subject)
                if (attachment != null && attachment.exists()) {
                    val textPart = MimeBodyPart()
                    textPart.setText(body)
                    val filePart = MimeBodyPart()
                    filePart.setDataHandler(DataHandler(FileDataSource(attachment)))
                    filePart.setFileName(attachment.name)
                    val multi = MimeMultipart()
                    multi.addBodyPart(textPart)
                    multi.addBodyPart(filePart)
                    msg.setContent(multi)
                } else {
                    msg.setText(body)
                }
                Transport.send(msg)
                onDone?.invoke("Email sent. Check inbox and Spam.")
            } catch (e: Exception) {
                Log.e("Mailer", "Send failed", e)
                onDone?.invoke("Email failed: ${e.message}")
            }
        }.start()
    }
}
