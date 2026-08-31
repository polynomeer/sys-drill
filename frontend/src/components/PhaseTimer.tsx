"use client";

import { useEffect, useRef, useState } from "react";

const TICK_MS = 1000;
const WARNING_THRESHOLD_SECONDS = 60;

function formatRemaining(seconds: number): string {
  const clamped = Math.max(0, seconds);
  const m = Math.floor(clamped / 60);
  const s = clamped % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

/** PLAN.md step 28 — a per-phase countdown for interview-timer mode sessions. Calls [onExpire] exactly once when the deadline passes; the caller decides what "expire" means (auto-submit). */
export function PhaseTimer({ deadlineAt, onExpire }: { deadlineAt: string; onExpire: () => void }) {
  const deadlineMs = new Date(deadlineAt).getTime();
  const [remainingSeconds, setRemainingSeconds] = useState(() => Math.round((deadlineMs - Date.now()) / 1000));
  const expiredRef = useRef(false);

  useEffect(() => {
    expiredRef.current = false;
    const timer = setInterval(() => {
      const remaining = Math.round((deadlineMs - Date.now()) / 1000);
      setRemainingSeconds(remaining);
      if (remaining <= 0 && !expiredRef.current) {
        expiredRef.current = true;
        clearInterval(timer);
        onExpire();
      }
    }, TICK_MS);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deadlineAt]);

  const isWarning = remainingSeconds <= WARNING_THRESHOLD_SECONDS;

  return (
    <div
      className={`flex items-center gap-2 rounded border px-3 py-1.5 text-sm font-mono ${
        isWarning
          ? "border-red-400 bg-red-50 text-red-700 dark:border-red-700 dark:bg-red-950 dark:text-red-300"
          : "border-zinc-300 text-zinc-600 dark:border-zinc-700 dark:text-zinc-400"
      }`}
    >
      <span aria-hidden>⏱</span>
      <span>남은 시간 {formatRemaining(remainingSeconds)}</span>
    </div>
  );
}
