import { Card } from "@/components/ui/Card";

/** SysDrill_UIUX_Design_Plan.docx §4/§10 — Learning은 P2(콘텐츠·네트워크 효과 확장)라
 * 이번 라운드 스코프 밖. 헤더 IA에는 있어야 하므로 죽은 링크 대신 최소 placeholder만 둔다. */
export default function LearningPage() {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">Learning</h1>
        <p className="mt-1 text-sm text-foreground-muted">개념, 구현 가이드, 레퍼런스 자료를 제공할 예정입니다.</p>
      </div>
      <Card>
        <p className="text-sm text-foreground-muted">곧 제공됩니다.</p>
      </Card>
    </div>
  );
}
