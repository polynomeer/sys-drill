"use client";

import { useState } from "react";
import Link from "next/link";
import {
  type PuzzleActResult,
  type PuzzleDebrief,
  type PuzzleEvidence,
  type SimulationActionType,
  actOnPuzzle,
} from "@/lib/api";
import { actionLabel } from "@/lib/actionLabels";
import { formatDuration } from "@/lib/metrics";
import { SystemDiagram } from "@/components/SystemDiagram";
import { TimelineView } from "@/components/TimelineView";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Waterfall } from "@/app/design/[sessionId]/TracesView";

/**
 * docs/LEARNING_DEEPENING_PLAN.md L16 (PLAN.md Round E40) — the puzzle's investigation, action and
 * debrief steps. Evidence and curves come from the backend replaying the seed's scene.
 */

const LEVEL_CLASS: Record<string, string> = { ERROR: "text-danger", WARN: "text-warning", INFO: "text-foreground-muted" };

export function EvidenceView({ evidence }: { evidence: PuzzleEvidence }) {
  switch (evidence.kind) {
    case "logs":
      return (
        <ul className="max-h-72 overflow-y-auto rounded border border-border bg-background p-2 font-mono text-[11px] leading-relaxed">
          {(evidence.logs ?? []).map((l, i) => (
            <li key={i} className="break-words">
              <span className={LEVEL_CLASS[l.level] ?? ""}>{l.level.padEnd(5)}</span> <span className="text-foreground-muted">[{l.service}]</span> {l.message}
            </li>
          ))}
        </ul>
      );
    case "trace": {
      const spans = evidence.spans ?? [];
      const root = spans[0];
      if (!root) return null;
      return <Waterfall trace={{ traceId: "지금 가장 느린 요청", source: "SYNTHETIC", at: "", durationMs: root.durationMs, spans }} />;
    }
    case "components":
      return evidence.state && evidence.domain ? <SystemDiagram domain={evidence.domain} state={evidence.state} caption="장애가 시작된 뒤 지금 시점의 구성 요소별 상태" /> : null;
    case "changes":
      return (
        <ul className="flex flex-col gap-1.5 text-sm">
          {(evidence.changes ?? []).map((c) => (
            <li key={c.text} className="flex gap-2">
              <span className="w-20 shrink-0 font-mono text-xs text-foreground-muted">{changeWhen(c.minutesBefore)}</span>
              <span>{c.text}</span>
            </li>
          ))}
        </ul>
      );
    default:
      return null;
  }
}

function changeWhen(minutes: number): string {
  if (minutes === 0) return "시작 시점";
  if (minutes < 60) return `${minutes}분 전`;
  if (minutes < 1440) return `${Math.round(minutes / 60)}시간 전`;
  return `${Math.round(minutes / 1440)}일 전`;
}

const VERDICT: Record<PuzzleActResult["verdict"], { text: string; className: string; note: string }> = {
  RECOVERED: { text: "회복", className: "text-success", note: "에러율이 1% 아래로 돌아왔습니다." },
  PARTIAL: { text: "일부 개선", className: "text-warning", note: "나빠지는 것은 막았지만 아직 장애 중입니다." },
  NO_EFFECT: { text: "효과 없음", className: "text-danger", note: "사용자가 겪는 지표는 아무것도 하지 않았을 때와 같습니다." },
};

