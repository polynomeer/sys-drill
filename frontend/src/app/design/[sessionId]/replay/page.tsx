"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { TimelineStep, getSession, getSimulationTimeline } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { MetricsPanel } from "../WargameLive";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

const AUTO_PLAY_INTERVAL_MS = 1800;

/** PLAN.md step 25 — scrubs through an already-finished incident's real timeline (AppliedAction rows), reusing WargameLive's MetricsPanel so each step renders identically to how it looked live. */
export default function IncidentReplayPage() {
  const params = useParams<{ sessionId: string }>();
  const router = useRouter();
  const sessionId = params.sessionId;

  const [domain, setDomain] = useState<string | null>(null);
  const [steps, setSteps] = useState<TimelineStep[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [autoPlaying, setAutoPlaying] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }

    Promise.all([getSession(sessionId), getSimulationTimeline(sessionId)])
      .then(([session, timeline]) => {
        setDomain(session.domain);
        setSteps(timeline);
      })
      .catch(() => setError("리플레이 타임라인을 불러오지 못했습니다."));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  const stopAutoPlay = useCallback(() => {
    setAutoPlaying(false);
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  useEffect(() => {
    if (!autoPlaying || !steps) return;
    timerRef.current = setInterval(() => {
      setCurrentIndex((prev) => {
        if (prev >= steps.length - 1) {
          stopAutoPlay();
          return prev;
        }
        return prev + 1;
      });
    }, AUTO_PLAY_INTERVAL_MS);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [autoPlaying, steps, stopAutoPlay]);

  if (error) {
    return (
      <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-4 p-8">
        <p className="text-sm text-danger">{error}</p>
      </div>
    );
  }

  if (!domain || !steps) {
    return (
      <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-4 p-8">
        <LoadingState />
      </div>
    );
  }

  if (steps.length === 0) {
    return (
      <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-4 p-8">
        <p className="text-sm text-foreground-muted">이 세션은 인시던트를 시작하지 않아 리플레이할 타임라인이 없습니다.</p>
      </div>
    );
  }

  const step = steps[currentIndex];

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">인시던트 리플레이</h1>
        <Link href={`/report/${sessionId}`} className="text-sm underline">
          리포트로
        </Link>
      </div>

      <Card as="section">
        <div className="flex items-center justify-between gap-4">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              stopAutoPlay();
              setCurrentIndex((i) => Math.max(0, i - 1));
            }}
            disabled={currentIndex === 0}
          >
            이전
          </Button>

          <div className="flex flex-col items-center gap-1 text-center">
            <span className="font-mono text-xs text-foreground-muted">
              {currentIndex + 1} / {steps.length}
            </span>
            <span className="text-sm font-medium">{step.label}</span>
            <span className="text-xs text-foreground-muted">{new Date(step.appliedAt).toLocaleTimeString()}</span>
          </div>

          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              stopAutoPlay();
              setCurrentIndex((i) => Math.min(steps.length - 1, i + 1));
            }}
            disabled={currentIndex === steps.length - 1}
          >
            다음
          </Button>
        </div>

        <div className="mt-3 flex justify-center">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              if (autoPlaying) {
                stopAutoPlay();
              } else {
                if (currentIndex === steps.length - 1) setCurrentIndex(0);
                setAutoPlaying(true);
              }
            }}
          >
            {autoPlaying ? "일시정지" : "자동 재생"}
          </Button>
        </div>
      </Card>

      <MetricsPanel state={step.systemState} domain={domain} />
    </div>
  );
}
