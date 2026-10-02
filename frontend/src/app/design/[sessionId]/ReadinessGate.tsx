"use client";

import { useEffect, useState } from "react";
import { type Readiness, confirmReadiness, getReadiness } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { AlertsView } from "./AlertsView";
import { ChangeReviewCard } from "@/components/ChangeReviewCard";

const POLL_MS = 3000;

/**
 * docs/OBSERVABILITY_UI_PLAN.md O7 (PLAN.md Round E26) — "deploy" checklist right before the
 * incident. Alerts and SLO are checked from what was actually set (the editor is right here);
 * structured logging and tracing are switches. Whatever is left off is missing during the incident
 * (no traces, logs without fields) — the point is to feel that, not to be told.
 */
export function ReadinessGate({ sessionId, onDone }: { sessionId: string; onDone: () => void }) {
  const [readiness, setReadiness] = useState<Readiness | null>(null);
  const [logging, setLogging] = useState(false);
  const [tracing, setTracing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Alert rules and SLO are edited in the panel below — re-read the auto checks while the gate is open.
  useEffect(() => {
    const load = () => getReadiness(sessionId).then(setReadiness).catch(() => undefined);
    load();
    const timer = setInterval(load, POLL_MS);
    return () => clearInterval(timer);
  }, [sessionId]);

  async function deploy() {
    setBusy(true);
    setError(null);
    try {
      await confirmReadiness(sessionId, { structuredLogging: logging, tracing });
      onDone();
    } catch {
      setError("준비 상태를 저장하지 못했습니다.");
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Card as="section" className="flex flex-col gap-3 text-sm">
        <div>
          <h2 className="font-semibold">배포 전 준비 (Production Readiness)</h2>
          <p className="mt-1 text-xs text-foreground-muted">
            곧 이 설계가 운영에 나가고 장애가 납니다. 지금 준비하지 않은 것은 장애 중에 쓸 수 없습니다 — 트레이싱을 끄면 트레이스가 없고, 구조화 로그를 끄면 로그를
            서비스·trace id로 거를 수 없습니다.
          </p>
        </div>
        <ul className="flex flex-col gap-1.5">
          {readiness?.items
            .filter((i) => i.auto)
            .map((i) => (
              <li key={i.key} className="flex items-center gap-2">
                <span className={i.checked ? "text-success" : "text-foreground-muted"}>{i.checked ? "✓" : "○"}</span>
                {i.label}
                <span className="text-xs text-foreground-muted">{i.checked ? "" : "— 아래에서 설정할 수 있습니다"}</span>
              </li>
            ))}
          <li>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={logging} onChange={(e) => setLogging(e.target.checked)} />
              구조화 로그 (서비스·trace id 필드)
            </label>
          </li>
          <li>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={tracing} onChange={(e) => setTracing(e.target.checked)} />
              분산 트레이싱 활성화
            </label>
          </li>
        </ul>
        {error && <p className="text-danger">{error}</p>}
        <Button onClick={deploy} disabled={busy} className="self-start">
          {busy ? "배포하는 중..." : "배포하고 인시던트 시작"}
        </Button>
      </Card>
      {/* PLAN.md Round E30 (M9) — only for scenarios whose incident carries a release change list */}
      <ChangeReviewCard sessionId={sessionId} />
      <AlertsView sessionId={sessionId} series={null} isOwner onInvestigate={() => undefined} />
    </div>
  );
}