/** Up to three actions in order, applied every 30 s from the moment asked about. */
export function ActionStep({ seed, actions, onResult }: { seed: number; actions: SimulationActionType[]; onResult: (r: PuzzleActResult) => void }) {
  const [plan, setPlan] = useState<SimulationActionType[]>([]);
  const [result, setResult] = useState<PuzzleActResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function apply() {
    setBusy(true);
    setError(null);
    try {
      const r = await actOnPuzzle(seed, plan);
      setResult(r);
      onResult(r);
    } catch {
      setError("조치를 적용하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card as="section" className="flex flex-col gap-3">
      <div>
        <h2 className="font-semibold">4. 조치 — 무엇을 어떤 순서로?</h2>
        <p className="mt-1 text-xs text-foreground-muted">
          최대 3개, 지금 시점부터 30초 간격으로 적용합니다(같은 조치를 두 번 골라도 됩니다). 시뮬레이션 엔진이 회복 곡선을 계산합니다.
        </p>
      </div>
      <div className="flex flex-wrap gap-1.5">
        {actions.map((a) => (
          <Button key={a} size="sm" variant="secondary" disabled={!!result || plan.length >= 3} onClick={() => setPlan((p) => [...p, a])}>
            + {actionLabel(a)}
          </Button>
        ))}
      </div>
      <ol className="flex min-h-8 flex-wrap items-center gap-1.5 text-xs">
        {plan.length === 0 && <li className="text-foreground-muted">고른 조치가 여기에 순서대로 쌓입니다.</li>}
        {plan.map((a, i) => (
          <li key={i} className="flex items-center gap-1.5">
            {i > 0 && <span className="text-foreground-muted" aria-hidden>→</span>}
            <span className="rounded-full border border-accent px-2 py-0.5">{actionLabel(a)}</span>
          </li>
        ))}
      </ol>
      {!result && (
        <div className="flex gap-2">
          <Button size="sm" onClick={apply} disabled={plan.length === 0 || busy}>
            {busy ? "계산하는 중…" : "적용하고 지켜보기"}
          </Button>
          {plan.length > 0 && (
            <Button size="sm" variant="secondary" onClick={() => setPlan([])}>
              다시 고르기
            </Button>
          )}
        </div>
      )}
      {error && <p className="text-xs text-danger">{error}</p>}
      {result && (
        <div className="flex flex-col gap-3">
          <p className="text-sm">
            <span className={`font-semibold ${VERDICT[result.verdict].className}`}>{VERDICT[result.verdict].text}</span>{" "}
            <span className="text-foreground-muted">— {VERDICT[result.verdict].note}</span>
          </p>
          {result.badFixWhy && (
            <p className="rounded-lg border border-danger/40 p-2 text-sm">
              <span className="font-medium">이 장애의 &lsquo;그럴듯하지만 틀린 대응&rsquo;입니다.</span> <span className="text-foreground-muted">{result.badFixWhy}</span>
            </p>
          )}
          <p className="text-xs text-foreground-muted">
            장애 패턴의 완화 순서: {result.recommended.map(actionLabel).join(" → ")}
          </p>
          <TimelineView block={result.timeline} toneLabels={{ bad: "회복 못 함", good: "회복" }} />
        </div>
      )}
    </Card>
  );
}

/** After answering: which alert went off first, then where to read and practise next. */
export function DebriefLinks({ domain, debrief }: { domain: string; debrief: PuzzleDebrief }) {
  const fired = debrief.signals.filter((s) => s.second !== null);
  return (
    <div className="flex flex-col gap-2 text-sm">
      {fired.length > 0 && (
        <div>
          <p className="text-xs text-foreground-muted">경보가 울린 순서 (차트에 표시)</p>
          <ol className="mt-1 flex flex-wrap items-center gap-1.5 text-xs">
            {fired.map((s, i) => (
              <li key={s.label} className="flex items-center gap-1.5">
                {i > 0 && <span className="text-foreground-muted" aria-hidden>→</span>}
                <span className="rounded-full border border-warning px-2 py-0.5">
                  <span className="font-mono">+{formatDuration(s.second!)}</span> {s.label}
                </span>
              </li>
            ))}
          </ol>
        </div>
      )}
      {debrief.relatedConcepts.length > 0 && (
        <div className="flex flex-wrap gap-1.5 text-xs">
          {debrief.relatedConcepts.map((c) => (
            <Link key={c.riskKey} href={`/learning/${c.riskKey}`} className="rounded-full border border-border px-2 py-0.5 hover:border-accent">
              개념: {c.label}
            </Link>
          ))}
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        <Button href={`/learning/failures/${domain}`} size="sm" variant="secondary">
          장애 패턴 — 전파 경로·타임라인 →
        </Button>
        <Button href={`/learning/guides/${domain}`} size="sm" variant="secondary">
          설계 가이드 →
        </Button>
        {debrief.lab && (
          <Button href={`/learning/labs/${debrief.lab}`} size="sm" variant="secondary">
            랩에서 손잡이로 →
          </Button>
        )}
      </div>
    </div>
  );
}
