"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ApiError,
  GameDaySession,
  OrganizationDetail,
  OrganizationInvitation,
  OrganizationRole,
  ScenarioSummary,
  createCustomScenario,
  getOrganization,
  inviteMember,
  leaveOrganization,
  listGameDaySessions,
  listInvitations,
  listOrganizationScenarios,
  removeMember,
  revokeInvitation,
  startSession,
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
  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [gameDaySessions, setGameDaySessions] = useState<GameDaySession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<OrganizationRole>("MEMBER");
  const [inviting, setInviting] = useState(false);
  const [lastInviteToken, setLastInviteToken] = useState<string | null>(null);

  const [scenarioTitle, setScenarioTitle] = useState("");
  const [scenarioDomain, setScenarioDomain] = useState("");
  const [scenarioDifficulty, setScenarioDifficulty] = useState("");
  const [scenarioInitialPrompt, setScenarioInitialPrompt] = useState("");
  const [scenarioFollowupPrompt, setScenarioFollowupPrompt] = useState("");
  const [creatingScenario, setCreatingScenario] = useState(false);
  const [startingScenarioId, setStartingScenarioId] = useState<string | null>(null);

  const load = useCallback(async () => {
    const detail = await getOrganization(orgId);
    setOrg(detail);
    setScenarios(await listOrganizationScenarios(orgId));
    setGameDaySessions(await listGameDaySessions(orgId));
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

  async function handleCreateScenario(e: React.FormEvent) {
    e.preventDefault();
    if (!scenarioTitle.trim() || !scenarioDomain.trim() || !scenarioInitialPrompt.trim() || !scenarioFollowupPrompt.trim()) return;
    setCreatingScenario(true);
    setError(null);
    try {
      await createCustomScenario(orgId, {
        title: scenarioTitle.trim(),
        difficulty: scenarioDifficulty.trim() || undefined,
        domain: scenarioDomain.trim(),
        initialPrompt: scenarioInitialPrompt.trim(),
        followupPrompt: scenarioFollowupPrompt.trim(),
      });
      setScenarioTitle("");
      setScenarioDomain("");
      setScenarioDifficulty("");
      setScenarioInitialPrompt("");
      setScenarioFollowupPrompt("");
      setScenarios(await listOrganizationScenarios(orgId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "시나리오를 만들지 못했습니다.");
    } finally {
      setCreatingScenario(false);
    }
  }

  async function handleStartScenario(scenarioId: string) {
    setStartingScenarioId(scenarioId);
    setError(null);
    try {
      const session = await startSession(scenarioId);
      router.push(`/design/${session.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "세션을 시작하지 못했습니다.");
      setStartingScenarioId(null);
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

      <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <h2 className="mb-3 text-sm font-semibold text-zinc-500">진행 중인 팀 세션 (Game Day)</h2>
        {gameDaySessions.length === 0 && <p className="text-sm text-zinc-500">지금 진행 중인 팀 세션이 없습니다.</p>}
        <ul className="flex flex-col gap-2">
          {gameDaySessions.map((s) => (
            <li key={s.sessionId} className="flex items-center justify-between text-sm">
              <span>
                {s.scenarioTitle}
                <span className="ml-2 text-xs text-zinc-500">
                  {s.ownerNickname} · {s.currentPhase ?? s.status}
                </span>
              </span>
              <Link href={`/design/${s.sessionId}`} className="text-xs text-blue-600 underline dark:text-blue-400">
                관전하기
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <h2 className="mb-3 text-sm font-semibold text-zinc-500">커스텀 시나리오</h2>
        {scenarios.length === 0 && <p className="text-sm text-zinc-500">아직 만들어진 커스텀 시나리오가 없습니다.</p>}
        <ul className="flex flex-col gap-2">
          {scenarios.map((scenario) => (
            <li key={scenario.id} className="flex items-center justify-between text-sm">
              <span>
                {scenario.title}
                {scenario.difficulty && <span className="ml-2 text-xs text-zinc-500">({scenario.difficulty})</span>}
              </span>
              <button
                onClick={() => handleStartScenario(scenario.id)}
                disabled={startingScenarioId === scenario.id}
                className="rounded bg-foreground px-3 py-1 text-xs font-medium text-background disabled:opacity-50"
              >
                {startingScenarioId === scenario.id ? "시작하는 중..." : "세션 시작"}
              </button>
            </li>
          ))}
        </ul>

        {org.myRole === "ADMIN" && (
          <form onSubmit={handleCreateScenario} className="mt-4 flex flex-col gap-2 border-t border-zinc-200 pt-4 dark:border-zinc-800">
            <p className="text-xs text-zinc-500">새 시나리오 만들기 (설계 + 꼬리설계 2단계, 장애 대응 단계는 없습니다)</p>
            <input
              className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
              value={scenarioTitle}
              onChange={(e) => setScenarioTitle(e.target.value)}
              placeholder="제목"
            />
            <div className="flex gap-2">
              <input
                className="flex-1 rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
                value={scenarioDomain}
                onChange={(e) => setScenarioDomain(e.target.value)}
                placeholder="도메인 라벨 (예: internal-payment)"
              />
              <input
                className="w-32 rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
                value={scenarioDifficulty}
                onChange={(e) => setScenarioDifficulty(e.target.value)}
                placeholder="난이도"
              />
            </div>
            <textarea
              className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
              value={scenarioInitialPrompt}
              onChange={(e) => setScenarioInitialPrompt(e.target.value)}
              placeholder="초기 설계 프롬프트"
              rows={3}
            />
            <textarea
              className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
              value={scenarioFollowupPrompt}
              onChange={(e) => setScenarioFollowupPrompt(e.target.value)}
              placeholder="꼬리설계 프롬프트"
              rows={3}
            />
            <button
              type="submit"
              disabled={creatingScenario}
              className="self-start rounded bg-foreground px-4 py-2 text-sm font-medium text-background disabled:opacity-50"
            >
              {creatingScenario ? "만드는 중..." : "시나리오 만들기"}
            </button>
          </form>
        )}
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
