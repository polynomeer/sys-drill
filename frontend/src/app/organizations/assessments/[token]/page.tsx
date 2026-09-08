"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ApiError, AssessmentPreview, previewAssessment, startAssessment } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";

export default function AssessmentPreviewPage() {
  const params = useParams<{ token: string }>();
  const router = useRouter();

  const [preview, setPreview] = useState<AssessmentPreview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);
  const [loggedIn, setLoggedIn] = useState(false);

  useEffect(() => {
    setLoggedIn(!!getStoredToken());
    previewAssessment(params.token)
      .then(setPreview)
      .catch((err) => setError(err instanceof ApiError ? err.message : "평가 정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [params.token]);

  async function handleStart() {
    setStarting(true);
    setError(null);
    try {
      const session = await startAssessment(params.token);
      router.push(`/design/${session.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "평가를 시작하지 못했습니다.");
      setStarting(false);
    }
  }

  if (loading) return <p className="p-8 text-sm text-zinc-500">불러오는 중...</p>;
  if (error && !preview) return <p className="p-8 text-sm text-red-600">{error}</p>;
  if (!preview) return null;

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">{preview.organizationName}의 역량 평가</h1>
        <p className="mt-1 text-sm text-zinc-500">
          {preview.candidateEmail}님께 발송된 평가입니다. 시나리오: {preview.scenarioTitle} ({preview.scenarioDomain})
        </p>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {preview.expired && <p className="text-sm text-red-600">이 평가는 만료되었습니다. 조직 관리자에게 문의하세요.</p>}
      {!preview.expired && preview.alreadyStarted && <p className="text-sm text-zinc-500">이 평가는 이미 시작되었습니다.</p>}

      {!preview.expired && !preview.alreadyStarted && (
        <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
          {loggedIn ? (
            <button
              onClick={handleStart}
              disabled={starting}
              className="rounded bg-foreground px-4 py-2 text-sm font-medium text-background disabled:opacity-50"
            >
              {starting ? "시작하는 중..." : "평가 시작하기"}
            </button>
          ) : (
            <div className="flex flex-col gap-2">
              <p className="text-sm text-zinc-500">
                평가를 치르려면 <strong>{preview.candidateEmail}</strong>로 가입하거나 로그인해야 합니다.
              </p>
              <div className="flex gap-4">
                <Link href="/onboarding" className="text-sm text-blue-600 underline dark:text-blue-400">
                  가입하기
                </Link>
                <Link href="/login" className="text-sm text-blue-600 underline dark:text-blue-400">
                  로그인
                </Link>
              </div>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
