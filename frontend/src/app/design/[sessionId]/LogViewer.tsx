"use client";

import { useEffect, useRef, useState } from "react";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";

export type LogLevel = "INFO" | "WARN" | "ERROR";
export type LogEntry = { time: Date; level: LogLevel; service: string; message: string; traceId?: string | null };
/** A time window to narrow to — set by an alert's "조사하기". */
export type LogWindow = { from: Date; to: Date; label: string };

const LEVEL_BADGE: Record<LogLevel, "neutral" | "warning" | "danger"> = {
  INFO: "neutral",
  WARN: "warning",
  ERROR: "danger",
};

const ALL_LEVELS: LogLevel[] = ["INFO", "WARN", "ERROR"];
const CONTEXT_LINES = 5;
const QUERY_REPORT_DELAY_MS = 1200;

/** Stable across polls — the entries array is rebuilt on every refresh, so identity can't be used. */
const entryKey = (e: LogEntry) => `${e.time.getTime()}|${e.service}|${e.traceId ?? ""}|${e.message}`;

/**
 * SysDrill_UIUX_Design_Plan.docx §7 Log Viewer — 시간/레벨/서비스/메시지 + 검색/필터/자동스크롤.
 * PLAN.md Round E17 (docs/OBSERVABILITY_UI_PLAN.md O4): entries are the server's generated logs
 * (`GET .../simulation/logs`), not client-side synthesis. Clicking a line shows ±5 lines of
 * context and a jump to the metrics at that moment; an alert can hand in a time window.
 */
export function LogViewer({
  entries,
  window: timeWindow,
  onClearWindow,
  onShowMetrics,
  onQuery,
}: {
  entries: LogEntry[];
  window?: LogWindow | null;
  onClearWindow?: () => void;
  onShowMetrics?: (at: Date) => void;
  /** O0-b — called once the learner stops typing a search. */
  onQuery?: (query: string) => void;
}) {
  const [query, setQuery] = useState("");
  const [activeLevels, setActiveLevels] = useState<Set<LogLevel>>(new Set(ALL_LEVELS));
  const [autoScroll, setAutoScroll] = useState(true);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const needle = query.trim().toLowerCase();
  const filtered = entries.filter(
    (e) =>
      activeLevels.has(e.level) &&
      (!timeWindow || (e.time >= timeWindow.from && e.time <= timeWindow.to)) &&
      (!needle ||
        e.message.toLowerCase().includes(needle) ||
        e.service.toLowerCase().includes(needle) ||
        (e.traceId ?? "").includes(needle)),
  );

  useEffect(() => {
    if (autoScroll && !timeWindow && listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [filtered.length, autoScroll, timeWindow]);

  useEffect(() => {
    if (!needle || !onQuery) return;
    const timer = setTimeout(() => onQuery(needle), QUERY_REPORT_DELAY_MS);
    return () => clearTimeout(timer);
  }, [needle, onQuery]);

  function toggleLevel(level: LogLevel) {
    setActiveLevels((prev) => {
      const next = new Set(prev);
      if (next.has(level)) next.delete(level);
      else next.add(level);
      return next;
    });
  }

  // Context comes from every line, not the filtered view — that's the point of looking around.
  const selectedIndex = selectedKey ? entries.findIndex((e) => entryKey(e) === selectedKey) : -1;
  const selected = selectedIndex >= 0 ? entries[selectedIndex] : null;
  const context = selectedIndex >= 0 ? entries.slice(Math.max(0, selectedIndex - CONTEXT_LINES), selectedIndex + CONTEXT_LINES + 1) : [];

  return (
    <section className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground-muted">로그</h2>
        <div className="flex flex-wrap items-center gap-2">
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="검색 (메시지·서비스·trace)" className="w-44 py-1 text-xs" />
          {ALL_LEVELS.map((level) => (
            <button key={level} onClick={() => toggleLevel(level)} className={`text-xs ${activeLevels.has(level) ? "" : "opacity-40"}`}>
              <Badge variant={LEVEL_BADGE[level]}>{level}</Badge>
            </button>
          ))}
          <label className="flex items-center gap-1 text-xs text-foreground-muted">
            <input type="checkbox" checked={autoScroll} onChange={(e) => setAutoScroll(e.target.checked)} />
            자동스크롤
          </label>
        </div>
      </div>
      {timeWindow && (
        <div className="flex items-center gap-2 text-xs text-foreground-muted">
          <Badge variant="neutral">{timeWindow.label}</Badge>
          {timeWindow.from.toLocaleTimeString()} ~ {timeWindow.to.toLocaleTimeString()}
          {onClearWindow && (
            <button type="button" className="underline" onClick={onClearWindow}>
              전체 보기
            </button>
          )}
        </div>
      )}
      <div ref={listRef} className="max-h-72 overflow-y-auto rounded-lg bg-background font-mono text-xs">
        {filtered.length === 0 && <p className="p-3 text-foreground-muted">표시할 로그가 없습니다.</p>}
        {filtered.map((entry, i) => (
          <button
            type="button"
            key={i}
            onClick={() => setSelectedKey(entry === selected ? null : entryKey(entry))}
            className={`flex w-full items-start gap-2 border-b border-border/50 px-3 py-1.5 text-left last:border-0 hover:bg-surface ${selected && entryKey(entry) === selectedKey ? "bg-surface" : ""}`}
          >
            <span className="shrink-0 text-foreground-muted">{entry.time.toLocaleTimeString()}</span>
            <Badge variant={LEVEL_BADGE[entry.level]} className="shrink-0">
              {entry.level}
            </Badge>
            <span className="shrink-0 text-foreground-muted">[{entry.service}]</span>
            <span className="min-w-0 break-words text-foreground">{entry.message}</span>
          </button>
        ))}
      </div>
      {selected && (
        <div className="flex flex-col gap-2 rounded-lg border border-border p-3 text-xs">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-foreground-muted">
              앞뒤 {CONTEXT_LINES}줄{selected.traceId && <> · trace <span className="font-mono">{selected.traceId}</span></>}
            </span>
            {onShowMetrics && (
              <Button size="sm" variant="ghost" onClick={() => onShowMetrics(selected.time)}>
                이 시각 지표 보기
              </Button>
            )}
          </div>
          <div className="font-mono">
            {context.map((entry, i) => (
              <div key={i} className={`flex gap-2 ${entry === selected ? "font-semibold text-foreground" : "text-foreground-muted"}`}>
                <span className="shrink-0">{entry.time.toLocaleTimeString()}</span>
                <span className="shrink-0">{entry.level}</span>
                <span className="shrink-0">[{entry.service}]</span>
                <span className="min-w-0 break-words">{entry.message}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
