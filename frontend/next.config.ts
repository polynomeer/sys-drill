import type { NextConfig } from "next";

const isDev = process.env.NODE_ENV === "development";
// docs/COMMERCIALIZATION.md — same var api.ts uses for its own fetch calls,
// so connect-src always matches whatever backend this build actually talks
// to, instead of hardcoding one.
const backendOrigin = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

// docs/COMMERCIALIZATION.md — follows Next's own documented "Without
// Nonces" CSP pattern (node_modules/next/dist/docs/01-app/02-guides/
// content-security-policy.md) rather than nonce-based: nonces require every
// page to opt into dynamic rendering, and most of this app's pages are
// statically generated on purpose. 'unsafe-inline' is the tradeoff that
// comes with skipping nonces -- still blocks the common case (loading an
// arbitrary external script/style origin), just not inline-script
// injection specifically.
const cspHeader = `
  default-src 'self';
  script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""};
  style-src 'self' 'unsafe-inline';
  img-src 'self' blob: data:;
  font-src 'self';
  connect-src 'self' ${backendOrigin} https://*.sentry.io;
  object-src 'none';
  base-uri 'self';
  form-action 'self';
  frame-ancestors 'none';
  upgrade-insecure-requests;
`
  .replace(/\s{2,}/g, " ")
  .trim();

const nextConfig: NextConfig = {
  // docs/COMMERCIALIZATION.md — Dockerfile's runtime stage copies only the
  // standalone output + static assets, not the full node_modules tree.
  output: "standalone",

  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "Content-Security-Policy", value: cspHeader },
          { key: "X-Content-Type-Options", value: "nosniff" },
          // Superseded by the CSP's frame-ancestors above in modern browsers,
          // kept alongside it for the ones that don't parse CSP3 yet.
          { key: "X-Frame-Options", value: "DENY" },
          // No-op over plain HTTP (dev, this repo's only deployment target
          // today per docs/COMMERCIALIZATION.md) -- browsers only honor this
          // over HTTPS, so it's inert until this is actually served over one.
          { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
