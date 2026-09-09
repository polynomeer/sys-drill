"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ApiError, confirmPasswordReset, requestPasswordReset } from "@/lib/api";

function ResetPasswordForm() {
  const router = useRouter();
  const token = useSearchParams().get("token");

  const [email, setEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [requestSent, setRequestSent] = useState(false);
  const [resetDone, setResetDone] = useState(false);

  async function handleRequest(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await requestPasswordReset(email.trim());
      setRequestSent(true);
    } catch {
      setError("요청 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleConfirm(e: React.FormEvent) {
    e.preventDefault();
    if (!token) return;
    setSubmitting(true);
    setError(null);
    try {
      await confirmPasswordReset(token, newPassword);
      setResetDone(true);
      setTimeout(() => router.push("/login"), 1500);
    } catch (err) {
      setError(err instanceof ApiError ? "링크가 만료되었거나 이미 사용되었습니다." : "재설정 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">비밀번호 재설정</h1>
      </div>

      {!token && (
        <form onSubmit={handleRequest} className="flex flex-col gap-4">
          {requestSent ? (
            <p className="text-sm text-zinc-500">
              해당 이메일 계정이 존재하면 재설정 링크를 보냈습니다. 메일함(및 로그)을 확인해주세요.
            </p>
          ) : (
            <>
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
              {error && <p className="text-sm text-red-600">{error}</p>}
              <button
                type="submit"
                disabled={submitting || !email.trim()}
                className="rounded bg-foreground px-4 py-2 font-medium text-background disabled:opacity-50"
              >
                {submitting ? "요청하는 중..." : "재설정 링크 받기"}
              </button>
            </>
          )}
        </form>
      )}

      {token && (
        <form onSubmit={handleConfirm} className="flex flex-col gap-4">
          {resetDone ? (
            <p className="text-sm text-zinc-500">비밀번호가 변경되었습니다. 로그인 화면으로 이동합니다...</p>
          ) : (
            <>
              <label className="flex flex-col gap-1 text-sm">
                새 비밀번호 (8자 이상)
                <input
                  className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="********"
                  autoFocus
                />
              </label>
              {error && <p className="text-sm text-red-600">{error}</p>}
              <button
                type="submit"
                disabled={submitting || newPassword.length < 8}
                className="rounded bg-foreground px-4 py-2 font-medium text-background disabled:opacity-50"
              >
                {submitting ? "재설정하는 중..." : "비밀번호 재설정"}
              </button>
            </>
          )}
        </form>
      )}

      <Link href="/login" className="text-center text-sm text-zinc-500 underline">
        로그인으로 돌아가기
      </Link>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense>
      <ResetPasswordForm />
    </Suspense>
  );
}
