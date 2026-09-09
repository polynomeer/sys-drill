"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError, signup } from "@/lib/api";
import { storeUser } from "@/lib/localSession";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";

export default function OnboardingPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [nickname, setNickname] = useState("");
  const [experienceYears, setExperienceYears] = useState("");
  const [primaryStack, setPrimaryStack] = useState("");
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!email.trim() || !password || !nickname.trim()) {
      setError("이메일, 비밀번호, 닉네임을 모두 입력해주세요.");
      return;
    }
    if (!termsAccepted) {
      setError("이용약관과 개인정보처리방침에 동의해야 가입할 수 있습니다.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const { token, user } = await signup({
        email: email.trim(),
        password,
        nickname: nickname.trim(),
        experienceYears: experienceYears ? Number(experienceYears) : undefined,
        primaryStack: primaryStack.trim() || undefined,
        termsAccepted,
      });
      storeUser(user.nickname, token);
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
        <p className="mt-1 text-sm text-foreground-muted">이메일과 비밀번호로 가입하면 바로 첫 훈련을 시작할 수 있습니다.</p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input
          label="이메일"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
          autoFocus
        />

        <Input
          label="비밀번호 (8자 이상)"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="********"
        />

        <Input label="닉네임" value={nickname} onChange={(e) => setNickname(e.target.value)} placeholder="drill-user" />

        <Input
          label="연차 (선택)"
          type="number"
          min={0}
          value={experienceYears}
          onChange={(e) => setExperienceYears(e.target.value)}
          placeholder="3"
        />

        <Input
          label="주 사용 스택 (선택)"
          value={primaryStack}
          onChange={(e) => setPrimaryStack(e.target.value)}
          placeholder="Kotlin / Spring Boot"
        />

        <label className="flex items-start gap-2 text-sm text-foreground-muted">
          <input
            type="checkbox"
            checked={termsAccepted}
            onChange={(e) => setTermsAccepted(e.target.checked)}
            className="mt-0.5"
          />
          <span>
            <Link href="/terms" className="underline" target="_blank">
              이용약관
            </Link>
            과{" "}
            <Link href="/privacy" className="underline" target="_blank">
              개인정보처리방침
            </Link>
            에 동의합니다.
          </span>
        </label>

        {error && <p className="text-sm text-danger">{error}</p>}

        <Button type="submit" disabled={submitting} className="w-full">
          {submitting ? "가입하는 중..." : "가입하고 시작하기"}
        </Button>
      </form>

      <p className="text-center text-sm text-foreground-muted">
        이미 계정이 있으신가요?{" "}
        <Link href="/login" className="underline">
          로그인
        </Link>
      </p>
    </div>
  );
}
