// docs/COMMERCIALIZATION.md — error tracking, inert until SENTRY_DSN is set
// (Sentry's own SDK behavior for an empty/missing dsn -- same "not
// configured" stance as MailConfig.kt on the backend). No source-map upload
// plugin wired into next.config.ts here — that needs a Sentry auth token
// this project doesn't have yet; basic error capture doesn't require it.
export async function register() {
  if (!process.env.SENTRY_DSN) return;

  if (process.env.NEXT_RUNTIME === "nodejs") {
    const Sentry = await import("@sentry/nextjs");
    Sentry.init({ dsn: process.env.SENTRY_DSN, tracesSampleRate: 0 });
  }

  if (process.env.NEXT_RUNTIME === "edge") {
    const Sentry = await import("@sentry/nextjs");
    Sentry.init({ dsn: process.env.SENTRY_DSN, tracesSampleRate: 0 });
  }
}
