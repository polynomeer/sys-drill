"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError, OrganizationSummary, createOrganization, listOrganizations } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "관리자",
  MEMBER: "멤버",
};

export default function OrganizationsPage() {
  const router = useRouter();
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    listOrganizations()
      .then(setOrganizations)
      .catch((err) => setError(err instanceof ApiError ? err.message : "조직 목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setCreating(true);
    setError(null);
    try {
      const created = await createOrganization(name.trim());
      router.push(`/organizations/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "조직을 만들지 못했습니다.");
      setCreating(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <Link href="/dashboard" className="text-sm text-zinc-500 underline">
          대시보드로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">내 조직</h1>
        <p className="mt-1 text-sm text-zinc-500">소속된 조직 목록입니다. 새 조직을 만들거나 초대를 기다리세요.</p>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {loading && <p className="text-sm text-zinc-500">불러오는 중...</p>}

      {!loading && organizations.length === 0 && (
        <p className="text-sm text-zinc-500">아직 속한 조직이 없습니다.</p>
      )}

      <ul className="flex flex-col gap-3">
        {organizations.map((org) => (
          <li key={org.id}>
            <Link
              href={`/organizations/${org.id}`}
              className="flex items-center justify-between rounded border border-zinc-300 p-4 dark:border-zinc-700"
            >
              <span className="font-medium">{org.name}</span>
              <span className="text-xs text-zinc-500">{ROLE_LABELS[org.myRole] ?? org.myRole}</span>
            </Link>
          </li>
        ))}
      </ul>

      <form onSubmit={handleCreate} className="flex flex-col gap-3 rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <h2 className="text-sm font-semibold text-zinc-500">새 조직 만들기</h2>
        <div className="flex gap-2">
          <input
            className="flex-1 rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="조직 이름"
          />
          <button
            type="submit"
            disabled={creating}
            className="rounded bg-foreground px-4 py-2 text-sm font-medium text-background disabled:opacity-50"
          >
            {creating ? "만드는 중..." : "만들기"}
          </button>
        </div>
      </form>
    </div>
  );
}
