"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, CertificationStatus, getMyCertification } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

export default function CertificationsPage() {
  const router = useRouter();

  const [status, setStatus] = useState<CertificationStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    getMyCertification()
      .then(setStatus)
      .catch((err) => setError(err instanceof ApiError ? err.message : "인증 현황을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router]);

  if (loading) return <LoadingState className="p-8" />;
  if (error && !status) return <p className="p-8 text-sm text-danger">{error}</p>;
  if (!status) return null;

  const verifyUrl = typeof window !== "undefined" ? `${window.location.origin}/certifications/${status.userId}` : "";
  const passedCount = status.domains.filter((d) => d.passed).length;

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">SysDrill Certified Incident Responder</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          발급·저장되는 자격증이 아니라, 조회할 때마다 최신 기록으로 다시 계산되는 실시간 판정입니다.
        </p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Card as="section">
        <div className="mb-3 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-foreground-muted">전체 인증 상태</h2>
            <p className="text-xs text-foreground-muted">
              {passedCount}/{status.domains.length}개 도메인 완료
            </p>
          </div>
          <Badge variant={status.certified ? "success" : "neutral"}>{status.certified ? "인증됨" : "미인증"}</Badge>
        </div>
        <div className="mb-3 h-1.5 overflow-hidden rounded-full bg-surface-elevated">
          <div
            className="h-full rounded-full bg-accent transition-all"
            style={{ width: `${status.domains.length ? (passedCount / status.domains.length) * 100 : 0}%` }}
          />
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

      {verifyUrl && (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">공개 검증 링크</h2>
          <p className="mb-2 text-xs text-foreground-muted">이 링크를 공유하면 누구나 로그인 없이 인증 여부를 확인할 수 있습니다.</p>
          <div className="flex items-center gap-2">
            <code className="flex-1 truncate rounded bg-surface-elevated px-2 py-1 text-xs ">{verifyUrl}</code>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                navigator.clipboard.writeText(verifyUrl).then(() => {
                  setCopied(true);
                  setTimeout(() => setCopied(false), 1500);
                });
              }}
            >
              {copied ? "복사됨" : "복사"}
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}
