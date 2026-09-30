"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import type { DiscussionMessage, DiscussionThread } from "@/lib/api";
import { getDiscussion, postDiscussion, reportDiscussion } from "@/lib/api";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { LoadingState } from "@/components/ui/LoadingState";

/** ADR-0040 — 전송은 폴링이다(ADR-0026 연장). 질문 스레드는 채팅이 아니라서 이 간격으로 충분하다. */
const POLL_INTERVAL_MS = 15000;

function formatTime(iso?: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(
    d.getMinutes(),
  ).padStart(2, "0")}`;
}

function Message({
  message,
  scenarioId,
  onReport,
}: {
  message: DiscussionMessage;
  scenarioId: string;
  onReport: (id: string) => void;
}) {
  return (
    <li className="py-3">
      <div className="mb-1 flex flex-wrap items-baseline gap-2">
        <span className="text-sm font-medium">{message.authorNickname}</span>
        <span className="text-xs text-foreground-muted">{formatTime(message.createdAt)}</span>
        {!message.mine && (
          <button
            type="button"
            onClick={() => onReport(message.id)}
            disabled={message.reportedByMe}
            className="ml-auto text-xs text-foreground-muted underline underline-offset-2 disabled:no-underline disabled:opacity-60"
          >
            {message.reportedByMe ? "신고됨" : "신고"}
          </button>
        )}
      </div>
      <p className="whitespace-pre-wrap text-sm">{message.body}</p>

      {message.quoted &&
        (message.quoted.locked ? (
          // ADR-0041 — 인용만 잠긴다. 본문은 위에 그대로 보인다.
          <p className="mt-2 rounded border border-border px-2 py-1 text-xs text-foreground-muted">
            공개 풀이를 인용했습니다 — 이 시나리오를 완료하면 열립니다.
          </p>
        ) : (
          <Link
            href={`/writeups/${scenarioId}/${message.quoted.sessionId}`}
            className="mt-2 block rounded border border-border px-2 py-1 text-xs underline underline-offset-2"
          >
            인용한 풀이 보기 — {message.quoted.authorNickname ?? "익명"}
          </Link>
        ))}
    </li>
  );
}

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.5 / ADR-0040 — 시나리오별 토론.
 *
 * 스레드가 비어 있어도 "빈 게시판"으로 보이지 않게, 서버가 함께 주는 집계
 * 신호(완료 수·평균 점수)를 상단에 둔다. 자유 게시판이 아니라 "이 시나리오에
 * 대한 질문"이라 질문이 없는 것은 초라한 게 아니라 그냥 아직 없는 것이다.
 */
export function DiscussionPanel({ scenarioId }: { scenarioId: string }) {
  const [thread, setThread] = useState<DiscussionThread | null>(null);
  const [draft, setDraft] = useState("");
  const [posting, setPosting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(
    () =>
      getDiscussion(scenarioId)
        .then(setThread)
        .catch(() => setError("토론을 불러오지 못했습니다.")),
    [scenarioId],
  );

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  async function submit() {
    const body = draft.trim();
    if (!body || posting) return;
    setPosting(true);
    setError(null);
    try {
      await postDiscussion(scenarioId, body);
      setDraft("");
      await refresh();
    } catch {
      setError("글을 남기지 못했습니다.");
    } finally {
      setPosting(false);
    }
  }

  async function report(discussionId: string) {
    try {
      await reportDiscussion(discussionId);
      await refresh();
    } catch {
      setError("신고를 접수하지 못했습니다.");
    }
  }

  if (!thread) return <Card as="section">{error ? <p className="text-sm text-danger">{error}</p> : <LoadingState />}</Card>;

  return (
    <Card as="section">
      <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold">토론</h2>
        <span className="text-xs text-foreground-muted">
          완료 {thread.completedCount}명
          {typeof thread.averageScore === "number" ? ` · 평균 ${thread.averageScore}점` : ""}
        </span>
      </div>
      <p className="mb-3 text-xs text-foreground-muted">
        이 시나리오에 대한 질문과 회고를 나누는 곳입니다. 다른 시나리오 이야기는 그쪽 스레드에서 해주세요.
      </p>

      {thread.messages.length === 0 ? (
        <p className="text-sm text-foreground-muted">아직 질문이 없습니다. 첫 질문을 남겨보세요.</p>
      ) : (
        <ul className="divide-y divide-border">
          {thread.messages.map((message) => (
            <Message key={message.id} message={message} scenarioId={scenarioId} onReport={report} />
          ))}
        </ul>
      )}

      <div className="mt-4 flex flex-col gap-2">
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          rows={3}
          maxLength={4000}
          placeholder="이 시나리오에서 막힌 지점이나 다른 설계를 물어보세요."
          className="w-full rounded border border-border bg-transparent p-2 text-sm"
        />
        <div className="flex items-center justify-between gap-2">
          {error ? <span className="text-xs text-danger">{error}</span> : <span />}
          <Button size="sm" onClick={submit} disabled={posting || draft.trim().length === 0}>
            {posting ? "남기는 중..." : "남기기"}
          </Button>
        </div>
      </div>
    </Card>
  );
}
