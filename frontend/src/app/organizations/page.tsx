"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError, OrganizationSummary, createOrganization, listOrganizations } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Input } from "@/components/ui/Input";
import { LoadingState } from "@/components/ui/LoadingState";

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
    <div className="mx-auto flex max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">내 조직</h1>
        <p className="mt-1 text-sm text-foreground-muted">소속된 조직 목록입니다. 새 조직을 만들거나 초대를 기다리세요.</p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}
      {loading && <LoadingState />}

      {!loading && organizations.length === 0 && <EmptyState message="아직 속한 조직이 없습니다." />}

      <ul className="flex flex-col gap-3">
        {organizations.map((org) => (
          <li key={org.id}>
            <Link href={`/organizations/${org.id}`}>
              <Card className="flex items-center justify-between">
                <span className="font-medium">{org.name}</span>
                <Badge>{ROLE_LABELS[org.myRole] ?? org.myRole}</Badge>
              </Card>
            </Link>
          </li>
        ))}
      </ul>

      <Card as="section">
        <form onSubmit={handleCreate} className="flex flex-col gap-3">
          <h2 className="text-sm font-semibold text-foreground-muted">새 조직 만들기</h2>
          <div className="flex gap-2">
            <Input className="flex-1" value={name} onChange={(e) => setName(e.target.value)} placeholder="조직 이름" />
            <Button type="submit" disabled={creating}>
              {creating ? "만드는 중..." : "만들기"}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
