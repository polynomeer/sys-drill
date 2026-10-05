"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  type DiagnosticPuzzle,
  type PuzzleActResult,
  type PuzzleDebrief,
  type PuzzleEvidence,
  type PuzzlePoint,
  type PuzzleResult,
  type SystemState,
  answerPuzzle,
  getPuzzle,
  getPuzzleDebrief,
  getPuzzleEvidence,
} from "@/lib/api";
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { Choices, PUZZLE_METRIC_KEYS, PuzzleCharts } from "@/components/PuzzleCharts";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";
import { ActionStep, DebriefLinks, EvidenceView } from "./PuzzleSteps";

/**
 * docs/LEARNING_EXPANSION_PLAN.md L9 (PLAN.md Round E28) — five minutes, no login: metrics from an
 * incident in progress, then "what is it?" and "what would you check first?". Not recorded —
 * mastery comes from Drills only.
 *
 * docs/LEARNING_DEEPENING_PLAN.md L16 (PLAN.md Round E40) — now a short incident response:
 * observe → investigate (evidence costs budget) → diagnose → act (recovery curve) → debrief.
 * Difficulty sets the budget and how many charts are hidden; streak and per-domain accuracy stay in
 * this browser only (nothing is sent — §3 of the plan).
 */

type Difficulty = "easy" | "normal" | "hard";

const DIFFICULTY: Record<Difficulty, { label: string; budget: number | null; hidden: number; note: string }> = {
  easy: { label: "쉬움", budget: null, hidden: 0, note: "조사 무제한 · 힌트" },
  normal: { label: "보통", budget: 5, hidden: 0, note: "조사 예산 5" },
  hard: { label: "어려움", budget: 3, hidden: 3, note: "조사 예산 3 · 지표 3개 숨김" },
};

const DOMAINS = ["coupon", "notification", "product-browsing", "payment", "reservation", "batch-settlement", "autoscaling", "deployment"];

/** Engine metric → the puzzle chart showing it (for "moved first" markers). */
const METRIC_TO_POINT: Partial<Record<keyof SystemState, keyof PuzzlePoint>> = {
  trafficRps: "trafficRps",
  p95LatencyMs: "p95LatencyMs",
  errorRate: "errorRatePct",
  dbReadLoad: "dbReadLoadPct",
  dbWriteLoad: "dbWriteLoadPct",
  connectionPoolUsage: "poolUsagePct",
  cacheHitRatio: "cacheHitPct",
  cacheLatencyMs: "cacheLatencyMs",
  queueLag: "queueLag",
  externalDependencyLatencyMs: "externalLatencyMs",
};

type Stats = { streak: number; best: number; byDomain: Record<string, { right: number; total: number }> };
const STATS_KEY = "sysdrill:puzzle-stats";

function loadStats(): Stats {
  try {
    const raw = window.localStorage.getItem(STATS_KEY);
    if (raw) return JSON.parse(raw) as Stats;
  } catch {
    // Private mode or blocked storage — stats just start empty.
  }
  return { streak: 0, best: 0, byDomain: {} };
}

function saveStats(stats: Stats) {
  try {
    window.localStorage.setItem(STATS_KEY, JSON.stringify(stats));
  } catch {
    // Not saved; the page works the same.
  }
}

/** Hard mode hides three charts — picked by the seed so the same puzzle hides the same ones. */
function hiddenFor(seed: number, count: number): (keyof PuzzlePoint)[] {
  const pool = PUZZLE_METRIC_KEYS.filter((k) => k !== "trafficRps" && k !== "errorRatePct");
  const picked: (keyof PuzzlePoint)[] = [];
  let x = Math.abs(seed) % 2147483647 || 1;
  while (picked.length < count && picked.length < pool.length) {
    x = (x * 48271) % 2147483647;
    const k = pool[x % pool.length];
    if (!picked.includes(k)) picked.push(k);
  }
  return picked;
}

/** Easy-mode hint: the charts that moved most between the first and the last point. */
function biggestMovers(points: PuzzlePoint[]): string[] {
  const first = points[0];
  const last = points.at(-1);
  if (!first || !last) return [];
  const labels: Partial<Record<keyof PuzzlePoint, string>> = {
    p95LatencyMs: "P95 지연", errorRatePct: "에러율", dbReadLoadPct: "DB 읽기 부하", dbWriteLoadPct: "DB 쓰기 부하", poolUsagePct: "커넥션 풀",
    cacheHitPct: "캐시 적중률", cacheLatencyMs: "캐시 지연", queueLag: "큐 적체", externalLatencyMs: "외부 의존성 지연",
  };
  return (Object.keys(labels) as (keyof PuzzlePoint)[])
    .map((k) => {
      const a = Number(first[k]);
      const b = Number(last[k]);
      return { k, change: Math.abs(b - a) / Math.max(Math.abs(a), 1) };
    })
    .filter((x) => x.change > 0.2)
    .sort((x, y) => y.change - x.change)
    .slice(0, 2)
    .map((x) => labels[x.k]!);
}

