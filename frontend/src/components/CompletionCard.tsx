"use client";

import { useEffect, useState } from "react";
import { CircleCheck } from "lucide-react";
import { CertificationStatus, SessionResponse, getMyCertification, getSession } from "@/lib/api";
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { Button } from "@/components/ui/Button";

function formatElapsed(ms: number): string {
  const minutes = Math.max(1, Math.round(ms / 60_000));
  if (minutes < 60) return `${minutes}분`;
  return `${Math.floor(minutes / 60)}시간 ${minutes % 60}분`;
}

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.8 — the "you finished it" moment at the
 * top of a completed session's report: time taken, where this leaves the
 * domain track, and a copyable public-profile link. Owner-only; renders
 * nothing for spectators, unfinished sessions, or if either call fails.
 */
export function CompletionCard({ sessionId, averageScore }: { sessionId: string; averageScore: number | null }) {
  const [session, setSession] = useState<SessionResponse | null>(null);
  const [certification, setCertification] = useState<CertificationStatus | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    getSession(sessionId).then(setSession).catch(() => setSession(null));
    getMyCertification().then(setCertification).catch(() => setCertification(null));
  }, [sessionId]);

  if (!session || !session.isOwner || session.status !== "COMPLETED" || !session.completedAt) return null;

  const domainTitle = DOMAIN_TITLES[session.domain];
  const elapsed = new Date(session.completedAt).getTime() - new Date(session.startedAt).getTime();
  const passedDomains = certification?.domains.filter((d) => d.passed).length ?? 0;
  const totalDomains = certification?.domains.length ?? 0;
  const profileUrl = certification ? `${window.location.origin}/certifications/${certification.userId}` : null;

  async function copyProfileUrl() {
    if (!profileUrl) return;
    try {
      await navigator.clipboard.writeText(profileUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <section className="flex flex-col gap-4 rounded-xl border border-success/40 bg-success/5 p-5 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-start gap-3">
        <CircleCheck className="mt-0.5 h-6 w-6 shrink-0 text-success" aria-hidden />
        <div>
          <p className="font-semibold">Drill 완료{domainTitle ? ` — ${domainTitle}` : ""}</p>
          <p className="mt-1 text-sm text-foreground-muted">
            {averageScore !== null && `평균 ${averageScore}점 · `}소요 {formatElapsed(elapsed)}
            {totalDomains > 0 && ` · 인증 도메인 ${passedDomains} / ${totalDomains}`}
          </p>
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        {domainTitle && (
          <Button href={`/tracks/${session.domain}`} variant="secondary" size="sm">
            {domainTitle} 트랙 →
          </Button>
        )}
        {profileUrl && (
          <Button onClick={copyProfileUrl} variant="secondary" size="sm">
            {copied ? "복사됨" : "공개 프로필 링크 복사"}
          </Button>
        )}
      </div>
    </section>
  );
}
