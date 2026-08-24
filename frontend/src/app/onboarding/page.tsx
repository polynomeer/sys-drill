"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, createUser } from "@/lib/api";
import { storeUser } from "@/lib/localSession";

export default function OnboardingPage() {
  const router = useRouter();
  const [nickname, setNickname] = useState("");
  const [experienceYears, setExperienceYears] = useState("");
  const [primaryStack, setPrimaryStack] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!nickname.trim()) {
      setError("닉네임을 입력해주세요.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const user = await createUser({
        nickname: nickname.trim(),
        experienceYears: experienceYears ? Number(experienceYears) : undefined,
        primaryStack: primaryStack.trim() || undefined,
      });
      storeUser(user.id, user.nickname);
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "가입 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">SysDrill 시작하기</h1>
        <p className="mt-1 text-sm text-zinc-500">
          간단한 정보만 입력하면 바로 첫 훈련을 시작할 수 있습니다.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm">
          닉네임
          <input
            className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="drill-user"
            autoFocus
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          연차 (선택)
          <input
            className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
            type="number"
            min={0}
            value={experienceYears}
            onChange={(e) => setExperienceYears(e.target.value)}
            placeholder="3"
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          주 사용 스택 (선택)
          <input
            className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
            value={primaryStack}
            onChange={(e) => setPrimaryStack(e.target.value)}
            placeholder="Kotlin / Spring Boot"
          />
        </label>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <button
          type="submit"
          disabled={submitting}
          className="rounded bg-foreground px-4 py-2 font-medium text-background disabled:opacity-50"
        >
          {submitting ? "시작하는 중..." : "시작하기"}
        </button>
      </form>
    </div>
  );
}