export default function PuzzlePage() {
  const [difficulty, setDifficulty] = useState<Difficulty>("normal");
  const [domainFilter, setDomainFilter] = useState("");
  const [puzzle, setPuzzle] = useState<DiagnosticPuzzle | null>(null);
  const [evidence, setEvidence] = useState<PuzzleEvidence[]>([]);
  const [spent, setSpent] = useState(0);
  const [hint, setHint] = useState(false);
  const [pattern, setPattern] = useState<string | null>(null);
  const [check, setCheck] = useState<string | null>(null);
  const [result, setResult] = useState<PuzzleResult | null>(null);
  const [debrief, setDebrief] = useState<PuzzleDebrief | null>(null);
  const [acted, setActed] = useState<PuzzleActResult | null>(null);
  const [stats, setStats] = useState<Stats>({ streak: 0, best: 0, byDomain: {} });
  const [error, setError] = useState<string | null>(null);

  const next = useCallback((domain: string) => {
    setPuzzle(null);
    setEvidence([]);
    setSpent(0);
    setHint(false);
    setPattern(null);
    setCheck(null);
    setResult(null);
    setDebrief(null);
    setActed(null);
    getPuzzle(undefined, domain || undefined)
      .then(setPuzzle)
      .catch(() => setError("퍼즐을 불러오지 못했습니다."));
  }, []);

  useEffect(() => {
    // Fetch on mount; stats live in this browser only.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setStats(loadStats());
    next("");
  }, [next]);

  const config = DIFFICULTY[difficulty];
  const remaining = config.budget === null ? null : config.budget - spent;

  async function investigate(key: string, cost: number) {
    if (!puzzle || evidence.some((e) => e.kind === key)) return;
    if (remaining !== null && cost > remaining) return;
    try {
      const found = await getPuzzleEvidence(puzzle.seed, key);
      setEvidence((list) => [...list, found]);
      setSpent((s) => s + cost);
    } catch {
      setError("증거를 불러오지 못했습니다.");
    }
  }

  async function submit() {
    if (!puzzle) return;
    const graded = await answerPuzzle(puzzle.seed, { pattern: pattern ?? undefined, check: check ?? undefined });
    setResult(graded);
    getPuzzleDebrief(puzzle.seed).then(setDebrief).catch(() => setDebrief(null));
    const right = graded.patternCorrect === true;
    const d = stats.byDomain[graded.answerPattern] ?? { right: 0, total: 0 };
    const streak = right ? stats.streak + 1 : 0;
    const updated: Stats = {
      streak,
      best: Math.max(stats.best, streak),
      byDomain: { ...stats.byDomain, [graded.answerPattern]: { right: d.right + (right ? 1 : 0), total: d.total + 1 } },
    };
    setStats(updated);
    saveStats(updated);
  }

  // Score: budget spent costs points (not in easy), wrong answers and an action that didn't work cost more.
  const score = result
    ? Math.max(
        0,
        100 -
          (config.budget === null ? 0 : spent * 8) -
          (result.patternCorrect === false || result.patternCorrect === null ? 40 : 0) -
          (result.checkCorrect === false || result.checkCorrect === null ? 20 : 0) -
          (acted ? { RECOVERED: 0, PARTIAL: 10, NO_EFFECT: 25 }[acted.verdict] : 0),
      )
    : null;

  const hidden = puzzle && !result ? hiddenFor(puzzle.seed, config.hidden) : [];
  const markers: Partial<Record<keyof PuzzlePoint, { second: number; rank: number }>> = {};
  debrief?.signals
    .filter((s) => s.second !== null)
    .forEach((s, i) => {
      const key = METRIC_TO_POINT[s.metric];
      if (key && !markers[key]) markers[key] = { second: s.second!, rank: i + 1 };
    });
  const accuracy = Object.values(stats.byDomain).reduce((a, d) => ({ right: a.right + d.right, total: a.total + d.total }), { right: 0, total: 0 });

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 p-4 sm:p-8">
      <div>
        <Link href="/learning" className="text-sm text-foreground-muted hover:text-foreground">
          ← Learning
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">진단 퍼즐</h1>
        <p className="mt-1 max-w-[72ch] text-sm text-foreground-muted">
          운영 중인 시스템에서 장애가 시작됐습니다(점선 = 시작 시점). 지표를 보고, 필요하면 증거를 더 열어 본 뒤, 무슨 일인지 진단하고 조치까지 해 보세요. 기록되지 않습니다.
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
        <div className="flex gap-1" role="group" aria-label="난이도">
          {(Object.keys(DIFFICULTY) as Difficulty[]).map((d) => (
            <Button key={d} size="sm" variant={d === difficulty ? "primary" : "secondary"} onClick={() => setDifficulty(d)} disabled={!!result || spent > 0}>
              {DIFFICULTY[d].label}
            </Button>
          ))}
        </div>
        <span className="text-xs text-foreground-muted">{config.note}</span>
        <label className="flex items-center gap-2 text-xs">
          도메인
          <select
            className="rounded border border-border bg-surface px-2 py-1 text-sm"
            value={domainFilter}
            onChange={(e) => {
              setDomainFilter(e.target.value);
              next(e.target.value);
            }}
          >
            <option value="">무작위</option>
            {DOMAINS.map((d) => (
              <option key={d} value={d}>
                {DOMAIN_TITLES[d] ?? d}
              </option>
            ))}
          </select>
        </label>
        <span className="ml-auto text-xs text-foreground-muted">
          연속 정답 {stats.streak} · 최고 {stats.best}
          {accuracy.total > 0 && ` · 적중 ${accuracy.right}/${accuracy.total}`}
        </span>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}
      {!puzzle && !error && <LoadingState />}
      {puzzle && (
        <div className="flex flex-col gap-4 lg:grid lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start lg:gap-6">
          <div className="flex min-w-0 flex-col gap-4">
            <section className="flex flex-col gap-2">
              <h2 className="text-sm font-semibold text-foreground-muted">1. 관찰{result && debrief && " — 노란 테두리는 경보가 울린 순서"}</h2>
              <PuzzleCharts points={puzzle.points} hidden={hidden} markers={result ? markers : {}} />
              {difficulty === "easy" && !result && (
                <div className="text-xs">
                  {hint ? (
                    <p className="text-foreground-muted">힌트: 가장 크게 움직인 지표는 {biggestMovers(puzzle.points).join(", ") || "—"}입니다. 그 지표를 만드는 구성 요소가 어디일까요?</p>
                  ) : (
                    <button type="button" className="text-accent hover:underline" onClick={() => setHint(true)}>
                      힌트 보기
                    </button>
                  )}
                </div>
              )}
            </section>

            {(puzzle.investigations?.length ?? 0) > 0 && (
              <Card as="section" className="flex flex-col gap-3">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <h2 className="font-semibold">2. 조사 — 증거를 더 열어 볼까요?</h2>
                  <span className="text-xs text-foreground-muted">{remaining === null ? "예산 무제한" : `남은 예산 ${remaining}`}</span>
                </div>
                <p className="text-xs text-foreground-muted">조사할수록 맞히기 쉬워지지만 점수에서 예산 1당 8점이 빠집니다(쉬움은 제외). 컴포넌트 상태는 시스템 구조를 그대로 보여 줍니다.</p>
                <div className="flex flex-wrap gap-1.5">
                  {puzzle.investigations!.map((inv) => {
                    const opened = evidence.some((e) => e.kind === inv.key);
                    const affordable = remaining === null || inv.cost <= remaining;
                    return (
                      <Button key={inv.key} size="sm" variant="secondary" disabled={opened || !affordable || !!result} onClick={() => investigate(inv.key, inv.cost)}>
                        {inv.label} <span className="text-foreground-muted">({opened ? "열림" : `-${inv.cost}`})</span>
                      </Button>
                    );
                  })}
                </div>
                {evidence.map((e) => (
                  <div key={e.kind} className="flex flex-col gap-1">
                    <p className="text-xs font-semibold text-foreground-muted">{puzzle.investigations!.find((i) => i.key === e.kind)?.label}</p>
                    <EvidenceView evidence={e} />
                  </div>
                ))}
              </Card>
            )}

            {result && debrief && <ActionStep key={puzzle.seed} seed={puzzle.seed} actions={debrief.actions} onResult={setActed} />}
          </div>

          <Card as="section" className="flex min-w-0 flex-col gap-3 text-sm lg:sticky lg:top-6">
            <h2 className="font-semibold">3. 진단 — 무슨 일이 일어나고 있나요?</h2>
            <Choices options={puzzle.patterns} value={pattern} onChange={setPattern} disabled={!!result} result={result ? [result.answerPattern] : null} />
            <h2 className="font-semibold">가장 먼저 무엇을 확인하겠습니까?</h2>
            <Choices options={puzzle.checks} value={check} onChange={setCheck} disabled={!!result} result={result?.acceptedChecks ?? null} />
            {!result ? (
              <Button onClick={submit} disabled={!pattern && !check} className="self-start">
                답 확인
              </Button>
            ) : (
              <div className="flex flex-col gap-3 rounded-lg border border-border p-3">
                <p className="font-medium">
                  {result.patternCorrect ? "✓ 맞혔습니다" : "✗ 아닙니다"} — {result.answerPatternName}
                </p>
                <p className="text-foreground-muted">{result.explanation}</p>
                {score !== null && (
                  <p className="text-xs text-foreground-muted">
                    점수 <span className="font-mono text-base text-foreground">{score}</span>
                    {acted ? "" : " · 왼쪽에서 조치까지 해 보면 점수가 확정됩니다"}
                  </p>
                )}
                {debrief && <DebriefLinks domain={result.answerPattern} debrief={debrief} />}
                <Button size="sm" onClick={() => next(domainFilter)} className="self-start">
                  다음 퍼즐
                </Button>
              </div>
            )}
          </Card>
        </div>
      )}
    </div>
  );
}
