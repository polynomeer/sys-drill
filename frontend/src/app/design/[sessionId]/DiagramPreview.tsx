"use client";

import { MermaidDiagram } from "@/components/MermaidDiagram";

const MERMAID_BLOCK = /```mermaid\n([\s\S]*?)```/g;

const TEMPLATE = `\n\n\`\`\`mermaid\nflowchart TD\n    Client([Client]) --> API[API 서버]\n    API --> Cache[(Cache)]\n    API --> DB[(DB)]\n\`\`\`\n`;

function extractDiagrams(answer: string): string[] {
  return [...answer.matchAll(MERMAID_BLOCK)].map((match) => match[1].trim());
}

/**
 * Renders any ```mermaid fenced blocks inside the free-text design answer as
 * live diagrams. The answer itself stays the single source of truth for
 * submission — this is a pure read-only preview over it, nothing here changes
 * what gets sent to the evaluator.
 */
export function DiagramPreview({ answer, onAppend }: { answer: string; onAppend: (text: string) => void }) {
  const diagrams = extractDiagrams(answer);

  return (
    <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-zinc-500">다이어그램 미리보기</h2>
        <button type="button" onClick={() => onAppend(TEMPLATE)} className="text-xs text-zinc-500 underline">
          템플릿 삽입
        </button>
      </div>

      {diagrams.length === 0 ? (
        <p className="text-xs text-zinc-500">
          답안에 <code className="rounded bg-zinc-100 px-1 dark:bg-zinc-800">```mermaid</code> 블록을 추가하면 여기에
          다이어그램이 표시됩니다.
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          {diagrams.map((code, i) => (
            <MermaidDiagram key={i} code={code} />
          ))}
        </div>
      )}

      <details className="mt-3 text-xs text-zinc-500">
        <summary className="cursor-pointer select-none">Mermaid 문법 치트시트</summary>
        <ul className="mt-2 list-inside list-disc space-y-1">
          <li>
            <code>A --&gt; B</code> — A에서 B로 요청/흐름
          </li>
          <li>
            <code>A -.-&gt; B</code> — 비동기/이벤트 흐름
          </li>
          <li>
            <code>Service[서비스]</code> — 사각형 (서버/서비스)
          </li>
          <li>
            <code>DB[(DB)]</code> — 원통형 (저장소)
          </li>
          <li>
            <code>Queue{"{{"}큐{"}}"}</code> — 육각형 (큐/브로커)
          </li>
          <li>
            <code>Client([Client])</code> — 캡슐형 (외부 액터)
          </li>
        </ul>
      </details>
    </section>
  );
}
