import Link from "next/link";
import { Alert } from "@/components/ui/Alert";

/** docs/COMMERCIALIZATION.md — placeholder content pending legal review; the page shell (route, versioning, consent link target) is real. */
export default function PrivacyPolicyPage() {
  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <Link href="/onboarding" className="text-sm text-foreground-muted underline">
          가입 화면으로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">개인정보처리방침</h1>
        <p className="mt-1 text-xs text-foreground-muted">버전 초안 · 최종 수정 없음</p>
      </div>

      <Alert>
        이 문서는 초안이며 법률 검토가 완료되지 않았습니다. 특히 AI 평가를 위해 사용자 답안이 외부 LLM 프로바이더로
        전송되는 부분, 개인정보 국외 이전 이슈는 반드시 법률 자문을 거쳐야 합니다.
      </Alert>

      <section className="flex flex-col gap-3 text-sm text-foreground-muted">
        <p>(플레이스홀더) 수집하는 개인정보 항목과 이용 목적이 여기에 기재됩니다.</p>
        <p>(플레이스홀더) 제3자 제공 및 국외 이전(예: LLM 프로바이더) 관련 고지가 여기에 기재됩니다.</p>
        <p>(플레이스홀더) 보유 기간, 이용자의 권리와 행사 방법이 여기에 기재됩니다.</p>
      </section>
    </div>
  );
}
