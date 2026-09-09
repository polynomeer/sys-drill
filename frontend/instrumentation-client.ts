// docs/COMMERCIALIZATION.md — client-side counterpart to instrumentation.ts's
// register(). NEXT_PUBLIC_* so it's inlined at build time for the browser
// bundle; same "empty dsn = inert" behavior.
import * as Sentry from "@sentry/nextjs";

if (process.env.NEXT_PUBLIC_SENTRY_DSN) {
  Sentry.init({ dsn: process.env.NEXT_PUBLIC_SENTRY_DSN, tracesSampleRate: 0 });
}
