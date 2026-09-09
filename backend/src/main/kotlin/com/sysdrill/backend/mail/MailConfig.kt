package com.sysdrill.backend.mail

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

/**
 * Spring Boot's `MailSenderAutoConfiguration` only creates a [JavaMailSender]
 * bean when `spring.mail.host` is set — so [ObjectProvider.ifAvailable] being
 * null IS the "not configured" signal, with no need to duplicate that
 * property under a `sysdrill.*` namespace.
 */
@Configuration
class MailConfig(
    private val mailSenderProvider: ObjectProvider<JavaMailSender>,
    @Value("\${sysdrill.mail.from:no-reply@sysdrill.dev}") private val from: String,
) {
    @Bean
    fun emailSender(): EmailSender =
        mailSenderProvider.ifAvailable?.let { SmtpEmailSender(it, from) } ?: LoggingEmailSender()
}
