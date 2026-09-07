"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError, CertificationStatus, getMyCertification } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";

export default function CertificationsPage() {
  const router = useRouter();

  const [status, setStatus] = useState<CertificationStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  if (loading) return <p className="p-8 text-sm text-zinc-500">불러오는 중...</p>;
  if (error && !status) return <p className="p-8 text-sm text-red-600">{error}</p>;
  if (!status) return null;

  const verifyUrl = typeof window !== "undefined" ? `${window.location.origin}/certifications/${status.userId}` : "";

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <Link href="/dashboard" className="text-sm text-zinc-500 underline">
          대시보드로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">SysDrill Certified Incident Responder</h1>
        <p className="mt-1 text-sm text-zinc-500">
          발급·저장되는 자격증이 아니라, 조회할 때마다 최신 기록으로 다시 계산되는 실시간 판정입니다.
        </p>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-zinc-500">전체 인증 상태</h2>
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

      {verifyUrl && (
        <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
          <h2 className="mb-2 text-sm font-semibold text-zinc-500">공개 검증 링크</h2>
          <p className="mb-2 text-xs text-zinc-500">이 링크를 공유하면 누구나 로그인 없이 인증 여부를 확인할 수 있습니다.</p>
          <code className="break-all text-xs">{verifyUrl}</code>
        </section>
      )}
    </div>
  );
}
