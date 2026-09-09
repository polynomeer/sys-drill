package com.sysdrill.backend.mail

import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

/** Real delivery via whatever SMTP relay `spring.mail.*` points at. Wired by [MailConfig] only when Spring Boot's own mail autoconfiguration produced a [JavaMailSender] (i.e. `spring.mail.host` is set). */
class SmtpEmailSender(
    private val mailSender: JavaMailSender,
    private val from: String,
) : EmailSender {

    override fun send(to: String, subject: String, body: String) {
        val message = SimpleMailMessage()
        message.setFrom(from)
        message.setTo(to)
        message.setSubject(subject)
        message.setText(body)
        mailSender.send(message)
    }
}
