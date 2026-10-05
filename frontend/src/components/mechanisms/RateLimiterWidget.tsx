"use client";

import { useMemo, useState } from "react";
import {
  type Algorithm,
  BOUNDARY_BURST,
  RATE_LIMITER_SCENARIOS,
  type RateDecision,
  type RateRequest,
  STAGE1,
  STAGE2,
  STAGE3,
  STAGE4,
  STAGE6,
  maxAllowedWithin,
  raceAllowed,
  sharedStoreAllowed,
  simulate,
} from "@/lib/mechanisms/rateLimiter";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

/**
 * docs/LEARNING_DEEPENING_PLAN.md L17 (PLAN.md Round E41, ADR-0055) — the Rate Limiter mechanism,
 * computed in the browser on a virtual clock. The burst lab is free play; the stage scenes replay
 * the Build challenge's own stage values (checked by `npm run check:mechanisms`).
 */

const ALGORITHMS: { key: Algorithm; label: string; note: string }[] = [
  { key: "fixed-window", label: "고정 윈도우", note: "키의 첫 요청에서 윈도우가 열리고, 윈도우가 끝나면 카운터가 사라진다 (Build 과제의 구현)" },
  { key: "sliding-log", label: "슬라이딩 로그", note: "지난 윈도우 길이 안에 통과한 요청 시각을 모두 기억한다 — 정확하지만 요청마다 기록이 남는다" },
  { key: "token-bucket", label: "토큰 버킷", note: "용량만큼 토큰을 담고 윈도우마다 용량만큼 채운다 — 순간 burst를 용량으로 묶는다" },
];

const PRESETS: { label: string; requests: number[] }[] = [
  { label: "경계에 몰림", requests: BOUNDARY_BURST.requests.map((r) => r.t) },
  { label: "고르게", requests: [1, 3, 5, 7, 9, 11, 13, 15, 17, 19] },
  { label: "한꺼번에", requests: [5, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7] },
];

const SPAN = 20; // seconds shown on the burst lab's axis

export function RateLimiterWidget() {
  return (
    <div className="flex flex-col gap-5">
      <BurstLab />
      <StageScenes />
    </div>
  );
}

// ---- 경계 burst: 같은 요청을 세 알고리즘에 ----

function BurstLab() {
  const [capacity, setCapacity] = useState(BOUNDARY_BURST.capacity);
  const [windowSeconds, setWindowSeconds] = useState(BOUNDARY_BURST.windowSeconds);
  const [times, setTimes] = useState<number[]>(PRESETS[0].requests);
  const requests: RateRequest[] = times.map((t) => ({ t, key: "user-a" }));

  function addAt(event: React.MouseEvent<SVGSVGElement>) {
    const box = event.currentTarget.getBoundingClientRect();
    const t = Math.round(((event.clientX - box.left) / box.width) * SPAN * 10) / 10;
    if (t >= 0 && t <= SPAN && times.length < 30) setTimes((list) => [...list, t].sort((a, b) => a - b));
  }

  return (
    <Card as="section" className="flex flex-col gap-4">
      <div>
        <h2 className="font-semibold">경계 구간 burst — 같은 요청, 세 가지 알고리즘</h2>
        <p className="mt-1 text-sm text-foreground-muted">
          한 사용자의 요청을 시간 축에 놓고 알고리즘마다 무엇이 통과하는지 봅니다. 축을 누르면 그 시각에 요청이 추가됩니다. 기본값은 Build 1단계와 같은 용량 {BOUNDARY_BURST.capacity} / 윈도우 {BOUNDARY_BURST.windowSeconds}초입니다.
        </p>
      </div>
      <div className="flex flex-wrap items-end gap-4 text-sm">
        <label className="flex flex-col gap-1">
          <span className="text-xs text-foreground-muted">용량 {capacity}</span>
          <input type="range" min={1} max={10} value={capacity} onChange={(e) => setCapacity(Number(e.target.value))} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs text-foreground-muted">윈도우 {windowSeconds}초</span>
          <input type="range" min={2} max={20} value={windowSeconds} onChange={(e) => setWindowSeconds(Number(e.target.value))} />
        </label>
        <div className="flex flex-wrap gap-1.5">
          {PRESETS.map((p) => (
            <Button key={p.label} size="sm" variant="secondary" onClick={() => setTimes(p.requests)}>
              {p.label}
            </Button>
          ))}
          <Button size="sm" variant="secondary" onClick={() => setTimes([])}>
            비우기
          </Button>
        </div>
      </div>
      <div className="flex flex-col gap-3">
        {ALGORITHMS.map((a) => (
          <Lane key={a.key} algorithm={a.key} label={a.label} note={a.note} decisions={simulate(a.key, capacity, windowSeconds, requests)} windowSeconds={windowSeconds} capacity={capacity} onAdd={addAt} />
        ))}
      </div>
      <p className="text-xs text-foreground-muted">
        &ldquo;1초 안 최대&rdquo;는 뒤의 DB가 실제로 받는 순간 부하입니다. 고정 윈도우는 윈도우가 바뀌는 순간 앞뒤로 용량의 거의 두 배까지 통과시킵니다 — Build 1단계의 학습 포인트입니다.
      </p>
    </Card>
  );
}

