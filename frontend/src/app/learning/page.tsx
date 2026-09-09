import { Card } from "@/components/ui/Card";
import { DESIGN_GUIDANCE_BY_DOMAIN, DOMAIN_TITLES, INCIDENT_GUIDANCE } from "@/lib/designGuidance";
import { RISK_KEYS, riskDescription, riskLabel } from "@/lib/riskLabels";

/**
 * SysDrill_UIUX_Design_Plan.docx §4/§10 — Learning은 P2(콘텐츠·네트워크
 * 효과 확장)라 실시간 CMS는 이번 스코프 밖이지만, 정적 콘텐츠는 사실 이미
 * 이 프로젝트 안에 있었다: Design Workspace가 세션 중에 보여주는 도메인별
 * 가이드(designGuidance.ts, 원래 design/[sessionId]/page.tsx 로컬 상수였던
 * 것을 공용 모듈로 추출)와, RuleEvaluator의 riskKey별 설명이 그것이다.
 * 새 콘텐츠를 지어내는 대신 이미 검증된 두 소스를 재사용한다.
 */
export default function LearningPage() {
  const domains = Object.keys(DESIGN_GUIDANCE_BY_DOMAIN);

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">Learning</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          도메인별 설계 가이드와 핵심 개념 레퍼런스입니다 — 훈련 중에도 이 문구들을 그대로 만나게 됩니다.
        </p>
      </div>

      <section className="flex flex-col gap-4">
        <h2 className="text-sm font-semibold text-foreground-muted">도메인별 설계 가이드</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          {domains.map((domain) => (
            <Card key={domain} as="section">
              <h3 className="mb-2 font-medium">{DOMAIN_TITLES[domain] ?? domain}</h3>
              <ul className="list-inside list-disc space-y-1 text-sm text-foreground-muted">
                {DESIGN_GUIDANCE_BY_DOMAIN[domain].map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </Card>
          ))}
          <Card as="section">
            <h3 className="mb-2 font-medium">장애 대응 회고</h3>
            <ul className="list-inside list-disc space-y-1 text-sm text-foreground-muted">
              {INCIDENT_GUIDANCE.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </Card>
        </div>
      </section>

      <section className="flex flex-col gap-4">
        <h2 className="text-sm font-semibold text-foreground-muted">개념 레퍼런스</h2>
        <p className="text-xs text-foreground-muted">
          AI 피드백의 “놓친 점”에서 지적받을 수 있는 패턴들입니다 — 채점 기준과 같은 어휘를 씁니다.
        </p>
        <div className="grid gap-4 sm:grid-cols-2">
          {RISK_KEYS.map((key) => (
            <Card key={key}>
              <h3 className="mb-1 font-medium">{riskLabel(key)}</h3>
              <p className="text-sm text-foreground-muted">{riskDescription(key)}</p>
            </Card>
          ))}
        </div>
      </section>
    </div>
  );
}
