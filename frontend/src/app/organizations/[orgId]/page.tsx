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
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Input, Textarea } from "@/components/ui/Input";
import { LoadingState } from "@/components/ui/LoadingState";

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "관리자",
  MEMBER: "멤버",
};

/** ROADMAP.md Phase 4 "커스텀 루브릭" — "이름:점수" 한 줄씩, 합계 100(Rubric.maxTotal)을 클라이언트에서도 검증
 * (서버가 이중 검증하지만, 빈 텍스트영역 제출 실패 전에 미리 알려주기 위함). 비어있으면 dimensions는 빈 객체. */
function parseRubricLines(value: string): { dimensions: Record<string, number>; error: string | null } {
  const lines = value
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  if (lines.length === 0) return { dimensions: {}, error: null };
  const dimensions: Record<string, number> = {};
  for (const line of lines) {
    const idx = line.indexOf(":");
    const name = idx >= 0 ? line.slice(0, idx).trim() : "";
    const score = idx >= 0 ? Number(line.slice(idx + 1).trim()) : NaN;
    if (!name || !Number.isFinite(score)) {
      return { dimensions: {}, error: `루브릭 형식 오류: "${line}" (예: 보안 검토:50)` };
    }
    dimensions[name] = score;
  }
  const sum = Object.values(dimensions).reduce((a, b) => a + b, 0);
  if (sum !== 100) return { dimensions, error: `루브릭 점수 합계는 100이어야 합니다 (현재 ${sum})` };
  return { dimensions, error: null };
}

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
  // ADR-0038 — an incident (Wargame) step is only possible when domain is one
  // of the 7 known simulation domains, not the free-text label the domain
  // field otherwise accepts, so this toggle switches the domain input itself
  // between a free-text field and a constrained dropdown.
  const [includeIncident, setIncludeIncident] = useState(false);
  const [scenarioIncidentPrompt, setScenarioIncidentPrompt] = useState("");
  const [scenarioRubricText, setScenarioRubricText] = useState("");
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
    if (includeIncident && !scenarioIncidentPrompt.trim()) return;
    const { dimensions: rubricDimensions, error: rubricError } = parseRubricLines(scenarioRubricText);
    if (rubricError) {
      setError(rubricError);
      return;
    }
    setCreatingScenario(true);
    setError(null);
    try {
      await createCustomScenario(orgId, {
        title: scenarioTitle.trim(),
        difficulty: scenarioDifficulty.trim() || undefined,
        domain: scenarioDomain.trim(),
        initialPrompt: scenarioInitialPrompt.trim(),
        followupPrompt: scenarioFollowupPrompt.trim(),
        incidentPrompt: includeIncident ? scenarioIncidentPrompt.trim() : undefined,
        rubricDimensions: Object.keys(rubricDimensions).length > 0 ? rubricDimensions : undefined,
      });
      setScenarioTitle("");
      setScenarioDomain("");
      setScenarioDifficulty("");
      setScenarioInitialPrompt("");
      setScenarioFollowupPrompt("");
      setIncludeIncident(false);
      setScenarioIncidentPrompt("");
      setScenarioRubricText("");
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

  if (loading) return <LoadingState className="p-8" />;
  if (error && !org) return <p className="p-8 text-sm text-danger">{error}</p>;
  if (!org) return null;

  const inviteLink = lastInviteToken ? `${window.location.origin}/organizations/invitations/${lastInviteToken}` : null;

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6 p-8">
      <div>
        <Link href="/organizations" className="text-sm text-foreground-muted underline">
          내 조직 목록으로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">{org.name}</h1>
        <p className="mt-1 text-sm text-foreground-muted">내 역할: {ROLE_LABELS[org.myRole] ?? org.myRole}</p>
        <div className="mt-2 flex gap-4">
          <Link href={`/organizations/${orgId}/curriculum`} className="text-sm text-accent underline">
            온보딩 커리큘럼 보기
          </Link>
          {org.myRole === "ADMIN" && (
            <>
              <Link href={`/organizations/${orgId}/dashboard`} className="text-sm text-accent underline">
                팀 대시보드 보기
              </Link>
              <Link href={`/organizations/${orgId}/audit-log`} className="text-sm text-accent underline">
                감사 로그 보기
              </Link>
              <Link href={`/organizations/${orgId}/assessments`} className="text-sm text-accent underline">
                역량 평가 보기
              </Link>
            </>
          )}
        </div>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">멤버 ({org.members.length}명)</h2>
        <ul className="flex flex-col gap-2">
          {org.members.map((member) => (
            <li key={member.userId} className="flex items-center justify-between text-sm">
              <span>
                {member.nickname} <span className="text-foreground-muted">({member.email})</span>
              </span>
              <span className="flex items-center gap-2">
                <Badge>{ROLE_LABELS[member.role] ?? member.role}</Badge>
                {org.myRole === "ADMIN" && (
                  <Button variant="danger" size="sm" onClick={() => handleRemove(member.userId)}>
                    제거
                  </Button>
                )}
              </span>
            </li>
          ))}
        </ul>
        <Button variant="ghost" size="sm" onClick={handleLeave} className="mt-4">
          나가기
        </Button>
      </Card>

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">진행 중인 팀 세션 (Game Day)</h2>
        {gameDaySessions.length === 0 && <EmptyState message="지금 진행 중인 팀 세션이 없습니다." />}
        <ul className="flex flex-col gap-2">
          {gameDaySessions.map((s) => (
            <li key={s.sessionId} className="flex items-center justify-between text-sm">
              <span>
                {s.scenarioTitle}
                <span className="ml-2 text-xs text-foreground-muted">
                  {s.ownerNickname} · {s.currentPhase ?? s.status}
                </span>
              </span>
              <Button href={`/design/${s.sessionId}`} variant="ghost" size="sm">
                관전하기
              </Button>
            </li>
          ))}
        </ul>
      </Card>

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">커스텀 시나리오</h2>
        {scenarios.length === 0 && <EmptyState message="아직 만들어진 커스텀 시나리오가 없습니다." />}
        <ul className="flex flex-col gap-2">
          {scenarios.map((scenario) => (
            <li key={scenario.id} className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-2">
                {scenario.title}
                {scenario.difficulty && <Badge>{scenario.difficulty}</Badge>}
              </span>
              <Button size="sm" variant="secondary" onClick={() => handleStartScenario(scenario.id)} disabled={startingScenarioId === scenario.id}>
                {startingScenarioId === scenario.id ? "시작하는 중..." : "세션 시작"}
              </Button>
            </li>
          ))}
        </ul>

        {org.myRole === "ADMIN" && (
          <form onSubmit={handleCreateScenario} className="mt-4 flex flex-col gap-2 border-t border-border pt-4 ">
            <p className="text-xs text-foreground-muted">
              새 시나리오 만들기 (설계 + 꼬리설계 2단계{includeIncident ? " + 장애 대응" : ", 장애 대응 단계는 없습니다"})
            </p>
            <Input value={scenarioTitle} onChange={(e) => setScenarioTitle(e.target.value)} placeholder="제목" />
            <div className="flex gap-2">
              {includeIncident ? (
                <select
                  className="flex-1 rounded border border-border bg-transparent px-2 py-1.5 text-sm"
                  value={scenarioDomain}
                  onChange={(e) => setScenarioDomain(e.target.value)}
                >
                  <option value="">도메인 선택</option>
                  {Object.entries(DOMAIN_TITLES).map(([domain, title]) => (
                    <option key={domain} value={domain}>
                      {title} ({domain})
                    </option>
                  ))}
                </select>
              ) : (
                <Input className="flex-1" value={scenarioDomain} onChange={(e) => setScenarioDomain(e.target.value)} placeholder="도메인 라벨 (예: internal-payment)" />
              )}
              <Input className="w-32" value={scenarioDifficulty} onChange={(e) => setScenarioDifficulty(e.target.value)} placeholder="난이도" />
            </div>
            <Textarea value={scenarioInitialPrompt} onChange={(e) => setScenarioInitialPrompt(e.target.value)} placeholder="초기 설계 프롬프트" rows={3} />
            <Textarea value={scenarioFollowupPrompt} onChange={(e) => setScenarioFollowupPrompt(e.target.value)} placeholder="꼬리설계 프롬프트" rows={3} />
            <label className="flex items-center gap-2 text-xs text-foreground-muted">
              <input
                type="checkbox"
                checked={includeIncident}
                onChange={(e) => {
                  setIncludeIncident(e.target.checked);
                  if (e.target.checked && scenarioDomain.trim() && !(scenarioDomain in DOMAIN_TITLES)) setScenarioDomain("");
                }}
              />
              장애 대응(Wargame) 단계 추가 — 위 도메인이 기존 7개 시뮬레이션 도메인 중 하나로 제한됩니다
            </label>
            {includeIncident && (
              <Textarea
                value={scenarioIncidentPrompt}
                onChange={(e) => setScenarioIncidentPrompt(e.target.value)}
                placeholder="장애 대응 프롬프트 (예: Redis latency가 급증합니다)"
                rows={3}
              />
            )}
            <Textarea
              value={scenarioRubricText}
              onChange={(e) => setScenarioRubricText(e.target.value)}
              placeholder={"커스텀 채점 루브릭 (선택, 한 줄에 '이름:점수', 합계 100)\n예: 보안 검토:50\n비용 효율성:50"}
              rows={3}
            />
            <Button type="submit" disabled={creatingScenario} className="self-start">
              {creatingScenario ? "만드는 중..." : "시나리오 만들기"}
            </Button>
          </form>
        )}
      </Card>

      {org.myRole === "ADMIN" && (
        <>
          <Card as="section">
            <h2 className="mb-3 text-sm font-semibold text-foreground-muted">멤버 초대</h2>
            <form onSubmit={handleInvite} className="flex flex-wrap gap-2">
              <Input
                className="flex-1"
                type="email"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                placeholder="invitee@example.com"
              />
              <select
                className="rounded border border-border px-3 py-2 text-sm  "
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value as OrganizationRole)}
              >
                <option value="MEMBER">멤버</option>
                <option value="ADMIN">관리자</option>
              </select>
              <Button type="submit" disabled={inviting}>
                {inviting ? "초대하는 중..." : "초대"}
              </Button>
            </form>
            {inviteLink && (
              <div className="mt-3 rounded bg-surface-elevated p-3 text-xs ">
                <p className="mb-1 text-foreground-muted">초대 이메일을 발송했습니다. 아래 링크를 직접 전달할 수도 있습니다:</p>
                <code className="break-all">{inviteLink}</code>
              </div>
            )}
          </Card>

          <Card as="section">
            <h2 className="mb-3 text-sm font-semibold text-foreground-muted">대기 중인 초대</h2>
            {invitations.length === 0 && <EmptyState message="대기 중인 초대가 없습니다." />}
            <ul className="flex flex-col gap-2">
              {invitations.map((inv) => (
                <li key={inv.id} className="flex items-center justify-between text-sm">
                  <span className="flex items-center gap-2">
                    {inv.inviteeEmail} <span className="text-xs text-foreground-muted">({ROLE_LABELS[inv.role] ?? inv.role})</span>
                    {inv.expired && <Badge variant="danger">만료됨</Badge>}
                  </span>
                  <Button variant="danger" size="sm" onClick={() => handleRevoke(inv.id)}>
                    취소
                  </Button>
                </li>
              ))}
            </ul>
          </Card>
        </>
      )}
    </div>
  );
}
