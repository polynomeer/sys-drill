"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ApiError,
  ArchitectureAnalysis,
  ScenarioSummary,
  analyzeArchitecture,
  listMyArchitectureScenarios,
  startSession,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { MermaidDiagram } from "@/components/MermaidDiagram";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

export default function ArchitectureAnalysisPage() {
  const router = useRouter();

  const [mine, setMine] = useState<ScenarioSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [spec, setSpec] = useState("");
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState<ArchitectureAnalysis | null>(null);
  const [startingId, setStartingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setMine(await listMyArchitectureScenarios());
  }, []);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    load()
      .catch((err) => setError(err instanceof ApiError ? err.message : "목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router, load]);

  function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    file.text().then(setSpec);
  }

  async function handleAnalyze(e: React.FormEvent) {
    e.preventDefault();
    if (!spec.trim()) return;
    setAnalyzing(true);
    setError(null);
    setResult(null);
    try {
      const analysis = await analyzeArchitecture(spec);
      setResult(analysis);
      setSpec("");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "분석하지 못했습니다. OpenAPI 3.0 YAML/JSON 형식인지 확인하세요.");
    } finally {
      setAnalyzing(false);
    }
  }

  async function handleStart(scenarioId: string) {
    setStartingId(scenarioId);
    setError(null);
    try {
      const session = await startSession(scenarioId);
      router.push(`/design/${session.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "세션을 시작하지 못했습니다.");
      setStartingId(null);
    }
  }

  if (loading) return <LoadingState className="p-8" />;

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">정적 분석 (Architecture Linter)</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          OpenAPI 스펙을 업로드하면 규칙 기반으로 리스크를 찾아 나만의 훈련 시나리오를 만듭니다. 원본 스펙은 저장하지 않으며, 생성된
          시나리오는 본인만 볼 수 있습니다.
        </p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">OpenAPI 스펙 분석</h2>
        <form onSubmit={handleAnalyze} className="flex flex-col gap-2">
          <input type="file" accept=".yaml,.yml,.json" onChange={handleFileSelect} className="text-sm" />
          <textarea
            className="rounded border border-border px-3 py-2 font-mono text-xs  "
            value={spec}
            onChange={(e) => setSpec(e.target.value)}
            placeholder="OpenAPI 3.0 YAML 또는 JSON을 붙여넣으세요"
            rows={10}
          />
          <Button type="submit" disabled={analyzing} className="self-start">
            {analyzing ? "분석하는 중..." : "분석하기"}
          </Button>
        </form>

        {result && (
          <div className="mt-4 rounded bg-surface-elevated p-3 text-sm ">
            <p className="mb-2 font-medium">{result.scenario.title} — 발견된 리스크 {result.findings.length}건</p>
            <div className="mb-3 rounded border border-border bg-surface p-2  ">
              <MermaidDiagram code={result.diagram} />
              <p className="mt-1 text-[11px] text-foreground-muted">빨강 = 높은 위험, 노랑 = 중간 위험 엔드포인트</p>
            </div>
            {result.findings.length === 0 ? (
              <p className="text-xs text-foreground-muted">뚜렷한 문제를 찾지 못했습니다.</p>
            ) : (
              <ul className="mb-3 flex flex-col gap-1 text-xs text-foreground-muted">
                {result.findings.map((f, i) => (
                  <li key={i}>- {f}</li>
                ))}
              </ul>
            )}
            <Button variant="secondary" size="sm" onClick={() => handleStart(result.scenario.id)} disabled={startingId === result.scenario.id}>
              {startingId === result.scenario.id ? "시작하는 중..." : "이 시나리오로 시작"}
            </Button>
          </div>
        )}
      </Card>

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">내가 만든 분석 시나리오 ({mine.length}개)</h2>
        {mine.length === 0 && <p className="text-sm text-foreground-muted">아직 만든 시나리오가 없습니다.</p>}
        <ul className="flex flex-col gap-2">
          {mine.map((s) => (
            <li key={s.id} className="flex items-center justify-between text-sm">
              <span>{s.title}</span>
              <Button variant="secondary" size="sm" onClick={() => handleStart(s.id)} disabled={startingId === s.id}>
                {startingId === s.id ? "시작하는 중..." : "시작"}
              </Button>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
