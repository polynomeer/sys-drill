"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ApiError, CertificationStatus, getCertification } from "@/lib/api";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

/** Public verification page — no login required, works for any visitor. */
export default function CertificationVerificationPage() {
  const params = useParams<{ userId: string }>();

  const [status, setStatus] = useState<CertificationStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getCertification(params.userId)
      .then(setStatus)
      .catch((err) => setError(err instanceof ApiError && err.status === 404 ? "존재하지 않는 사용자입니다." : "인증 현황을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [params.userId]);

  if (loading) return <LoadingState className="p-8" />;
  if (error || !status) return <p className="p-8 text-sm text-danger">{error ?? "인증 현황을 불러오지 못했습니다."}</p>;

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">{status.nickname}님의 SysDrill 인증 현황</h1>
        <p className="mt-1 text-sm text-foreground-muted">공개 검증 페이지 — 로그인 없이 누구나 확인할 수 있습니다.</p>
      </div>

      <Card as="section">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-foreground-muted">SysDrill Certified Incident Responder</h2>
          <Badge variant={status.certified ? "success" : "neutral"}>{status.certified ? "인증됨" : "미인증"}</Badge>
        </div>
        <ul className="flex flex-col gap-2">
          {status.domains.map((d) => (
            <li key={d.domain} className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-2">
                {d.title}
                <span className="text-xs text-foreground-muted">({d.domain})</span>
              </span>
              <span className="flex items-center gap-2">
                <span className="text-xs text-foreground-muted">{d.bestScore !== null ? `최고 ${d.bestScore}점` : "미완료"}</span>
                <Badge variant={d.passed ? "success" : "neutral"}>{d.passed ? "완료" : "미완료"}</Badge>
              </span>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
