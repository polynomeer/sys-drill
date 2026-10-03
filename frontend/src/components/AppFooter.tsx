"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { isFullBleed } from "@/lib/fullBleed";
import { ThemeSelect } from "@/components/ThemeSelect";

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C7 (PLAN.md Round E3) — 학습 커뮤니티(시나리오별 토론)와
 * 제품 개발 커뮤니티(GitHub Issues)를 나눈다. ADR-0040으로 Community 탭에서 GitHub 링크를
 * 걷어낸 뒤 버그·기능 요청을 남길 곳이 사라졌던 것을 푸터로 되살린다.
 */
export function AppFooter() {
  const fullBleed = isFullBleed(usePathname());
  return (
    <footer className={`mt-auto border-t border-border px-6 py-4 text-xs text-foreground-muted ${fullBleed ? "md:px-8" : ""}`}>
      <div className={`mx-auto flex flex-wrap items-center gap-x-4 gap-y-1 ${fullBleed ? "max-w-none" : "max-w-7xl"}`}>
        <span>SysDrill</span>
        <Link href="/how-it-works" className="hover:text-foreground">동작 방식</Link>
        <Link href="/terms" className="hover:text-foreground">이용약관</Link>
        <Link href="/privacy" className="hover:text-foreground">개인정보처리방침</Link>
        <a
          href="https://github.com/polynomeer/sys-drill/issues"
          target="_blank"
          rel="noopener noreferrer"
          className="ml-auto hover:text-foreground"
        >
          제품 피드백 ↗
        </a>
        <ThemeSelect />
      </div>
    </footer>
  );
}
