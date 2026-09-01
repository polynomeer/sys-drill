"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError, login } from "@/lib/api";
import { storeUser } from "@/lib/localSession";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!email.trim() || !password) {
      setError("이메일과 비밀번호를 입력해주세요.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const { token, user } = await login(email.trim(), password);
      storeUser(user.id, user.nickname, token);
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? "이메일 또는 비밀번호가 올바르지 않습니다." : "로그인 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">로그인</h1>
        <p className="mt-1 text-sm text-zinc-500">이메일과 비밀번호로 로그인하세요.</p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm">
          이메일
          <input
            className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            autoFocus
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          비밀번호
          <input
            className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="********"
          />
        </label>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <button
          type="submit"
          disabled={submitting}
          className="rounded bg-foreground px-4 py-2 font-medium text-background disabled:opacity-50"
        >
          {submitting ? "로그인하는 중..." : "로그인"}
        </button>
      </form>

      <p className="text-center text-sm text-zinc-500">
        아직 계정이 없으신가요?{" "}
        <Link href="/onboarding" className="underline">
          가입하기
        </Link>
      </p>
    </div>
  );
}
