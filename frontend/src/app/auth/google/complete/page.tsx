"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { storeUser } from "@/lib/localSession";

/** PLAN.md step 37 — the backend redirects here after a successful Google login, with the token/nickname in the URL fragment (never sent to a server, never logged). */
export default function GoogleAuthCompletePage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const hash = new URLSearchParams(window.location.hash.replace(/^#/, ""));
    const token = hash.get("token");
    const nickname = hash.get("nickname");
    if (!token || !nickname) {
      // One-time redirect-result parsing on mount, not a cascading render loop.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setError("로그인 정보를 확인하지 못했습니다.");
      return;
    }
    storeUser(nickname, token);
    router.replace("/dashboard");
  }, [router]);

  if (error) {
    return (
      <div className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center gap-4 p-8 text-center">
        <p className="text-sm text-red-600">{error}</p>
        <Link href="/login" className="text-sm underline">
          로그인으로 돌아가기
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center p-8">
      <p className="text-sm text-zinc-500">로그인하는 중...</p>
    </div>
  );
}
