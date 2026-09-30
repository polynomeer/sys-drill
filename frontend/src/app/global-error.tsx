"use client";

import { useEffect } from "react";
import Link from "next/link";
import * as Sentry from "@sentry/nextjs";

/**
 * docs/COMMERCIALIZATION.md — last-resort boundary for when the root layout
 * itself throws. Per Next's own docs (node_modules/next/dist/docs/...
 * error.md) this must render its own <html>/<body> and does NOT
 * automatically inherit globals.css/next-font -- so this intentionally
 * doesn't depend on Tailwind, fonts, or any other app component (all of
 * which may be exactly what's broken); colors are the same dark-theme
 * tokens as globals.css, just inlined.
 */
export default function GlobalError({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => {
    console.error(error);
    Sentry.captureException(error);
  }, [error]);

  return (
    <html lang="ko">
      <body style={{ margin: 0, background: "#08111f", color: "#f5f7fa", fontFamily: "system-ui, sans-serif" }}>
        <div style={{ display: "flex", minHeight: "100vh", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: "1rem", padding: "2rem", textAlign: "center" }}>
          <p style={{ color: "#ef4444", fontSize: "0.875rem" }}>문제가 발생했어요. 페이지를 표시할 수 없습니다.</p>
          <div style={{ display: "flex", gap: "0.5rem" }}>
            <button
              onClick={() => retry()}
              style={{ borderRadius: "0.5rem", background: "#2f80ff", color: "#f5f7fa", padding: "0.5rem 1rem", fontSize: "0.875rem", border: "none", cursor: "pointer" }}
            >
              다시 시도
            </button>
            <Link
              href="/"
              style={{ borderRadius: "0.5rem", border: "1px solid #334155", color: "#f5f7fa", padding: "0.5rem 1rem", fontSize: "0.875rem", textDecoration: "none" }}
            >
              홈으로
            </Link>
          </div>
        </div>
      </body>
    </html>
  );
}
