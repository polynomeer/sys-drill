"use client";

import { useEffect, useRef, useState } from "react";
import { Badge } from "@/components/ui/Badge";
import { Input } from "@/components/ui/Input";

export type LogLevel = "INFO" | "WARN" | "ERROR";
export type LogEntry = { time: Date; level: LogLevel; service: string; message: string };

const LEVEL_BADGE: Record<LogLevel, "neutral" | "warning" | "danger"> = {
  INFO: "neutral",
  WARN: "warning",
  ERROR: "danger",
};

const ALL_LEVELS: LogLevel[] = ["INFO", "WARN", "ERROR"];

/**
 * SysDrill_UIUX_Design_Plan.docx §7 Log Viewer — 시간/레벨/서비스/메시지 +
 * 검색/필터/자동스크롤. No backend schema change: entries are handed in by
 * the caller, which derives `level`/`service` client-side (see
 * WargameLive.tsx) rather than the backend persisting a real structured log.
 */
export function LogViewer({ entries }: { entries: LogEntry[] }) {
  const [query, setQuery] = useState("");
  const [activeLevels, setActiveLevels] = useState<Set<LogLevel>>(new Set(ALL_LEVELS));
  const [autoScroll, setAutoScroll] = useState(true);
  const listRef = useRef<HTMLDivElement>(null);

  const filtered = entries.filter(
    (e) => activeLevels.has(e.level) && (!query.trim() || e.message.toLowerCase().includes(query.trim().toLowerCase())),
  );

  useEffect(() => {
    if (autoScroll && listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [filtered.length, autoScroll]);

  function toggleLevel(level: LogLevel) {
    setActiveLevels((prev) => {
      const next = new Set(prev);
      if (next.has(level)) next.delete(level);
      else next.add(level);
      return next;
    });
  }

  return (
    <section className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground-muted">로그</h2>
        <div className="flex flex-wrap items-center gap-2">
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="검색..." className="w-32 py-1 text-xs" />
          {ALL_LEVELS.map((level) => (
            <button
              key={level}
              onClick={() => toggleLevel(level)}
              className={`text-xs ${activeLevels.has(level) ? "" : "opacity-40"}`}
            >
              <Badge variant={LEVEL_BADGE[level]}>{level}</Badge>
            </button>
          ))}
          <label className="flex items-center gap-1 text-xs text-foreground-muted">
            <input type="checkbox" checked={autoScroll} onChange={(e) => setAutoScroll(e.target.checked)} />
            자동스크롤
          </label>
        </div>
      </div>
      <div ref={listRef} className="max-h-48 overflow-y-auto rounded-lg bg-background font-mono text-xs">
        {filtered.length === 0 && <p className="p-3 text-foreground-muted">표시할 로그가 없습니다.</p>}
        {filtered.map((entry, i) => (
          <div key={i} className="flex items-start gap-2 border-b border-border/50 px-3 py-1.5 last:border-0">
            <span className="shrink-0 text-foreground-muted">{entry.time.toLocaleTimeString()}</span>
            <Badge variant={LEVEL_BADGE[entry.level]} className="shrink-0">
              {entry.level}
            </Badge>
            <span className="shrink-0 text-foreground-muted">[{entry.service}]</span>
            <span className="text-foreground">{entry.message}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
