"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AdminDashboardStats, ApiError, getAdminDashboardStats } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

/** docs/COMMERCIALIZATION.md — no separate role check on the frontend; the backend's PlatformAccessGuard is the real gate, this page just renders whatever it returns (stats, or a 403 message). */
export default function AdminDashboardPage() {
  const router = useRouter();
  const [stats, setStats] = useState<AdminDashboardStats | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    getAdminDashboardStats()
      .then(setStats)
      .catch((err) =>
        setError(
          err instanceof ApiError && err.status === 403
            ? "관리자 권한이 필요합니다."
            : "대시보드를 불러오지 못했습니다.",
        ),
      )
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router]);

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">운영 대시보드</h1>
      </div>

      {loading && <LoadingState />}
      {error && <p className="text-sm text-danger">{error}</p>}

      {stats && (
        <div className="grid grid-cols-2 gap-4">
          <StatCard label="총 사용자" value={stats.totalUsers} />
          <StatCard label="오늘 신규 가입" value={stats.newUsersToday} />
          <StatCard label="총 조직 수" value={stats.totalOrganizations} />
          <StatCard label="오늘 완료된 세션" value={stats.sessionsCompletedToday} />
        </div>
      )}
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <p className="text-sm text-foreground-muted">{label}</p>
      <p className="text-3xl font-semibold">{value}</p>
    </Card>
  );
}
