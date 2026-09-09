import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

const REPO_URL = "https://github.com/polynomeer/sys-drill";

/**
 * SysDrill_UIUX_Design_Plan.docx §4/§10 — Community는 P2(콘텐츠·네트워크
 * 효과 확장)라 자체 게시판/댓글 시스템은 이번 스코프 밖. 인앱 CMS를 지어내는
 * 대신 실제로 동작하는 GitHub Issues로 안내한다 — 이 저장소는 Discussions가
 * 꺼져 있어(gh repo view로 확인) 그쪽으로 안내하지 않는다.
 */
export default function CommunityPage() {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">Community</h1>
        <p className="mt-1 text-sm text-foreground-muted">풀이, 설계 회고, 장애 대응 회고와 토론을 나누는 공간입니다.</p>
      </div>
      <Card>
        <h2 className="mb-2 font-medium">GitHub Issues</h2>
        <p className="mb-3 text-sm text-foreground-muted">
          지금은 GitHub Issues에서 질문·설계 회고·버그 리포트를 나누고 있습니다. 인앱 게시판은 이후 별도로 준비할 예정입니다.
        </p>
        <Button href={`${REPO_URL}/issues`} target="_blank" size="sm">
          Issues 열기 →
        </Button>
      </Card>
    </div>
  );
}
