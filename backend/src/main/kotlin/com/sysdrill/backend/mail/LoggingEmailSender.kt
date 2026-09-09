package com.sysdrill.backend.mail

import org.slf4j.LoggerFactory

/**
 * Default [EmailSender] when no SMTP host is configured (`spring.mail.host`
 * blank) — local/demo environments and CI. Mirrors GoogleAuthService's
 * "not configured" stance, but a no-op-with-a-log instead of an error: unlike
 * Google login (a user-initiated action that must fail loudly if broken),
 * email is fired from background flows (invitations, password reset) where a
 * loud failure would break the calling transaction for something the caller
 * didn't directly ask for. Wired by [MailConfig], not a `@Component` itself.
 */
class LoggingEmailSender : EmailSender {
    private val log = LoggerFactory.getLogger(LoggingEmailSender::class.java)

    override fun send(to: String, subject: String, body: String) {
        log.info("[mail:not-configured] to={} subject={}\n{}", to, subject, body)
    }
}
