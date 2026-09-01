"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ApiError,
  OrganizationDetail,
  OrganizationInvitation,
  OrganizationRole,
  getOrganization,
  inviteMember,
  leaveOrganization,
  listInvitations,
  removeMember,
  revokeInvitation,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "관리자",
  MEMBER: "멤버",
};

export default function OrganizationDetailPage() {
  const params = useParams<{ orgId: string }>();
  const router = useRouter();
  const orgId = params.orgId;

  const [org, setOrg] = useState<OrganizationDetail | null>(null);
  const [invitations, setInvitations] = useState<OrganizationInvitation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<OrganizationRole>("MEMBER");
  const [inviting, setInviting] = useState(false);
  const [lastInviteToken, setLastInviteToken] = useState<string | null>(null);

  const load = useCallback(async () => {
    const detail = await getOrganization(orgId);
    setOrg(detail);
    if (detail.myRole === "ADMIN") {
      setInvitations(await listInvitations(orgId));
    }
  }, [orgId]);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    load()
      .catch((err) => setError(err instanceof ApiError ? err.message : "조직 정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router, load]);

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    if (!inviteEmail.trim()) return;
    setInviting(true);
    setError(null);
    try {
      const invitation = await inviteMember(orgId, inviteEmail.trim(), inviteRole);
      setLastInviteToken(invitation.token);
      setInviteEmail("");
      setInvitations(await listInvitations(orgId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "초대를 만들지 못했습니다.");
    } finally {
      setInviting(false);
    }
  }

  async function handleRevoke(invitationId: string) {
    setError(null);
    try {
      await revokeInvitation(orgId, invitationId);
      setInvitations(await listInvitations(orgId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "초대를 취소하지 못했습니다.");
    }
  }

  async function handleRemove(targetUserId: string) {
    setError(null);
    try {
      await removeMember(orgId, targetUserId);
      setOrg(await getOrganization(orgId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "멤버를 제거하지 못했습니다.");
    }
  }

  async function handleLeave() {
    setError(null);
    try {
      await leaveOrganization(orgId);
      router.push("/organizations");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "조직에서 나가지 못했습니다.");
    }
  }

  if (loading) return <p className="p-8 text-sm text-zinc-500">불러오는 중...</p>;
  if (error && !org) return <p className="p-8 text-sm text-red-600">{error}</p>;
  if (!org) return null;

  const inviteLink = lastInviteToken ? `${window.location.origin}/organizations/invitations/${lastInviteToken}` : null;

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <Link href="/organizations" className="text-sm text-zinc-500 underline">
          내 조직 목록으로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">{org.name}</h1>
        <p className="mt-1 text-sm text-zinc-500">내 역할: {ROLE_LABELS[org.myRole] ?? org.myRole}</p>
        {org.myRole === "ADMIN" && (
          <Link href={`/organizations/${orgId}/dashboard`} className="mt-2 inline-block text-sm text-blue-600 underline dark:text-blue-400">
            팀 대시보드 보기
          </Link>
        )}
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <h2 className="mb-3 text-sm font-semibold text-zinc-500">멤버 ({org.members.length}명)</h2>
        <ul className="flex flex-col gap-2">
          {org.members.map((member) => (
            <li key={member.userId} className="flex items-center justify-between text-sm">
              <span>
                {member.nickname} <span className="text-zinc-500">({member.email})</span>
              </span>
              <span className="flex items-center gap-2">
                <span className="text-xs text-zinc-500">{ROLE_LABELS[member.role] ?? member.role}</span>
                {org.myRole === "ADMIN" && (
                  <button onClick={() => handleRemove(member.userId)} className="text-xs text-red-600 underline">
                    제거
                  </button>
                )}
              </span>
            </li>
          ))}
        </ul>
        <button onClick={handleLeave} className="mt-4 text-xs text-zinc-500 underline">
          나가기
        </button>
      </section>

      {org.myRole === "ADMIN" && (
        <>
          <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
            <h2 className="mb-3 text-sm font-semibold text-zinc-500">멤버 초대</h2>
            <form onSubmit={handleInvite} className="flex flex-wrap gap-2">
              <input
                className="flex-1 rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
                type="email"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                placeholder="invitee@example.com"
              />
              <select
                className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value as OrganizationRole)}
              >
                <option value="MEMBER">멤버</option>
                <option value="ADMIN">관리자</option>
              </select>
              <button
                type="submit"
                disabled={inviting}
                className="rounded bg-foreground px-4 py-2 text-sm font-medium text-background disabled:opacity-50"
              >
                {inviting ? "초대하는 중..." : "초대"}
              </button>
            </form>
            {inviteLink && (
              <div className="mt-3 rounded bg-zinc-100 p-3 text-xs dark:bg-zinc-900">
                <p className="mb-1 text-zinc-500">이 코드를 Slack 등으로 직접 전달하세요 (이메일은 자동 발송되지 않습니다):</p>
                <code className="break-all">{inviteLink}</code>
              </div>
            )}
          </section>

          <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
            <h2 className="mb-3 text-sm font-semibold text-zinc-500">대기 중인 초대</h2>
            {invitations.length === 0 && <p className="text-sm text-zinc-500">대기 중인 초대가 없습니다.</p>}
            <ul className="flex flex-col gap-2">
              {invitations.map((inv) => (
                <li key={inv.id} className="flex items-center justify-between text-sm">
                  <span>
                    {inv.inviteeEmail} <span className="text-xs text-zinc-500">({ROLE_LABELS[inv.role] ?? inv.role})</span>
                    {inv.expired && (
                      <span className="ml-2 rounded bg-red-100 px-1.5 py-0.5 text-[10px] font-medium text-red-700 dark:bg-red-950 dark:text-red-300">
                        만료됨
                      </span>
                    )}
                  </span>
                  <button onClick={() => handleRevoke(inv.id)} className="text-xs text-red-600 underline">
                    취소
                  </button>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  );
}
