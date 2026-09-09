"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { API_BASE_URL, ApiError, login } from "@/lib/api";
import { storeUser } from "@/lib/localSession";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";

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
      storeUser(user.nickname, token);
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
        <p className="mt-1 text-sm text-foreground-muted">이메일과 비밀번호로 로그인하세요.</p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input label="이메일" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" autoFocus />

        <Input label="비밀번호" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="********" />

        {error && <p className="text-sm text-danger">{error}</p>}

        <Button type="submit" disabled={submitting} className="w-full">
          {submitting ? "로그인하는 중..." : "로그인"}
        </Button>

        <Link href="/reset-password" className="text-center text-xs text-foreground-muted underline">
          비밀번호를 잊으셨나요?
        </Link>
      </form>

      <div className="flex items-center gap-3 text-xs text-foreground-muted">
        <div className="h-px flex-1 bg-border" />
        또는
        <div className="h-px flex-1 bg-border" />
      </div>

      {/* PLAN.md step 37 — plain browser navigation, not a fetch call: this starts a redirect-based OAuth flow, so CORS never applies. */}
      <Button href={`${API_BASE_URL}/auth/google/login`} variant="secondary" className="w-full">
        Google로 계속하기
      </Button>

      <p className="text-center text-sm text-foreground-muted">
        아직 계정이 없으신가요?{" "}
        <Link href="/onboarding" className="underline">
          가입하기
        </Link>
      </p>
    </div>
  );
}
