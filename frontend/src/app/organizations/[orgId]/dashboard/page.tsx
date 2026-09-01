"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ApiError, OrganizationDashboard, OrganizationDetail, getOrganization, getOrganizationDashboard } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "관리자",
  MEMBER: "멤버",
};

const TREND_DIRECTION_LABELS: Record<string, { text: string; className: string }> = {
  IMPROVING: { text: "▲ 상승", className: "text-emerald-600 dark:text-emerald-400" },
  DECLINING: { text: "▼ 하락", className: "text-red-600 dark:text-red-400" },
  STABLE: { text: "▬ 안정", className: "text-zinc-500" },
  INSUFFICIENT_DATA: { text: "데이터 부족", className: "text-zinc-400" },
};

function formatLastActive(lastActiveAt: string | null): string {
  if (!lastActiveAt) return "훈련 이력 없음";
  return new Date(lastActiveAt).toLocaleString("ko-KR");
}

export default function OrganizationDashboardPage() {
  const params = useParams<{ orgId: string }>();
  const router = useRouter();
  const orgId = params.orgId;

  const [org, setOrg] = useState<OrganizationDetail | null>(null);
  const [dashboard, setDashboard] = useState<OrganizationDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    Promise.all([getOrganization(orgId), getOrganizationDashboard(orgId)])
      .then(([orgDetail, dashboardData]) => {
        setOrg(orgDetail);
        setDashboard(dashboardData);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setError("이 대시보드는 관리자만 볼 수 있습니다.");
        } else {
          setError(err instanceof ApiError ? err.message : "대시보드를 불러오지 못했습니다.");
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
  if (!org || !dashboard) return null;

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 p-8">
      <div>
        <Link href={`/organizations/${orgId}`} className="text-sm text-zinc-500 underline">
          {org.name} 조직 상세로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">팀 대시보드</h1>
        <p className="mt-1 text-sm text-zinc-500">멤버별 훈련 현황 ({dashboard.members.length}명)</p>
      </div>

      <section className="overflow-x-auto rounded border border-zinc-300 dark:border-zinc-700">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-zinc-300 text-left text-xs text-zinc-500 dark:border-zinc-700">
              <th className="px-4 py-2 font-medium">멤버</th>
              <th className="px-4 py-2 font-medium">역할</th>
              <th className="px-4 py-2 font-medium">완료 세션</th>
              <th className="px-4 py-2 font-medium">마지막 활동</th>
              <th className="px-4 py-2 font-medium">최근 추세</th>
            </tr>
          </thead>
          <tbody>
            {dashboard.members.map((member) => (
              <tr key={member.userId} className="border-b border-zinc-200 last:border-0 dark:border-zinc-800">
                <td className="px-4 py-2">
                  {member.nickname} <span className="text-xs text-zinc-500">({member.email})</span>
                </td>
                <td className="px-4 py-2 text-xs text-zinc-500">{ROLE_LABELS[member.role] ?? member.role}</td>
                <td className="px-4 py-2">{member.completedSessionCount}</td>
                <td className="px-4 py-2 text-xs text-zinc-500">{formatLastActive(member.lastActiveAt)}</td>
                <td className={`px-4 py-2 text-xs font-medium ${TREND_DIRECTION_LABELS[member.trendDirection].className}`}>
                  {TREND_DIRECTION_LABELS[member.trendDirection].text}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
