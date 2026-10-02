"use client";

import { useEffect, useMemo, useState } from "react";
import {
  type AnchorType,
  type ReviewKind,
  type WriteupComments,
  ApiError,
  getWriteupComments,
  postWriteupComment,
  reportWriteupComment,
} from "@/lib/api";
import { withActionLabels } from "@/lib/actionLabels";
import { ReactionBar } from "@/components/ReactionBar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Textarea } from "@/components/ui/Input";

export const REVIEW_KINDS: { kind: ReviewKind; label: string; hint: string }[] = [
  { kind: "QUESTION", label: "질문", hint: "왜 이렇게 했는지 묻기" },
  { kind: "RISK", label: "리스크", hint: "깨질 수 있는 지점" },
  { kind: "SUGGESTION", label: "제안", hint: "이렇게 바꾸면" },
  { kind: "ALTERNATIVE", label: "대안", hint: "다른 접근" },
  { kind: "TRADEOFF", label: "트레이드오프", hint: "얻은 것과 잃은 것" },
];
const KIND_LABEL = Object.fromEntries(REVIEW_KINDS.map((k) => [k.kind, k.label])) as Record<ReviewKind, string>;

const prettyAnchor = withActionLabels;

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C10 (PLAN.md Round E22) — reviews pinned to a canvas node, a
 * moment of the incident, or the whole writeup. A review kind is required: it's the nudge away
 * from "좋네요" toward questions and risks.
 */
export function ReviewPanel({ sessionId }: { sessionId: string }) {
  const [data, setData] = useState<WriteupComments | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [anchor, setAnchor] = useState("NONE:");
  const [kind, setKind] = useState<ReviewKind>("QUESTION");
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);
  const [filter, setFilter] = useState<string | null>(null);

  useEffect(() => {
    getWriteupComments(sessionId)
      .then(setData)
      .catch(() => setError("리뷰를 불러오지 못했습니다."));
  }, [sessionId]);

  const grouped = useMemo(() => {
    const counts = new Map<string, number>();
    data?.comments.forEach((c) => {
      const key = `${c.anchorType}:${c.anchorRef ?? ""}`;
      counts.set(key, (counts.get(key) ?? 0) + 1);
    });
    return counts;
  }, [data]);

  if (!data) return error ? <p className="text-sm text-danger">{error}</p> : null;

  async function submit() {
    const split = anchor.indexOf(":");
    const type = anchor.slice(0, split) as AnchorType;
    const ref = anchor.slice(split + 1);
    setBusy(true);
    setError(null);
    try {
      setData(await postWriteupComment(sessionId, { anchorType: type, anchorRef: ref || undefined, kind, body }));
      setBody("");
    } catch (err) {
      setError(err instanceof ApiError && err.status === 400 ? "이 위치에는 리뷰를 남길 수 없습니다." : "리뷰를 남기지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function report(id: string) {
    try {
      await reportWriteupComment(id);
      setData((d) => d && { ...d, comments: d.comments.map((c) => (c.id === id ? { ...c, reportedByMe: true } : c)) });
    } catch {
      setError("신고하지 못했습니다.");
    }
  }

  const shown = data.comments.filter((c) => !filter || `${c.anchorType}:${c.anchorRef ?? ""}` === filter);

  return (
    <Card as="section" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground-muted">리뷰 {data.comments.length}</h2>
        <span className="text-xs text-foreground-muted">노드나 대응 시점에 고정해 남길 수 있습니다</span>
      </div>

      {data.anchors.length > 0 && (
        <div className="flex flex-wrap gap-1.5 text-xs">
          <button type="button" onClick={() => setFilter(null)} className={`rounded-full border px-2 py-0.5 ${filter === null ? "border-accent" : "border-border"}`}>
            전체
          </button>
          {data.anchors.map((a) => {
            const key = `${a.type}:${a.ref}`;
            const n = grouped.get(key) ?? 0;
            return (
              <button
                key={key}
                type="button"
                onClick={() => setFilter(filter === key ? null : key)}
                className={`rounded-full border px-2 py-0.5 ${filter === key ? "border-accent" : "border-border"} ${n === 0 ? "text-foreground-muted" : ""}`}
              >
                {a.type === "NODE" ? "◇ " : "⏱ "}
                {prettyAnchor(a.label)}
                {n > 0 && ` ${n}`}
              </button>
            );
          })}
        </div>
      )}

      <ul className="flex flex-col gap-2">
        {shown.length === 0 && <li className="text-sm text-foreground-muted">아직 리뷰가 없습니다.</li>}
        {shown.map((c) => (
          <li key={c.id} className="rounded-lg border border-border p-3 text-sm">
            <div className="mb-1 flex flex-wrap items-center gap-2 text-xs text-foreground-muted">
              <Badge variant={c.kind === "RISK" ? "danger" : "neutral"}>{KIND_LABEL[c.kind]}</Badge>
              {c.anchorType !== "NONE" && <span>{c.anchorType === "NODE" ? "◇" : "⏱"} {prettyAnchor(c.anchorLabel)}</span>}
              <span className="font-medium text-foreground">{c.authorNickname}</span>
              {!c.mine &&
                (c.reportedByMe ? (
                  <span className="ml-auto">신고함</span>
                ) : (
                  <button type="button" className="ml-auto underline" onClick={() => report(c.id)}>
                    신고
                  </button>
                ))}
            </div>
            <p className="whitespace-pre-wrap">{c.body}</p>
            <ReactionBar targetType="WRITEUP_COMMENT" targetId={c.id} initial={c.reactions} mine={c.mine} />
          </li>
        ))}
      </ul>

      <div className="flex flex-col gap-2 border-t border-border pt-3">
        <div className="flex flex-wrap gap-1.5">
          {REVIEW_KINDS.map((k) => (
            <button
              key={k.kind}
              type="button"
              title={k.hint}
              onClick={() => setKind(k.kind)}
              className={`rounded-full border px-2.5 py-0.5 text-xs ${kind === k.kind ? "border-accent text-foreground" : "border-border text-foreground-muted"}`}
            >
              {k.label}
            </button>
          ))}
        </div>
        <select value={anchor} onChange={(e) => setAnchor(e.target.value)} className="rounded-lg border border-border bg-background px-2 py-1 text-xs" aria-label="리뷰 위치">
          <option value="NONE:">풀이 전체</option>
          {data.anchors.map((a) => (
            <option key={`${a.type}:${a.ref}`} value={`${a.type}:${a.ref}`}>
              {a.type === "NODE" ? "노드 · " : "타임라인 · "}
              {prettyAnchor(a.label)}
            </option>
          ))}
        </select>
        <Textarea value={body} onChange={(e) => setBody(e.target.value)} maxLength={2000} className="min-h-[80px] text-sm" placeholder="예: 이 DB가 단일 장애 지점이 되지 않나요? 쓰기 폭주 때는요?" />
        {error && <p className="text-sm text-danger">{error}</p>}
        <Button size="sm" className="self-start" onClick={submit} disabled={busy || !body.trim()}>
          {busy ? "남기는 중..." : `${KIND_LABEL[kind]} 남기기`}
        </Button>
      </div>
    </Card>
  );
}
