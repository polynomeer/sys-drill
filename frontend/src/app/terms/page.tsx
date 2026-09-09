import Link from "next/link";
import { Alert } from "@/components/ui/Alert";

/** docs/COMMERCIALIZATION.md — placeholder content pending legal review; the page shell (route, versioning, consent link target) is real. */
export default function TermsPage() {
  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <Link href="/onboarding" className="text-sm text-foreground-muted underline">
          가입 화면으로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">이용약관</h1>
        <p className="mt-1 text-xs text-foreground-muted">버전 초안 · 최종 수정 없음</p>
      </div>

      <Alert>이 문서는 초안이며 법률 검토가 완료되지 않았습니다. 실제 서비스 운영 전 반드시 법률 자문을 거쳐야 합니다.</Alert>

      <section className="flex flex-col gap-3 text-sm text-foreground-muted">
        <p>(플레이스홀더) 본 약관은 SysDrill 서비스 이용에 관한 조건을 정합니다.</p>
        <p>(플레이스홀더) 서비스 내용, 이용자의 권리와 의무, 면책 조항 등이 여기에 기재됩니다.</p>
      </section>
    </div>
  );
}
