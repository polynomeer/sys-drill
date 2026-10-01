"use client";

import { useState } from "react";
import { Check, Copy, Eye, EyeOff } from "lucide-react";
import { API_BASE_URL } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";

function CopyLine({ text, label }: { text: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <div className="flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-2 font-mono text-xs">
      <code className="min-w-0 flex-1 overflow-x-auto whitespace-pre">{label ?? text}</code>
      <button
        type="button"
        onClick={async () => {
          try {
            await navigator.clipboard.writeText(text);
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
          } catch {
            setCopied(false);
          }
        }}
        className="shrink-0 text-foreground-muted hover:text-foreground"
        aria-label="복사"
      >
        {copied ? <Check className="h-3.5 w-3.5 text-success" /> : <Copy className="h-3.5 w-3.5" />}
      </button>
    </div>
  );
}

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.3 (PLAN.md Round B12) — solve in your own
 * editor and submit from the terminal. The page keeps watching for the newest
 * submission while this panel is open, so a `./submit.sh` run shows up in the
 * test log below without a refresh.
 */
export function LocalSolvePanel({ dir, waiting }: { dir: string; waiting: boolean }) {
  const [showToken, setShowToken] = useState(false);
  const token = getStoredToken() ?? "";
  const masked = token ? `${token.slice(0, 12)}…${token.slice(-6)}` : "(로그인이 필요합니다)";

  return (
    <section className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-5 text-sm">
      <ol className="flex flex-col gap-4">
        <li className="flex flex-col gap-2">
          <p className="font-medium">1. 저장소를 받고 과제 디렉터리로 이동</p>
          <CopyLine text={`git clone https://github.com/polynomeer/sys-drill.git && cd sys-drill/challenges/${dir}`} />
        </li>
        <li className="flex flex-col gap-2">
          <p className="font-medium">2. 토큰과 API 주소 설정</p>
          <p className="text-xs text-foreground-muted">
            로그인 세션 토큰입니다. 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 여기서 다시
            복사하면 됩니다.
          </p>
          <CopyLine text={`export SYSDRILL_TOKEN=${token}`} label={`export SYSDRILL_TOKEN=${showToken ? token : masked}`} />
          <button
            type="button"
            onClick={() => setShowToken((v) => !v)}
            className="flex items-center gap-1 self-start text-xs text-foreground-muted hover:text-foreground"
          >
            {showToken ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
            {showToken ? "토큰 가리기" : "토큰 보기"}
          </button>
          <CopyLine text={`export SYSDRILL_API_BASE_URL=${API_BASE_URL}`} />
        </li>
        <li className="flex flex-col gap-2">
          <p className="font-medium">3. 자기 에디터에서 고치고 제출</p>
          <CopyLine text="./submit.sh" />
          <p className="text-xs text-foreground-muted">터미널에 단계별 결과가 바로 나오고, 이 화면의 단계 목록과 테스트 로그에도 반영됩니다.</p>
        </li>
      </ol>
      {waiting && (
        <p className="flex items-center gap-2 border-t border-border pt-3 text-xs text-foreground-muted">
          <span className="h-2 w-2 animate-pulse rounded-full bg-accent" aria-hidden />
          제출을 기다리는 중…
        </p>
      )}
    </section>
  );
}
