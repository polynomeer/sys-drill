"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import type { DiscussionKind, DiscussionMessage, DiscussionThread } from "@/lib/api";
import { getDiscussion, postDiscussion, reportDiscussion } from "@/lib/api";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { LoadingState } from "@/components/ui/LoadingState";

/** ADR-0040 — 전송은 폴링이다(ADR-0026 연장). 질문 스레드는 채팅이 아니라서 이 간격으로 충분하다. */
const POLL_INTERVAL_MS = 15000;

/** docs/COMMUNITY_EXPANSION_PLAN.md C7 — 필터용 글 종류. 입력 템플릿은 두지 않는다. */
const KIND_LABELS: Record<DiscussionKind, string> = {
  QUESTION: "질문",
  DESIGN: "설계",
  RESPONSE: "대응",
  INSIGHT: "인사이트",
};
const KINDS = Object.keys(KIND_LABELS) as DiscussionKind[];

function formatTime(iso?: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(
    d.getMinutes(),
  ).padStart(2, "0")}`;
}

function MessageBody({ message, scenarioId }: { message: DiscussionMessage; scenarioId: string }) {
  if (message.spoilerLocked) {
    // ADR-0041 게이트를 본문에도 — 서버가 본문을 아예 보내지 않는다.
    return (
      <p className="rounded border border-dashed border-border px-2 py-1 text-xs text-foreground-muted">
        풀이 내용이 담긴 글입니다 — 이 시나리오를 완료하면 열립니다.
      </p>
    );
  }
  return (
    <>
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
    </>
  );
}

function MessageHeader({
  message,
  onReport,
}: {
  message: DiscussionMessage;
  onReport?: (id: string) => void;
}) {
  return (
    <div className="mb-1 flex flex-wrap items-baseline gap-2">
      <span className="text-sm font-medium">{message.authorNickname}</span>
      {/* 종류는 최상위 글의 필터용 — 답글에는 표시하지 않는다. */}
      {!message.parentId && (
        <span className="rounded bg-surface-elevated px-1.5 text-[11px] text-foreground-muted">{KIND_LABELS[message.kind] ?? message.kind}</span>
      )}
      {message.containsSpoiler && !message.spoilerLocked && (
        <span className="rounded bg-warning/15 px-1.5 text-[11px] text-warning">풀이 포함</span>
      )}
      <span className="text-xs text-foreground-muted">{formatTime(message.createdAt)}</span>
      {onReport && !message.mine && (
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
  );
}

function Composer({
  placeholder,
  submitLabel,
  withKind,
  onSubmit,
  onCancel,
}: {
  placeholder: string;
  submitLabel: string;
  withKind: boolean;
  onSubmit: (body: string, kind: DiscussionKind, containsSpoiler: boolean) => Promise<boolean>;
  onCancel?: () => void;
}) {
  const [draft, setDraft] = useState("");
  const [kind, setKind] = useState<DiscussionKind>("QUESTION");
  const [spoiler, setSpoiler] = useState(false);
  const [posting, setPosting] = useState(false);

  async function submit() {
    const body = draft.trim();
    if (!body || posting) return;
    setPosting(true);
    const ok = await onSubmit(body, kind, spoiler);
    setPosting(false);
    if (ok) {
      setDraft("");
      setSpoiler(false);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <textarea
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        rows={withKind ? 3 : 2}
        maxLength={4000}
        placeholder={placeholder}
        className="w-full rounded border border-border bg-transparent p-2 text-sm"
      />
      <div className="flex flex-wrap items-center gap-3">
        {withKind && (
          <select
            value={kind}
            onChange={(e) => setKind(e.target.value as DiscussionKind)}
            className="rounded border border-border bg-transparent px-2 py-1 text-xs"
            aria-label="글 종류"
          >
            {KINDS.map((k) => (
              <option key={k} value={k}>
                {KIND_LABELS[k]}
              </option>
            ))}
          </select>
        )}
        <label className="flex items-center gap-1.5 text-xs text-foreground-muted">
          <input type="checkbox" checked={spoiler} onChange={(e) => setSpoiler(e.target.checked)} />
          풀이 내용 포함 (미완료자에게 가려집니다)
        </label>
        <div className="ml-auto flex gap-2">
          {onCancel && (
            <Button size="sm" variant="ghost" onClick={onCancel}>
              취소
            </Button>
          )}
          <Button size="sm" onClick={submit} disabled={posting || draft.trim().length === 0}>
            {posting ? "남기는 중..." : submitLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.5 / ADR-0040 — 시나리오별 토론.
 *
 * 스레드가 비어 있어도 "빈 게시판"으로 보이지 않게, 서버가 함께 주는 집계
 * 신호(완료 수·평균 점수)를 상단에 둔다. 자유 게시판이 아니라 "이 시나리오에
 * 대한 질문"이라 질문이 없는 것은 초라한 게 아니라 그냥 아직 없는 것이다.
 *
 * PLAN.md Round E3 — 답글(한 단계), 글 종류 필터, 스포일러 잠금, 이전 버전 스레드.
 * `compact`는 Drill 개요·리포트에 끼워 넣는 요약판: 최근 글 몇 개와 전체 보기 링크만.
 */
export function DiscussionPanel({ scenarioId, compact = false }: { scenarioId: string; compact?: boolean }) {
  const [thread, setThread] = useState<DiscussionThread | null>(null);
  const [filter, setFilter] = useState<DiscussionKind | "ALL">("ALL");
  const [replyTo, setReplyTo] = useState<string | null>(null);
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
    if (compact) return;
    const timer = setInterval(refresh, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refresh, compact]);

  async function post(
    body: string,
    kind: DiscussionKind,
    containsSpoiler: boolean,
    parentId?: string,
  ): Promise<boolean> {
    setError(null);
    try {
      await postDiscussion(scenarioId, body, { kind, containsSpoiler, parentId });
      setReplyTo(null);
      await refresh();
      return true;
    } catch {
      setError("글을 남기지 못했습니다.");
      return false;
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

  const topLevel = thread.messages.filter((m) => !m.parentId);
  const repliesOf = (id: string) => thread.messages.filter((m) => m.parentId === id);
  const shown = topLevel.filter((m) => filter === "ALL" || m.kind === filter);

  if (compact) {
    const latest = [...topLevel].reverse().slice(0, 3);
    return (
      <Card as="section">
        <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-sm font-semibold">토론</h2>
          <span className="text-xs text-foreground-muted">글 {thread.messages.length}개</span>
        </div>
        {latest.length === 0 ? (
          <p className="text-sm text-foreground-muted">아직 질문이 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {latest.map((m) => (
              <li key={m.id} className="text-sm">
                <span className="mr-1 text-xs text-foreground-muted">[{KIND_LABELS[m.kind]}]</span>
                {m.spoilerLocked ? (
                  <span className="text-foreground-muted">풀이 내용이 담긴 글 (완료 후 열림)</span>
                ) : (
                  <span className="line-clamp-2">{m.body}</span>
                )}
                {repliesOf(m.id).length > 0 && (
                  <span className="ml-1 text-xs text-foreground-muted">· 답글 {repliesOf(m.id).length}</span>
                )}
              </li>
            ))}
          </ul>
        )}
        <Link href={`/discussions/${scenarioId}`} className="mt-3 inline-block text-xs text-accent hover:underline">
          토론 전체 보기 / 질문하기 →
        </Link>
      </Card>
    );
  }

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

      <div className="mb-2 flex flex-wrap gap-1.5" role="group" aria-label="글 종류 필터">
        {(["ALL", ...KINDS] as const).map((k) => (
          <button
            key={k}
            type="button"
            onClick={() => setFilter(k)}
            aria-pressed={filter === k}
            className={`rounded-full border px-2.5 py-0.5 text-xs ${filter === k ? "border-accent text-accent" : "border-border text-foreground-muted hover:text-foreground"}`}
          >
            {k === "ALL" ? "전체" : KIND_LABELS[k]}
          </button>
        ))}
      </div>

      {shown.length === 0 ? (
        <p className="text-sm text-foreground-muted">
          {topLevel.length === 0 ? "아직 질문이 없습니다. 첫 질문을 남겨보세요." : "이 종류의 글이 없습니다."}
        </p>
      ) : (
        <ul className="divide-y divide-border">
          {shown.map((message) => (
            <li key={message.id} className="py-3">
              <MessageHeader message={message} onReport={report} />
              <MessageBody message={message} scenarioId={scenarioId} />
              {repliesOf(message.id).length > 0 && (
                <ul className="mt-2 flex flex-col gap-2 border-l-2 border-border pl-3">
                  {repliesOf(message.id).map((reply) => (
                    <li key={reply.id}>
                      <MessageHeader message={reply} onReport={report} />
                      <MessageBody message={reply} scenarioId={scenarioId} />
                    </li>
                  ))}
                </ul>
              )}
              {replyTo === message.id ? (
                <div className="mt-2 pl-3">
                  <Composer
                    placeholder="답글을 남겨보세요."
                    submitLabel="답글 남기기"
                    withKind={false}
                    onSubmit={(body, _kind, spoiler) => post(body, "INSIGHT", spoiler, message.id)}
                    onCancel={() => setReplyTo(null)}
                  />
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setReplyTo(message.id)}
                  className="mt-1 text-xs text-foreground-muted underline underline-offset-2 hover:text-foreground"
                >
                  답글
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-4">
        <Composer
          placeholder="이 시나리오에서 막힌 지점이나 다른 설계를 물어보세요."
          submitLabel="남기기"
          withKind
          onSubmit={(body, kind, spoiler) => post(body, kind, spoiler)}
        />
        {error && <p className="mt-1 text-xs text-danger">{error}</p>}
      </div>

      {thread.previousVersions.length > 0 && (
        // PLAN.md Round E3 / ADR-0048 — 시나리오가 새 버전으로 바뀌어도 옛 글은 읽을 수 있다(쓰기는 최신 버전만).
        <details className="mt-6 border-t border-border pt-3">
          <summary className="cursor-pointer text-xs text-foreground-muted">
            이전 버전 토론 ({thread.previousVersions.map((v) => `v${v.versionNo}`).join(", ")}) — 읽기 전용
          </summary>
          {thread.previousVersions.map((version) => (
            <div key={version.versionNo} className="mt-3">
              <p className="mb-1 text-xs font-semibold text-foreground-muted">
                v{version.versionNo} — 문제 구성이 지금과 다를 수 있습니다
              </p>
              <ul className="divide-y divide-border">
                {version.messages.map((m) => (
                  <li key={m.id} className={`py-2 ${m.parentId ? "pl-3" : ""}`}>
                    <MessageHeader message={m} />
                    <MessageBody message={m} scenarioId={scenarioId} />
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </details>
      )}
    </Card>
  );
}
