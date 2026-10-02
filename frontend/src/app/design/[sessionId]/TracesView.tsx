"use client";

import { useEffect, useState } from "react";
import { type TraceList, type TraceView, getTrace, getTraces } from "@/lib/api";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";

/**
 * docs/OBSERVABILITY_UI_PLAN.md O6 (PLAN.md Round E25) — recent traces and one waterfall.
 * [selected] comes from outside too: a log line's trace id opens its trace here.
 */
export function TracesView({
  sessionId,
  selected,
  onSelect,
  onShowService,
}: {
  sessionId: string;
  selected: string | null;
  onSelect: (traceId: string) => void;
  /** A span's service → the Service Map (where that component lives). */
  onShowService?: (service: string) => void;
}) {
  const [list, setList] = useState<TraceList | null>(null);
  const [trace, setTrace] = useState<TraceView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getTraces(sessionId).then(setList).catch(() => setError("트레이스를 불러오지 못했습니다."));
  }, [sessionId]);

  useEffect(() => {
    if (!selected) return;
    getTrace(sessionId, selected)
      .then(setTrace)
      .catch(() => setTrace(null));
  }, [sessionId, selected]);

  if (error) return <p className="text-sm text-danger">{error}</p>;
  if (!list) return null;
  if (!list.available) {
    return (
      <Card as="section" className="text-sm text-foreground-muted">
        No data — {list.note ?? "인시던트가 시작되면 요청 트레이스가 여기에 보입니다."}
      </Card>
    );
  }

  return (
    <div className="grid gap-4 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
      <Card as="section" className="min-w-0 text-sm">
        <div className="mb-2 flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-foreground-muted">최근 트레이스</h2>
          <Badge variant="neutral">{list.source === "JAEGER" ? "Jaeger 실측" : "합성"}</Badge>
        </div>
        {list.note && <p className="mb-2 text-xs text-foreground-muted">{list.note}</p>}
        <ul className="flex max-h-80 flex-col overflow-y-auto font-mono text-xs">
          {list.traces.length === 0 && <li className="text-foreground-muted">아직 트레이스가 없습니다.</li>}
          {list.traces.map((t) => (
            <li key={t.traceId}>
              <button
                type="button"
                onClick={() => onSelect(t.traceId)}
                className={`flex w-full items-center gap-2 rounded px-2 py-1 text-left hover:bg-surface ${selected === t.traceId ? "bg-surface" : ""}`}
              >
                <span className={t.error ? "text-danger" : "text-foreground-muted"}>{t.error ? "●" : "○"}</span>
                <span className="shrink-0 text-foreground-muted">{new Date(t.at).toLocaleTimeString()}</span>
                <span className="min-w-0 flex-1 truncate">{t.rootName}</span>
                <span className="shrink-0">{t.durationMs}ms</span>
              </button>
            </li>
          ))}
        </ul>
      </Card>
      <Card as="section" className="min-w-0 text-sm">
        {!trace ? (
          <p className="text-foreground-muted">트레이스를 고르면 구간별 소요 시간이 보입니다. 로그의 trace id에서도 열 수 있습니다.</p>
        ) : (
          <Waterfall trace={trace} onShowService={onShowService} />
        )}
      </Card>
    </div>
  );
}

function Waterfall({ trace, onShowService }: { trace: TraceView; onShowService?: (service: string) => void }) {
  const total = Math.max(1, trace.durationMs);
  const depth = new Map<string, number>();
  trace.spans.forEach((s) => depth.set(s.spanId, s.parentSpanId ? (depth.get(s.parentSpanId) ?? 0) + 1 : 0));
  const slowest = trace.spans.slice(1).reduce<TraceView["spans"][number] | null>((a, b) => (!a || b.durationMs > a.durationMs ? b : a), null);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-baseline justify-between gap-2 text-xs text-foreground-muted">
        <span className="font-mono">{trace.traceId}</span>
        <span>
          총 {trace.durationMs}ms{slowest && ` · 가장 긴 구간: ${slowest.name} (${Math.round((slowest.durationMs / total) * 100)}%)`}
        </span>
      </div>
      <ul className="flex flex-col gap-1">
        {trace.spans.map((s) => (
          <li key={s.spanId} className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] items-center gap-2 text-xs">
            <span className="min-w-0 truncate" style={{ paddingLeft: `${(depth.get(s.spanId) ?? 0) * 12}px` }} title={`${s.service} · ${s.name}`}>
              {onShowService ? (
                <button type="button" className="text-foreground-muted underline-offset-2 hover:underline" onClick={() => onShowService(s.service)}>
                  {s.service}
                </button>
              ) : (
                <span className="text-foreground-muted">{s.service}</span>
              )}{" "}
              {s.name}
            </span>
            <span className="relative h-4 rounded bg-background">
              <span
                className={`absolute top-0 h-4 rounded ${s.error ? "bg-danger" : "bg-accent"}`}
                style={{ left: `${(s.startMs / total) * 100}%`, width: `${Math.max(0.5, (s.durationMs / total) * 100)}%` }}
              />
              <span className="absolute right-1 top-0 text-[10px] leading-4 text-foreground">{s.durationMs}ms</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
