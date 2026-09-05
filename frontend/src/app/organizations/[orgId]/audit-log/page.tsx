"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ApiError, AuditLogEntry, OrganizationAuditAction, OrganizationDetail, getOrganization, listAuditLog } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";

const ACTION_LABELS: Record<OrganizationAuditAction, string> = {
  ORGANIZATION_CREATED: "조직 생성",
  MEMBER_INVITED: "멤버 초대",
  INVITATION_REVOKED: "초대 취소",
  MEMBER_JOINED: "멤버 가입",
  MEMBER_REMOVED: "멤버 제거",
  MEMBER_LEFT: "멤버 탈퇴",
  CUSTOM_SCENARIO_CREATED: "커스텀 시나리오 생성",
};

function formatDetail(entry: AuditLogEntry): string {
  if (!entry.detail) return "-";
  return Object.entries(entry.detail)
    .map(([key, value]) => `${key}: ${value}`)
    .join(", ");
}

export default function OrganizationAuditLogPage() {
  const params = useParams<{ orgId: string }>();
  const router = useRouter();
  const orgId = params.orgId;

  const [org, setOrg] = useState<OrganizationDetail | null>(null);
  const [entries, setEntries] = useState<AuditLogEntry[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    Promise.all([getOrganization(orgId), listAuditLog(orgId)])
      .then(([orgDetail, auditEntries]) => {
        setOrg(orgDetail);
        setEntries(auditEntries);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setError("이 감사 로그는 관리자만 볼 수 있습니다.");
        } else {
          setError(err instanceof ApiError ? err.message : "감사 로그를 불러오지 못했습니다.");
        }
      })
      .finally(() => setLoading(false));
  }, [router, orgId]);

  if (loading) return <p className="p-8 text-sm text-zinc-500">불러오는 중...</p>;
  if (error) {
    return (
      <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-4 p-8">
        <Link href={`/organizations/${orgId}`} className="text-sm text-zinc-500 underline">
          조직 상세로
        </Link>
        <p className="text-sm text-red-600">{error}</p>
      </div>
    );
  }
  if (!org || !entries) return null;

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 p-8">
      <div>
        <Link href={`/organizations/${orgId}`} className="text-sm text-zinc-500 underline">
          {org.name} 조직 상세로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">감사 로그</h1>
        <p className="mt-1 text-sm text-zinc-500">조직 관리 활동 기록 (최근 {entries.length}건)</p>
      </div>

      <section className="overflow-x-auto rounded border border-zinc-300 dark:border-zinc-700">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-zinc-300 text-left text-xs text-zinc-500 dark:border-zinc-700">
              <th className="px-4 py-2 font-medium">시각</th>
              <th className="px-4 py-2 font-medium">행위자</th>
              <th className="px-4 py-2 font-medium">행동</th>
              <th className="px-4 py-2 font-medium">상세</th>
            </tr>
          </thead>
          <tbody>
            {entries.length === 0 && (
              <tr>
                <td colSpan={4} className="px-4 py-6 text-center text-xs text-zinc-500">
                  아직 기록된 활동이 없습니다.
                </td>
              </tr>
            )}
            {entries.map((entry) => (
              <tr key={entry.id} className="border-b border-zinc-200 last:border-0 dark:border-zinc-800">
                <td className="px-4 py-2 text-xs text-zinc-500">
                  {entry.createdAt ? new Date(entry.createdAt).toLocaleString("ko-KR") : "-"}
                </td>
                <td className="px-4 py-2">
                  {entry.actorNickname} <span className="text-xs text-zinc-500">({entry.actorEmail})</span>
                </td>
                <td className="px-4 py-2">{ACTION_LABELS[entry.action] ?? entry.action}</td>
                <td className="px-4 py-2 text-xs text-zinc-500">{formatDetail(entry)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
