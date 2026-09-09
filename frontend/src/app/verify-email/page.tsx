"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { verifyEmail } from "@/lib/api";

function VerifyEmailStatus() {
  const token = useSearchParams().get("token");
  const [status, setStatus] = useState<"verifying" | "done" | "error">("verifying");
  // React Strict Mode double-invokes effects in dev, which would otherwise
  // fire this token-consuming call twice -- the second call 400s (token
  // already used) and its rejection can land after the first call's success,
  // clobbering the correct "done" status with "error". Guard against
  // re-running for the same mount regardless of how many times the effect body executes.
  const calledRef = useRef(false);

  useEffect(() => {
    if (calledRef.current) return;
    calledRef.current = true;

    if (!token) {
      setStatus("error");
      return;
    }
    verifyEmail(token)
      .then(() => setStatus("done"))
      .catch(() => setStatus("error"));
  }, [token]);

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-4 p-8 text-center">
      <h1 className="text-2xl font-semibold">이메일 인증</h1>
      {status === "verifying" && <p className="text-sm text-foreground-muted">확인하는 중...</p>}
      {status === "done" && <p className="text-sm text-success">이메일이 인증되었습니다.</p>}
      {status === "error" && <p className="text-sm text-danger">유효하지 않거나 만료된 링크입니다.</p>}
      <Link href="/dashboard" className="text-sm text-foreground-muted underline">
        대시보드로
      </Link>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense>
      <VerifyEmailStatus />
    </Suspense>
  );
}