function Lane({
  algorithm,
  label,
  note,
  decisions,
  windowSeconds,
  capacity,
  onAdd,
}: {
  algorithm: Algorithm;
  label: string;
  note: string;
  decisions: RateDecision[];
  windowSeconds: number;
  capacity: number;
  onAdd: (e: React.MouseEvent<SVGSVGElement>) => void;
}) {
  const allowed = decisions.filter((d) => d.allowed).length;
  const burst = maxAllowedWithin(decisions, 1);
  const windows = useMemo(
    () => (algorithm === "fixed-window" ? [...new Set(decisions.map((d) => d.windowStart!))] : []),
    [algorithm, decisions],
  );
  const x = (t: number) => (t / SPAN) * 1000;
  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
        <span className="font-medium">{label}</span>
        <span className="text-xs text-foreground-muted">
          통과 {allowed}/{decisions.length} · 1초 안 최대 <span className={burst > capacity ? "font-semibold text-danger" : "font-semibold"}>{burst}</span>
        </span>
      </div>
      <svg viewBox="0 0 1000 56" className="h-14 w-full cursor-crosshair rounded border border-border bg-background" onClick={onAdd} role="img" aria-label={`${label}: ${decisions.length}개 중 ${allowed}개 통과, 1초 안 최대 ${burst}개`}>
        {windows.map((start) => (
          <rect key={start} x={x(start)} y={4} width={x(Math.min(SPAN, start + windowSeconds)) - x(start)} height={48} fill="var(--accent)" opacity={0.08} stroke="var(--accent)" strokeOpacity={0.3} />
        ))}
        {Array.from({ length: SPAN / 5 + 1 }, (_, i) => i * 5).map((t) => (
          <g key={t}>
            <line x1={x(t)} x2={x(t)} y1={0} y2={56} stroke="var(--border)" />
            <text x={x(t) + 3} y={54} fontSize={10} fill="var(--foreground-muted)">
              {t}s
            </text>
          </g>
        ))}
        {decisions.map((d, i) => (
          <circle key={i} cx={x(d.t)} cy={22} r={7} fill={d.allowed ? "var(--success)" : "var(--danger)"} opacity={0.85}>
            <title>{`${d.t}s ${d.allowed ? "통과" : "거절"} (${algorithm === "token-bucket" ? `남은 토큰 ${d.level}` : `카운트 ${d.level}`})`}</title>
          </circle>
        ))}
      </svg>
      <p className="text-[11px] text-foreground-muted">{note}</p>
    </div>
  );
}

// ---- Build 스테이지별 장면 ----

function StageScenes() {
  const scenes = RATE_LIMITER_SCENARIOS.filter((s) => s.build);
  const [active, setActive] = useState(scenes[0].id);
  const scene = scenes.find((s) => s.id === active)!;
  return (
    <Card as="section" className="flex flex-col gap-4">
      <div>
        <h2 className="font-semibold">Build 과제 스테이지로 보기</h2>
        <p className="mt-1 text-sm text-foreground-muted">각 장면의 값은 Build 과제의 그 스테이지 테스트와 같습니다. 직접 구현하면 같은 테스트로 채점됩니다.</p>
      </div>
      <div role="tablist" aria-label="스테이지" className="flex flex-wrap gap-1 border-b border-border">
        {scenes.map((s) => (
          <button
            key={s.id}
            type="button"
            role="tab"
            aria-selected={active === s.id}
            onClick={() => setActive(s.id)}
            className={`-mb-px border-b-2 px-3 py-2 text-sm ${active === s.id ? "border-accent font-medium" : "border-transparent text-foreground-muted hover:text-foreground"}`}
          >
            {s.build!.stage}단계
          </button>
        ))}
      </div>
      <div className="flex flex-col gap-3">
        <div>
          <p className="font-medium">{scene.title}</p>
          <p className="text-sm text-foreground-muted">{scene.lesson}</p>
        </div>
        {active === "stage1" && <Stage1 />}
        {active === "stage2" && <Stage2 />}
        {active === "stage3" && <Stage3 />}
        {active === "stage4" && <Stage4 />}
        {active === "stage5" && <Stage5 />}
        {active === "stage6" && <Stage6 />}
        <div className="flex flex-wrap items-center gap-2">
          <Button href="/bridge?challenge=rate-limiter" size="sm">
            Build {scene.build!.stage}단계 직접 구현하기 →
          </Button>
          <span className="text-[11px] text-foreground-muted">테스트: {scene.build!.files.map((f) => f.path.split("/").slice(-3).join("/")).join(", ")}</span>
        </div>
      </div>
    </Card>
  );
}

