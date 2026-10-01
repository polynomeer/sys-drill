"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ScenarioSummary, listScenarios } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { pickFirstDrill } from "@/lib/drillPrereq";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";

const MODES = [
  {
    badge: "accent" as const,
    label: "Design",
    title: "설계하고",
    desc: "100만 사용자 선착순 쿠폰, 결제, 예약 — 실제 서비스 요구사항으로 아키텍처를 설계하고 AI 루브릭 피드백을 받습니다.",
  },
  {
    badge: "success" as const,
    label: "Build",
    title: "직접 만들고",
    desc: "Rate Limiter 같은 핵심 컴포넌트를 코드로 구현하고, 샌드박스에서 단계별 테스트로 검증합니다.",
  },
  {
    badge: "danger" as const,
    label: "Incident",
    title: "무너뜨리고 복구합니다",
    desc: "트래픽 폭증·DB 지연을 워게임으로 겪고, 실시간 지표를 보며 대응한 뒤 회고까지 작성합니다.",
  },
];

const LOOP = ["초기 설계", "꼬리설계 — 조건이 바뀝니다", "장애 대응 워게임", "리포트와 다음 추천"];

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.4 — the product landing page. This used
 * to be a backend health-check readout (JSON dump); health lives at
 * /actuator/health for anyone who needs it. Signed-in visitors skip straight
 * to Home. The primary CTA goes to the pinned first Drill's overview —
 * GET /scenarios is public, so this works before sign-up.
 */
export default function LandingPage() {
  const router = useRouter();
  const [firstDrill, setFirstDrill] = useState<ScenarioSummary | undefined>();

  useEffect(() => {
    if (getStoredToken()) {
      router.replace("/dashboard");
      return;
    }
    listScenarios()
      .then((scenarios) => setFirstDrill(pickFirstDrill(scenarios)))
      .catch(() => setFirstDrill(undefined));
  }, [router]);

  return (
    <div className="flex flex-col">
      <section className="border-b border-border">
        <div className="mx-auto grid max-w-5xl items-center gap-10 px-6 py-16 md:grid-cols-[1fr_360px] md:py-24">
          <div className="flex flex-col gap-5">
            <p className="font-mono text-sm font-semibold text-accent">Train. Break. Fix. Repeat.</p>
            <h1 className="break-keep text-4xl font-semibold leading-tight md:text-5xl">
              시스템 설계,
              <br />
              읽지 말고 훈련하세요.
            </h1>
            <p className="max-w-lg break-keep text-foreground-muted">
              백엔드 개발자를 위한 시스템 설계·장애 대응 훈련 플랫폼. 실제 서비스 시나리오로 설계하고, 조건이 바뀌면
              다시 설계하고, 장애가 나면 직접 복구합니다.
            </p>
            <div className="flex flex-wrap items-center gap-3">
              <Button
                href={firstDrill ? `/drills/${firstDrill.id}` : "/onboarding"}
                className="px-6 py-2.5 text-base"
              >
                {firstDrill ? `첫 Drill 살펴보기 — ${firstDrill.title} →` : "무료로 시작하기 →"}
              </Button>
              <Button href="/login" variant="secondary" className="px-6 py-2.5 text-base">
                로그인
              </Button>
            </div>
          </div>
          {/* Recorded from the real Incident Drill (README demo), not a mockup. */}
          {/* eslint-disable-next-line @next/next/no-img-element -- animated GIF; next/image would re-encode it */}
          <img
            src="/landing/demo-wargame.gif"
            alt="Incident Drill 워게임 화면 — 실시간 지표를 보며 대응 액션을 실행하는 모습"
            width={760}
            height={860}
            className="w-full rounded-xl border border-border"
          />
        </div>
      </section>

      <section className="mx-auto grid w-full max-w-5xl gap-4 px-6 py-16 md:grid-cols-3">
        {MODES.map((mode) => (
          <div key={mode.label} className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-5">
            <Badge variant={mode.badge} className="self-start">
              {mode.label}
            </Badge>
            <p className="text-lg font-semibold">{mode.title}</p>
            <p className="text-sm text-foreground-muted">{mode.desc}</p>
          </div>
        ))}
      </section>

      <section className="border-t border-border">
        <div className="mx-auto flex max-w-5xl flex-col gap-6 px-6 py-16">
          <h2 className="text-2xl font-semibold">한 번의 Drill은 이렇게 흘러갑니다</h2>
          <ol className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
            {LOOP.map((step, i) => (
              <li key={step} className="rounded-xl border border-border p-4">
                <span className="font-mono text-xs text-foreground-muted">{String(i + 1).padStart(2, "0")}</span>
                <p className="mt-1 font-medium">{step}</p>
              </li>
            ))}
          </ol>
          <p className="text-sm text-foreground-muted">
            단계마다 AI가 7개 루브릭 항목으로 채점하고, 놓친 점과 꼬리질문을 돌려줍니다.
          </p>
          <div className="flex flex-wrap items-center gap-4">
            <Button href="/onboarding">가입하고 시작하기 →</Button>
            <Button href="/how-it-works" variant="ghost">
              자세히 알아보기
            </Button>
          </div>
        </div>
      </section>
    </div>
  );
}
