"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ApiError, CertificationStatus, getCertification } from "@/lib/api";

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

  if (loading) return <p className="p-8 text-sm text-zinc-500">불러오는 중...</p>;
  if (error || !status) return <p className="p-8 text-sm text-red-600">{error ?? "인증 현황을 불러오지 못했습니다."}</p>;

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">{status.nickname}님의 SysDrill 인증 현황</h1>
        <p className="mt-1 text-sm text-zinc-500">공개 검증 페이지 — 로그인 없이 누구나 확인할 수 있습니다.</p>
      </div>

      <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-zinc-500">SysDrill Certified Incident Responder</h2>
          <span
            className={`rounded px-2 py-1 text-xs font-medium ${
              status.certified
                ? "bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300"
                : "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400"
            }`}
          >
            {status.certified ? "인증됨" : "미인증"}
          </span>
        </div>
        <ul className="flex flex-col gap-2">
          {status.domains.map((d) => (
            <li key={d.domain} className="flex items-center justify-between text-sm">
              <span>
                {d.passed ? "✅" : "⬜️"} {d.title}
                <span className="ml-2 text-xs text-zinc-500">({d.domain})</span>
              </span>
              <span className="text-xs text-zinc-500">{d.bestScore !== null ? `최고 ${d.bestScore}점` : "미완료"}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