function DecisionRow({ decisions, showKey }: { decisions: RateDecision[]; showKey?: boolean }) {
  return (
    <ol className="flex flex-wrap gap-1.5 text-xs">
      {decisions.map((d, i) => (
        <li key={i} className={`rounded border px-2 py-1 ${d.allowed ? "border-success text-success" : "border-danger text-danger"}`}>
          {showKey && <span className="text-foreground-muted">{d.key} </span>}
          {d.t}s · 카운트 {d.level} · {d.allowed ? "통과" : "거절"}
        </li>
      ))}
    </ol>
  );
}

function Stage1() {
  const d = simulate("fixed-window", STAGE1.capacity, STAGE1.windowSeconds, STAGE1.requests);
  return (
    <div className="flex flex-col gap-2 text-sm">
      <p className="text-foreground-muted">
        용량 {STAGE1.capacity} / 윈도우 {STAGE1.windowSeconds}초. user-a가 {STAGE1.requests.filter((r) => r.key === "user-a").length}번 연속 요청한 뒤 user-b가 한 번.
      </p>
      <DecisionRow decisions={d} showKey />
      <p className="text-xs text-foreground-muted">카운터는 키마다 따로 — user-a가 거절당하는 중에도 user-b는 통과합니다.</p>
    </div>
  );
}

function Stage2() {
  const d = simulate("fixed-window", STAGE2.capacity, STAGE2.windowSeconds, STAGE2.requests);
  return (
    <div className="flex flex-col gap-2 text-sm">
      <p className="text-foreground-muted">
        용량 {STAGE2.capacity} / 윈도우 {STAGE2.windowSeconds}초. 세 번 연속 요청한 뒤 0.7초 쉬고 다시.
      </p>
      <DecisionRow decisions={d} />
      <p className="text-xs text-foreground-muted">
        0초에 열린 윈도우가 {STAGE2.windowSeconds}초에 끝나 카운터가 사라졌으므로 0.72초의 요청은 새 윈도우의 첫 요청입니다. 만료를 구현하지 않으면 윈도우가 영원히 끝나지 않습니다.
      </p>
    </div>
  );
}

