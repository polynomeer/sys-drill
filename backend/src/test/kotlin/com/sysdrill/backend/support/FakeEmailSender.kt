package com.sysdrill.backend.support

import com.sysdrill.backend.mail.EmailSender
import java.util.concurrent.CopyOnWriteArrayList
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

data class SentEmail(val to: String, val subject: String, val body: String)

/** No real mail server in tests — substitutes a controllable fake for [EmailSender] via ordinary Spring DI (this codebase uses no mocking framework). Import with `@Import(FakeEmailConfig::class)`. */
class FakeEmailSender : EmailSender {
    val sent = CopyOnWriteArrayList<SentEmail>()

    override fun send(to: String, subject: String, body: String) {
        sent.add(SentEmail(to, subject, body))
    }
}

@TestConfiguration
class FakeEmailConfig {
    // A distinct bean name from MailConfig.emailSender() -- Spring Boot refuses
    // to start when two @Bean methods share the SAME name regardless of
    // @Primary (that only disambiguates by type at injection points, it
    // doesn't permit a name collision), so this can't be named emailSender().
    @Bean
    @Primary
    fun fakeEmailSender(): EmailSender = FakeEmailSender()
}
