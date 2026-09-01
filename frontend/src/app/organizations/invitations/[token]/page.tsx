"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ApiError, InvitationPreview, acceptInvitation, previewInvitation } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "관리자",
  MEMBER: "멤버",
};

export default function InvitationAcceptPage() {
  const params = useParams<{ token: string }>();
  const router = useRouter();
  const token = params.token;

  const [preview, setPreview] = useState<InvitationPreview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [accepting, setAccepting] = useState(false);

  const load = useCallback(async () => {
    setPreview(await previewInvitation(token));
  }, [token]);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    load()
      .catch((err) =>
        setError(
          err instanceof ApiError && err.status === 404
            ? "이 초대는 다른 이메일 계정으로 발송되었거나 존재하지 않습니다."
            : "초대 정보를 불러오지 못했습니다."
        )
      )
      .finally(() => setLoading(false));
  }, [router, load]);

  async function handleAccept() {
    setAccepting(true);
    setError(null);
    try {
      const org = await acceptInvitation(token);
      router.push(`/organizations/${org.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "초대를 수락하지 못했습니다.");
      setAccepting(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">조직 초대</h1>
      </div>

      {loading && <p className="text-sm text-zinc-500">불러오는 중...</p>}
      {error && <p className="text-sm text-red-600">{error}</p>}

      {preview && (
        <div className="flex flex-col gap-4 rounded border border-zinc-300 p-4 dark:border-zinc-700">
          <p className="text-sm">
            <span className="font-medium">{preview.organizationName}</span>에{" "}
            <span className="font-medium">{ROLE_LABELS[preview.role] ?? preview.role}</span>(으)로 초대되었습니다.
          </p>
          <p className="text-xs text-zinc-500">초대받은 이메일: {preview.inviteeEmail}</p>

          {preview.expired && <p className="text-sm text-red-600">이 초대는 만료되었습니다.</p>}
          {preview.alreadyResolved && !preview.expired && (
            <p className="text-sm text-red-600">이 초대는 이미 처리되었습니다.</p>
          )}

          {!preview.expired && !preview.alreadyResolved && (
            <button
              onClick={handleAccept}
              disabled={accepting}
              className="rounded bg-foreground px-4 py-2 text-sm font-medium text-background disabled:opacity-50"
            >
              {accepting ? "수락하는 중..." : "수락"}
            </button>
          )}
        </div>
      )}

      <Link href="/organizations" className="text-center text-sm text-zinc-500 underline">
        내 조직 목록으로
      </Link>
    </div>
  );
}