function Stage3() {
  const [atomic, setAtomic] = useState(false);
  const total = STAGE3.workers * STAGE3.callsPerWorker;
  const result = raceAllowed(STAGE3.workers, STAGE3.callsPerWorker, STAGE3.capacity, atomic);
  return (
    <div className="flex flex-col gap-3 text-sm">
      <p className="text-foreground-muted">
        용량 {STAGE3.capacity}에 요청 {total}개가 동시에 (Python: 스레드 {STAGE3.workers}개 × {STAGE3.callsPerWorker}번, TypeScript: Promise.all {total}개).
      </p>
      <label className="flex items-center gap-2">
        <input type="checkbox" checked={atomic} onChange={(e) => setAtomic(e.target.checked)} />
        증가를 원자적으로 (Redis INCR·락)
      </label>
      <div className="overflow-x-auto">
        <table className="text-xs">
          <thead>
            <tr className="text-left text-foreground-muted">
              <th className="pr-3 font-normal">차례</th>
              {Array.from({ length: 4 }, (_, w) => (
                <th key={w} className="pr-3 font-normal">요청 {w + 1}</th>
              ))}
              <th className="font-normal">…</th>
            </tr>
          </thead>
          <tbody>
            {[0, 1].map((round) => (
              <tr key={round}>
                <td className="pr-3 text-foreground-muted">{round + 1}</td>
                {Array.from({ length: 4 }, (_, w) => (
                  <td key={w} className="pr-3 font-mono">
                    {atomic ? `INCR → ${round * STAGE3.workers + w + 1}` : `읽음 ${round} → 씀 ${round + 1}`}
                  </td>
                ))}
                <td>…</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className={result.allowed > STAGE3.capacity ? "font-medium text-danger" : "font-medium text-success"}>
        통과 {result.allowed}개 (한도 {STAGE3.capacity}) · 카운터 최종값 {result.counter}
      </p>
      <p className="text-xs text-foreground-muted">
        {atomic
          ? "읽기와 더하기가 한 동작이라 요청마다 다른 값을 받습니다 — 정확히 한도만큼 통과합니다."
          : "모두가 같은 값을 읽고 그 값 + 1을 덮어쓰므로 카운터가 거의 오르지 않고, 모두 \"아직 한도 안\"이라고 판단합니다."}
      </p>
    </div>
  );
}

function Stage4() {
  const [shared, setShared] = useState(true);
  const allowed = sharedStoreAllowed(STAGE4.instances, STAGE4.requests, STAGE4.capacity, shared);
  return (
    <div className="flex flex-col gap-3 text-sm">
      <p className="text-foreground-muted">
        인스턴스 {STAGE4.instances}대, 용량 {STAGE4.capacity}. 요청 {STAGE4.requests}개를 번갈아 받습니다.
      </p>
      <label className="flex items-center gap-2">
        <input type="checkbox" checked={shared} onChange={(e) => setShared(e.target.checked)} />
        카운터 저장소를 공유 (Redis 하나)
      </label>
      <p className={allowed > STAGE4.capacity ? "font-medium text-danger" : "font-medium text-success"}>
        전체 통과 {allowed}개 (의도한 한도 {STAGE4.capacity})
      </p>
      <p className="text-xs text-foreground-muted">
        {shared ? "모든 인스턴스가 같은 카운터를 올리므로 전체 한도가 지켜집니다 — 대신 저장소가 새 의존성이 됩니다(5단계)." : "인스턴스마다 자기 카운터를 쓰면 한도가 인스턴스 수만큼 곱해집니다 — 스케일 아웃할수록 보호가 약해집니다."}
      </p>
    </div>
  );
}

function Stage5() {
  const [mode, setMode] = useState<"open" | "closed">("open");
  return (
    <div className="flex flex-col gap-3 text-sm">
      <p className="text-foreground-muted">카운터 저장소(Redis)가 응답하지 않습니다. 요청을 어떻게 할까요?</p>
      <div className="flex gap-1.5">
        {(["open", "closed"] as const).map((m) => (
          <Button key={m} size="sm" variant={m === mode ? "primary" : "secondary"} onClick={() => setMode(m)}>
            fail-{m}
          </Button>
        ))}
      </div>
      <p className={`font-medium ${mode === "open" ? "text-warning" : "text-danger"}`}>{mode === "open" ? "요청 통과 — 한도 없이" : "요청 거절 — 전부"}</p>
      <p className="text-xs text-foreground-muted">
        {mode === "open"
          ? "가용성을 고른다: 사용자는 계속 쓰지만, 그동안 Rate Limit이 없는 것과 같다. 쿠폰 이벤트처럼 보호가 핵심인 곳에서는 위험하다."
          : "보호를 고른다: 뒤의 시스템은 안전하지만, 저장소 장애가 곧 전체 장애가 된다. 저장소의 가용성이 서비스 가용성의 상한이 된다."}
      </p>
    </div>
  );
}

function Stage6() {
  const d = simulate("fixed-window", STAGE6.capacity, STAGE6.windowSeconds, STAGE6.requests);
  const allowed = d.filter((x) => x.allowed).length;
  const rejected = d.length - allowed;
  return (
    <div className="flex flex-col gap-2 text-sm">
      <DecisionRow decisions={d} />
      <dl className="grid grid-cols-3 gap-2 text-center">
        {[
          ["통과", String(allowed)],
          ["거절", String(rejected)],
          ["거절률", `${((rejected / d.length) * 100).toFixed(1)}%`],
        ].map(([k, v]) => (
          <div key={k} className="rounded border border-border p-2">
            <dt className="text-xs text-foreground-muted">{k}</dt>
            <dd className="font-mono text-lg">{v}</dd>
          </div>
        ))}
      </dl>
      <p className="text-xs text-foreground-muted">거절률이 갑자기 오르면 공격·버그·한도 설정 중 하나다 — 키별로 쪼개 보면(key skew) 누가 한도를 쓰는지 보인다.</p>
    </div>
  );
}
