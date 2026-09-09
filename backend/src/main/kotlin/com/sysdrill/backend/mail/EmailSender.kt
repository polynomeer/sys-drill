package com.sysdrill.backend.mail

/**
 * Provider-agnostic email boundary (docs/COMMERCIALIZATION.md). Real delivery
 * goes through [SmtpEmailSender] via any standard SMTP relay (SES, SendGrid,
 * Postmark, etc. all speak SMTP) — no provider-specific SDK, matching this
 * codebase's preference for the plainest tech that does the job.
 */
interface EmailSender {
    fun send(to: String, subject: String, body: String)
}
